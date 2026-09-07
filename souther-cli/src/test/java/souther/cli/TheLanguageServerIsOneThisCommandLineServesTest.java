package souther.cli;

import souther.lsp.transport.MessageConnection;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `souther lsp` is the language server on this command line's own stdio, and not a second launcher
 * that happens to be documented next to one. What the protocol tests hold the server to is held
 * over an in-memory connection; what is held here is that a line naming this command reaches it.
 *
 * <p>The protocol has the whole of stdout, so the other half of that is what this command writes
 * when it does not start. A refusal on stdout would be a frame the client cannot read, arriving
 * where the handshake was expected.
 */
class TheLanguageServerIsOneThisCommandLineServesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void aLineNamingTheCommandIsAnsweredWithTheHandshake() {
        Answer answer = run(frames(
                message(1, "initialize", Map.of()),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of(
                        "textDocument", Map.of("uri", "file:///t.sou",
                                "text", "module demo\ndata M = { name: Missing }\n"))),
                // Shut down and then the stream ends, which is a session ending the way a client
                // that closes its editor ends one. No `exit`: that is a client saying stop now, and
                // a server told to stop now has nothing more to publish.
                message(2, "shutdown", Map.of())));

        assertEquals(0, answer.code(), "a session its client shut down is this command finishing");

        JsonNode capabilities = answer.replyTo(1).get("result").get("capabilities");
        assertTrue(capabilities.has("textDocumentSync"), "the server this command serves is the one "
                + "the protocol tests hold: it answers initialize with what it can do");

        JsonNode published = answer.notified("textDocument/publishDiagnostics");
        assertEquals("E1023", published.get("params").get("diagnostics").get(0).get("code").asString(),
                "a source opened over this wire is analysed, and what the analysis says arrives");
    }

    @Test
    void anArgumentIsRefusedWhereThereIsNoOperandForItToBe() {
        Answer answer = run(frames(message(1, "initialize", Map.of())), "model.sou");

        assertEquals(2, answer.code());
        assertTrue(answer.written().isEmpty(), "nothing goes to stdout but the protocol, and this "
                + "line never started one");
        assertTrue(answer.said().contains("model.sou"), "the refusal names what it was given");
    }

    /** What one line wrote, on each of the three streams a command line has. */
    private record Answer(int code, List<JsonNode> written, String said) {

        JsonNode replyTo(int id) {
            return written.stream().filter(m -> m.has("id") && m.get("id").asInt() == id)
                    .findFirst().orElseThrow(() -> new AssertionError("no reply to request " + id));
        }

        JsonNode notified(String method) {
            return written.stream()
                    .filter(m -> m.has("method") && m.get("method").asString().equals(method))
                    .findFirst().orElseThrow(() -> new AssertionError("no " + method));
        }
    }

    /** Runs {@code souther lsp} over the given input, on streams of this test's own. */
    private static Answer run(byte[] input, String... arguments) {
        InputStream in = System.in;
        PrintStream out = System.out;
        PrintStream err = System.err;
        ByteArrayOutputStream wrote = new ByteArrayOutputStream();
        ByteArrayOutputStream complained = new ByteArrayOutputStream();
        int code;
        try {
            System.setIn(new ByteArrayInputStream(input));
            System.setOut(new PrintStream(wrote, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(complained, true, StandardCharsets.UTF_8));
            String[] line = new String[arguments.length + 1];
            line[0] = "lsp";
            System.arraycopy(arguments, 0, line, 1, arguments.length);
            code = Main.dispatch(line);
        } finally {
            System.setIn(in);
            System.setOut(out);
            System.setErr(err);
        }
        return new Answer(code, readFrames(wrote.toByteArray()),
                complained.toString(StandardCharsets.UTF_8));
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

    private static List<JsonNode> readFrames(byte[] bytes) {
        MessageConnection reader = new MessageConnection(
                new ByteArrayInputStream(bytes), OutputStream.nullOutputStream());
        List<JsonNode> read = new ArrayList<>();
        String s;
        while ((s = reader.read()) != null) {
            read.add(JSON.readTree(s));
        }
        return read;
    }
}
