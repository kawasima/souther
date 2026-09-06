package souther.compiler.check;

import java.util.function.BinaryOperator;

/**
 * How far a reading goes into a connective the clause was written with.
 *
 * <p>A clause's shape is read out of the tree once ({@link ClauseExpr}), and every reading over it
 * meets the same connectives. What they do not share is how far down they go: a reading whose state
 * composes a choice reads both alternatives and joins them, and one whose state does not reads the
 * choice as one thing and hands it to whatever reads a part. Held as a flag beside the fold, that
 * decision and the composition it licenses were two answers a reading gave apart — and a reading
 * that said it descended and could not compose had nowhere to say so.
 *
 * <p>So the two are one answer. Descending means saying how the parts compose, and a reading with
 * no way to compose them says it takes the connective whole. What it is then handed is the node an
 * author wrote it at, read as a part like any other.
 *
 * @param <S> what a reading of a clause comes to
 */
sealed interface Descent<S> {

    /** The connective is read as one thing, and what is under it is not read at all. */
    record Whole<S>() implements Descent<S> {}

    /**
     * Both of what it composes are read, and this is what holding them together comes to.
     *
     * <p>The composition and not a name for it. Which connective this is and what a reading makes
     * of it are the reading's own business — a choice may be a join in one state and a meet in
     * another — and a reading with something to say about where the choice was written closes over
     * the node it was asked about.
     */
    record Into<S>(BinaryOperator<S> compose) implements Descent<S> {

        public Into {
            if (compose == null) {
                throw new IllegalArgumentException(
                        "a reading that descends says what holding both comes to");
            }
        }
    }
}
