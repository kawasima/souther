package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.Primary;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.Messages;
import souther.compiler.diag.SourceContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When more than one {@code example} row fails, every row is reported with its own reason. The
 * failures used to be collapsed into one count-only diagnostic ("2 examples do not hold") whose
 * actual/expected blocks were empty and whose hint pointed at errors that were never printed
 * (issue #98), so the author had to comment rows out one at a time to find out what happened.
 */
class ExampleMultipleFailuresTest {

    private static final String BASE = """
            module demo
            import String ( length )

            data 従業員ID = String
                invariant length(value) > 0

            data 名札 = { id: 従業員ID }

            behavior 作る : (id: 従業員ID) -> 名札
                constructs 名札

            let 作る (id) = 名札 { id = id }
            """;

    /** Two rows whose input fixture cannot be built — a failure with no value diff, the case that
     * used to render empty 実際 / 期待 blocks. */
    private static final String TWO_BAD_FIXTURES = BASE + """
            example 作る
              | "1件目" : (従業員ID("")) -> 名札 { id = 従業員ID("x") }
              | "2件目" : (従業員ID("")) -> 名札 { id = 従業員ID("y") }
            """;

    @Test
    void eachFailingRowIsItsOwnDiagnostic() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(TWO_BAD_FIXTURES));

        List<Diagnostic> ds = e.diagnostics();
        assertEquals(2, ds.size(), "one diagnostic per failing row");
        assertNotEquals(
                WhereItSits.in(TWO_BAD_FIXTURES,
                        ((Primary.InSource) ds.get(0).primary()).place().region()).start().line(),
                WhereItSits.in(TWO_BAD_FIXTURES,
                        ((Primary.InSource) ds.get(1).primary()).place().region()).start().line(),
                "each points at its own row");
        assertEquals(ds.get(0), e.diagnostic(), "the first is still the one a single-diagnostic caller reads");
    }

    @Test
    void everyRowKeepsItsOwnReason() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(TWO_BAD_FIXTURES));

        for (Diagnostic d : e.diagnostics()) {
            assertEquals("E1903", d.code());
            String rendered = new HumanRenderer(false)
                    .render(d, new SourceContext("probe.sou", TWO_BAD_FIXTURES, SourceLayout.of(TWO_BAD_FIXTURES)), Locale.ENGLISH);
            assertTrue(rendered.contains("invariant violated"),
                    "the reason the value did not produce, was: " + rendered);
            assertFalse(rendered.contains("examples do not hold"),
                    "no count-only summary stands in for the reason, was: " + rendered);
        }
    }

    @Test
    void aDiffLessFailureRendersNoEmptyActualExpected() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(TWO_BAD_FIXTURES));

        for (Diagnostic d : e.diagnostics()) {
            String rendered = new HumanRenderer(false)
                    .render(d, new SourceContext("probe.sou", TWO_BAD_FIXTURES, SourceLayout.of(TWO_BAD_FIXTURES)),
                            Messages.resolveLocale("ja"));
            assertFalse(rendered.contains("実際:"),
                    "a fixture that cannot be built has no value to compare, was: " + rendered);
        }
    }

    @Test
    void aSingleFailureStillReportsOneDiagnostic() {
        String model = BASE + """
                example 作る
                  | "1件目" : (従業員ID("")) -> 名札 { id = 従業員ID("x") }
                """;

        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(model));

        assertEquals(1, e.diagnostics().size());
    }

    @Test
    void anErrorWithNothingToReportIsRejected() {
        // a caller that did not check whether it found anything is a bug in the caller, not an
        // error with no diagnostic
        assertThrows(IllegalArgumentException.class,
                () -> CompileException.ofAll(List.of(), "nothing failed"));
    }

    @Test
    void twoMismatchesEachCarryTheirOwnValues() {
        String model = BASE + """
                example 作る
                  | "1件目" : (従業員ID("a")) -> 名札 { id = 従業員ID("x") }
                  | "2件目" : (従業員ID("b")) -> 名札 { id = 従業員ID("y") }
                """;

        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(model));

        List<Diagnostic> ds = e.diagnostics();
        assertEquals(2, ds.size());
        assertEquals("名札 { id = 従業員ID(\"a\") }", ds.get(0).diff().actualType());
        assertEquals("名札 { id = 従業員ID(\"b\") }", ds.get(1).diff().actualType());
    }
}
