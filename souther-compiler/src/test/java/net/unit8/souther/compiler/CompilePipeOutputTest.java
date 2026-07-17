package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A {@code >->} composition may optionally declare its output with a trailing {@code -> arms}
 * (spec 14.5). When declared, it must match the inferred output exactly — neither too narrow
 * (an arm the pipeline produces is missing) nor too wide (a declared arm the pipeline never
 * produces) is accepted (E1604). Without the declaration, inference stands and the check is off.
 */
class CompilePipeOutputTest {

    /** {@code guard} retires {@code TooLarge}; {@code toDoubled} yields {@code Doubled}, so the
     * pipeline produces {@code Doubled | TooLarge}. */
    private static String module(String pipeline) {
        return """
                module demo

                data Amount = Int
                data TooLarge = { limit: Int }
                data Doubled = Int

                behavior guard : (a: Amount) -> Amount | TooLarge constructs TooLarge
                let guard (a) = {
                    require a.value <= 100 else TooLarge { limit: 100 }
                    a
                }

                behavior toDoubled : (a: Amount) -> Doubled constructs Doubled
                let toDoubled (a) = Doubled { value: a.value }

                """ + pipeline + "\n";
    }

    @Test
    void declaredOutputMatchingInferenceCompiles() {
        assertDoesNotThrow(() ->
                Compiler.compile(module("behavior process = guard >-> toDoubled -> Doubled | TooLarge")));
    }

    @Test
    void anUndeclaredCompositionStillCompiles() {
        assertDoesNotThrow(() -> Compiler.compile(module("behavior process = guard >-> toDoubled")));
    }

    @Test
    void aTooNarrowDeclaredOutputIsE1604() {
        CompileException e = assertThrows(CompileException.class, () ->
                Compiler.compile(module("behavior process = guard >-> toDoubled -> Doubled")));
        assertEquals("E1604", e.code());
    }

    @Test
    void aTooWideDeclaredOutputIsE1604() {
        CompileException e = assertThrows(CompileException.class, () ->
                Compiler.compile(module("behavior process = guard >-> toDoubled -> Doubled | TooLarge | Amount")));
        assertEquals("E1604", e.code());
    }
}
