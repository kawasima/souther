package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

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
        Result<?> r = Codecs.decode(loader, "demo.Scores", Map.of("byName", Map.of("a", 1L, "b", 2L)));
        assertTrue(r instanceof Ok);

        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Scores", ((Ok<?>) r).value());
        Map<?, ?> byName = (Map<?, ?>) out.get("byName");
        assertEquals(1L, byName.get("a"));
        assertEquals(2L, byName.get("b"));
    }

    @Test
    void getAndContainsKeyOnAMap() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Map ( get, containsKey )

                data Scores = { byName: Map<String, Int> }
                data Answer = { found: Bool, value: Int }

                behavior lookupA : (s: Scores) -> Answer constructs Answer

                let lookupA (s) =
                    match get("a", s.byName) with
                        | Some v -> Answer { found = containsKey("a", s.byName), value = v }
                        | None -> Answer { found = false, value = 0 }
                """), getClass().getClassLoader());

        Result<?> r = Codecs.decode(loader, "demo.Scores", Map.of("byName", Map.of("a", 7L)));
        assertTrue(r instanceof Ok);
        Object scores = ((Ok<?>) r).value();
        Object behavior = loader.loadClass("demo.LookupA").getConstructor().newInstance();
        Object answer = Codecs.apply(behavior, scores);

        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Answer", answer);
        assertEquals(true, out.get("found"));
        assertEquals(7L, out.get("value"));
    }
}
