package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A helper's function-typed parameter may be applied directly: {@code let applyTo (x: Int, f: (Int)
 * -> Int) = f(x)} (spec §fn-declaration). The application {@code f(x)} inlines to the passed fn's
 * body at the call site, so no runtime closure is built.
 */
class CompileFunctionApplyTest {

    private static final String MODULE = """
            module demo

            data Order = { v: Int }
            data Result = { n: Int }

            behavior check : (o: Order) -> Result
                constructs Result

            let check (o) = Result { n = applyTo(o.v, inc) }

            let applyTo (x: Int, f: (Int) -> Int) = f(x)
            let inc (x: Int) = x + 1
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private long run(BytesClassLoader loader, Object check, long v) throws Exception {
        Object order = Codecs.decoded(loader, "demo.Order", Map.of("v", v));
        Object r = Codecs.apply(check, order);
        return (Long) ((Map<?, ?>) Codecs.encode(loader, "demo.Result", r)).get("n");
    }

    @Test
    void functionTypedParamAppliedDirectly() throws Exception {
        BytesClassLoader loader = loader();
        Object check = loader.loadClass("demo.Check" + "$Impl").getDeclaredConstructor().newInstance();

        assertEquals(6L, run(loader, check, 5L));
        assertEquals(1L, run(loader, check, 0L));
    }
}
