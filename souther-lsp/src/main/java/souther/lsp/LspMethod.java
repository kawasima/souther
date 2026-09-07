package souther.lsp;

import souther.lsp.analysis.Analyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every request and notification this server answers, and what tells a client about each.
 *
 * <p>The set lives here rather than in the case labels of a switch because the rest of the server
 * has to read it. What the handshake advertises and what is registered after it are both built from
 * these values, so the two cannot say something the server does not answer; and a method cannot be
 * answered without being one of these values, because {@link LspServer} reaches its handlers only
 * through {@link #of}. A method nobody was told about is then not something to test for — it is
 * something there is no way to write.
 *
 * <p>What a method is advertised by is not a name derived from the method's own: three notifications
 * share one capability, one method is registered after the handshake instead, and the lifecycle is
 * announced by nothing. Each of those is a value here rather than a row missing from a table.
 *
 * <p>Answered is what this is a set of. {@code $/cancelRequest} is not among them and is not missing
 * from them: it is not answered, it says something about another message while that message is being
 * worked on, and it is acted on where the frames are read.
 */
public enum LspMethod {

    INITIALIZE("initialize", Announced.LIFECYCLE),
    INITIALIZED("initialized", Announced.LIFECYCLE),
    SET_TRACE("$/setTrace", Announced.PROTOCOL_DEFINED),
    DID_CHANGE_CONFIGURATION("workspace/didChangeConfiguration", Announced.PROTOCOL_DEFINED),
    DID_OPEN("textDocument/didOpen", Announced.TEXT_DOCUMENT_SYNC),
    DID_CHANGE("textDocument/didChange", Announced.TEXT_DOCUMENT_SYNC),
    DID_CLOSE("textDocument/didClose", Announced.TEXT_DOCUMENT_SYNC),
    DID_CHANGE_WATCHED_FILES("workspace/didChangeWatchedFiles", Announced.SOU_FILE_WATCHER),
    DOCUMENT_SYMBOL("textDocument/documentSymbol",
            new Advertisement.StaticCapability("documentSymbolProvider", true)),
    SEMANTIC_TOKENS_FULL("textDocument/semanticTokens/full", Announced.SEMANTIC_TOKENS),
    HOVER("textDocument/hover", new Advertisement.StaticCapability("hoverProvider", true)),
    DEFINITION("textDocument/definition", new Advertisement.StaticCapability("definitionProvider", true)),
    REFERENCES("textDocument/references", new Advertisement.StaticCapability("referencesProvider", true)),
    // A `.` opens the list, because what may be written after one is a different set from what may
    // be written anywhere else and an author who has to ask for it does not know that.
    COMPLETION("textDocument/completion",
            new Advertisement.StaticCapability("completionProvider",
                    Map.of("triggerCharacters", List.of(".")))),
    INLAY_HINT("textDocument/inlayHint",
            new Advertisement.StaticCapability("inlayHintProvider", true)),
    DOCUMENT_HIGHLIGHT("textDocument/documentHighlight",
            new Advertisement.StaticCapability("documentHighlightProvider", true)),
    SELECTION_RANGE("textDocument/selectionRange",
            new Advertisement.StaticCapability("selectionRangeProvider", true)),
    WORKSPACE_SYMBOL("workspace/symbol",
            new Advertisement.StaticCapability("workspaceSymbolProvider", true)),
    // `(` and `,` open it: a signature is wanted when an argument is begun, and every argument is
    // begun by one of the two.
    SIGNATURE_HELP("textDocument/signatureHelp",
            new Advertisement.StaticCapability("signatureHelpProvider",
                    Map.of("triggerCharacters", List.of("(", ",")))),
    CODE_ACTION("textDocument/codeAction",
            new Advertisement.StaticCapability("codeActionProvider",
                    Map.of("resolveProvider", true))),
    CODE_ACTION_RESOLVE("codeAction/resolve", Announced.UNDER_CODE_ACTION),
    CODE_LENS("textDocument/codeLens",
            new Advertisement.StaticCapability("codeLensProvider", Map.of("resolveProvider", false))),
    RENAME("textDocument/rename", new Advertisement.StaticCapability("renameProvider", true)),
    FORMATTING("textDocument/formatting",
            new Advertisement.StaticCapability("documentFormattingProvider", true)),
    SHUTDOWN("shutdown", Announced.LIFECYCLE),
    EXIT("exit", Announced.LIFECYCLE);

    /** Advertisements shared by more than one method, or whose value is built rather than written. */
    private static final class Announced {

        static final Advertisement LIFECYCLE =
                new Advertisement.None(Advertisement.Reason.LIFECYCLE);

        static final Advertisement PROTOCOL_DEFINED =
                new Advertisement.None(Advertisement.Reason.UNADVERTISED_PROTOCOL_METHOD);

        static final Advertisement UNDER_CODE_ACTION =
                new Advertisement.None(Advertisement.Reason.UNDER_ANOTHER_METHODS_CAPABILITY);

        /** 1 = full document sync. Opening, changing and closing a document are the three
         * notifications this one capability admits, and none of them names it. */
        static final Advertisement TEXT_DOCUMENT_SYNC =
                new Advertisement.StaticCapability("textDocumentSync", 1);

        static final Advertisement SEMANTIC_TOKENS =
                new Advertisement.StaticCapability("semanticTokensProvider", semanticTokens());

        static final Advertisement SOU_FILE_WATCHER = new Advertisement.DynamicRegistration(
                "souther-sou-watcher",
                Map.of("watchers", List.of(Map.of("globPattern", "**/*.sou"))));

        private static Map<String, Object> semanticTokens() {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("legend", Map.of("tokenTypes", Analyzer.TOKEN_TYPES,
                    "tokenModifiers", List.of()));
            options.put("full", true);
            return options;
        }

        private Announced() {
        }
    }

    /**
     * Everything derived from the table is derived once, as the class is loaded.
     *
     * <p>A table that contradicts itself is not a request's failure to be reported to whoever
     * happened to ask first — it is this server being unable to say what it does, and it says so
     * before it has answered anything.
     */
    private static final Map<String, LspMethod> BY_WIRE = byWire();

    private static final Map<String, Object> CAPABILITIES = capabilities();

    private static final List<Map<String, Object>> REGISTRATIONS = registrations();

    private final String wire;
    private final Advertisement advertisement;

    LspMethod(String wire, Advertisement advertisement) {
        this.wire = wire;
        this.advertisement = advertisement;
    }

    /** How this method is spelled in a JSON-RPC {@code method} field. */
    public String wire() {
        return wire;
    }

    /** What tells a client this method can be called. */
    public Advertisement advertisement() {
        return advertisement;
    }

    /** The method a client named, or empty when the server answers no such method. */
    public static Optional<LspMethod> of(String wire) {
        return Optional.ofNullable(BY_WIRE.get(wire));
    }

    /** The {@code capabilities} object of the initialize result. */
    public static Map<String, Object> serverCapabilities() {
        return CAPABILITIES;
    }

    /**
     * The registrations to send once the client reports {@code initialized}, in the shape the
     * protocol's {@code Registration} takes.
     */
    public static List<Map<String, Object>> dynamicRegistrations() {
        return REGISTRATIONS;
    }

    /**
     * The capabilities, built from the methods rather than beside them, so a client is told about
     * what is answered and nothing else.
     *
     * <p>One field may announce several methods, and then it is the one advertisement all of them
     * hold — not one written out again under the same key. Two methods reaching the same field by
     * different advertisements are refused even where both spell the same value: the field would
     * announce one of them, and the other would be answered without being announced, which is the
     * whole of what a capability rules out.
     */
    private static Map<String, Object> capabilities() {
        Map<String, LspMethod> announcedBy = new LinkedHashMap<>();
        Map<String, Object> capabilities = new LinkedHashMap<>();
        for (LspMethod method : values()) {
            if (!(method.advertisement instanceof Advertisement.StaticCapability capability)) {
                continue;
            }
            LspMethod first = announcedBy.putIfAbsent(capability.key(), method);
            if (first == null) {
                capabilities.put(capability.key(), capability.value());
            } else if (first.advertisement != method.advertisement) {
                throw new IllegalStateException(first + " and " + method + " both advertise "
                        + capability.key() + " without sharing one advertisement");
            }
        }
        return Collections.unmodifiableMap(capabilities);
    }

    /** The registrations, whose {@code method} is the method holding each one, so what is registered
     * and what is answered cannot name different things. */
    private static List<Map<String, Object>> registrations() {
        List<Map<String, Object>> registrations = new ArrayList<>();
        for (LspMethod method : values()) {
            if (!(method.advertisement instanceof Advertisement.DynamicRegistration registration)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", registration.id());
            entry.put("method", method.wire);
            entry.put("registerOptions", registration.registerOptions());
            registrations.add(Collections.unmodifiableMap(entry));
        }
        return Collections.unmodifiableList(registrations);
    }

    /**
     * Indexes the methods by their spelling, refusing two that are spelled the same.
     *
     * <p>A repeated spelling would leave one of the two unreachable and say nothing about it, and
     * the one left out would be the one that keeps its advertisement — a capability announcing a
     * method that answers as something else.
     */
    private static Map<String, LspMethod> byWire() {
        Map<String, LspMethod> index = new LinkedHashMap<>();
        for (LspMethod method : values()) {
            LspMethod clash = index.put(method.wire, method);
            if (clash != null) {
                throw new IllegalStateException(
                        clash + " and " + method + " are both spelled " + method.wire);
            }
        }
        return index;
    }
}
