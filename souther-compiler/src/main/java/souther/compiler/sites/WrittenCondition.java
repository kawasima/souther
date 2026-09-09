package souther.compiler.sites;

import souther.compiler.types.SourceConstructOrigin;

/**
 * One condition a source wrote, as the module that wrote it files it.
 *
 * <p>An address and not a place. What is here is which condition of which source, said so that a
 * copy cannot change it: a helper expanded into three callers puts one condition in three trees,
 * and where it is written is the writing module's answer rather than something each copy carries.
 *
 * <p><b>Two shapes, because two things are conditions and only one of them is a construct.</b> A
 * comparison and a short-circuit operator are constructs the source wrote and carry an origin of
 * their own. An arm of a fork is not: what the source wrote there is the fork, and the arm stands
 * among its arms by an index. So the arm borrows the fork's identity and does not borrow its place
 * — what is filed for one is where the arm itself is written, which is what a reader is shown.
 *
 * <p><b>Not every condition a reading meets.</b> A condition of a shape the reading has no words
 * for is any expression at all, and the source wrote no construct there this can name; where such
 * a one is reported is asked of the reading that met it
 * ({@link souther.compiler.partition.ConditionReportAnchor}). Widened to cover those, this would
 * be a question the writing module answers with a place it worked out for something else.
 */
public sealed interface WrittenCondition {

    /** The origin of the construct the identity rests on, which is what says whose source files
     *  it. */
    SourceConstructOrigin construct();

    /** A comparison or a short-circuit operator: a construct the source wrote, and a condition on
     *  its own. */
    record Construct(SourceConstructOrigin construct) implements WrittenCondition {

        public Construct {
            if (construct == null) {
                throw new IllegalArgumentException("a condition the source wrote is some"
                        + " construct it wrote");
            }
        }

        @Override
        public String toString() {
            return "condition " + construct;
        }
    }

    /**
     * One arm of a fork, which is the condition that the value being matched turned out to be the
     * case the arm selects.
     *
     * @param construct the fork, which is what the source wrote
     * @param part      which arm of it, in the order the fork holds them
     */
    record ForkArm(SourceConstructOrigin construct, int part) implements WrittenCondition {

        public ForkArm {
            if (construct == null) {
                throw new IllegalArgumentException("an arm is an arm of a fork somewhere");
            }
            if (part < 0) {
                throw new IllegalArgumentException(
                        "an arm stands somewhere among the fork's: " + part);
            }
        }

        @Override
        public String toString() {
            return "arm " + part + " of " + construct;
        }
    }
}
