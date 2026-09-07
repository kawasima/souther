package souther.lsp;

import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What has been read off the connection and not yet carried out.
 *
 * <p>The one place the two threads of a session meet. The reading thread only ever adds to it; the
 * analysis thread takes from it, and owns everything a message is carried out against.
 *
 * <p>A request is registered under its id before it is queued, and not when it is taken off the
 * queue. That order is what a cancel depends on: a client that sends a request and cancels it in the
 * same breath is naming something the analysis thread has not looked at yet, and the name has to
 * already mean something. Where the request is when the cancel arrives — queued, running, or done —
 * is then not a distinction anything has to make.
 *
 * <p>A failure of the reading thread is published twice, and the two are not copies. The queue
 * carries it so a thread waiting for work is woken and meets it in the order it happened; the slot
 * carries it so a thread that is in the middle of a long answer can be told at its next checkpoint,
 * which it cannot be by a queue it is not reading.
 */
final class Inbox {

    private final BlockingQueue<Inbound> waiting = new LinkedBlockingQueue<>();

    /** The requests read but not yet answered, by the id the client named them with. */
    private final Map<String, Cancellation> outstanding = new ConcurrentHashMap<>();

    private final AtomicReference<Throwable> readerFailure = new AtomicReference<>();

    /** Hands over one message, registering it first if it is a request the client may cancel. */
    void hand(JsonNode body) {
        Cancellation cancellation = new Cancellation();
        String id = nameOf(body.get("id"));
        if (id != null) {
            outstanding.put(id, cancellation);
        }
        waiting.add(new Inbound.Message(body, cancellation));
    }

    /** Says the client has given up on the request {@code id} names, wherever it has got to. */
    void cancel(JsonNode id) {
        String name = nameOf(id);
        if (name == null) {
            return;
        }
        Cancellation cancellation = outstanding.get(name);
        if (cancellation != null) {
            cancellation.ask();
        }
    }

    /** Says {@code id} has been answered, so a cancel naming it from now on names nothing. */
    void answered(JsonNode id) {
        String name = nameOf(id);
        if (name != null) {
            outstanding.remove(name);
        }
    }

    void readerEnded() {
        waiting.add(new Inbound.EndOfInput());
    }

    void readerFailed(Throwable cause) {
        readerFailure.compareAndSet(null, cause);
        waiting.add(new Inbound.ReaderFailed(cause));
    }

    /** What stopped the reading thread, or null while it is reading. */
    Throwable readerFailure() {
        return readerFailure.get();
    }

    /**
     * Whether anything is waiting — what makes a diagnose give way.
     *
     * <p>Anything, and not only a message. What is in here is either a client asking for something
     * or a session ending, and both are reasons to stop diagnosing: the first because answering is
     * what a diagnose steps aside for, the second because there is nobody left to publish to.
     */
    boolean anyWaiting() {
        return !waiting.isEmpty();
    }

    /** The next thing to carry out, or null if nothing is waiting. */
    Inbound take() {
        return waiting.poll();
    }

    /** The next thing to carry out, waiting for one to arrive. */
    Inbound await() {
        try {
            return waiting.take();
        } catch (InterruptedException _) {
            // Nothing here interrupts this thread, so someone outside the session did. A session
            // that was interrupted did not end the way the protocol ends one, and the exit code
            // says so.
            Thread.currentThread().interrupt();
            return new Inbound.EndOfInput();
        }
    }

    /**
     * What a request is registered under: the id as it was written, brackets and quotes and all.
     *
     * <p>The protocol lets an id be a number or a string, and {@code 1} and {@code "1"} are two
     * requests. Taking the text of the node keeps them apart, where reading either as a string would
     * let a cancel for one end the other.
     */
    private static String nameOf(JsonNode id) {
        return id == null || id.isNull() ? null : id.toString();
    }
}
