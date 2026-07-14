package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

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
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
        return d.decode(Raw.text(value));
    }

    @Test
    @SuppressWarnings("unchecked")
    void composesDependencyFreeBehaviors() throws Exception {
        BytesClassLoader loader = loader();
        Object ab = loader.loadClass("demo.ab").getConstructor().newInstance();
        // apply returns the output arm value directly
        Object out = ((Behavior<Object, Object>) ab).apply(decode(loader, "Wrap", "hi"));

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        assertEquals(Raw.text("hi"), ((Raw.ObjectValue) rewrap(enc, out)).value().get("value"));
    }

    // Out's encoder is `Text(self.value)`, so encode returns a Raw.Text; wrap it for a uniform check.
    private Raw rewrap(Encoder enc, Object out) {
        Raw raw = enc.encode(out);
        return Raw.object(java.util.Map.of("value", raw));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void injectsRequiredBehaviorIntoPipeline() throws Exception {
        BytesClassLoader loader = loader();
        Decoder<?> midDecoder = (Decoder<?>) loader.loadClass("demo.Mid")
                .getMethod("decoder").invoke(null);

        // The Java-side implementation of `fetch` returns the Mid arm value directly.
        Behavior fetch = w -> midDecoder.decode(Raw.text("hello"));

        Object handle = loader.loadClass("demo.handle")
                .getConstructor(Behavior.class).newInstance(fetch);
        Object out = ((Behavior) handle).apply(decode(loader, "Wrap", "ignored"));

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        assertEquals(Raw.text("hello"), enc.encode(out));
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
