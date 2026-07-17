package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** End-to-end test for multi-parameter behaviors (spec 12.1, AND inputs). */
class CompileMultiParamTest {

    private static final String MODULE = """
            module demo

            data A = Int
            data B = Int

            data Pair = {
                left: Int
                right: Int
            }

            behavior mkPair : (a: A, b: B) -> Pair constructs Pair

            let mkPair (a, b) = Pair { left: a.value, right: b.value }
            """;

    @Test
    void twoArgumentBehaviorUsesBothInputs() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        Object a = decode(loader, "A", 3L);
        Object b = decode(loader, "B", 7L);

        Object behavior = loader.loadClass("demo.MkPair").getConstructor().newInstance();
        Object out = behavior.getClass()
                .getMethod("apply", Object.class, Object.class)
                .invoke(behavior, a, b);

        Encoder enc = (Encoder) loader.loadClass("demo.Pair").getMethod("encoder").invoke(null);
        Map<?, ?> pair = (Map<?, ?>) enc.encode(out);
        assertEquals(3L, pair.get("left"));
        assertEquals(7L, pair.get("right"));
    }

    private Object decode(BytesClassLoader loader, String type, long n) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
        return ((Ok) d.decode(n, Path.ROOT)).value();
    }
}
