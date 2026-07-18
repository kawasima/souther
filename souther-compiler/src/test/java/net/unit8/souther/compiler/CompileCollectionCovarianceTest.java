package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Immutable collections are element-covariant (spec 6): with {@code A} a case of the sum {@code S}
 * ({@code A <: S}), a {@code List}/{@code Map}/{@code Option} of {@code A} is assignable where one
 * of {@code S} is expected. This is sound because the collections cannot be mutated, so no write
 * can smuggle a sibling case in. Covariance follows the case→sum relation only; two sibling cases stay
 * unrelated.
 */
class CompileCollectionCovarianceTest {

    private static final String BASE = """
            module demo
            data A = { v: Int }
            data B = { v: Int }
            data S = A | B
            """;

    /** Copies a field of type {@code fromType} into a field of type {@code toType}, exercising
     * {@code assignable(fromType, toType)} at the record-literal field init. */
    private static String mod(String toType, String fromType) {
        return BASE + """
                data W1 = { x: %s }
                data W2 = { y: %s }
                behavior f : (w: W2) -> W1 constructs W1
                let f (w) = W1 { x: w.y }
                """.formatted(toType, fromType);
    }

    @Test
    void listIsElementCovariant() {
        assertDoesNotThrow(() -> Compiler.compile(mod("List<S>", "List<A>")));
    }

    @Test
    void mapIsValueCovariant() {
        assertDoesNotThrow(() -> Compiler.compile(mod("Map<String, S>", "Map<String, A>")));
    }

    @Test
    void optionIsElementCovariant() {
        assertDoesNotThrow(() -> Compiler.compile(mod("S?", "A?")));
    }

    @Test
    void covarianceDoesNotRelateSiblingCases() {
        // A and B are both cases of S, but unrelated to each other: List<A> is not a List<B>
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(mod("List<B>", "List<A>")));
        assertTrue(e.getMessage().contains("expects"), e.getMessage());
    }
}
