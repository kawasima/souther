package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Integer division yields Int | DivisionByZero, matched via a primitive arm (spec 18.2). */
class CompileDivideTest {

    private static final String MODULE = """
            module demo

            data Pair = { a: Int  b: Int }
            data Outcome = { q: Int  ok: Bool }

            behavior divideThem = (p: Pair) -> Outcome constructs Outcome

            fn divideThem (p) =
                match divide(p.a, p.b) {
                    case Int as q => Outcome { q: q, ok: true }
                    case DivisionByZero => Outcome { q: 0, ok: false }
                }
            """;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<?, ?> divide(long a, long b) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.Pair").getMethod("decoder").invoke(null);
        Object pair = ((Ok) d.decode(Map.of("a", a, "b", b), Path.ROOT)).value();
        Object outcome = ((Behavior<Object, Object>) loader.loadClass("demo.divideThem")
                .getConstructor().newInstance()).apply(pair);
        Encoder enc = (Encoder) loader.loadClass("demo.Outcome").getMethod("encoder").invoke(null);
        return (Map<?, ?>) enc.encode(outcome);
    }

    @Test
    void dividesWhenDivisorNonZero() throws Exception {
        Map<?, ?> out = divide(10, 2);
        assertEquals(5L, out.get("q"));
        assertEquals(true, out.get("ok"));
    }

    @Test
    void takesDivisionByZeroArm() throws Exception {
        Map<?, ?> out = divide(10, 0);
        assertEquals(0L, out.get("q"));
        assertEquals(false, out.get("ok"));
    }
}
