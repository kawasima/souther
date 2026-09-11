package souther.compiler.values;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The denials a reading was told, as it was told them.
 *
 * <p>A denial is between two positions and comes to a pair of blocks, and which blocks those are is
 * settled by everything the reading holds as one value. A conjunction is where that is found out, so
 * what it costs a reading to conjoin is what this decides: told as positions, the denials of two
 * readings are put together and nothing is carried anywhere.
 *
 * <p>Three things follow that a model cannot show, because what a model shows is which declarations
 * are refused and these are all rules about what a refusal costs to reach.
 *
 * <p>That the denials are read out once each and in the order they were first stated. That reading
 * them back is not a limit on how many there may be, nor on how often a caller says the same thing —
 * a reading states one denial at a time, so what a long one composes is as deep as it is long, and
 * nothing is copied when two are said together.
 *
 * <p>And that a denial whose ends the reading came to hold as one value still empties it. Which of
 * the two readings being conjoined was the one holding them apart is not something the answer may
 * turn on, so it is asked both ways round.
 */
class WhatAnAlternativeStatesIsReadOnceHoweverItWasComposedTest {

    /** A reading saying enough denials for one that called down what they compose to not answer. */
    private static final int LONGER_THAN_A_STACK = 100_000;

    /** Doublings enough that a reader paying per reach rather than per part takes far longer than
     *  this test is given, and few enough that it still stops and says so. */
    private static final int MORE_REACHES_THAN_THERE_ARE_PARTS = 27;

    /** Doublings enough that how many reaches there are is past what a count of them holds. */
    private static final int PAST_WHAT_A_COUNT_HOLDS = 64;

    private static StatedApartness<String> said(List<String> pairs) {
        StatedApartness<String> out = StatedApartness.none();
        for (String pair : pairs) {
            String[] ends = pair.split("/");
            out = out.and(StatedApartness.of(ends[0], ends[1]));
        }
        return out;
    }

    private static StatedApartness.Denial<String> denial(String one, String other) {
        return new StatedApartness.Denial<>(one, other);
    }

    /** A denial stated again is the same denial, and it is read where it was first stated. */
    @Test
    void aDenialStatedAgainIsReadOnceAndWhereItWasFirstStated() {
        assertEquals(List.of(denial("p", "q"), denial("q", "r"), denial("p", "r")),
                List.copyOf(said(List.of("p/q", "q/r", "q/p", "p/r", "r/q")).denials()),
                "and a denial stated the other way round is the same denial");
    }

    /** Two readings' denials said together are the first's and then what the second adds. */
    @Test
    void twoReadingsDenialsAreReadAsTheFirstsAndThenWhatTheSecondAdds() {
        assertEquals(List.of(denial("p", "q"), denial("q", "r"), denial("r", "s")),
                List.copyOf(said(List.of("p/q", "q/r")).and(said(List.of("q/r", "r/s"))).denials()));
    }

    /** Nothing stated, which is what a reading that read no denial holds. */
    @Test
    void aReadingToldNothingStatesNothing() {
        assertTrue(StatedApartness.<String>none().isEmpty());
        assertEquals(Set.of(), StatedApartness.<String>none().denials());
    }

    /**
     * A reading longer than a reader could call down is read back whole.
     *
     * <p>The denial stated last is reached, and so is the one stated first — which is under
     * everything else that was stated, and is the one a reader that stopped anywhere would not have.
     */
    @Test
    void aReadingLongerThanAStackIsReadBackWhole() {
        StatedApartness<String> deep = StatedApartness.of("p", "q");
        for (int stated = 0; stated < LONGER_THAN_A_STACK; stated++) {
            deep = deep.and(StatedApartness.of("p", "q"));
        }
        assertEquals(List.of(denial("p", "q"), denial("r", "s")),
                List.copyOf(deep.and(StatedApartness.of("r", "s")).denials()));
    }

    /**
     * What was stated once and reached many times is read once.
     *
     * <p>Nothing is copied when two of these are said together, so a caller doubling what it says
     * holds one more part and reaches twice as much. Read by what it reaches, what a reading costs
     * would double with it — which is the cost this whole arrangement is about, arrived at from the
     * other end.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void whatWasStatedOnceIsReadOnceHoweverManyTimesItIsReached() {
        StatedApartness<String> shared = StatedApartness.of("p", "q");
        for (int doubled = 0; doubled < MORE_REACHES_THAN_THERE_ARE_PARTS; doubled++) {
            shared = shared.and(shared);
        }
        assertEquals(List.of(denial("p", "q")), List.copyOf(shared.denials()));
    }

    /**
     * A denial stated twice is the denial, and a reading is what it states.
     *
     * <p>Which is what every reader of these is told they hold, and is not a property of how one was
     * arrived at. Two readings holding one denial are one alternative, so a conjunction of a reading
     * with itself leaves the alternatives it had rather than as many again.
     */
    @Test
    void aReadingStatingOneDenialTwiceIsTheReadingStatingIt() {
        StatedApartness<String> once = StatedApartness.of("p", "q");
        StatedApartness<String> twice = once.and(once);

        assertEquals(once, twice);
        assertEquals(once.hashCode(), twice.hashCode());
        assertEquals(1, new LinkedHashSet<>(List.of(once, twice)).size(),
                "so a set of alternatives holds one of them and not two");
    }

    /**
     * And a reading that reaches its denials however many times still holds them.
     *
     * <p>Nothing is copied when two of these are said together, so what a caller says twice it
     * reaches twice — carried as how many reaches there are, what a reading holds would come to
     * nothing the moment there were as many of them as a number can count.
     */
    @Test
    void aReadingReachingOneDenialPastCountingStillStatesIt() {
        StatedApartness<String> shared = StatedApartness.of("p", "q");
        for (int doubled = 0; doubled < PAST_WHAT_A_COUNT_HOLDS; doubled++) {
            shared = shared.and(shared);
        }

        assertFalse(shared.isEmpty(), "a reading that states a denial states it");
        assertEquals(List.of(denial("p", "q")), List.copyOf(shared.denials()));
        assertTrue(shared.contradicts(Sameness.of("p", "q")),
                "and holding its ends as one value empties the reading");
    }

    /**
     * The pair a denial comes to, between the blocks the reading holds its ends on.
     *
     * <p>Both ends landing on one block is kept: what it says there is that a value differs from
     * itself, which nothing satisfies, and a reader that dropped it would leave the reading standing
     * on rules that emptied it.
     */
    @Test
    void aDenialComesToAPairOfTheBlocksItsEndsAreOn() {
        StatedApartness<String> stated = StatedApartness.of("p", "r");

        assertEquals(Set.of(new Apartness.Edge<>(Sameness.Block.of("p"), Sameness.Block.of("r"))),
                stated.quotientBy(Sameness.discrete()).edges());

        Apartness<String> asOne = stated.quotientBy(Sameness.of("p", "r"));
        assertTrue(asOne.holdsABlockApartFromItself(),
                () -> "a block stated to differ from itself, and not a pair to drop: " + asOne);
    }

    /**
     * A reading emptied by holding as one value what it holds apart says so, whichever side of the
     * conjunction stated which.
     */
    @Test
    void holdingApartWhatIsHeldAsOneValueEmptiesTheReadingEitherWayRound() {
        assertEquals(Emptiness.EMPTY, PlannedValues.<String>heldApart("p", "r")
                        .meet(PlannedValues.holdingAsOne("p", "r"))
                        .anyAlternativeAdmits((_, _) -> Emptiness.NONEMPTY),
                "the equality read after the denial");
        assertEquals(Emptiness.EMPTY, PlannedValues.<String>holdingAsOne("p", "r")
                        .meet(PlannedValues.heldApart("p", "r"))
                        .anyAlternativeAdmits((_, _) -> Emptiness.NONEMPTY),
                "and read before it");
    }

    /** And a reading holding apart two positions nothing holds together is not emptied by them. */
    @Test
    void holdingApartTwoPositionsNothingHoldsTogetherLeavesTheReadingStanding() {
        assertFalse(PlannedValues.<String>heldApart("p", "r")
                        .meet(PlannedValues.holdingAsOne("p", "q"))
                        .anyAlternativeAdmits((_, _) -> Emptiness.NONEMPTY) == Emptiness.EMPTY,
                "holding p with q says nothing about whether p differs from r");
    }
}
