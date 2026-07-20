package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import net.unit8.souther.runtime.ConstraintViolation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic operators {@code + - * /} over Int and Decimal (spec 18.1). A Decimal literal
 * carries the {@code m} suffix (F# form). {@code /} aborts on a zero divisor (like overflow), while
 * the {@code divide} functions still return {@code X | DivisionByZero} for case handling; Decimal
 * {@code /} rounds to F#/.NET System.Decimal precision, half away from zero.
 */
class CompileArithmeticOperatorTest {

    private Object run(String module, String behavior, String type, Object input) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(module), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo." + type, input);
        String behaviorClass = Character.toUpperCase(behavior.charAt(0)) + behavior.substring(1);
        Object b = loader.loadClass("demo." + behaviorClass).getDeclaredConstructor().newInstance();
        Object out = Codecs.apply(b, in);
        return Codecs.encode(loader, "demo." + type, out);
    }

    private static final String INT_CALC = """
            module demo
            data N = Int
            behavior calc : (n: N) -> N constructs N
            let calc (n) = N { value = %s }
            """;

    private static final String DEC_CALC = """
            module demo
            data Money = Decimal
            behavior calc : (m: Money) -> Money constructs Money
            let calc (m) = Money { value = %s }
            """;

    @Test
    void intOperators() throws Exception {
        assertEquals(14L, run(INT_CALC.formatted("n.value * 3 - 1"), "calc", "N", 5L));
        assertEquals(3L, run(INT_CALC.formatted("n.value / 2"), "calc", "N", 7L));   // truncating
    }

    @Test
    void decimalOperators() throws Exception {
        // 5 + 10.5 = 15.5, via the `+` operator and the `10.5m` literal
        assertEquals(new BigDecimal("15.5"),
                run(DEC_CALC.formatted("m.value + 10.5m"), "calc", "Money", new BigDecimal("5")));
        // an integer-valued Decimal literal carries `m` too
        assertEquals(0, ((BigDecimal) run(DEC_CALC.formatted("500m"), "calc", "Money", new BigDecimal("1")))
                .compareTo(new BigDecimal("500")));
    }

    @Test
    void decimalDivisionRoundsLikeFSharp() throws Exception {
        // 10 / 3 rounds to ~29 significant digits, HALF_UP — it does not abort on a non-terminating result
        BigDecimal expected = new BigDecimal("10").divide(new BigDecimal("3"),
                new MathContext(29, RoundingMode.HALF_UP));
        assertEquals(expected,
                run(DEC_CALC.formatted("m.value / 3m"), "calc", "Money", new BigDecimal("10")));
    }

    @Test
    void divisionByZeroAborts() {
        // the `/` operator aborts (like overflow); the `divide` function is the case-returning form
        assertThrows(ConstraintViolation.class,
                () -> run(INT_CALC.formatted("n.value / 0"), "calc", "N", 5L));
        assertThrows(ConstraintViolation.class,
                () -> run(DEC_CALC.formatted("m.value / 0m"), "calc", "Money", new BigDecimal("5")));
    }

    @Test
    void aFractionalLiteralWithoutMIsRejected() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(DEC_CALC.formatted("1.5")));
        assertTrue(e.getMessage().contains("m"), "the diagnostic should point at the `m` suffix: " + e.getMessage());
    }
}
