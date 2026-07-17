package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code match} whose arms yield different data types widens to the union of those types, the
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
                    | A as a -> OutA { value: a.v }
                    | B as b -> OutB { value: b.v }
            """;

    @Test
    void divergentMatchArmsWidenToAUnion() {
        assertDoesNotThrow(() -> Compiler.compile(MODULE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theWidenedMatchRunsAndPicksTheRightArm() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(MODULE), CompileMatchWideningTest.class.getClassLoader());
        Decoder bothDec = (Decoder) loader.loadClass("demo.Both").getMethod("decoder").invoke(null);
        Object b = ((Ok) bothDec.decode(Map.of("type", "B", "v", 7L), Path.ROOT)).value();

        Object pick = loader.loadClass("demo.Pick").getConstructor().newInstance();
        Object out = ((Behavior<Object, Object>) pick).apply(b);

        // the B arm produced an OutB { b: 7 }; OutB is a single-Int-field newtype, so it encodes bare
        assertEquals("demo.OutB", out.getClass().getName());
        Encoder enc = (Encoder) loader.loadClass("demo.OutB").getMethod("encoder").invoke(null);
        assertEquals(7L, enc.encode(out));
    }
}
