package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
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
 * Decoders/encoders are not written in the domain; they are derived from the data shape
 * (spec responsibility split). This module declares only data + invariant, yet decode/encode
 * work with default conventions (key = field name, single-primitive-field = newtype,
 * sum discriminator = "type"/arm name).
 */
class CompileDeriveTest {

    private static final String MODULE = """
            module demo

            data 金額 { value: Int  invariant value >= 0 }

            data Member {
                cost: 金額
                name: String
            }

            data EmailC { email: String }
            data PhoneC { phone: String }
            data Contact = EmailC | PhoneC
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Decoder decoder(BytesClassLoader loader, String type) throws Exception {
        return (Decoder) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
    }

    private Encoder encoder(BytesClassLoader loader, String type) throws Exception {
        return (Encoder) loader.loadClass("demo." + type).getMethod("encoder").invoke(null);
    }

    @Test
    void newtypeDerivesAPrimitiveCodec() throws Exception {
        BytesClassLoader loader = loader();
        Decoder d = decoder(loader, "金額");

        Result r = d.decode(50L, Path.ROOT);
        assertTrue(r instanceof Ok);
        assertEquals(50L, encoder(loader, "金額").encode(((Ok) r).value()));

        assertTrue(d.decode(-5L, Path.ROOT) instanceof Err,
                "invariant still runs on the derived construction");
    }

    @Test
    void recordDerivesAnObjectCodec() throws Exception {
        BytesClassLoader loader = loader();
        Result r = decoder(loader, "Member")
                .decode(Map.of("cost", 30L, "name", "bob"), Path.ROOT);
        assertTrue(r instanceof Ok);

        Map<?, ?> out = (Map<?, ?>) encoder(loader, "Member").encode(((Ok) r).value());
        assertEquals(30L, out.get("cost"));
        assertEquals("bob", out.get("name"));
    }

    @Test
    void sumDerivesADiscriminatorOnType() throws Exception {
        BytesClassLoader loader = loader();
        Result r = decoder(loader, "Contact")
                .decode(Map.of("type", "EmailC", "email", "a@b"), Path.ROOT);
        assertTrue(r instanceof Ok);
        Object email = ((Ok) r).value();
        assertEquals("demo.EmailC", email.getClass().getName());

        // round-trips through the derived sum encoder
        Map<?, ?> out = (Map<?, ?>) encoder(loader, "Contact").encode(email);
        assertEquals("EmailC", out.get("type"));
        assertEquals("a@b", out.get("email"));
    }
}
