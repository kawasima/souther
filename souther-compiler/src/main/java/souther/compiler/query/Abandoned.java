package souther.compiler.query;

/**
 * Thrown out of a walk that was asked to stop before it had an answer.
 *
 * <p>An {@link Error} and not an exception, because the callers above catch {@code RuntimeException}
 * and {@code StackOverflowError} to turn a fault into a marker in the file the author is reading. A
 * walk that was stopped has no fault to report and nothing to say about any file: it was told to
 * stop, and only whoever told it is entitled to read that. Passing through those catches is what the
 * type is for.
 *
 * <p>No stack trace and no suppression. It is control flow on a path that is taken as often as an
 * author types, and where it was thrown from says nothing that the reason it was thrown does not.
 */
public final class Abandoned extends Error {
    private static final long serialVersionUID = 1L;

    public Abandoned() {
        super("the walk was asked to stop before it answered", null, false, false);
    }
}
