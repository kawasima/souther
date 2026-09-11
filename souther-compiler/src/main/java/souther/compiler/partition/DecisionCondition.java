package souther.compiler.partition;

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
     * <p>Over whichever quantities the body compared. A number of the input and a number a
     * dependency answered are compared the same way and canonicalised the same way, so
     * {@code riskScore(c) >= 700} and {@code 700 <= riskScore(c)} are one column for the reason
     * {@code n > 100} and {@code n <= 100} are.
     *
     * @param form        the comparison with its threshold moved in, so that what it states is
     *                    {@code form rel 0}
     * @param proposition the relation this column is read as holding, which is the canonical one of
     *                    the pair
     */
    record AComparison(LinearForm<DecisionAtom> form, Rel proposition) implements DecisionCondition {

        public AComparison {
            if (form == null || proposition == null) {
                throw new IllegalArgumentException(
                        "a comparison of a decision is a relation over a form");
            }
            if (proposition != proposition.orItsDenial()) {
                throw new IllegalArgumentException("a comparison and its denial are one column,"
                        + " read as " + proposition.orItsDenial() + " rather than as "
                        + proposition);
            }
        }
    }

    /**
     * A value read for its truth, as the subject it is read of.
     *
     * <p>The subject and not the place. {@code guard allowed} written twice over one value is one
     * distinction, and a column apiece for them would admit an assignment where the value holds and
     * does not.
     *
     * <p>Not a comparison against {@code true}. The two answers a truth has are the two answers,
     * and saying them as a form and a relation would put a construct in the table that the body
     * never wrote — after which a reader placing the point of a line would find a line drawn on a
     * comparison nobody can be sent to.
     */
    record ATruth(DecisionSubject of) implements DecisionCondition {

        public ATruth {
            if (of == null) {
                throw new IllegalArgumentException("a truth of a decision is a truth of something");
            }
        }
    }

    /**
     * A fork on a sum, as the subject its scrutinee is.
     *
     * <p>The subject and not the arm. What a row is composed to do is put a value at a position or
     * stand a dependency in; "the second arm was taken" is a fact about the text, and a fork's arms
     * are the answers to one question about one subject rather than a question apiece.
     *
     * @param of the scrutinee, before any arm narrows it
     */
    record ACase(DecisionSubject of) implements DecisionCondition {

        public ACase {
            if (of == null) {
                throw new IllegalArgumentException("a fork of a decision is asked of something");
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
