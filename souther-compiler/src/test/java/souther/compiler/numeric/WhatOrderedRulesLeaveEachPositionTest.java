package souther.compiler.numeric;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /** An order that stops nowhere, for the questions the extent has no part in. */
    private static final ValueOrder ANY_ORDER = () -> OrderedInterval.OPEN;

    /** And one that stops at both ends, which is what every carrier but a decimal is. */
    private static final ValueOrder ZERO_TO_TEN_ORDER = () -> from(0, 10);

    /** The vocabulary the positions are ordered by, which is what a reading is read against. */
    private static final java.util.Map<String, ValueOrder> ANY =
            java.util.Map.of(A, ANY_ORDER, B, ANY_ORDER);
    private static final java.util.Map<String, ValueOrder> ZERO_TO_TEN =
            java.util.Map.of(A, ZERO_TO_TEN_ORDER, B, ZERO_TO_TEN_ORDER);

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

    /**
     * A position nothing was said about is every value its order has, which is what makes a meet
     * with what the rules said the whole answer.
     *
     * <p>Its order's values and not every value there is. The two are one for an order that stops
     * nowhere and are two for one that stops, and a state answering with the first for both would
     * be answering about a decimal wherever it was asked about an {@code Int}.
     */
    @Test
    void aPositionNothingWasSaidAboutIsEveryValueOfItsOrder() {
        OrderedIntervals<String> nothing = OrderedIntervals.top();

        assertNull(nothing.statedAt(A), "no rule put an end on it");
        assertEquals(OrderedInterval.OPEN, nothing.valuesAt(A, ANY));
        assertEquals(from(0, 10), nothing.valuesAt(A, ZERO_TO_TEN),
                "an order that stops has those ends whether or not a rule was written");
        assertFalse(nothing.isBottom());
    }

    /**
     * And a pair of bounds covering the order leaves the position exactly where nothing said
     * anything would.
     *
     * <p>The reading behind {@code n >= 2 || n <= 0} on a whole number, which is the shape a reader
     * comparing what two alternatives leave has to see through. Told apart, a choice above such a
     * branch is as wide as it is because of the branch beside it — and an end nothing worked out
     * stays open at a position the model draws no line at.
     */
    @Test
    void boundsCoveringTheOrderLeaveThePositionWhereEveryValueIs() {
        OrderedIntervals<String> covered = OrderedIntervals.at(A, above(6))
                .joinLive(OrderedIntervals.at(A, below(5)));

        assertTrue(covered.valuesAt(A, ZERO_TO_TEN).sameValuesAs(ZERO_TO_TEN_ORDER.extent()),
                "between them the two bounds hold every value the order has");
        assertTrue(covered.valuesAt(A, ZERO_TO_TEN)
                        .sameValuesAs(OrderedIntervals.<String>top().valuesAt(A, ZERO_TO_TEN)),
                "which is what a reading that said nothing about it leaves");
    }

    /**
     * And a position the rules stopped on an order the vocabulary does not name is said to be a
     * mistake in this compiler.
     *
     * <p>What a range leaves is only ever the values of the order it is a range of, so there is no
     * answer to give. Answered with the pair of absent ends, this would be handing back the reading
     * that has no order in it — the one every other method here exists to stop being read as a
     * value — and a caller that dropped the order on the way would get it silently.
     *
     * <p>The position nothing was said about is not that. Nothing put a range there, so there is no
     * range to be read against an order, and every value there is is the answer that cannot be
     * wrong about an order nobody named.
     */
    @Test
    void aPositionWithNoOrderIsAMistakeInThisCompiler() {
        OrderedIntervals<String> bounded = OrderedIntervals.at(A, above(5));

        assertThrows(IllegalStateException.class,
                () -> bounded.valuesAt(A, java.util.Map.of()),
                "the rules stopped it and nothing here says what they stopped it on");
        assertThrows(IllegalStateException.class,
                () -> bounded.valuesAt(B, java.util.Map.of()),
                "and the position nothing was written about is the one whose answer is its order"
                        + " and nothing else");
        assertThrows(IllegalStateException.class,
                () -> OrderedIntervals.<String>top().valuesAt(A, java.util.Map.of()),
                "including where nothing was written about any of them");
        assertNull(bounded.statedAt(B),
                "whether a rule wrote anything is the other question, and it needs no order");
    }

    /** Both rules holding is the tighter of each end. */
    @Test
    void rulesHoldingTogetherLeaveWhatIsInsideBoth() {
        OrderedIntervals<String> both = OrderedIntervals.at(A, above(5))
                .meet(OrderedIntervals.at(A, below(9)));

        assertEquals(from(5, 9), both.statedAt(A));
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

        assertEquals(from(5, 30), either.statedAt(A));
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

        assertNull(either.statedAt(A));
        assertNull(either.statedAt(B));
        assertEquals(OrderedInterval.OPEN, either.valuesAt(A, ANY));
        assertEquals(OrderedInterval.OPEN, either.valuesAt(B, ANY));
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
