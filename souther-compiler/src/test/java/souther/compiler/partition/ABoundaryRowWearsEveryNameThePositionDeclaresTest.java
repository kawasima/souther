package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.Prepared;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.core.Core;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Shapes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A row at a boundary is written wearing every name its position declares.
 *
 * <p>Two questions meet at a boundary row and only one of them is the carrier's. What a count is
 * written as — a number, an ISO date, a case name — is the carrier's; how many names that value
 * wears on the way to the position is the position's, and a newtype over a newtype wears two. Asked
 * of the carrier as well, the second was answered one layer deep, and the row came back missing the
 * name in the middle for the decoder to refuse.
 *
 * <p>Held here rather than left to the corpus: no example declares a newtype over a newtype, so
 * nothing measured would have said this stopped working.
 */
class ABoundaryRowWearsEveryNameThePositionDeclaresTest {

    @Test
    void aTemporalBoundaryUnderTwoNamesWearsBoth() {
        assertEquals(List.of("ON -> ShippingDay(Day(Date(\"2026-07-31\")))",
                        "OFF -> ShippingDay(Day(Date(\"2026-08-01\")))"),
                rowsAtBoundaries("""
                        module example.nesteddate

                        data Day = Date
                        data ShippingDay = Day

                        data Ok
                        data No
                        data Verdict = Ok | No

                        behavior f : (d: ShippingDay) -> Verdict
                        let f (d) = { guard d.value.value < Date("2026-08-01") else Ok
                            No }
                        """));
    }

    /** The same of a number, which was one layer deep before any of this and is not any more. */
    @Test
    void aNumericBoundaryUnderTwoNamesWearsBoth() {
        assertEquals(List.of("ON -> ShippingAmount(Amount(499))",
                        "OFF -> ShippingAmount(Amount(500))"),
                rowsAtBoundaries("""
                        module example.nestedint

                        data Amount = Int
                        data ShippingAmount = Amount

                        data Ok
                        data No
                        data Verdict = Ok | No

                        behavior f : (a: ShippingAmount) -> Verdict
                        let f (a) = { guard a.value.value < 500 else Ok
                            No }
                        """));
    }

    /** What a row carries at each boundary of the one behavior in {@code source}. */
    private static List<String> rowsAtBoundaries(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");

        Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().get(0);
        Core body = checked.behaviorBodies().get(spec.name());
        CoverageSites.Plan plan = checked.plan();
        InputDomain domain = compilation.db()
                .ask(new souther.compiler.query.Adequacy.Inputs(module)).value().get(spec.name());
        GuardThresholds.Guards guards =
                GuardThresholds.of(spec.name(), checked.analysisBodies().get(spec.name()), body,
                        plan, domain, rules);
        souther.compiler.inputs.Quantities reading = domain.quantities(rules);
        Partitions.Partitioning p = Partitions.withThresholds(
                Partitions.of(spec.name(), domain, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                reading,
                guards.thresholds(),
                RuleReadingContext.unshared(rules,
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                souther.compiler.values.Allowance.of(souther.compiler.regex.PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS));

        List<String> names = new ArrayList<>();
        spec.params().forEach(each -> names.add(each.name()));
        MeasuredInput subject = MeasuredInput.of(spec.name(), domain.reading(rules), p);

        List<String> out = new ArrayList<>();
        for (Axis axis : p.axes()) {
            for (Border border
                    : Partitions.bordersOf(axis, reading, reading.runsBetween(axis.term()), new LinesRead())) {
              for (PointRole role : List.of(PointRole.ON, PointRole.OFF)) {
                if (!(border.demand(role).criterion()
                        instanceof Criterion.AtTheLevel each)) {
                    continue;   // no row is owed there, so there is none to write
                }
                out.add(role + " -> "
                        + (Generator.probeFixing(subject, border.label(role),
                                java.util.Map.of(
                                        new RealizationTarget.AtOnePosition(axis.term()),
                                        ((Level.OnACarrier) each.at()).at()),
                                Reachability.untouched(domain.quantities(rules).region()),
                                Generator.CandidateCheck.ANY)
                                instanceof Generator.BoundaryAttempt.Built built
                                        ? String.join(", ", built.row().inputs().stream()
                                                .map(FixtureTemplate::text).toList())
                                        : "no row"));
              }
            }
        }
        return out;
    }
}
