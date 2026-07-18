package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A value of a case type is a value of its sum (spec 8.3): it can be assigned to a sum-typed
 * field and passed to a sum-typed parameter, not only returned. The functional-language norm.
 */
class CompileCaseWideningTest {

    private static final String MODULE = """
            module demo

            data A = { x: Int, tag: Int }
            data B = { y: Int }
            data AB = A | B
            data Wrap = { it: AB }

            behavior makeWrap : (a: A) -> Wrap constructs Wrap

            let makeWrap (a) = Wrap { it = a }
            """;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void anCaseAssignsToASumField() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder ad = (Decoder) loader.loadClass("demo.A").getMethod("decoder").invoke(null);
        Object a = ((Ok) ad.decode(Map.of("x", 5L, "tag", 1L), Path.ROOT)).value();

        Object wrap = ((Behavior<Object, Object>) loader.loadClass("demo.MakeWrap")
                .getConstructor().newInstance()).apply(a);

        Encoder enc = (Encoder) loader.loadClass("demo.Wrap").getMethod("encoder").invoke(null);
        Map<?, ?> out = (Map<?, ?>) enc.encode(wrap);
        Map<?, ?> it = (Map<?, ?>) out.get("it");
        assertEquals("A", it.get("type"), "the case is tagged as its sum's case");
        assertEquals(5L, it.get("x"));
    }

    @Test
    void anCasePassesToASumParameter() {
        // store expects the sum AB; use passes an A (a case) — must type-check
        String src = """
                module demo
                data A = { x: Int }
                data B = { y: Int }
                data AB = A | B
                data Stored = { it: AB }
                behavior store : (ab: AB) -> Stored
                behavior use : (a: A) -> Stored requires store
                let use (a, store) = store(a)
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }
}
