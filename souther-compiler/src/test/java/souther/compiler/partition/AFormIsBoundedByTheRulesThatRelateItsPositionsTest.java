package souther.compiler.partition;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a form over several positions runs is what the rules relating them leave it, and not what
 * its positions leave it one at a time.
 *
 * <p>A product of per-position ranges cannot carry a relation. Two fields each running to five come
 * to ten that way, and a record whose clause holds their sum at five has no value anywhere near it —
 * so a rule cutting the sum at eight drew a border on a quantity that never arrives there, and the
 * rows it asked for were rows nobody can write. What a report then said of each was that every value
 * tried had been refused, which is a fact about a search sent somewhere the model does not go.
 *
 * <p>Both directions, because a check that only looks for the absent lines passes on a reading that
 * draws nothing at all. A threshold inside where the form does run still draws its border and still
 * owes its rows.
 */
class AFormIsBoundedByTheRulesThatRelateItsPositionsTest {

    /** Two fields, each running from none to five, whose record holds their sum at five. */
    private static final String SUMMED = """
            module example.together

            data N = Int
                invariant atLeastNone = value >= 0
                invariant atMostFive  = value <= 5

            data P = { x: N, y: N }
                invariant together = x.value + y.value <= 5

            data No = { why: Int }
            data Yes = { v: Int }
            data Result = No | Yes

            behavior f : (p: P) -> Result
                constructs Yes, No
            let f (p) = {
                guard Int.add(p.x.value, p.y.value) <= CUT else No { why = 0 }
                Yes { v = 1 }
            }

            example f
                | "low" : (P { x = N(0), y = N(0) }) -> Yes { v = 1 }
            """;

    /** A cut past where the sum runs. The box of the two positions reaches it and the rules do not. */
    private static final String PAST_THE_SUM = SUMMED.replace("CUT", "8");

    /** A cut inside where the sum runs, which is a border like any other. */
    private static final String INSIDE_THE_SUM = SUMMED.replace("CUT", "3");

    @Test
    void aCutPastWhereTheFormRunsDrawsNoBorder() {
        String report = report(PAST_THE_SUM);

        assertFalse(report.contains("p.x + p.y = 8"), report);
        assertFalse(report.contains("p.x + p.y = 9"), report);
        assertFalse(report.contains("p.x + p.y in 9"), report);
    }

    @Test
    void aCutInsideWhereTheFormRunsDrawsOne() {
        String report = report(INSIDE_THE_SUM);

        assertTrue(report.contains("read as f/p.x + p.y: = 3"), report);
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
