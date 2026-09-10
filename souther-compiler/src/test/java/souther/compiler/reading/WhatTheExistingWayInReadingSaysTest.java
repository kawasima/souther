package souther.compiler.reading;

import org.junit.jupiter.api.Test;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.diag.Severity;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the reading of the ways into a place already says, and where it stops.
 *
 * <p>Written down because two readings of "what held on the way here" is one reading too many, and
 * which of them a new one may be built on is decided by what this one's answers are rather than by
 * what its shape looks like. The shape is the same pair a decision rule wants — what a row is
 * steered by, and what a run that came this way would be seen doing — and the answers are not the
 * same answers.
 *
 * <p>Characterization, so every assertion here is what this reading does today rather than what it
 * ought to do. One of them moving is a reading that changed, and whoever changed it is the one who
 * decides whether the sentence beside it still holds.
 *
 * <p>What they settle together: the short-circuit split is here and is worth reusing; the vocabulary
 * the ways are written in is not the one a decision rule is told apart by, and does not carry enough
 * to be projected into it; and two shapes leave no ways at all, which is a list nothing can filter.
 */
class WhatTheExistingWayInReadingSaysTest {

    private static final String DECLARATIONS = """
            module example.ways

            data Count = Int
                invariant value >= 0
            data Yes
            data No
            data Answer = Yes | No
            """;

    /**
     * The split a decision rule wants is here: the arm a conjunction fails into is reached two ways,
     * and one of them says nothing about the second comparison.
     */
    @Test
    void aConjunctionFailsIntoOneArmByTwoWays() {
        List<List<Condition.Side>> ways = comparisonsOnTheWaysInto(waysOf("both", DECLARATIONS + """

                behavior both : (a: Count, b: Count) -> Answer
                let both (a, b) = if a.value > 5 && b.value > 5 then Yes else No
                """));
        List<List<Condition.Side>> failing = ways.stream().filter(way -> way.size() == 1).toList();
        assertEquals(1, ways.stream().filter(way -> way.size() == 1).count(),
                () -> "one way in reads one comparison: " + ways);
        assertEquals(false, failing.get(0).get(0).held(),
                () -> "and it is the first comparison having failed: " + ways);
        assertEquals(2, ways.stream().filter(way -> way.size() == 2).count(),
                () -> "two ways in read both comparisons: " + ways);
    }

    /**
     * A comparison of two positions keeps its way, and the way says it of one of them.
     *
     * <p>Which is the vocabulary difference and not a limit: what a row is steered by is a class of
     * one position, and a relation between two is not one. A decision rule is about the relation.
     */
    @Test
    void aComparisonOfTwoPositionsIsSaidOfOneOfThem() {
        List<List<Condition.Side>> ways = comparisonsOnTheWaysInto(waysOf("relates", DECLARATIONS
                + """

                behavior relates : (a: Count, b: Count) -> Answer
                let relates (a, b) = if a.value < b.value then Yes else No
                """));
        Set<String> terms = new LinkedHashSet<>();
        ways.forEach(way -> way.forEach(side -> terms.add(String.valueOf(side.at()))));
        assertEquals(1, terms.size(),
                () -> "a relation between two positions is written of one term: " + terms);
    }

    /**
     * One proposition consulted twice is two decisions on one way, told apart by where each is
     * written.
     *
     * <p>Which is right for what this reading is for — an operation applying a block twice compares
     * two values — and is what a decision rule may not do: a table with a column apiece admits an
     * assignment where one proposition holds and does not.
     *
     * <p>And the term is all that survives of what the comparison says. Two comparisons of one term
     * against different numbers are told apart by their occurrences and by nothing else here, so
     * there is nothing in a way to project onto the proposition it states.
     */
    @Test
    void oneCallOfOneHelperMadeTwiceIsTwoDecisions() {
        List<List<Condition.Side>> ways = comparisonsOnTheWaysInto(waysOf("twice", DECLARATIONS + """

                let big (n: Count): Bool = n.value >= 10

                behavior twice : (a: Count) -> Answer
                let twice (a) = {
                    guard big(a) else No
                    guard big(a) else No
                    Yes
                }
                """));
        List<Condition.Side> both = ways.stream().filter(way -> way.size() == 2)
                .findFirst().orElseThrow(() -> new AssertionError("no way reads both: " + ways));
        assertEquals(both.get(0).at(), both.get(1).at(),
                () -> "one helper on one argument is one number: " + both);
        assertNotEquals(both.get(0).comparison(), both.get(1).comparison(),
                () -> "and the two readings of it are told apart by where each is written: " + both);
    }

    /**
     * A fork inside one of the language's own operations leaves no ways at all.
     *
     * <p>So a decision rule read off this and filtered down to what the model states has nothing to
     * filter: the arm is reached by a list this reading declined to write, rather than by a list
     * holding the operation's own conditions beside the author's.
     */
    @Test
    void aForkInsideOneOfTheLanguagesOwnOperationsLeavesNoWays() {
        List<PathAccess> arms = accessOf("anyActive", """
                module example.ways

                data On
                data Off
                data Active = On | Off
                data Item = { active: Active }
                data Yes
                data No
                data Answer = Yes | No

                behavior anyActive : (items: List<Item>) -> Answer
                let anyActive (items) =
                    if List.any(i -> i.active == On, items) then Yes else No
                """);
        // Counted, because a body whose arms nothing numbered would walk the loop below no times
        // and be reported as one every arm of which answered what this expects.
        assertEquals(2, arms.size(), () -> "the fork's two arms are numbered: " + arms);
        for (PathAccess access : arms) {
            assertEquals(new PathAccess.Unsupported(
                            PathAccess.Unsupported.Why.WAYS_NOT_ENUMERABLE), access,
                    "the ways through the operation are not written down");
        }
    }

    /**
     * Over the figure this reading holds itself to, what a place is told carries no ways.
     *
     * <p>The walk itself keeps them — {@code Reach.Coarse} holds the ways beside the reason — and
     * what a caller of the finished reading is handed does not. So a reader that has to keep an
     * obligation open under a weakening has to sit inside the walk rather than on its answer.
     */
    @Test
    void overTheFigureAPlaceIsToldTheLimitAndNotTheWays() {
        StringBuilder condition = new StringBuilder("a.value > 0");
        for (int each = 1; each <= 20; each++) {
            condition.append(" && a.value > ").append(each);
        }
        assertTrue(accessOf("many", DECLARATIONS + """

                behavior many : (a: Count) -> Answer
                let many (a) = if %s then Yes else No
                """.formatted(condition)).stream()
                        .anyMatch(new PathAccess.Unsupported(
                                PathAccess.Unsupported.Why.MORE_WAYS_IN_THAN_ARE_READ)::equals),
                "over the figure, an arm is told the limit rather than the ways");
    }

    /** The comparisons on each way, which is what a decision rule is told apart by. */
    private static List<List<Condition.Side>> comparisonsOnTheWaysInto(List<WayIn> ways) {
        List<List<Condition.Side>> out = new ArrayList<>();
        for (WayIn way : ways) {
            List<Condition.Side> sides = new ArrayList<>();
            for (Decision each : way.decisions()) {
                if (each.constrains() instanceof Condition.Side side) {
                    sides.add(side);
                }
            }
            out.add(sides);
        }
        return out;
    }

    /** Every way into any arm of {@code behavior}, which over a body with one fork is the ways
     *  through it. */
    private static List<WayIn> waysOf(String behavior, String source) {
        List<WayIn> out = new ArrayList<>();
        for (PathAccess access : accessOf(behavior, source)) {
            if (access instanceof PathAccess.Ways ways) {
                out.addAll(ways.ways());
            }
        }
        return out;
    }

    /** What the reading says about each arm the plan numbered. */
    private static List<PathAccess> accessOf(String behavior, String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream)
                        .filter(said -> said.diagnostic().severity() == Severity.ERROR)
                        .map(said -> said.diagnostic().code() + " " + said.diagnostic().titleKey())
                        .toList(),
                "a model that did not compile answers every question with nothing");
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        CoverageSites.Plan plan = checked.plan();
        InputDomain inputs =
                compilation.db().ask(new Adequacy.Inputs(module)).value().get(behavior);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        CoverageRead.Read read = CoverageRead.of(behavior, checked.behaviorBodies().get(behavior),
                plan, inputs, rules);
        return List.copyOf(read.arms().values());
    }
}
