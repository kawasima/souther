package net.unit8.souther.compiler;

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
        Object order = Codecs.decoded(loader, "demo.Order", Map.of("qtys", qtys));
        Object r = Codecs.apply(check, order);
        return (Map<?, ?>) Codecs.encode(loader, "demo.Result", r);
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
