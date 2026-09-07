package souther.lsp;

/**
 * What carrying out one message came to, and so what is written back for it.
 *
 * <p>Returned rather than written where the work finishes, so that one place writes and every
 * message leaves through it. That a request is answered exactly once is then a property of that
 * place and not a habit of eighteen handlers.
 */
sealed interface Outcome {

    /** The answer to a request. Null is an answer: {@code shutdown} is replied to with nothing. */
    record Answered(Object result) implements Outcome {}

    /**
     * The client gave up on this request before it was answered.
     *
     * <p>An outcome and not a failure. The work was asked for and then unasked for, which is a thing
     * that happens on every keystroke, and the reply the protocol asks for says exactly that.
     */
    record Cancelled() implements Outcome {}

    /** The request could not be carried out. */
    record Failed(int code, String message) implements Outcome {}

    /** Nothing to write. A notification, or a request written as one. */
    record Nothing() implements Outcome {}

    /** Nothing to write, and the session ends here. */
    record Stop() implements Outcome {}
}
