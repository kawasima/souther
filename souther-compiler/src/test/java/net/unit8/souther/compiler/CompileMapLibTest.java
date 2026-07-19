package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Map building standard library ([#stdlib-map]): empty / singleton / insert / remove /
 *  isEmpty / size, over the immutable string-keyed Map. */
class CompileMapLibTest {

    @Test
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

        Object in = Codecs.decoded(loader, "demo.In", Map.of("base", Map.of("a", 1L, "c", 3L)));
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals(Map.of("a", 1L, "b", 2L), m.get("built"));
        assertEquals(Map.of("x", 9L), m.get("one"));
        assertEquals(Map.of("c", 3L), m.get("afterRemove"), "remove drops only the named key");
        assertEquals(2L, m.get("n"));
        assertEquals(false, m.get("baseEmpty"));
        assertEquals(false, m.get("builtEmpty"));
        assertEquals(true, m.get("hasB"));
        assertEquals(Map.of(), m.get("emptyMap"), "Map.empty() is an empty map");
    }

    /** The Map higher-order operations ([#stdlib-map]): fold sums the values, map keeps the keys and
     *  transforms the values, update rewrites one present key through a {@code value -> value} step
     *  (an absent key is a no-op — insert / remove cover the other two cases). */
    @Test
    void foldMapUpdateOverAMap() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Map ( fold, map, update )

                data In = { stock: Map<String, Int> }
                data Out = {
                    total: Int
                    , doubled: Map<String, Int>
                    , flagged: Map<String, Bool>
                    , afterIssue: Map<String, Int>
                    , afterAbsent: Map<String, Int>
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    Out {
                        total = fold((acc, k, v) -> acc + v, 0, i.stock),
                        doubled = map((k, v) -> v * 2, i.stock),
                        flagged = map((k, v) -> v > 15, i.stock),
                        afterIssue = update("a", (v) -> v - 1, i.stock),
                        afterAbsent = update("z", (v) -> v - 1, i.stock)
                    }
                }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of("stock", Map.of("a", 10L, "b", 20L)));
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals(30L, m.get("total"), "fold sums every value");
        assertEquals(Map.of("a", 20L, "b", 40L), m.get("doubled"), "map keeps keys, doubles values");
        assertEquals(Map.of("a", false, "b", true), m.get("flagged"),
                "map changes the value type Int -> Bool, keeping the keys");
        assertEquals(Map.of("a", 9L, "b", 20L), m.get("afterIssue"), "update rewrites the present key");
        assertEquals(Map.of("a", 10L, "b", 20L), m.get("afterAbsent"),
                "update leaves the map unchanged when the key is absent");
    }

    /** fold / map / update over a statically-typed map that is empty at runtime: fold returns the
     *  seed, map and update return an empty map — no step runs. (A bare {@code Map.empty()} has no
     *  value type for a step to consume, so the meaningful empty case is a typed map, empty at run
     *  time.) */
    @Test
    void foldMapUpdateOverTheEmptyMap() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Map ( fold, map, update )

                data In = { stock: Map<String, Int> }
                data Out = {
                    total: Int
                    , doubled: Map<String, Int>
                    , updated: Map<String, Int>
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    Out {
                        total = fold((acc, k, v) -> acc + v, 0, i.stock),
                        doubled = map((k, v) -> v * 2, i.stock),
                        updated = update("a", (v) -> v - 1, i.stock)
                    }
                }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of("stock", Map.of()));
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals(0L, m.get("total"), "fold over an empty map is the seed");
        assertEquals(Map.of(), m.get("doubled"), "map over an empty map is empty");
        assertEquals(Map.of(), m.get("updated"), "update on an absent key of an empty map is empty");
    }
}
