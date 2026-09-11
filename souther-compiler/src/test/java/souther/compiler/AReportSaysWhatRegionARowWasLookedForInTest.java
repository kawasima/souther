package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A report says what the search for a row was looked over, where the way to the line is not all in
 * it.
 *
 * <p>A row for a border a guard owes is looked for in the region that reaches the guard, and the
 * same sentence came out whether that region was the declarations because nothing stood on the way,
 * or the declarations because a condition above could not be read. The second is a limit of this
 * compiler and the first is not, and an author told only that a search composed nothing had no way
 * to find out that a guard above the one they are looking at is why.
 *
 * <p>Two models whose conditions above the line differ in whether this reading has words for one.
 * Not a controlled pair beyond that: a readable condition draws a line of its own and narrows the
 * region below it, so which points a search settles is not the same on both sides — which is the
 * mechanism working rather than a difference to hold against. What is held here is the sentence: it
 * is there where something on the way was not accounted for, it is absent where everything was, and
 * it is added to what the search already said rather than replacing it.
 */
class AReportSaysWhatRegionARowWasLookedForInTest {

    /** {@code above} written as the condition over the line, in a model nothing can build a row of. */
    private static String model(String above) {
        return """
                module example.region

                data Ok

                data Amount = Int
                    invariant range = value >= 0 && value <= 100

                data Tag = String
                    invariant shape = String.matches("(a+)\\\\1", value)

                data Pair = { low: Amount, high: Amount, tag: Tag }
                    invariant together = low.value /= high.value

                behavior check : (p: Pair) -> Ok

                let check (p) =
                    if %s then
                        if p.low.value > 10 then Ok else Ok
                    else Ok

                example check
                    | "one" : (Pair { low = Amount(5), high = Amount(7), tag = Tag("aa") }) -> Ok
                """.formatted(above);
    }

    /** A comparison over a string, which draws no line and is on the way to one. */
    private static final String NOTHING_READS_IT = "String.startsWith(p.tag.value, \"a\")";

    /** The same shape of condition, over a number, which this reading has words for. */
    private static final String READ = "p.high.value > 3";

    private static String report(String above) {
        Compilation compilation = Compilation.ofSource(model(above), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    /** The line about one point, which is where what a search came to is written. */
    private static String about(String point, String above) {
        String human = report(above);
        return human.lines()
                .filter(each -> each.contains("read as " + point))
                .findFirst().orElseThrow(() -> new AssertionError(human));
    }

    /**
     * A condition on the way that nothing could take in is said, beside what the search came to.
     *
     * <p>The sentence names what the row was composed against rather than what the region
     * represents, because the region is no longer the whole of it: a condition may be represented
     * there and still be one no value was composed under, and both leave the same gap between what
     * the row was built to be and what reaches the line.
     */
    @Test
    void aConditionTheRegionDoesNotAccountForIsSaid() {
        String line = about("check/p.low: = 11", NOTHING_READS_IT);

        assertTrue(line.contains("not every condition on the way to the line is one the row was"
                + " composed against"), line);
        assertTrue(line.contains(
                        "a condition that is neither a comparison nor a combination of them (17:8)"),
                "named for the shape this reading stopped at, and where it is written: " + line);
        // Beside what the search came to and not instead of it. The two answer different questions
        // — what happened, and what the search was looking over — and a reader acts on both.
        assertTrue(line.contains("nothing composed one: every value tried at p.low = 11 was"
                + " refused"), line);
    }

    /**
     * And a search that accounted for everything on the way says nothing extra.
     *
     * <p>Which is the half that makes the other half worth reading. Said of every search, the
     * sentence would be a decoration rather than a difference between two models.
     */
    @Test
    void aRegionThatAccountsForTheWholeWayIsNotRemarkedOn() {
        String line = about("check/p.low: = 11", READ);

        assertFalse(line.contains("not every condition on the way"), line);
    }

    /**
     * A border with nothing on the way to it says nothing either.
     *
     * <p>An invariant is about the values and holds wherever one stands, so there is nowhere for a
     * row to have come from and nothing that could have gone unaccounted for.
     */
    @Test
    void aLineNothingStandsOnTheWayToIsNotRemarkedOn() {
        // The rule is on the point and what the search came to is on the reading under it, so the
        // two are two lines: a line is owed once wherever it is read, and only the reading has a
        // position to name.
        assertTrue(report(NOTHING_READS_IT).contains(
                        "the ON point value = 0 (invariant Amount (range))"),
                () -> report(NOTHING_READS_IT));
        assertFalse(about("check/p.low: = 0", NOTHING_READS_IT)
                        .contains("not every condition on the way"),
                () -> about("check/p.low: = 0", NOTHING_READS_IT));
    }

}
