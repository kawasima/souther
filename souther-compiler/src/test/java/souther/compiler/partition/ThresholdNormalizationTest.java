package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.core.Core;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.InputDomain;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Towards;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lines a behavior's own comparisons draw through its inputs.
 *
 * <p>This is where a numeric position stops being one undivided range. "Pre-approval above a hundred
 * thousand" is in no type — it is the comparison the body makes — and it is the line the rows have to
 * be written on both sides of.
 */
class ThresholdNormalizationTest {

    /** The symbols are carried because the reading needs them: which carrier a position's values
     *  are on is read off its declared type, so asking with symbols that cannot resolve it is
     *  asking a different question from the one the compiler asks. */
    private record Read(Partitions.Partitioning partitioning,
                        souther.compiler.inputs.Quantities reading, List<Threshold> thresholds,
                        RuleReadingSource rules) {}

    private static Read read(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");

        Core body = checked.behaviorBodies().get(behavior);
        assertNotNull(body);
        CoverageSites.Plan plan = checked.plan();
        GuardThresholds.Guards guards = GuardThresholds.of(behavior,
                checked.analysisBodies().get(behavior), body, plan,
                compilation.db().ask(new souther.compiler.query.Adequacy.Inputs(module)).value().get(behavior), rules);
        List<Threshold> thresholds = guards.thresholds();
        InputDomain domain = InputDomain.of(sigs.get(behavior), rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        souther.compiler.inputs.Quantities reading = domain.quantities(rules);
        Partitions.Partitioning base = Partitions.of(behavior, domain, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        return new Read(Partitions.withThresholds(base, reading, thresholds, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES,
                souther.compiler.values.Allowance.of(souther.compiler.regex.PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS)), reading, thresholds, rules);
    }

    private static Axis axis(Partitions.Partitioning partitioning, String path) {
        return partitioning.axes().stream().filter(a -> a.path().toString().equals(path))
                .findFirst().orElseThrow();
    }

    private static List<String> labels(Axis axis) {
        return axis.classes().stream().map(PartitionClass::label).toList();
    }

    private static final String CEILING = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Draft = { cost: Amount }
            data Submitted = { cost: Amount }
            data Waiting = { cost: Amount }

            behavior submit : (request: Draft) -> Submitted | Waiting
                constructs Submitted, Waiting

            let submit (request) = {
                guard request.cost.value <= 100000 else Waiting { cost = request.cost }
                Submitted { cost = request.cost }
            }
            """;

    @Test
    void aComparisonDividesAPositionTheTypeLeftWhole() {
        Read read = read(CEILING, "submit");

        assertEquals(1, read.thresholds().size());
        Threshold threshold = read.thresholds().get(0);
        assertEquals("request.cost", threshold.path().toString());
        assertEquals(Count.of(new BigDecimal(100000)), threshold.value());
        assertEquals(Towards.BELOW, threshold.valueBelongs(), "`<= c` puts c on the low side");

        Axis cost = axis(read.partitioning(), "request.cost");
        assertEquals(List.of("0 <= x <= 100000", "100000 < x"), labels(cost));
    }

    /** The newtype's `value` is not a step: the guard and the parameter walk have to spell the same
     * location the same way, or the position becomes two axes and neither is ever covered. */
    @Test
    void aNewtypesValueIsTheSameLocationAsTheNewtype() {
        Read read = read(CEILING, "submit");

        assertEquals(List.of("request.cost"),
                read.thresholds().stream().map(t -> t.path().toString()).toList());
        assertTrue(read.partitioning().axes().stream()
                        .noneMatch(a -> a.path().toString().equals("request.cost.value")),
                "there is no second axis under the newtype's field");
    }

    /** The invariant bounds what exists; the guard says where the behavior changes. The range below
     * zero holds nothing a row could write, so it is not a range to cover. */
    @Test
    void theTypesOwnDomainIsIntersectedIn() {
        Read read = read("""
                module example.bounded

                data Level = Int
                    invariant value >= 0

                data Answer = { n: Int }

                behavior classify : (level: Level) -> Answer
                    constructs Answer

                let classify (level) =
                    if level.value < 10 then Answer { n = 1 } else Answer { n = 2 }
                """, "classify");

        assertEquals(List.of("0 <= x < 10", "10 <= x"), labels(axis(read.partitioning(), "level")));
    }

    @Test
    void severalCutsOnOnePositionBecomeOneRunOfRanges() {
        Read read = read("""
                module example.bands

                data Score = Int

                data Band = { name: String }

                behavior bandFor : (score: Score) -> Band
                    constructs Band

                let bandFor (score) =
                    if score.value < 10 then Band { name = "low" }
                    else if score.value < 20 then Band { name = "mid" }
                    else Band { name = "high" }
                """, "bandFor");

        assertEquals(List.of("x < 10", "10 <= x < 20", "20 <= x"),
                labels(axis(read.partitioning(), "score")));
    }

    /**
     * Short-circuiting is why only a bare comparison is read. An overseas request takes the else arm
     * without `cost` ever being compared, so a row that lands there is not a boundary test of `cost`.
     */
    @Test
    void aCompoundConditionDrawsNoLine() {
        Read read = read("""
                module example.compound

                data Domestic
                data Overseas
                data Kind = Domestic | Overseas

                data Request = { kind: Kind, cost: Int }
                data Answer = { n: Int }

                behavior check : (request: Request) -> Answer
                    constructs Answer

                let check (request) =
                    match request.kind with
                        | Domestic ->
                            if request.cost <= 100 then Answer { n = 1 } else Answer { n = 2 }
                        | Overseas -> Answer { n = 3 }
                """, "check");

        assertEquals(1, read.thresholds().size(), "the bare comparison inside the arm is read");

        Read compound = read("""
                module example.compound2

                data Request = { urgent: Bool, cost: Int }
                data Answer = { n: Int }

                behavior check : (request: Request) -> Answer
                    constructs Answer

                let check (request) =
                    if request.urgent && request.cost <= 100
                        then Answer { n = 1 } else Answer { n = 2 }
                """, "check");

        // The line is the model's wherever in the condition it is written. What stands beside the
        // comparison decides nothing about that: `urgent` draws no line, and the comparison behind
        // it draws the same one it would draw on its own.
        assertEquals(1, compound.thresholds().size(), compound.thresholds().toString());
        Threshold bare = read.thresholds().get(0);
        Threshold beside = compound.thresholds().get(0);
        assertEquals(
                List.of(bare.term(), bare.parts(), bare.valueBelongs()),
                List.of(beside.term(), beside.parts(), beside.valueBelongs()),
                "and the line is the same one either way: " + compound.thresholds());
    }

    @Test
    void aGuardsBoundaryWantsTheValueAndItsNeighbour() {
        Read read = read(CEILING, "submit");
        Axis cost = axis(read.partitioning(), "request.cost");

        NumericDomain.Bounds within = read.reading().runsBetween(cost.term());
        assertNotNull(within, "the invariant's domain is what this asks the obligations about");
        List<String> described = pointsAgainstTheLines(cost, read.reading(), within);

        assertTrue(described.contains("ON 100000"), described.toString());
        assertTrue(described.contains("OFF 100001"), described.toString());
        assertTrue(described.contains("ON 0"), "the invariant's own edge is still worth a row");
        assertFalse(described.contains("OFF 1"),
                "an invariant's bound has nothing on the far side to reach");
    }

    /**
     * A line on an enumeration owes the case it is drawn at and the case beside it.
     *
     * <p>Written as the cases and never as the places they take in the declaration. What carries an
     * enumeration into the algebra is the ordinal, which is 0, 1, 2 — the most plausible-looking
     * wrong value any carrier has, and the one a reader would not catch in a report.
     *
     * <p>The classes are the cases and the cut adds none. {@code s < Qualified} divides the values
     * into `{Prospecting}` and `{Qualified, Won}`, which is coarser than the three cases the type
     * already states, so the meet of the two is the cases — the line is worth its rows and the
     * partition it would have made is one the model had already made finer (ADR-0090).
     */
    @Test
    void aLineOnAnEnumerationIsOwedAtCaseNames() {
        Read read = read("""
                module example.pipeline

                data Prospecting
                data Qualified
                data Won
                data Stage = Prospecting | Qualified | Won

                data Bigger
                data Smaller
                data Size = Bigger | Smaller

                behavior classifyStage : (s: Stage) -> Size
                let classifyStage (s) = {
                    guard s < Qualified else Bigger
                    Smaller }
                """, "classifyStage");

        Axis stage = axis(read.partitioning(), "s");
        assertEquals(List.of("Prospecting", "Qualified", "Won"), labels(stage),
                "the cut is the coarser partition, so the classes stay the cases");

        List<String> described = pointsAgainstTheLines(stage, read.reading(),
                read.reading().runsBetween(stage.term()));
        assertEquals(List.of("ON Prospecting", "OFF Qualified"), described);
    }

    /**
     * The shipping-fee example from smdd-book chapter 8, and the number it derives: the boundary of
     * "under three thousand" is 2999. Which neighbour is the other class's edge follows from which
     * side of the line the compared value is on, which is why the origin carries it.
     */
    @Test
    void theBoundaryOfAnUnderRuleIsTheValueBelowIt() {
        Read read = read("""
                module example.shipping

                data Yen = Int
                    invariant value >= 0

                data Fee = { yen: Int }

                behavior feeFor : (amount: Yen) -> Fee
                    constructs Fee

                let feeFor (amount) =
                    if amount.value < 3000 then Fee { yen = 500 } else Fee { yen = 0 }
                """, "feeFor");

        Axis amount = axis(read.partitioning(), "amount");
        assertEquals(List.of("0 <= x < 3000", "3000 <= x"), labels(amount));

        NumericDomain.Bounds within = read.reading().runsBetween(amount.term());
        assertNotNull(within, "the invariant's domain is what this asks the obligations about");
        List<String> described = pointsAgainstTheLines(amount, read.reading(), within);
        assertTrue(described.contains("OFF 3000"), described.toString());
        assertTrue(described.contains("ON 2999"), described.toString());
    }

    /** The points against each of {@code axis}'s borders, as {@code role value}. */
    private static List<String> pointsAgainstTheLines(Axis axis,
                                                      souther.compiler.inputs.Quantities reading,
                                                      NumericDomain.Bounds within) {
        return Partitions.bordersOf(axis, reading, within, new LinesRead()).stream()
                .flatMap(border -> java.util.stream.Stream.of(PointRole.ON, PointRole.OFF)
                        .filter(role -> border.demand(role).criterion() != null)
                        .map(role -> role + " "
                                + border.demand(role).criterion().asked(border.cut().of()).substring(2)))
                .toList();
    }


    /** The same value cut by two rules is one class boundary and two things to exercise. */
    @Test
    void aCutDrawnTwiceKeepsBothRules() {
        Read read = read("""
                module example.twice

                data Level = Int
                    invariant value >= 10

                data Answer = { n: Int }

                behavior classify : (level: Level) -> Answer
                    constructs Answer

                let classify (level) =
                    if level.value < 20 then Answer { n = 1 } else Answer { n = 2 }
                """, "classify");

        Axis level = axis(read.partitioning(), "level");
        Cut at20 = level.cuts().stream()
                .filter(c -> c.at().sameAs(Count.of(20L))).findFirst()
                .orElseThrow();
        Cut at10 = level.cuts().stream()
                .filter(c -> c.at().sameAs(Count.of(10L))).findFirst()
                .orElseThrow();

        assertEquals(1, at10.origins().size(), "the declaration's own end");
        assertTrue(at10.origins().stream().anyMatch(o -> o instanceof LineOrigin.InvariantOrigin));
        assertEquals(1, at20.origins().size(), "and the body's, which divides what it leaves");
        assertTrue(at20.origins().stream().anyMatch(o -> o instanceof LineOrigin.ComparisonOrigin));
    }

    /**
     * A comparison the declarations can never satisfy still draws its line.
     *
     * <p>{@code value < 10} over a {@code Level} the rules stop at ten and up is satisfied by no
     * value there is. It divides the values all the same: a row at ten is one the behavior takes
     * the other way, and that row is writable. A line has two sides and owes a point against each,
     * so what makes it a line somebody can write a row against is a value at the line — not a value
     * on the side the rule is satisfied on.
     *
     * <p>Which is why the two rules at ten stay two. The declaration's own end and the comparison
     * are different things to exercise, and the comparison being unsatisfiable is a fact about one
     * of its points rather than about whether it drew a line.
     */
    @Test
    void aComparisonTheDeclarationsCannotSatisfyStillDrawsItsLine() {
        Read read = read("""
                module example.empty

                data Level = Int
                    invariant value >= 10

                data Answer = { n: Int }

                behavior classify : (level: Level) -> Answer
                    constructs Answer

                let classify (level) =
                    if level.value < 10 then Answer { n = 1 } else Answer { n = 2 }
                """, "classify");

        Axis level = axis(read.partitioning(), "level");
        Cut at10 = level.cuts().stream()
                .filter(c -> c.at().sameAs(Count.of(10L))).findFirst()
                .orElseThrow();

        assertEquals(2, at10.origins().size(),
                () -> "the declaration's end and the comparison, both at ten: " + at10.origins());
        assertTrue(at10.origins().stream().anyMatch(o -> o instanceof LineOrigin.InvariantOrigin),
                () -> "the clause's own end: " + at10.origins());
        assertTrue(at10.origins().stream().anyMatch(o -> o instanceof LineOrigin.ComparisonOrigin),
                () -> "and the comparison, which no value satisfies and which divides them all the"
                        + " same: " + at10.origins());
    }
}
