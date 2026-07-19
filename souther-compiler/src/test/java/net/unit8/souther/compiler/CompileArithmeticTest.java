package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** End-to-end test for arithmetic operators with correct precedence (spec 18.2). */
class CompileArithmeticTest {

    private static final String MODULE = """
            module demo

            data In = Int
            data Out = Int

            // * binds tighter than +, so this is (value * 2) + 10
            behavior compute : (x: In) -> Out constructs Out

            let compute (x) = Out { value = x.value * 2 + 10 }
            """;

    @Test
    void evaluatesArithmeticWithPrecedence() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", 5L);

        Object compute = loader.loadClass("demo.Compute").getConstructor().newInstance();
        // apply returns the output case value directly (no Result wrapper)
        Object out = Codecs.apply(compute, in);

        // 5 * 2 + 10 = 20
        assertEquals(20L, Codecs.encode(loader, "demo.Out", out));
    }
}
