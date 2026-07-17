package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

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

                data Name = String
                data Greeting = String

                behavior greet = (n: Name) -> Greeting constructs Greeting

                fn greet (n) = Greeting { value: concat(concat("hi ", uppercase(substring(n.value, 0, 3))), "!") }
                """), getClass().getClassLoader());

        Decoder nameDec = (Decoder) loader.loadClass("demo.Name").getMethod("decoder").invoke(null);
        Object name = ((Ok) nameDec.decode("robert", Path.ROOT)).value();
        Object greeting = ((Behavior<Object, Object>) loader.loadClass("demo.Greet")
                .getConstructor().newInstance()).apply(name);

        Encoder enc = (Encoder) loader.loadClass("demo.Greeting").getMethod("encoder").invoke(null);
        // Greeting has a single String field, so it is a newtype: encodes as bare Text
        assertEquals("hi ROB!", enc.encode(greeting));
    }

    @Test
    void startsWithAndEndsWithInAnInvariant() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data Sku = String
                    invariant startsWith(value, "X") && endsWith(value, "Z")
                """), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.Sku").getMethod("decoder").invoke(null);

        assertTrue(d.decode("XabcZ", Path.ROOT) instanceof Ok);
        assertTrue(d.decode("Xabc", Path.ROOT) instanceof Err, "must end with Z");
        assertTrue(d.decode("abcZ", Path.ROOT) instanceof Err, "must start with X");
    }
}
