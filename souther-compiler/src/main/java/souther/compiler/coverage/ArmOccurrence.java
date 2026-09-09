package souther.compiler.coverage;

import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.SourceConstructOrigin;

/**
 * One arm of one fork, in the tree that runs: which fork, and which of its arms.
 *
 * <p>What tells one decision from another where all a reader can say is that a fork was settled. A
 * reading that cannot name the position a fork is about still knows that going one way and going
 * the other are the same decision settled twice, and this is what says which decision that is.
 *
 * <p><b>The fork as the tree has it, and not as a walk counted it.</b> A non-recursive helper is
 * spliced into each body that calls it and an operation applies the block it was handed as often as
 * it likes, so one fork the author wrote is several in the tree that runs — each reached under its
 * own conditions, and each settled on a value of its own. Which of them this is is
 * {@link ConstructOccurrence}, which the fork node carries; a number handed out by whatever walked
 * the bodies would be an identity two readers can derive differently, and the readers below joining
 * on it would be joining on the walk.
 *
 * <p><b>And not where a run through it is recorded.</b> That is {@link ArmProbe}, an address the
 * emitter issues for what it instruments — an arm answering {@code unreachable} has none, and is
 * still an arm a reading can prove nothing arrives at. An identity and an address held as one value
 * would leave such an arm nameless.
 *
 * @param fork which fork of the tree that runs
 * @param part which of its arms, by the order the emitter emits them
 */
public record ArmOccurrence(ConstructOccurrence fork, int part) {

    public ArmOccurrence {
        if (fork == null) {
            throw new IllegalArgumentException("an arm is an arm of some fork: part " + part);
        }
        if (part < 0) {
            throw new IllegalArgumentException(
                    "an arm is one of its fork's own: part " + part + " of " + fork);
        }
    }

    /** Which fork of the source this is an arm of, which is what an obligation is owed for. */
    public SourceConstructOrigin origin() {
        return fork.origin();
    }

    @Override
    public String toString() {
        return fork + "/" + part;
    }
}
