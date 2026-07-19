package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code match} whose cases yield different data types widens to the union of those types, the
 * same way an {@code if} does (spec 16.2, 16.3). Before, match required every branch to have the
 * identical type; now {@code | A -> OutA} and {@code | B -> OutB} give {@code OutA | OutB}.
 */
class CompileMatchWideningTest {

    private static final String MODULE = """
            module demo

            data A = { v: Int }
            data B = { v: Int }
            data Both = A | B

            data OutA = Int
            data OutB = Int

            behavior pick : (x: Both) -> OutA | OutB constructs OutA, OutB
            let pick (x) =
                match x with
                    | A as a -> OutA { value = a.v }
                    | B as b -> OutB { value = b.v }
            """;

    @Test
    void divergentMatchCasesWidenToAUnion() {
        assertDoesNotThrow(() -> Compiler.compile(MODULE));
    }

    @Test
    void theWidenedMatchRunsAndPicksTheRightCase() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(MODULE), CompileMatchWideningTest.class.getClassLoader());
        Object b = Codecs.decoded(loader, "demo.Both", Map.of("type", "B", "v", 7L));

        Object pick = loader.loadClass("demo.Pick").getConstructor().newInstance();
        Object out = Codecs.apply(pick, b);

        // the B case produced an OutB { b: 7 }; OutB is a single-Int-field newtype, so it encodes bare
        assertEquals("demo.OutB", out.getClass().getName());
        assertEquals(7L, Codecs.encode(loader, "demo.OutB", out));
    }
}
