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
     * An answer the body does not give is a failing row, and the account still counts where the row
     * went.
     *
     * <p>The control the law above needs. Coverage is about what the rows reached and correctness is
     * about what they answered, and a walk that folded them would let a model be called uncovered
     * for disagreeing — or, the other way about, let a disagreement be absorbed and never said.
     * Here the same rows are written with one answer the body refuses: the account comes out exactly
     * as it does above, and the build is refused over the row.
     */
    @Test
    void anAnswerTheBodyRefusesIsAFailingRowRatherThanAHoleInTheAccount() {
        List<String> offered = rowsOffered(MODEL);
        String agreeing = withRows(MODEL, offered);
        String disagreeing = withRows(MODEL, offered, "Yes");

        assertEquals(List.of("E1905"), errorsIn(disagreeing),
                () -> "the row whose answer the body refuses is reported:\n" + disagreeing);
        assertEquals(report(agreeing), report(disagreeing),
                "and the account says the same of both: where a row went is not what it answered");
    }

    /** The lines the report marks as work left, which a settled account has none of. */
    private static List<String> marked(String human) {
        return human.lines().filter(line -> line.stripLeading().startsWith("! ")).toList();
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
        return withRows(model, rows, null);
    }

    /**
     * The same, with {@code insteadOf} written at the first row it is not the answer to.
     *
     * <p>Which is what an author does who writes what they believe the model owes and is wrong
     * about it, and is the one way a completion can miss what it was offered for: the inputs are
     * the block's and only the answer is theirs.
     */
    private static String withRows(String model, List<String> rows, String insteadOf) {
        StringBuilder out = new StringBuilder(model);
        boolean written = false;
        for (String row : rows) {
            String owed = answerFor(row);
            String answer = !written && insteadOf != null && !insteadOf.equals(owed)
                    ? insteadOf : owed;
            written |= !answer.equals(owed);
            out.append("    ")
                    .append(row, 0, row.length() - "<?>".length())
                    .append(answer)
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
