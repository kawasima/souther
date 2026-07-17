package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.ConstraintViolation;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Int is signed 64-bit; an add/subtract/multiply that leaves that range aborts with a
 * {@link ConstraintViolation} rather than wrapping (spec 18.2). This covers both the {@code *}
 * operator and the {@code multiply} stdlib call, and confirms in-range arithmetic is unaffected.
 */
class CompileIntOverflowTest {

    private static String module(String bodyExpr) {
        return """
                module demo

                data In = Int
                data Out = Int

                behavior compute = (x: In) -> Out constructs Out

                fn compute (x) = Out { value: %s }
                """.formatted(bodyExpr);
    }

    @SuppressWarnings("unchecked")
    private static Object run(String bodyExpr, long input) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(module(bodyExpr)),
                CompileIntOverflowTest.class.getClassLoader());
        Decoder inDecoder = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) inDecoder.decode(input, Path.ROOT)).value();
        Object compute = loader.loadClass("demo.compute").getConstructor().newInstance();
        Object out = ((Behavior<Object, Object>) compute).apply(in);
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        return enc.encode(out);
    }

    @Test
    void multiplyOperatorOverflowAborts() {
        // 5e9 * 5e9 = 2.5e19 > Long.MAX_VALUE
        assertThrows(ConstraintViolation.class, () -> run("x.value * x.value", 5_000_000_000L));
    }

    @Test
    void multiplyStdlibOverflowAborts() {
        assertThrows(ConstraintViolation.class,
                () -> run("multiply(x.value, x.value)", 5_000_000_000L));
    }

    @Test
    void addOperatorOverflowAborts() {
        // 9.2e18 (near Long.MAX) + itself overflows
        assertThrows(ConstraintViolation.class, () -> run("x.value + x.value", 9_000_000_000_000_000_000L));
    }

    @Test
    void inRangeArithmeticIsUnaffected() throws Exception {
        assertEquals(100L, run("x.value * x.value", 10L));
    }
}
