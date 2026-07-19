package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A Set<T> as a data field crosses the codec boundary ([#stdlib-set], ADR-0009): the derived
 *  decoder reads a JSON array and deduplicates it into a Set (via an invokedynamic List -> Set map),
 *  and the encoder writes the Set back as an array. */
class CompileSetCodecTest {

    @Test
    void setFieldDedupesOnDecodeAndRoundTrips() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Set ( insert, size, contains )

                data In = { tags: Set<String> }
                data Out = {
                    tags: Set<String>
                    , n: Int
                    , hasX: Bool
                    , more: Set<String>
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    tags = i.tags,
                    n = size(i.tags),
                    hasX = contains("x", i.tags),
                    more = insert("z", i.tags)
                }
                """), getClass().getClassLoader());

        // the array has a duplicate "a" — the Set decoder drops it
        Object in = Codecs.decoded(loader, "demo.In", Map.of("tags", List.of("a", "b", "a", "c")));
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        // decode dedupes; encode writes an array in a deterministic hash order (not first-seen).
        assertEquals(Set.of("a", "b", "c"), Set.copyOf((List<?>) m.get("tags")));
        assertEquals(3L, m.get("n"), "the deduped set has three members");
        assertEquals(false, m.get("hasX"));
        assertEquals(Set.of("a", "b", "c", "z"), Set.copyOf((List<?>) m.get("more")));
    }
}
