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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shipped intrinsic (ADR-0028) is a stdlib function whose body is a named primitive — its
 * signature lives in the prelude ({@code souther.string}) instead of hard-coded in the checker. It
 * behaves like a built-in: a call is checked against that signature and the backend emits the
 * primitive. {@code trim} is the first such function moved out of the hard-coded switch.
 */
class CompileIntrinsicTest {

    private static final String MODULE = """
            module demo

            data In = { s: String }
            data Out = { s: String }

            behavior clean : (i: In) -> Out constructs Out

            let clean (i) = Out { s: trim(i.s) }
            """;

    @Test
    @SuppressWarnings("unchecked")
    void theTrimIntrinsicRunsAsAStdlibFunction() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) d.decode(Map.of("s", "  hi  "), Path.ROOT)).value();
        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Clean")
                .getConstructor().newInstance()).apply(in);
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        assertEquals("hi", ((Map<?, ?>) enc.encode(out)).get("s"), "trim strips surrounding whitespace");
    }

    /** The intrinsic keeps a real signature: a non-String argument is still rejected. */
    @Test
    void trimStillTypeChecksItsArgument() {
        String src = MODULE.replace("trim(i.s)", "trim(5)");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("argument"), e.getMessage());
    }

    /** A user module cannot write `intrinsic`; it is a core privilege. */
    @Test
    void aUserModuleCannotDeclareAnIntrinsic() {
        String src = """
                module demo
                data Out = { s: String }
                behavior clean : (i: Out) -> Out constructs Out
                let strip (s: String): String = intrinsic "string.trim"
                let clean (i) = i
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }
}
