package souther.compiler;

import souther.compiler.query.Measurement;
import souther.compiler.report.AdequacyReport;
import org.junit.jupiter.api.Test;

import souther.compiler.observe.MeasurementStatus;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.PartitionEvidence;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shapes of model that the measures were getting wrong.
 *
 * <p>Each of these came back from a review with a way to reproduce it, and each is a shape ordinary
 * enough that nothing written before it had happened to use one: a `Decimal` compared against a
 * fraction, a loop written as a recursive helper, three inputs of one type.
 */
class CompileAdequacyShapesTest {

    private static Compilation measured(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    private static PartitionEvidence partition(Compilation compilation, String behavior) {
        Map<String, PartitionEvidence> all = compilation.db()
                .ask(new Adequacy.Coverage(compilation.modules().get(0))).value();
        assertNotNull(all, "the model under test compiles");
        return all.get(behavior);
    }

    /** The lines that behavior's positions met, whosever the row at each point is. */
    private static List<BorderAssessment> lines(Compilation compilation, String behavior) {
        Map<String, List<BorderAssessment>> all =
                Adequacy.readingsOf(compilation.db(), compilation.modules().get(0));
        assertNotNull(all, "the model under test compiles");
        return all.get(behavior);
    }

    /**
     * A bare `Decimal` position compared against a fraction.
     *
     * <p>Whether a position counts in whole numbers is a fact about the position. Read instead off
     * the bound an invariant states, every position that has no invariant reads as an integer — and
     * a threshold of {@code 0.5m} is then asked for its exact {@code long}, which is an
     * {@code ArithmeticException} out through the whole compile rather than a report.
     */
    @Test
    void aFractionalGuardOnAPlainDecimalIsMeasuredRatherThanThrown() {
        Compilation compilation = measured("""
                module example.dec

                data Yes = { r: Decimal }
                data No = { r: Decimal }

                behavior classify : (rate: Decimal) -> Yes | No
                    constructs Yes, No

                let classify (rate) = {
                    guard rate < 0.5m else No { r = rate }
                    Yes { r = rate }
                }

                example classify
                    | (0.1m) -> Yes { r = 0.1m }
                """);
        PartitionEvidence evidence = partition(compilation, "classify");
        List<BorderAssessment> lines = lines(compilation, "classify");

        assertEquals(1, evidence.axes().size());
        assertEquals(List.of("rate/x < 0.5", "rate/0.5 <= x"), evidence.axes().get(0).classes());
        assertEquals(List.of("0.5"),
                lines.stream().map(BorderAssessment::value).toList());
        // A `Decimal` names no value one step over, so the border owes its own point and says why
        // the other one is not a gap rather than leaving it out.
        // `< 0.5` puts the cut outside the partition it names, so the row at 0.5 is the border's
        // OFF point — and the ON point one step in is a value a `Decimal` names none of.
        assertEquals(new souther.compiler.query.ItemAssessment.NotOwed(
                        souther.compiler.partition.NotOwedReason.THE_CARRIER_NAMES_NO_NEIGHBOUR),
                lines.get(0).at(souther.compiler.partition.PointRole.ON));
    }

    /**
     * A loop written the way loops are written.
     *
     * <p>A recursive helper is emitted as a shared method, and its forks belong to no one behavior's
     * arms — so the plan holds none for them. Demanding one anyway made the emitter refuse the whole
     * module, and the refusal reads downstream as "the arms could not be measured", which is what a
     * module with one such helper reported for every behavior it had.
     */
    @Test
    void aRecursiveHelperWithAForkDoesNotStopTheModuleBeingMeasured() {
        Compilation compilation = measured("""
                module example.rec

                data Limit = Int
                    invariant value >= 0

                data Ok = { n: Int }
                data No = { n: Int }

                partial let countdown (n: Int, acc: Int): Int =
                    if n <= 0 then acc else countdown(n - 1, acc + n)

                behavior check : (limit: Limit) -> Ok | No
                    constructs Ok, No

                let check (limit) = {
                    guard limit.value >= 1 else No { n = 0 }
                    Ok { n = countdown(limit.value, 0) }
                }

                example check
                    | (Limit(3)) -> Ok { n = 6 }
                """);

        Adequacy.BranchEvidence branch = compilation.db()
                .ask(new Adequacy.BranchCoverage(compilation.modules().get(0))).value().get("check");
        assertEquals(MeasurementStatus.COMPLETE, AdequacyReport.statusOf(branch.measured()),
                "the behavior's own arms are countable whatever its helpers look like");
        assertEquals(2, branch.arms().counted(), "the guard's two arms, and none of the helper's");

        assertTrue(BorderAssessment.pointsOf(lines(compilation, "check")).stream()
                        .anyMatch(p -> p.item().weakeningSource() instanceof Measurement.Complete<?>),
                "and the guard's boundary is decided rather than unavailable");
    }

    /**
     * A fork somewhere that is not a behavior's body.
     *
     * <p>An invariant's clause is emitted through the same generator a body is, and a rule written
     * with a condition in it has forks. They are not arms of any behavior — an invariant is a property
     * of a type — so the plan holds none, and the emitter must not go looking for one.
     *
     * <p>It did, and the failure was as quiet as a failure gets: the generation was abandoned, the
     * measured classes came back absent, and every behavior in the module reported its arms as
     * unmeasured. Two of the eleven models measured here were in that state, which is where 121 of
     * their arms had gone.
     */
    @Test
    void aForkOutsideABehaviorsBodyDoesNotStopTheModuleBeingMeasured() {
        Compilation compilation = measured("""
                module example.rule

                data Sku = String

                data Line = { sku: Sku, quantity: Int }

                data Stock = { rows: List<Line> }
                    invariant List.allDistinctBy(.sku, rows)

                data Ok = { n: Int }
                data Empty = { n: Int }

                behavior count : (stock: Stock) -> Ok | Empty
                    constructs Ok, Empty

                let count (stock) = {
                    guard List.length(stock.rows) > 0 else Empty { n = 0 }
                    Ok { n = List.length(stock.rows) }
                }

                example count
                    | (Stock { rows = [] }) -> Empty { n = 0 }
                """);

        Adequacy.BranchEvidence branch = compilation.db()
                .ask(new Adequacy.BranchCoverage("example.rule")).value().get("count");
        assertEquals(MeasurementStatus.COMPLETE, AdequacyReport.statusOf(branch.measured()),
                "the invariant's own fork is not this behavior's, and does not stop it being counted");
        assertEquals(2, branch.arms().counted(), "the guard's two arms, and none of the invariant's");
        assertEquals(1, branch.arms().covered());
    }

    /**
     * Three positions of one type.
     *
     * <p>A class is named uniquely within its position and not across positions — three `Flag` inputs
     * each have a `Yes` — so a record of what a row covered has to say which positions it is about.
     * Keyed by the two class names alone, every combination one row covers is one entry.
     */
    @Test
    void aRowCoveringSeveralPairsIsCountedAsSeveral() {
        PartitionEvidence evidence = partition(measured("""
                module example.pairs

                data Yes
                data No
                data Flag = Yes | No

                data Picked = { n: Int }

                behavior pick : (a: Flag, b: Flag, c: Flag) -> Picked
                    constructs Picked

                let pick (a, b, c) = Picked { n = 0 }

                example pick
                    | (Yes, Yes, Yes) -> Picked { n = 0 }
                """), "pick");

        assertEquals(12, evidence.pairs().total(), "three positions of two classes");
        assertEquals(3, evidence.pairs().counts().covered(), "one row sits in three of the pairs");
        assertEquals(9, evidence.pairs().unknown());
    }

    /**
     * One line, however it was written down.
     *
     * <p>`0.00` and `0` are the same number and the same line. Told apart, a position ends up with two
     * classes both holding zero — which is not a partition, so the classifier that reads a row
     * against it has no answer — and one boundary is owed twice under one printed value.
     */
    @Test
    void aValueWrittenAtTwoScalesIsOneLine() {
        Compilation compilation = measured("""
                module example.scale

                data Rate = Decimal
                    invariant value >= 0.00m

                data Yes = { r: Rate }
                data No = { r: Rate }

                behavior classify : (rate: Rate) -> Yes | No
                    constructs Yes, No

                let classify (rate) = {
                    guard rate.value <= 0m else No { r = rate }
                    Yes { r = rate }
                }

                example classify
                    | (Rate(1m)) -> No { r = Rate(1m) }
                """);
        PartitionEvidence evidence = partition(compilation, "classify");

        List<String> at = lines(compilation, "classify").stream()
                .map(BorderAssessment::value).filter(v -> v.equals("0")).toList();
        assertEquals(2, at.size(), "one line, and two rules that drew it there");
        assertFalse(evidence.axes().isEmpty());
        assertEquals(List.of("rate/0 <= x <= 0", "rate/0 < x"), evidence.axes().get(0).classes(),
                "two ranges over one line — not three, with two of them holding zero");
    }
}
