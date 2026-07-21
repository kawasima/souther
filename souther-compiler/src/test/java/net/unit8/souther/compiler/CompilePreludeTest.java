package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

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
            import Bool ( not )

            data In = { flag: Bool }
            data Out = { flag: Bool }

            behavior flip : (i: In) -> Out constructs Out

            let flip (i) = Out { flag = not(i.flag) }
            """;

    private boolean flip(boolean in) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object input = Codecs.decoded(loader, "demo.In", Map.of("flag", in));
        Object behavior = loader.loadClass("demo.Flip" + "$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, input);
        return (Boolean) ((Map<?, ?>) Codecs.encode(loader, "demo.Out", out)).get("flag");
    }

    @Test
    void autoImportedNotNegatesTrue() throws Exception {
        assertEquals(false, flip(true), "not(true) is false");
    }

    @Test
    void autoImportedNotNegatesFalse() throws Exception {
        assertEquals(true, flip(false), "not(false) is true");
    }

    /** The prefix `!` is gone now that `not` covers negation (ADR-0028). */
    @Test
    void thePrefixBangNoLongerParses() {
        String src = MODULE.replace("not(i.flag)", "!i.flag");
        org.junit.jupiter.api.Assertions.assertThrows(
                CompileException.class, () -> Compiler.compile(src));
    }
}
