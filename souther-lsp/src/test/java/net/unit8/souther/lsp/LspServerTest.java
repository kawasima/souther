package net.unit8.souther.lsp;

import net.unit8.souther.lsp.transport.MessageConnection;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
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
