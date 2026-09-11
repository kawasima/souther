package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run a declaration is owed is written as a run, under the declaration that wrote the line.
 *
 * <p>Two of the four points of a line name a value and two name a region, and what a report says of
 * each is what it asks a row for. Written the same way, the sentence about a region said a run was
 * one value — {@code String.length(value) = 1 < String.length(value)} — which is a row nobody can
 * write and a sentence nobody can read.
 *
 * <p>Under the declaration and not under a behavior, because a run beside a clause's line stops
 * where the clause leaves the quantity: nothing in a body moved it, so one row anywhere settles it
 * and there is no body to send an author to.
 */
class ARunADeclarationIsOwedIsWrittenAsARunTest {

    /** A clause with a floor, and a row at the floor and nowhere above it. */
    private static final String AT_THE_FLOOR_ONLY = """
            module example.floor

            data Amount = Int
                invariant atLeastNothing = value >= 0

            behavior take : (a: Amount) -> Int
            let take (a) = 1

            example take
                | "at the line" : (Amount(0)) -> 1
            """;

    /**
     * The run above the line is short a row, and the report says so as a run.
     *
     * <p>The row at zero settles the point on the line. What is left is a row of an ordinary amount
     * above it, which is a region and reads as one.
     */
    @Test
    void aRegionADeclarationIsOwedReadsAsARegion() {
        String report = reportOf(AT_THE_FLOOR_ONLY);

        assertTrue(report.contains("no row is at the IN point value in 0 < value"),
                () -> "the run above the floor, written as a run: " + report);
        assertFalse(report.contains("IN point value = "),
                () -> "and never as one value, which no row could be written at: " + report);
    }

    /** And the point on the line, which a row does stand at, is not said to be short one. */
    @Test
    void thePointARowStandsAtIsNotSaidToBeShortOne() {
        String report = reportOf(AT_THE_FLOOR_ONLY);

        assertFalse(report.contains("no row is at the ON point"),
                () -> "the row at zero stands on the line: " + report);
    }

    private static String reportOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
