package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The String standard library beyond length/trim/lowercase (spec 18.1). */
class CompileStringLibTest {

    @Test
    void concatUppercaseAndSubstringInABehavior() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import String ( concat, uppercase, substring )

                data Name = String
                data Greeting = String

                behavior greet : (n: Name) -> Greeting constructs Greeting

                let greet (n) = Greeting { value = concat(concat("hi ", uppercase(substring(0, 3, n.value))), "!") }
                """), getClass().getClassLoader());

        Object name = Codecs.decoded(loader, "demo.Name", "robert");
        Object behavior = loader.loadClass("demo.Greet").getConstructor().newInstance();
        Object greeting = Codecs.apply(behavior, name);

        // Greeting has a single String field, so it is a newtype: encodes as bare Text
        assertEquals("hi ROB!", Codecs.encode(loader, "demo.Greeting", greeting));
    }

    @Test
    void splitJoinAndReplace() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import String ( split, join, replace )

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

        Object raw = Codecs.decoded(loader, "demo.Raw", "a,b,,c");
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, raw);

        java.util.Map<?, ?> m = (java.util.Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals(java.util.List.of("a", "b", "", "c"), m.get("parts"), "split keeps empty pieces");
        assertEquals("a|b||c", m.get("joined"));
        assertEquals("a;b;;c", m.get("swapped"));
    }

    @Test
    void wordsAndFromInt() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import String ( words, concat, fromInt )

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

        Object in = Codecs.decoded(loader, "demo.In", java.util.Map.of("text", "  the  quick fox ", "n", 42L));
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        java.util.Map<?, ?> m = (java.util.Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals(java.util.List.of("the", "quick", "fox"), m.get("tokens"), "words splits on whitespace runs");
        assertEquals("item-42", m.get("label"));
    }

    @Test
    void startsWithAndEndsWithInAnInvariant() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import String ( startsWith, endsWith )

                data Sku = String
                    invariant startsWith("X", value) && endsWith("Z", value)
                """), getClass().getClassLoader());
        assertTrue(Codecs.decode(loader, "demo.Sku", "XabcZ") instanceof Ok);
        assertTrue(Codecs.decode(loader, "demo.Sku", "Xabc") instanceof Err, "must end with Z");
        assertTrue(Codecs.decode(loader, "demo.Sku", "abcZ") instanceof Err, "must start with X");
    }
}
