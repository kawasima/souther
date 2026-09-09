package souther.compiler.values;

import org.junit.jupiter.api.Test;

import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternParser;
import souther.compiler.regex.PatternRead;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a block of a conjunction is promised is one question, put to the allowance once.
 *
 * <p>A conjunction's block covers several of each side's own, and what it is promised is what every
 * one of those promises. Gathered per side and met, the sides' halves are two sets nobody asked
 * about, built and charged to the block before the set that was wanted is built from them — so what
 * the block costs is how many blocks each side happened to hold its positions in.
 *
 * <p><b>Asked of sets that cost something.</b> A promise written out as values is free: the
 * descriptions meet plainly and every arrangement of them comes to one set without a machine. It is
 * a promise the rules describe rather than list that is built under an allowance, so that is what
 * these readings promise — with anything else the halves and the whole are one plan and this
 * measures nothing.
 *
 * <p>And of a conjunction where both sides hold two of their own blocks inside one of its. With
 * fewer, a side's half is a meet with what a reading promises where it promised nothing, which is
 * every value and drops out — the plan comes to the same thing and no second question is put.
 */
class WhatABlockIsPromisedIsOneQuestionAndCostsOnceTest {

    /** What the rules of a position describe rather than list, which is built under an allowance
     *  and is what makes a meet of two of them cost anything. */
    private static ValueSet matching(String regex) {
        PatternRead said = PatternParser.read(regex);
        return ValueSet.matching(
                PatternPlan.of(assertInstanceOf(PatternRead.Read.class, said).syntax())
                        .compile(PatternPlan.Budget.OF_ADMITTED_VALUES.meter()));
    }

    private static final ValueSet ONE = matching("x|a{40}");
    private static final ValueSet TWO = matching("x|b{40}");
    private static final ValueSet THREE = matching("x|c{40}");
    private static final ValueSet FOUR = matching("x|d{40}");

    /**
     * One side, holding two pairs as one value and promising each pair its own set.
     *
     * <p>Both of its blocks are inside the conjunction's one, so both of its promises are about
     * that block's value and neither of them is every value.
     */
    private static AdmissibleValues<String> side(String one, String other, ValueSet said,
                                                 String third, String fourth, ValueSet also) {
        return built(PlannedValues.<String>holdingAsOne(one, other)
                .meet(PlannedValues.at(one, AdmittedPlan.of(said)))
                .meet(PlannedValues.<String>holdingAsOne(third, fourth))
                .meet(PlannedValues.at(third, AdmittedPlan.of(also))));
    }

    private static AdmissibleValues<String> built(PlannedValues<String> planned) {
        return planned.resolve(AsACompilationAllows.forAdmittedValues()).values();
    }

    /** What every one of these promises, said as one plan. */
    private static AdmittedPlan asOnePlan(ValueSet... these) {
        return AdmittedPlan.meeting(List.of(these).stream().map(AdmittedPlan::of).toList());
    }

    /**
     * The conjunction puts the whole meet to the allowance, and never a side's half of it.
     *
     * <p>{@code p == q} with {@code r == s} met with {@code q == r} and {@code p == s} holds all
     * four as one value. Each side promises two of the four sets, and what stands there is what all
     * four admit — one plan, worked out once, and no plan of one side's two.
     */
    @Test
    void aConjunctionAsksForTheWholeMeetAndNeverASidesHalf() {
        Allowance<String> sets = AsACompilationAllows.forAdmittedValues();
        AdmissibleValues<String> met =
                side("p", "q", ONE, "r", "s", TWO).meet(side("q", "r", THREE, "p", "s", FOUR), sets);

        Sameness.Block<String> block = met.blockOf("p");
        assertEquals(4, block.members().size(), "the four are one value, or this asks nothing");
        assertNotNull(sets.known(block, asOnePlan(ONE, TWO, THREE, FOUR)),
                "what the block is promised was put as one question");
        assertNull(sets.known(block, asOnePlan(ONE, TWO)),
                "and no side's own two were met into a set of their own");
        assertNull(sets.known(block, asOnePlan(THREE, FOUR)),
                "on either side");
    }

    /** And what it promises is what all four admit, which is the one string they share. */
    @Test
    void andWhatItPromisesIsWhatAllFourAdmit() {
        Allowance<String> sets = AsACompilationAllows.forAdmittedValues();
        AdmissibleValues<String> met =
                side("p", "q", ONE, "r", "s", TWO).meet(side("q", "r", THREE, "p", "s", FOUR), sets);

        ValueSet promised = met.guaranteedAt("p");
        assertTrue(promised.has(Value.text("x")), "the string all four admit");
        assertFalse(promised.has(Value.text("a".repeat(40))), "and none that only one of them does");
        assertFalse(promised.has(Value.text("d".repeat(40))));
        assertEquals(promised, met.guaranteedAt("s"), "which is one promise across the block");
    }

    /** That these sets are ones a machine is made for, so the meet above is work the allowance
     *  sees rather than a description that folds itself away. */
    @Test
    void theseArePromisesTheAllowanceIsAskedToBuild() {
        Allowance<String> sets = AsACompilationAllows.forAdmittedValues();
        int before = sets.spentSoFar();
        sets.meetingPromised(Sameness.Block.of("p"), List.of(ONE, TWO));

        assertTrue(sets.spentSoFar() > before, "a meet of these costs something");
    }
}
