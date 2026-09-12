package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Severity;
import souther.compiler.diag.SourceRendering;
import souther.compiler.report.AdequacyReport;
import souther.compiler.report.GeneratedRows;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Answer everything the block offers, ask again, and nothing is owed.
 *
 * <p>The law the surfaces exist to meet, over a model short at every derivation at once. Each of the
 * other checks holds one step — that an obligation is derived, that the report names it, that a row
 * is offered against it, that a row discharges it — and all of them can hold while the ends
 * disagree. What is held here is that the ends meet: what a person is handed, written out and
 * answered, closes exactly what the report was naming, and a second run has nothing further to say.
 *
 * <p>Over every derivation together rather than one at a time, which is what makes it about the
 * account. A model short only at its classes reaches this with the arms, the lines, the rules and
 * the signature switched off; the model here is short at all of them, so the walk cannot pass on
 * rows offered for something else.
 *
 * <p><b>Coverage and correctness stay two answers, which the second check holds.</b> A row is
 * offered with its answer unwritten because the author states what the model owes there; an author
 * who writes what the body does not do has written a failing row, and the account still counts
 * where that row went. Read the other way — a disagreement absorbed as an uncovered obligation —
 * the report would be answering a question about the model's correctness that nothing here asks.
 */
class TheBlockAnsweredIsTheFixedPointOfTheAccountTest {

    /**
     * A model owing a row at every derivation the account has.
     *
     * <p>A case of the output no row expects and a case of an input no row uses (the signature),
     * a class no row is in (the domain), an arm no row goes through (the branch), a way through the
     * body no row takes (the decision), points of the line the fork draws and of the line the
     * invariant draws (the borders, the second owed to the module's declarations).
     */
    private static final String MODEL = """
            module example.whole

            data Yes
            data No
            data Verdict = Yes | No

            data Amount = Int
                invariant value >= 0 && value <= 100

            data Small
            data Large
            data Size = Small | Large

            behavior judge : (size: Size, cost: Amount) -> Verdict
            let judge (size, cost) =
                if cost.value > 50 then No else Yes

            example judge
                | "small and cheap" : (Small, Amount(10)) -> Yes
            """;

    /** What each derivation is short of, in the report's own words. */
    private static final List<String> OWED_AT_EVERY_DERIVATION = List.of(
            "! no row expects `No`",
            "! no row uses `Large`",
            "! no row is in `Large` at size",
            "! no row goes through `then`",
            "! no row takes a decision rule",
            "! no row is at the ON point (comparison",
            "! no row is at an IN point (invariant Amount #1)",
            "! no row is at the ON point value = 0 (invariant Amount #1)");

    @Test
    void everythingTheBlockOffersAnsweredLeavesNothingOwed() {
        // Short at every derivation, so that what the walk closes is the account and not one
        // measure of it. Read off the page a person is given.
        String owing = report(MODEL);
        for (String said : OWED_AT_EVERY_DERIVATION) {
            assertTrue(owing.contains(said), () -> said + " is what this model owes:\n" + owing);
        }
        assertTrue(owing.contains("adequacy: not satisfied"), owing);

        // And the block offers rows against them. An obligation nothing could compose for says so
        // rather than going quiet: the case of the output is reached by answering a row and never
        // by a row composed for it, and the block writes that down.
        List<String> offered = rowsOffered(MODEL);
        assertFalse(offered.isEmpty(), () -> "the block offers rows:\n" + block(MODEL));
        assertTrue(block(MODEL).contains("nothing offers a row for `No` in `judge`"),
                () -> "and names what it could compose none for:\n" + block(MODEL));

        // Written out with their answers, they are rows this module keeps.
        String answered = withRows(MODEL, offered);
        assertEquals(List.of(), errorsIn(answered),
                () -> "the rows the block offered are rows this model admits:\n" + answered);

        // And then nothing is owed, nothing further is offered, and the verdict says so.
        String settled = report(answered);
        assertEquals(List.of(), marked(settled), () -> "nothing is left owed:\n" + settled);
        assertTrue(settled.contains("adequacy: satisfied"), () -> settled);
        assertEquals("", block(answered), () -> "and nothing is offered a second time");
    }

    /**
     * What the answers did not discharge stays owed, and is said again.
     *
     * <p>The law that makes the one above a law rather than a hope. A completion closes what it
     * closes; what it leaves is not absorbed by everything beside it having been closed, and the
     * next run says so. Without it the fixed point is reachable by an account that forgets, which
     * is the same page as a block that stops offering.
     *
     * <p>The completion here is the block's own rows with an answer of the author's: every one of
     * them written {@code Yes}, which is what somebody writes who has the policy wrong. The inputs
     * are the block's, so where the rows go is what the block composed them to reach — every class,
     * both arms, both ways through the body and every point — and all of that is discharged. What
     * is left is the case of the output no row now expects, and the account keeps naming it.
     *
     * <p><b>And it is not discharged by the body having answered with it.</b> Three of these rows
     * make the body answer {@code No}, and the measure says so in its own column — observed two of
     * two, specified one of two. What an output case is owed is a row that expects it, so a walk
     * reading the observation into the obligation would close it on the strength of the rows being
     * wrong.
     *
     * <p><b>Said rather than offered, and the block writes down which.</b> Nothing composes a row by
     * the case it would answer with, so this obligation is one the generator carries a shortfall for
     * rather than a proposal — which is what "no silent loss" asks of it, and is why the law's own
     * example, a proposal targeting an output case beside an arm, has nothing to stand for it here.
     * A proposal is composed for a class, an arm or a rule, and the author's answer settles none of
     * those.
     */
    @Test
    void whatTheAnswersMissedStaysOwedAndIsSaidAgain() {
        List<String> offered = rowsOffered(MODEL);
        String missed = answeredThroughoutWith(MODEL, offered, "Yes");

        // Everything the rows were composed to reach is discharged, and one thing is not.
        assertEquals(List.of("! no row expects `No`"), marked(report(missed)),
                () -> "what the answers missed, and nothing else:\n" + report(missed));
        assertTrue(report(missed).contains("adequacy: not satisfied"), () -> report(missed));

        // Not closed by the body having answered with it: the rows are what state a case.
        assertTrue(report(missed).contains("out specified 1/2  observed 2/2"),
                () -> "what was answered and what was expected are two columns:\n" + report(missed));

        // And the next run says it again, in the words of the thing that could compose no row.
        assertTrue(block(missed).contains("nothing offers a row for `No` in `judge`"),
                () -> "the block names what is still owed:\n" + block(missed));

        // Beside it, the other half of writing answers by hand: the rows that are wrong are wrong,
        // and that is a refusal about the model rather than a hole in the account.
        assertTrue(errorsIn(missed).contains("E1905"),
                () -> "the rows whose answers the body refuses are reported: " + errorsIn(missed));
    }

    /** The lines the report marks as work left, which a settled account has none of. */
    private static List<String> marked(String human) {
        return human.lines().map(String::strip).filter(line -> line.startsWith("! ")).toList();
    }

    /** What this compiler refuses a model over. A law read off a model it refuses is a law about
     *  nothing (#1579). */
    private static List<String> errorsIn(String model) {
        List<String> out = new ArrayList<>();
        measured(model).diagnostics().forEach((_, said) -> said.forEach(each -> {
            if (each.diagnostic().severity() == Severity.ERROR) {
                out.add(String.valueOf(each.diagnostic().code()));
            }
        }));
        return out;
    }

    /**
     * The rows the block offers, as an author would copy them.
     *
     * <p>Read off the text a person is handed rather than off the values a search composed. What
     * this law is about is the two ends meeting, and the end a person meets is the block.
     */
    private static List<String> rowsOffered(String model) {
        List<String> out = new ArrayList<>();
        for (String line : block(model).lines().toList()) {
            String row = line.strip();
            if (row.startsWith("| ") && row.endsWith("-> <?>")) {
                out.add(row);
            }
        }
        return out;
    }

    /** The model with those rows written into its block, each answered the way the model owes. */
    private static String withRows(String model, List<String> rows) {
        return written(model, rows, TheBlockAnsweredIsTheFixedPointOfTheAccountTest::answerFor);
    }

    /**
     * The same, with {@code answer} written at every one of them.
     *
     * <p>What an author does who has the policy wrong. The inputs are the block's and only the
     * answer is theirs, so this is a completion of the proposals rather than a different row set:
     * where the rows go is what the block composed them to reach, and what they state is not.
     */
    private static String answeredThroughoutWith(String model, List<String> rows, String answer) {
        return written(model, rows, _ -> answer);
    }

    private static String written(String model, List<String> rows,
                                  java.util.function.Function<String, String> answer) {
        StringBuilder out = new StringBuilder(model);
        for (String row : rows) {
            out.append("    ")
                    .append(row, 0, row.length() - "<?>".length())
                    .append(answer.apply(row))
                    .append(System.lineSeparator());
        }
        return out.toString();
    }

    /** What the model owes at {@code row}, which its author works out and this stands in for: the
     *  body answers `No` over the line the guard draws and `Yes` under it. */
    private static String answerFor(String row) {
        String written = row.substring(row.indexOf("Amount(") + "Amount(".length());
        return Integer.parseInt(written.substring(0, written.indexOf(')'))) > 50 ? "No" : "Yes";
    }

    private static String block(String model) {
        Compilation measured = measured(model);
        return GeneratedRows.of(measured, "example.whole", null,
                SourceRendering.namedByIdentity(measured.texts())).text();
    }

    private static String report(String model) {
        Compilation measured = measured(model);
        return AdequacyReport.of(measured)
                .human(SourceRendering.namedByIdentity(measured.texts()));
    }

    private static Compilation measured(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }
}
