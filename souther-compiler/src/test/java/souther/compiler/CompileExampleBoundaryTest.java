package souther.compiler;

import souther.compiler.report.AdequacyReport;
import org.junit.jupiter.api.Test;

import souther.compiler.observe.MeasurementStatus;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.BorderAssessment;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.PartitionEvidence;

import java.util.List;
import java.util.Map;

import static souther.compiler.AxisClasses.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which classes and which boundaries a behavior's rows actually reach.
 *
 * <p>The two are measured differently on purpose. Being in a class is a property of the value a row
 * wrote. Meeting a boundary a {@code guard} drew is not: the comparison has to have been evaluated,
 * and a row can carry the exact value and never reach the guard, so until the arms are instrumented
 * those boundaries are reported as not measured rather than as met or missed.
 */
class CompileExampleBoundaryTest {

    private static final String MODEL = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Draft = { cost: Amount }
            data Submitted = { cost: Amount }
            data Waiting = { cost: Amount }

            behavior submit : (request: Draft) -> Submitted | Waiting
                constructs Submitted, Waiting

            let submit (request) = {
                guard request.cost.value <= 100 else Waiting { cost = request.cost }
                Submitted { cost = request.cost }
            }
            """;

    private static Compilation measured(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        // Measuring the arms is opted into, so a test about them opts in.
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    private static PartitionEvidence evidence(String source) {
        Compilation compilation = measured(source);
        Map<String, PartitionEvidence> all = compilation.db()
                .ask(new Adequacy.Coverage(compilation.modules().get(0))).value();
        assertNotNull(all, "the model under test compiles");
        return all.get("submit");
    }

    /** The lines the behavior's positions met, whosever the row at each point is. */
    private static List<BorderAssessment> lines(String source) {
        Compilation compilation = measured(source);
        Map<String, List<BorderAssessment>> all =
                Adequacy.readingsOf(compilation.db(), compilation.modules().get(0));
        assertNotNull(all, "the model under test compiles");
        return all.get("submit");
    }

    private static PartitionEvidence.AxisCoverage cost(PartitionEvidence evidence) {
        return evidence.axes().stream().filter(a -> a.path().equals("request.cost"))
                .findFirst().orElseThrow();
    }

    /** The points against a line at {@code value}, which is what a row there is owed for. */
    private static List<BorderAssessment.Point> at(List<BorderAssessment> lines, String value) {
        return BorderAssessment.pointsOf(lines).stream()
                .filter(p -> p.role().againstTheLine()).filter(p -> p.owed() != null)
                .filter(p -> value.equals(p.against())).toList();
    }

    @Test
    void aClassNoRowIsInIsReportedAsUncovered() {
        PartitionEvidence one = evidence(MODEL + """

                example submit
                    | (Draft { cost = Amount(50) }) -> Submitted
                """);

        assertEquals(List.of("request.cost/100 < x"), names(cost(one).uncovered()));
        assertEquals(MeasurementStatus.COMPLETE, AdequacyReport.statusOf(cost(one).reached()));

        PartitionEvidence both = evidence(MODEL + """

                example submit
                    | (Draft { cost = Amount(50) })  -> Submitted
                    | (Draft { cost = Amount(500) }) -> Waiting
                """);
        assertEquals(List.of(), names(cost(both).uncovered()));
    }

    /** Nothing below the bound can be constructed, so writing the value is the whole of what there
     * is to reach — and reaching it needs no branch to have run. */
    @Test
    void anInvariantsBoundIsMetByWritingTheValue() {
        List<BorderAssessment> away = lines(MODEL + """

                example submit
                    | (Draft { cost = Amount(50) }) -> Submitted
                """);
        BorderAssessment.Point zero = at(away, "0").get(0);
        assertFalse(zero.owed().hasRowWitness());
        assertEquals(MeasurementStatus.COMPLETE, AdequacyReport.statusOf(zero.item().weakeningSource()));
        assertTrue(zero.border().origin().saidWithoutAPlace().startsWith("invariant"),
                zero.border().origin().saidWithoutAPlace());

        List<BorderAssessment> edge = lines(MODEL + """

                example submit
                    | (Draft { cost = Amount(0) }) -> Submitted
                """);
        assertTrue(at(edge, "0").get(0).owed().hasRowWitness(),
                "a row is written at the edge");
    }

    /** A row that writes the value and reaches the comparison meets the line the guard drew. */
    @Test
    void aGuardsBoundaryIsMetByARowThatReachesTheComparison() {
        List<BorderAssessment> lines = lines(MODEL + """

                example submit
                    | (Draft { cost = Amount(100) }) -> Submitted
                """);

        BorderAssessment.Point hundred = at(lines, "100").get(0);
        assertEquals(MeasurementStatus.COMPLETE, AdequacyReport.statusOf(hundred.item().weakeningSource()));
        assertTrue(hundred.owed().hasRowWitness(),
                "the row wrote 100 and the guard compared it");
        assertTrue(hundred.border().origin().isWrittenRatherThanNamed(),
                hundred.border().origin().saidWithoutAPlace());
    }

    /**
     * Writing the value is not reaching the comparison.
     *
     * <p>Here an earlier guard sends every negative cost away before the second one is evaluated, so a
     * row at exactly 100 on that second line never gets to it. The value is in the row and the rule was
     * never run against it, and counting that as met would report a rule as exercised that nothing has
     * ever executed.
     */
    @Test
    void aValueWrittenButNeverComparedDoesNotMeetTheLine() {
        List<BorderAssessment> lines = lines("""
                module example.trip

                data Amount = Int
                    invariant value >= -1000

                data Draft = { cost: Amount }
                data Submitted = { cost: Amount }
                data Waiting = { cost: Amount }

                behavior submit : (request: Draft) -> Submitted | Waiting
                    constructs Submitted, Waiting

                let submit (request) = {
                    guard request.cost.value >= 0 else Waiting { cost = request.cost }
                    guard request.cost.value <= 100 else Waiting { cost = request.cost }
                    Submitted { cost = request.cost }
                }

                example submit
                    | (Draft { cost = Amount(-1) }) -> Waiting { cost = Amount(-1) }
                """);

        BorderAssessment.Point first = at(lines, "0").stream()
                .filter(p -> p.border().origin().isWrittenRatherThanNamed()).findFirst().orElseThrow();
        BorderAssessment.Point second = at(lines, "100").get(0);

        assertEquals(MeasurementStatus.COMPLETE, AdequacyReport.statusOf(second.item().weakeningSource()),
                "the arms were measured");
        assertFalse(second.owed().hasRowWitness(), "no row was ever compared against 100");
        assertFalse(first.owed().hasRowWitness(), "and the row wrote -1, not 0");
    }

    @Test
    void bothSidesOfAGuardsLineAreAskedFor() {
        List<BorderAssessment> lines = lines(MODEL + """

                example submit
                    | (Draft { cost = Amount(50) }) -> Submitted
                """);

        assertEquals(1, at(lines, "100").size(), "the value itself");
        assertEquals(1, at(lines, "101").size(), "and the first value on the other side");
    }

    /** Not a gap. The model draws no line through a plain String, so there was nothing to measure,
     * and saying so is different from reporting nothing covered. */
    @Test
    void aPositionTheModelDoesNotDivideIsNamedRatherThanCounted() {
        Compilation compilation = Compilation.ofSource("""
                module example.note

                data Note = String
                data Kept = { note: Note }

                behavior keep : (note: Note) -> Kept
                    constructs Kept

                let keep (note) = Kept { note = note }

                example keep
                    | (Note("x")) -> Kept { note = Note("x") }
                """, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, PartitionEvidence> all = compilation.db()
                .ask(new Adequacy.Coverage(compilation.modules().get(0))).value();
        assertNotNull(all);
        PartitionEvidence keep = all.get("keep");

        // Read rather than built to compare against. An absence is what completing a position
        // produces and nothing outside that can make one, which is the whole of what the word is
        // worth — so this asks the answer what it is.
        assertEquals(1, keep.notDerivable().size());
        assertEquals(TermPath.of("note"), keep.notDerivable().get(0).at());
        assertTrue(keep.notDerivable().get(0).why()
                        instanceof souther.compiler.partition.UndividedPosition.Why.Absent,
                "the model divides it no way, which is established rather than assumed");
        assertEquals(List.of(), keep.axes());
        assertEquals(List.of(),
                Adequacy.readingsOf(compilation.db(), compilation.modules().get(0)).get("keep"));
    }
}
