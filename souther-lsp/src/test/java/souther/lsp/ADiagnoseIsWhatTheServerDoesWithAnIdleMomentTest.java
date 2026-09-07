package souther.lsp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static souther.lsp.Session.message;
import static souther.lsp.Session.publishedFor;
import static souther.lsp.Session.replyTo;

/**
 * When the workspace is diagnosed, and what that costs a client that is typing.
 *
 * <p>Diagnostics are what this server does with a moment in which nothing has been asked of it. That
 * one sentence is the whole of the scheduling, and both things worth holding it to follow from it. A
 * run of keystrokes costs one diagnose rather than one each, because the second keystroke arrives
 * while the first one's diagnose has not been started or has not finished, and either way it gives
 * way. And a question asked after a keystroke is answered before the keystroke's diagnostics are
 * published, because the question is something asked and the diagnose is what there is to do
 * instead.
 *
 * <p>Nothing here holds the server to diagnosing at all while a client keeps asking. It does not:
 * a client that never stops asking is a client that never leaves an idle moment, and that is what
 * asking without pause means rather than a case to write a threshold for.
 */
class ADiagnoseIsWhatTheServerDoesWithAnIdleMomentTest {

    @Test
    void aRunOfEditsCostsOneDiagnoseAndNotOneEach() throws Exception {
        Path dir = aWorkspace();
        String uri = dir.resolve("m0.sou").toUri().toString();
        try (Session session = new Session()) {
            session.send(opening(dir, uri));
            session.await(publishedFor(uri), "the diagnostics of the document as it was opened");
            session.awaitQuiet();
            long published = session.count(publishedFor(uri));

            session.send(
                    message(null, "textDocument/didChange", changedTo(uri, edited("Amount0"))),
                    message(null, "textDocument/didChange", changedTo(uri, edited("Missing"))),
                    message(null, "textDocument/didChange", changedTo(uri, edited("Amount0"))));
            session.awaitAtLeast(published + 1, publishedFor(uri),
                    "diagnostics for the document as the last of the edits left it");
            session.awaitQuiet();

            assertEquals(published + 1, session.count(publishedFor(uri)),
                    "three edits in a row are one thing to say about the document, said once");
        }
    }

    @Test
    void aQuestionAskedAfterAnEditIsAnsweredBeforeTheEditIsDiagnosed() throws Exception {
        Path dir = aWorkspace();
        String uri = dir.resolve("m0.sou").toUri().toString();
        try (Session session = new Session()) {
            session.send(opening(dir, uri));
            session.await(publishedFor(uri), "the diagnostics of the document as it was opened");
            session.awaitQuiet();
            long published = session.count(publishedFor(uri));

            session.send(
                    message(null, "textDocument/didChange", changedTo(uri, edited("Missing"))),
                    message(2, "textDocument/hover", somewhereIn(uri)));
            session.await(replyTo(2), "the answer to the question asked after the edit");

            assertEquals(published, session.count(publishedFor(uri)),
                    "the question was answered while what the edit provoked was still owed");
        }
    }

    /** The handshake and one open document. */
    private static String[] opening(Path dir, String uri) throws IOException {
        return new String[] {
            message(1, "initialize", Map.of("rootUri", dir.toUri().toString())),
            message(null, "initialized", Map.of()),
            message(null, "textDocument/didOpen",
                    Map.of("textDocument", Map.of("uri", uri, "text", Files.readString(dir.resolve("m0.sou"))))),
        };
    }

    private static Map<String, Object> changedTo(String uri, String text) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", Map.of("uri", uri));
        params.put("contentChanges", java.util.List.of(Map.of("text", text)));
        return params;
    }

    private static Map<String, Object> somewhereIn(String uri) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("textDocument", Map.of("uri", uri));
        params.put("position", Map.of("line", 1, "character", 6));
        return params;
    }

    /** The opened document with the field's type named {@code type}, which one edit is as good as. */
    private static String edited(String type) {
        return moduleNamed(0).replace("of: Int", "of: " + type);
    }

    private static String moduleNamed(int i) {
        return """
                module m%d
                data Amount%d = { of: Int }
                behavior twice%d : (a: Amount%d) -> Amount%d
                let twice%d (a) = Amount%d { of = a.of + a.of }
                example twice%d
                  | (Amount%d { of = 2 }) -> Amount%d { of = 4 }
                """.formatted(i, i, i, i, i, i, i, i, i, i);
    }

    /** Enough modules that a diagnose is long enough to be interrupted by the next frame. */
    private static Path aWorkspace() throws IOException {
        Path dir = Files.createTempDirectory("lsp-idle");
        for (int i = 0; i < 24; i++) {
            Files.writeString(dir.resolve("m" + i + ".sou"), moduleNamed(i));
        }
        return dir;
    }
}
