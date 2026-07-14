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

/** End-to-end test for sum encoders (the inverse of discriminate): round-trip a sum value (spec 10.4, 11). */
class CompileSumEncoderTest {

    private static final String MODULE = """
            module demo

            data EmailContact {
                email: String
                decoder from Object { email <- field("email", string)  EmailContact { email } }
                encoder self { Object { "email": Text(self.email) } }
            }
            data PhoneContact {
                phone: String
                decoder from Object { phone <- field("phone", string)  PhoneContact { phone } }
                encoder self { Object { "phone": Text(self.phone) } }
            }
            data Contact = EmailContact | PhoneContact {
                decoder from Object discriminate on "kind" {
                    "email" => EmailContact.decoder
                    "phone" => PhoneContact.decoder
                }
                encoder discriminate on "kind" {
                    EmailContact => "email"
                    PhoneContact => "phone"
                }
            }
            """;

    private Raw roundTrip(BytesClassLoader loader, Raw input) throws Exception {
        Class<?> contact = loader.loadClass("demo.Contact");
        Decoder<?> decoder = (Decoder<?>) contact.getMethod("decoder").invoke(null);
        Result<?, NonEmptyList<DecodeError>> decoded = decoder.decode(input);
        assertTrue(decoded.isOk());
        Encoder enc = (Encoder) contact.getMethod("encoder").invoke(null);
        return enc.encode(((Result.Ok<?, ?>) decoded).value());
    }

    @Test
    void encodesEachArmWithItsDiscriminant() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        Raw email = roundTrip(loader, Raw.object(Map.of("kind", Raw.text("email"), "email", Raw.text("a@b"))));
        Map<String, Raw> emailMap = ((Raw.ObjectValue) email).value();
        assertEquals(Raw.text("email"), emailMap.get("kind"));
        assertEquals(Raw.text("a@b"), emailMap.get("email"));

        Raw phone = roundTrip(loader, Raw.object(Map.of("kind", Raw.text("phone"), "phone", Raw.text("123"))));
        Map<String, Raw> phoneMap = ((Raw.ObjectValue) phone).value();
        assertEquals(Raw.text("phone"), phoneMap.get("kind"));
        assertEquals(Raw.text("123"), phoneMap.get("phone"));
    }
}
