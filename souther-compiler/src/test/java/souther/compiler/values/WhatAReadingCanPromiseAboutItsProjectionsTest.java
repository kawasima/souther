package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a reading may promise about the values it reports, which is two questions and not one.
 *
 * <p>What is held is one set per position, standing for the product of them. A choice between
 * alternatives written at two positions is a union of two products, and the smallest product holding
 * that union is the pair of unions — so the relation is lost there. The projections are not: the
 * projection of a union is the union of the projections, so what each position is reported as
 * holding is still exactly what the read rules leave it.
 *
 * <p>It is the next conjunction that spends what the choice lost. Two such readings are met one
 * position at a time, and a pair the two of them refuse between them is a pair neither intersection
 * can exclude — so the values come back wider than the rules are with every rule read.
 *
 * <p>Both are what this reading can prove and not what is true of the model. A conservative rule may
 * answer false where the values happen to be exact, which is why they are read as guarantees: what
 * they say holds, and what they do not say is unknown.
 *
 * <p><b>Said of a choice whose alternatives are merged</b> — {@code join} here, which is what a
 * declaration too large to hold them apart is read by. Held apart the same witness is answered
 * exactly, and that is {@link AlternativesHeldApartAnswerThePositionExactlyTest}.
 */
class WhatAReadingCanPromiseAboutItsProjectionsTest {

    private static final String A = "a";
    private static final String B = "b";
    private static final String C = "c";
    private static final Value FIVE = Value.text("5");
    private static final Value SIX = Value.text("6");
    private static final Value ZERO = Value.text("0");
    private static final Value ONE = Value.text("1");

    /** One rule worked out, which is how a reading of one position is come by. */
    private static AdmissibleValues<String> says(String atom, Value value) {
        return built(plans(atom, value));
    }

    /** What puts the sets of these readings together. Every set here is values written out, so
     *  nothing is built and no allowance is spent. */
    private static final Allowance<String> SETS = AsACompilationAllows.forAdmittedValues();

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
    private static AdmissibleValues<String> built(PlannedValues<String> planned) {
        return planned.resolve(SETS).values();
    }

    /** A reading with nothing read is exact about everything it says, which is nothing. */
    @Test
    void aReadingThatSaysNothingPromisesBoth() {
        AdmissibleValues<String> nothing = AdmissibleValues.top();

        assertTrue(nothing.relationExact(), "an empty reading is the whole of what it read");
        assertTrue(nothing.projectionExactAt(A), "and every position of it is at ANY, exactly");
    }

    /** A choice whose alternatives are written at one position between them is a product, so the
     *  union of two of them is one and nothing is lost. */
    @Test
    void aChoiceAtOnePositionPromisesItsRelation() {
        AdmissibleValues<String> either = built(plans(A, FIVE).joinLive(plans(A, SIX)));

        assertEquals(ValueSet.oneOf(Set.of(FIVE, SIX)), either.at(A));
        assertTrue(either.relationExact(), "two values of one position are a product");
        assertTrue(either.projectionExactAt(A));
    }

    /**
     * A choice reaching across two positions loses the relation and keeps the projections.
     *
     * <p>The witness of issue #877, read as far as its first invariant. {@code a} really is left
     * {@code 5} or {@code 6} by that clause alone, and reporting so is right; what is gone is which
     * {@code b} went with which.
     */
    @Test
    void aChoiceAcrossTwoPositionsKeepsItsProjectionsAndLosesItsRelation() {
        AdmissibleValues<String> one = built(pair(FIVE, ZERO).joinLive(pair(SIX, ONE)));

        assertEquals(ValueSet.oneOf(Set.of(FIVE, SIX)), one.at(A));
        assertEquals(ValueSet.oneOf(Set.of(ZERO, ONE)), one.at(B));
        assertTrue(one.projectionExactAt(A), "the projection of a union is the union of projections");
        assertFalse(one.relationExact(), "which b went with which a is what the product cannot say");
    }

    /**
     * And the conjunction of two of them can promise neither.
     *
     * <p>The whole witness of issue #877. Only {@code (a = 5, b = 0)} satisfies both invariants —
     * {@code (6, 1)} is refused by the second and {@code (6, 0)} by the first — so {@code a} is left
     * {@code 5} and nothing else, while the reading meets {@code {5, 6}} with {@code {5, 6}} at
     * {@code a} and comes back with both.
     */
    @Test
    void twoChoicesAcrossTwoPositionsMetTogetherPromiseNeither() {
        AdmissibleValues<String> one = built(pair(FIVE, ZERO).joinLive(pair(SIX, ONE)));
        AdmissibleValues<String> two = built(pair(FIVE, ZERO).joinLive(pair(SIX, ZERO)));

        AdmissibleValues<String> both = one.meet(two, SETS);

        assertEquals(ValueSet.oneOf(Set.of(FIVE, SIX)), both.at(A), "which is wider than the rules leave it");
        assertFalse(both.projectionExactAt(A), "so the reading may not say this is what a holds");
        assertFalse(both.relationExact());
    }

    /** A conjunction of readings that are each a product is a product, and says so. */
    @Test
    void aConjunctionOfProductsPromisesBoth() {
        AdmissibleValues<String> both = built(pair(FIVE, ZERO).meet(plans(A, FIVE)));

        assertTrue(both.relationExact(), "the intersection of two products is a product");
        assertTrue(both.projectionExactAt(A));
    }

    /**
     * A position no lost correlation reaches keeps its promise.
     *
     * <p>What a choice across two positions costs is those positions. A third, written about by a
     * clause of its own, is left where it was: the relation factors into the tangled part and a
     * product over everything else, and the projection of a product is exact at each of its places.
     *
     * <p>The promise is asked of a position for this reason. Held as one answer for the whole
     * reading, "not every position is shown exact" is the only thing it can say, and a reader
     * wanting the position gets that sentence about each of them — which is a different claim, and
     * a false one here.
     */
    @Test
    void aPositionNoLostCorrelationReachesKeepsItsPromise() {
        AdmissibleValues<String> one = built(pair(FIVE, ZERO).joinLive(pair(SIX, ONE)));
        AdmissibleValues<String> two = built(pair(FIVE, ZERO).joinLive(pair(SIX, ZERO)));
        AdmissibleValues<String> apart = says(C, ZERO);

        AdmissibleValues<String> all = one.meet(two, SETS).meet(apart, SETS);

        assertFalse(all.projectionExactAt(A), "a and b are what the choice reached across");
        assertFalse(all.projectionExactAt(B));
        assertTrue(all.projectionExactAt(C), "and nothing the choice lost reaches c");
        assertEquals(ValueSet.just(ZERO), all.at(C));
    }

    /** A promise about the relation is a promise about the projections, never the other way. */
    @Test
    void promisingTheRelationIsPromisingTheProjections() {
        for (AdmissibleValues<String> each : java.util.List.<AdmissibleValues<String>>of(
                AdmissibleValues.top(),
                says(A, FIVE),
                built(plans(A, FIVE).joinLive(plans(A, SIX))),
                built(pair(FIVE, ZERO).joinLive(pair(SIX, ONE))),
                built(pair(FIVE, ZERO).joinLive(pair(SIX, ONE))
                        .meet(pair(FIVE, ZERO).joinLive(pair(SIX, ZERO)))))) {
            assertTrue(!each.relationExact() || each.projectionExactAt(A),
                    each + " promises its relation and not its projections");
        }
    }
}
