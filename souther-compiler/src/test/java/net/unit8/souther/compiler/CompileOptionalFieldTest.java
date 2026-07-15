package net.unit8.souther.compiler;

import net.unit8.souther.runtime.DecodeFailure;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field marked {@code T?} is optional (spec 7.4): it decodes to {@code Some}/{@code None}
 * and round-trips. An absent (or null) key decodes to {@code None} without failing.
 */
class CompileOptionalFieldTest {

    private static final String MODULE = """
            module demo

            data Id { value: String }
            data Trip { id: Id  approver: Id? }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Decoder<?> decoder(BytesClassLoader loader) throws Exception {
        return (Decoder<?>) loader.loadClass("demo.Trip").getMethod("decoder").invoke(null);
    }

    private Encoder encoder(BytesClassLoader loader) throws Exception {
        return (Encoder) loader.loadClass("demo.Trip").getMethod("encoder").invoke(null);
    }

    @Test
    void presentOptionalRoundTrips() throws Exception {
        BytesClassLoader loader = loader();
        Object ok = decoder(loader).decode(Raw.object(new HashMap<>(Map.of(
                "id", Raw.text("t-1"), "approver", Raw.text("e-9")))));
        assertTrue(!(ok instanceof DecodeFailure));

        Raw.ObjectValue out = (Raw.ObjectValue) encoder(loader).encode(ok);
        assertEquals(Raw.text("e-9"), out.value().get("approver"));
    }

    @Test
    void absentOptionalDecodesToNone() throws Exception {
        BytesClassLoader loader = loader();
        Object ok = decoder(loader).decode(Raw.object(Map.of("id", Raw.text("t-1"))));
        assertTrue(!(ok instanceof DecodeFailure), "an absent optional key is None, not a failure");

        Raw.ObjectValue out = (Raw.ObjectValue) encoder(loader).encode(ok);
        assertEquals(Raw.nullValue(), out.value().get("approver"), "None encodes to Raw.Null");
    }
}
