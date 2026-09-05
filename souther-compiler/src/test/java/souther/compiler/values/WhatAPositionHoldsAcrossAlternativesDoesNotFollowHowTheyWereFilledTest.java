package souther.compiler.values;

import org.junit.jupiter.api.Test;

import souther.compiler.regex.PatternParser;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternRead;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A set of alternatives is a set, and what a position holds across them costs one thing.
 *
 * <p>{@link AdmissibleValues.Held.Alternatives} says so of itself: the alternatives are a union, so
 * the same one written twice is one alternative and the order two of them were met in is not part of
 * the answer. What a position holds across them was worked out by folding them two at a time in the
 * order the set happened to be filled — so three alternatives holding languages at one position cost
 * whatever the first pair came to, and two sets that are equal were two amounts of work.
 *
 * <p>Which is not a schedule that needs sorting. The alternatives are handed over together and what
 * they come to is said as one plan, which is the same arrangement every other composition here uses:
 * the order is the plan's and there is no fold left to have an order of its own.
 */
class WhatAPositionHoldsAcrossAlternativesDoesNotFollowHowTheyWereFilledTest {

    private static ValueSet matching(String regex) {
        PatternRead said = PatternParser.read(regex);
        return ValueSet.matching(PatternPlan.of(
                assertInstanceOf(PatternRead.Read.class, said, regex).syntax())
                .compile(PatternPlan.Budget.OF_ADMITTED_VALUES.meter()));
    }

    /** One alternative, holding {@code set} at the one position there is. */
    private static AdmissibleValues.Alternative<String> box(ValueSet set) {
        return AdmissibleValues.Alternative.at(Map.of("here", set));
    }

    /**
     * Three alternatives whose pairs cost three different things.
     *
     * <p>Two of them are large and share little; the third is one string. A fold that reaches the
     * small one first is a join with a written value, and one that meets the two large ones first is
     * a machine of their sum.
     */
    private static List<AdmissibleValues.Alternative<String>> three() {
        return List.of(box(matching("x|a{300}")), box(matching("x|b{300}")),
                box(ValueSet.just(Value.text("x"))));
    }

    /** The same three in every order they can be filled, and one answer between them. */
    @Test
    void everyOrderOfFillingTheSetLeavesOneAnswerAndOneCost() {
        ValueSet first = null;
        Set<Sameness.Block<String>> gaveUp = null;
        int spent = -1;
        for (List<Integer> order : List.of(List.of(0, 1, 2), List.of(0, 2, 1), List.of(1, 0, 2),
                List.of(1, 2, 0), List.of(2, 0, 1), List.of(2, 1, 0))) {
            List<AdmissibleValues.Alternative<String>> boxes = three();
            Set<AdmissibleValues.Alternative<String>> filled = new LinkedHashSet<>();
            order.forEach(each -> filled.add(boxes.get(each)));

            Allowance<String> by = AsACompilationAllows.forAdmittedValues();
            AdmissibleValues.Held.Alternatives.Made<String> made =
                    AdmissibleValues.Held.Alternatives.of(filled, by);

            AdmissibleValues.Held<String> held = made.held();
            assertInstanceOf(AdmissibleValues.Held.Alternatives.class, held);
            ValueSet across = ((AdmissibleValues.Held.Alternatives<String>) held).at("here");
            if (first == null) {
                first = across;
                gaveUp = made.gaveUp();
                spent = spentOn(by);
                continue;
            }
            assertEquals(first, across, "the values, filled as " + order);
            assertEquals(gaveUp, made.gaveUp(), "and what was given up on, filled as " + order);
            assertEquals(spent, spentOn(by), "and the states, filled as " + order);
        }
        assertTrue(spent >= 0, "and something was measured");
    }

    /** How much of the position's allowance has gone. */
    private static int spentOn(Allowance<String> by) {
        return PatternPlan.Budget.OF_ADMITTED_VALUES.mostBuilt()
                - by.left(Sameness.Block.of("here"));
    }
}
