package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end test for multi-parameter behaviors (spec 12.1, AND inputs). */
class CompileMultiParamTest {

    private static final String MODULE = """
            module demo

            data A { value: Int  decoder from Int as n { A { value: n } } }
            data B { value: Int  decoder from Int as n { B { value: n } } }

            data Pair {
                left: Int
                right: Int
                encoder self { Object { "left": Int(self.left), "right": Int(self.right) } }
            }

            behavior mkPair(a: A, b: B) -> Pair constructs Pair {
                Pair { left: a.value, right: b.value }
            }
            """;

    @Test
    void twoArgumentBehaviorUsesBothInputs() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        Object a = decode(loader, "A", 3);
        Object b = decode(loader, "B", 7);

        Object behavior = loader.loadClass("demo.mkPair").getConstructor().newInstance();
        Result<?, ?> r = (Result<?, ?>) behavior.getClass()
                .getMethod("apply", Object.class, Object.class)
                .invoke(behavior, a, b);
        assertTrue(r.isOk());

        Encoder enc = (Encoder) loader.loadClass("demo.Pair").getMethod("encoder").invoke(null);
        Raw.ObjectValue pair = (Raw.ObjectValue) enc.encode(((Result.Ok<?, ?>) r).value());
        assertEquals(Raw.integer(3), pair.value().get("left"));
        assertEquals(Raw.integer(7), pair.value().get("right"));
    }

    private Object decode(BytesClassLoader loader, String type, long n) throws Exception {
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
        return ((Result.Ok<?, ?>) d.decode(Raw.integer(n))).value();
    }
}
