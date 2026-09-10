package souther.compiler.partition;

import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;

/**
 * One distinction a body draws to decide, apart from what any path made of it.
 *
 * <p>A column of the decision the body states. What a path did with it is
 * {@link DecidedCondition}'s, and the two are apart because a rule is read both ways: a reader
 * composing a row asks what this path came out as, and a reader placing the point of a line asks
 * which rules carry <em>this</em> condition at all.
 *
 * <p><b>Not a position, and not a place.</b> Positions are a condition's operands — {@code x < y}
 * is one distinction over two of them — and where a condition is written tells one from another
 * only for as long as no two of them are written alike. So each shape below is told apart by what
 * it means, and the one shape with no meaning to be told apart by says so by being that shape.
 *
 * <p>Which is what keeps the table exclusive. Two writings of one inequality over one form are one
 * column, and a table with a column apiece for them admits an assignment where one proposition
 * holds and does not — an assignment no row can be written at and nothing can show impossible.
 */
public sealed interface DecisionCondition {

    /**
     * A comparison the arithmetic took in, as the proposition it states.
     *
     * <p>The form and one of the two relations over it, so that a comparison and its denial are one
     * column. Which of the two is written down is settled by {@link #proposition} and is not the
     * one the author happened to write: {@code n > 100} in one body and {@code n <= 100} in another
     * are the same distinction, and a reader that took the authored side would have two columns for
     * it.
     *
     * @param form        the comparison with its threshold moved in, so that what it states is
     *                    {@code form rel 0}
     * @param proposition the relation this column is read as holding, which is the canonical one of
     *                    the pair
     */
    record AComparison(LinearForm<NumericTerm> form, Rel proposition) implements DecisionCondition {

        public AComparison {
            if (form == null || proposition == null) {
                throw new IllegalArgumentException(
                        "a comparison of a decision is a relation over a form");
            }
            if (!proposition.equals(canonical(proposition))) {
                throw new IllegalArgumentException(
                        "a comparison and its denial are one column, read as " + canonical(proposition)
                                + " rather than as " + proposition);
            }
        }

        /**
         * The proposition {@code stated} and its denial are read as, which is one of the two.
         *
         * <p>Chosen by the order the relations are declared in, which is a rule and not a meaning:
         * what matters is that the two sides of one distinction pick the same side of it, and no
         * reader is owed a reason why {@code GE} is the written one rather than {@code LT}.
         */
        public static Rel canonical(Rel stated) {
            return stated.ordinal() <= stated.denied().ordinal() ? stated : stated.denied();
        }
    }

    /**
     * A fork on a sum, as the position its scrutinee stands at.
     *
     * <p>The position and not the arm. What a row is composed to be is a value at a position; "the
     * second arm was taken" is a fact about the text, and a fork's arms are the answers to one
     * question about one position rather than a question apiece.
     *
     * @param at the scrutinee's position, before any arm narrows it
     */
    record APosition(TermPath at) implements DecisionCondition {

        public APosition {
            if (at == null) {
                throw new IllegalArgumentException("a fork of a decision is asked of some position");
            }
        }
    }

    /**
     * A condition this reading has no words for, named by the reading that met it.
     *
     * <p>The one shape told apart by a name. What the other two carry says what they mean, and two
     * conditions nothing could read mean nothing to be told apart by — so without a name, two
     * distinctions this compiler does distinguish would be one column and the table would say a
     * body decides less than it does.
     *
     * <p>On the table rather than dropped. A rule carrying one is a rule whose conditions were not
     * all read, which is what a measurement says of itself; left off, the rule would claim to state
     * every distinction on its path.
     *
     * @param met which condition of the reading it is
     * @param why what stopped it, in the walk's own words
     */
    record AConditionNotRead(ConditionOccurrence met, OnTheWay.Why why)
            implements DecisionCondition {

        public AConditionNotRead {
            if (met == null || why == null) {
                throw new IllegalArgumentException(
                        "a condition nothing read is one some reading met, for a reason");
            }
        }
    }
}
