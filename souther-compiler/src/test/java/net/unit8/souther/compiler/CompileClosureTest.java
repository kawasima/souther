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
 * A lambda chosen at runtime cannot be expanded inline: {@code let f = if c then λ1 else λ2} binds
 * {@code f} to an {@code if}, not a block, so each branch becomes a first-class function value and
 * the application {@code f(x)} dispatches through {@link net.unit8.souther.runtime.Fn}. The lambda's
 * parameter type is inferred from the argument at the application site.
 */
class CompileClosureTest {

    private static final String MODULE = """
            module demo

            data Order = { v: Int  spring: Bool }
            data Result = { n: Int }

            behavior check = (o: Order) -> Result
                constructs Result

            fn check (o) = {
                let f = if o.spring then (x) -> x + 100 else (x) -> x + 1
                Result { n: f(o.v) }
            }
            """;

    private long run(BytesClassLoader loader, Object check, long v, boolean spring) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo.Order").getMethod("decoder").invoke(null);
        Object order = ((Ok) d.decode(Map.of("v", v, "spring", spring), Path.ROOT)).value();
        Object r = ((Behavior) check).apply(order);
        Encoder enc = (Encoder) loader.loadClass("demo.Result").getMethod("encoder").invoke(null);
        return (Long) ((Map<?, ?>) enc.encode(r)).get("n");
    }

    @Test
    void aLambdaChosenAtRuntimeDispatchesThroughFn() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        assertEquals(110L, run(loader, check, 10L, true));
        assertEquals(11L, run(loader, check, 10L, false));
    }

    // both branches capture the enclosing `o`, so the Fn class holds it in a field
    private static final String CAPTURING = """
            module demo

            data Order = { v: Int  spring: Bool }
            data Result = { n: Int }

            behavior check = (o: Order) -> Result
                constructs Result

            fn check (o) = {
                let f = if o.spring then (x) -> x + o.v else (x) -> x - o.v
                Result { n: f(100) }
            }
            """;

    @Test
    void aClosureCapturesAFreeVariable() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(CAPTURING), getClass().getClassLoader());
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        assertEquals(110L, run(loader, check, 10L, true));    // 100 + 10
        assertEquals(90L, run(loader, check, 10L, false));    // 100 - 10
    }
}
