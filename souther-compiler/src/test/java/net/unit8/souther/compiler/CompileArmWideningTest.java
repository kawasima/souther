package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A value of an arm type is a value of its sum (spec 8.3): it can be assigned to a sum-typed
 * field and passed to a sum-typed parameter, not only returned. The functional-language norm.
 */
class CompileArmWideningTest {

    private static final String MODULE = """
            module demo

            data A { x: Int  tag: Int }
            data B { y: Int }
            data AB = A | B
            data Wrap { it: AB }

            behavior wrap(a: A) -> Wrap constructs Wrap {
                Wrap { it: a }
            }
            """;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void anArmAssignsToASumField() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder<?> ad = (Decoder<?>) loader.loadClass("demo.A").getMethod("decoder").invoke(null);
        Object a = ad.decode(Raw.object(Map.of("x", Raw.integer(5), "tag", Raw.integer(1))));

        Object wrap = ((Behavior<Object, Object>) loader.loadClass("demo.wrap")
                .getConstructor().newInstance()).apply(a);

        Encoder enc = (Encoder) loader.loadClass("demo.Wrap").getMethod("encoder").invoke(null);
        Raw.ObjectValue out = (Raw.ObjectValue) enc.encode(wrap);
        Raw.ObjectValue it = (Raw.ObjectValue) out.value().get("it");
        assertEquals(Raw.text("A"), it.value().get("type"), "the arm is tagged as its sum's arm");
        assertEquals(Raw.integer(5), it.value().get("x"));
    }

    @Test
    void anArmPassesToASumParameter() {
        // store expects the sum AB; use passes an A (an arm) — must type-check
        String src = """
                module demo
                data A { x: Int }
                data B { y: Int }
                data AB = A | B
                data Stored { it: AB }
                required behavior store(AB) -> Stored
                behavior use(a: A) -> Stored { store(a) }
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }
}
