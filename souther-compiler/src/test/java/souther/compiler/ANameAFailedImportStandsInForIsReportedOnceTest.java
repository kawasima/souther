package souther.compiler;

import souther.compiler.diag.Primary;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.MessageKeys;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Db;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An import line that could not bring a name in is reported on that line, and the uses of the name
 * below it are not reported at all.
 *
 * <p>What is wrong is the import, and an author sent to each use instead is sent to the wrong place
 * as many times as the model writes the name. So the name stays in scope denoting nothing, and a use
 * of it takes the error type.
 *
 * <p>Held here rather than on the mechanism. The scope used to hold a {@code TypeSymbol} of a module
 * no source may name for this, which is why every reader below it had to ask whether the identity it
 * had was really one; the answer is a {@code Denotation} now. Either way what must be true is what a
 * build says, and nothing said it before this.
 */
class ANameAFailedImportStandsInForIsReportedOnceTest {

    private static final String LIB = """
            module probe.lib exposing ( Sku )

            data Sku = String
            """;

    /** A module importing a name `probe.lib` does not expose, and writing it three times. */
    private static final String APP = """
            module probe.app

            import probe.lib ( Sku, Amount )

            data Line = { sku: Sku, price: Amount }

            data Total = { amount: Amount }

            behavior priced : (line: Line) -> Amount

            let priced (line) = line.price
            """;

    @Test
    void theImportLineIsWhatIsReported() {
        assertEquals(List.of("E1507"), said(LIB, APP));
    }

    /** And the report is on the import line, not on any of the three uses. */
    @Test
    void andItIsReportedThere() {
        assertEquals(List.of(3), lines(APP, LIB, APP));
    }

    /** What a compile of these sources said — a diagnostic's code where it has one, its message key
     * where it does not. */
    private static List<String> said(String... sources) {
        List<String> found = new ArrayList<>();
        for (Diagnostic d : diagnostics(sources)) {
            found.add(d.code() != null ? d.code() : MessageKeys.of(d.said()));
        }
        return found;
    }

    /** The lines it said them at. */
    private static List<Integer> lines(String in, String... sources) {
        List<Integer> found = new ArrayList<>();
        for (Diagnostic d : diagnostics(sources)) {
            found.add(WhereItSits.in(in, ((Primary.InSource) d.primary()).place().region())
                    .start().line());
        }
        return found;
    }

    private static List<Diagnostic> diagnostics(String... sources) {
        Compilation compilation = Compilation.ofSources(List.of(sources), ModulePath.EMPTY);
        compilation.answerEverything();
        List<Diagnostic> found = new ArrayList<>();
        for (Db.Found report : compilation.db().allReports()) {
            found.add(report.report().diagnostic());
        }
        return found;
    }
}
