package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A point this compiler declined to compose a value for is a point the model still owes a row at.
 *
 * <p>What the account is for is telling an author what the model owes. A budget of this compiler's
 * is not the model saying anything: the row it declined to build may be the easiest one in the file
 * to write by hand, and an obligation that leaves the count over it is this compiler's own policy
 * printed as a fact about what somebody wrote.
 *
 * <p><b>The same rule reached by two different figures.</b> One model asks for a collection of a
 * given length and one asks for a total spread over elements that hold at most one, and they stop at
 * two different budgets in two different stages. Held against one of them, closing the other would
 * be a separate piece of work nobody could see was missing.
 *
 * <p>And read as a count that does not move. What a strict build refuses over changes with the
 * number in the model, which is the model's own business; how many rows it owes does not.
 */
class APointThisCompilerDeclinedToComposeForStillOwesARowTest {

    /** A rule on how many elements a list holds, with one short row written. */
    private static String aLengthOf(int many) {
        return """
                module example.count

                data Many
                data Few

                behavior decide : (items: List<Int>) -> Many | Few

                let decide (items) =
                    if List.length(items) >= %d
                    then Many else Few

                example decide
                    | "three is few" : ([ 1, 2, 3 ]) -> Few
                """.formatted(many);
    }

    /** The same rule on a total, over elements the rules leave at most one. */
    private static String aTotalOf(int total) {
        return """
                module example.total

                data Many
                data Few

                data Small = Int
                    invariant range = value >= 0 && value <= 1

                data Line = { amount: Small }

                behavior decide : (lines: List<Line>) -> Many | Few

                let decide (lines) =
                    if List.sum(List.map(one -> one.amount.value, lines)) >= %d
                    then Many else Few

                example decide
                    | "one line" : ([ Line { amount = Small(1) } ]) -> Few
                """.formatted(total);
    }

    /**
     * The line owes its four points at every number, on both sides of every figure.
     *
     * <p>Ten is under every budget there is, and two hundred is over the ones these models reach.
     * Sixty-four and sixty-five are either side of how many elements a proposal holds, which is
     * where the count used to start moving.
     */
    @Test
    void theCountDoesNotMoveWithTheFigureThisCompilerStopsAt() {
        List<String> counted = new ArrayList<>();
        for (int many : new int[] {10, 64, 65, 200}) {
            counted.add(many + ": " + obligationsIn(report(aLengthOf(many))));
        }
        for (int total : new int[] {10, 200}) {
            counted.add(total + ": " + obligationsIn(report(aTotalOf(total))));
        }

        assertEquals(List.of("10: 1/4", "64: 1/4", "65: 1/4", "200: 1/4", "10: 1/4", "200: 1/4"),
                counted,
                "one row is written and four points are owed, whatever this compiler could build");
    }

    /**
     * And the point says which figure this compiler stopped at, rather than that nothing promises a
     * row there.
     *
     * <p>The two sentences send an author to different places. One says the model admits no row and
     * the work is to write a different rule; the other says a policy here ran out, and names the
     * figure that would have to give.
     */
    @Test
    void thePointNamesTheFigureRatherThanTheModel() {
        String length = report(aLengthOf(200));
        String total = report(aTotalOf(200));

        assertFalse(length.contains("not known to be writable"),
                () -> "nothing here says the model promises no row: " + length);
        assertTrue(length.contains(
                "this compiler stopped at how many elements a proposed collection holds"),
                () -> "and the figure it stopped at is named: " + length);
        assertTrue(total.contains("this compiler stopped at how many elements a total is spread"),
                () -> "the total reaches its own figure and names that one: " + total);
    }

    /**
     * A search a budget ended is not reported as one that composed nothing.
     *
     * <p>The two are one sentence away from each other and mean opposite things: a search that had
     * everything and reached nothing says what this compiler can do, and one that stopped says how
     * far it was willing to go.
     */
    @Test
    void aSearchABudgetEndedIsNotSaidToHaveComposedNothing() {
        String length = report(aLengthOf(200));

        assertFalse(length.contains("nothing composed one: this compiler stopped"),
                () -> "a stopped search wears its own opening: " + length);
    }

    /** What the border section of a report says a behavior's obligations came to. */
    private static String obligationsIn(String report) {
        return report.lines()
                .filter(each -> each.contains("border") && each.contains("obligations"))
                .map(each -> each.substring(each.indexOf("obligations") + "obligations ".length()))
                .findFirst().orElseThrow(() -> new AssertionError(report));
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }
}
