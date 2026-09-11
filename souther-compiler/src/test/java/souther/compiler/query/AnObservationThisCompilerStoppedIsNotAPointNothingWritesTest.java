package souther.compiler.query;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.report.AdequacyReport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule whose values this compilation could not read whole still owes the rows it owes.
 *
 * <p>How much of a value an observation keeps is a policy about what an answer may hold. It decides
 * nothing about the model, so the account of what an author owes is the same account whether or not
 * the walk that reads a row got to the end of it: the same rule is written, the same four points
 * are on its line, and what changes is only whether anybody can say a row is at one of them.
 *
 * <p><b>Held over the length of the reading, and not against one model.</b> A name standing for
 * another is the one thing a model may have any number of, so a wrapper added is not a statement
 * about anything — and every number an observation could stop at is a model somebody may write.
 * A regression for the model that was reported would go green again under a larger budget; this
 * would not.
 */
class AnObservationThisCompilerStoppedIsNotAPointNothingWritesTest {


    /**
     * The same rule with {@code names} wrappers around the list.
     *
     * <p>Every wrapper is one more name between the total and the values it is over. None of them
     * says anything about the rule, and past a dozen of them the walk that reads a row hands back a
     * value it stopped at rather than the list.
     */
    private static String model(int names) {
        StringBuilder out = new StringBuilder("""
                module example.through

                data 金額 = Int

                data 明細 = { 金額: 金額 }

                data 高額
                data 少額

                data 束1 = List<明細>
                """);
        for (int n = 2; n <= names; n++) {
            out.append("data 束%d = 束%d%n".formatted(n, n - 1));
        }
        out.append("%nbehavior 判定する : (明細: 束%d) -> 高額 | 少額%n".formatted(names));
        StringBuilder taken = new StringBuilder("件");
        for (int n = 1; n <= names; n++) {
            taken.insert(0, "束%d(".formatted(n)).append(")");
        }
        out.append("""

                let 判定する (%s) =
                    if List.sum(List.map(一件 -> 一件.金額.value, 件)) >= 100000
                    then 高額 else 少額

                example 判定する
                    | "ちょうど10万円なら高額" : (%s) -> 高額
                    | "1000円なら少額"        : (%s) -> 少額
                """.formatted(taken, wrapped(names, 100000), wrapped(names, 1000)));
        return out.toString();
    }

    /** One list of one detail of {@code amount}, under every wrapper the model declares. */
    private static String wrapped(int names, int amount) {
        StringBuilder out = new StringBuilder("[ 明細 { 金額 = 金額(%d) } ]".formatted(amount));
        for (int n = 1; n <= names; n++) {
            out.insert(0, "束%d(".formatted(n)).append(")");
        }
        return out.toString();
    }

    /**
     * One name between, or sixteen: the line owes four rows either way.
     *
     * <p>What the rows then answer is a second question and is allowed to differ — a reading that
     * stopped finds nothing, which is what it means to have stopped. What may not differ is how
     * many rows the model is asking for.
     */
    @Test
    void theSameFourRowsAreOwedHoweverManyNamesStandBetween() {
        Map<Integer, String> owed = new LinkedHashMap<>();
        for (int names : new int[] {1, 8, 12, 16}) {
            owed.put(names, counted(names));
        }

        assertEquals(Map.of(1, "4", 8, "4", 12, "4", 16, "4"), owed,
                () -> "one account however many names stand between: " + owed);
    }

    /**
     * And a reading that stopped is said as one, not as a point nothing can write a row at.
     *
     * <p>The two sentences send an author to different places. One says the rows do not settle a
     * point they are owed at; the other says to stop looking, because nothing here could write a
     * row there at all — and an author who reads the second goes looking for a model that admits no
     * such row. There is one, and they wrote it.
     */
    @Test
    void whatStoppedIsSaidAsUndecidedAndNotAsUnwritable() {
        String said = report(16);

        assertFalse(said.contains("not known to be writable"),
                () -> "a value this compiler declined to keep is not a point nothing writes:\n"
                        + said);
        assertTrue(said.contains("undecided whether a row is at the ON point"),
                () -> "what the rows left open is what an author is told:\n" + said);
    }

    /**
     * How many obligations the behavior's border account counts, as the report prints it.
     *
     * <p>Read off the account and not off how many points the line has. Every line has four, owed
     * or not; the number a reader acts on is the denominator of what the rows are held to, and it
     * is the one an observation must not move.
     */
    private static String counted(int names) {
        Matcher said = Pattern.compile("obligations \\d+/(\\d+)").matcher(report(names));
        assertTrue(said.find(), () -> "the border block prints an account at " + names + " names");
        return said.group(1);
    }

    private static String report(int names) {
        return AdequacyReport.of(compiled(names)).only("example.through", "判定する")
                .human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    private static Compilation compiled(int names) {
        Compilation compilation = Compilation.ofSource(model(names), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(List.of(), compilation.errors().stream()
                        .map(each -> each.diagnostic().code()).toList(),
                () -> "the model under test compiles at " + names + " names");
        return compilation;
    }
}
