package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code match} case may destructure a case's fields in the pattern, mirroring record
 * construction: {@code | EmailContact { email = e } -> ...} binds {@code e} to the field. It is
 * sugar for an {@code as} binding plus field reads (spec §match), so it needs a single named case.
 */
class CompileMatchDestructureTest {

    private static final String HEAD = """
            module demo

            data Label = String
            data EmailContact = { email: String }
            data PhoneContact = { phone: String }
            data Contact = EmailContact | PhoneContact

            behavior contactValue : (c: Contact) -> Label constructs Label

            let contactValue (c) =
            """;

    private String run(String matchBody, Map<String, Object> input) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(HEAD + matchBody), getClass().getClassLoader());
        Decoder cd = (Decoder) loader.loadClass("demo.Contact").getMethod("decoder").invoke(null);
        Object contact = ((Ok) cd.decode(input, Path.ROOT)).value();
        Object behavior = loader.loadClass("demo.ContactValue").getConstructor().newInstance();
        @SuppressWarnings("unchecked")
        Object label = ((Behavior<Object, Object>) behavior).apply(contact);
        Encoder enc = (Encoder) loader.loadClass("demo.Label").getMethod("encoder").invoke(null);
        return (String) enc.encode(label);
    }

    private static final String DESTRUCTURE = """
                    match c with
                        | EmailContact { email = e } -> Label { value = e }
                        | PhoneContact { phone = p } -> Label { value = p }
            """;

    @Test
    void destructureBindsAField() throws Exception {
        assertEquals("a@b", run(DESTRUCTURE, Map.of("type", "EmailContact", "email", "a@b")));
        assertEquals("123", run(DESTRUCTURE, Map.of("type", "PhoneContact", "phone", "123")));
    }

    @Test
    void shorthandBindsTheFieldToItsOwnName() throws Exception {
        String body = """
                    match c with
                        | EmailContact { email } -> Label { value = email }
                        | PhoneContact { phone } -> Label { value = phone }
            """;
        assertEquals("a@b", run(body, Map.of("type", "EmailContact", "email", "a@b")));
    }

    @Test
    void destructureCombinesWithAsForTheWholeCase() throws Exception {
        // `{ email = e } as whole` binds both the field and the whole case value
        String body = """
                    match c with
                        | EmailContact { email = e } as whole -> Label { value = whole.email }
                        | PhoneContact { phone = p } -> Label { value = p }
            """;
        assertEquals("a@b", run(body, Map.of("type", "EmailContact", "email", "a@b")));
    }

    @Test
    void anUnknownFieldIsRejected() {
        String body = """
                    match c with
                        | EmailContact { nope = e } -> Label { value = e }
                        | PhoneContact { phone = p } -> Label { value = p }
            """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(HEAD + body));
        assertTrue(ex.getMessage().contains("nope") || ex.getMessage().contains("email"), ex.getMessage());
    }

    @Test
    void destructureOnAnOrPatternIsRejected() {
        // an or-pattern binds to the sum type and cannot name a case's fields
        String body = """
                    match c with
                        | EmailContact | PhoneContact { email = e } -> Label { value = e }
            """;
        assertThrows(CompileException.class, () -> Compiler.compile(HEAD + body));
    }
}
