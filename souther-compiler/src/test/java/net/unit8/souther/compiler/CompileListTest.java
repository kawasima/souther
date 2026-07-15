package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end test for {@code List<T>} fields, list decoders, and the {@code size} builtin (spec 7.2, 18.4). */
class CompileListTest {

    private static final String MODULE = """
            module demo

            data Reason { code: String }

            data Request {
                nums: List<Int>
                reasons: List<Reason>
            }

            data Count { value: Int }

            behavior countReasons(r: Request) -> Count constructs Count {
                Count { value: size(r.reasons) }
            }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    @Test
    @SuppressWarnings("unchecked")
    void decodesListsAndCountsThem() throws Exception {
        BytesClassLoader loader = loader();
        Decoder decoder = (Decoder) loader.loadClass("demo.Request").getMethod("decoder").invoke(null);

        Result r = decoder.decode(Map.of(
                "nums", List.of(1L, 2L, 3L),
                "reasons", List.of("high", "late")), Path.ROOT);
        assertTrue(r instanceof Ok);
        Object request = ((Ok) r).value();
        Object count = loader.loadClass("demo.countReasons").getConstructor().newInstance();
        Object out = ((Behavior<Object, Object>) count).apply(request);

        Encoder enc = (Encoder) loader.loadClass("demo.Count").getMethod("encoder").invoke(null);
        assertEquals(2L, enc.encode(out));
    }

    @Test
    void listElementErrorsCarryIndexPaths() throws Exception {
        Decoder decoder = (Decoder) loader().loadClass("demo.Request")
                .getMethod("decoder").invoke(null);

        Result r = decoder.decode(Map.of(
                "nums", List.of(1L, "bad", 3L),
                "reasons", List.of()), Path.ROOT);
        assertTrue(r instanceof Err);
        Issue e = ((Err) r).issues().asList().get(0);
        assertEquals("type_mismatch", e.code());
        // path is [nums, 1]
        assertEquals("/nums/1", e.path().toJsonPointer());
    }
}
