package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Map<String, T> as a field type with get/containsKey (spec 7.2, 18.5). */
class CompileMapTest {

    @Test
    void mapFieldRoundTrips() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data Scores = { byName: Map<String, Int> }
                """), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.Scores").getMethod("decoder").invoke(null);

        Result r = d.decode(Map.of("byName", Map.of("a", 1L, "b", 2L)), Path.ROOT);
        assertTrue(r instanceof Ok);

        Encoder enc = (Encoder) loader.loadClass("demo.Scores").getMethod("encoder").invoke(null);
        Map<?, ?> out = (Map<?, ?>) enc.encode(((Ok) r).value());
        Map<?, ?> byName = (Map<?, ?>) out.get("byName");
        assertEquals(1L, byName.get("a"));
        assertEquals(2L, byName.get("b"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getAndContainsKeyOnAMap() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Map { get, containsKey }

                data Scores = { byName: Map<String, Int> }
                data Answer = { found: Bool, value: Int }

                behavior lookupA : (s: Scores) -> Answer constructs Answer

                let lookupA (s) =
                    match get(s.byName, "a") with
                        | Some as v -> Answer { found = containsKey(s.byName, "a"), value = v }
                        | None -> Answer { found = false, value = 0 }
                """), getClass().getClassLoader());

        Decoder d = (Decoder) loader.loadClass("demo.Scores").getMethod("decoder").invoke(null);
        Result r = d.decode(Map.of("byName", Map.of("a", 7L)), Path.ROOT);
        assertTrue(r instanceof Ok);
        Object scores = ((Ok) r).value();
        Object answer = ((Behavior<Object, Object>) loader.loadClass("demo.LookupA")
                .getConstructor().newInstance()).apply(scores);

        Encoder enc = (Encoder) loader.loadClass("demo.Answer").getMethod("encoder").invoke(null);
        Map<?, ?> out = (Map<?, ?>) enc.encode(answer);
        assertEquals(true, out.get("found"));
        assertEquals(7L, out.get("value"));
    }
}
