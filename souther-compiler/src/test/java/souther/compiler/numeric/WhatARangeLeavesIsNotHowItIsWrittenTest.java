package souther.compiler.numeric;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which values a pair of ends leaves, told apart from the pair of ends it is written as.
 *
 * <p>A record derives its equality from what it holds, and what these hold reaches
 * {@link BigDecimal#equals}, which tells {@code 3.0} from {@code 3.00}. Two ends at one place are
 * one end whichever way the rule that put them there spelled the number, and the whole reason
 * {@link Place#key} exists is to say so — a reader comparing the ends instead has two answers where
 * the order has one.
 *
 * <p>And the question stops short of the carrier. An absent end is no end and is not the same end
 * as one at the last value a carrier has; whether those two leave the same values is a question
 * about the order they are ranges of, and it is asked where the order is
 * ({@link OrderedIntervals#valuesAt}).
 */
class WhatARangeLeavesIsNotHowItIsWrittenTest {

    private static OrderedInterval from(String low) {
        return new OrderedInterval(
                Endpoint.inclusive(Count.of(new BigDecimal(low))), null);
    }

    private static OrderedInterval between(long low, long high) {
        return new OrderedInterval(Endpoint.inclusive(Count.of(low)),
                Endpoint.inclusive(Count.of(high)));
    }

    /** One place written two ways is one end, which the derived equality does not say. */
    @Test
    void onePlaceWrittenTwoWaysIsOneEnd() {
        assertTrue(from("3.0").sameValuesAs(from("3.00")));
        assertTrue(from("3.00").sameValuesAs(from("3.0")), "either way round");
        assertFalse(from("3.0").equals(from("3.00")),
                "and the derived equality is the writing, which a report writes back as written");
    }

    /** And an end that is there is not the same as no end. */
    @Test
    void anEndThatIsThereIsNotNoEnd() {
        assertFalse(from("3.0").sameValuesAs(OrderedInterval.OPEN));
        assertFalse(OrderedInterval.OPEN.sameValuesAs(from("3.0")));
    }

    /**
     * And a range with no value in it leaves what every other empty one leaves.
     *
     * <p>Ends that cross say nothing about where they crossed. Told apart, two rules each stepping
     * off the end of their order would be two answers about a position that holds nothing either
     * way.
     */
    @Test
    void aRangeWithNoValueInItLeavesWhatEveryOtherEmptyOneLeaves() {
        OrderedInterval crossed = between(6, 2);
        OrderedInterval elsewhere = between(9, 1);

        assertTrue(crossed.holdsNothing() && elsewhere.holdsNothing(), "both hold nothing");
        assertTrue(crossed.sameValuesAs(elsewhere));
        assertFalse(crossed.sameValuesAs(between(2, 6)), "and neither is one that holds something");
    }

    /** And the question is an equivalence, so a reader may hold two answers against a third. */
    @Test
    void leavingTheSameValuesIsAnEquivalence() {
        OrderedInterval one = from("5");
        OrderedInterval spelled = from("5.0");
        OrderedInterval again = from("5.00");

        assertTrue(one.sameValuesAs(one), "reflexive");
        assertTrue(one.sameValuesAs(spelled) && spelled.sameValuesAs(one), "symmetric");
        assertTrue(spelled.sameValuesAs(again) && one.sameValuesAs(again), "transitive");
    }

    /**
     * The ends a rule states are the ones the order does not already stop the values at.
     *
     * <p>Read for the line an author drew. Every whole number stops at the largest one whether or
     * not a rule was written, and a reader downstream cannot tell that end from one a rule states.
     */
    @Test
    void anEndWhereTheOrderAlreadyStopsIsNotOneARuleStated() {
        OrderedInterval order = between(0, 10);

        assertEquals(new OrderedInterval(Endpoint.inclusive(Count.of(2L)), null),
                between(2, 10).endsStatedWithin(order),
                "the upper end is where the order stops anyway");
        assertEquals(OrderedInterval.OPEN, order.endsStatedWithin(order),
                "and a range reaching both ends of its order states neither");
    }

    /**
     * And nothing is struck off a range with no value in it, which would come back holding some.
     *
     * <p>An empty range can share one end with its order and not both, so striking that one off
     * leaves a range open on that side — which holds every value under the other end, and the rule
     * it came from holds none.
     */
    @Test
    void nothingIsStruckOffARangeThatHoldsNothing() {
        OrderedInterval crossed = between(0, -1);
        OrderedInterval order = between(0, 10);

        assertTrue(crossed.holdsNothing(), "nothing is at once at least nought and at most less");
        assertTrue(crossed.endsStatedWithin(order).holdsNothing(),
                "and striking off the end it shares with its order would leave it open below");
    }
}
