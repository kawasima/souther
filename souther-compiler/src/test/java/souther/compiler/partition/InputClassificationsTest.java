package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.core.Core;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.InputDomain;
import souther.compiler.observe.Classification;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.RowOutcome;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading back out of a row which class each of its inputs fell in.
 *
 * <p>A row writes values, not class names. What it covers is therefore a question about the values it
 * was given, and one it has to be able to decline: a value that could not be read leaves that axis
 * undecided, and only that axis.
 */
class InputClassificationsTest {

    private static final String MODEL = """
            module example.trip

            data Domestic
            data Overseas
            data Kind = Domestic | Overseas

            data Amount = Int
                invariant value >= 0

            data Request = { kind: Kind, cost: Amount, memo: String }
            data Submitted = { cost: Amount }
            data Waiting = { cost: Amount }

            behavior submit : (request: Request) -> Submitted | Waiting
                constructs Submitted, Waiting

            let submit (request) = {
                guard request.cost.value <= 100 else Waiting { cost = request.cost }
                Submitted { cost = request.cost }
            }
            """;

    private record Read(MeasuredInput subject, List<RowOutcome> rows) {

        MeasuredInput.MeasuredAxes axes() {
            return subject.axes();
        }
    }

    private static Read read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");

        Core body = checked.behaviorBodies().get("submit");
        CoverageSites.Plan plan = checked.plan();
        InputDomain read = InputDomain.of(sigs.get("submit"), rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        Partitions.Partitioning partitioning = Partitions.withThresholds(
                Partitions.of("submit", read, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                read.quantities(rules),
                GuardThresholds.of("submit", checked.analysisBodies().get("submit"), body, plan,
                compilation.db().ask(new souther.compiler.query.Adequacy.Inputs(module)).value().get("submit"), rules).thresholds(),
                RuleReadingContext.unshared(rules,
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                souther.compiler.values.Allowance.of(souther.compiler.regex.PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS));

        Output.Examples.Of observed = compilation.db()
                .ask(Output.Examples.asked(compilation.db(), module,
                        compilation.sourceIds().get(0))).value();
        assertNotNull(observed);
        return new Read(MeasuredInput.of("submit", read.reading(rules), partitioning),
                observed.rows());
    }

    private static Classification at(Map<AxisId, Classification> classes, String path) {
        return classes.entrySet().stream().filter(e -> e.getKey().term().equals(path))
                .map(Map.Entry::getValue).findFirst()
                .orElseThrow(() -> new AssertionError("no axis at " + path + "; had "
                        + classes.keySet().stream().map(AxisId::term).toList()));
    }

    @Test
    void aRowsValuesAreReadBackIntoTheClassesTheyFellIn() {
        Read read = read(MODEL + """

                example submit
                    | (Request { kind = Domestic, cost = Amount(50), memo = "" }) -> Submitted
                """);

        Map<AxisId, Classification> classes =
                InputClassifications.of(read.rows().get(0).inputs(), read.axes());

        assertEquals(Classification.in("Domestic"), at(classes, "request.kind"));
        assertEquals(Classification.in("request.cost/0 <= x <= 100"),
                at(classes, "request.cost"));
    }

    @Test
    void aValueOnTheOtherSideOfAThresholdFallsInTheOtherClass() {
        Read read = read(MODEL + """

                example submit
                    | (Request { kind = Overseas, cost = Amount(500), memo = "" }) -> Waiting
                """);

        Map<AxisId, Classification> classes =
                InputClassifications.of(read.rows().get(0).inputs(), read.axes());

        assertEquals(Classification.in("Overseas"), at(classes, "request.kind"));
        assertEquals(Classification.in("request.cost/100 < x"),
                at(classes, "request.cost"));
    }

    /** A position the model does not divide has nothing to fall into, so it is not asked about. */
    @Test
    void anAxisWithNoClassesIsLeftOut() {
        Read read = read(MODEL + """

                example submit
                    | (Request { kind = Domestic, cost = Amount(50), memo = "" }) -> Submitted
                """);

        Map<AxisId, Classification> classes =
                InputClassifications.of(read.rows().get(0).inputs(), read.axes());

        assertTrue(classes.keySet().stream().noneMatch(a -> a.term().equals("request.memo")),
                "a plain String is not divided, so there is no class to be in");
    }

    /** The point of classifying per axis: one unreadable value does not take the row's other answers
     * with it. */
    @Test
    void anUnreadableValueLeavesOnlyItsOwnAxisUndecided() {
        Read read = read(MODEL + """

                example submit
                    | (Request { kind = Domestic, cost = Amount(50), memo = "" }) -> Submitted
                """);
        RowOutcome row = read.rows().get(0);

        ObservedValue.Constructed request =
                assertInstanceOf(ObservedValue.Constructed.class, row.inputs().get(0));
        Map<String, ObservedValue> broken = new java.util.LinkedHashMap<>(request.fields());
        broken.put("cost", new ObservedValue.Truncated());
        RowOutcome damaged = new RowOutcome(row.at(), row.target(), row.identity(),
                row.expectation(), row.stage(),
                row.disposition(), row.failurePhase(), row.expectedArm(), row.resultArm(),
                row.inputCases(),
                List.of(new ObservedValue.Constructed(request.type(), broken)),
                // A row whose input the observation stopped in states no values, which is what the
                // one place that decides answers for these. Kept as the row's own, the fixture
                // would say two different things about what it handed over.
                souther.compiler.observe.RowStatements.read(List.of(),
                        List.of(new ObservedValue.Constructed(request.type(), broken)),
                        ((souther.compiler.observe.RowStatement.Stated) row.statement()).expects()),
                row.run());

        Map<AxisId, Classification> classes =
                InputClassifications.of(damaged.inputs(), read.axes());

        assertEquals(Classification.in("Domestic"), at(classes, "request.kind"),
                "the readable field still answers");
        Classification.Unclassified cost = assertInstanceOf(Classification.Unclassified.class,
                at(classes, "request.cost"));
        assertEquals(Incompleteness.Code.VALUE_TRUNCATED, cost.reason().code());
    }

    @Test
    void aRowThatNeverBuiltItsInputsClassifiesNothing() {
        Read read = read(MODEL + """

                example submit
                    | (Request { kind = Domestic, cost = Amount(-1), memo = "" }) -> Submitted
                """);
        RowOutcome row = read.rows().get(0);
        assertEquals(List.of(), row.inputs(), "the fixture never built");

        Map<AxisId, Classification> classes =
                InputClassifications.of(row.inputs(), read.axes());

        assertTrue(classes.values().stream().noneMatch(Classification::isClassified));
    }
}
