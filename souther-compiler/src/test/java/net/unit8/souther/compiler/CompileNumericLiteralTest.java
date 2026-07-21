package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Numeric literals a model needs to write constants: a {@code Decimal} literal like {@code 0.08}
 * (spec 7.1, 18.3) and unary minus for a negative value like {@code -5} (spec 18.1).
 */
class CompileNumericLiteralTest {

    private Object run(String module, String behavior, String type, Object input) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(module), getClass().getClassLoader());
        Object in = Codecs.decoded(loader, "demo." + type, input);
        // the generated behavior class capitalizes the behavior's first letter (spec 19.5)
        String behaviorClass = Character.toUpperCase(behavior.charAt(0)) + behavior.substring(1);
        Object b = loader.loadClass("demo." + behaviorClass + "$Impl").getDeclaredConstructor().newInstance();
        Object out = Codecs.apply(b, in);
        return Codecs.encode(loader, "demo." + type, out);
    }

    @Test
    void aDecimalLiteralIsAValue() throws Exception {
        String module = """
                module demo
                data Rate = Decimal
                behavior fixed : (r: Rate) -> Rate constructs Rate
                let fixed (r) = Rate { value = 0.08m }
                """;
        assertEquals(new BigDecimal("0.08"), run(module, "fixed", "Rate", new BigDecimal("1.00")));
    }

    @Test
    void unaryMinusNegates() throws Exception {
        String module = """
                module demo
                data N = Int
                behavior flip : (n: N) -> N constructs N
                let flip (n) = N { value = -n.value }
                """;
        assertEquals(-7L, run(module, "flip", "N", 7L));
        assertEquals(5L, run(module, "flip", "N", -5L));
    }

    @Test
    void aNegativeIntegerLiteral() throws Exception {
        String module = """
                module demo
                data N = Int
                behavior zero : (n: N) -> N constructs N
                let zero (n) = N { value = -5 }
                """;
        assertEquals(-5L, run(module, "zero", "N", 0L));
    }
}
