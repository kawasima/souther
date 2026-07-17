package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A newtype arm of a sum uses adjacent tagging: its inner value sits under {@code "value"} next to
 * the {@code "type"} discriminator (spec 10.3, A.10) — {@code {"type": "管理職", "value": 3}} —
 * while a unit arm is just its tag and a product arm stays flat.
 */
class CompileNewtypeArmTest {

    private static final String MODULE = """
            module demo

            data 一般社員
            data 管理職 = Int
            data 役職 = 一般社員 | 管理職
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Decoder dec(BytesClassLoader loader) throws Exception {
        return (Decoder) loader.loadClass("demo.役職").getMethod("decoder").invoke(null);
    }

    private Encoder enc(BytesClassLoader loader) throws Exception {
        return (Encoder) loader.loadClass("demo.役職").getMethod("encoder").invoke(null);
    }

    @Test
    void newtypeArmUsesAdjacentValueTag() throws Exception {
        BytesClassLoader loader = loader();
        Object v = ((Ok) dec(loader).decode(Map.of("type", "管理職", "value", 3L), Path.ROOT)).value();
        assertEquals("demo.管理職", v.getClass().getName());
        assertEquals(Map.of("type", "管理職", "value", 3L), enc(loader).encode(v),
                "a newtype arm's inner goes under `value`, adjacent to `type`");
    }

    @Test
    void unitArmIsJustItsTag() throws Exception {
        BytesClassLoader loader = loader();
        Object v = ((Ok) dec(loader).decode(Map.of("type", "一般社員"), Path.ROOT)).value();
        assertEquals("demo.一般社員", v.getClass().getName());
        assertEquals(Map.of("type", "一般社員"), enc(loader).encode(v));
    }
}
