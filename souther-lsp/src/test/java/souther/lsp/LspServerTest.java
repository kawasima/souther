package souther.lsp;

import souther.lsp.transport.MessageConnection;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the server end to end over a connection: an initialize handshake and an opened document with
 * a syntax error, checking the capabilities response and the published diagnostics.
 *
 * <p>What is asked and answered in one breath is handed over as a stream that ends. What is published
 * because the workspace was left alone for a moment is not: diagnostics are what this server does
 * with an idle moment, and a stream that is already at its end never leaves one. Those cases run
 * against a {@link Session}, which holds the connection open and waits for what comes back.
 */
class LspServerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void initializeAndDiagnosticsFlowOverTheConnection() {
        String uri = "file:///t.sou";
        try (Session session = new Session()) {
            session.send(
                    Session.message(1, "initialize", Map.of()),
                    Session.message(null, "initialized", Map.of()),
                    Session.message(null, "textDocument/didOpen", Map.of(
                            "textDocument", Map.of("uri", uri,
                                    "text", "module demo\ndata M = { name String }\n"))));   // missing `:`

            JsonNode initResult = session.await(Session.replyTo(1), "an answer to initialize");
            assertTrue(initResult.get("result").get("capabilities").has("textDocumentSync"));

            JsonNode publish = session.await(Session.publishedFor(uri), "diagnostics for the document");
            JsonNode diagnostics = publish.get("params").get("diagnostics");
            assertTrue(diagnostics.size() > 0, "expected at least one diagnostic");
            assertEquals("souther", diagnostics.get(0).get("source").asString());
        }
    }

    @Test
    void diagnosticsResolveAcrossModulesFromTheWorkspaceRoot() throws Exception {
        Path dir = Files.createTempDirectory("lsp");
        Files.writeString(dir.resolve("a.sou"), "module a exposing ( N )\ndata N = { v: Int }\n");
        String bText = "module b\nimport a ( N )\ndata M = { n: Int }\n"
                + "behavior f : (x: M) -> M\nlet f (x) = x\n"
                + "example f\n  | (M { n = 1 }) -> M { n = 2 }\n";   // failing example (E1905)
        Path b = dir.resolve("b.sou");
        Files.writeString(b, bText);
        String bUri = b.toUri().toString();

        try (Session session = new Session()) {
            session.send(
                    Session.message(1, "initialize", Map.of("rootUri", dir.toUri().toString())),
                    Session.message(null, "initialized", Map.of()),
                    Session.message(null, "textDocument/didOpen", Map.of(
                            "textDocument", Map.of("uri", bUri, "text", bText))));

            // the importing module used to bail; now it resolves a against the root and reports E1905
            JsonNode publish = session.await(Session.publishedFor(bUri), "diagnostics for b");
            JsonNode diagnostics = publish.get("params").get("diagnostics");
            assertTrue(diagnostics.size() > 0, "the cross-module compile surfaces the failing example");
            assertEquals("E1905", diagnostics.get(0).get("code").asString());
        }
    }

    @Test
    void capabilitiesAdvertiseReferences() {
        byte[] input = frames(message(1, "initialize", Map.of()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        JsonNode caps = readFrames(out.toByteArray()).stream()
                .filter(m -> m.has("id") && m.get("id").isNumber() && m.get("id").asInt() == 1)
                .findFirst().orElseThrow()
                .get("result").get("capabilities");
        assertTrue(caps.get("referencesProvider").asBoolean(), "references is advertised");
    }

    @Test
    void registersAFileWatcherForSouSourcesOnInitialized() {
        byte[] input = frames(
                message(1, "initialize", Map.of()),
                message(null, "initialized", Map.of()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        JsonNode registration = readFrames(out.toByteArray()).stream()
                .filter(m -> m.has("method")
                        && m.get("method").asString().equals("client/registerCapability"))
                .findFirst().orElseThrow(() -> new AssertionError("expected a client/registerCapability"))
                .get("params").get("registrations").get(0);

        assertEquals("workspace/didChangeWatchedFiles", registration.get("method").asString());
        String glob = registration.get("registerOptions").get("watchers").get(0)
                .get("globPattern").asString();
        assertTrue(glob.contains("*.sou"), "watches Souther sources: " + glob);
    }

    @Test
    void capabilitiesAdvertiseFormatting() {
        byte[] input = frames(message(1, "initialize", Map.of()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        JsonNode caps = readFrames(out.toByteArray()).stream()
                .filter(m -> m.has("id") && m.get("id").isNumber() && m.get("id").asInt() == 1)
                .findFirst().orElseThrow()
                .get("result").get("capabilities");
        assertTrue(caps.get("documentFormattingProvider").asBoolean(), "formatting is advertised");
        assertTrue(caps.get("renameProvider").asBoolean(), "rename is advertised");
        assertTrue(caps.has("completionProvider"), "completion is advertised");
        assertTrue(caps.has("codeActionProvider"), "code actions are advertised");
    }

    @Test
    void renameReturnsAWorkspaceEditForEveryUse() {
        String uri = "file:///r.sou";
        String text = "module demo\ndata X = { a: Int }\nbehavior f : (x: X) -> X\nlet f (x) = x\n";
        byte[] input = frames(
                message(1, "initialize", Map.of()),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", uri, "text", text))),
                message(2, "textDocument/rename", Map.of(
                        "textDocument", Map.of("uri", uri),
                        "position", Map.of("line", 1, "character", 5),   // on `data X`
                        "newName", "Y")));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        JsonNode changes = responseFor(readFrames(out.toByteArray()), 2).get("changes");
        JsonNode edits = changes.get(uri);
        assertEquals(3, edits.size(), "the declaration plus its two type uses");
        assertEquals("Y", edits.get(0).get("newText").asString());
    }

    @Test
    void formattingReturnsAFullDocumentEdit() {
        String uri = "file:///f.sou";
        byte[] input = frames(
                message(1, "initialize", Map.of()),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", uri, "text", "module demo\ndata X = { a: Int }\n"))),
                message(2, "textDocument/formatting", Map.of(
                        "textDocument", Map.of("uri", uri),
                        "options", Map.of("tabSize", 4, "insertSpaces", true))));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        JsonNode edits = responseFor(readFrames(out.toByteArray()), 2);
        assertEquals(1, edits.size(), "a single full-document edit");
        String newText = edits.get(0).get("newText").asString();
        assertTrue(newText.contains("{ a: Int"), newText);
        JsonNode start = edits.get(0).get("range").get("start");
        assertEquals(0, start.get("line").asInt());
        assertEquals(0, start.get("character").asInt());
    }

    @Test
    void completionReturnsNameCandidates() {
        String uri = "file:///c.sou";
        String text = "module demo\ndata Thing = { v: Int }\nbehavior f : (x: Thing) -> Thing\nlet f (x) = x\n";
        byte[] input = frames(
                message(1, "initialize", Map.of()),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", uri, "text", text))),
                message(2, "textDocument/completion", Map.of(
                        "textDocument", Map.of("uri", uri),
                        "position", Map.of("line", 3, "character", 12))));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        JsonNode items = responseFor(readFrames(out.toByteArray()), 2);
        boolean hasType = false;
        boolean hasKeyword = false;
        for (JsonNode it : items) {
            String label = it.get("label").asString();
            if (label.equals("Thing")) {
                hasType = true;
            }
            if (label.equals("data")) {
                hasKeyword = true;
            }
        }
        assertTrue(hasType, "the data type is offered");
        assertTrue(hasKeyword, "a keyword is offered");
    }

    @Test
    void codeActionOffersASpellingQuickFix() {
        String uri = "file:///q.sou";
        String text = "module demo\nbehavior f : (value: Int) -> Int\nlet f (value) = valuee\n";
        byte[] input = frames(
                message(1, "initialize", Map.of()),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", uri, "text", text))),
                message(2, "textDocument/codeAction", Map.of(
                        "textDocument", Map.of("uri", uri),
                        "range", Map.of("start", Map.of("line", 2, "character", 16),
                                "end", Map.of("line", 2, "character", 22)),
                        "context", Map.of("diagnostics", List.of(Map.of(
                                "range", Map.of("start", Map.of("line", 2, "character", 16),
                                        "end", Map.of("line", 2, "character", 22)),
                                "message", "unknown identifier"))))));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        JsonNode actions = responseFor(readFrames(out.toByteArray()), 2);
        assertEquals(1, actions.size(), actions.toString());
        assertEquals("quickfix", actions.get(0).get("kind").asString());
        String newText = actions.get(0).get("edit").get("changes").get(uri).get(0).get("newText").asString();
        assertEquals("value", newText);
    }

    /**
     * A client sends back the diagnostics it holds, which are the ones it was last published, and
     * the document may have been edited since. So what it sends says there may be something to offer
     * here and never what: the offer is worked out from the text this server holds now.
     */
    @Test
    void aDiagnosticTheDocumentNoLongerHasOffersNothing() {
        String uri = "file:///q3.sou";
        String typo = "module demo\nbehavior f : (value: Int) -> Int\nlet f (value) = valuee\n";
        String fixed = "module demo\nbehavior f : (value: Int) -> Int\nlet f (value) = value\n";
        Map<String, Object> changed = new LinkedHashMap<>();
        changed.put("textDocument", Map.of("uri", uri));
        changed.put("contentChanges", List.of(Map.of("text", fixed)));
        byte[] input = frames(
                message(1, "initialize", Map.of()),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", uri, "text", typo))),
                message(null, "textDocument/didChange", changed),
                message(2, "textDocument/codeAction", Map.of(
                        "textDocument", Map.of("uri", uri),
                        "range", Map.of("start", Map.of("line", 2, "character", 16),
                                "end", Map.of("line", 2, "character", 21)),
                        // what the client still holds from before the edit
                        "context", Map.of("diagnostics", List.of(Map.of(
                                "range", Map.of("start", Map.of("line", 2, "character", 16),
                                        "end", Map.of("line", 2, "character", 22)),
                                "message", "unknown identifier"))))));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        JsonNode actions = responseFor(readFrames(out.toByteArray()), 2);
        assertEquals(0, actions.size(),
                () -> "the name is spelled right now: " + actions);
    }

    @Test
    void codeActionWithNoContextDiagnosticsOffersNothing() {
        String uri = "file:///q2.sou";
        String text = "module demo\nbehavior f : (value: Int) -> Int\nlet f (value) = valuee\n";
        byte[] input = frames(
                message(1, "initialize", Map.of()),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", uri, "text", text))),
                message(2, "textDocument/codeAction", Map.of(
                        "textDocument", Map.of("uri", uri),
                        "range", Map.of("start", Map.of("line", 2, "character", 16),
                                "end", Map.of("line", 2, "character", 22)),
                        "context", Map.of("diagnostics", List.of()))));   // client reports none in range

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        JsonNode actions = responseFor(readFrames(out.toByteArray()), 2);
        assertEquals(0, actions.size(), "with no context diagnostics the server skips the compile");
    }

    /**
     * A module with a type, a use of it, and a behavior, so the navigation requests below have
     * somewhere to go.
     *
     * <p>`data X` is at line 1 character 5; the `X` in the signature is at line 2 character 17.
     */
    private static final String NAVIGABLE =
            "module demo\ndata X = { a: Int }\nbehavior f : (x: X) -> X\nlet f (x) = x\n";

    /** Opens {@code NAVIGABLE} and asks one request of it, answering with that request's result. */
    private static JsonNode askOf(String uri, String method, Map<String, Object> params) {
        byte[] input = frames(
                message(1, "initialize", Map.of()),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", uri, "text", NAVIGABLE))),
                message(2, method, params));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();
        return responseFor(readFrames(out.toByteArray()), 2);
    }

    @Test
    void documentSymbolsNameWhatTheModuleDeclares() {
        String uri = "file:///s.sou";
        JsonNode symbols = askOf(uri, "textDocument/documentSymbol",
                Map.of("textDocument", Map.of("uri", uri)));

        List<String> names = new ArrayList<>();
        for (JsonNode symbol : symbols) {
            names.add(symbol.get("name").asString());
        }
        assertTrue(names.contains("X"), names.toString());
        assertTrue(names.contains("f"), names.toString());
    }

    @Test
    void semanticTokensComeBackAsFiveNumbersEach() {
        String uri = "file:///t.sou";
        JsonNode data = askOf(uri, "textDocument/semanticTokens/full",
                Map.of("textDocument", Map.of("uri", uri))).get("data");

        assertTrue(data.size() > 0, "the module is highlighted");
        assertEquals(0, data.size() % 5, "line, start, length, type, modifiers: " + data);
    }

    @Test
    void hoverOverATypeShowsHowItIsDeclared() {
        String uri = "file:///h.sou";
        JsonNode hover = askOf(uri, "textDocument/hover", Map.of(
                "textDocument", Map.of("uri", uri),
                "position", Map.of("line", 1, "character", 5)));   // on `data X`

        assertNotNull(hover, "a declaration says something about itself");
        String contents = hover.get("contents").get("value").asString();
        assertTrue(contents.contains("data X"), contents);
    }

    @Test
    void definitionGoesFromAUseToTheDeclaration() {
        String uri = "file:///d.sou";
        JsonNode location = askOf(uri, "textDocument/definition", Map.of(
                "textDocument", Map.of("uri", uri),
                "position", Map.of("line", 2, "character", 17)));   // the `X` in the signature

        assertNotNull(location, "the use resolves");
        assertEquals(uri, location.get("uri").asString());
        assertEquals(1, location.get("range").get("start").get("line").asInt(),
                "the `data X` line, zero-based");
    }

    @Test
    void referencesAnswerWithTheDeclarationAndItsUses() {
        String uri = "file:///r2.sou";
        JsonNode locations = askOf(uri, "textDocument/references", Map.of(
                "textDocument", Map.of("uri", uri),
                "position", Map.of("line", 1, "character", 5),   // on `data X`
                "context", Map.of("includeDeclaration", true)));

        assertEquals(3, locations.size(),
                "the declaration plus its two type uses: " + locations);
    }

    @Test
    void aCodeLensIsDrawnOverABehaviorTheClientAskedToMeasure() throws Exception {
        Path dir = Files.createTempDirectory("lens");
        String text = """
                module demo

                data Draft = { cost: Int }
                data Submitted = { cost: Int }

                behavior submit : (request: Draft) -> Submitted
                let submit (request) = Submitted { cost = request.cost }

                example submit
                    | (Draft { cost = 1 }) -> Submitted { cost = 1 }
                """;
        Path source = dir.resolve("demo.sou");
        Files.writeString(source, text);
        String uri = source.toUri().toString();

        byte[] input = frames(
                message(1, "initialize", Map.of(
                        "rootUri", dir.toUri().toString(),
                        "initializationOptions", Map.of("souther", Map.of("adequacy", "witness")))),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", uri, "text", text))),
                message(2, "textDocument/codeLens", Map.of("textDocument", Map.of("uri", uri))));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        JsonNode lenses = responseFor(readFrames(out.toByteArray()), 2);
        assertEquals(1, lenses.size(), "one behavior, so one line: " + lenses);
        assertEquals(5, lenses.get(0).get("range").get("start").get("line").asInt(),
                "the `behavior` line, zero-based");
        assertTrue(lenses.get(0).get("command").get("title").asString().contains("row"),
                lenses.get(0).toString());
    }

    /**
     * A resolve fills in what the action was sent without, and alters nothing it was sent with.
     *
     * <p>The data is what identifies the work, and the client hands it back for exactly that. A
     * reply built as a fresh action rather than from the request drops whatever the server did not
     * think to write again — which is the data first, and then anything the client itself added.
     */
    @Test
    void resolvingAnActionKeepsWhatTheClientSentAndAddsTheEdit() throws Exception {
        Path dir = Files.createTempDirectory("resolve");
        String text = """
                module example.trip

                data Amount = Int
                    invariant value >= 0

                data Draft = { cost: Amount }
                data Submitted = { cost: Amount }
                data Waiting = { cost: Amount }

                behavior submit : (request: Draft) -> Submitted | Waiting
                    constructs Submitted, Waiting

                let submit (request) = {
                    guard request.cost.value <= 100 else Waiting { cost = request.cost }
                    Submitted { cost = request.cost }
                }

                example submit
                    | (Draft { cost = Amount(50) }) -> Submitted { cost = Amount(50) }
                """;
        Path source = dir.resolve("demo.sou");
        Files.writeString(source, text);
        String uri = source.toUri().toString();

        byte[] input = frames(
                message(1, "initialize", Map.of(
                        "rootUri", dir.toUri().toString(),
                        "initializationOptions", Map.of("souther", Map.of("adequacy", "witness")),
                        "capabilities", Map.of("textDocument", Map.of("codeAction", Map.of(
                                "dataSupport", true,
                                "resolveSupport", Map.of("properties", List.of("edit"))))))),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", uri, "text", text))),
                message(2, "textDocument/codeAction", Map.of(
                        "textDocument", Map.of("uri", uri),
                        "range", Map.of("start", Map.of("line", 9, "character", 0),
                                "end", Map.of("line", 9, "character", 0)),
                        "context", Map.of("diagnostics", List.of()))));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();
        JsonNode offered = responseFor(readFrames(out.toByteArray()), 2);
        assertEquals(1, offered.size(), offered.toString());
        JsonNode action = offered.get(0);
        assertFalse(action.has("edit"), "the offer is made without one: " + action);
        assertTrue(action.has("data"), "and with what identifies the work: " + action);

        // The same action back, as a client that took it sends it.
        Map<String, Object> taken = new LinkedHashMap<>();
        action.properties().forEach(p -> taken.put(p.getKey(), p.getValue()));
        byte[] second = frames(
                message(1, "initialize", Map.of(
                        "rootUri", dir.toUri().toString(),
                        "initializationOptions", Map.of("souther", Map.of("adequacy", "witness")),
                        "capabilities", Map.of("textDocument", Map.of("codeAction", Map.of(
                                "dataSupport", true,
                                "resolveSupport", Map.of("properties", List.of("edit"))))))),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", uri, "text", text))),
                message(3, "codeAction/resolve", taken));

        ByteArrayOutputStream back = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(second), back)).run();
        JsonNode resolved = responseFor(readFrames(back.toByteArray()), 3);

        assertEquals(action.get("title"), resolved.get("title"), "the title is the client's");
        assertEquals(action.get("kind"), resolved.get("kind"), "and so is the kind");
        assertEquals(action.get("data"), resolved.get("data"),
                "and the data it handed back is the data it gets back");
        assertTrue(resolved.has("edit"), "with the one property it asked to be filled in");
        assertTrue(resolved.get("edit").get("changes").get(uri).get(0).get("newText").asString()
                .contains("-> <?>"), resolved.toString());
    }

    // --- helpers: build and read framed JSON-RPC messages ---

    /** The {@code result} array of the response to request {@code id}. */
    private static JsonNode responseFor(List<JsonNode> messages, int id) {
        return messages.stream()
                .filter(m -> m.has("result") && m.has("id") && m.get("id").isNumber()
                        && m.get("id").asInt() == id)
                .findFirst().orElseThrow(() -> new AssertionError("no response for id " + id))
                .get("result");
    }

    private static String message(Integer id, String method, Object params) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jsonrpc", "2.0");
        if (id != null) {
            m.put("id", id);
        }
        m.put("method", method);
        m.put("params", params);
        return JSON.writeValueAsString(m);
    }

    private static byte[] frames(String... messages) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MessageConnection writer = new MessageConnection(new ByteArrayInputStream(new byte[0]), buffer);
        for (String m : messages) {
            writer.write(m);
        }
        return buffer.toByteArray();
    }

    private static List<JsonNode> readFrames(byte[] bytes) {
        MessageConnection reader = new MessageConnection(
                new ByteArrayInputStream(bytes), OutputStream.nullOutputStream());
        List<JsonNode> out = new ArrayList<>();
        String s;
        while ((s = reader.read()) != null) {
            out.add(JSON.readTree(s));
        }
        return out;
    }
}
