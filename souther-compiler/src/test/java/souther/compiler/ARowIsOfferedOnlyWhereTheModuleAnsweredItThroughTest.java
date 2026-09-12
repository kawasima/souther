package souther.compiler;

import org.junit.jupiter.api.Test;
import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.GeneratedRows;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row is offered only where the module's own environment answered the row through.
 *
 * <p>A table a module states answers the calls it states answers for. A row leaning on it may make
 * a call it has no row for and no {@code _} to fall through to, and that is <em>E1909</em> where a
 * row is written: the row cannot be run, and an author is told so.
 *
 * <p>A candidate is run to find out where it goes, and a run that stops still went where it went —
 * an invariant refusing the answer, an {@code unreachable} reached, a budget spent. Each of those
 * is something the author's identical row would do too, and the row stays a row: what it found is
 * about the model. A call the environment has no answer for is not like them. It says this row is
 * not one this module can run at all, so nothing it passed on the way stands as a reason to offer
 * it — offered anyway, the search certifies a row that fails the moment it is pasted in, which is
 * the one thing a search of this module's environment exists to stop.
 */
class ARowIsOfferedOnlyWhereTheModuleAnsweredItThroughTest {

    /**
     * A body that calls its dependency without deciding on what it answers.
     *
     * <p>So the way asks nothing of the dependency and the row leans on the module's table, which
     * is what puts a table's dispatch under a composed row. The call is made only down one arm of
     * the body's own fork, so a candidate reaches that arm and then asks the table something.
     */
    private static String model(String fallback) {
        return """
                module example.partial

                data Yes
                data No
                data Answer = Yes | No
                data Note = Int

                behavior lookup : (id: Int) -> Note

                let keep (n: Note): Answer = Yes

                behavior decides : (id: Int) -> Answer
                    depends on lookup
                let decides (id, lookup) =
                    if id > 5 then keep(lookup(id))
                    else No

                fake lookup
                    | (0) -> Note(0)
                """
                + fallback
                + """

                example decides
                    | (0) -> No
                """;
    }

    /** The table as written answers one input, and a row taking the arm asks it about another. */
    private static final String PARTIAL = model("");

    /** The same table with a `_` row, which answers whatever it is asked. */
    private static final String TOTAL = model("    | _ -> Note(1)\n");

    /**
     * Every row the block offers runs where it is pasted.
     *
     * <p>Written out and compiled, which is the claim itself rather than something standing for it:
     * a row offered for an arm the table cannot answer a call on is a row an author is handed and
     * told is wrong.
     */
    @Test
    void everyRowOfferedRunsWhereItIsPasted() {
        for (String model : List.of(PARTIAL, TOTAL)) {
            List<String> offered = rowsOf(model);
            Compilation pasted = Compilation.ofSource(written(model, offered), "Main");
            pasted.answerEverything();

            List<String> said = pasted.errors().stream()
                    .map(each -> String.valueOf(each.diagnostic().code())).toList();
            assertFalse(said.contains("E1909"),
                    () -> "a row was offered that the module's table has no answer for: "
                            + offered + " came back " + said);
        }
    }

    /**
     * A row leans on the table where it cannot refuse a call, and answers the dependency itself
     * where it can.
     *
     * <p>The control the check above needs, and what keeps that check from being passed by giving
     * up. The two models differ by a `_` row and nothing else: under the one that answers whatever
     * it is asked the row writes nothing at the dependency and runs in the module's own
     * environment, and under the one that may refuse it writes a value of its own — which is a row
     * that still fills the arm, rather than an arm left without one.
     */
    @Test
    void aRowLeansOnTheTableOnlyWhereItCannotRefuseACall() {
        List<String> total = rowsOf(TOTAL).stream().filter(row -> takesTheArm(row)).toList();
        List<String> partial = rowsOf(PARTIAL).stream().filter(row -> takesTheArm(row)).toList();

        assertFalse(total.isEmpty(), "the arm is filled either way");
        assertTrue(total.stream().noneMatch(row -> row.contains("with lookup")),
                () -> "a table that answers whatever it is asked is the row's environment: "
                        + total);
        assertEquals(partial.size(), total.size(),
                () -> "the same ways are filled under a table that may refuse: " + partial);
        assertTrue(partial.stream().allMatch(row -> row.contains("with lookup")),
                () -> "and the row answers the dependency itself rather than leaning on it: "
                        + partial);
    }

    /** Whether a row takes the arm that calls the dependency, which is where the input is over the
     *  line the body's own fork draws. */
    private static boolean takesTheArm(String row) {
        String written = row.substring(row.indexOf('(') + 1, row.indexOf(')'));
        return Integer.parseInt(written.trim()) > 5;
    }

    /** The model with the offered rows written into its block, each answering something, so that
     *  what is left to fail is the table having no answer rather than the row disagreeing. */
    private static String written(String model, List<String> offered) {
        StringBuilder out = new StringBuilder(model);
        for (String row : offered) {
            out.append(row.replace("<?>", "Yes")).append("\n");
        }
        return out.toString();
    }

    /** The rows a block offers, one to an entry. */
    private static List<String> rowsOf(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(List.of(), compilation.errors().stream()
                        .map(each -> String.valueOf(each.diagnostic().code())).toList(),
                "the model under test compiles");
        String block = GeneratedRows.of(compilation, compilation.modules().get(0), "decides",
                SourceRendering.namedByIdentity(compilation.texts())).text();
        return block.lines().map(String::trim).filter(line -> line.startsWith("| ")).toList();
    }
}
