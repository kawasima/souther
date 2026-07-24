package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A failing {@code example} whose failure carries no value-diff (an input fixture that cannot be
 * built) must still surface its code, location and reason through the annotation processor path —
 * {@link CompileException#getMessage()} is what the processor prints. Before, this collapsed to a
 * bare {@code "example failed"} with no code or reason, forcing a drop into the compiler internals
 * to find out why.
 */
class ExampleFailureDiagnosticTest {

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

    @Test
    void aFixtureThatCannotBeBuiltReportsCodeAndReason() {
        // 従業員ID("") violates its invariant, so the input fixture cannot be decoded (a diff-less
        // failure). The empty string cannot be the id.
        String model = BASE + """
                example 作る
                  | "empty id" : (従業員ID("")) -> 名札 { id = 従業員ID("x") }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(model));

        assertNotEquals("example failed", e.getMessage(),
                "the processor message must not collapse to a bare 'example failed'");
        assertTrue(e.getMessage().contains("E1903"),
                "message should carry the code E1903, was: " + e.getMessage());
    }
}
