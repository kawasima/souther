package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The String standard library beyond length/trim/lowercase (spec 18.1). */
class CompileStringLibTest {

    @Test
    void appendOperatorUppercaseAndSubstringInABehavior() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import String ( uppercase, substring )

                data Name = String
                data Greeting = String

                behavior greet : (n: Name) -> Greeting constructs Greeting

                let greet (n) = Greeting { value = "hi " ++ uppercase(substring(0, 3, n.value)) ++ "!" }
                """), getClass().getClassLoader());

        Object name = Codecs.decoded(loader, "demo.Name", "robert");
        Object behavior = loader.loadClass("demo.Greet").getConstructor().newInstance();
        Object greeting = Codecs.apply(behavior, name);

        // Greeting has a single String field, so it is a newtype: encodes as bare Text
        assertEquals("hi ROB!", Codecs.encode(loader, "demo.Greeting", greeting));
    }

    @Test
    void appendFunctionAndConcatOfAList() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import String ( append, concat )

                data In = { name: String, parts: List<String> }
                data Out = {
                    greeting: String
                    , joined: String
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    greeting = append("Hello, ", i.name),
                    joined = concat(i.parts)
                }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In",
                java.util.Map.of("name", "world", "parts", java.util.List.of("a", "b", "c")));
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        java.util.Map<?, ?> m = (java.util.Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals("Hello, world", m.get("greeting"), "append joins two strings in order");
        assertEquals("abc", m.get("joined"), "concat flattens a List<String> with no separator");
    }

    @Test
    void concatOfAnEmptyListIsTheEmptyString() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import String ( concat )

                data In = { parts: List<String> }
                data Out = String

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { value = concat(i.parts) }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In",
                java.util.Map.of("parts", java.util.List.of()));
        Object behavior = loader.loadClass("demo.Run").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        // Out has a single String field, so it is a newtype: encodes as bare Text
        assertEquals("", Codecs.encode(loader, "demo.Out", out), "concat([]) is the empty string");
    }

    @Test
    void appendingAStringToAListIsRejected() {
        // `++` is Elm's appendable: two lists or two strings, never a mix (spec 18.1).
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                data Bad = String

                behavior run : (b: Bad) -> Bad constructs Bad

                let run (b) = Bad { value = b.value ++ [1] }
                """));
        assertTrue(e.getMessage().contains("two lists or two strings"),
                "the diagnostic should name both admissible operand shapes: " + e.getMessage());
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

                import String ( words, fromInt )

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
                    label = "item-" ++ fromInt(i.n)
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
