package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Map building standard library ([#stdlib-map]): empty / singleton / insert / remove /
 *  isEmpty / size, over the immutable string-keyed Map. */
class CompileMapLibTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void buildAndQueryAMap() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Map ( singleton, insert, remove, isEmpty, size, containsKey )

                data In = { base: Map<String, Int> }
                data Out = {
                    built: Map<String, Int>
                    , one: Map<String, Int>
                    , afterRemove: Map<String, Int>
                    , n: Int
                    , baseEmpty: Bool
                    , builtEmpty: Bool
                    , hasB: Bool
                    , emptyMap: Map<String, Int>
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    let built = insert("b", 2, insert("a", 1, Map.empty()))
                    Out {
                        built = built,
                        one = singleton("x", 9),
                        afterRemove = remove("a", i.base),
                        n = size(built),
                        baseEmpty = isEmpty(i.base),
                        builtEmpty = isEmpty(built),
                        hasB = containsKey("b", built),
                        emptyMap = Map.empty()
                    }
                }
                """), getClass().getClassLoader());

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) inDec.decode(Map.of("base", Map.of("a", 1L, "c", 3L)), Path.ROOT)).value();
        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Run")
                .getConstructor().newInstance()).apply(in);

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        Map<?, ?> m = (Map<?, ?>) enc.encode(out);
        assertEquals(Map.of("a", 1L, "b", 2L), m.get("built"));
        assertEquals(Map.of("x", 9L), m.get("one"));
        assertEquals(Map.of("c", 3L), m.get("afterRemove"), "remove drops only the named key");
        assertEquals(2L, m.get("n"));
        assertEquals(false, m.get("baseEmpty"));
        assertEquals(false, m.get("builtEmpty"));
        assertEquals(true, m.get("hasB"));
        assertEquals(Map.of(), m.get("emptyMap"), "Map.empty() is an empty map");
    }
}
