package souther.compiler.values;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a walk over a reading that is still a description has to reach before it answers.
 *
 * <p>An alternative stands where every block of it still admits something, and the reading stands
 * where any alternative does. The walk stops as soon as one of those is decided, which is a
 * question about the operation it is walking under and not about the answer it is holding: stopped
 * on the wrong one, it answers about the blocks it had reached and calls that the reading's answer.
 *
 * <p>Both orders of each, because what a walk reaches first is the order the blocks of an
 * alternative and the alternatives of a reading happen to be in, and neither order is anybody's
 * claim. A reading written the other way round is the same reading.
 */
class EveryBlockOfAnAlternativeIsAskedAndEveryAlternativeOfAReadingTest {

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
    void anAlternativeIsAskedAboutAtEveryBlockItNames() {
        assertEquals(Emptiness.EMPTY,
                plans("here", A).meet(plans("there", B)).anyAlternativeAdmits(admitting(HERE)),
                "an alternative holding a block nothing admits at stands for nothing, however many"
                        + " of its blocks were answered before that one was reached");
        assertEquals(Emptiness.EMPTY,
                plans("there", B).meet(plans("here", A)).anyAlternativeAdmits(admitting(HERE)));
    }

    @Test
    void andAReadingIsAskedAboutAtEveryAlternativeItHolds() {
        assertEquals(Emptiness.NONEMPTY,
                alternative(A, B).joinLiveApart(alternative(C, C))
                        .anyAlternativeAdmits(admittingWhatIsPlanned(C)),
                "and a reading one alternative of which stands admits something, however many"
                        + " alternatives were refused before that one was reached");
        assertEquals(Emptiness.NONEMPTY,
                alternative(C, C).joinLiveApart(alternative(A, B))
                        .anyAlternativeAdmits(admittingWhatIsPlanned(C)));
    }
}
