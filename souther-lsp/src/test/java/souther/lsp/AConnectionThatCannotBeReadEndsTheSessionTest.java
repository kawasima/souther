package souther.lsp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A session whose frames cannot be read stops being a session.
 *
 * <p>Reading happens on a thread of its own, which is a thread that cannot end a session: it holds
 * nothing, answers nothing, and a failure it kept to itself would leave the session waiting for a
 * message that is never coming, with everything the client already asked for still owed. So what
 * stopped it is carried to the thread that owns the session and raised there.
 *
 * <p>Raised, and not turned into a failed request. A request the server cannot answer costs that
 * request; a connection that cannot be read costs the session, and there is nowhere left to write
 * the failure to.
 */
class AConnectionThatCannotBeReadEndsTheSessionTest {

    @Test
    void aHeaderThatSaysNoLengthIsMetOnTheThreadThatOwnsTheSession() {
        ByteArrayInputStream unreadable = new ByteArrayInputStream(
                "Content-Length: as long as it takes\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII));

        assertThrows(ConnectionLost.class,
                () -> LspServer.serve(unreadable, new ByteArrayOutputStream()),
                "what stopped the reading thread is what the session stops on");
    }
}
