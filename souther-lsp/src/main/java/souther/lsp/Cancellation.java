package souther.lsp;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Whether the client has said it no longer wants the answer to one request.
 *
 * <p>One of these per request, made where the frame is read and handed over with it, so that saying
 * so and reading it are the same object seen from two threads. A request is registered under its id
 * before it is put where the analysis thread can see it, which is what makes a cancel that arrives
 * while the request is still queued reach the same value as one that arrives while it is running.
 *
 * <p>When it is read is what settles the request: the read taken after the work is done and before
 * the outcome is decided is the moment the request either was cancelled or was not. A cancel that
 * lands after that read does not change what the client is sent, and nothing here pretends
 * otherwise — there is no state to move it to.
 */
final class Cancellation {

    private final AtomicBoolean asked = new AtomicBoolean();

    /** Says the client has given up on this request. */
    void ask() {
        asked.set(true);
    }

    /** Whether the client had given up as of this read. */
    boolean asked() {
        return asked.get();
    }
}
