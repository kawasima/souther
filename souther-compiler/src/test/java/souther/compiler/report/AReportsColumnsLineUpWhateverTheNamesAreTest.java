package souther.compiler.report;

import souther.compiler.diag.SourceRendering;
import souther.compiler.report.AdequacyReport;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The adequacy report writes fixed-width columns, and a name written in Japanese takes twice the
 * room its characters suggest. Held from the caller rather than from the padding, because the
 * padding has a test of its own and would keep passing if the report went back to a format string:
 * a field width is applied inside the formatter, after the name is in its hands, and counts UTF-16
 * units there.
 *
 * <p>What is asserted is where the next field starts. That is the thing a reader sees and the thing
 * the two models have to agree about — a report is a table only if the column is in one place.
 */
class AReportsColumnsLineUpWhateverTheNamesAreTest {

    private static final String JAPANESE = """
            module 医療.支払

            data 金額 = Int
                invariant value >= 0

            behavior 合算 : (額: 金額) -> 金額

            let 合算 (額) = 額
            """;

    private static final String ASCII = """
            module medical.payment

            data Yen = Int
                invariant value >= 0

            behavior total : (amount: Yen) -> Yen

            let total (amount) = amount
            """;

    private static String report(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    /** An accounting of the columns that does not read the table the report is padded by. Every
     *  character these two models use is a CJK ideograph or ASCII. */
    private static int columnsOf(String text) {
        int at = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            at += (c >= 0x2E80 && c <= 0xA4CF) || (c >= 0xFF00 && c <= 0xFF60) ? 2 : 1;
        }
        return at;
    }

    /** The column {@code label} opens at on the first line that holds it. */
    private static int opensAt(String report, String label) {
        String line = report.lines().filter(l -> l.contains(label)).findFirst()
                .orElseThrow(() -> new AssertionError("no line says " + label + ":\n" + report));
        return columnsOf(line.substring(0, line.indexOf(label)));
    }

    @Test
    void a_module_name_in_japanese_leaves_its_measurement_where_an_ascii_one_does() {
        assertEquals(opensAt(report(ASCII), "measurement:"),
                opensAt(report(JAPANESE), "measurement:"));
        assertEquals(57, opensAt(report(JAPANESE), "measurement:"),
                "the name's field, and the space after it");
    }

    @Test
    void a_behavior_name_in_japanese_leaves_the_rest_of_its_row_where_an_ascii_one_does() {
        assertEquals(opensAt(report(ASCII), "implemented"),
                opensAt(report(JAPANESE), "implemented"));
        assertEquals(opensAt(report(ASCII), "rows"), opensAt(report(JAPANESE), "rows"));
    }
}
