package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sum whose cases include a unit data (no fields) derives a decoder/encoder: the unit case
 * decodes from the discriminated object and encodes back to {@code {"type": "<case>"}}
 * (spec 8.3, 8.4, 10.3, 11.2). Previously the derived sum decoder rejected unit cases.
 */
class CompileUnitCaseTest {

    private static final String MODULE = """
            module demo

            data Amount = Int
            data High = { threshold: Amount }
            data LowRole
            data Reason = High | LowRole
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Decoder reasonDecoder(BytesClassLoader loader) throws Exception {
        return (Decoder) loader.loadClass("demo.Reason").getMethod("decoder").invoke(null);
    }

    @Test
    void decodesAUnitCase() throws Exception {
        BytesClassLoader loader = loader();
        Result low = reasonDecoder(loader).decode(Map.of("type", "LowRole"), Path.ROOT);
        assertTrue(low instanceof Ok);
        assertEquals("demo.LowRole", ((Ok) low).value().getClass().getName());

        Result high = reasonDecoder(loader)
                .decode(Map.of("type", "High", "threshold", 5L), Path.ROOT);
        assertTrue(high instanceof Ok);
        assertEquals("demo.High", ((Ok) high).value().getClass().getName());
    }

    @Test
    void encodesAUnitCaseWithItsTag() throws Exception {
        BytesClassLoader loader = loader();
        Result low = reasonDecoder(loader).decode(Map.of("type", "LowRole"), Path.ROOT);

        Encoder enc = (Encoder) loader.loadClass("demo.Reason").getMethod("encoder").invoke(null);
        Map<?, ?> out = (Map<?, ?>) enc.encode(((Ok) low).value());
        assertEquals("LowRole", out.get("type"));
        assertEquals(1, out.size(), "a unit case encodes to just its tag");
    }
}
