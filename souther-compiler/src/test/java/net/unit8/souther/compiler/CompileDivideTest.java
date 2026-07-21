package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Integer division yields Int | DivisionByZero, matched via a primitive case (spec 18.2). */
class CompileDivideTest {

    private static final String MODULE = """
            module demo

            import Int ( divide )

            data Pair = { a: Int, b: Int }
            data Outcome = { q: Int, ok: Bool }

            behavior divideThem : (p: Pair) -> Outcome constructs Outcome

            let divideThem (p) =
                match divide(p.a, p.b) with
                    | Int as q -> Outcome { q = q, ok = true }
                    | DivisionByZero -> Outcome { q = 0, ok = false }
            """;

    private Map<?, ?> divide(long a, long b) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object pair = Codecs.decoded(loader, "demo.Pair", Map.of("a", a, "b", b));
        Object outcome = Codecs.apply(
                loader.loadClass("demo.DivideThem" + "$Impl").getConstructor().newInstance(), pair);
        return (Map<?, ?>) Codecs.encode(loader, "demo.Outcome", outcome);
    }

    @Test
    void dividesWhenDivisorNonZero() throws Exception {
        Map<?, ?> out = divide(10, 2);
        assertEquals(5L, out.get("q"));
        assertEquals(true, out.get("ok"));
    }

    @Test
    void takesDivisionByZeroCase() throws Exception {
        Map<?, ?> out = divide(10, 0);
        assertEquals(0L, out.get("q"));
        assertEquals(false, out.get("ok"));
    }
}
