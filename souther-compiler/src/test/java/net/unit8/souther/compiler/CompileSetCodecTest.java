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

/** A Set<T> as a data field crosses the codec boundary ([#stdlib-set], ADR-0009): the derived
 *  decoder reads a JSON array and deduplicates it into a Set (via an invokedynamic List -> Set map),
 *  and the encoder writes the Set back as an array. */
class CompileSetCodecTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
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

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        // the array has a duplicate "a" — the Set decoder drops it
        Object in = ((Ok) inDec.decode(Map.of("tags", List.of("a", "b", "a", "c")), Path.ROOT)).value();
        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Run")
                .getConstructor().newInstance()).apply(in);

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        Map<?, ?> m = (Map<?, ?>) enc.encode(out);
        assertEquals(List.of("a", "b", "c"), m.get("tags"), "decode dedupes; encode writes an array");
        assertEquals(3L, m.get("n"), "the deduped set has three members");
        assertEquals(false, m.get("hasX"));
        assertEquals(List.of("a", "b", "c", "z"), m.get("more"));
    }
}
