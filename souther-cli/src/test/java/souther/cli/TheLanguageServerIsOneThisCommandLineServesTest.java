package souther.cli;

import souther.lsp.transport.MessageConnection;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
    void aLineNamingTheCommandIsAnsweredWithTheHandshake() throws Exception {
        Answer answer = converse(said -> {
            said.send(
                    message(1, "initialize", Map.of()),
                    message(null, "initialized", Map.of()),
                    message(null, "textDocument/didOpen", Map.of(
                            "textDocument", Map.of("uri", "file:///t.sou",
                                    "text", "module demo\ndata M = { name: Missing }\n"))));
            // Read what the analysis published before saying goodbye. Diagnostics are what the
            // server does with a moment nothing was asked of it, so shutting it down in the same
            // breath as opening the document is shutting it down before it has had one.
            said.awaitNotification("textDocument/publishDiagnostics");
            said.send(message(2, "shutdown", Map.of()), message(null, "exit", Map.of()));
        });

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

    /**
     * Runs {@code souther lsp} while {@code conversation} talks to it, on streams of this test's own.
     *
     * <p>A stream that stays open, because what is being watched for here is published rather than
     * replied to, and this server publishes in a moment when nothing has been asked of it. A run of
     * frames written all at once and then ended leaves no such moment.
     */
    private static Answer converse(Consumer<Said> conversation) throws Exception {
        PipedOutputStream toServer = new PipedOutputStream();
        PipedInputStream serverIn = new PipedInputStream(toServer, 1 << 16);
        ByteArrayOutputStream wrote = new ByteArrayOutputStream();
        ByteArrayOutputStream complained = new ByteArrayOutputStream();
        InputStream in = System.in;
        PrintStream out = System.out;
        PrintStream err = System.err;
        AtomicInteger code = new AtomicInteger(-1);
        try {
            System.setIn(serverIn);
            System.setOut(new PrintStream(wrote, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(complained, true, StandardCharsets.UTF_8));
            Thread serving = Thread.ofPlatform().name("souther-lsp-under-test")
                    .start(() -> code.set(Main.dispatch(new String[] {"lsp"})));
            conversation.accept(new Said(toServer, wrote));
            toServer.close();
            serving.join(WAIT_MILLIS);
        } finally {
            System.setIn(in);
            System.setOut(out);
            System.setErr(err);
        }
        return new Answer(code.get(), framesSoFar(wrote.toByteArray()),
                complained.toString(StandardCharsets.UTF_8));
    }

    /** Long enough that a slow machine is not a failure, short enough to be a test. */
    private static final long WAIT_MILLIS = 60_000;

    /** What a test says to a session, and what it waits to hear back. */
    private record Said(PipedOutputStream toServer, ByteArrayOutputStream wrote) {

        void send(String... messages) {
            try {
                toServer.write(frames(messages));
                toServer.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void awaitNotification(String method) {
            long until = System.currentTimeMillis() + WAIT_MILLIS;
            while (System.currentTimeMillis() < until) {
                boolean heard = framesSoFar(wrote.toByteArray()).stream()
                        .anyMatch(m -> m.has("method") && m.get("method").asString().equals(method));
                if (heard) {
                    return;
                }
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            throw new AssertionError("the server never sent " + method);
        }
    }

    /**
     * The messages in {@code wrote} that can be read whole.
     *
     * <p>Read while the writing is going on, so the last frame may be a header with its body still
     * to come. That one is not a message yet and is left for the next read.
     */
    private static List<JsonNode> framesSoFar(byte[] wrote) {
        List<JsonNode> messages = new ArrayList<>();
        int at = 0;
        while (true) {
            int blank = -1;
            for (int i = at; i + 3 < wrote.length; i++) {
                if (wrote[i] == '\r' && wrote[i + 1] == '\n'
                        && wrote[i + 2] == '\r' && wrote[i + 3] == '\n') {
                    blank = i;
                    break;
                }
            }
            if (blank < 0) {
                return messages;
            }
            String header = new String(wrote, at, blank - at, StandardCharsets.US_ASCII);
            int length = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
            int body = blank + 4;
            if (body + length > wrote.length) {
                return messages;
            }
            messages.add(JSON.readTree(new String(wrote, body, length, StandardCharsets.UTF_8)));
            at = body + length;
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
