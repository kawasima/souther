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

                import String { concat, uppercase, substring }

                data Name = String
                data Greeting = String

                behavior greet : (n: Name) -> Greeting constructs Greeting

                let greet (n) = Greeting { value = concat(concat("hi ", uppercase(substring(0, 3, n.value))), "!") }
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    void splitJoinAndReplace() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import String { split, join, replace }

                data Raw = String
                data Out = {
                    parts: List<String>
                    , joined: String
                    , swapped: String
                }

                behavior run : (r: Raw) -> Out constructs Out

                let run (r) = Out {
                    parts = split(",", r.value),
                    joined = join("|", split(",", r.value)),
                    swapped = replace(",", ";", r.value)
                }
                """), getClass().getClassLoader());

        Decoder rawDec = (Decoder) loader.loadClass("demo.Raw").getMethod("decoder").invoke(null);
        Object raw = ((Ok) rawDec.decode("a,b,,c", Path.ROOT)).value();
        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Run")
                .getConstructor().newInstance()).apply(raw);

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        java.util.Map<?, ?> m = (java.util.Map<?, ?>) enc.encode(out);
        assertEquals(java.util.List.of("a", "b", "", "c"), m.get("parts"), "split keeps empty pieces");
        assertEquals("a|b||c", m.get("joined"));
        assertEquals("a;b;;c", m.get("swapped"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void wordsAndFromInt() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import String { words, concat, fromInt }

                data In = {
                    text: String
                    , n: Int
                }
                data Out = {
                    tokens: List<String>
                    , label: String
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    tokens = words(i.text),
                    label = concat("item-", fromInt(i.n))
                }
                """), getClass().getClassLoader());

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) inDec.decode(java.util.Map.of("text", "  the  quick fox ", "n", 42L), Path.ROOT)).value();
        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Run")
                .getConstructor().newInstance()).apply(in);

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        java.util.Map<?, ?> m = (java.util.Map<?, ?>) enc.encode(out);
        assertEquals(java.util.List.of("the", "quick", "fox"), m.get("tokens"), "words splits on whitespace runs");
        assertEquals("item-42", m.get("label"));
    }

    @Test
    void startsWithAndEndsWithInAnInvariant() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import String { startsWith, endsWith }

                data Sku = String
                    invariant startsWith("X", value) && endsWith("Z", value)
                """), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.Sku").getMethod("decoder").invoke(null);

        assertTrue(d.decode("XabcZ", Path.ROOT) instanceof Ok);
        assertTrue(d.decode("Xabc", Path.ROOT) instanceof Err, "must end with Z");
        assertTrue(d.decode("abcZ", Path.ROOT) instanceof Err, "must start with X");
    }
}
