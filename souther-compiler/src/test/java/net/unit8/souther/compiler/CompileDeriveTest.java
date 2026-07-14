package net.unit8.souther.compiler;

import net.unit8.souther.runtime.DecodeError;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.NonEmptyList;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

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

    private Decoder<?> decoder(BytesClassLoader loader, String type) throws Exception {
        return (Decoder<?>) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
    }

    private Encoder encoder(BytesClassLoader loader, String type) throws Exception {
        return (Encoder) loader.loadClass("demo." + type).getMethod("encoder").invoke(null);
    }

    @Test
    void newtypeDerivesAPrimitiveCodec() throws Exception {
        BytesClassLoader loader = loader();
        Decoder<?> d = decoder(loader, "金額");

        Result<?, NonEmptyList<DecodeError>> ok = d.decode(Raw.integer(50));
        assertTrue(ok.isOk());
        assertEquals(Raw.integer(50), encoder(loader, "金額").encode(((Result.Ok<?, ?>) ok).value()));

        assertTrue(d.decode(Raw.integer(-5)).isErr(), "invariant still runs on the derived construction");
    }

    @Test
    void recordDerivesAnObjectCodec() throws Exception {
        BytesClassLoader loader = loader();
        Result<?, ?> ok = decoder(loader, "Member")
                .decode(Raw.object(Map.of("cost", Raw.integer(30), "name", Raw.text("bob"))));
        assertTrue(ok.isOk());

        Raw.ObjectValue out = (Raw.ObjectValue) encoder(loader, "Member").encode(((Result.Ok<?, ?>) ok).value());
        assertEquals(Raw.integer(30), out.value().get("cost"));
        assertEquals(Raw.text("bob"), out.value().get("name"));
    }

    @Test
    void sumDerivesADiscriminatorOnType() throws Exception {
        BytesClassLoader loader = loader();
        Result<?, ?> email = decoder(loader, "Contact")
                .decode(Raw.object(Map.of("type", Raw.text("EmailC"), "email", Raw.text("a@b"))));
        assertTrue(email.isOk());
        assertEquals("demo.EmailC", ((Result.Ok<?, ?>) email).value().getClass().getName());

        // round-trips through the derived sum encoder
        Raw.ObjectValue out = (Raw.ObjectValue) encoder(loader, "Contact").encode(((Result.Ok<?, ?>) email).value());
        assertEquals(Raw.text("EmailC"), out.value().get("type"));
        assertEquals(Raw.text("a@b"), out.value().get("email"));
    }
}
