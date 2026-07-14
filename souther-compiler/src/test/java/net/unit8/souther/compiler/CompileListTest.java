package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.DecodeError;
import net.unit8.souther.runtime.DecodeFailure;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.NonEmptyList;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

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
        Decoder<?> decoder = (Decoder<?>) loader.loadClass("demo.Request").getMethod("decoder").invoke(null);

        Raw input = Raw.object(Map.of(
                "nums", Raw.list(List.of(Raw.integer(1), Raw.integer(2), Raw.integer(3))),
                "reasons", Raw.list(List.of(Raw.text("high"), Raw.text("late")))));
        Object request = decoder.decode(input);
        assertTrue(!(request instanceof DecodeFailure));
        Object count = loader.loadClass("demo.countReasons").getConstructor().newInstance();
        Object out = ((Behavior<Object, Object>) count).apply(request);

        Encoder enc = (Encoder) loader.loadClass("demo.Count").getMethod("encoder").invoke(null);
        assertEquals(Raw.integer(2), enc.encode(out));
    }

    @Test
    void listElementErrorsCarryIndexPaths() throws Exception {
        Decoder<?> decoder = (Decoder<?>) loader().loadClass("demo.Request")
                .getMethod("decoder").invoke(null);

        Raw input = Raw.object(Map.of(
                "nums", Raw.list(List.of(Raw.integer(1), Raw.text("bad"), Raw.integer(3))),
                "reasons", Raw.list(List.of())));
        Object bad = decoder.decode(input);
        assertTrue(bad instanceof DecodeFailure);
        DecodeError e = ((DecodeFailure) bad).errors().head();
        assertEquals("expected_int", e.code());
        // path is [nums, 1]
        assertEquals(new DecodeError.PathElement.Field("nums"), e.path().get(0));
        assertEquals(new DecodeError.PathElement.Index(1), e.path().get(1));
    }
}
