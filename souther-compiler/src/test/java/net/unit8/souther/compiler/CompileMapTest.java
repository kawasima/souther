package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.DecodeFailure;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

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

                data Scores { byName: Map<String, Int> }
                """), getClass().getClassLoader());
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo.Scores").getMethod("decoder").invoke(null);

        Object ok = d.decode(Raw.object(Map.of("byName",
                Raw.object(Map.of("a", Raw.integer(1), "b", Raw.integer(2))))));
        assertTrue(!(ok instanceof DecodeFailure));

        Encoder enc = (Encoder) loader.loadClass("demo.Scores").getMethod("encoder").invoke(null);
        Raw.ObjectValue out = (Raw.ObjectValue) enc.encode(ok);
        Raw.ObjectValue byName = (Raw.ObjectValue) out.value().get("byName");
        assertEquals(Raw.integer(1), byName.value().get("a"));
        assertEquals(Raw.integer(2), byName.value().get("b"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getAndContainsKeyOnAMap() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data Scores { byName: Map<String, Int> }
                data Answer { found: Bool  value: Int }

                behavior lookupA(s: Scores) -> Answer constructs Answer {
                    match get(s.byName, "a") {
                        case Some as v => Answer { found: containsKey(s.byName, "a"), value: v }
                        case None => Answer { found: false, value: 0 }
                    }
                }
                """), getClass().getClassLoader());

        Decoder<?> d = (Decoder<?>) loader.loadClass("demo.Scores").getMethod("decoder").invoke(null);
        Object scores = d.decode(Raw.object(Map.of("byName",
                Raw.object(Map.of("a", Raw.integer(7))))));
        Object answer = ((Behavior<Object, Object>) loader.loadClass("demo.lookupA")
                .getConstructor().newInstance()).apply(scores);

        Encoder enc = (Encoder) loader.loadClass("demo.Answer").getMethod("encoder").invoke(null);
        Raw.ObjectValue out = (Raw.ObjectValue) enc.encode(answer);
        assertEquals(Raw.bool(true), out.value().get("found"));
        assertEquals(Raw.integer(7), out.value().get("value"));
    }
}
