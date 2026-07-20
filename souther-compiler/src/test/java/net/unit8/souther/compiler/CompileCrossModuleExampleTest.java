package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An example may be written for a behavior whose input (or a fake's type) is an imported type
 * (spec 4, 14). The example evaluator decodes such a fixture against the type's declaring module,
 * not the module holding the example — so a cross-module fixture builds and the example evaluates.
 */
class CompileCrossModuleExampleTest {

    private static final String PRICING = """
            module pricing exposing ( Priced )
            data Priced = { total: Int }
            """;

    // b's example builds a Priced — a type imported from `pricing`. Its decoder lives in pricing's
    // package, so the evaluator must resolve the fixture there, not in b.
    private static final String ORDER = """
            module order
            import pricing ( Priced )
            data Receipt = { total: Int }
            behavior bill : (p: Priced) -> Receipt
                constructs Receipt
            let bill (p) = Receipt { total = p.total }
            example bill
              | (Priced { total = 300 }) -> Receipt { total = 300 }
            """;

    @Test
    void anExampleFixtureMayBeAnImportedType() {
        assertDoesNotThrow(() -> Compiler.compileModules(List.of(PRICING, ORDER)));
    }

    @Test
    void aCrossModuleExampleStillCatchesAMismatch() {
        String bad = ORDER.replace("-> Receipt { total = 300 }", "-> Receipt { total = 999 }");
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(PRICING, bad)));
        assertEquals("E1905", e.diagnostic().code());
    }
}
