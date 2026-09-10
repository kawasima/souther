package souther.compiler.partition;

import souther.compiler.inputs.Refinement;

/**
 * One condition a path consulted, and what it came out as.
 *
 * <p>An entry of a rule's vector. A condition a path never consulted has no entry here at all,
 * which is what leaving it out means: a short-circuit that settled before reaching a condition
 * yields a rule that says nothing about it, and that silence is the rule rather than something
 * folded away afterwards.
 *
 * <p>The three shapes pair each condition with the answers it has. A comparison and a condition
 * nothing read come out one of two ways; a fork on a position comes out as one of the position's
 * cases. Written as a condition beside an outcome of its own type, a comparison could be recorded
 * as having come out a case and a fork as having held.
 */
public sealed interface DecidedCondition {

    /** Which distinction this is an answer about. */
    DecisionCondition condition();

    /** A comparison, held or denied. */
    record Compared(DecisionCondition.AComparison condition, boolean held)
            implements DecidedCondition {

        public Compared {
            if (condition == null) {
                throw new IllegalArgumentException("an answer is about some comparison");
            }
        }
    }

    /**
     * A position read as one of its cases.
     *
     * @param to which values the arm the path took leaves at that position
     */
    record Narrowed(DecisionCondition.APosition condition, Refinement to)
            implements DecidedCondition {

        public Narrowed {
            if (condition == null || to == null) {
                throw new IllegalArgumentException(
                        "an answer about a position is one of the cases it was read as");
            }
        }
    }

    /**
     * A condition nothing could read, coming out one of its two ways.
     *
     * <p>Both halves are known even where the condition is not: the walk took a path because the
     * condition came out that way, whatever the condition says. So a rule records the outcome and
     * says, by the shape of the column, that it cannot say of what.
     */
    record Unread(DecisionCondition.AConditionNotRead condition, boolean held)
            implements DecidedCondition {

        public Unread {
            if (condition == null) {
                throw new IllegalArgumentException("an answer is about some condition");
            }
        }
    }
}
