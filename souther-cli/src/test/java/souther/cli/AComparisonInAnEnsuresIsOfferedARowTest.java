package souther.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A comparison written in an {@code ensures} draws a line, and {@code --generate} offers a row at
 * it.
 *
 * <p>The model's own lines come from more than a {@code data}'s invariant and a comparison in a
 * body. {@code ensures asked = NotFound -> id.value > 0} says the behavior may not answer
 * {@code NotFound} at or below zero and may above it, which is a value the stated relation changes
 * at — and a row there is one the model has a reason to want (issue #823).
 *
 * <p>Both sides, because the line has values on either side of it. That is what tells it from an
 * invariant's bound, where nothing outside the bound can be constructed at all and the value is the
 * whole of what there is to write.
 */
class AComparisonInAnEnsuresIsOfferedARowTest {

    private static final String MODULE = """
            module demo

            data TodoId = Int
            data Todo = { id: TodoId }
            data NotFound = { asked: TodoId }

            behavior findTodo : (id: TodoId) -> Todo | NotFound
                ensures asked = NotFound -> id.value > 0
                constructs Todo
            let findTodo (id) = Todo { id = id }

            example findTodo | (TodoId(5)) -> Todo { id = TodoId(5) }
            """;

    @Test
    void aRowIsOfferedAtTheValueTheClauseNames() throws Exception {
        List<String> rows = rowsOf(generated());

        assertTrue(rows.stream().anyMatch(line -> line.contains("TodoId(0)")),
                () -> "the clause draws a line at zero and no row is offered there: " + rows);
    }

    /** And at its neighbour, which is the other side of the line the clause draws. */
    @Test
    void aRowIsOfferedBesideIt() throws Exception {
        List<String> rows = rowsOf(generated());

        assertTrue(rows.stream().anyMatch(line -> line.contains("TodoId(1)")),
                () -> "the value beside the line is not offered: " + rows);
    }

    /**
     * A clause's line is met by writing the value; a fork's at the same value is not.
     *
     * <p>The distinction the whole thing turns on, in one report. The row hands the behavior a
     * hundred and takes the branch above the comparison, so it never reaches
     * {@code a.n.value > 100} — the fork's line at a hundred is unmet, and the clause's is met,
     * because every rule of a declaration runs whenever the behavior answers.
     *
     * <p>Matched on the word each rule is written with, so that the two are told apart by what
     * each is rather than by the value they name: a comparison is found where it is written and a
     * clause by the name the author gave it, and a report that called them one thing would have
     * this pass while saying nothing about which rule went unmet.
     */
    @Test
    void aClausesLineIsMetByWritingTheValueAndAForksIsNot() throws Exception {
        List<String> gaps = boundaryGaps(reportOf(REACHED_BY_ONE_RULE_ONLY));

        // Told apart by the rule and the role, which is what a point is named by: the value is the
        // reading's word and is said under the mark.
        assertTrue(gaps.stream().anyMatch(line -> line.contains("the ON point (comparison@")),
                () -> "no row got the fork's comparison to answer on its line: " + gaps);
        assertFalse(gaps.stream().anyMatch(line -> line.contains("the ON point (ensures")),
                () -> "the row writes a hundred, which is the whole of what the clause wants: "
                        + gaps);
    }

    private static final String REACHED_BY_ONE_RULE_ONLY = """
            module demo

            data N = Int
            data Amount = { flag: Bool, n: N }
            data Ok = { n: Int }
            data Refused = { why: String }

            behavior charge : (a: Amount) -> Ok | Refused
                ensures small = Refused -> a.n.value <= 100
                constructs Ok, Refused
            let charge (a) =
                if a.flag then
                    Ok { n = 1 }
                else
                    if a.n.value > 100 then Ok { n = 2 } else Refused { why = "small" }

            example charge
                | (Amount { flag = true, n = N(100) }) -> Ok { n = 1 }
            """;

    /**
     * The same, of a line between two of a behavior's positions.
     *
     * <p>A clause comparing one input against another draws a line where the two hold one count. It
     * is on neither of them, so it has no axis and no class either side — and what meeting it takes
     * is the same answer the clause's other lines get: the row that puts one count in both
     * positions has met it. The fork drawing the same line is not met, because the row takes the
     * branch above the comparison.
     */
    @Test
    void aClausesLineBetweenTwoPositionsIsMetByWritingBothValues() throws Exception {
        List<String> gaps = boundaryGaps(reportOf(A_LINE_BETWEEN_TWO_POSITIONS));

        assertTrue(gaps.stream().anyMatch(line -> line.contains("the ON point (comparison@")),
                () -> "no row got the fork's comparison to answer on its line: " + gaps);
        assertFalse(gaps.stream().anyMatch(line -> line.contains("the ON point (ensures")),
                () -> "the row puts one count in both positions, which is what the clause wants: "
                        + gaps);
        // The point one step from the line is a different pair, and this row is not at it. Named by
        // the role rather than by the value the two lines share, so the point beside the clause's
        // line cannot answer for the point on it.
        assertTrue(gaps.stream().anyMatch(line -> line.contains("the OFF point (ensures")),
                () -> "the pair one step from the line is owed and no row is at it: " + gaps);
    }

    private static final String A_LINE_BETWEEN_TWO_POSITIONS = """
            module demo

            data Minute = Int
            data Ok = { n: Int }
            data Refused = { why: String }

            behavior book : (from: Minute, to: Minute) -> Ok | Refused
                ensures ordered = Refused -> from.value >= to.value
                constructs Ok, Refused
            let book (from, to) =
                if from.value == 0 then
                    Ok { n = 0 }
                else
                    if from.value < to.value then Ok { n = 1 } else Refused { why = "no" }

            example book
                | (Minute(0), Minute(0)) -> Ok { n = 0 }
            """;

    private static List<String> rowsOf(String report) {
        return report.lines().filter(line -> line.startsWith("    | ")).toList();
    }

    /** The lines a report marks as boundaries nothing is at. */
    private static List<String> boundaryGaps(String report) {
        return report.lines().map(String::strip)
                .filter(line -> line.startsWith("! no row is at ")).toList();
    }

    private static String generated() throws Exception {
        return reportOf(MODULE);
    }

    private static String reportOf(String module) throws Exception {
        Path file = Files.createTempDirectory("souther-823").resolve("model.sou");
        Files.writeString(file, module);
        PrintStream was = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            Main.main(new String[] {"examples", file.toString(), "--generate"});
        } finally {
            System.setOut(was);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
