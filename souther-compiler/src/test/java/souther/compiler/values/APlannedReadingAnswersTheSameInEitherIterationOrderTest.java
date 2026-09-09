package souther.compiler.values;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a walk over a reading that is still a description may answer from.
 *
 * <p>An alternative stands where every block of it still admits something, and the reading stands
 * where any alternative does. The walk takes them in one at a time and stops as soon as what it is
 * holding cannot be moved, so what it read is the blocks up to that one and no further — and it may
 * answer from those only because they settled the walk, which is a question about the operation it
 * is walking under and not about the answer it is holding. Stopped on the other operation's
 * question, it answers from a run of blocks that settled nothing.
 *
 * <p>So what is asked of these is not how much they read. It is that the answer is the same wherever
 * in the order the block or the alternative that settles it happens to fall: the blocks of an
 * alternative are named in the order an author wrote the rules, the alternatives of a choice in the
 * order the branches were written, and a reading written the other way round is the same reading.
 */
class APlannedReadingAnswersTheSameInEitherIterationOrderTest {

    private static final Value A = Value.text("A");

    private static final Value B = Value.text("B");

    private static final Value C = Value.text("C");

    private static final Sameness.Block<String> HERE = Sameness.Block.of("here");

    /** A rule about one position while it is still a description. */
    private static PlannedValues<String> plans(String atom, Value value) {
        return PlannedValues.at(atom, AdmittedPlan.of(ValueSet.just(value)));
    }

    /** A question nothing but {@code where} answers, settled either way and never waiting. */
    private static AskedOfEachBlock<String> admitting(Sameness.Block<String> where) {
        return (block, _) -> block.equals(where) ? Emptiness.NONEMPTY : Emptiness.EMPTY;
    }

    /** One alternative of two, told apart by what it admits rather than by where: alternatives
     *  held apart are over the same blocks, and a question about a block would be about both. */
    private static AskedOfEachBlock<String> admittingWhatIsPlanned(Value value) {
        return (_, set) -> set.equals(ValueSet.just(value)) ? Emptiness.NONEMPTY : Emptiness.EMPTY;
    }

    /** An alternative naming two positions, which is what a choice cannot merge into a product. */
    private static PlannedValues<String> alternative(Value here, Value there) {
        return plans("here", here).meet(plans("there", there));
    }

    @Test
    void anAlternativeIsRefusedByAnyBlockInEitherOrder() {
        assertEquals(Emptiness.EMPTY,
                plans("here", A).meet(plans("there", B)).anyAlternativeAdmits(admitting(HERE)),
                "an alternative holding a block nothing admits at stands for nothing, whether that"
                        + " block was the one the walk reached first or the one it reached last");
        assertEquals(Emptiness.EMPTY,
                plans("there", B).meet(plans("here", A)).anyAlternativeAdmits(admitting(HERE)));
    }

    @Test
    void andAReadingAdmitsWhenAnyAlternativeDoesInEitherOrder() {
        assertEquals(Emptiness.NONEMPTY,
                alternative(A, B).joinLiveApart(alternative(C, C))
                        .anyAlternativeAdmits(admittingWhatIsPlanned(C)),
                "and a reading one alternative of which stands admits something, whether the"
                        + " alternatives refused were written before that one or after it");
        assertEquals(Emptiness.NONEMPTY,
                alternative(C, C).joinLiveApart(alternative(A, B))
                        .anyAlternativeAdmits(admittingWhatIsPlanned(C)));
    }
}
