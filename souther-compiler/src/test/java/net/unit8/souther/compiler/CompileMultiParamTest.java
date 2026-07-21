package net.unit8.souther.compiler;

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
                , right: Int
            }

            behavior mkPair : (a: A, b: B) -> Pair constructs Pair

            let mkPair (a, b) = Pair { left = a.value, right = b.value }
            """;

    @Test
    void twoArgumentBehaviorUsesBothInputs() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        Object a = decode(loader, "A", 3L);
        Object b = decode(loader, "B", 7L);

        Object behavior = loader.loadClass("demo.MkPair" + "$Impl").getConstructor().newInstance();
        Object out = behavior.getClass()
                .getMethod("apply", Object.class, Object.class)
                .invoke(behavior, a, b);

        Map<?, ?> pair = (Map<?, ?>) Codecs.encode(loader, "demo.Pair", out);
        assertEquals(3L, pair.get("left"));
        assertEquals(7L, pair.get("right"));
    }

    private Object decode(BytesClassLoader loader, String type, long n) throws Exception {
        return Codecs.decoded(loader, "demo." + type, n);
    }
}
