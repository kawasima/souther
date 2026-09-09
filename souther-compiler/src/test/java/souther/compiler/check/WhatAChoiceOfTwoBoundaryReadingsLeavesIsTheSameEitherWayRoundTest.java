package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A choice is between its alternatives and not between their order, and a conjunction is between
 * its conjuncts — over every state a reading of one of these numbers can be in.
 *
 * <p><b>Enumerated and not chosen.</b> What a number is to this reading is one of a handful of
 * things, and every pair and every triple of them is written out here rather than picked. The cases
 * that were got wrong were the ones nobody thought to write down: two branches each leaving the
 * number no value came out as whichever was on the right, and a case like that is not found by
 * asking whether the cases somebody wrote hold.
 *
 * <p>Over the states and not over sources. What a model can be written to produce is a question
 * about this compiler's other readings; what the laws of this one are is a question about it, and
 * asking it through a source would leave the answer resting on which states a corpus happens to
 * reach.
 */
class WhatAChoiceOfTwoBoundaryReadingsLeavesIsTheSameEitherWayRoundTest {

    private static final DerivedNumber LENGTH = new DerivedNumber(RuleKey.of("s"),
            ValueName.Stdlib.operation("String", "length"));
    private static final DerivedNumber SIZE = new DerivedNumber(RuleKey.of("t"),
            ValueName.Stdlib.operation("Set", "size"));

    /** Two lines an author wrote, told apart by being the ones they are. */
    private static final OpenEnd ONE_LINE = new OpenEnd(LENGTH);
    private static final OpenEnd ANOTHER_LINE = new OpenEnd(LENGTH);

    private static final OrderedInterval FROM_TWO = above(2);
    private static final OrderedInterval FROM_THREE = above(3);
    private static final OrderedInterval CROSSED =
            new OrderedInterval(Endpoint.inclusive(Count.of(5)), Endpoint.inclusive(Count.of(3)));

    /**
     * Every state a reading of one number can be in.
     *
     * <p>Nothing said of it, stopped at one of two places, stopped where no value is, and each of
     * those with a line on it nothing placed — which are the four the type names, crossed with
     * whether a line is waiting.
     */
    private static List<BoundaryState> states() {
        List<BoundaryState> out = new ArrayList<>();
        for (Map<DerivedNumber, BoundaryState.Left> left : List.of(
                Map.<DerivedNumber, BoundaryState.Left>of(),
                Map.<DerivedNumber, BoundaryState.Left>of(LENGTH,
                        new BoundaryState.Left.Known(FROM_TWO)),
                Map.<DerivedNumber, BoundaryState.Left>of(LENGTH,
                        new BoundaryState.Left.Known(FROM_THREE)),
                Map.<DerivedNumber, BoundaryState.Left>of(LENGTH,
                        new BoundaryState.Left.NothingLeft()),
                // A second number beside the first, so that a rule mixing them is here too.
                Map.<DerivedNumber, BoundaryState.Left>of(SIZE,
                        new BoundaryState.Left.Known(FROM_TWO)))) {
            for (Set<OpenEnd> open : List.of(Set.<OpenEnd>of(), Set.of(ONE_LINE),
                    Set.of(ANOTHER_LINE), Set.of(ONE_LINE, ANOTHER_LINE))) {
                out.add(new BoundaryState(left, open));
            }
        }
        return out;
    }

    @Test
    void aChoiceOfTwoOfThemIsTheSameEitherWayRound() {
        assertEquals(List.of(), turnedRound(BoundaryState::either, "||"),
                "a choice is between its alternatives, and which of them an author wrote first is"
                        + " not one of them");
    }

    @Test
    void andAConjunctionOfTwoOfThemIs() {
        assertEquals(List.of(), turnedRound(BoundaryState::both, "&&"),
                "and so is a conjunction");
    }

    @Test
    void andAChoiceDoesNotTurnOnHowItWasBracketed() {
        assertEquals(List.of(), rebracketed(BoundaryState::either, "||"),
                "the brackets an author put round three alternatives are not a fact about the"
                        + " rule");
    }

    @Test
    void norDoesAConjunction() {
        assertEquals(List.of(), rebracketed(BoundaryState::both, "&&"), "nor round three"
                + " conjuncts");
    }

    @Test
    void andOneOfThemWrittenTwiceIsItself() {
        List<String> differing = new ArrayList<>();
        for (BoundaryState each : states()) {
            if (!said(each.either(each)).equals(said(each))) {
                differing.add(said(each) + " || itself = " + said(each.either(each)));
            }
            if (!said(each.both(each)).equals(said(each))) {
                differing.add(said(each) + " && itself = " + said(each.both(each)));
            }
        }
        assertEquals(List.of(), differing,
                "a choice between one reading and itself is that reading, and so is a conjunction");
    }

    /**
     * The pairs {@code join} answers differently when they are written the other way round.
     *
     * <p>Only those. Every pair is asked and the ones that agree are what a passing run has to say
     * nothing about — held as two whole lists instead, a run that found one disagreement printed
     * every pair there is, and what had gone wrong was somewhere in it.
     */
    private static List<String> turnedRound(
            java.util.function.BinaryOperator<BoundaryState> join, String written) {
        List<String> differing = new ArrayList<>();
        for (BoundaryState one : states()) {
            for (BoundaryState other : states()) {
                String asked = said(join.apply(one, other));
                String answered = said(join.apply(other, one));
                if (!asked.equals(answered)) {
                    differing.add(said(one) + " " + written + " " + said(other)
                            + " = " + asked + ", turned round = " + answered);
                }
            }
        }
        return differing;
    }

    /** The triples {@code join} answers differently when the brackets move. */
    private static List<String> rebracketed(
            java.util.function.BinaryOperator<BoundaryState> join, String written) {
        List<String> differing = new ArrayList<>();
        for (BoundaryState one : states()) {
            for (BoundaryState other : states()) {
                for (BoundaryState third : states()) {
                    String left = said(join.apply(join.apply(one, other), third));
                    String right = said(join.apply(one, join.apply(other, third)));
                    if (!left.equals(right)) {
                        differing.add("(" + said(one) + " " + written + " " + said(other) + ") "
                                + written + " " + said(third) + " = " + left
                                + ", rebracketed = " + right);
                    }
                }
            }
        }
        return differing;
    }

    /**
     * And the states this walks over are the ones the type names, all of them.
     *
     * <p>Without this the rules above hold of whatever happened to be listed, and a state left out
     * is one no law was ever asked about — which is how two branches leaving the number no value
     * came to be answered by whichever was written second.
     */
    @Test
    void andEveryStateOfOneNumberIsWalkedOver() {
        Set<String> reached = new LinkedHashSet<>();
        for (BoundaryState each : states()) {
            reached.add(shapeOf(each, LENGTH));
        }
        assertEquals(Set.of("nothing said", "stopped somewhere", "nothing left",
                        "nothing said, and a line waiting", "stopped somewhere, and a line waiting",
                        "nothing left, and a line waiting"),
                reached,
                "every state the type names is one these laws were asked about");
    }

    /** Which of the states {@code number} is in, said in the type's own words. */
    private static String shapeOf(BoundaryState state, DerivedNumber number) {
        String said = state.knownAt(number) != null ? "stopped somewhere"
                : state.byNumber().containsKey(number) ? "nothing left" : "nothing said";
        return state.open().stream().anyMatch(each -> each.number().equals(number))
                ? said + ", and a line waiting" : said;
    }

    /** What a state says, spelled so that two of them can be held against each other. */
    private static String said(BoundaryState state) {
        Map<String, String> byNumber = new LinkedHashMap<>();
        state.byNumber().forEach((number, left) -> byNumber.put(number.toString(),
                left instanceof BoundaryState.Left.Known it ? it.range().toString()
                        : "nothing left"));
        List<String> open = new ArrayList<>();
        state.open().forEach(each -> open.add(each == ONE_LINE ? "one line" : "another line"));
        return new java.util.TreeMap<>(byNumber) + " with " + open.stream().sorted().toList();
    }

    private static OrderedInterval above(int value) {
        return new OrderedInterval(Endpoint.inclusive(Count.of(value)), null);
    }

    /** And the two lines are two, so a law that lost one of them is not passing by having one. */
    @Test
    void andTheTwoLinesAreTwo() {
        assertTrue(ONE_LINE != ANOTHER_LINE && !ONE_LINE.equals(ANOTHER_LINE),
                "each line an author wrote is the one it is");
    }
}
