package souther.compiler.partition;

import souther.compiler.types.ModelOccurrence;

/**
 * What a run down one path of a body would be seen doing, for one condition of it.
 *
 * <p>Beside a rule and not inside it. A rule is told apart by the distinctions it consulted, and
 * where a run through one of them is written down is not one of them — two bodies stating one rule
 * in two places state one rule. What this is for is the other direction: a reader holding a run
 * asks which rule it took, and these are what it asks about.
 *
 * <p>Said in the model's words. Which construct of the model a condition is is what the tree the
 * rules are read off and the tree that runs agree about; where the emitter numbered it is that
 * construct looked up in a plan, and is the plan's answer rather than this one's.
 *
 * <p><b>And where there is nothing to be seen, that.</b> A condition this reading has no words for
 * is a condition it cannot name a construct of the model for either, so a rule carrying one is a
 * rule no run can be shown to have taken. Left off, such a rule would look witnessed by whichever
 * conditions beside it were seen.
 */
public sealed interface ShownBy {

    /**
     * A comparison of the model coming out one way.
     *
     * @param comparison which comparison of the model
     * @param held       the way the path took it
     */
    record AtAComparison(ModelOccurrence comparison, boolean held) implements ShownBy {

        public AtAComparison {
            if (comparison == null) {
                throw new IllegalArgumentException("a comparison of the model is some construct");
            }
        }
    }

    /**
     * One arm of a fork of the model.
     *
     * <p>The arm and not the narrowing it establishes. What a run writes down is which way it went;
     * what the rule says the value turned out to be is the column, and the two are answers to one
     * question in two vocabularies.
     */
    record AtAnArm(ModelOccurrence fork, int part) implements ShownBy {

        public AtAnArm {
            if (fork == null) {
                throw new IllegalArgumentException("an arm is an arm of some fork of the model");
            }
            if (part < 0) {
                throw new IllegalArgumentException("an arm stands somewhere among its fork's: "
                        + part);
            }
        }
    }

    /**
     * A condition with no construct of the model to be seen at.
     *
     * <p>Named by the column, which is what tells two of them apart wherever anything does. What a
     * rule carrying one says is that a run through it cannot be recognised, which is this
     * compiler's shortfall rather than anything about the model.
     */
    record NothingIsRecorded(DecisionCondition condition) implements ShownBy {

        public NothingIsRecorded {
            if (condition == null) {
                throw new IllegalArgumentException(
                        "a condition nothing records is some condition");
            }
        }
    }
}
