package souther.compiler;

import org.junit.jupiter.api.Test;
import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.compiler.report.GeneratedRows;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row is composed inside the environment the module already states.
 *
 * <p>A {@code fake} block is what a module says its rows run against, and every row somebody writes
 * runs against it. A row this compiler composes is a row somebody is about to write, so it is
 * composed against the same thing: what the module answers is left answered, and the row writes at
 * a dependency only what it has to say of its own.
 *
 * <p>Two things follow, and they are the two this holds. A way needing one dependency to answer
 * differently at different calls is a way a row cannot write a value for — and the module's table
 * answers call by call, so wherever a module states one there is a row for such a way after all,
 * where before this compiler said only that a table was what it needed. And a way asking nothing of
 * a dependency is left to the table rather than written over: a row writing its own value there
 * would be certified in an environment none of the module's own rows are in, and would go somewhere
 * else the moment it was pasted in beside them.
 *
 * <p>Whether the table answers what a way needs is not decided while the row is composed. It is
 * what the run finds, which is what a trial is for — the table is read where the table is, and a
 * second reading of it here would be the reading rather than the fact.
 */
class AModulesTableIsTheEnvironmentARowIsComposedInTest {

    /**
     * A body deciding on one dependency's answer at two calls.
     *
     * <p>The outer {@code match} reads what the dependency answers for the input and the inner one
     * what it answers for a number of the body's own, so the way through {@code Cleared} and then
     * {@code Blocked} needs the two calls answered differently.
     */
    private static final String BODY = """
            module example.stood

            data Yes
            data No
            data Answer = Yes | No

            data Blocked = Int
            data Cleared

            behavior lookup : (id: Int) -> Blocked | Cleared

            behavior decides : (id: Int) -> Answer
                depends on lookup
            let decides (id, lookup) =
                if id > 5 then
                    match lookup(id) with
                        | Blocked -> No
                        | Cleared ->
                            match lookup(0) with
                                | Blocked -> Yes
                                | Cleared -> No
                else No
            """;

    /** The module states nothing, so a row answers the dependency or nothing does. */
    private static final String NO_TABLE = BODY + """

            example decides
                | (0) with lookup = Cleared -> No
            """;

    /** And here the module states what the dependency answers, call by call. */
    private static final String A_TABLE = BODY + """

            fake lookup
                | (0) -> Blocked(1)
                | _ -> Cleared

            example decides
                | (0) -> No
            """;

    /**
     * The way wanting two answers is one this compiler has a row for where the module states a
     * table.
     *
     * <p>Held on the page rather than on the block, because what changed is what the search
     * settled: before, the rule came back as one nothing could be tried with, and a reader was
     * shown a way nobody could act on. Now it is a rule owed a row like its neighbours.
     */
    @Test
    void aWayWantingTwoAnswersIsSettledWhereTheModuleStatesATable() {
        assertTrue(decisionSection(human(NO_TABLE)).stream().anyMatch(line ->
                        line.contains("answer by what it was applied to")),
                "with nothing stated, the way wanting two answers is one nothing could be tried"
                        + " with");

        List<String> decision = decisionSection(human(A_TABLE));
        assertFalse(decision.stream().anyMatch(line -> line.contains("nothing could show a row")),
                () -> "and with a table stated, every way is settled: " + decision);
        assertTrue(decision.stream().anyMatch(line -> line.contains("`case Blocked` (19:17)")),
                () -> "the way through the second call's `Blocked` among them: " + decision);
    }

    /**
     * And a row goes out for it, standing the dependency in with nothing of its own.
     *
     * <p>Which is the whole of what makes the way reachable: the row is run against the table, the
     * table answers the two calls differently, and the run is what says the row goes there. Written
     * with a value of the row's own it would answer both calls alike and go somewhere else.
     */
    @Test
    void andTheRowThatGoesOutForItWritesNothingAtTheDependency() {
        List<String> rows = rowsOf(A_TABLE);

        assertTrue(rows.stream().anyMatch(row -> !row.contains("with lookup")),
                () -> "a row leaning on the table goes out: " + rows);
        assertEquals(List.of(), rowsOf(NO_TABLE).stream()
                        .filter(row -> !row.contains("with lookup")).toList(),
                "and with nothing stated every row answers the dependency itself");
    }

    /**
     * A way that does ask something of the dependency still writes it.
     *
     * <p>The row is looked at before the table, so a value written on it is the one thing that
     * certainly meets what the way asks. Left to the table because a table happens to be stated,
     * the rows for the two arms of one call would be one row, and whichever arm the table answers
     * with is the only one anybody would ever be offered.
     */
    @Test
    void aWayThatAsksSomethingOfItIsStillWrittenOnTheRow() {
        List<String> rows = rowsOf(A_TABLE);
        List<String> asking = rows.stream().filter(row -> row.contains("with lookup")).toList();

        assertTrue(asking.stream().anyMatch(row -> row.contains("Blocked")),
                () -> "the way through the first call's `Blocked` writes its own answer: " + asking);
        assertTrue(asking.stream().anyMatch(row -> row.contains("Cleared")),
                () -> "and so does the way through its `Cleared`: " + asking);
        // Those two ways and no others. The way turning on nothing the dependency answers has
        // nothing to say about it, and the way wanting two answers has nothing it can say; both
        // leave it to the table, and a row writing over the table at either is a row certified
        // somewhere its reader will not be.
        assertEquals(rows.size() - asking.size(), 2,
                () -> "the ways with one answer to ask for write it, and the rest leave it: "
                        + rows);
    }

    /** The rows a block offers, one to an entry. */
    private static List<String> rowsOf(String model) {
        Compilation compilation = measured(model);
        String block = GeneratedRows.of(compilation, compilation.modules().get(0), "decides", true,
                SourceRendering.namedByIdentity(compilation.texts())).text();
        return block.lines().map(String::trim).filter(line -> line.startsWith("| ")).toList();
    }

    /** The lines of the one implemented behavior's decision measure. */
    private static List<String> decisionSection(String page) {
        List<String> out = new ArrayList<>();
        boolean inside = false;
        for (String line : page.split("\n")) {
            if (line.startsWith("    decision ")) {
                inside = true;
            } else if (inside && !line.startsWith("      ") && !line.startsWith("          ")) {
                inside = false;
            }
            if (inside) {
                out.add(line);
            }
        }
        return out;
    }

    private static String human(String model) {
        Compilation compilation = measured(model);
        return AdequacyReport.of(compilation)
                .human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    private static Compilation measured(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(List.of(), compilation.errors().stream()
                        .map(e -> e.diagnostic().code().toString()).toList(),
                "the model under test compiles");
        return compilation;
    }
}
