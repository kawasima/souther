package souther.lsp;

import souther.compiler.cst.LineIndex;
import souther.lsp.analysis.Analyzer;
import souther.lsp.analysis.DocumentStore;
import souther.lsp.analysis.ModuleGraph;
import souther.lsp.analysis.Workspace;
import souther.compiler.query.Abandoned;
import souther.compiler.query.Abandonment;
import souther.compiler.query.Adequacy;
import souther.lsp.protocol.CodeAction;
import souther.lsp.protocol.CodeLens;
import souther.lsp.protocol.CompletionItem;
import souther.lsp.protocol.DocumentHighlight;
import souther.lsp.protocol.DocumentSymbol;
import souther.lsp.protocol.Hover;
import souther.lsp.protocol.InlayHint;
import souther.lsp.protocol.Insertion;
import souther.lsp.protocol.Location;
import souther.lsp.protocol.LspDiagnostic;
import souther.lsp.protocol.Position;
import souther.lsp.protocol.Range;
import souther.lsp.protocol.WorkspaceSymbol;
import souther.lsp.rpc.InboundDecoders;
import souther.lsp.rpc.Params;
import souther.lsp.transport.MessageConnection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * A hand-rolled LSP server over a {@link MessageConnection}. It reads JSON-RPC messages, dispatches
 * by method, and answers requests / publishes diagnostics. Inbound payloads are decoded with Raoh
 * ({@link InboundDecoders}); outbound trees are built as maps and serialised with Jackson. The
 * language work is delegated to the {@link Analyzer}, which knows nothing of the protocol.
 *
 * <p>Two threads, and one of them owns everything: a session reads frames on a thread of its own and
 * carries them out on the thread it was started on, which holds the documents, the workspace and the
 * analyzer. {@link #run} says what that buys and why it is that way round.
 */
public final class LspServer {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** JSON-RPC's, and the protocol's for a request the client gave up on. */
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INTERNAL_ERROR = -32603;
    private static final int REQUEST_CANCELLED = -32800;

    /**
     * The one method carried out where the frames are read rather than where they are answered.
     *
     * <p>It is not in {@link LspMethod} because it is not answered: it says something about another
     * message, and it says it while that message is being worked on. Handed over like the rest, it
     * would be read after the request it names had been answered — which is every request it could
     * have stopped.
     *
     * <p>Acted on only where it is a notification, which is what the protocol says it is. Written
     * with an id it is a request, and every request is owed a reply; this server has no reply for
     * one, so it goes on to be answered the way a method nobody declared is.
     */
    private static final String CANCEL_REQUEST = "$/cancelRequest";

    /** Nothing to write. Held once because it says nothing and every message that says it says the
     * same nothing. */
    private static final Outcome NOTHING = new Outcome.Nothing();

    private final MessageConnection conn;
    private final DocumentStore documents = new DocumentStore();
    private final Analyzer analyzer = new Analyzer();

    /** Whether this client said it reads completion placeholders. False until it says so. */
    private boolean readsSnippets;
    /** Whether this client sent the {@code shutdown} request. What the exit code answers. */
    private boolean askedToShutDown;
    private final Workspace workspace = new Workspace();
    private int nextRequestId = 1;

    /** What has been read and not yet carried out. The only thing both threads touch. */
    private final Inbox inbox = new Inbox();

    /** Whether what was published still says what the documents say. */
    private boolean diagnosticsAreStale;

    /**
     * What would make the unit of work in hand stop short of its answer.
     *
     * <p>It is the unit of work that decides, and the two units this server has are stopped by
     * different things: a request by the client saying it no longer wants the answer, a diagnose by
     * anything at all arriving, because a diagnose is what this thread does when it has nothing else
     * to do. Written as each unit begins and read through {@link #abandonment}.
     */
    private BooleanSupplier stopWhen = () -> false;

    /**
     * What every step of the work in hand asks. One value, held by the analyzer and the workspace
     * from the start, so that the reason work stops is settled in one place and not carried down
     * through each of them.
     */
    private final Abandonment abandonment = new Abandonment(this::stopNow);

    public LspServer(MessageConnection conn) {
        this.conn = conn;
        analyzer.abandonWhen(abandonment);
        workspace.abandonWhen(abandonment);
    }

    /**
     * Whether the work in hand should stop, and where a failure of the reading thread is met.
     *
     * <p>In that order, and the order is the point. A session whose connection cannot be read has
     * nobody left to answer, so whether this particular answer is still wanted is no longer a
     * question. It is asked here because a thread in the middle of a long answer is not reading the
     * queue the failure is also published to.
     */
    private boolean stopNow() {
        Throwable failed = inbox.readerFailure();
        if (failed != null) {
            throw new ConnectionLost(failed);
        }
        return stopWhen.getAsBoolean();
    }

    public static void main(String[] args) {
        System.exit(serve(System.in, System.out));
    }

    /**
     * Serves one session over the given streams and returns what a command line exits with.
     *
     * <p>The entry point a caller that is not a {@code java -jar} line uses. {@code souther lsp}
     * launches this server in the command line's own process, so what starts a session cannot be a
     * {@code main} that owns the exit: a method returning the code leaves that decision where the
     * process is, which is one level up from here either way.
     *
     * <p>What the code says is whether the client shut the server down before it went: the protocol
     * has a session end on it, and a session that stopped without one stopped for a reason nobody
     * here can name. {@code exit} after {@code shutdown} is zero, and so is a client that closed the
     * stream once it had shut the server down; anything else is one.
     */
    public static int serve(InputStream in, OutputStream out) {
        return new LspServer(new MessageConnection(in, out)).run();
    }

    /**
     * Serves one session, and answers with the code the protocol asks the process to end under.
     *
     * <p>A thread beside this one reads frames; this one owns the documents, the workspace and the
     * analyzer, and carries the frames out in the order they arrived. The store a compile is made of
     * is asked by one thread only, which is the sentence it is written under, and what the second
     * thread buys is not a second question being answered — it is that a question can be read while
     * the one before it is still being answered, and so that it can be given up on.
     *
     * <p>This way round, and not the other. Whoever owns the store owns the session: an error
     * nothing here is entitled to recover from ends this thread, and ending this thread ends the
     * process, because the thread that reads is a daemon. A server that had lost its analysis and
     * gone on reading would answer nothing and say nothing about it.
     */
    public int run() {
        Thread.ofPlatform().daemon().name("souther-lsp-reader").start(this::read);
        return carryOutWhatArrives();
    }

    /**
     * Reads frames and hands them over until the stream ends or reading cannot go on.
     *
     * <p>Nothing is answered here. What this thread decides is the order things happened in, and the
     * one thing it acts on itself is a cancel, which has to reach a request that is already being
     * worked on.
     */
    private void read() {
        try {
            String message;
            while ((message = conn.read()) != null) {
                JsonNode m;
                try {
                    m = JSON.readTree(message);
                } catch (RuntimeException _) {
                    continue;   // a malformed frame is dropped, not fatal
                }
                JsonNode methodNode = m.get("method");
                if (methodNode == null || methodNode.isNull()) {
                    continue;   // a response to a server-initiated request; nothing to do
                }
                JsonNode id = m.get("id");
                if (CANCEL_REQUEST.equals(methodNode.asString()) && (id == null || id.isNull())) {
                    JsonNode params = m.get("params");
                    inbox.cancel(params == null ? null : params.get("id"));
                    continue;
                }
                inbox.hand(m);
            }
            inbox.readerEnded();
        } catch (Throwable t) {
            // Caught to be carried, not to be recovered from: this thread cannot end a session and
            // the one that can is not reading the stream. It is raised again there.
            inbox.readerFailed(t);
        }
    }

    /**
     * Carries out what arrives, and diagnoses the workspace whenever nothing else is waiting.
     *
     * <p>Which is the whole of the scheduling. A diagnose is what this thread does with an idle
     * moment: it gives way to anything at all, and a run of keystrokes therefore costs one diagnose
     * at the end of it rather than one each. What it does not do is guarantee it ever runs — a client
     * that never stopped asking would never be told anything, and that is what asking without pause
     * means, not a case to write a threshold for.
     */
    private int carryOutWhatArrives() {
        while (true) {
            Inbound next = inbox.take();
            if (next == null && diagnosticsAreStale) {
                diagnose();
                continue;
            }
            if (next == null) {
                next = inbox.await();
            }
            switch (next) {
                case Inbound.ReaderFailed failed -> throw new ConnectionLost(failed.cause());
                case Inbound.EndOfInput _ -> {
                    // Not something to give way to. Everything else in this queue is a client
                    // asking for something, and a diagnose steps aside for those; the end of the
                    // stream asks for nothing, so what was already asked for is finished first. A
                    // diagnose is the only thing that can still be owed at this point.
                    if (diagnosticsAreStale) {
                        diagnose();
                    }
                    return exitCode();
                }
                case Inbound.Message message -> {
                    if (carryOut(message)) {
                        return exitCode();
                    }
                }
            }
        }
    }

    /** What the protocol asks the process to end under: whether the client shut the server down. */
    private int exitCode() {
        return askedToShutDown ? 0 : 1;
    }

    /**
     * Carries out one message, writes the one reply it is owed, and says whether the session ends.
     *
     * <p>Where a request is settled. The read of the cancellation taken here is what decides it: a
     * client that gave up before this read is sent the reply that says so, and one that gave up
     * after it is sent the answer, because by then there was an answer and nothing to be gained by
     * throwing it away. Which of the two happened is not a thing the client can be told apart from
     * the reply it gets, and the protocol asks for one reply either way.
     */
    private boolean carryOut(Inbound.Message message) {
        JsonNode id = message.body().get("id");
        stopWhen = message.cancellation()::asked;
        Outcome outcome;
        try {
            outcome = dispatch(message.body());
        } catch (Abandoned _) {
            outcome = new Outcome.Cancelled();
        } catch (RuntimeException | StackOverflowError e) {
            // One request the server cannot answer must cost that request, not the session. The
            // analysis layer catches what it can so it can publish a marker instead, but that is a
            // promise made inside it; this is the one that holds whatever it does.
            outcome = new Outcome.Failed(INTERNAL_ERROR,
                    "the request could not be completed (" + e.getClass().getSimpleName() + ")");
        }
        if (outcome instanceof Outcome.Answered && message.cancellation().asked()) {
            outcome = new Outcome.Cancelled();
        }
        inbox.answered(id);
        write(id, outcome);
        return outcome instanceof Outcome.Stop;
    }

    /**
     * The one place a reply to a client's message is written.
     *
     * <p>A notification asked nothing, so nothing is written for one — an unknown notification
     * included, which is what makes naming a method the way a client learns the server does not
     * answer it without making an unnamed notification an error.
     */
    private void write(JsonNode id, Outcome outcome) {
        if (id == null || id.isNull()) {
            return;
        }
        switch (outcome) {
            case Outcome.Answered answered -> respond(id, answered.result());
            case Outcome.Cancelled _ ->
                    respondError(id, REQUEST_CANCELLED, "the request was cancelled");
            case Outcome.Failed failed -> respondError(id, failed.code(), failed.message());
            case Outcome.Nothing _ -> { }
            case Outcome.Stop _ -> { }
        }
    }

    /**
     * What one message comes to.
     *
     * <p>Naming the method is where a client learns the server does not answer it: nothing below
     * this point takes a spelling, so there is no second place a method could be refused, and none
     * where one could be answered without being declared.
     */
    private Outcome dispatch(JsonNode body) {
        String method = body.get("method").asString();
        LspMethod named = LspMethod.of(method).orElse(null);
        if (named == null) {
            return new Outcome.Failed(METHOD_NOT_FOUND, "method not found: " + method);
        }
        return answer(named, body.get("id"), body.get("params"));
    }

    /**
     * Carries out one method.
     *
     * <p>A switch expression with no {@code default}: a method added to {@link LspMethod} and left
     * unhandled here does not compile. That is what makes the set of methods the server answers the
     * set written there, and so the set the handshake is built from.
     */
    private Outcome answer(LspMethod method, JsonNode id, JsonNode params) {
        return switch (method) {
            case INITIALIZE -> { captureRoots(params); yield new Outcome.Answered(initializeResult()); }
            case INITIALIZED -> { registerDynamically(); yield NOTHING; }
            case SET_TRACE, DID_CHANGE_CONFIGURATION -> NOTHING;   // no-op
            case DID_OPEN -> {
                InboundDecoders.decode(InboundDecoders.DID_OPEN, params)
                        .ifPresent(p -> { documents.open(p.uri(), p.text()); diagnosticsAreStale = true; });
                yield NOTHING;
            }
            case DID_CHANGE -> {
                InboundDecoders.decode(InboundDecoders.DID_CHANGE, params)
                        .ifPresent(p -> { documents.change(p.uri(), p.text()); diagnosticsAreStale = true; });
                yield NOTHING;
            }
            case DID_CLOSE -> {
                InboundDecoders.decode(InboundDecoders.DOC_REF, params)
                        .ifPresent(p -> { documents.close(p.uri()); clearDiagnostics(p.uri()); });
                yield NOTHING;
            }
            case DID_CHANGE_WATCHED_FILES -> {
                workspace.markChanged();   // a file changed on disk; drop the cached scan and re-read
                diagnosticsAreStale = true;
                yield NOTHING;
            }
            case DOCUMENT_SYMBOL -> new Outcome.Answered(documentSymbols(params));
            case SEMANTIC_TOKENS_FULL -> new Outcome.Answered(semanticTokens(params));
            case HOVER -> new Outcome.Answered(hover(params));
            case DEFINITION -> new Outcome.Answered(definition(params));
            case REFERENCES -> new Outcome.Answered(references(params));
            case COMPLETION -> new Outcome.Answered(completion(params));
            case INLAY_HINT -> new Outcome.Answered(inlayHints(params));
            case DOCUMENT_HIGHLIGHT -> new Outcome.Answered(documentHighlights(params));
            case SELECTION_RANGE -> new Outcome.Answered(selectionRanges(params));
            case WORKSPACE_SYMBOL -> new Outcome.Answered(workspaceSymbols(params));
            case SIGNATURE_HELP -> new Outcome.Answered(signatureHelp(params));
            case CODE_ACTION -> new Outcome.Answered(codeActions(params));
            case CODE_ACTION_RESOLVE -> new Outcome.Answered(codeActionResolve(params));
            case CODE_LENS -> new Outcome.Answered(codeLenses(params));
            case RENAME -> new Outcome.Answered(rename(params));
            case FORMATTING -> new Outcome.Answered(formatting(params));
            // Only as the request it is. A `shutdown` written as a notification asked nothing, so
            // there is nothing to reply to and nothing the exit code can hold the client to.
            case SHUTDOWN -> {
                if (id == null || id.isNull()) {
                    yield NOTHING;
                }
                askedToShutDown = true;
                yield new Outcome.Answered(null);
            }
            case EXIT -> new Outcome.Stop();
        };
    }

    // --- capabilities ---

    /** Records the workspace roots the client announces, from {@code workspaceFolders} (preferred) or
     * the legacy {@code rootUri}, so the analyzer can resolve names across the whole module set. */
    private void captureRoots(JsonNode params) {
        if (params == null || params.isNull()) {
            return;
        }
        List<String> roots = new ArrayList<>();
        JsonNode folders = params.get("workspaceFolders");
        if (folders != null && folders.isArray()) {
            for (JsonNode folder : folders) {
                JsonNode uri = folder.get("uri");
                if (uri != null && !uri.isNull()) {
                    roots.add(uri.asString());
                }
            }
        }
        JsonNode rootUri = params.get("rootUri");
        if (roots.isEmpty() && rootUri != null && !rootUri.isNull()) {
            roots.add(rootUri.asString());
        }
        workspace.setRoots(roots);
        analyzer.measure(adequacyAsked(params));
        readsSnippets = snippetSupportAsked(params);
        analyzer.resolvesActions(resolvesEditsAsked(params));
    }

    /**
     * Whether the client said it will come back for an action's edit, from
     * {@code capabilities.textDocument.codeAction.dataSupport} and {@code resolveSupport.properties}.
     *
     * <p>Both, because they are two halves of one thing. What identifies the work travels in the
     * action's {@code data} and comes back on the resolve, and the property that is worked out then
     * is the edit — a client that keeps the data and will not resolve the edit, or resolves the edit
     * and drops the data, cannot be handed an action without one.
     *
     * <p>False unless it said so, which is the protocol's default and not a guess. A client that
     * never resolves is handed the edit up front, which costs what it costs; handed an action with
     * no edit, it would show an offer that does nothing.
     */
    private static boolean resolvesEditsAsked(JsonNode params) {
        JsonNode at = params == null ? null : params.get("capabilities");
        for (String field : List.of("textDocument", "codeAction")) {
            if (at == null || at.isNull()) {
                return false;
            }
            at = at.get(field);
        }
        if (at == null || at.isNull()) {
            return false;
        }
        JsonNode data = at.get("dataSupport");
        if (data == null || !data.isBoolean() || !data.asBoolean()) {
            return false;
        }
        JsonNode properties = at.path("resolveSupport").get("properties");
        if (properties == null || !properties.isArray()) {
            return false;
        }
        for (JsonNode property : properties) {
            if (property.isString() && "edit".equals(property.asString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the client said it reads completion placeholders, from
     * {@code capabilities.textDocument.completion.completionItem.snippetSupport}.
     *
     * <p>False unless it said so, which is the protocol's default and not a guess: a client sent a
     * snippet it did not ask for gets the placeholders in its buffer as characters. Nothing else the
     * client declares is read — what this server answers does not depend on it — so this is the one
     * thing asked of the handshake beyond where the workspace is.
     */
    private static boolean snippetSupportAsked(JsonNode params) {
        JsonNode at = params == null ? null : params.get("capabilities");
        for (String field : List.of("textDocument", "completion", "completionItem",
                "snippetSupport")) {
            if (at == null || at.isNull()) {
                return false;
            }
            at = at.get(field);
        }
        return at != null && at.isBoolean() && at.asBoolean();
    }

    /**
     * How much of what the rows cover this client asked to be told, from
     * {@code initializationOptions.souther.adequacy}: {@code off}, {@code witness} or {@code all}.
     *
     * <p>Off unless asked, and one setting rather than one per measure. What separates them is what
     * they cost: {@code witness} reads what the compile already ran, and {@code all} generates a
     * second set of classes and runs every row again — on every save, in an editor. Nothing that
     * costs that should arrive by default.
     */
    private static Adequacy.Asked adequacyAsked(JsonNode params) {
        JsonNode options = params.get("initializationOptions");
        JsonNode souther = options == null ? null : options.get("souther");
        JsonNode asked = souther == null ? null : souther.get("adequacy");
        if (asked == null || asked.isNull()) {
            return Adequacy.Asked.NOTHING;
        }
        return switch (asked.asString()) {
            case "witness" -> Adequacy.Asked.reportOnly(Adequacy.Level.WITNESS);
            case "all" -> Adequacy.Asked.reportOnly(Adequacy.Level.ALL);
            default -> Adequacy.Asked.NOTHING;
        };
    }

    /** What the client is told it may call, drawn from the methods that are answered. */
    private Map<String, Object> initializeResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capabilities", LspMethod.serverCapabilities());
        result.put("serverInfo", Map.of("name", "souther-lsp", "version", "0.1.0"));
        return result;
    }

    // --- document symbols ---

    private List<Object> documentSymbols(JsonNode params) {
        String uri = InboundDecoders.decode(InboundDecoders.DOC_REF, params)
                .map(Params.DocRef::uri).orElse(null);
        String text = uri == null ? null : documents.get(uri);
        if (text == null) {
            return List.of();
        }
        List<Object> out = new ArrayList<>();
        for (DocumentSymbol s : analyzer.documentSymbols(text)) {
            out.add(symbolJson(s));
        }
        return out;
    }

    private Object symbolJson(DocumentSymbol s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.name());
        m.put("kind", s.kind());
        m.put("range", rangeJson(s.range()));
        m.put("selectionRange", rangeJson(s.selectionRange()));
        if (!s.children().isEmpty()) {
            List<Object> children = new ArrayList<>();
            for (DocumentSymbol child : s.children()) {
                children.add(symbolJson(child));
            }
            m.put("children", children);
        }
        return m;
    }

    // --- hover / definition ---

    private Object hover(JsonNode params) {
        Params.PositionParams p = InboundDecoders.decode(InboundDecoders.POSITION_PARAMS, params)
                .orElse(null);
        String text = p == null ? null : documents.get(p.uri());
        if (text == null) {
            return null;
        }
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        Hover h = analyzer.hover(p.uri(), text, p.position(), graph).orElse(null);
        if (h == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contents", Map.of("kind", "markdown", "value", h.contents()));
        m.put("range", rangeJson(h.range()));
        return m;
    }

    private Object definition(JsonNode params) {
        Params.PositionParams p = InboundDecoders.decode(InboundDecoders.POSITION_PARAMS, params)
                .orElse(null);
        if (p == null || documents.get(p.uri()) == null) {
            return null;
        }
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        return analyzer.definition(p.uri(), p.position(), graph)
                .<Object>map(loc -> Map.of("uri", loc.uri(), "range", rangeJson(loc.range())))
                .orElse(null);
    }

    private Object references(JsonNode params) {
        Params.PositionParams p = InboundDecoders.decode(InboundDecoders.POSITION_PARAMS, params)
                .orElse(null);
        if (p == null || documents.get(p.uri()) == null) {
            return List.of();
        }
        boolean includeDeclaration = includeDeclaration(params);
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        List<Object> out = new ArrayList<>();
        for (Location loc : analyzer.references(p.uri(), p.position(), graph, includeDeclaration)) {
            out.add(Map.of("uri", loc.uri(), "range", rangeJson(loc.range())));
        }
        return out;
    }

    /** The {@code context.includeDeclaration} flag of a references request; defaults to true. */
    private static boolean includeDeclaration(JsonNode params) {
        if (params == null) {
            return true;
        }
        JsonNode context = params.get("context");
        if (context == null || context.get("includeDeclaration") == null) {
            return true;
        }
        return context.get("includeDeclaration").asBoolean();
    }

    // --- completion ---

    /**
     * The names that may be written at the cursor.
     *
     * <p>Answered from the workspace snapshot, as every other question about a position is: what a
     * bare name reaches here is settled by this document's imports and by the modules around it, and
     * one document cannot say what its own import lines brought in without reading them a second
     * time — which is the rule that decides it, written twice.
     */
    private Object completion(JsonNode params) {
        Params.PositionParams p = InboundDecoders.decode(InboundDecoders.POSITION_PARAMS, params)
                .orElse(null);
        if (p == null || documents.get(p.uri()) == null) {
            return List.of();
        }
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        List<Object> items = new ArrayList<>();
        for (CompletionItem item : analyzer.completions(p.uri(), p.position(), graph)) {
            Map<String, Object> sent = new LinkedHashMap<>();
            sent.put("label", item.label());
            sent.put("kind", item.kind());
            // Omitted rather than sent as null for a name with no origin: a client renders an empty
            // detail as an empty line beside the label.
            if (item.detail() != null) {
                sent.put("detail", item.detail());
            }
            if (item.writes() != null) {
                sent.put("insertText", readsSnippets
                        ? Insertion.snippet(item.writes())
                        : Insertion.plain(item.writes()));
                if (readsSnippets) {
                    sent.put("insertTextFormat", Insertion.SNIPPET_FORMAT);
                }
            }
            items.add(sent);
        }
        return items;
    }

    // --- code lenses ---

    /**
     * The line above each behavior saying what its rows cover of it.
     *
     * <p>Answered from the workspace snapshot rather than from this document alone: a behavior's rows
     * are written across its module's source and any attached files, and one file cannot say what the
     * others cover. Empty unless the client asked to be measured, which it does through
     * {@code souther.adequacy} at initialization.
     */
    private Object codeLenses(JsonNode params) {
        String uri = InboundDecoders.decode(InboundDecoders.DOC_REF, params)
                .map(Params.DocRef::uri).orElse(null);
        if (uri == null || documents.get(uri) == null) {
            return List.of();
        }
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        List<Object> out = new ArrayList<>();
        for (CodeLens lens : analyzer.codeLenses(uri, graph)) {
            out.add(Map.of("range", rangeJson(lens.range()),
                    "command", Map.of("title", lens.title(), "command", "")));
        }
        return out;
    }

    /** Null where the cursor is in no call the server can say anything about, which the protocol
     *  reads as no help rather than as help with nothing in it. */
    private Object signatureHelp(JsonNode params) {
        Params.PositionParams p = InboundDecoders.decode(InboundDecoders.POSITION_PARAMS, params)
                .orElse(null);
        if (p == null || documents.get(p.uri()) == null) {
            return null;
        }
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        return analyzer.signatureHelp(p.uri(), p.position(), graph).<Object>map(help -> {
            List<Object> parameters = new ArrayList<>();
            for (String each : help.parameters()) {
                parameters.add(Map.of("label", each));
            }
            Map<String, Object> written = new LinkedHashMap<>();
            written.put("signatures",
                    List.of(Map.of("label", help.label(), "parameters", parameters)));
            written.put("activeSignature", 0);
            // Left out where the signature takes nothing. A mark is about one of the parameters, and
            // where there are none the protocol asks for none — writing a number there would be
            // writing about something that was not sent.
            help.active().ifPresent(at -> written.put("activeParameter", at));
            return written;
        }).orElse(null);
    }

    private Object workspaceSymbols(JsonNode params) {
        Params.Query p = InboundDecoders.decode(InboundDecoders.QUERY, params).orElse(null);
        if (p == null) {
            return List.of();
        }
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        List<Object> out = new ArrayList<>();
        for (WorkspaceSymbol symbol : analyzer.workspaceSymbols(p.query(), graph)) {
            out.add(Map.of("name", symbol.name(), "kind", symbol.kind(),
                    "location", Map.of("uri", symbol.location().uri(),
                            "range", rangeJson(symbol.location().range()))));
        }
        return out;
    }

    // --- what else here means this ---

    private Object documentHighlights(JsonNode params) {
        Params.PositionParams p = InboundDecoders.decode(InboundDecoders.POSITION_PARAMS, params)
                .orElse(null);
        if (p == null || documents.get(p.uri()) == null) {
            return List.of();
        }
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        List<Object> out = new ArrayList<>();
        for (DocumentHighlight highlight
                : analyzer.documentHighlights(p.uri(), p.position(), graph)) {
            out.add(Map.of("range", rangeJson(highlight.range()), "kind", highlight.kind()));
        }
        return out;
    }

    /**
     * One answer per place asked about, in the order they were asked, each nested outwards.
     *
     * <p>The protocol pairs a result with a position by where it sits in the list, so a place with
     * nothing written on it — a blank line, the space between two tokens — is answered rather than
     * left out. Left out, every place after it would be given another place's widening, which is a
     * wrong answer where dropping the one is merely no answer. What such a place is answered with is
     * itself: a range covering nothing, at the position, which widens to nothing because nothing is
     * there.
     *
     * <p>The widening is a chain rather than a list, so what the analyzer answers innermost first is
     * built up from the outside in — the widest is what has no parent.
     */
    private Object selectionRanges(JsonNode params) {
        Params.PositionsParams p = InboundDecoders.decode(InboundDecoders.POSITIONS_PARAMS, params)
                .orElse(null);
        if (p == null || documents.get(p.uri()) == null) {
            return List.of();
        }
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        List<Object> out = new ArrayList<>();
        for (Position at : p.positions()) {
            List<Range> widening = analyzer.selectionRanges(p.uri(), at, graph);
            Map<String, Object> nested = null;
            for (int i = widening.size() - 1; i >= 0; i--) {
                Map<String, Object> here = new LinkedHashMap<>();
                here.put("range", rangeJson(widening.get(i)));
                if (nested != null) {
                    here.put("parent", nested);
                }
                nested = here;
            }
            out.add(nested == null
                    ? Map.of("range", rangeJson(new Range(at, at)))
                    : nested);
        }
        return out;
    }

    // --- inlay hints ---

    private Object inlayHints(JsonNode params) {
        Params.RangeParams p = InboundDecoders.decode(InboundDecoders.DOC_RANGE, params)
                .orElse(null);
        if (p == null || documents.get(p.uri()) == null) {
            return List.of();
        }
        List<Object> out = new ArrayList<>();
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        for (InlayHint hint : analyzer.inlayHints(p.uri(), p.range(), graph)) {
            Map<String, Object> written = new LinkedHashMap<>();
            written.put("position", positionJson(hint.position()));
            written.put("label", hint.label());
            written.put("paddingLeft", hint.paddingLeft());
            if (hint.tooltip() != null) {
                written.put("tooltip", hint.tooltip());
            }
            out.add(written);
        }
        return out;
    }

    // --- code actions ---

    private Object codeActions(JsonNode params) {
        Params.RangeParams p = InboundDecoders.decode(InboundDecoders.DOC_RANGE, params)
                .orElse(null);
        String text = p == null ? null : documents.get(p.uri());
        // Only the range's diagnostics can be fixed, so with none in context there is usually nothing
        // to offer. Short-circuiting here avoids a recompile — the analyzer's fix lookup compiles the
        // file — on every lightbulb over clean code, which is the overwhelmingly common case. Where
        // adequacy is being measured there is one offer that is not about a diagnostic — the rows a
        // behavior does not cover — so the short cut is skipped, and only for a client that asked.
        if (text == null || (!hasContextDiagnostics(params) && !analyzer.measuring())) {
            return List.of();
        }
        List<Object> out = new ArrayList<>();
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        for (CodeAction a : analyzer.codeActions(p.uri(), text, p.range(), graph)) {
            out.add(written(a));
        }
        return out;
    }

    /**
     * One action as the protocol writes it: with its edit, or with what it takes to work one out.
     *
     * <p>An action with neither is what a client sees while it is deciding whether to show the
     * offer, and an action with both would be this server paying for an edit it was about to hand
     * over unasked.
     */
    private static Map<String, Object> written(CodeAction a) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("title", a.title());
        action.put("kind", "quickfix");
        switch (a) {
            case CodeAction.Applied applied -> action.put("edit", changes(applied.edit()));
            case CodeAction.Deferred deferred -> action.put("data",
                    Map.of("uri", deferred.uri(), "module", deferred.module(),
                            "behavior", deferred.behavior()));
        }
        return action;
    }

    /** One edit, as the property of an action that carries it. */
    private static Map<String, Object> changes(CodeAction.Edit edit) {
        return Map.of("changes",
                Map.of(edit.uri(), List.of(textEdit(edit.range(), edit.newText()))));
    }

    /**
     * The edit for an action somebody took.
     *
     * <p>The document is read again here rather than remembered from when the offer was made: an
     * editor asks what is available on every cursor move and resolves one of them much later, and
     * an edit composed against the older text would be written into source it was not composed for.
     *
     * <p>What comes back is what the client sent, with the one property it was sent without. A
     * resolve fills in what an action was missing and alters nothing else it carries — the data that
     * identifies the work among them — so the reply is built from the request rather than from a
     * fresh action, which would drop whatever this server did not think to write again.
     *
     * <p>An action that resolves to nothing comes back as it went in, with no edit. There is nothing
     * to write, and writing the notes instead would put a comment into somebody's source.
     */
    private Object codeActionResolve(JsonNode params) {
        if (params == null || params.get("data") == null) {
            return params;   // not one of ours to work out; hand it back untouched
        }
        JsonNode data = params.get("data");
        String uri = text(data, "uri");
        String module = text(data, "module");
        String behavior = text(data, "behavior");
        String title = text(params, "title");
        if (uri == null || module == null || behavior == null || title == null) {
            return params;
        }
        CodeAction.Edit edit = analyzer.resolve(
                new CodeAction.Deferred(title, uri, module, behavior), documents.get(uri),
                workspace.snapshot(documents.openDocuments()));
        if (edit == null) {
            return params;
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        params.properties().forEach(property ->
                resolved.put(property.getKey(), property.getValue()));
        resolved.put("edit", changes(edit));
        return resolved;
    }

    private static String text(JsonNode at, String field) {
        JsonNode found = at.get(field);
        return found == null || !found.isString() ? null : found.asString();
    }

    /** Whether the codeAction request carries any client-side diagnostics for its range. */
    private static boolean hasContextDiagnostics(JsonNode params) {
        if (params == null) {
            return false;
        }
        JsonNode context = params.get("context");
        if (context == null) {
            return false;
        }
        JsonNode diagnostics = context.get("diagnostics");
        return diagnostics != null && diagnostics.isArray() && !diagnostics.isEmpty();
    }

    // --- rename ---

    /** A {@code WorkspaceEdit} renaming the name under the cursor everywhere it is used,
     * or {@code null} when the new name is not a legal identifier or the cursor is not on a
     * renameable symbol — the client then reports the rename as unavailable. */
    private Object rename(JsonNode params) {
        Params.RenameParams p = InboundDecoders.decode(InboundDecoders.RENAME, params).orElse(null);
        if (p == null || documents.get(p.uri()) == null || !analyzer.isValidName(p.newName())) {
            return null;
        }
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        Map<String, List<souther.lsp.protocol.TextEdit>> edits =
                analyzer.renameEdits(p.uri(), p.position(), graph, p.newName());
        if (edits.isEmpty()) {
            return null;
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, List<souther.lsp.protocol.TextEdit>> e : edits.entrySet()) {
            List<Object> textEdits = new ArrayList<>();
            for (souther.lsp.protocol.TextEdit edit : e.getValue()) {
                // what to write comes with the place: a binding written as a field's own name is
                // renamed by naming the field it reads, not by writing over it
                textEdits.add(textEdit(edit.range(), edit.newText()));
            }
            changes.put(e.getKey(), textEdits);
        }
        return Map.of("changes", changes);
    }

    // --- formatting ---

    private Object formatting(JsonNode params) {
        String uri = InboundDecoders.decode(InboundDecoders.DOC_REF, params)
                .map(Params.DocRef::uri).orElse(null);
        String text = uri == null ? null : documents.get(uri);
        if (text == null) {
            return List.of();
        }
        return analyzer.format(text)
                .filter(formatted -> !formatted.equals(text))   // no edit when already canonical
                .<Object>map(formatted -> List.of(fullEdit(text, formatted)))
                .orElse(List.of());
    }

    /** A single {@code TextEdit} replacing the whole document with {@code formatted}. */
    private static Map<String, Object> fullEdit(String text, String formatted) {
        LineIndex lines = new LineIndex(text);
        int end = text.length();
        Range range = new Range(new Position(0, 0),
                new Position(lines.lspLine(end), lines.lspColumn(end)));
        return textEdit(range, formatted);
    }

    /** A {@code TextEdit}: replace {@code range} with {@code newText}. */
    private static Map<String, Object> textEdit(Range range, String newText) {
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("range", rangeJson(range));
        edit.put("newText", newText);
        return edit;
    }

    // --- semantic tokens ---

    private Object semanticTokens(JsonNode params) {
        String uri = InboundDecoders.decode(InboundDecoders.DOC_REF, params)
                .map(Params.DocRef::uri).orElse(null);
        String text = uri == null ? null : documents.get(uri);
        if (text == null) {
            return Map.of("data", List.of());
        }
        List<Integer> data = new ArrayList<>();
        for (int value : analyzer.semanticTokens(text)) {
            data.add(value);
        }
        return Map.of("data", data);
    }

    // --- diagnostics ---

    /**
     * Brings the published diagnostics up to date with what the documents now say, giving way to
     * anything that arrives while it runs.
     *
     * <p>Giving way costs nothing that was worth keeping. What a diagnose reaches is kept in the
     * compile's store — an answer of this revision is an answer of this revision, however the walk
     * that wanted it ended — so the diagnose that follows pays for what this one did not finish and
     * for nothing else.
     *
     * <p>A diagnose that could not be carried out at all is not asked for again. The same documents
     * would fail the same way, and a server retrying it would do nothing else for as long as they
     * stood.
     */
    private void diagnose() {
        diagnosticsAreStale = false;
        stopWhen = inbox::anyWaiting;
        try {
            publishAll();
        } catch (Abandoned _) {
            diagnosticsAreStale = true;
        } catch (RuntimeException | StackOverflowError _) {
            // nothing to publish and nobody to tell: a diagnose is not a request
        }
    }

    /**
     * Recomputes diagnostics for the whole workspace and publishes each open document's set — an edit
     * to one module can change what its importers report, so every open file is refreshed together.
     *
     * <p>Asked before each document whether what it is about to publish is still what the documents
     * say. It stops short of promising the published set is never behind: what a client is told is
     * decided here and read there, and nothing between the two is held. What it does hold is that a
     * set published behind is followed by one that is not, because the diagnose that gave way is
     * asked for again.
     */
    private void publishAll() {
        ModuleGraph graph = workspace.snapshot(documents.openDocuments());
        Map<String, List<LspDiagnostic>> byUri = analyzer.diagnostics(graph, workspace.modulePath());
        for (String uri : documents.uris()) {
            abandonment.stopIfAsked();
            publish(uri, byUri.getOrDefault(uri, List.of()));
        }
    }

    private void publish(String uri, List<LspDiagnostic> diagnostics) {
        List<Object> items = new ArrayList<>();
        for (LspDiagnostic d : diagnostics) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("range", rangeJson(d.range()));
            item.put("severity", d.severity());
            if (d.code() != null) {
                item.put("code", d.code());
            }
            item.put("source", "souther");
            item.put("message", d.message());
            if (!d.tags().isEmpty()) {
                item.put("tags", d.tags());
            }
            if (!d.related().isEmpty()) {
                List<Object> related = new ArrayList<>();
                for (LspDiagnostic.Related r : d.related()) {
                    related.add(Map.of("location",
                            Map.of("uri", r.uri(), "range", rangeJson(r.range())),
                            "message", r.message()));
                }
                item.put("relatedInformation", related);
            }
            items.add(item);
        }
        notify("textDocument/publishDiagnostics", Map.of("uri", uri, "diagnostics", items));
    }

    private void clearDiagnostics(String uri) {
        notify("textDocument/publishDiagnostics", Map.of("uri", uri, "diagnostics", List.of()));
    }

    private static Map<String, Object> rangeJson(Range r) {
        return Map.of("start", positionJson(r.start()), "end", positionJson(r.end()));
    }

    private static Map<String, Object> positionJson(Position p) {
        return Map.of("line", p.line(), "character", p.character());
    }

    // --- JSON-RPC framing of responses / notifications ---

    private void respond(JsonNode id, Object result) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("result", result);
        conn.write(JSON.writeValueAsString(message));
    }

    private void respondError(JsonNode id, int code, String text) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("error", Map.of("code", code, "message", text));
        conn.write(JSON.writeValueAsString(message));
    }

    private void notify(String method, Object params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("method", method);
        message.put("params", params);
        conn.write(JSON.writeValueAsString(message));
    }

    /** Announces the methods no capability carries — asking the client to watch the workspace's
     * {@code .sou} files and report on-disk changes via {@code workspace/didChangeWatchedFiles}, so
     * the cached disk scan is dropped when a file is created, edited, or deleted outside the editor
     * rather than relying on the client watching by default. What is registered comes from the same
     * methods the capabilities do, so this and the handshake describe one server between them. The
     * registration response is a no-op here (dropped by {@link #run}); a client without dynamic
     * registration simply ignores the request. */
    private void registerDynamically() {
        List<Map<String, Object>> registrations = LspMethod.dynamicRegistrations();
        if (registrations.isEmpty()) {
            return;
        }
        sendRequest("client/registerCapability", Map.of("registrations", registrations));
    }

    private void sendRequest(String method, Object params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", "souther-" + nextRequestId++);
        message.put("method", method);
        message.put("params", params);
        conn.write(JSON.writeValueAsString(message));
    }
}
