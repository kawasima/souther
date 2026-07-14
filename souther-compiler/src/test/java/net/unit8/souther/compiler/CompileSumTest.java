package net.unit8.souther.compiler;

import net.unit8.souther.runtime.DecodeError;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.NonEmptyList;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end test for sum data (sealed interface) and discriminate decoders (spec 8.3, 10.4). */
class CompileSumTest {

    private static final String CONTACT = """
            module demo

            data EmailContact {
                email: String
                decoder from Object {
                    email <- field("email", string)
                    EmailContact { email }
                }
            }

            data PhoneContact {
                phone: String
                decoder from Object {
                    phone <- field("phone", string)
                    PhoneContact { phone }
                }
            }

            data Contact = EmailContact | PhoneContact {
                decoder from Object discriminate on "kind" {
                    "email" => EmailContact.decoder
                    "phone" => PhoneContact.decoder
                }
            }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(CONTACT), getClass().getClassLoader());
    }

    @Test
    void discriminatesToTheRightArm() throws Exception {
        BytesClassLoader loader = loader();
        Class<?> contact = loader.loadClass("demo.Contact");
        Class<?> emailContact = loader.loadClass("demo.EmailContact");
        Decoder<?> decoder = (Decoder<?>) contact.getMethod("decoder").invoke(null);

        Result<?, NonEmptyList<DecodeError>> email = decoder.decode(
                Raw.object(Map.of("kind", Raw.text("email"), "email", Raw.text("a@b"))));
        assertTrue(email.isOk());
        Object value = ((Result.Ok<?, ?>) email).value();
        assertTrue(emailContact.isInstance(value), "decoded to EmailContact");
        assertTrue(contact.isInstance(value), "EmailContact is a Contact");

        Result<?, NonEmptyList<DecodeError>> phone = decoder.decode(
                Raw.object(Map.of("kind", Raw.text("phone"), "phone", Raw.text("123"))));
        assertTrue(phone.isOk());
        assertEquals("demo.PhoneContact", ((Result.Ok<?, ?>) phone).value().getClass().getName());
    }

    @Test
    void unknownTagFailsWithNoVariant() throws Exception {
        Decoder<?> decoder = (Decoder<?>) loader().loadClass("demo.Contact")
                .getMethod("decoder").invoke(null);
        Result<?, NonEmptyList<DecodeError>> bad = decoder.decode(
                Raw.object(Map.of("kind", Raw.text("fax"))));
        assertTrue(bad.isErr());
        assertEquals("no_variant",
                ((Result.Err<?, NonEmptyList<DecodeError>>) bad).error().head().code());
    }

    @Test
    void sumInterfaceIsSealedOverItsArms() throws Exception {
        Class<?> contact = loader().loadClass("demo.Contact");
        assertTrue(contact.isInterface());
        assertTrue(contact.isSealed());
        Set<String> permitted = Arrays.stream(contact.getPermittedSubclasses())
                .map(Class::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("demo.EmailContact", "demo.PhoneContact"), permitted);
    }
}
