package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field marked {@code T?} is optional (spec 7.4): it decodes to {@code Some}/{@code None}
 * and round-trips. An absent (or null) key decodes to {@code None} without failing.
 */
class CompileOptionalFieldTest {

    private static final String MODULE = """
            module demo

            data Id = String
            data Trip = { id: Id  approver: Id? }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Decoder decoder(BytesClassLoader loader) throws Exception {
        return (Decoder) loader.loadClass("demo.Trip").getMethod("decoder").invoke(null);
    }

    private Encoder encoder(BytesClassLoader loader) throws Exception {
        return (Encoder) loader.loadClass("demo.Trip").getMethod("encoder").invoke(null);
    }

    @Test
    void presentOptionalRoundTrips() throws Exception {
        BytesClassLoader loader = loader();
        Result r = decoder(loader).decode(new HashMap<>(Map.of(
                "id", "t-1", "approver", "e-9")), Path.ROOT);
        assertTrue(r instanceof Ok);

        Map<?, ?> out = (Map<?, ?>) encoder(loader).encode(((Ok) r).value());
        assertEquals("e-9", out.get("approver"));
    }

    @Test
    void absentOptionalDecodesToNone() throws Exception {
        BytesClassLoader loader = loader();
        Result r = decoder(loader).decode(Map.of("id", "t-1"), Path.ROOT);
        assertTrue(r instanceof Ok, "an absent optional key is None, not a failure");

        Map<?, ?> out = (Map<?, ?>) encoder(loader).encode(((Ok) r).value());
        assertFalse(out.containsKey("approver"),
                "None omits the key entirely, not a null value (spec 11.2)");
    }
}
