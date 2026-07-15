package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end test for {@code require ... else} and type-routed {@code >>} composition: an arm
 * the next stage does not accept propagates through unchanged (spec 12.2, 14.2).
 */
class CompileRailwayTest {

    private static final String MODULE = """
            module demo

            data Amount = { value: Int }
            data TooLarge = { limit: Int }
            data Doubled = { value: Int }

            // over 100 leaves the main line as a TooLarge arm. `require ... else` mints the
            // TooLarge, so the behavior declares it (spec 12.3).
            behavior guard = (a: Amount) -> Amount | TooLarge constructs TooLarge {
                require a.value <= 100 else TooLarge { limit: 100 }
                a
            }

            // only accepts Amount; a TooLarge flowing in would bypass this stage
            behavior toDoubled = (a: Amount) -> Doubled constructs Doubled {
                Doubled { value: a.value }
            }

            behavior process = guard >> toDoubled
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object amount(BytesClassLoader loader, long n) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo.Amount").getMethod("decoder").invoke(null);
        return ((Ok) d.decode(n, Path.ROOT)).value();
    }

    @Test
    @SuppressWarnings("unchecked")
    void amountFlowsThroughToDoubled() throws Exception {
        BytesClassLoader loader = loader();
        Object process = loader.loadClass("demo.process").getConstructor().newInstance();
        Object r = ((Behavior<Object, Object>) process).apply(amount(loader, 42));
        // 42 <= 100, so guard yields Amount, which toDoubled consumes into a Doubled
        assertEquals("demo.Doubled", r.getClass().getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tooLargeArmPropagatesPastToDoubled() throws Exception {
        BytesClassLoader loader = loader();
        Object process = loader.loadClass("demo.process").getConstructor().newInstance();
        Object r = ((Behavior<Object, Object>) process).apply(amount(loader, 500));
        // 500 > 100, so guard yields TooLarge, which toDoubled does not accept: it propagates
        assertEquals("demo.TooLarge", r.getClass().getName());
    }

    @Test
    void requireElseValueMustBeAnOutputArm() {
        // `other` is a B, which is not one of `bad`'s output arms (just A), so this is rejected.
        String src = """
                module demo
                data A = { value: Int }
                data B = { value: Int }
                behavior bad = (a: A, other: B) -> A {
                    require a.value <= 1 else other
                    a
                }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }
}
