package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A newtype case of a sum uses adjacent tagging: its inner value sits under {@code "value"} next to
 * the {@code "type"} discriminator (spec 10.3, A.10) — {@code {"type": "管理職", "value": 3}} —
 * while a unit case is just its tag and a product case stays flat.
 */
class CompileNewtypeCaseTest {

    private static final String MODULE = """
            module demo

            data 一般社員
            data 管理職 = Int
            data 役職 = 一般社員 | 管理職
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    @Test
    void newtypeCaseUsesAdjacentValueTag() throws Exception {
        BytesClassLoader loader = loader();
        Object v = Codecs.decoded(loader, "demo.役職", Map.of("type", "管理職", "value", 3L));
        assertEquals("demo.管理職", v.getClass().getName());
        assertEquals(Map.of("type", "管理職", "value", 3L), Codecs.encode(loader, "demo.役職", v),
                "a newtype case's inner goes under `value`, adjacent to `type`");
    }

    @Test
    void unitCaseIsJustItsTag() throws Exception {
        BytesClassLoader loader = loader();
        Object v = Codecs.decoded(loader, "demo.役職", Map.of("type", "一般社員"));
        assertEquals("demo.一般社員", v.getClass().getName());
        assertEquals(Map.of("type", "一般社員"), Codecs.encode(loader, "demo.役職", v));
    }
}
