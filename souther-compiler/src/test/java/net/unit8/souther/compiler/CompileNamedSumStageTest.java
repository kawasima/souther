package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When a pipeline stage outputs a named sum, {@code >->} routes over that sum's leaf arms, not the
 * sum as one opaque arm (spec 8.3, 14.2). A downstream stage that accepts one arm consumes it and
 * the rest retire; before, the whole sum failed to match and the composition was wrongly E1701.
 */
class CompileNamedSumStageTest {

    private static final String MODULE = """
            module demo

            data A = { v: Int }
            data B = { v: Int }
            data AB = A | B
            data OutA = { a: Int }

            behavior classify : (a: A) -> AB constructs B
            let classify (a) = if a.v > 0 then a else B { v: 0 }

            behavior handleA : (a: A) -> OutA constructs OutA
            let handleA (a) = OutA { a: a.v }

            // classify outputs AB (= A | B); handleA accepts A, so A routes in and B retires.
            behavior pipe = classify >-> handleA
            """;

    @Test
    void aNamedSumStageOutputRoutesOverItsArms() {
        assertDoesNotThrow(() -> Compiler.compile(MODULE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theAcceptedArmRoutesIntoTheNextStage() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(MODULE), CompileNamedSumStageTest.class.getClassLoader());
        // A is an arm of AB, so it decodes from an object; v > 0 makes classify pass it through
        Decoder aDec = (Decoder) loader.loadClass("demo.A").getMethod("decoder").invoke(null);
        Object a = ((Ok) aDec.decode(java.util.Map.of("v", 5L), Path.ROOT)).value();

        Object pipe = loader.loadClass("demo.Pipe").getConstructor().newInstance();
        Object out = ((Behavior<Object, Object>) pipe).apply(a);

        // A routed into handleA, producing OutA { a: 5 }
        assertEquals("demo.OutA", out.getClass().getName());
    }
}
