package souther.compiler.coverage;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run is read as places of the numbering it was recorded under, and of no other.
 *
 * <p>This is the crossing the whole numbering exists for. What a run leaves behind is numbers; what
 * a number means is a place under the numbering that handed it out. Read under another numbering,
 * every number still resolves — the other numbering has numbers too — and what comes back is an
 * ordinary answer about places the run was never near. Nothing downstream can tell that from a
 * right one, so it is refused here.
 *
 * <p><b>The other numbering is the same size and numbers different places.</b> A numbering that is
 * shorter would be caught by the number falling off the end, which is a different mechanism and one
 * that happens to fire; the case to hold is the one where nothing about the numbers themselves is
 * wrong.
 *
 * <p>And two numberings of the same places are one, which is the other half: the refusal is about
 * what a numbering is rather than about which construction made it, so a recording read under a
 * numbering derived a second time is read and not refused.
 */
class ARunIsReadUnderTheNumberingItWasRecordedUnderTest {

    /** Two arms and a comparison, so that a number of each family is in play. */
    private static SiteNumbering here() {
        return Numberings.of(Numberings.Family.ARM, Numberings.Family.COMPARISON,
                Numberings.Family.ARM);
    }

    /** The same size, and every place a different one: the comparison and the arms have swapped
     *  round, so each number addresses something else. */
    private static SiteNumbering elsewhere() {
        return Numberings.of(Numberings.Family.COMPARISON, Numberings.Family.ARM,
                Numberings.Family.COMPARISON);
    }

    @Test
    void aRecordingOfAnotherNumberingIsRefusedRatherThanAnswered() {
        // A run of the other numbering, recorded at its own places: its arm is 1 and its
        // comparisons are 0 and 2, which is exactly what this numbering has the other way round.
        Observation seen = new Observation(elsewhere().identity(), Set.of(1),
                Set.of(new ComparisonOutcome(0, true)));

        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> here().align(seen));

        assertTrue(refused.getMessage().contains("a number means a place under the numbering"),
                refused.getMessage());
    }

    /**
     * And a place of another numbering put to a run that was read is refused too.
     *
     * <p>The other end of the same crossing, and the one a check at the door does not reach. A
     * reader can hold two numberings — the module's own and one it took off an artifact — and what
     * a run of the first did at a place of the second is nothing. Answered as an ordinary "no", it
     * says the row did not reach a place it was never asked about, which is the same false answer
     * the refusal above exists to stop, one step further along.
     */
    @Test
    void aPlaceOfAnotherNumberingIsRefusedByARunThatWasRead() {
        AlignedObservation read = here().align(new Observation(here().identity(), Set.of(0, 2),
                Set.of(new ComparisonOutcome(1, true))));

        assertThrows(IllegalArgumentException.class, () -> read.lit(elsewhere().arm(1)),
                "an arm of another numbering is no arm this run can be asked about");
        assertThrows(IllegalArgumentException.class,
                () -> read.saw(elsewhere().comparison(0), true),
                "and neither is a comparison of one");
        assertThrows(IllegalArgumentException.class, () -> read.reached(elsewhere().comparison(2)),
                "however the question is put");
        assertTrue(read.lit(here().arm(0)), "while a place of its own is answered");
    }

    /**
     * And a numbering derived a second time reads it, because it is the same numbering.
     *
     * <p>Which is what says the check above is about the numbering and not about the construction.
     * Held against the refusal: a rule that turned on which walk made the numbering would refuse
     * this one too, and every recomputation of a store would stop being able to read a recording it
     * had just been able to read.
     */
    @Test
    void aRecordingIsReadUnderANumberingDerivedASecondTime() {
        Observation seen = new Observation(here().identity(), Set.of(0, 2),
                Set.of(new ComparisonOutcome(1, false)));

        AlignedObservation read = here().align(seen);

        assertEquals(Set.of(here().arm(0), here().arm(2)), read.arms(),
                "the arms it was recorded at, as places of a numbering built for this reading");
        assertTrue(read.saw(here().comparison(1), false),
                "and the way its comparison came out");
    }
}
