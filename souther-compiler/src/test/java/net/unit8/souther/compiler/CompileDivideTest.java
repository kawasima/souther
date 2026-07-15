package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Integer division yields Int | DivisionByZero, matched via a primitive arm (spec 18.2). */
class CompileDivideTest {

    private static final String MODULE = """
            module demo

            data Pair { a: Int  b: Int }
            data Outcome { q: Int  ok: Bool }

            behavior divideThem(p: Pair) -> Outcome constructs Outcome {
                match divide(p.a, p.b) {
                    case Int as q => Outcome { q: q, ok: true }
                    case DivisionByZero => Outcome { q: 0, ok: false }
                }
            }
            """;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Raw.ObjectValue divide(long a, long b) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo.Pair").getMethod("decoder").invoke(null);
        Object pair = d.decode(Raw.object(Map.of("a", Raw.integer(a), "b", Raw.integer(b))));
        Object outcome = ((Behavior<Object, Object>) loader.loadClass("demo.divideThem")
                .getConstructor().newInstance()).apply(pair);
        Encoder enc = (Encoder) loader.loadClass("demo.Outcome").getMethod("encoder").invoke(null);
        return (Raw.ObjectValue) enc.encode(outcome);
    }

    @Test
    void dividesWhenDivisorNonZero() throws Exception {
        Raw.ObjectValue out = divide(10, 2);
        assertEquals(Raw.integer(5), out.value().get("q"));
        assertEquals(Raw.bool(true), out.value().get("ok"));
    }

    @Test
    void takesDivisionByZeroArm() throws Exception {
        Raw.ObjectValue out = divide(10, 0);
        assertEquals(Raw.integer(0), out.value().get("q"));
        assertEquals(Raw.bool(false), out.value().get("ok"));
    }
}
