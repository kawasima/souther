package souther.lsp;

import tools.jackson.databind.JsonNode;

/**
 * What the thread reading frames hands the thread that carries them out.
 *
 * <p>Three things and not one, because a session ends in more than one way and each way has to reach
 * the analysis thread through the same queue. Putting them in the queue is what makes the order they
 * happened in the order they are seen in, and it is what wakes a thread that is waiting for work.
 */
sealed interface Inbound {

    /** A message to carry out, and whether the client has since given up on it. */
    record Message(JsonNode body, Cancellation cancellation) implements Inbound {}

    /** The client closed the stream. Whether that is an orderly end is the exit code's question. */
    record EndOfInput() implements Inbound {}

    /**
     * The reading thread stopped on something it could not read past.
     *
     * <p>Carried rather than reported where it happened: the thread that reads frames is not the one
     * that owns the session, and a failure of the reader is a failure of the session. It is raised
     * again on the thread that can end one.
     */
    record ReaderFailed(Throwable cause) implements Inbound {}
}
