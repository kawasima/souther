package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceRendering;
import souther.compiler.report.AdequacyReport;
import souther.compiler.report.GeneratedRows;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filling what the block offers for a rule settles it, and nothing offers it again.
 *
 * <p>The one law that says the surfaces are readings of one account. Each of the others holds one
 * step of it — that a rule no row takes is reported, that a row is offered against it, that a row
 * taking it discharges it — and every one of them can hold while the ends disagree: a person filling
 * everything the block offers, asking again and being offered nothing further, with the verdict
 * where it was.
 *
 * <p>So this walks the whole of it on one model. What is held is not the wording of any step but
 * that the steps meet: the rule the report names is the rule the block offers a row for, the row
 * written from it takes that rule, and the account then says so.
 */
class ARuleOfferedARowIsCoveredOnceTheRowIsWrittenTest {

    /**
     * A body whose rows reach every arm and every class, and one of whose ways nothing takes.
     *
     * <p>The case the decision account is for, and the one where a row offered for a rule is a row
     * no other measure asks for. {@code choose} answers alike under {@code Safe}, so the three rows
     * go through both of its arms and both of {@code decide}'s, and every class of both positions
     * has a row in it. What is left is the way through {@code Safe} and {@code Off}: a rule, and
     * nothing else here is short of anything.
     *
     * <p>A model whose rules the classes and the arms happen to cover would hold this law with the
     * decision account switched off — every step would pass on rows offered for something else.
     */
    private static final String MODEL = """
            module example.decide

            data Yes
            data No
            data Verdict = Yes | No

            data Safe
            data Risky
            data Mode = Safe | Risky

            data On
            data Off
            data Flag = On | Off

            let choose (flag: Flag, yes: Verdict, no: Verdict): Verdict =
                match flag with
                    | On  -> yes
                    | Off -> no

            behavior decide : (mode: Mode, flag: Flag) -> Verdict
            let decide (mode, flag) =
                match mode with
                    | Safe  -> choose(flag, Yes, Yes)
                    | Risky -> choose(flag, Yes, No)

            example decide
                | "safe on"   : (Safe, On)   -> Yes
                | "risky on"  : (Risky, On)  -> Yes
                | "risky off" : (Risky, Off) -> No
            """;

    /** What each row of the block answers, which the author writes and this test stands in for. */
    private static String answerFor(String row) {
        return row.contains("Risky") && row.contains("Off") ? "No" : "Yes";
    }

    @Test
    void whatTheBlockOffersForARuleIsCoveredOnceItIsWrittenAndIsNotOfferedAgain() {
        // The account names rules no row takes, and the page says so.
        assertTrue(rulesOwedBy(MODEL) > 0, "this model owes rows for rules it does not take");
        assertTrue(report(MODEL).contains("no row takes a decision rule"),
                () -> "and the page says so:\n" + report(MODEL));

        // The block offers a row for each of them, which is the work a person is handed.
        List<String> offered = rowsOffered(MODEL);
        assertFalse(offered.isEmpty(), () -> "the block offers rows: " + block(MODEL));

        // Written with their answers, those rows take the rules they were offered for.
        String filled = withRows(MODEL, offered);
        // Held to having compiled before anything is read off it. A model that did not compile
        // answers every question with nothing, and a law read off one is a law that holds while
        // the thing it is about is gone (#1579).
        assertEquals(List.of(), problemsIn(filled),
                () -> "the rows the block offered are rows this model admits:\n" + filled);
        assertEquals(0, rulesOwedBy(filled),
                () -> "every rule is taken once the rows are written:\n" + report(filled));

        // And nothing is offered a second time for work that is done.
        assertEquals(List.of(), rowsOffered(filled),
                () -> "nothing further is offered:\n" + block(filled));
        assertFalse(report(filled).contains("no row takes a decision rule"),
                () -> "and the page no longer names one:\n" + report(filled));
    }

    /** What this compiler refuses the model over, which a law read off it may have none of. */
    private static List<String> problemsIn(String model) {
        List<String> out = new ArrayList<>();
        measured(model).diagnostics().forEach((_, said) -> said.forEach(each -> {
            if (each.diagnostic().severity() == souther.compiler.diag.Severity.ERROR) {
                out.add(String.valueOf(each.diagnostic().code()));
            }
        }));
        return out;
    }

    /** How many rules of the model's one behavior no row takes. */
    private static long rulesOwedBy(String model) {
        return measured(model).db().ask(new Adequacy.DecisionFindings("example.decide")).value()
                .stream().filter(each -> each.about() instanceof About.ARuleNoRowTakes).count();
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

    /** The model with those rows written into its block, each with the answer its author writes. */
    private static String withRows(String model, List<String> rows) {
        StringBuilder out = new StringBuilder(model);
        for (String row : rows) {
            out.append("    ")
                    .append(row, 0, row.length() - "<?>".length())
                    .append(answerFor(row))
                    .append(System.lineSeparator());
        }
        return out.toString();
    }

    private static String block(String model) {
        Compilation measured = measured(model);
        return GeneratedRows.of(measured, "example.decide", null, true,
                SourceRendering.namedByIdentity(measured.texts())).text();
    }

    private static String report(String model) {
        Compilation measured = measured(model);
        return AdequacyReport.of(measured)
                .human(SourceRendering.namedByIdentity(measured.texts()));
    }

    private static Compilation measured(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport(Adequacy.AdequacyBar.CLASSES));
        compilation.answerEverything();
        return compilation;
    }
}
