package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A helper's function-typed parameter may be applied directly: {@code fn applyTo (x: Int, f: (Int)
 * -> Int) = f(x)} (spec §fn-declaration). The application {@code f(x)} inlines to the passed fn's
 * body at the call site, so no runtime closure is built.
 */
class CompileFunctionApplyTest {

    private static final String MODULE = """
            module demo

            data Order = { v: Int }
            data Result = { n: Int }

            behavior check = (o: Order) -> Result
                constructs Result

            fn check (o) = Result { n: applyTo(o.v, inc) }

            fn applyTo (x: Int, f: (Int) -> Int) = f(x)
            fn inc (x: Int) = x + 1
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private long run(BytesClassLoader loader, Object check, long v) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo.Order").getMethod("decoder").invoke(null);
        Object order = ((Ok) d.decode(Map.of("v", v), Path.ROOT)).value();
        Object r = ((Behavior) check).apply(order);
        Encoder enc = (Encoder) loader.loadClass("demo.Result").getMethod("encoder").invoke(null);
        return (Long) ((Map<?, ?>) enc.encode(r)).get("n");
    }

    @Test
    void functionTypedParamAppliedDirectly() throws Exception {
        BytesClassLoader loader = loader();
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        assertEquals(6L, run(loader, check, 5L));
        assertEquals(1L, run(loader, check, 0L));
    }
}
