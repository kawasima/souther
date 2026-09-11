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
 * A row at a point of a form is looked for where the rules leave one, not in the box around them.
 *
 * <p>Where a border is drawn and where a row for it can be written are two questions, and the second
 * is the one a search answers. Handed the ends of each position and nothing else, a search walks a
 * box that the rule relating those positions has cut a corner off: it offers an assignment inside
 * the box, the value it was for is refused at construction, and a report says that every value tried
 * was refused — of a point some other assignment sits at perfectly well.
 *
 * <p>The reach is right here and the search is not, which is what tells the two apart. Each position
 * already runs between ends the relation narrowed, and their sum already runs exactly as far as the
 * form does; the pair {@code (4, 0)} satisfies every rule and stands at the point the report says
 * nothing could be composed for.
 *
 * <p>Both directions. A reading that composed nothing anywhere would pass a check that only looks
 * for the absent sentence, so what a row does reach is asserted beside it.
 */
class ARowAtAFormsPointIsSearchedForWhereTheRulesLeaveOneTest {

    /**
     * Two fields the record holds three apart, so the box around them has a corner the rules refuse.
     *
     * <p>{@code x} runs from three to five and {@code y} from none to two, and the pairs where they
     * come to four are {@code (4, 0)} alone — every other pair of that sum is one the record
     * refuses.
     */
    private static final String APART = """
            module example.skew

            data N = Int
                invariant atLeastNone = value >= 0
                invariant atMostFive  = value <= 5

            data P = { x: N, y: N }
                invariant apart = x.value >= y.value + 3

            data No = { why: Int }
            data Yes = { v: Int }
            data Result = No | Yes

            behavior f : (p: P) -> Result
                constructs Yes, No
            let f (p) = {
                guard Int.add(p.x.value, p.y.value) <= 3 else No { why = 0 }
                Yes { v = 1 }
            }

            example f
                | "on the line" : (P { x = N(3), y = N(0) }) -> Yes { v = 1 }
            """;

    /**
     * The OFF point of the guard's line is a point a row stands at.
     *
     * <p>{@code P { x = N(4), y = N(0) }} satisfies every rule and puts the sum at four. So the
     * point is owed a row and the report asks for one — what it may not say is that every value
     * tried there was refused, which is what a walk of the box says after offering the one pair of
     * that sum the record does not allow.
     */
    @Test
    void aPointTheRulesLeaveAPairAtIsNotReportedAsRefused() {
        String report = report(APART);

        assertTrue(report.contains("read as f/p.x + p.y: = 4"), report);
        assertFalse(report.contains("every value tried at p.x + p.y = 4 was refused"), report);
    }

    /** And the run past it, which the same walk gave up on for the same reason. */
    @Test
    void aRunTheRulesLeaveAPairInIsNotReportedAsRefusedEither() {
        String report = report(APART);

        assertFalse(report.contains("every value tried at 4 < p.x + p.y <= 7 was refused"), report);
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
