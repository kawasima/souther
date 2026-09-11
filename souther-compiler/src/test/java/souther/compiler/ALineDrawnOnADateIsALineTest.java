package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body comparing a date draws a line, and the rows are owed both sides of it.
 *
 * <p>{@code Date} is one of the ordered primitives — {@code <} typechecks and the branch is real —
 * and the reading that draws lines was written for numbers, so a rule dividing an input by a date
 * came back as a position the model divides no way. Every business rule with a cutover date is this
 * one.
 *
 * <p>A date is a discrete total order, so what an interval and a neighbour need of it is what a whole
 * number gives: compare, and the value one step either way. Nothing about that is arithmetic on the
 * text, and nothing a row or a report shows may be the count that carries it — an author reads
 * {@code Date("2026-01-01")}, never a day number.
 */
class ALineDrawnOnADateIsALineTest {

    private static final String MODEL = """
            module example.dated

            data Stale
            data Fresh

            behavior freshness : (on: Date) -> Stale | Fresh
            let freshness (on) = if on < Date("2026-01-01") then Stale else Fresh

            example freshness
                | "before the line" : (Date("2025-12-31")) -> Stale
            """;

    private static String report() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    /** The position is divided, where it used to be one nothing was established about. */
    @Test
    void thePositionIsDivided() {
        String human = report();

        assertTrue(human.contains("partition   axes 1"), human);
        assertFalse(notReadAbout(human, "on"), human);
    }

    /** Both sides of the line are owed, and the row already written covers the one it is on. */
    @Test
    void bothSidesOfTheLineAreOwed() {
        String human = report();

        assertTrue(human.contains("border      borders 1   obligations 1/4"), human);
        assertTrue(human.contains("2026-01-01"), human);
    }

    /** What a report shows is the date, never the count that carries it. */
    @Test
    void nothingShowsTheDayNumber() {
        String human = report();

        assertFalse(human.contains("20454"), human);
        assertFalse(human.contains("20453"), human);
    }

    /**
     * Whether any {@code not read} line of {@code block} is about {@code position}.
     *
     * <p>Asked as a line rather than as a prefix. A finding about a rule names the rule first and
     * the position after it, and one about a position names the position — so a test matching
     * `+not read: <position>+` stopped meaning anything for the first kind rather than failing,
     * which is a negative assertion that passes because the words moved.
     */
    private static boolean notReadAbout(String block, String position) {
        return block.lines().anyMatch(line -> line.contains("not read:")
                && (line.contains("not read: " + position + " ")
                        || line.contains("about `" + position + "`")));
    }
}
