package souther.lsp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A server running against a connection that stays open, for the tests about when it answers what.
 *
 * <p>The other protocol tests hand the server every frame at once and read what it wrote afterwards,
 * which is enough while the answer to a message depends only on the messages before it. It is not
 * enough here: what this server does with an idle moment is the thing being tested, and a stream
 * that is already at its end never has one. So the frames go in through a pipe the test holds open,
 * and the test waits on what comes back rather than on the session ending.
 */
final class Session implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Long enough that a slow machine is not a failure, short enough to be a test. */
    private static final long WAIT_MILLIS = 60_000;

    /** How long nothing may arrive before the server is taken to have finished what it was doing. */
    private static final long QUIET_MILLIS = 400;

    private final PipedOutputStream toServer = new PipedOutputStream();

    private final ByteArrayOutputStream fromServer = new ByteArrayOutputStream();

    private final Thread serving;

    private volatile Throwable stopped;

    Session() {
        PipedInputStream in;
        try {
            in = new PipedInputStream(toServer, 1 << 16);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        serving = Thread.ofPlatform().name("souther-lsp-under-test").start(() -> {
            try {
                LspServer.serve(in, fromServer);
            } catch (Throwable t) {
                stopped = t;
            }
        });
    }

    /** Writes the messages as one run of frames, the way a client that pipelines them would. */
    void send(String... messages) {
        try {
            for (String message : messages) {
                byte[] body = message.getBytes(StandardCharsets.UTF_8);
                toServer.write(("Content-Length: " + body.length + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                toServer.write(body);
            }
            toServer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Everything the server has written so far that can be read as a whole frame. */
    List<JsonNode> heard() {
        byte[] wrote = fromServer.toByteArray();
        List<JsonNode> messages = new ArrayList<>();
        int at = 0;
        while (true) {
            int blank = indexOf(wrote, at);
            if (blank < 0) {
                return messages;
            }
            String header = new String(wrote, at, blank - at, StandardCharsets.US_ASCII);
            int length = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
            int body = blank + 4;
            if (body + length > wrote.length) {
                return messages;   // the frame is still being written
            }
            messages.add(JSON.readTree(new String(wrote, body, length, StandardCharsets.UTF_8)));
            at = body + length;
        }
    }

    /** How many of the messages written so far {@code what} holds of. */
    long count(Predicate<JsonNode> what) {
        return heard().stream().filter(what).count();
    }

    /** Waits for a message the server writes that {@code what} holds of, and answers with it. */
    JsonNode await(Predicate<JsonNode> what, String wanted) {
        long until = System.currentTimeMillis() + WAIT_MILLIS;
        while (System.currentTimeMillis() < until) {
            JsonNode found = heard().stream().filter(what).findFirst().orElse(null);
            if (found != null) {
                return found;
            }
            pause();
        }
        throw new AssertionError("the server never wrote " + wanted);
    }

    /** Waits until the server has written at least {@code many} messages that {@code what} holds of. */
    void awaitAtLeast(long many, Predicate<JsonNode> what, String wanted) {
        long until = System.currentTimeMillis() + WAIT_MILLIS;
        while (System.currentTimeMillis() < until) {
            if (count(what) >= many) {
                return;
            }
            pause();
        }
        throw new AssertionError("the server never wrote " + wanted);
    }

    /** Waits until the server has written nothing for long enough to have finished what it had. */
    void awaitQuiet() {
        long lastChange = System.currentTimeMillis();
        int seen = fromServer.size();
        while (System.currentTimeMillis() - lastChange < QUIET_MILLIS) {
            pause();
            if (fromServer.size() != seen) {
                seen = fromServer.size();
                lastChange = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void close() {
        try {
            toServer.close();
            serving.join(WAIT_MILLIS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        if (stopped != null) {
            throw new AssertionError("the session did not end the way a session ends", stopped);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /** Where the blank line that ends a frame's header block begins, or -1 if it is not there yet. */
    private static int indexOf(byte[] wrote, int from) {
        for (int i = from; i + 3 < wrote.length; i++) {
            if (wrote[i] == '\r' && wrote[i + 1] == '\n' && wrote[i + 2] == '\r' && wrote[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    /** One JSON-RPC message: a request when {@code id} is given, a notification when it is not. */
    static String message(Integer id, String method, Object params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        if (id != null) {
            message.put("id", id);
        }
        message.put("method", method);
        message.put("params", params);
        return JSON.writeValueAsString(message);
    }

    /** Whether {@code message} is the reply to the request numbered {@code id}. */
    static Predicate<JsonNode> replyTo(int id) {
        return m -> m.has("id") && !m.has("method") && m.get("id").asInt() == id;
    }

    /** Whether {@code message} publishes diagnostics for {@code uri}. */
    static Predicate<JsonNode> publishedFor(String uri) {
        return m -> m.has("method")
                && m.get("method").asString().equals("textDocument/publishDiagnostics")
                && m.get("params").get("uri").asString().equals(uri);
    }
}
