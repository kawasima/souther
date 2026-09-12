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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
     * <p>An obligation is not absorbed by everything beside it having been closed, and the next run
     * says so. Without it the fixed point above is reachable by an account that forgets, which is
     * the same page as a block that stops offering.
     *
     * <p><b>Not the law {@code P(g) − D(complete(g))} states.</b> What is left owed here is not a
     * target of the proposals that were completed — nothing composes a row by the case it would
     * answer with — so this is an obligation of the account surviving a completion rather than a
     * target surviving its own proposal. Whether a target can survive at all is
     * {@link #everyTargetOfAProposalIsDischargedByWhateverIsAnsweredToIt}, which is where that law
     * stands or falls.
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
     * <p><b>Said rather than offered, and the block writes down which.</b> This obligation is one
     * the generator carries a shortfall for rather than a proposal, which is what "no silent loss"
     * asks of it.
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

    /**
     * Every target of a proposal is discharged by whatever is answered to it — so a proposal's
     * targets cannot survive their own completion, and there is nothing for the law about that to
     * preserve.
     *
     * <p><b>The status of {@code P(g) − D(complete(g))} under this generator, held as a check.</b>
     * A proposal is composed for a class, an arm or a rule ({@code OfferedRow} refuses to carry
     * anything else), and each of those is discharged by where the row goes. What an author writes
     * into a completion is the answer, and the answer moves none of them: the inputs are the
     * block's. So the set the law is about is empty, and the law holds by having nothing in it
     * rather than by anything this compiler does to keep it.
     *
     * <p><b>Which is a fact worth a check rather than a sentence.</b> The issue states the law from
     * a proposal targeting an output case beside an arm, completed into a row that goes through the
     * arm and expects another case; that proposal cannot be composed here, and a reader who takes
     * the law at its word will look for the machinery that keeps it and find none. Written down as
     * prose, the day a proposal comes to target something an answer settles — the output case the
     * block says it can compose no row for is the obvious candidate — the prose is what goes stale.
     * Written as this, that day is the day this goes red, and whoever made proposals cleverer is
     * told that the law now has something to preserve and needs a check that preserves it.
     *
     * <p>Read off the measures rather than off the composer, so that what is compared is what an
     * author is told. Two completions of one block, differing in every answer and in nothing else:
     * where the rows went is the same in both, measure for measure, and the signature is the one
     * place they differ because it is the one measure the answer is evidence for.
     *
     * <p>What keeps the vocabulary closed is {@code OfferedRow}, which refuses to carry a purpose
     * that is not one of the three. So this is where to come when that refusal is relaxed: a target
     * an answer can settle is what gives the law something to preserve, and the check for it has to
     * be written then.
     *
     * <p>With a control, because two readings of one unchanged thing are equal for free. The same
     * reading over the model before its block was written is different, so the comparison has the
     * resolution the claim needs.
     */
    @Test
    void everyTargetOfAProposalIsDischargedByWhateverIsAnsweredToIt() {
        List<String> offered = rowsOffered(MODEL);
        List<String> owed = whereTheRowsWent(report(withRows(MODEL, offered)));
        List<String> believed =
                whereTheRowsWent(report(answeredThroughoutWith(MODEL, offered, "Yes")));

        assertFalse(offered.isEmpty(), "there are proposals whose targets this is about");
        assertFalse(owed.isEmpty(), "and measures of where their rows went");
        assertEquals(owed, believed,
                "a proposal's targets are discharged by any answer written into it");
        assertNotEquals(owed, whereTheRowsWent(report(MODEL)),
                "and the reading tells two row sets apart, so the equality above is a measurement");
        // And the one measure an answer is evidence for says the two apart, so this is not two
        // readings of one unchanged report.
        assertTrue(report(withRows(MODEL, offered)).contains("out specified 2/2"),
                "the completion that answers what the model owes states both cases");
        assertTrue(report(answeredThroughoutWith(MODEL, offered, "Yes"))
                        .contains("out specified 1/2"),
                "and the one that does not, states one");
    }

    /** The measures of where a behavior's rows went, which is what a proposal's targets are made
     *  of: the classes, the arms, the ways through the body and the lines. */
    private static List<String> whereTheRowsWent(String human) {
        return human.lines().map(String::strip)
                .filter(line -> line.startsWith("partition ") || line.startsWith("branch ")
                        || line.startsWith("decision ") || line.startsWith("border ")
                        || line.startsWith("declarations "))
                .toList();
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
