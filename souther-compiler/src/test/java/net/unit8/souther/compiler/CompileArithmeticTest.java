package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** End-to-end test for arithmetic operators with correct precedence (spec 18.2). */
class CompileArithmeticTest {

    private static final String MODULE = """
            module demo

            data In  { value: Int }
            data Out { value: Int }

            // * binds tighter than +, so this is (value * 2) + 10
            behavior compute(x: In) -> Out constructs Out {
                Out { value: x.value * 2 + 10 }
            }
            """;

    @Test
    @SuppressWarnings("unchecked")
    void evaluatesArithmeticWithPrecedence() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        Decoder<?> inDecoder = (Decoder<?>) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Result.Ok<?, ?>) inDecoder.decode(Raw.integer(5))).value();

        Object compute = loader.loadClass("demo.compute").getConstructor().newInstance();
        // apply returns the output arm value directly (no Result wrapper)
        Object out = ((Behavior<Object, Object>) compute).apply(in);

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        // 5 * 2 + 10 = 20
        assertEquals(Raw.integer(20), enc.encode(out));
    }
}
