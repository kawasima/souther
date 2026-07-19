package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decoders/encoders are not written in the domain; they are derived from the data shape
 * (spec responsibility split). This module declares only data + invariant, yet decode/encode
 * work with default conventions (key = field name, {@code data X = Y} = bare newtype,
 * sum discriminator = "type"/case name).
 */
class CompileDeriveTest {

    private static final String MODULE = """
            module demo

            data 金額 = Int
                invariant value >= 0

            data Member = {
                cost: 金額
                , name: String
            }

            data EmailC = { email: String }
            data PhoneC = { phone: String }
            data Contact = EmailC | PhoneC
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    @Test
    void newtypeDerivesAPrimitiveCodec() throws Exception {
        BytesClassLoader loader = loader();

        Result<?> r = Codecs.decode(loader, "demo.金額", 50L);
        assertTrue(r instanceof Ok);
        assertEquals(50L, Codecs.encode(loader, "demo.金額", ((Ok<?>) r).value()));

        assertTrue(Codecs.decode(loader, "demo.金額", -5L) instanceof Err,
                "invariant still runs on the derived construction");
    }

    @Test
    void recordDerivesAnObjectCodec() throws Exception {
        BytesClassLoader loader = loader();
        Result<?> r = Codecs.decode(loader, "demo.Member", Map.of("cost", 30L, "name", "bob"));
        assertTrue(r instanceof Ok);

        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Member", ((Ok<?>) r).value());
        assertEquals(30L, out.get("cost"));
        assertEquals("bob", out.get("name"));
    }

    @Test
    void sumDerivesADiscriminatorOnType() throws Exception {
        BytesClassLoader loader = loader();
        Result<?> r = Codecs.decode(loader, "demo.Contact", Map.of("type", "EmailC", "email", "a@b"));
        assertTrue(r instanceof Ok);
        Object email = ((Ok<?>) r).value();
        assertEquals("demo.EmailC", email.getClass().getName());

        // round-trips through the derived sum encoder
        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Contact", email);
        assertEquals("EmailC", out.get("type"));
        assertEquals("a@b", out.get("email"));
    }
}
