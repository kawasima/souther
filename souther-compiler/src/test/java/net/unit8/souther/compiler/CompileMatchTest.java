package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** End-to-end test for {@code match} over a sum type, including exhaustiveness (spec 16.3, 22.7). */
class CompileMatchTest {

    private static final String MODULE = """
            module demo

            data Label { value: String }

            data EmailContact { email: String }
            data PhoneContact { phone: String }
            data Contact = EmailContact | PhoneContact

            behavior contactValue(c: Contact) -> Label constructs Label {
                match c {
                    case EmailContact as e => Label { value: e.email }
                    case PhoneContact as p => Label { value: p.phone }
                }
            }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Raw run(BytesClassLoader loader, Raw contactRaw) throws Exception {
        Decoder<?> cd = (Decoder<?>) loader.loadClass("demo.Contact").getMethod("decoder").invoke(null);
        Object contact = ((net.unit8.souther.runtime.Result.Ok<?, ?>) cd.decode(contactRaw)).value();

        Object behavior = loader.loadClass("demo.contactValue").getConstructor().newInstance();
        @SuppressWarnings("unchecked")
        Object label = ((Behavior<Object, Object>) behavior).apply(contact);

        Encoder enc = (Encoder) loader.loadClass("demo.Label").getMethod("encoder").invoke(null);
        return enc.encode(label);
    }

    @Test
    void matchSelectsTheEmailArm() throws Exception {
        BytesClassLoader loader = loader();
        Raw out = run(loader, Raw.object(Map.of("type", Raw.text("EmailContact"), "email", Raw.text("a@b"))));
        assertEquals(Raw.text("a@b"), out);
    }

    @Test
    void matchSelectsThePhoneArm() throws Exception {
        BytesClassLoader loader = loader();
        Raw out = run(loader, Raw.object(Map.of("type", Raw.text("PhoneContact"), "phone", Raw.text("123"))));
        assertEquals(Raw.text("123"), out);
    }

    @Test
    void nonExhaustiveMatchIsE1201() {
        String src = """
                module demo
                data A { x: String }
                data B { y: String }
                data AB = A | B
                data Label { value: String }
                behavior pick(v: AB) -> Label constructs Label {
                    match v {
                        case A as a => Label { value: a.x }
                    }
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1201", e.code());
    }
}
