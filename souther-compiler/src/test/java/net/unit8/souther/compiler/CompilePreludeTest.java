package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shipped prelude (ADR-0028) auto-imports {@code souther.bool.not} into every user module, so a
 * model may call {@code not(b)} with no import. {@code not} is the first self-hosted stdlib function
 * — written in Souther as {@code if b then false else true} and, being non-recursive, expanded
 * inline at the call site rather than lowered to a class.
 */
class CompilePreludeTest {

    private static final String MODULE = """
            module demo

            data In = { flag: Bool }
            data Out = { flag: Bool }

            behavior flip : (i: In) -> Out constructs Out

            let flip (i) = Out { flag: not(i.flag) }
            """;

    @SuppressWarnings("unchecked")
    private boolean flip(boolean in) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object input = ((Ok) d.decode(Map.of("flag", in), Path.ROOT)).value();
        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Flip")
                .getConstructor().newInstance()).apply(input);
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        return (Boolean) ((Map<?, ?>) enc.encode(out)).get("flag");
    }

    @Test
    void autoImportedNotNegatesTrue() throws Exception {
        assertEquals(false, flip(true), "not(true) is false");
    }

    @Test
    void autoImportedNotNegatesFalse() throws Exception {
        assertEquals(true, flip(false), "not(false) is true");
    }
}
