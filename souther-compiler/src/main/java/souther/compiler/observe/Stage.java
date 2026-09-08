package souther.compiler.observe;

/**
 * How far an {@code example} row got.
 *
 * <p>Separate from {@link Disposition} because how far a row reached and how it ended are different
 * questions with different readers. A row that never built its input tells a measure nothing about
 * the behavior; a row that ran and disagreed tells it which case the behavior actually produced. Both
 * are "failed", and collapsing them loses the second.
 */
public enum Stage {

    /** Nothing was established — the fixtures did not build. */
    NONE,

    /** The row's values were built — its inputs, and the expectation where it states one — so they
     *  are known to be legal. */
    FIXTURES_VALIDATED,

    /** The behavior was applied. Whatever it answered, it answered. */
    INVOKED,

    /** The behavior answered and the answer was taken. Where a row states what it expects this is
     *  passed through on the way to {@link #COMPARED}; where the answer is owed it is where the row
     *  ends, and it is what a measure reading the answer reads. */
    ANSWERED,

    /** The answer was compared against what the row expects. */
    COMPARED;

    public boolean reached(Stage at) {
        return compareTo(at) >= 0;
    }
}
