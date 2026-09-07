package souther.lsp;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static souther.lsp.Session.message;
import static souther.lsp.Session.replyTo;

/**
 * What a client is sent for a request it stopped waiting for.
 *
 * <p>A reply, and one — the protocol has no way to say nothing, and a client that never hears back
 * about a request it sent is a client holding an entry open for the rest of the session. What the
 * reply says is that the request was cancelled, which is the thing that happened.
 *
 * <p>Where the request had got to when the cancel arrived is not a distinction anything makes, and
 * the cases here are written so that it cannot be one. The first names a request that had certainly
 * not been looked at, because the reply to the request in front of it shows that one was still being
 * answered; the second names a request with nothing in front of it at all. Both rest on a request
 * being registered under its id before it is put where the thread that answers it can see it —
 * registered afterwards, the first would find nothing to cancel and be answered with a hover.
 *
 * <p>What is not written here is a cancel that arrives after the answer was settled, which is
 * answered with the answer. Which side of that a cancel falls on is decided by one read, and a test
 * that tried to land on the far side of it would be a test of how long a compile happens to take.
 */
class ARequestTheClientGaveUpOnIsAnsweredWithThatAndNothingElseTest {

    private static final int REQUEST_CANCELLED = -32800;

    @Test
    void oneQueuedBehindWorkAlreadyUnderWayIsAnsweredWithTheCancellation() throws Exception {
        Path dir = aWorkspaceWorthCompiling();
        String uri = dir.resolve("m0.sou").toUri().toString();
        try (Session session = new Session()) {
            session.send(afterOpening(dir, uri,
                    message(2, "textDocument/hover", somewhereIn(uri)),
                    message(3, "textDocument/hover", somewhereIn(uri)),
                    message(null, "$/cancelRequest", Map.of("id", 3))));

            assertCancelled(session, 3);
            assertTrue(session.await(replyTo(2), "a reply to the request it did wait for").has("result"),
                    "the request in front of it was not the one given up on, and was answered");
        }
    }

    @Test
    void oneAlreadyBeingWorkedOnIsAnsweredWithTheCancellation() throws Exception {
        Path dir = aWorkspaceWorthCompiling();
        String uri = dir.resolve("m0.sou").toUri().toString();
        try (Session session = new Session()) {
            session.send(afterOpening(dir, uri,
                    message(2, "textDocument/hover", somewhereIn(uri)),
                    message(null, "$/cancelRequest", Map.of("id", 2))));

            assertCancelled(session, 2);
        }
    }

    @Test
    void oneNobodyGaveUpOnIsAnsweredWithWhatItAsked() throws Exception {
        Path dir = aWorkspaceWorthCompiling();
        String uri = dir.resolve("m0.sou").toUri().toString();
        try (Session session = new Session()) {
            session.send(afterOpening(dir, uri,
                    message(2, "textDocument/hover", somewhereIn(uri))));

            JsonNode reply = session.await(replyTo(2), "a reply to the hover");
            assertTrue(reply.has("result"), "a request nobody cancelled is answered with its answer");
            assertFalse(reply.has("error"), "and not with a cancellation");
        }
    }

    private static void assertCancelled(Session session, int id) {
        JsonNode reply = session.await(replyTo(id), "a reply to the request it gave up on");
        assertFalse(reply.has("result"),
                "a client that gave up is not sent the answer it stopped waiting for");
        assertEquals(REQUEST_CANCELLED, reply.get("error").get("code").asInt(),
                "it is sent what the protocol says a cancelled request is answered with");
        session.awaitQuiet();
        assertEquals(1, session.count(replyTo(id)), "and is sent it once");
    }

    /**
     * The handshake, one open document, and then {@code then} — as one run of frames.
     *
     * <p>One run, and not a handshake sent and waited on first. A session that had been left to
     * itself for a moment has diagnosed the workspace, and a compile that has answered once answers
     * the next question about it too quickly to be a question in flight.
     */
    private static String[] afterOpening(Path dir, String uri, String... then) throws IOException {
        List<String> frames = new ArrayList<>(List.of(
                message(1, "initialize", Map.of("rootUri", dir.toUri().toString())),
                message(null, "initialized", Map.of()),
                message(null, "textDocument/didOpen", Map.of("textDocument",
                        Map.of("uri", uri, "text", Files.readString(dir.resolve("m0.sou")))))));
        frames.addAll(List.of(then));
        return frames.toArray(String[]::new);
    }

    private static Map<String, Object> somewhereIn(String uri) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", Map.of("uri", uri));
        params.put("position", Map.of("line", 1, "character", 6));
        return params;
    }

    /**
     * Enough modules that answering one question about them takes long enough to cancel.
     *
     * <p>Each carries an example, so answering means running it. What is being tested is what
     * happens while work is in flight, and work that is over before the next frame is read is not
     * work in flight.
     */
    private static Path aWorkspaceWorthCompiling() throws IOException {
        Path dir = Files.createTempDirectory("lsp-cancel");
        for (int i = 0; i < 24; i++) {
            Files.writeString(dir.resolve("m" + i + ".sou"), """
                    module m%d
                    data Amount%d = { of: Int }
                    behavior twice%d : (a: Amount%d) -> Amount%d
                    let twice%d (a) = Amount%d { of = a.of + a.of }
                    example twice%d
                      | (Amount%d { of = 2 }) -> Amount%d { of = 4 }
                    """.formatted(i, i, i, i, i, i, i, i, i, i));
        }
        return dir;
    }
}
