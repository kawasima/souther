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
 * The rows {@code souther examples --generate --boundaries} writes offer a value at a position that
 * is a sum of records, and say nothing about that position having none.
 *
 * <p>Whether a value can be composed for such a position is the compiler's question and is asked of
 * it ({@code ACaseComposedForOneReaderIsComposedForEveryReaderTest} in souther-compiler, issue
 * #651). What is asked here is that the answer reaches the report a reader actually runs — the two
 * came apart once, and the report said "no value can be written there" two lines under a row that
 * wrote one.
 */
class GeneratedRowsOfferAValueAtASumOfRecordsTest {

    private static final String MODULE = """
            module demo

            data Yen = Int invariant value >= 0
            data Ok = { n: Int }

            data Boxed = { a: Int, b: Int }
                invariant rising = a < b
            data Labelled = { s: String }
            data EveryCaseARecord = Boxed | Labelled

            data Wrapped = EveryCaseARecord

            data Bag = { xs: List<Int> }
                invariant enough = List.length(xs) >= 2

            behavior recordCases : (bill: Yen, v: EveryCaseARecord) -> Ok
                constructs Ok
            let recordCases (bill, v) = Ok { n = bill.value }

            example recordCases | (Yen(5), Boxed { a = 1, b = 2 }) -> Ok { n = 5 }
            """;

    /** The boundary strategy fills every other position, and one that is a sum is one of them. */
    @Test
    void aBoundaryRowIsOfferedWhereACompanionPositionIsASumOfRecords() throws Exception {
        String report = generated();

        List<String> rows = report.lines().filter(line -> line.startsWith("    | ")).toList();
        assertFalse(rows.isEmpty(), () -> "the block offers rows to look at: " + report);
        // The value at the boundary rather than the name of the line: a row composed only for a
        // line is offered without a name, since which of the lines it sits on is still owed is what
        // an unrelated row changes.
        assertTrue(rows.stream().anyMatch(line -> line.contains("Yen(0)")),
                () -> "the row at the boundary is offered: " + rows);
    }

    /** And the sentence that says otherwise is not printed beside the row that disproves it. */
    @Test
    void nothingSaysThePositionHasNoValue() throws Exception {
        String report = generated();

        assertFalse(report.contains("no value can be written there"),
                () -> "a case was composed two lines above:\n" + report);
    }

    private static String generated() throws Exception {
        Path file = Files.createTempDirectory("souther-651").resolve("model.sou");
        Files.writeString(file, MODULE);
        PrintStream was = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            Main.main(new String[] {"examples", file.toString(), "--generate", "--boundaries"});
        } finally {
            System.setOut(was);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
