package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An alternative is refused by everything that refused it, and gives a witness up where it is read.
 *
 * <p>An alternative is a conjunction of its sides, so one with a side left no value and denials
 * that state a value to differ from itself is refused both ways and neither reason is what it is
 * instead of the other. Asked as two questions in a row, whichever the walk put first is the one a
 * reader is handed, and the other is gone with nothing saying it was there.
 *
 * <p>Which witnesses a holder keeps is the second question here, and it is a question about the
 * holder. A reading answers per position, so a lone position left no value is not a witness the
 * reading is the place to name — and a reader asking what a declaration comes to against the ranges
 * is naming a place the reading's own rules did not refuse, and keeps it. Answered while the proof
 * was being built, an alternative refused at one position alone would come back refused by nothing.
 */
class AnAlternativeRefusedTwoWaysSaysBothOfThemTest {

    private static final Sameness.Block<String> P = Sameness.Block.of("p");
    private static final Sameness.Block<String> BOTH = Sameness.of("p", "q").blockOf("p");

    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    /** A lack about blocks together, for the half a set of blocks cannot hold. */
    private static Lacks<String> apartFromItself(Sameness.Block<String> block) {
        return Lacks.of(new RelationalLack.ABlockApartFromItself<>(block));
    }

    /** An alternative over one block, whose values the question below it answers about. */
    private static Map<Sameness.Block<String>, ValueSet> over(Sameness.Block<String> block) {
        return Map.of(block, ValueSet.NONE);
    }

    /** Denials that state {@link #BOTH} to differ from itself, which nothing satisfies. */
    private static WhatARelationShows<String> denialsApartFromItself() {
        return WhatARelationShows.statedApart(
                Apartness.of("p", "q")
                        .filedIn(Refinement.of(Sameness.discrete(), Sameness.of("p", "q"))));
    }

    /** Denials that refuse nothing, which is what an alternative stating none holds. */
    private static WhatARelationShows<String> noDenials() {
        return WhatARelationShows.statedApart(Apartness.nothing());
    }

    /**
     * An alternative refused at a block and about blocks together carries both.
     *
     * <p>Neither half is asked because the other came back empty, so what comes back does not
     * depend on which of the two the walk reaches first.
     */
    @Test
    void anAlternativeRefusedAtABlockAndAboutBlocksTogetherCarriesBoth() {
        Refusal<String> shown = Refusal.ofAnAlternative(over(BOTH),
                (_, _) -> true, denialsApartFromItself());

        assertEquals(Set.of(BOTH), shown.atEachOf(), "the block it was left nothing at");
        assertEquals(apartFromItself(BOTH), shown.together(),
                "and what its denials state, which the block being empty did not answer");
    }

    /** And a relation is read where every block of the alternative is refused. */
    @Test
    void andTheRelationIsReadWhereEveryBlockIsRefused() {
        assertFalse(Refusal.ofAnAlternative(over(BOTH), (_, _) -> true,
                denialsApartFromItself()).together().isEmpty(),
                "a block left nothing is not a reason to leave the denials unread");
    }

    /** And the blocks are read where the relation refuses. */
    @Test
    void andTheBlocksAreReadWhereTheRelationRefuses() {
        assertEquals(Set.of(BOTH), Refusal.ofAnAlternative(over(BOTH), (_, _) -> true,
                denialsApartFromItself()).atEachOf());
    }

    /** Where neither refuses, the alternative is refused by nothing anything can name. */
    @Test
    void andWhereNeitherRefusesTheAlternativeIsRefusedByNothing() {
        assertTrue(Refusal.ofAnAlternative(over(BOTH), (_, _) -> false, noDenials()).isNowhere());
    }

    /**
     * A lone position left nothing is a witness of the proof, and not one the reading keeps.
     *
     * <p>What a reading answers per position it does not answer again here, so a block of one
     * position is given up where the reading takes its own proof. Given up while the proof was
     * being built, an alternative whose only empty side is one position would come back refused by
     * nothing and a reader taking that for the answer would have it standing.
     */
    @Test
    void aLonePositionLeftNothingIsAWitnessOfTheProofAndNotOfTheReading() {
        Refusal<String> shown = Refusal.ofAnAlternative(over(P), (_, _) -> true, noDenials());

        assertEquals(Set.of(P), shown.atEachOf(), "the proof names where it was refused");
        assertFalse(shown.isNowhere(), "so the alternative is refused, and by something");

        assertTrue(shown.withoutWhatAPositionAnswers().isNowhere(),
                "and a reading holding it has nothing of its own to name");
    }

    /** And what the denials showed survives a lone position being given up. */
    @Test
    void andWhatTheDenialsShowedSurvivesALonePositionBeingGivenUp() {
        Refusal<String> kept = Refusal.ofAnAlternative(over(P), (_, _) -> true,
                denialsApartFromItself()).withoutWhatAPositionAnswers();

        assertEquals(Set.of(), kept.atEachOf());
        assertEquals(apartFromItself(BOTH), kept.together(),
                "which is the witness a walk that stopped at the empty side never looked for");
        assertEquals(Refusal.Nearest.OF_THEM_TOGETHER, kept.nearest());
    }

    /** A block of several positions is kept, since no position answers for it on its own. */
    @Test
    void andABlockOfSeveralPositionsIsKept() {
        Refusal<String> shown = Refusal.ofAnAlternative(over(BOTH), (_, _) -> true, noDenials());

        assertSame(shown, shown.withoutWhatAPositionAnswers(),
                "nothing is given up, so it is the same proof");
    }

    /**
     * A conjunction refused at a position and by its denials says both, through the operations.
     *
     * <p>One reading holds {@code p} and {@code r} as one value and leaves {@code q} one value; the
     * other states {@code p} and {@code r} apart and leaves {@code q} another. Their conjunction is
     * one pair of alternatives, and it is refused twice over: {@code q} is left nothing, and the
     * denial has both its ends on the block the equality leaves.
     *
     * <p>What the reading keeps is the denial. {@code q} is one position and its own rules are what
     * left it nothing — so a walk that stopped at the empty side would have handed a reader a
     * refusal naming nowhere, with the one thing it could have named unlooked for.
     */
    @Test
    void aConjunctionRefusedAtAPositionAndByItsDenialsSaysBoth() {
        Allowance<String> sets = AsACompilationAllows.forAdmittedValues();
        AdmissibleValues<String> both = holding(PlannedValues.holdingAsOne("p", "r"), A, sets)
                .meet(holding(PlannedValues.heldApart("p", "r"), B, sets), sets);

        assertTrue(both.isBottom(), "no value of these rules can be written");
        assertEquals(Refusal.Nearest.OF_THEM_TOGETHER, both.refusedBy().nearest(),
                "and what it is refused by is the denials, which a lone position does not answer");
        assertInstanceOf(RelationalLack.ABlockApartFromItself.class,
                both.refusedBy().together().only().lack());
        assertEquals(Set.of(Sameness.of("p", "r").blockOf("p")),
                both.refusedBy().together().blocks());
    }

    /** And where the empty side is a block of several positions, both halves are kept. */
    @Test
    void andWhereTheEmptySideIsABlockOfSeveralPositionsBothHalvesAreKept() {
        Allowance<String> sets = AsACompilationAllows.forAdmittedValues();
        AdmissibleValues<String> both =
                holding(PlannedValues.<String>holdingAsOne("p", "r")
                        .meet(PlannedValues.holdingAsOne("q", "s")), A, sets)
                .meet(holding(PlannedValues.<String>heldApart("p", "r")
                        .meet(PlannedValues.holdingAsOne("q", "s")), B, sets), sets);

        assertTrue(both.isBottom());
        assertEquals(Set.of(Sameness.of("q", "s").blockOf("q")), both.refusedBy().atEachOf(),
                "the block several positions share, which none of them answers for on its own");
        assertInstanceOf(RelationalLack.ABlockApartFromItself.class,
                both.refusedBy().together().only().lack(),
                "and the denial, which the empty block did not answer");
    }

    /**
     * A reader asking what a reading comes to against the ranges is answered both ways too.
     *
     * <p>The other side of the same walk: what refuses a block here is the reading's rules met with
     * whatever the reader holds them against, and what the denials come to is that reader's
     * question as well. An alternative both of them refuse is refused both ways, and a walk that
     * read the relation only where every block stood would answer with the blocks alone.
     *
     * <p>A lone position is named here and not given up. What refused it is not the reading's own
     * rules — those left it something — so there is no second account of it for this to be.
     */
    @Test
    void aReaderAskingAgainstTheRangesIsAnsweredBothWays() {
        Allowance<String> sets = AsACompilationAllows.forAdmittedValues();
        AdmissibleValues<String> reading = PlannedValues.<String>top()
                .meet(PlannedValues.at("q", new AdmittedPlan.Of(ValueSet.just(A))))
                .resolve(sets).values();

        Refusal<String> shown = reading.refusedInEveryAlternativeAt(
                (_, _) -> Emptiness.EMPTY,
                (_, _) -> new Apartness.Reduction.Nothing<>(apartFromItself(BOTH)));

        assertEquals(Set.of(Sameness.Block.of("q")), shown.atEachOf(),
                "the block the reader's own question refuses, lone position and all");
        assertEquals(apartFromItself(BOTH), shown.together(),
                "and what it was told the denials come to, which the block did not answer");
    }

    /** And the same of a reading nobody has worked out, where the denials answer on their own. */
    @Test
    void andAReadingNobodyHasWorkedOutIsAnsweredBothWays() {
        PlannedValues<String> planned = PlannedValues.<String>holdingAsOne("p", "r")
                .meet(PlannedValues.heldApart("p", "r"))
                .meet(PlannedValues.at("q", new AdmittedPlan.Of(ValueSet.just(A))));

        Refusal<String> shown = planned.refusedInEveryAlternativeAt(
                (block, _) -> block.equals(Sameness.Block.of("q"))
                        ? Emptiness.EMPTY : Emptiness.NONEMPTY);

        assertEquals(Set.of(Sameness.Block.of("q")), shown.atEachOf());
        assertInstanceOf(RelationalLack.ABlockApartFromItself.class, shown.together().only().lack(),
                "a block stated to differ from itself is refused by reading the rule, and this"
                        + " side reads it whether or not a block was refused first");
    }

    /**
     * And what a reading nobody has worked out is refused by includes its denials.
     *
     * <p>The proof this side of building can write: a block stated to differ from itself needs no
     * values to refuse it. Read off the blocks alone, a reading refused by nothing but its denials
     * would say it was refused by nothing at all.
     */
    @Test
    void andWhatAPlannedReadingIsRefusedByIncludesItsDenials() {
        PlannedValues<String> planned = PlannedValues.<String>holdingAsOne("p", "r")
                .meet(PlannedValues.heldApart("p", "r"));

        assertEquals(Refusal.Nearest.OF_THEM_TOGETHER, planned.refusedBy().nearest());
        assertInstanceOf(RelationalLack.ABlockApartFromItself.class,
                planned.refusedBy().together().only().lack());
    }

    /** A reading of {@code planned} that leaves {@code q} the one value {@code only}. */
    private static AdmissibleValues<String> holding(PlannedValues<String> planned, Value only,
                                                    Allowance<String> sets) {
        return planned.meet(PlannedValues.at("q", new AdmittedPlan.Of(ValueSet.just(only))))
                .resolve(sets).values();
    }
}
