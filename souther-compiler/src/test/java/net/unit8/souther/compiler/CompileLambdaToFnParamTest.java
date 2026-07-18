package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A lambda may be passed straight to a helper's function-typed parameter — {@code anyOf(xs, x -> x >
 * 0)} — not only a named function. The lambda is β-reduced at each application of the parameter
 * inside the helper body, exactly as a {@code let}-bound lambda is (spec 12.5). This is what lets the
 * prelude combinators, once they take a {@code p: ('a) -> Bool} parameter, still be called with an
 * inline block the way the built-ins were.
 */
class CompileLambdaToFnParamTest {

    private static final String MODULE = """
            module demo
            import List ( all )
            import Bool ( not )

            data Order = { qtys: List<Int> }
            data Result = { ok: Bool }

            behavior check : (o: Order) -> Result
                constructs Result

            let check (o) = Result { ok = anyOf(o.qtys, x -> x > 0) }
            let anyOf (xs: List<Int>, p: (Int) -> Bool) = not(all(y -> not(p(y)), xs))
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Map<?, ?> run(BytesClassLoader loader, Object check, List<Long> qtys) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo.Order").getMethod("decoder").invoke(null);
        Object order = ((Ok) d.decode(Map.of("qtys", qtys), Path.ROOT)).value();
        Object r = ((Behavior) check).apply(order);
        Encoder enc = (Encoder) loader.loadClass("demo.Result").getMethod("encoder").invoke(null);
        return (Map<?, ?>) enc.encode(r);
    }

    @Test
    void lambdaPassedToAFunctionTypedParameter() throws Exception {
        BytesClassLoader loader = loader();
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        assertEquals(Boolean.TRUE, run(loader, check, List.of(-1L, 2L)).get("ok"));
        assertEquals(Boolean.FALSE, run(loader, check, List.of(-1L, -2L)).get("ok"));
        assertEquals(Boolean.FALSE, run(loader, check, List.of()).get("ok"));
    }
}
