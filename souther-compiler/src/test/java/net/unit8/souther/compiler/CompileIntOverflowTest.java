package net.unit8.souther.compiler;

import net.unit8.souther.runtime.ConstraintViolation;

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
                import Int ( multiply )

                data In = Int
                data Out = Int

                behavior compute : (x: In) -> Out constructs Out

                let compute (x) = Out { value = %s }
                """.formatted(bodyExpr);
    }

    private static Object run(String bodyExpr, long input) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(module(bodyExpr)),
                CompileIntOverflowTest.class.getClassLoader());
        Object in = Codecs.decoded(loader, "demo.In", input);
        Object compute = loader.loadClass("demo.Compute").getConstructor().newInstance();
        Object out = Codecs.apply(compute, in);
        return Codecs.encode(loader, "demo.Out", out);
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
