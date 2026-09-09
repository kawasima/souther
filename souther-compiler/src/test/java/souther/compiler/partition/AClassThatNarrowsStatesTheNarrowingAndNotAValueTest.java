package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A class of a sum states which case the value is, and the value is built at the case.
 *
 * <p>Every kind of case, and that is the point of the row. A case holding a record offers no value
 * of its own; a case wrapping one offers the value it wraps, and a case that is the whole of a value
 * offers that. Taken as values of the position the class is a class of, the last two decide the same
 * location the positions under the case decide — one location under two names — and the plan reads
 * whichever it meets first. What is fixed under the case is then never looked at, and the row comes
 * back carrying the class's own representative rather than the one that was asked for.
 */
class AClassThatNarrowsStatesTheNarrowingAndNotAValueTest {

    /** A case that wraps a number, with a body drawing a line inside it — so the sum and what the
     *  case wraps are both axes, and both are the same location. */
    private static final String WRAPPED = """
            module g

            data Special = Int
                invariant value >= 0

            data Plain = { n: Int }
            data Choice = Special | Plain
            data Page = { n: Int }

            behavior use : (x: Choice) -> Page
                constructs Page

            let use (x) =
                match x with
                    | Special as s -> {
                        guard s.value > 10 else Page { n = 0 }
                        Page { n = 1 }
                      }
                    | Plain as p -> Page { n = 2 }
            """;

    /** The same under a name the position wears, which is what a row has to write back on. */
    private static final String WORN = """
            module g

            data Special = Int
                invariant value >= 0

            data Plain = { n: Int }
            data Bare = Special | Plain
            data Choice = Bare
            data Page = { n: Int }

            behavior use : (x: Choice) -> Page
            """;

    private static List<Generator.GeneratedRow> rowsOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        Core body = checked.behaviorBodies().get("use");
        InputDomain domain = InputDomain.of(sigs.get("use"), rules, ReadAs.THE_COMPILATION_DOES);
        Partitions.Partitioning axes =
                Partitions.of("use", domain, rules, ReadAs.THE_COMPILATION_DOES);
        // What a body draws, where there is one. A behavior nothing implements has the classes its
        // declarations state and no lines beside them, which is the whole of what one of these
        // models is for.
        if (body != null) {
            GuardThresholds.Guards guards = GuardThresholds.of("use",
                    checked.analysisBodies().get("use"), body,
                    checked.plan(),
                    compilation.db().ask(new Adequacy.Inputs(module)).value().get("use"), rules);
            axes = Partitions.withThresholds(axes, domain.quantities(rules), guards.thresholds(),
                    rules, ReadAs.THE_COMPILATION_DOES, guards.noLine(), guards.singled(),
                    guards.between(),
                souther.compiler.values.Allowance.of(souther.compiler.regex.PatternPlan.Budget.OF_BEHAVIOR_DISTINCTIONS));
        }
        FillResult filled = Generator.fill(
                MeasuredInput.of("use", domain.reading(rules), axes),
                List.of(), Generator.CandidateCheck.ANY, Budgets.generation());
        assertEquals(List.of(), filled.unresolved(), filled.unresolved().toString());
        return filled.rows();
    }

    /** The row for a class inside the case carries a value of that class, not the case's own. */
    @Test
    void aRowForAClassUnderAWrappingCaseCarriesThatClassesValue() {
        for (Generator.GeneratedRow row : rowsOf(WRAPPED)) {
            if (!row.labels().equals(List.of("x@Special=10 < x"))) {
                continue;
            }
            // The value the class above the line asks for, and not the one the `Special` class of
            // the sum offers — which is the same location decided twice where both are read.
            assertEquals("Special(11)", row.inputs().get(0).text());
            return;
        }
        throw new AssertionError("no row for the class above the line; there are "
                + rowsOf(WRAPPED).stream().map(Generator.GeneratedRow::labels).toList());
    }

    /** And every row of that behavior is a value of what the parameter declares. */
    @Test
    void everyRowIsWrittenAsTheParameterDeclaresIt() {
        for (Generator.GeneratedRow row : rowsOf(WRAPPED)) {
            String written = row.inputs().get(0).text();
            assertTrue(written.startsWith("Special(") || written.startsWith("Plain "),
                    () -> row.labels() + " is written as " + written);
        }
    }

    /** The same under a name the position wears, which the row puts back on. */
    @Test
    void aNarrowedValueIsWrittenUnderTheNameThePositionWears() {
        for (Generator.GeneratedRow row : rowsOf(WORN)) {
            String written = row.inputs().get(0).text();
            assertTrue(written.startsWith("Choice("),
                    () -> row.labels() + " is written as " + written);
        }
    }
}
