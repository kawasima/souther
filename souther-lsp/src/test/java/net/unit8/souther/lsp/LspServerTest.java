package net.unit8.souther.lsp;

import net.unit8.souther.lsp.transport.MessageConnection;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the server end to end over an in-memory connection: an initialize handshake and an opened
 * document with a syntax error, checking the capabilities response and the published diagnostics. */
class LspServerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void initializeAndDiagnosticsFlowOverTheConnection() {
        String didOpen = message(null, "textDocument/didOpen", Map.of(
                "textDocument", Map.of("uri", "file:///t.sou",
                        "text", "module demo\ndata M = { name String }\n")));   // missing `:`

        byte[] input = frames(
                message(1, "initialize", Map.of()),
                message(null, "initialized", Map.of()),
                didOpen);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        List<JsonNode> messages = readFrames(out.toByteArray());

        JsonNode initResult = messages.stream()
                .filter(m -> m.has("id") && m.get("id").asInt() == 1).findFirst().orElseThrow();
        assertTrue(initResult.get("result").get("capabilities").has("textDocumentSync"));

        JsonNode publish = messages.stream()
                .filter(m -> m.has("method")
                        && m.get("method").asString().equals("textDocument/publishDiagnostics"))
                .findFirst().orElse(null);
        assertNotNull(publish, "expected a publishDiagnostics notification");
        JsonNode diagnostics = publish.get("params").get("diagnostics");
        assertTrue(diagnostics.size() > 0, "expected at least one diagnostic");
        assertEquals("souther", diagnostics.get(0).get("source").asString());
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

        byte[] input = frames(
                message(1, "initialize", Map.of("rootUri", dir.toUri().toString())),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", bUri, "text", bText))));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        // the importing module used to bail; now it resolves a against the root and reports E1905
        JsonNode publish = readFrames(out.toByteArray()).stream()
                .filter(m -> m.has("method")
                        && m.get("method").asString().equals("textDocument/publishDiagnostics"))
                .filter(m -> m.get("params").get("uri").asString().equals(bUri))
                .reduce((first, second) -> second).orElse(null);   // the latest publish for b
        assertNotNull(publish, "expected a publishDiagnostics for b");
        JsonNode diagnostics = publish.get("params").get("diagnostics");
        assertTrue(diagnostics.size() > 0, "the cross-module compile surfaces the failing example");
        assertEquals("E1905", diagnostics.get(0).get("code").asString());
    }

    @Test
    void capabilitiesAdvertiseReferences() {
        byte[] input = frames(message(1, "initialize", Map.of()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new MessageConnection(new ByteArrayInputStream(input), out)).run();

        JsonNode caps = readFrames(out.toByteArray()).stream()
                .filter(m -> m.has("id") && m.get("id").asInt() == 1).findFirst().orElseThrow()
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

    // --- helpers: build and read framed JSON-RPC messages ---

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
