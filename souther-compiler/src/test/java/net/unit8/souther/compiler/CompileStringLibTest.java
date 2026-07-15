package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.DecodeFailure;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The String standard library beyond length/trim/lowercase (spec 18.1). */
class CompileStringLibTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void concatUppercaseAndSubstringInABehavior() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data Name { value: String }
                data Greeting { text: String }

                behavior greet(n: Name) -> Greeting constructs Greeting {
                    Greeting { text: concat(concat("hi ", uppercase(substring(n.value, 0, 3))), "!") }
                }
                """), getClass().getClassLoader());

        Decoder<?> nameDec = (Decoder<?>) loader.loadClass("demo.Name").getMethod("decoder").invoke(null);
        Object name = nameDec.decode(Raw.text("robert"));
        Object greeting = ((Behavior<Object, Object>) loader.loadClass("demo.greet")
                .getConstructor().newInstance()).apply(name);

        Encoder enc = (Encoder) loader.loadClass("demo.Greeting").getMethod("encoder").invoke(null);
        // Greeting has a single String field, so it is a newtype: encodes as bare Text
        assertEquals(Raw.text("hi ROB!"), enc.encode(greeting));
    }

    @Test
    void startsWithAndEndsWithInAnInvariant() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data Sku {
                    value: String
                    invariant startsWith(value, "X") && endsWith(value, "Z")
                }
                """), getClass().getClassLoader());
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo.Sku").getMethod("decoder").invoke(null);

        assertTrue(!(d.decode(Raw.text("XabcZ")) instanceof DecodeFailure));
        assertTrue(d.decode(Raw.text("Xabc")) instanceof DecodeFailure, "must end with Z");
        assertTrue(d.decode(Raw.text("abcZ")) instanceof DecodeFailure, "must start with X");
    }
}
