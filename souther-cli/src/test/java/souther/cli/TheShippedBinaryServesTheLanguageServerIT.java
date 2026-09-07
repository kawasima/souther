package souther.cli;

import souther.lsp.transport.MessageConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unit test reaches the dispatch directly, which leaves the part only packaging decides
 * untested. `souther lsp` is served by a module this one depends on and does not carry, and what a
 * reader is told to run is neither a class nor a jar but the launcher with the stack flag prepended
 * to it. The chain from that binary to a published diagnostic is what this runs.
 */
class TheShippedBinaryServesTheLanguageServerIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Path binary;

    @BeforeAll
    static void theBuiltBinary() {
        binary = Path.of(System.getProperty("souther.binary", "target/souther"));
        assertTrue(Files.isExecutable(binary),
                "the prepended launcher is built before this runs: " + binary.toAbsolutePath());
    }

    @Test
    void aSessionOverTheLaunchersOwnStdioIsAnsweredAndShutDown() throws Exception {
        Path source = Files.createTempDirectory("souther-lsp-it").resolve("trip.sou");
        Files.writeString(source, "module trip\n\ndata Draft = { who: Approver }\n");

        Process process = new ProcessBuilder(binary.toString(), "lsp").start();
        MessageConnection answers = new MessageConnection(
                process.getInputStream(), OutputStream.nullOutputStream());
        process.getOutputStream().write(frames(
                message(1, "initialize", Map.of("rootUri", source.getParent().toUri().toString())),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", source.toUri().toString(),
                                "text", Files.readString(source))))));
        process.getOutputStream().flush();

        // Read until the analysis arrives, and only then say goodbye. Diagnostics are what the
        // server does with a moment nothing was asked of it, so asking it to stop in the same breath
        // as opening the document is asking it to stop before it has had one.
        JsonNode capabilities = null;
        JsonNode published = null;
        String frame;
        while (published == null && (frame = answers.read()) != null) {
            JsonNode m = JSON.readTree(frame);
            if (m.has("id") && m.get("id").isNumber() && m.get("id").asInt() == 1) {
                capabilities = m.get("result").get("capabilities");
            } else if (m.has("method")
                    && m.get("method").asString().equals("textDocument/publishDiagnostics")) {
                published = m;
            }
        }

        process.getOutputStream().write(frames(
                message(2, "shutdown", Map.of()),
                message(null, "exit", Map.of())));
        process.getOutputStream().close();
        String said = new String(process.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

        assertEquals(0, process.waitFor(), "a client that shut the server down leaves it zero: " + said);

        assertNotNull(capabilities, "the handshake was answered");
        assertTrue(capabilities.has("definitionProvider"),
                "the server the launcher carries is the whole one, not a fragment the shade kept");
        assertNotNull(published, "no diagnostics were published");
        assertEquals("E1023", published.get("params").get("diagnostics").get(0).get("code").asString(),
                "and the analysis it needs came with it");
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
        MessageConnection writer =
                new MessageConnection(new ByteArrayInputStream(new byte[0]), buffer);
        for (String m : messages) {
            writer.write(m);
        }
        return buffer.toByteArray();
    }

}
