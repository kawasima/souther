package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.reading.PathAccess;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An arm nothing was tried at is answered from every place it stands in.
 *
 * <p>An arm the author wrote stands in the running tree once per call site of the helper carrying
 * it, and the reading of each place is its own: a helper called under a decision this compiler can
 * state and again under one it cannot has one splice nothing reaches and one it cannot name the way
 * to. The first is a fact about the model and the second is a shortfall of ours, and a reader acts
 * on them differently — so the answer for the arm may not be whichever place the walk wrote first.
 *
 * <p>What the arm comes to is the model's answer only where the model settles every place. One
 * place this compiler could not read leaves a row for the arm writable, whatever the rest are.
 */
class AnArmWithNoWayInAnywhereIsAnsweredFromEveryPlaceTest {

    private static final PathAccess REFUSED = new PathAccess.Unreachable(
            PathAccess.Unreachable.Why.THE_CONDITION_NEVER_COMES_OUT_THAT_WAY);

    private static final PathAccess UNREAD = new PathAccess.Unsupported(
            PathAccess.Unsupported.Why.RUNS_WHERE_SOMETHING_CALLS_IT);

    private static final PathAccess UNREAD_OTHERWISE = new PathAccess.Unsupported(
            PathAccess.Unsupported.Why.MORE_WAYS_IN_THAN_ARE_READ);

    @Test
    void theModelSettlesAnArmOnlyWhereItSettlesEveryPlaceOfIt() {
        assertTrue(new ArmDisposition.NoWayIn(List.of(REFUSED, REFUSED)).theModelSettlesIt(),
                "every place is one the model refuses");
        assertFalse(new ArmDisposition.NoWayIn(List.of(REFUSED, UNREAD)).theModelSettlesIt(),
                "one place this compiler could not read leaves the arm ours to fall short of");
    }

    /** And the answer is the same whichever order the places were walked in. */
    @Test
    void theOrderThePlacesWereWalkedInDecidesNothing() {
        assertEquals(
                new ArmDisposition.NoWayIn(List.of(REFUSED, UNREAD)).theModelSettlesIt(),
                new ArmDisposition.NoWayIn(List.of(UNREAD, REFUSED)).theModelSettlesIt(),
                "swapping two call sites of one body says nothing about the arm");
    }

    /**
     * What is missing is kept whole, in an order the walk does not decide.
     *
     * <p>Two places short of two different things are two things to say. Carried as one, a reader
     * would be told what one splice lacks and nothing about the other; ordered by the walk, the
     * same body with its call sites swapped would say them the other way round.
     */
    @Test
    void whatIsMissingAtEachPlaceIsKeptAndNotOrderedByTheWalk() {
        GenerationOutcome.NotSupported one = new GenerationOutcome.NotSupported(
                new LinkedHashSet<>(List.of(
                        GenerationOutcome.NotSupported.Reason.MORE_WAYS_IN_THAN_THE_READING_HOLDS,
                        GenerationOutcome.NotSupported.Reason.THE_ARM_RUNS_WHERE_SOMETHING_CALLS_IT)));
        GenerationOutcome.NotSupported other = new GenerationOutcome.NotSupported(
                new LinkedHashSet<>(List.of(
                        GenerationOutcome.NotSupported.Reason.THE_ARM_RUNS_WHERE_SOMETHING_CALLS_IT,
                        GenerationOutcome.NotSupported.Reason.MORE_WAYS_IN_THAN_THE_READING_HOLDS)));

        assertEquals(2, one.reasons().size(), () -> "both are kept: " + one);
        assertEquals(one, other, "and the order they were met in tells them apart from nothing");
    }

    /** The two Unsupported places of one arm really are two words, which the pair above stands
     *  for. */
    @Test
    void twoPlacesShortOfTwoThingsAreTwoWords() {
        assertFalse(UNREAD.equals(UNREAD_OTHERWISE),
                "a place that runs where something calls it and one the reading stopped short of"
                        + " are not one fact");
    }
}
