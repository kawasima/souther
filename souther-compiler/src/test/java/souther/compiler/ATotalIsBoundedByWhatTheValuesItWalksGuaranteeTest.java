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
 * A point below what the values a total is taken over admit is excluded, and not undecided.
 *
 * <p>A guard at nought over a total of amounts declared at or above nought draws a border whose
 * outward point is below nought. Nothing the model admits stands there, and the report has the words
 * for exactly that — it says them of a bound on a position everywhere else. What it said of the
 * total was that it did not know: the point was owed, no row could be composed for it because none
 * exists, and the account carried a debt no rows a person writes could ever meet.
 *
 * <p>What was missing was that a total is answered by no position, and only a position published
 * where a number runs. Now the run answers for its own total, out of what every value it walks
 * guarantees and the step the operation repeats.
 *
 * <p>The point being excluded is the whole of the claim. The neighbouring points stay owed and stay
 * reported, which is what says the border is still being measured rather than dropped.
 */
class ATotalIsBoundedByWhatTheValuesItWalksGuaranteeTest {

    /** Amounts at or above nought, held in a record, totalled and guarded at nought. */
    private static final String AMOUNTS = """
            module example.total

            data Amount = Int
                invariant value >= 0

            data Item = { cost: Amount }

            data Ok = { n: Int }
            data Bad

            let total (items: List<Item>): Int =
                List.sum(List.map(i -> i.cost.value, items))

            behavior settle : (items: List<Item>) -> Ok | Bad
                constructs Ok

            let settle (items) = {
                guard total(items) > 0 else Bad

                Ok { n = total(items) }
            }

            example settle
                | "one item" : ([ Item { cost = Amount(3) } ]) -> Ok { n = 3 }
            """;

    /**
     * The same amounts under a sum every case of which spreads them, which is the shape the issue
     * was found on.
     *
     * <p>Its own model because the path a run reads from stands at no position once it descends
     * through a sum. A reading that took the position at that path answers here and nowhere else,
     * and the two models together are what says the answer does not come from one.
     */
    private static final String UNDER_A_SUM = """
            module example.sum

            data Amount = Int
                invariant value >= 0

            data Common = { amount: Amount }
            data Fare = { ...Common, from: String }
            data Stay = { ...Common, nights: Int }
            data Cost = Fare | Stay

            data Item = { cost: Cost }

            data Ok = { n: Int }
            data Bad

            let total (items: List<Item>): Int =
                List.sum(List.map(i -> i.cost.amount.value, items))

            behavior settle : (items: List<Item>) -> Ok | Bad
                constructs Ok

            let settle (items) = {
                guard total(items) > 0 else Bad

                Ok { n = total(items) }
            }

            example settle
                | "one item" : ([ Item { cost = Fare { amount = Amount(3), from = "x" } } ])
                    -> Ok { n = 3 }
            """;

    /**
     * The same guard over numbers nothing declares, where the point below nought is a row somebody
     * can write.
     *
     * <p>What holds the answer to the declarations rather than to the operation. A total is not
     * excluded below nought because it is a total; it is excluded there when the values it walks
     * leave nothing there, and here they leave plenty.
     */
    private static final String PLAIN_NUMBERS = """
            module example.plain

            data Ok = { n: Int }
            data Bad

            let total (ns: List<Int>): Int = List.sum(ns)

            behavior settle : (ns: List<Int>) -> Ok | Bad
                constructs Ok

            let settle (ns) = {
                guard total(ns) > 0 else Bad

                Ok { n = total(ns) }
            }

            example settle
                | "one number" : ([ 3 ]) -> Ok { n = 3 }
            """;

    /** The amounts are at or above nought, so nothing stands below the guard's line. */
    @Test
    void aTotalOfAmountsAtOrAboveNoughtOwesNoPointBelowIt() {
        String said = report(AMOUNTS);

        assertTrue(said.contains("no OUT point is owed at List.sum(items[*].cost) = 0"
                + " (comparison@18:24): excluded — the rules leave no value there"), said);
    }

    /** And the same where the amounts are reached through a sum, which is where the issue was found. */
    @Test
    void andWhereTheAmountsAreReachedThroughASum() {
        String said = report(UNDER_A_SUM);

        assertTrue(said.contains("no OUT point is owed at List.sum(items[*].cost.amount) = 0"
                + " (comparison@23:24): excluded — the rules leave no value there"), said);
    }

    /** Neither of them owes it, said of the whole report: a line removed from one model and left on
     *  the other is the defect and not the fix. */
    @Test
    void neitherOwesARowNobodyCanWrite() {
        for (String model : java.util.List.of(AMOUNTS, UNDER_A_SUM)) {
            String said = report(model);
            assertFalse(said.contains("at the OUT point"),
                    () -> "no row is owed below the line, and the report says so: " + said);
        }
    }

    /**
     * The border is still measured. Its other points are owed and reported, which is what tells a
     * point excluded from a border nobody read.
     */
    @Test
    void theOtherPointsOfTheSameBorderAreStillOwed() {
        String said = report(AMOUNTS);

        assertTrue(said.contains("no row is at the ON point (comparison@18:24)"), said);
        assertTrue(said.contains("no row is at the OFF point (comparison@18:24)"), said);
    }

    /** And numbers nothing declares owe the point below the line, as they always did. */
    @Test
    void numbersNothingDeclaresStillOweThePointBelowTheLine() {
        String said = report(PLAIN_NUMBERS);

        assertFalse(said.contains("excluded — the rules leave no value there"), said);
        assertTrue(said.contains("OUT point"),
                () -> "nothing bounds these below, so the point below the line is owed: " + said);
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
