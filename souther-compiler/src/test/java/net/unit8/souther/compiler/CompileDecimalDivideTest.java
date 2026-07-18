package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Decimal division states its rounding as a domain decision: {@code divide(a, b, scale, mode)}
 * gives a Decimal rounded to {@code scale} places by {@code mode} (spec 18.3). A zero divisor is a
 * possible input, so it returns a {@code DivisionByZero} case rather than aborting. The 2-argument
 * {@code divide} stays Int-only.
 */
class CompileDecimalDivideTest {

    // Both match cases yield Out (as the Int divide test does): match requires the branches to agree
    // on type, so the DivisionByZero case reuses p.a as a placeholder value and flags ok: false.
    private static final String MODULE = """
            module demo

            import Decimal ( divide )

            data Pair = { a: Decimal, b: Decimal }
            data Out = { value: Decimal, ok: Bool }

            behavior divv : (p: Pair) -> Out constructs Out
            let divv (p) =
                match divide(p.a, p.b, 2, HALF_UP) with
                    | Decimal as q -> Out { value = q, ok = true }
                    | DivisionByZero -> Out { value = p.a, ok = false }
            """;

    @SuppressWarnings("unchecked")
    private static Object apply(BigDecimal a, BigDecimal b, BytesClassLoader loader) throws Exception {
        Decoder pairDec = (Decoder) loader.loadClass("demo.Pair").getMethod("decoder").invoke(null);
        Object p = ((Ok) pairDec.decode(Map.of("a", a, "b", b), Path.ROOT)).value();
        Object divv = loader.loadClass("demo.Divv").getConstructor().newInstance();
        return ((Behavior<Object, Object>) divv).apply(p);
    }

    @Test
    void dividesWithScaleAndRounding() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(MODULE), CompileDecimalDivideTest.class.getClassLoader());
        // 7 / 3 = 2.333..., HALF_UP at scale 2 = 2.33
        Object out = apply(new BigDecimal("7"), new BigDecimal("3"), loader);
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        Map<?, ?> m = (Map<?, ?>) enc.encode(out);
        assertEquals(new BigDecimal("2.33"), m.get("value"));
        assertEquals(true, m.get("ok"));
    }

    @Test
    void aZeroDivisorTakesTheDivisionByZeroCase() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(MODULE), CompileDecimalDivideTest.class.getClassLoader());
        Object out = apply(new BigDecimal("7"), new BigDecimal("0"), loader);
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        Map<?, ?> m = (Map<?, ?>) enc.encode(out);
        assertEquals(false, m.get("ok"));
    }

    @Test
    void twoArgDivideOnDecimalIsRejected() {
        String src = """
                module demo
                import Int ( divide )
                data Pair = { a: Decimal, b: Decimal }
                data Out = Decimal
                behavior divv : (p: Pair) -> Out constructs Out
                let divv (p) = Out { value = divide(p.a, p.b) }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }
}
