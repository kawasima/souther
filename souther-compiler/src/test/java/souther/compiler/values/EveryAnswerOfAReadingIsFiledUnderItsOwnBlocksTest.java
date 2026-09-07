package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every answer a reading holds, filed under a block the reading itself is a product over.
 *
 * <p>What a position admits, what it is promised, and whether either of those is exact are answers
 * about the one value the rules hold that position as — so they are filed under the block, and
 * the block has to be one of this reading's own. Filed under somebody else's, a reader asking
 * about a position would look under a coordinate the reading does not answer in and find nothing,
 * which reads as a position nobody said anything about.
 *
 * <p>The connectives are what can put them out of step: a conjunction leaves a coarser relation
 * than either side stated and a choice leaves a finer one, so what each side said has to be
 * carried across rather than kept as it arrived.
 *
 * <p>And a reading no equality reached is a product over its positions one at a time, which is what
 * every reading was before any of this. That is asserted beside the rest, because a change of
 * coordinates that moved those would be a change to every model there is.
 */
class EveryAnswerOfAReadingIsFiledUnderItsOwnBlocksTest {

    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    private static Allowance<String> allowing() {
        return AsACompilationAllows.forAdmittedValues();
    }

    /** A rule while it is still a description, which is where a choice between two of them is
     *  taken. */
    private static PlannedValues<String> plans(String atom, Value value) {
        return PlannedValues.at(atom, AdmittedPlan.of(ValueSet.just(value)));
    }

    /** A description worked out, which is the only way a reading holding alternatives is made. */
    private static AdmissibleValues<String> built(PlannedValues<String> planned,
                                                  Allowance<String> by) {
        return planned.resolve(by).values();
    }

    /** One rule about one position, worked out. */
    private static AdmissibleValues<String> says(String atom, Value value) {
        return built(plans(atom, value), allowing());
    }

    /** An equality between two positions, worked out. */
    private static AdmissibleValues<String> heldAsOne(String here, String there) {
        return built(PlannedValues.holdingAsOne(here, there), allowing());
    }

    /** Every coordinate this reading files an answer under. */
    private static Set<Sameness.Block<String>> filedUnder(AdmissibleValues<String> reading) {
        Set<Sameness.Block<String>> out = new LinkedHashSet<>(reading.guaranteed().keySet());
        out.addAll(reading.tangled());
        out.addAll(reading.widened());
        return out;
    }

    /** That every one of them is a block this reading is a product over. */
    private static void filedUnderItsOwn(AdmissibleValues<String> reading) {
        Sameness<String> heldAsOne = reading.sameness();
        filedUnder(reading).forEach(block -> block.members().forEach(position ->
                assertEquals(block, heldAsOne.blockOf(position),
                        () -> "an answer at " + block + " is filed under a coordinate "
                                + reading + " does not answer in")));
    }

    /** A reading of one position is a product over that position, and files its answers there. */
    @Test
    void aReadingOfOnePositionIsAProductOverIt() {
        AdmissibleValues<String> reading = says("p", A);

        assertTrue(reading.sameness().isDiscrete());
        assertEquals(Sameness.Block.of("p"), reading.blockOf("p"));
        filedUnderItsOwn(reading);
    }

    /** An equality makes one block of two positions, and both of them answer in it. */
    @Test
    void anEqualityMakesTheCoordinateBothPositionsAnswerIn() {
        AdmissibleValues<String> reading = heldAsOne("p", "r")
                .meet(says("p", A), allowing());

        assertEquals(reading.blockOf("p"), reading.blockOf("r"));
        assertEquals(Set.of("p", "r"), reading.blockOf("p").members());
        filedUnderItsOwn(reading);
    }

    /**
     * And what is stated at one of them is what the other admits, which is the whole of what an
     * equality says.
     */
    @Test
    void whatIsStatedAtOneOfThemIsWhatTheOtherAdmits() {
        AdmissibleValues<String> reading = heldAsOne("p", "r")
                .meet(says("p", A), allowing());

        assertEquals(ValueSet.just(A), reading.at("r"));
        assertEquals(reading.at("p"), reading.at("r"));
    }

    /** Two rules the equality cannot both hold leave the reading nothing, and say which places. */
    @Test
    void twoRulesOneValueCannotBothSatisfyLeaveNothing() {
        AdmissibleValues<String> reading = heldAsOne("p", "r")
                .meet(says("p", A), allowing())
                .meet(says("r", B), allowing());

        assertTrue(reading.isBottom());
        assertEquals(Set.of(Set.of("p", "r")),
                reading.refusedBy().blocks().stream().map(Sameness.Block::members)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    /** A conjunction leaves a coarser relation than either side stated, and carries what each of
     *  them said into it. */
    @Test
    void aConjunctionCarriesEachSidesAnswersIntoTheCoarserRelation() {
        AdmissibleValues<String> reading = says("p", A)
                .meet(says("r", A), allowing())
                .meet(heldAsOne("p", "r"), allowing());

        assertEquals(Set.of("p", "r"), reading.blockOf("p").members());
        filedUnderItsOwn(reading);
    }

    /** A choice leaves the finer relation both branches state, and carries what each of them said
     *  apart into it. */
    @Test
    void aChoiceCarriesEachBranchesAnswersIntoTheFinerRelation() {
        AdmissibleValues<String> reading = built(
                PlannedValues.<String>holdingAsOne("p", "r").meet(plans("p", A))
                        .joinLive(PlannedValues.<String>holdingAsOne("p", "s")
                                .meet(plans("p", B))), allowing());

        assertTrue(reading.sameness().isDiscrete(),
                "neither equality is stated by both branches");
        filedUnderItsOwn(reading);
    }

    /** And held apart, each alternative keeps the equality it states. */
    @Test
    void alternativesHeldApartKeepTheirOwnEqualities() {
        AdmissibleValues<String> reading = built(
                PlannedValues.<String>holdingAsOne("p", "r").meet(plans("p", A))
                        .joinLiveApart(PlannedValues.<String>holdingAsOne("p", "s")
                                .meet(plans("p", B))), allowing());

        assertTrue(reading.sameness().isDiscrete(),
                "what the union can say of a position is what both alternatives say");
        assertEquals(ValueSet.oneOf(Set.of(A, B)), reading.at("p"),
                "and each alternative still holds what it stated");
        filedUnderItsOwn(reading);
    }

    /**
     * A choice between two equalities has lost a relation, and says so.
     *
     * <p>Merged into one product, what is left holds neither pair as one value — the relation is
     * {@code p = r} or {@code p = s}, and no product states that. So the reading cannot promise
     * that what it holds is the whole of the relation, though what it says about each position on
     * its own is exact.
     *
     * <p>An equality narrows nothing anywhere, so the only thing that can carry "this rule shaped
     * the relation" is the promise's footprint. Left out of it, a choice between two equalities
     * would read as one nothing was lost by.
     */
    @Test
    void aChoiceBetweenTwoEqualitiesLosesARelationAndSaysSo() {
        AdmissibleValues<String> merged = built(PlannedValues.<String>holdingAsOne("p", "r")
                .joinLive(PlannedValues.holdingAsOne("p", "s")), allowing());

        assertFalse(merged.relationExact(), "neither pair survives the merge");
        assertTrue(merged.projectionExactAt("p"), "and what each position holds is still exact");
        assertTrue(merged.projectionExactAt("r"));
        assertTrue(merged.projectionExactAt("s"));
    }

    /** And held apart, nothing is lost: the alternatives keep the relation between them. */
    @Test
    void andHeldApartTheRelationSurvives() {
        AdmissibleValues<String> apart = built(PlannedValues.<String>holdingAsOne("p", "r")
                .joinLiveApart(PlannedValues.holdingAsOne("p", "s")), allowing());

        assertTrue(apart.relationExact(), "the union of two products is what it is");
    }

    /**
     * Two sides of one product may not hold a position between them.
     *
     * <p>They are not two sides then. Read as a relation they close into one, and what each of them
     * was stated to admit is filed under a side the product has no entry for — so the rules are
     * dropped without anything saying so. Putting them together means meeting what they admit,
     * which is a set somebody has to build and there is no allowance where a product is made.
     */
    @Test
    void twoSidesOfOneProductMayNotShareAPosition() {
        Map<Sameness.Block<String>, ValueSet> overlapping = new java.util.LinkedHashMap<>();
        overlapping.put(Sameness.of("p", "q").blockOf("p"), ValueSet.just(A));
        overlapping.put(Sameness.of("q", "r").blockOf("q"), ValueSet.just(B));

        assertThrows(IllegalArgumentException.class,
                () -> new AdmissibleValues.Box<>(overlapping));
    }

    /**
     * A reading no equality reached is a product over its positions one at a time, and every purse
     * it opened belongs to one position.
     *
     * <p>The control for the whole change. What is being asserted is that a model with no equality
     * in it is answered in the coordinates it always was — a block is what a position is on its
     * own — so nothing about such a model moved.
     */
    @Test
    void aReadingNoEqualityReachedIsAProductOverItsPositions() {
        Allowance<String> sets = allowing();
        AdmissibleValues<String> reading = built(
                plans("p", A).meet(plans("r", B)).joinLiveApart(plans("p", B)), sets);

        assertTrue(reading.sameness().isDiscrete());
        filedUnder(reading).forEach(block ->
                assertTrue(block.isOne(), () -> block + " is more than one position"));
        sets.purses().forEach(block ->
                assertTrue(block.isOne(), () -> "a purse was opened for " + block));
        assertFalse(sets.purses().isEmpty(), "or nothing was measured");
    }
}
