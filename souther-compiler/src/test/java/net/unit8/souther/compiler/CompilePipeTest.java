package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** End-to-end test for {@code >>} composition and required-behavior injection (spec 14, 13, 19.5). */
class CompilePipeTest {

    private static final String MODULE = """
            module demo

            data Wrap { value: String }
            data Mid  { value: String }
            data Out  { value: String }

            behavior a(w: Wrap) -> Mid constructs Mid { Mid { value: w.value } }
            behavior b(m: Mid) -> Out constructs Out { Out { value: m.value } }
            behavior ab = a >> b

            required behavior fetch(Wrap) -> Mid
            behavior handle = fetch >> b
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object decode(BytesClassLoader loader, String type, String value) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
        return ((Ok) d.decode(value, Path.ROOT)).value();
    }

    @Test
    @SuppressWarnings("unchecked")
    void composesDependencyFreeBehaviors() throws Exception {
        BytesClassLoader loader = loader();
        Object ab = loader.loadClass("demo.ab").getConstructor().newInstance();
        // apply returns the output arm value directly
        Object out = ((Behavior<Object, Object>) ab).apply(decode(loader, "Wrap", "hi"));

        // Out is a single-field newtype, so its encoder yields the bare String value.
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        assertEquals("hi", enc.encode(out));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void injectsRequiredBehaviorIntoPipeline() throws Exception {
        BytesClassLoader loader = loader();
        Decoder midDecoder = (Decoder) loader.loadClass("demo.Mid")
                .getMethod("decoder").invoke(null);

        // The Java-side implementation of `fetch` returns the Mid arm value directly.
        Behavior fetch = w -> ((Ok) midDecoder.decode("hello", Path.ROOT)).value();

        Object handle = loader.loadClass("demo.handle")
                .getConstructor(Behavior.class).newInstance(fetch);
        Object out = ((Behavior) handle).apply(decode(loader, "Wrap", "ignored"));

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        assertEquals("hello", enc.encode(out));
    }

    @Test
    void mismatchedCompositionIsE1701() {
        // a: Wrap -> Mid; feeding Mid into a second `a` (which wants Wrap) accepts no arm.
        String src = """
                module demo
                data Wrap { value: String }
                data Mid  { value: String }
                behavior a(w: Wrap) -> Mid constructs Mid { Mid { value: w.value } }
                behavior bad = a >> a
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1701", e.code());
    }
}
