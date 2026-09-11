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
 * A position nothing bounds is given a value the positions after it can finish, not one that merely
 * sits inside what they can reach.
 *
 * <p>A search bounds each position by what the rest can still add up to and enumerates it between
 * those ends. A position nothing bounds has no ends to run between, so one value is taken and the
 * walk goes on — and which value that is used to be settled by the reach alone. The reach is an
 * interval and the question is not: {@code 3a + 6b = 3} asks that {@code 3 - 3a} be a multiple of
 * six, which is {@code a} odd, and the value an unbounded range gives up is zero.
 *
 * <p>The same arithmetic was already written down as a pruning. Applied to a residue after a
 * position had been chosen it can only reject, and where a position offers one candidate there is
 * nothing for a rejection to leave.
 *
 * <p>Both kinds of position, because the arithmetic differs and only one of them counts. Over whole
 * numbers what a position is held to is an arithmetic progression. Over decimals it is dense and
 * still not every value — a third is not a decimal a model writes — so a coset there names a value
 * and has no next one.
 */
class APositionNothingBoundsIsGivenAValueTheRestCanCompleteTest {

    /**
     * Two positions nothing bounds, weighed three and six.
     *
     * <p>The quantity counts by three, three is a level it reaches, and {@code P { a = 1, b = 0 }}
     * is a row at it. The same model with both positions held between none and four is offered
     * exactly that row, so what fails is neither the reading of the level nor the arithmetic that
     * solves for it.
     */
    private static final String COUNTING = """
            module example.lattice

            data P = { a: Int, b: Int }

            data No = { why: Int }
            data Yes = { v: Int }
            data Result = No | Yes

            behavior f : (p: P) -> Result
                constructs Yes, No

            let f (p) = {
                guard 3 * p.a + 6 * p.b > 1 else No { why = 0 }
                guard 3 * p.a + 6 * p.b > 4 else No { why = 1 }
                Yes { v = 1 }
            }

            example f
                | "well above" : (P { a = 4, b = 0 }) -> Yes { v = 1 }
            """;

    /**
     * The same shape over positions whose values fill.
     *
     * <p>{@code p.a + 3 * p.b = 1} with {@code a} at zero asks for a third of {@code b}, and a third
     * is not a decimal a model writes. {@code P { a = 1m, b = 0m }} is a row at the point. This one
     * reaches the search by another way — the last position is divided rather than stepped to, and
     * the division not ending was read as the search giving up.
     */
    private static final String FILLING = """
            module example.dense

            data P = { a: Decimal, b: Decimal }

            data No = { why: Int }
            data Yes = { v: Int }
            data Result = No | Yes

            behavior f : (p: P) -> Result
                constructs Yes, No

            let f (p) = {
                guard p.a + 3m * p.b > 1m else No { why = 0 }
                Yes { v = 1 }
            }

            example f
                | "well above" : (P { a = 0m, b = 4m }) -> Yes { v = 1 }
            """;

    /**
     * Both ways round. A point that composed nothing is asked for all the same, so a reading that
     * stopped composing everything would pass a check that only looks for the absent sentence.
     */
    @Test
    void aLevelAProgressionReachesIsOwedARowAndNotAnAccountOfWhyNoneWasFound() {
        String report = report(COUNTING);

        assertTrue(report.contains("! no row is at the ON point (comparison"), report);
        assertTrue(report.contains("read as f/3 * p.a + 6 * p.b: = 3"), report);
        assertFalse(report.contains("the search left something untried before reaching 3 * p.a + 6 * p.b = 3"),
                report);
    }

    /** The same of a coset whose values fill, which the search reaches by dividing rather than by
     *  stepping. */
    @Test
    void aLevelACosetOfDecimalsReachesIsOwedARowToo() {
        String report = report(FILLING);

        assertTrue(report.contains("! no row is at the OFF point (comparison"), report);
        assertTrue(report.contains("read as f/p.a + 3 * p.b: = 1"), report);
        assertFalse(report.contains("the search left something untried before reaching p.a + 3 * p.b = 1"), report);
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
