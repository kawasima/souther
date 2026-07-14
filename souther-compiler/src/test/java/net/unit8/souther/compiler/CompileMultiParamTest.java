package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** End-to-end test for multi-parameter behaviors (spec 12.1, AND inputs). */
class CompileMultiParamTest {

    private static final String MODULE = """
            module demo

            data A { value: Int }
            data B { value: Int }

            data Pair {
                left: Int
                right: Int
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
        Object out = behavior.getClass()
                .getMethod("apply", Object.class, Object.class)
                .invoke(behavior, a, b);

        Encoder enc = (Encoder) loader.loadClass("demo.Pair").getMethod("encoder").invoke(null);
        Raw.ObjectValue pair = (Raw.ObjectValue) enc.encode(out);
        assertEquals(Raw.integer(3), pair.value().get("left"));
        assertEquals(Raw.integer(7), pair.value().get("right"));
    }

    private Object decode(BytesClassLoader loader, String type, long n) throws Exception {
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
        return ((Result.Ok<?, ?>) d.decode(Raw.integer(n))).value();
    }
}
