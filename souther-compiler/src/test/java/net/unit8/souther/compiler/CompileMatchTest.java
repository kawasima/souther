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

/** End-to-end test for {@code match} over a sum type, including exhaustiveness (spec 16.3, 22.7). */
class CompileMatchTest {

    private static final String MODULE = """
            module demo

            data Label = String

            data EmailContact = { email: String }
            data PhoneContact = { phone: String }
            data Contact = EmailContact | PhoneContact

            behavior contactValue = (c: Contact) -> Label constructs Label

            fn contactValue (c) =
                match c {
                    case EmailContact as e => Label { value: e.email }
                    case PhoneContact as p => Label { value: p.phone }
                }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object run(BytesClassLoader loader, Map<String, Object> contactInput) throws Exception {
        Decoder cd = (Decoder) loader.loadClass("demo.Contact").getMethod("decoder").invoke(null);
        Object contact = ((Ok) cd.decode(contactInput, Path.ROOT)).value();

        Object behavior = loader.loadClass("demo.contactValue").getConstructor().newInstance();
        @SuppressWarnings("unchecked")
        Object label = ((Behavior<Object, Object>) behavior).apply(contact);

        // Label is a single-field newtype, so its encoder yields the bare String.
        Encoder enc = (Encoder) loader.loadClass("demo.Label").getMethod("encoder").invoke(null);
        return enc.encode(label);
    }

    @Test
    void matchSelectsTheEmailArm() throws Exception {
        BytesClassLoader loader = loader();
        Object out = run(loader, Map.of("type", "EmailContact", "email", "a@b"));
        assertEquals("a@b", out);
    }

    @Test
    void matchSelectsThePhoneArm() throws Exception {
        BytesClassLoader loader = loader();
        Object out = run(loader, Map.of("type", "PhoneContact", "phone", "123"));
        assertEquals("123", out);
    }

    @Test
    void nonExhaustiveMatchIsE1201() {
        String src = """
                module demo
                data A = { x: String }
                data B = { y: String }
                data AB = A | B
                data Label = String
                behavior pick = (v: AB) -> Label constructs Label

                fn pick (v) =
                    match v {
                        case A as a => Label { value: a.x }
                    }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1201", e.code());
    }
}
