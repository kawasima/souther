package souther.compiler.numeric;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A range per position, and what the connectives do to a set of them.
 *
 * <p>The arithmetic on its own, apart from any reading of a clause. What this has to get right is
 * the direction: the state exists to decide that a position holds no value, so every operation that
 * cannot be exact has to widen. A join that narrowed would refuse a model somebody can write.
 */
class WhatOrderedRulesLeaveEachPositionTest {

    private static final String A = "a";
    private static final String B = "b";

    private static OrderedInterval from(long low, long high) {
        return new OrderedInterval(Endpoint.inclusive(Count.of(low)),
                Endpoint.inclusive(Count.of(high)));
    }

    private static OrderedInterval above(long low) {
        return new OrderedInterval(Endpoint.inclusive(Count.of(low)), null);
    }

    private static OrderedInterval below(long high) {
        return new OrderedInterval(null, Endpoint.inclusive(Count.of(high)));
    }

    /** A position nothing was said about is every value its order has, which is what makes a meet
     *  with what the rules said the whole answer. */
    @Test
    void aPositionNothingWasSaidAboutIsEveryValueOfItsOrder() {
        OrderedIntervals<String> nothing = OrderedIntervals.top();

        assertEquals(OrderedInterval.OPEN, nothing.at(A));
        assertFalse(nothing.isBottom());
    }

    /** Both rules holding is the tighter of each end. */
    @Test
    void rulesHoldingTogetherLeaveWhatIsInsideBoth() {
        OrderedIntervals<String> both = OrderedIntervals.at(A, above(5))
                .meet(OrderedIntervals.at(A, below(9)));

        assertEquals(from(5, 9), both.at(A));
        assertFalse(both.isBottom());
    }

    /** And where nothing is inside both, the position is named as holding nothing. */
    @Test
    void aPositionWhoseEndsCrossIsNamedAsHoldingNothing() {
        OrderedIntervals<String> crossed = OrderedIntervals.at(A, above(6))
                .meet(OrderedIntervals.at(A, below(2)));

        assertTrue(crossed.isBottom());
        assertEquals(Set.of(A), crossed.holdingNothing());
    }

    /**
     * A choice between two rules is the ends around both.
     *
     * <p>The hull and not the union: a pair of ends cannot say "5 to 9 or 20 to 30", and the ends
     * around both admit everything either does. Wide is the direction this must err in.
     */
    @Test
    void aChoiceBetweenTwoRulesLeavesTheEndsAroundBoth() {
        OrderedIntervals<String> either = OrderedIntervals.at(A, from(5, 9))
                .joinLive(OrderedIntervals.at(A, from(20, 30)));

        assertEquals(from(5, 30), either.at(A));
    }

    /**
     * A choice leaves a position only one side bounded open.
     *
     * <p>A value taken by the other alternative is under no obligation from this one, so what the
     * two of them together say about such a position is nothing.
     */
    @Test
    void aChoiceLeavesAPositionOnlyOneSideBoundedOpen() {
        OrderedIntervals<String> either = OrderedIntervals.at(A, from(5, 9))
                .joinLive(OrderedIntervals.at(B, from(1, 2)));

        assertEquals(OrderedInterval.OPEN, either.at(A));
        assertEquals(OrderedInterval.OPEN, either.at(B));
    }

    /**
     * A choice both sides of which hold nothing holds nothing, and names what both leave empty.
     *
     * <p>No side speaks for the other, and the two may not be met either: a meet is a conjunction
     * and the alternatives were never stated together.
     */
    @Test
    void aChoiceWithNothingOnEitherSideNamesWhatBothLeaveEmpty() {
        OrderedIntervals<String> left = OrderedIntervals.at(A, above(6))
                .meet(OrderedIntervals.at(A, below(2)));
        OrderedIntervals<String> right = OrderedIntervals.at(B, above(6))
                .meet(OrderedIntervals.at(B, below(2)));

        assertTrue(left.bothDead(right).isBottom(), "neither side can be taken");
        assertEquals(Set.of(), left.bothDead(right).holdingNothing(),
                "and no one position is what the choice leaves empty");
        assertEquals(left.bothDead(right).holdingNothing(), right.bothDead(left).holdingNothing(),
                "and the same either way round");
    }

    /** And a position every side leaves empty is one the choice leaves empty. */
    @Test
    void aPositionEverySideLeavesEmptyIsOneTheChoiceLeavesEmpty() {
        OrderedIntervals<String> empty = OrderedIntervals.at(A, above(6))
                .meet(OrderedIntervals.at(A, below(2)));
        OrderedIntervals<String> left = empty.meet(OrderedIntervals.at(B, from(0, 0)));
        OrderedIntervals<String> right = empty.meet(OrderedIntervals.at(B, from(1, 1)));

        assertEquals(Set.of(A), left.bothDead(right).holdingNothing());
        assertEquals(Set.of(A), right.bothDead(left).holdingNothing(), "and either way round");
    }

    /**
     * And a position neither side leaves empty is left alone by the choice.
     *
     * <p>The negative control for the two above. Met rather than joined, the two alternatives'
     * answers about {@code b} — one value each — would be a {@code b} at both and so at neither,
     * which is a contradiction the model does not contain.
     */
    @Test
    void aPositionNeitherSideLeavesEmptyIsNotMadeEmptyByTheChoice() {
        OrderedIntervals<String> empty = OrderedIntervals.at(A, above(6))
                .meet(OrderedIntervals.at(A, below(2)));
        OrderedIntervals<String> left = empty.meet(OrderedIntervals.at(B, from(0, 0)));
        OrderedIntervals<String> right = empty.meet(OrderedIntervals.at(B, from(1, 1)));

        assertFalse(left.bothDead(right).holdingNothing().contains(B));
        assertFalse(right.bothDead(left).holdingNothing().contains(B));
    }
}
