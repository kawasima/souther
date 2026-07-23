package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.runtime.ConstraintViolation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Int.modBy(divisor, n)} — Elm-style floored modulo (spec 18.2). It is declared in
 * {@code souther.int} and backed by the {@code IntMath.modBy} kernel: the divisor comes first, the
 * result takes the sign of the divisor (floored, unlike the truncating {@code Int.remainder}), and a
 * zero divisor aborts with {@link ConstraintViolation} — so the result is a plain {@code Int} that
 * reads cleanly in an invariant. This also covers the Int/Decimal function forms migrated to the
 * {@code .sou} declaration seam ({@code add}/{@code compare}).
 */
class CompileIntModByTest {

    private long calc(String expr, long input) throws Exception {
        String src = """
                module demo
                data N = Int
                behavior calc : (n: N) -> N constructs N
                let calc (n) = N { value = %s }
                """.formatted(expr);
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo.N", input);
        Object b = loader.loadClass("demo.Calc$Impl").getDeclaredConstructor().newInstance();
        return (long) Codecs.encode(loader, "demo.N", Codecs.apply(b, in));
    }

    @Test
    void modByComputesFlooredModulo() throws Exception {
        assertEquals(0L, calc("Int.modBy(12, n.value)", 24L));
        assertEquals(1L, calc("Int.modBy(12, n.value)", 25L));
        // Floored: modBy(12, -1) == 11 (sign of the divisor), where a truncating remainder gives -1.
        assertEquals(11L, calc("Int.modBy(12, n.value)", -1L));
    }

    @Test
    void modByAbortsOnZeroDivisor() {
        assertThrows(ConstraintViolation.class, () -> calc("Int.modBy(0, n.value)", 5L));
    }

    @Test
    void migratedIntFunctionFormsStillWork() throws Exception {
        // add/compare moved from compiler built-ins to souther.int, still callable as functions.
        assertEquals(30L, calc("Int.add(n.value, 5)", 25L));
        assertEquals(1L, calc("Int.compare(n.value, 0)", 7L));
        assertEquals(-1L, calc("Int.compare(n.value, 0)", -7L));
    }

    @Test
    void migratedDecimalFunctionFormsWork() throws Exception {
        // add is a BigDecimal instance method (jdk row); compare is a DecimalMath static (rt row).
        // Both moved from compiler built-ins to souther.decimal.
        assertEquals(0, new BigDecimal("3.5").compareTo(decAdd("Decimal.add(m.value, 2m)", "1.5")));
        assertEquals(1L, decCompare("2.5", "2m"));
        assertEquals(-1L, decCompare("1.5", "2m"));
        assertEquals(0L, decCompare("2.0", "2m"));   // compareTo ignores scale
    }

    // Money = Decimal; the expression fills its value (for add/subtract/multiply, a Decimal result).
    private BigDecimal decAdd(String expr, String input) throws Exception {
        String src = """
                module demo
                data Money = Decimal
                behavior calc : (m: Money) -> Money constructs Money
                let calc (m) = Money { value = %s }
                """.formatted(expr);
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo.Money", new BigDecimal(input));
        Object b = loader.loadClass("demo.Calc$Impl").getDeclaredConstructor().newInstance();
        return (BigDecimal) Codecs.encode(loader, "demo.Money", Codecs.apply(b, in));
    }

    // Decimal.compare returns Int, so it fills an Int-wrapping newtype.
    private long decCompare(String input, String against) throws Exception {
        String src = """
                module demo
                data Money = Decimal
                data Cmp = Int
                behavior calc : (m: Money) -> Cmp constructs Cmp
                let calc (m) = Cmp { value = Decimal.compare(m.value, %s) }
                """.formatted(against);
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo.Money", new BigDecimal(input));
        Object b = loader.loadClass("demo.Calc$Impl").getDeclaredConstructor().newInstance();
        return (long) Codecs.encode(loader, "demo.Cmp", Codecs.apply(b, in));
    }

    // --- the invariant use case (Issue #50): a plain-Int result reads cleanly in an invariant ---
    private static final String BOX = """
            module demo
            data 箱 = Int invariant Int.modBy(12, value) == 0
            """;

    private Decoder<Object, ?> boxDecoder() throws Exception {
        ClassLoader loader = new BytesClassLoader(Compiler.compile(BOX), getClass().getClassLoader());
        return Codecs.decoder(loader, "demo.箱");
    }

    @Test
    void modByInInvariantAcceptsAMultiple() throws Exception {
        Result<?> r = boxDecoder().decode(24L, Path.ROOT);
        assertTrue(r instanceof Ok, "24 is a multiple of 12, so the invariant holds");
    }

    @Test
    void modByInInvariantRejectsANonMultiple() throws Exception {
        Result<?> r = boxDecoder().decode(25L, Path.ROOT);
        assertTrue(r instanceof Err, "25 is not a multiple of 12, so the invariant fails");
    }

    // A constant construction is verified at compile time by evaluating the invariant bytecode (the
    // new intrinsic runs under CTFE too), so a constant that breaks the modBy invariant is a compile
    // error, not a runtime abort.
    private static final String CONST_BOX = """
            module demo
            data 箱 = Int invariant Int.modBy(12, value) == 0
            behavior mk : (x: Int) -> 箱 constructs 箱
            let mk (x) = 箱(%d)
            """;

    @Test
    void modByConstantConstructionCompilesWhenTheInvariantHolds() {
        Compiler.compile(CONST_BOX.formatted(24));
    }

    @Test
    void modByConstantConstructionIsACompileErrorWhenTheInvariantFails() {
        assertThrows(CompileException.class, () -> Compiler.compile(CONST_BOX.formatted(25)));
    }
}
