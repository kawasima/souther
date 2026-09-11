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
 * The value a coset names for a position nothing bounds is one the rules may still refuse, and the
 * next member of it is the answer.
 *
 * <p>What the coefficients of the rest can land on says which values of a position leave them a
 * residue they reach. It says nothing about whether the rules leave the position that value: a
 * disequality takes one value out of the middle of a run without moving either end, so a position
 * carrying one looks unbounded to anything reading ranges and is refused at exactly one place.
 *
 * <p>Which is the defect a pair on a line was repaired for, and it reaches a form as readily. Here
 * the form is {@code a + b}, every whole number is on the coset, and the member the arithmetic
 * writes down is zero — the one value neither position holds. The row at the point is
 * {@code (1, -1)}, one step along.
 *
 * <p>The point is owed and this is what makes it so: with one value tried instead of several the
 * same point comes back as a search that stopped, and a point nothing is known to be writable at is
 * a point nobody is asked to write a row for.
 */
class AValueTheRulesTakeOutOfARunIsSteppedOffForAFormTooTest {

    private static final String A_HOLE_IN_BOTH_POSITIONS = """
            module example.hole

            data NonZero = Int
                invariant notNone = value /= 0

            data P = { a: NonZero, b: NonZero }

            data No = { why: Int }
            data Yes = { v: Int }
            data Result = No | Yes

            behavior f : (p: P) -> Result
                constructs Yes, No

            let f (p) = {
                guard p.a.value + p.b.value > 0 else No { why = 0 }
                Yes { v = 1 }
            }

            example f
                | "above" : (P { a = NonZero(4), b = NonZero(1) }) -> Yes { v = 1 }
            """;

    @Test
    void aPointWhoseCosetNamesARefusedValueIsStillOwedARow() {
        String report = report(A_HOLE_IN_BOTH_POSITIONS);

        assertTrue(report.contains("read as f/p.a + p.b: = 0"), report);
        assertFalse(report.contains("the search left something untried before reaching p.a + p.b = 0"), report);
    }

    /** And the point above it, which the same walk reaches at the value the coset names. */
    @Test
    void theOnPointIsOwedTheSameWay() {
        String report = report(A_HOLE_IN_BOTH_POSITIONS);

        assertTrue(report.contains("read as f/p.a + p.b: = 1"), report);
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
