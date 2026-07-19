package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Spec 16.5: a block {@code { ... }} is an expression, usable anywhere an expression can go — such
 * as an {@code if} branch. A bare {@code &#123;} is unambiguously a block; a record literal is
 * prefixed by a type name (12.4).
 */
class CompileBlockExprTest {

    private static final String MODULE = """
            module demo

            data N = Int

            behavior f : (n: N) -> N constructs N
            let f (n) =
                if n.value > 0
                    then { let doubled = n.value + n.value  N { value = doubled } }
                    else n
            """;

    private long apply(BytesClassLoader loader, long in) throws Exception {
        Object n = Codecs.decoded(loader, "demo.N", in);
        Object f = loader.loadClass("demo.F").getConstructor().newInstance();
        Object r = Codecs.apply(f, n);
        return (long) Codecs.encode(loader, "demo.N", r);
    }

    @Test
    void aBlockIsAnExpressionInAnIfBranch() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        assertEquals(10L, apply(loader, 5));   // 5 > 0: block binds doubled = 10
        assertEquals(-1L, apply(loader, -1));  // else branch returns n unchanged
    }
}
