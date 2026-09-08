package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A choice whose alternatives are kept apart answers each position what the rules leave it.
 *
 * <p>Merged into one product, a choice reaching across two positions leaves every projection right
 * and the relation gone, and it is the next conjunction that spends what was lost: two such
 * readings are met one position at a time, and a pair the two of them refuse between them is a pair
 * neither intersection excludes.
 *
 * <p>Kept apart, the conjunction meets them pairwise. Three of the four pairs of issue #877's
 * witness leave nothing, and the one that stands is what the model leaves — with nothing to
 * qualify.
 *
 * <p>What the same witness comes to merged is
 * {@link WhatAReadingCanPromiseAboutItsProjectionsTest}, which is where the other reading is said.
 */
class AlternativesHeldApartAnswerThePositionExactlyTest {

    private static final String A = "a";
    private static final String B = "b";
    private static final String C = "c";
    private static final Value FIVE = Value.text("5");
    private static final Value SIX = Value.text("6");
    private static final Value ZERO = Value.text("0");
    private static final Value ONE = Value.text("1");

    /** What puts the sets of one reading together. Every set here is written out, so nothing is
     *  built and no allowance is spent. */
    private final Allowance<String> sets = AsACompilationAllows.forAdmittedValues();


    /** One rule while it is still a description, which is where a choice between two of them is
     *  taken. */
    private static PlannedValues<String> plans(String atom, Value value) {
        return PlannedValues.at(atom, AdmittedPlan.of(ValueSet.just(value)));
    }

    /** Both positions of one alternative, which is a product and is held as one. */
    private static PlannedValues<String> pair(Value a, Value b) {
        return plans(A, a).meet(plans(B, b));
    }

    /** A description worked out, which is the only way a reading holding alternatives is made. */
    private AdmissibleValues<String> built(PlannedValues<String> planned) {
        return planned.resolve(sets).values();
    }

    /**
     * The witness of issue #877, answered.
     *
     * <p>Only {@code (a = 5, b = 0)} satisfies both invariants — {@code (6, 1)} is refused by the
     * second and {@code (6, 0)} by the first. Merged, the reading meets {@code {5, 6}} with
     * {@code {5, 6}} at {@code a} and comes back with both; held apart, three of the four pairs
     * leave nothing.
     */
    @Test
    void twoChoicesAcrossTwoPositionsLeaveTheOnePairThatStands() {
        AdmissibleValues<String> one = built(pair(FIVE, ZERO).joinLiveApart(pair(SIX, ONE)));
        AdmissibleValues<String> two = built(pair(FIVE, ZERO).joinLiveApart(pair(SIX, ZERO)));

        AdmissibleValues<String> both = one.meet(two, sets);

        assertEquals(ValueSet.just(FIVE), both.at(A));
        assertEquals(ValueSet.just(ZERO), both.at(B));
        assertTrue(both.relationExact(), "nothing was merged, so what is held is what was read");
        assertTrue(both.projectionExactAt(A));
        assertTrue(both.projectionExactAt(B));
    }

    /**
     * A choice is one connective and not a tree, and what it leaves does not follow the brackets.
     *
     * <p>Said of what is held and not only of what is answered. A union is a set: the same
     * alternative offered twice is one alternative, the order two of them were met in is not part
     * of it, and three of them bracketed either way are the same three.
     */
    @Test
    void theAlternativesAreASetAndNotASequence() {
        PlannedValues<String> a = pair(FIVE, ZERO);
        PlannedValues<String> b = pair(SIX, ONE);
        PlannedValues<String> c = pair(SIX, ZERO);

        assertEquals(built(a.joinLiveApart(b)).held(),
                built(b.joinLiveApart(a)).held(),
                "either order, one union");
        assertEquals(built(a).held(), built(a.joinLiveApart(a)).held(),
                "and the same alternative twice is one");
        assertEquals(built(a.joinLiveApart(b).joinLiveApart(c)).held(),
                built(a.joinLiveApart(b.joinLiveApart(c))).held(),
                "and three of them are the same three, bracketed either way");
    }

    /** Held apart, three alternatives are three, which is what a merged one cannot say. */
    @Test
    void whatIsHeldIsTheAlternativesAndNotTheirHull() {
        AdmissibleValues<String> three = built(pair(FIVE, ZERO).joinLiveApart(pair(SIX, ONE))
                .joinLiveApart(pair(SIX, ZERO)));

        assertEquals(3, ((AdmissibleValues.Held.Alternatives<String>) three.held()).boxes().size());
        assertNotEquals(three.held(), built(pair(FIVE, ZERO).joinLive(pair(SIX, ONE))
                .joinLive(pair(SIX, ZERO))).held());
        assertEquals(ValueSet.oneOf(Set.of(FIVE, SIX)), three.at(A), "and the projection is theirs");
    }

    /**
     * A position beside them is answered by its own clause.
     *
     * <p>What the alternatives are written at is where a union is not a product, and a position
     * outside them is left where its own rule put it — the pairs multiply and every one of them
     * says the same thing about it.
     */
    @Test
    void aPositionBesideThemKeepsItsOwnAnswer() {
        AdmissibleValues<String> one = built(pair(FIVE, ZERO).joinLiveApart(pair(SIX, ONE)));
        AdmissibleValues<String> two = built(pair(FIVE, ZERO).joinLiveApart(pair(SIX, ZERO)));
        AdmissibleValues<String> apart = built(plans(C, ZERO));

        AdmissibleValues<String> all = one.meet(two, sets).meet(apart, sets);

        assertEquals(ValueSet.just(ZERO), all.at(C));
        assertEquals(ValueSet.just(FIVE), all.at(A));
        assertTrue(all.relationExact());
    }

    /**
     * A choice between two alternatives nobody can take admits nothing, and blames no position for
     * it where they fail at different ones.
     *
     * <p>The case a union of boxes may not be an empty union. Held as one, the two of them leave
     * nothing at no position between them, and that is an answer about the whole value.
     */
    @Test
    void aChoiceBetweenTwoImpossibleAlternativesNamesNoPosition() {
        PlannedValues<String> here = plans(A, FIVE).meet(plans(A, SIX));
        PlannedValues<String> there = plans(B, ZERO).meet(plans(B, ONE));

        // Neither branch is one anybody can be in, which is the settlement a caller reaches for
        // once both are known dead rather than a choice between two that stand.
        AdmissibleValues<String> either = built(here.bothDead(there));

        assertTrue(either.isBottom(), "neither alternative can be taken");
        assertEquals(ValueSet.ANY, either.at(A), "and neither position is one the choice empties");
        assertEquals(ValueSet.ANY, either.at(B));
    }

    /** What the alternatives promise between them is unchanged by holding them apart: the promise
     *  is about which rules went unread, and holding a union reads none of them. */
    @Test
    void holdingThemApartPromisesWhatMergingThemDid() {
        for (List<PlannedValues<String>> each : List.of(
                List.of(plans(A, FIVE), plans(A, SIX)),
                List.of(pair(FIVE, ZERO), pair(SIX, ONE)),
                List.of(plans(A, FIVE), PlannedValues.<String>unreadable(Set.of(B),
                        UnreadReason.FORM_NOT_READ)))) {
            AdmissibleValues<String> merged = built(each.get(0).joinLive(each.get(1)));
            AdmissibleValues<String> apart = built(each.get(0).joinLiveApart(each.get(1)));

            assertEquals(merged.guaranteedAt(A), apart.guaranteedAt(A), each + " at a");
            assertEquals(merged.guaranteedAt(B), apart.guaranteedAt(B), each + " at b");
            assertEquals(merged.guaranteedTogether(), apart.guaranteedTogether(), each.toString());
            assertEquals(merged.standing(), apart.standing(), each.toString());
        }
    }
}
