package net.unit8.souther.compiler;

import net.unit8.souther.runtime.DecodeFailure;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sum whose arms include a unit data (no fields) derives a decoder/encoder: the unit arm
 * decodes from the discriminated object and encodes back to {@code {"type": "<arm>"}}
 * (spec 8.3, 8.4, 10.3, 11.2). Previously the derived sum decoder rejected unit arms.
 */
class CompileUnitArmTest {

    private static final String MODULE = """
            module demo

            data Amount { value: Int }
            data High { threshold: Amount }
            data LowRole
            data Reason = High | LowRole
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Decoder<?> reasonDecoder(BytesClassLoader loader) throws Exception {
        return (Decoder<?>) loader.loadClass("demo.Reason").getMethod("decoder").invoke(null);
    }

    @Test
    void decodesAUnitArm() throws Exception {
        BytesClassLoader loader = loader();
        Object low = reasonDecoder(loader).decode(Raw.object(Map.of("type", Raw.text("LowRole"))));
        assertTrue(!(low instanceof DecodeFailure));
        assertEquals("demo.LowRole", low.getClass().getName());

        Object high = reasonDecoder(loader)
                .decode(Raw.object(Map.of("type", Raw.text("High"), "threshold", Raw.integer(5))));
        assertEquals("demo.High", high.getClass().getName());
    }

    @Test
    void encodesAUnitArmWithItsTag() throws Exception {
        BytesClassLoader loader = loader();
        Object low = reasonDecoder(loader).decode(Raw.object(Map.of("type", Raw.text("LowRole"))));

        Encoder enc = (Encoder) loader.loadClass("demo.Reason").getMethod("encoder").invoke(null);
        Raw.ObjectValue out = (Raw.ObjectValue) enc.encode(low);
        assertEquals(Raw.text("LowRole"), out.value().get("type"));
        assertEquals(1, out.value().size(), "a unit arm encodes to just its tag");
    }
}
