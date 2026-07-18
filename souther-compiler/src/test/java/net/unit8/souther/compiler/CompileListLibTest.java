package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The List standard library beyond map/filter/all/any (spec 18.4): the further Elm combinators
 *  derived from {@code fold}, plus the native {@code sort} primitive and String ordering. */
class CompileListLibTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void foldDerivedCombinators() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List { reverse, sum, product, member, isEmpty }

                data In = { ns: List<Int> }
                data Out = {
                    reversed: List<Int>
                    , total: Int
                    , prod: Int
                    , hasTwo: Bool
                    , none: Bool
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    reversed = reverse(i.ns),
                    total = sum(i.ns),
                    prod = product(i.ns),
                    hasTwo = member(i.ns, 2),
                    none = isEmpty(i.ns)
                }
                """), getClass().getClassLoader());

        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Run").getConstructor().newInstance())
                .apply(decodeIn(loader, List.of(1L, 2L, 3L)));

        Map<?, ?> m = encode(loader, out);
        assertEquals(List.of(3L, 2L, 1L), m.get("reversed"));
        assertEquals(6L, m.get("total"));
        assertEquals(6L, m.get("prod"));
        assertEquals(true, m.get("hasTwo"));
        assertEquals(false, m.get("none"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sortOfAnEmptyListLiteralIsAllowedAndYieldsEmpty() throws Exception {
        // The empty-list literal types as `List<Nothing>`; sorting it is valid (it sorts to itself),
        // so the ordered-element guard must let the bottom element through.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List { sort }

                data In = { ns: List<Int> }
                data Out = { xs: List<Int> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { xs = sort([]) }
                """), getClass().getClassLoader());

        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Run").getConstructor().newInstance())
                .apply(decodeIn(loader, List.of()));
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        assertEquals(List.of(), ((Map<?, ?>) enc.encode(out)).get("xs"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void flattenAndSortAndStringOrder() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import List { sort, concat }

                data In = { tags: List<String> }
                data Out = {
                    sorted: List<String>
                    , joinedNested: List<Int>
                    , ascending: Bool
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    sorted = sort(i.tags),
                    joinedNested = concat([[1, 2], [3]]),
                    ascending = "alpha" < "beta"
                }
                """), getClass().getClassLoader());

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) inDec.decode(Map.of("tags", List.of("gamma", "alpha", "beta")), Path.ROOT)).value();
        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Run").getConstructor().newInstance())
                .apply(in);

        Map<?, ?> m = encode(loader, out);
        assertEquals(List.of("alpha", "beta", "gamma"), m.get("sorted"));
        assertEquals(List.of(1L, 2L, 3L), m.get("joinedNested"));
        assertEquals(true, m.get("ascending"));
    }

    private static Object decodeIn(BytesClassLoader loader, List<Long> ns) throws Exception {
        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        return ((Ok) inDec.decode(Map.of("ns", ns), Path.ROOT)).value();
    }

    private static Map<?, ?> encode(BytesClassLoader loader, Object out) throws Exception {
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        return (Map<?, ?>) enc.encode(out);
    }
}
