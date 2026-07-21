package net.unit8.souther.compiler;

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
    void anCaseAssignsToASumField() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object a = Codecs.decoded(loader, "demo.A", Map.of("x", 5L, "tag", 1L));

        Object wrap = Codecs.apply(
                loader.loadClass("demo.MakeWrap" + "$Impl").getConstructor().newInstance(), a);

        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Wrap", wrap);
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
