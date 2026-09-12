package souther.compiler.examples;

/**
 * A fake table had no output for an input the behavior asked for, and no {@code _} row to fall
 * through to.
 *
 * <p>Raised where a stand-in dispatches and not where a table is built: what a table answers for is
 * known from what it states, and which calls a run makes is known only from the run. So this leaves
 * the behavior the way anything it ends with does, and whoever applied it is the one that says what
 * happened — a row being verified is told which input went unanswered, and a row a search is
 * running is one that stopped where it stopped.
 */
final class FakeMissException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    FakeMissException(String message) {
        super(message);
    }
}
