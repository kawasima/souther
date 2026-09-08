package souther.compiler.values;

import org.junit.jupiter.api.Test;

import souther.compiler.regex.Language;
import souther.compiler.regex.PatternParser;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternRead;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What two conjoined readings come to is settled by the purse the caller names, and not by which of
 * them the call was written on.
 *
 * <p>Where two readings name the same position, the factor they merge into holds a set neither of
 * them holds — and building it takes a machine somebody pays for. That answer is the caller's, so
 * the purse is the caller's ({@link ConjoinedAdmissibleValues#meet}). Read off one of the operands
 * instead, the pair would have two purses to be composed under and which one paid would be settled
 * by which side {@code .meet} was typed on: a composition the smaller purse refused gets built under
 * the roomier one, and the same two readings the other way round are refused again.
 *
 * <p>So the two orders are asked of purses that are equal and separate. Equal, because the same
 * question has to be put to both; separate, because an allowance is what one answer has spent so far
 * — asked twice of one of them, the second order would be measuring a purse the first order had
 * already emptied.
 */
class WhatAConjunctionCostsIsTheComposersToSayTest {

    private static final Sameness.Block<String> HERE = Sameness.Block.of("here");

    /** More than anything here asks for, so that a measurement is of the work and not of a limit. */
    private static final int PLENTY = 10_000_000;

    /**
     * Two readings of one position, each admitting the strings whose length divides a number.
     *
     * <p>Chosen because their meet is a machine as large as the two numbers multiply to and is never
     * empty: what is wanted is a composition big enough to be refused by a purse that has room for
     * each reading on its own. Values written out would be met by set arithmetic and cost nothing,
     * and there would be no purse to tell apart.
     */
    private static ConjoinedAdmissibleValues<String> everyMultipleOf(int every) {
        return ConjoinedAdmissibleValues.of(
                PlannedValues.at("here",
                                AdmittedPlan.of(ValueSet.matching(
                                        language("(?:[0-9]{" + every + "})+"))))
                        .resolve(AsACompilationAllows.forAdmittedValues()).values());
    }

    private static Language language(String regex) {
        PatternRead read = PatternParser.read(regex);
        Language made = PatternPlan.of(assertInstanceOf(PatternRead.Read.class, read, regex)
                .syntax()).compile(PatternPlan.Budget.OF_ADMITTED_VALUES.meter());
        assertNotNull(made, regex);
        return made;
    }

    /** An allowance of {@code inAll} states in all, no one machine being larger than the lot. */
    private static Allowance<String> allowing(int inAll) {
        return Allowance.of(new PatternPlan.Budget(inAll, inAll));
    }

    /** What building this composition takes at the position, measured rather than reckoned. */
    private static int costOfConjoining() {
        Allowance<String> measuring = allowing(PLENTY);
        everyMultipleOf(3).meet(everyMultipleOf(4), measuring);
        return PLENTY - measuring.left(HERE);
    }

    /**
     * A purse with room builds the same set whichever reading the call was written on.
     *
     * <p>And the same amount of it is spent, which is the half a set alone would not show: an answer
     * that came out equal while one order had paid for a machine the other borrowed would be two
     * answers that agree today.
     */
    @Test
    void aRoomyPurseAnswersTheSameEitherWayRound() {
        Allowance<String> forward = allowing(PLENTY);
        Allowance<String> backward = allowing(PLENTY);

        ConjoinedAdmissibleValues<String> one = everyMultipleOf(3).meet(everyMultipleOf(4), forward);
        ConjoinedAdmissibleValues<String> other =
                everyMultipleOf(4).meet(everyMultipleOf(3), backward);

        assertFalse(one.at("here").isAny(),
                "the composition was built, or the two orders agree about nothing");
        assertEquals(one.at("here"), other.at("here"));
        assertTrue(one.projectionExactAt("here"));
        assertEquals(one.projectionExactAt("here"), other.projectionExactAt("here"));
        assertEquals(List.of(), one.whyUnread("here"));
        assertEquals(one.whyUnread("here"), other.whyUnread("here"));
        assertTrue(forward.left(HERE) < PLENTY,
                "the purse the caller named is the one that paid — built out of one this call was"
                        + " not told about, an answer nobody budgeted for would come back exact");
        assertEquals(PLENTY - forward.left(HERE), PLENTY - backward.left(HERE),
                "and the same is spent building it");
        assertEquals(forward.spentSoFar(), backward.spentSoFar(),
                "over every purse the answer opened and not the one this test can name: which"
                        + " blocks a reading has is settled by its equalities, so a composition"
                        + " that opened another would spend where nothing was watching");
    }

    /**
     * And a purse that is short gives up the same way round.
     *
     * <p>Room for either reading and one state short of room for what they come to, so the only
     * thing that can refuse this is the composition itself. What comes back is every value, and the
     * shortfall arrives beside it.
     */
    @Test
    void aShortPurseGivesUpTheSameEitherWayRound() {
        int oneStateShort = costOfConjoining() - 1;
        Allowance<String> forward = allowing(oneStateShort);
        Allowance<String> backward = allowing(oneStateShort);

        ConjoinedAdmissibleValues<String> one = everyMultipleOf(3).meet(everyMultipleOf(4), forward);
        ConjoinedAdmissibleValues<String> other =
                everyMultipleOf(4).meet(everyMultipleOf(3), backward);

        assertTrue(one.at("here").isAny(), "the composition was refused, which is what is being"
                + " asked about — a purse this test did not make short says nothing");
        assertEquals(one.at("here"), other.at("here"));
        assertFalse(one.projectionExactAt("here"));
        assertEquals(one.projectionExactAt("here"), other.projectionExactAt("here"));
        assertEquals(List.of(UnreadReason.EXACT_VALUES_TOO_COSTLY), one.whyUnread("here"));
        assertEquals(one.whyUnread("here"), other.whyUnread("here"));
        assertEquals(Set.of(HERE), forward.spent());
        assertEquals(forward.spent(), backward.spent());
        assertEquals(forward.spentSoFar(), backward.spentSoFar(),
                "and the same is spent finding out, over every purse the answer opened");
    }

    /**
     * The purse that was named is what the answer follows.
     *
     * <p>The control the two tests above rest on: they say the orders agree, and that says something
     * only where the purses do not. The same pair of readings comes to two different answers under
     * two purses, and the difference is which purse the caller named — a reading holds none, so
     * there is no other place the choice could have come from.
     */
    @Test
    void andTheTwoPursesAnswerDifferently() {
        Allowance<String> roomy = allowing(PLENTY);
        Allowance<String> oneStateShort = allowing(costOfConjoining() - 1);

        ValueSet built = everyMultipleOf(3).meet(everyMultipleOf(4), roomy).at("here");
        ValueSet refused = everyMultipleOf(3).meet(everyMultipleOf(4), oneStateShort).at("here");

        assertFalse(built.isAny());
        assertTrue(refused.isAny());
    }
}
