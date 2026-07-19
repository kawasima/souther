package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A helper {@code fn} may take a function-typed parameter {@code p: (A) -> B} and pass it on to a
 * combinator (spec §fn-declaration, {@code let 適用する (xs: List<Int>, p: (Int) -> Bool) = all(p, xs)}).
 * Because the function is only passed — never returned or stored — the call site is expanded inline,
 * so no runtime closure is built.
 */
class CompileFunctionParamTest {

    private static final String MODULE = """
            module demo

            import List ( all )

            data Order = { qtys: List<Int> }
            data Result = { ok: Bool }

            behavior check : (o: Order) -> Result
                constructs Result

            let check (o) = Result { ok = applyAll(o.qtys, positive) }

            let applyAll (xs: List<Int>, p: (Int) -> Bool) = all(p, xs)
            let positive (x: Int) = x > 0
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object decodeOrder(BytesClassLoader loader, List<Long> qtys) throws Exception {
        return Codecs.decoded(loader, "demo.Order", Map.of("qtys", qtys));
    }

    private Map<?, ?> run(BytesClassLoader loader, Object check, List<Long> qtys) throws Exception {
        Object r = Codecs.apply(check, decodeOrder(loader, qtys));
        return (Map<?, ?>) Codecs.encode(loader, "demo.Result", r);
    }

    @Test
    void functionTypedParamPassedToCombinator() throws Exception {
        BytesClassLoader loader = loader();
        Object check = loader.loadClass("demo.Check").getDeclaredConstructor().newInstance();

        assertEquals(Boolean.TRUE, run(loader, check, List.of(1L, 2L, 3L)).get("ok"));
        assertEquals(Boolean.FALSE, run(loader, check, List.of(1L, -2L, 3L)).get("ok"));
    }
}
