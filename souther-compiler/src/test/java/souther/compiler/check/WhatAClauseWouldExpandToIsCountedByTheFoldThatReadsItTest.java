package souther.compiler.check;

import souther.compiler.types.BinOp;
import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How many alternatives a clause would come to, counted before any of them is built.
 *
 * <p>A budget has to be measurable before the work it limits. The number of alternatives a clause
 * comes to once duplicates are gone is not: finding it out is the expansion this is asked to bound.
 * So what is counted is the syntax — a choice adds, a conjunction multiplies, and a denial swaps the
 * two.
 *
 * <p><b>Counted on the fold that does the reading.</b> Which shapes are connectives, where a denial
 * goes and what is left as a leaf are {@link ClauseReading}'s. A walk of its own saying the same
 * thing again is a second answer that agrees with the first only until somebody changes one of
 * them — and the two coming apart is not a wrong count, it is a reading that expands past the
 * budget admitted for it.
 */
class WhatAClauseWouldExpandToIsCountedByTheFoldThatReadsItTest {

    private static final SourcePos POS = new SourcePos(0, 0);

    /** What is a leaf is the fold's answer and not this test's: anything with no connective on top
     *  of it costs one, whatever a reading later makes of it. */
    private static Core leaf() {
        return new Core.Bool(true, Type.BOOL, POS);
    }

    private static Core and(Core left, Core right) {
        return new Core.Binary(BinOp.AND, left, right, ConstructOccurrence.unwritten(), Type.BOOL,
                POS);
    }

    private static Core or(Core left, Core right) {
        return new Core.Binary(BinOp.OR, left, right, ConstructOccurrence.unwritten(), Type.BOOL,
                POS);
    }

    /** As the analysis representation spells one, which is what {@link Predicates} reads. */
    private static Core not(Core e) {
        return new Core.If(e, new Core.Bool(false, Type.BOOL, POS),
                new Core.Bool(true, Type.BOOL, POS),
                Core.ForkPlace.asWritten(ConstructOccurrence.unwritten()), Type.BOOL, POS);
    }

    private static long cost(Core e) {
        return counted(new ExpansionCost(64), e, true);
    }

    /** The count this reading comes to. It carries no environment, so a binding leaves what it was
     *  given ({@link ClauseScope#unchanged}) and the count is over the shape alone. */
    private static long counted(ExpansionCost counting, Core e, boolean positive) {
        return counting.read(e, positive, null, ClauseScope.unchanged());
    }

    /** A conjunction of choices, which doubles what it comes to per level and stays as long as the
     *  level count. Written as a chain rather than by doubling a shared operand, which is one node
     *  a reader walks twice and a count this reads as a tree. */
    private static Core doubling(int levels) {
        Core out = leaf();
        for (int i = 0; i < levels; i++) {
            out = and(or(leaf(), leaf()), out);
        }
        return out;
    }

    @Test
    void aClauseOfNoConnectiveIsOneAlternative() {
        assertEquals(1L, cost(leaf()));
    }

    @Test
    void aChoiceAddsAndAConjunctionMultiplies() {
        assertEquals(2L, cost(or(leaf(), leaf())));
        assertEquals(1L, cost(and(leaf(), leaf())));
        assertEquals(4L, cost(and(or(leaf(), leaf()), or(leaf(), leaf()))));
    }

    /**
     * A denial swaps them, because that is what it does to the connectives it is carried through.
     *
     * <p>Not stated here twice. The fold carries a denial down to the leaves, so the count of a
     * denied clause is the count of the clause read denied — which is the law, and the two arms
     * above are what it comes to.
     */
    @Test
    void aDenialCountsWhatTheClauseUnderItCountsDenied() {
        for (Core each : List.of(leaf(), or(leaf(), leaf()), and(leaf(), leaf()),
                and(or(leaf(), leaf()), leaf()), or(and(leaf(), leaf()), leaf()))) {
            assertEquals(counted(new ExpansionCost(64), each, false), cost(not(each)),
                    "a denial is carried down and not applied to what a branch came to");
        }
        assertEquals(1L, cost(not(or(leaf(), leaf()))), "a denied choice is a conjunction");
        assertEquals(2L, cost(not(and(leaf(), leaf()))), "and a denied conjunction is a choice");
    }

    /**
     * The count follows the connectives and not the brackets.
     *
     * <p>Which is what lets the domain be chosen before the fold: a declaration written one way and
     * the same declaration written another are admitted alike, so precision cannot turn on how an
     * author bracketed a choice.
     */
    @Test
    void theCountDoesNotFollowTheBracketsOrTheOrder() {
        Core left = or(or(leaf(), leaf()), leaf());
        Core right = or(leaf(), or(leaf(), leaf()));

        assertEquals(3L, cost(left));
        assertEquals(3L, cost(right));
        assertEquals(cost(and(left, right)), cost(and(right, left)));
    }

    /** A declaration is its clauses met, so what it costs is their product. */
    @Test
    void aDeclarationCostsTheProductOfItsClauses() {
        assertEquals(6L, ExpansionCost.of(List.of(or(leaf(), leaf()),
                or(or(leaf(), leaf()), leaf())), 64));
        assertEquals(1L, ExpansionCost.of(List.of(), 64), "and nothing read costs one");
    }

    /**
     * A count that runs past the limit stops there rather than overflowing.
     *
     * <p>What is asked of it is whether the limit is exceeded, and past that the number is not
     * wanted. Left to run, a conjunction of choices is a product that leaves the range of the
     * count's own type long before it leaves the range of what an author can write.
     */
    @Test
    void aCountThatRunsPastTheLimitSaturates() {
        assertEquals(65, cost(doubling(64)), "saturated at one past the limit, and not overflowed");
        assertEquals(4L, counted(new ExpansionCost(3),
                        and(or(leaf(), leaf()), or(leaf(), leaf())), true),
                "a limit of its own is saturated at one past that");
    }

    /**
     * And a limit at the top of its own type saturates like any other.
     *
     * <p>Counted in the type the limit is written in, one past the largest of them is the smallest,
     * and every count is under it — so the guardrail admits the expansions it exists to refuse. The
     * arithmetic is done a size up, so the ceiling is one past the limit whatever the limit is.
     */
    @Test
    void andALimitAtTheTopOfItsTypeSaturatesLikeAnyOther() {
        Core wide = doubling(40);

        for (int limit : new int[] {Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
            assertEquals((long) limit + 1, counted(new ExpansionCost(limit), wide, true),
                    "one past the limit, whatever the limit is: " + limit);
            assertTrue(counted(new ExpansionCost(limit), wide, true) > limit,
                    "and above it, so the guardrail refuses what it exists to refuse: " + limit);
        }
    }

    /** What a compilation grants a reading to build with, which these are not about. */
    private static souther.compiler.regex.PatternPlan.Budget allowed() {
        return souther.compiler.values.AsACompilationAllows.admittedValues();
    }

    /** And the other of the two, for the same reason. */
    private static souther.compiler.regex.PatternPlan.Budget handedOn() {
        return souther.compiler.values.AsACompilationAllows.whatARuleLeaves();
    }

    /**
     * A limit that bounds nothing is refused where it is written.
     *
     * <p>A reading holds at least one alternative, so a limit below one is one no reading is under.
     * Left to whoever writes it, a resource bound that admits everything is the absence of one and
     * says nothing about itself.
     *
     * <p>A count of places is refused below nought and not below one: a scale of nought is the whole
     * numbers, which is a grid a reading may be held to laying out and nothing else, and below it is
     * not a count of places at all.
     */
    @Test
    void aLimitThatBoundsNothingIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReadingPolicy(0, 2, allowed(), handedOn()));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadingPolicy(-1, 2, allowed(), handedOn()));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadingPolicy(64, -1, allowed(), handedOn()));
        // And the two allowances, which a reading is granted rather than numbers it compares
        // anything against: a reading with neither is one nothing bounds what it builds.
        assertThrows(IllegalArgumentException.class,
                () -> new ReadingPolicy(64, 2, null, handedOn()));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadingPolicy(64, 2, allowed(), null));
        assertEquals(0, new ReadingPolicy(64, 0, allowed(), handedOn()).scalePlacesLimit());
    }
}
