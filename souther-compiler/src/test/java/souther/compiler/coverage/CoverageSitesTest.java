package souther.compiler.coverage;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Numbering the arms of a body, before anything is measured with them.
 *
 * <p>The numbering has to be a property of the body and nothing else. If it moved with the example
 * rows, or with which arm happened to be asked about first, a coverage report could not be compared
 * against the one before it, and the author would be told a branch appeared when nothing changed.
 */
class CoverageSitesTest {

    private static final String MODEL = """
            module example.leave

            data UnderThirty
            data ThirtyOrOver
            data AgeBand = UnderThirty | ThirtyOrOver

            data Days = Int invariant value >= 90 && value <= 330

            behavior daysFor : (age: AgeBand, senior: Bool) -> Days
                constructs Days

            let daysFor (age, senior) =
                match age with
                    | UnderThirty ->
                        if senior then Days(120) else Days(90)
                    | ThirtyOrOver ->
                        unreachable "a member over thirty is handled elsewhere"
            """;

    /** The bodies a source compiles to, under the name the source declares them in. The name comes
     *  with them: a body's own parameters belong to the behavior the module declares, so a plan
     *  built under some other module's name is a plan of bodies whose names it cannot place. */
    private record Checked(String module, Map<String, Core> bodies) {}

    private static Checked bodiesOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db()
                .ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        return new Checked(module, checked.behaviorBodies());
    }

    private static CoverageSites.Plan planOf(String source) {
        return planOf(bodiesOf(source));
    }

    private static CoverageSites.Plan planOf(Checked checked) {
        return CoverageSites.of(
                new ModuleBodies(checked.module(),
                        new java.util.LinkedHashMap<>(checked.bodies())),
                souther.compiler.coverage.DecisionSources.NONE,
                souther.compiler.coverage.SuppliedRules.NONE);
    }

    private static List<String> labels(CoverageSites.Plan plan) {
        return plan.sites().stream().map(souther.compiler.report.ArmVocabulary::label).toList();
    }

    @Test
    void everyArmOfEveryForkIsASiteAndNothingElseIs() {
        CoverageSites.Plan plan = planOf(MODEL);

        assertEquals(List.of("case UnderThirty", "then", "else"), labels(plan));
        assertEquals(List.of(OutcomeName.CASE, OutcomeName.THEN, OutcomeName.ELSE),
                plan.sites().stream().map(CoverageSites.Site::name).toList());
    }

    /**
     * Reaching one is already an error, so a probe there would leave a correct model one arm short
     * for ever and make every denominator wrong.
     *
     * <p>Asserted as the whole list of arms rather than as the absence of a word: an arm answering
     * {@code unreachable} is labelled by the case it stands for, so nothing about the label of the
     * one that should be missing tells it from the ones that should be there.
     */
    @Test
    void anArmThatAnswersNothingIsNotAnArm() {
        CoverageSites.Plan plan = planOf(MODEL);

        assertFalse(labels(plan).contains("case ThirtyOrOver"),
                "the arm answers `unreachable`, so no row can be in it");
        assertEquals(3, plan.sites().size());
    }

    /** An arm every path of which ends in an {@code unreachable} answers as little as one written as
     * a bare {@code unreachable}, however many forks stand between. */
    @Test
    void anArmWhoseEveryPathAbortsIsNotAnArmEither() {
        CoverageSites.Plan plan = planOf("""
                module example.nested

                data Yes
                data No
                data Answer = Yes | No

                data Score = Int

                behavior scoreFor : (a: Answer, b: Answer) -> Score
                    constructs Score

                let scoreFor (a, b) =
                    match a with
                        | Yes -> match b with
                                     | Yes -> Score(1)
                                     | No  -> unreachable "no follows yes elsewhere"
                        | No  -> match b with
                                     | Yes -> unreachable "no answer here"
                                     | No  -> unreachable "nor here"
                """);

        assertEquals(List.of("case Yes", "case Yes"), labels(plan),
                "the outer `No` arm answers nothing, and neither of the arms it is made of does");
    }

    /**
     * A fork nothing reaches is not a fork to cover, however ordinary its arms look.
     *
     * <p>The arms of the inner {@code match} answer values and would be arms anywhere else. Here they
     * stand after a binding that aborts, so the only row that could go through one is a row that
     * reached the {@code unreachable} first — E1911, which states nothing. Counted, they are two gaps
     * that stay open for ever, which is this whole measure's fault one level in.
     */
    @Test
    void aForkBelowSomethingThatAbortsIsNotAForkToCover() {
        CoverageSites.Plan plan = planOf("""
                module example.dead

                data Yes
                data No
                data Answer = Yes | No

                data Score = Int

                behavior scoreFor : (a: Answer, b: Answer) -> Score
                    constructs Score

                let scoreFor (a, b) =
                    match a with
                        | Yes -> Score(1)
                        | No  -> {
                            let impossible: Int = unreachable "no No arrives"
                            match b with
                                | Yes -> Score(impossible)
                                | No  -> Score(3)
                        }
                """);

        assertEquals(List.of("case Yes"), labels(plan),
                "the outer `No` arm answers nothing, and the fork inside it is behind the abort");
    }

    /**
     * The fork nothing reaches is still in the plan, with no arms.
     *
     * <p>The emitter generates the bytecode of a body that aborts as it generates any other, and asks
     * the plan for the arms of each fork it passes; a fork with no entry stops it. So the structure is
     * registered and the obligation is not.
     */
    @Test
    void aForkNothingReachesIsPlannedWithNoArms() {
        Checked checked = bodiesOf("""
                module example.dead

                data Yes
                data No
                data Answer = Yes | No

                data Score = Int

                behavior scoreFor : (a: Answer, b: Answer) -> Score
                    constructs Score

                let scoreFor (a, b) =
                    match a with
                        | Yes -> Score(1)
                        | No  -> {
                            let impossible: Int = unreachable "no No arrives"
                            match b with
                                | Yes -> Score(impossible)
                                | No  -> Score(3)
                        }
                """);
        CoverageSites.Plan plan = planOf(checked);

        Core.Match outer = (Core.Match) unwrap(checked.bodies().get("scoreFor"));
        Core.Match inner = innerMatch(outer.cases().get(1).body());
        assertArrayEquals(new int[] {CoverageSites.NO_SITE, CoverageSites.NO_SITE},
                plan.probesOf(inner), "the emitter still finds it, and finds nothing to light");
    }

    /**
     * An arm the condition never sends a run down is not an arm to cover.
     *
     * <p>Nothing whole is greater than the largest whole number, so the condition comes out false on
     * every run and the first arm is one nobody enters. Numbered, it is a gap that stays open for
     * ever — a row is owed for a combination the body has no run at — which is the same fault as
     * counting an arm behind an abort and is caught by the same reading.
     *
     * <p>Read from the reading and not worked out here. Which arms a condition can reach is what the
     * reading of what the body does already answers, and a second account of it in the numbering
     * would be two answers to keep in step.
     */
    @Test
    void anArmTheConditionNeverComesOutTheWayOfIsNotAnArmToCover() {
        CoverageSites.Plan plan = planOf("""
                module example.pinned

                data Score = Int

                behavior scoreFor : (a: Int) -> Score
                    constructs Score

                let scoreFor (a) =
                    if a > 9223372036854775807 then Score(1) else Score(2)
                """);

        // The comparison keeps its site either way, which is deliberate and is said where the
        // numbering is: a comparison is numbered wider than what any boundary is later drawn on.
        // It comes first because the walk numbers where the source wrote it, and a condition is
        // written before the arms it settles.
        assertEquals(List.of("GT", "else"), labels(plan),
                "the condition holds on no run, so only the arm it fails into is one to cover");
    }

    private static Core.Match innerMatch(Core arm) {
        Core at = arm;
        while (!(at instanceof Core.Match)) {
            at = switch (at) {
                case Core.LetIn let -> let.body();
                case Core.Block block -> block.body();
                default -> throw new AssertionError("no `match` under this arm: " + at);
            };
        }
        return (Core.Match) at;
    }

    /** The same, at the top: a body that cannot answer has no arms to cover, whatever forks are
     * written below the point it stops at. */
    @Test
    void aBodyThatCannotAnswerHasNoArms() {
        CoverageSites.Plan plan = planOf("""
                module example.dead

                data Score = Int

                behavior scoreFor : (senior: Bool) -> Score
                    constructs Score

                let scoreFor (senior) = {
                    let impossible: Int = unreachable "nobody calls this"
                    if senior then Score(impossible) else Score(0)
                }
                """);

        assertEquals(List.of(), labels(plan));
        assertEquals(List.of(), plan.guards(), "and no line for a row to be at either");
    }

    /** The arm is one a row can be in whatever the rows happen to cover: what the denominator holds
     * is a property of the body, and a nested abort beside a reachable answer does not remove it. */
    @Test
    void anArmWithOneAnsweringPathIsStillAnArm() {
        CoverageSites.Plan plan = planOf("""
                module example.partial

                data Yes
                data No
                data Answer = Yes | No

                data Score = Int

                behavior scoreFor : (a: Answer, b: Answer) -> Score
                    constructs Score

                let scoreFor (a, b) =
                    match a with
                        | Yes -> Score(1)
                        | No  -> match b with
                                     | Yes -> Score(0)
                                     | No  -> unreachable "not both"
                """);

        assertEquals(List.of("case Yes", "case No", "case Yes"), labels(plan));
    }

    /**
     * The emitter indexes a node's arms by position, so an arm without a probe keeps its place.
     *
     * <p>A compacted array would move every arm after the missing one down by one, and the emitter
     * would light its neighbour's probe. Nothing downstream could tell such a hit from a real one:
     * the count would be right and the arm it was recorded against would be wrong.
     */
    @Test
    void anArmWithoutAProbeKeepsItsPlaceInTheArray() {
        Checked checked = bodiesOf("""
                module example.order

                data Yes
                data No
                data Answer = Yes | No

                data Score = Int

                behavior scoreFor : (a: Answer) -> Score
                    constructs Score

                let scoreFor (a) =
                    match a with
                        | Yes -> unreachable "the caller has already refused a yes"
                        | No  -> Score(0)
                """);
        CoverageSites.Plan plan = planOf(checked);

        Core.Match match = (Core.Match) unwrap(checked.bodies().get("scoreFor"));
        assertArrayEquals(new int[] {CoverageSites.NO_SITE, 0}, plan.probesOf(match),
                "the surviving arm is second, and it is the second entry that holds its probe");
    }

    @Test
    void aGuardKeepsBothOfItsArmsTogether() {
        CoverageSites.Plan plan = planOf(MODEL);

        assertEquals(1, plan.guards().size());
        CoverageSites.GuardRef guard = plan.guards().get(0);
        assertEquals("then", labelAt(plan, guard.whereThen()));
        assertEquals("else", labelAt(plan, guard.whereElse()));
    }

    /** What the arm {@code where} numbers is called, asked of the plan that numbered it. */
    private static String labelAt(CoverageSites.Plan plan, java.util.Optional<ArmProbe> where) {
        CoverageSites.ArmSite found = plan.sites().stream()
                .filter(CoverageSites.ArmSite.class::isInstance)
                .map(CoverageSites.ArmSite.class::cast)
                .filter(site -> site.index().equals(where.orElseThrow()))
                .findFirst().orElseThrow();
        return souther.compiler.report.ArmVocabulary.label(found);
    }

    /** The comparison was evaluated to reach the arm that is left, so the line it draws is still one
     * a row can be at. */
    @Test
    void aGuardWithOneArmLeftIsStillAGuard() {
        CoverageSites.Plan plan = planOf(MODEL.replace("if senior then Days(120) else Days(90)",
                "if senior then unreachable \"no senior is under thirty\" else Days(90)"));

        assertEquals(1, plan.guards().size());
        CoverageSites.GuardRef guard = plan.guards().get(0);
        assertTrue(guard.whereThen().isEmpty(), "the `then` arm answers nothing, so it has no place");
        assertEquals("else", labelAt(plan, guard.whereElse()));
    }

    /**
     * An {@code if} whose arms both answer nothing draws no line for a row to be at.
     *
     * <p>Kept as a reference with two absent sides, the boundary it drew would be reported as unmet
     * however the model is exercised — the same permanent gap this arm rule is about, moved from the
     * branch measure to the boundary one.
     */
    @Test
    void anIfWithNoArmsLeftIsNotAGuard() {
        CoverageSites.Plan plan = planOf("""
                module example.leave

                data UnderThirty
                data ThirtyOrOver
                data AgeBand = UnderThirty | ThirtyOrOver

                data Days = Int invariant value >= 90 && value <= 330

                behavior daysFor : (age: AgeBand, senior: Bool) -> Days
                    constructs Days

                let daysFor (age, senior) =
                    match age with
                        | UnderThirty ->
                            if senior
                                then unreachable "no senior is under thirty"
                                else unreachable "and no one else reaches here"
                        | ThirtyOrOver -> Days(90)
                """);

        assertEquals(List.of(), plan.guards());
        assertEquals(List.of("case ThirtyOrOver"), labels(plan),
                "and the arm the `if` is the whole of answers nothing either");
    }

    @Test
    void aSiteIsFoundByTheNodeInstanceTheEmitterHolds() {
        Checked checked = bodiesOf(MODEL);
        CoverageSites.Plan plan = planOf(checked);

        Core body = checked.bodies().get("daysFor");
        Core.Match match = (Core.Match) unwrap(body);
        int[] arms = plan.probesOf(match);
        assertNotNull(arms, "the plan is keyed by the instances it was built from");
        assertEquals(2, arms.length);
        assertEquals("case UnderThirty", labelAt(plan, java.util.Optional.of(
                plan.numbering().arm(arms[0]))));

        Core.Match copy = new Core.Match(match.scrutinee(), match.cases(),
                Core.ForkPlace.asWritten(match.occurrence()), match.type(), match.pos());
        assertEquals(match, copy, "an equal node is easy to make");
        assertNull(plan.probesOf(copy),
                "and it is not this one: a value-keyed plan would hand the emitter another arm's probe");
    }

    /** A body's arms are its own. Rows do not move them, and neither does anything above them. */
    @Test
    void theNumberingDependsOnTheBodyAndNothingElse() {
        List<String> without = labels(planOf(MODEL));
        List<String> withRows = labels(planOf(MODEL + """

                example daysFor
                    | (UnderThirty, true)  -> Days(120)
                    | (UnderThirty, false) -> Days(90)
                """));

        assertEquals(without, withRows);
        assertEquals(labels(planOf(MODEL)), without, "and it is the same on a second walk");
    }

    /**
     * What a row is owed for is where a fork was written, and two arms made of the same thing were
     * written in two places.
     *
     * <p>This used to be asked of a content hash, which answered the other way: two arms with the
     * same shape and label shared one, and the hash was kept out of identity for that reason. The
     * question is not the same question. A hash asks whether two arms are alike, which is a fact
     * about what they are made of and cannot tell an arm the author wrote twice from an arm a helper
     * wrote once and two callers copied. What a measure needs is which one the author wrote, and
     * only where it was written says that.
     */
    @Test
    void twoArmsMadeOfTheSameThingAreStillTwoObligations() {
        CoverageSites.Plan plan = planOf("""
                module example.same

                data Yes
                data No
                data Answer = Yes | No

                data Score = Int

                behavior scoreFor : (a: Answer, b: Answer) -> Score
                    constructs Score

                let scoreFor (a, b) =
                    match a with
                        | Yes -> match b with
                                     | Yes -> Score(1)
                                     | No  -> Score(0)
                        | No  -> match b with
                                     | Yes -> Score(1)
                                     | No  -> Score(0)
                """);

        List<CoverageSites.Obligation> inner = plan.sites().stream()
                .filter(s -> souther.compiler.report.ArmVocabulary.label(s).equals("case Yes"))
                .map(CoverageSites.Site::obligation).toList();
        assertEquals(inner.size(), inner.stream().distinct().count(),
                "the two inner `case Yes` arms are two arms the author wrote");
    }

    private static Core unwrap(Core body) {
        Core at = body;
        while (at instanceof Core.Block block) {
            at = block.body();
        }
        return at;
    }

    @Test
    void aModuleWithNoBodiesPlansNothing() {
        assertSame(true, planOf(new Checked("example.empty", Map.of())).hasNoProbes());
    }
}
