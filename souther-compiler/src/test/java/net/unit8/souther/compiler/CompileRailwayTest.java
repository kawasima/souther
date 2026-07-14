package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for Result behaviors with {@code require ... else} and Railway Oriented
 * {@code >>} composition that short-circuits on the first failure (spec 12.2, 14.7).
 */
class CompileRailwayTest {

    private static final String MODULE = """
            module demo

            data Amount { value: Int  decoder from Int as n { Amount { value: n } } }
            data TooLarge { limit: Int }
            data Doubled { value: Int }

            // fails when the amount is over 100
            behavior guard(a: Amount) -> Result<Amount, TooLarge> {
                require a.value <= 100 else TooLarge { limit: 100 }
                a
            }

            // pure stage: repackage
            behavior toDoubled(a: Amount) -> Doubled constructs Doubled {
                Doubled { value: a.value }
            }

            behavior process = guard >> toDoubled
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object amount(BytesClassLoader loader, long n) throws Exception {
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo.Amount").getMethod("decoder").invoke(null);
        return ((Result.Ok<?, ?>) d.decode(Raw.integer(n))).value();
    }

    @Test
    @SuppressWarnings("unchecked")
    void pipelineSucceedsWhenGuardPasses() throws Exception {
        BytesClassLoader loader = loader();
        Object process = loader.loadClass("demo.process").getConstructor().newInstance();
        Result<?, ?> r = ((Behavior<Object, Object>) process).apply(amount(loader, 42));
        assertTrue(r.isOk(), "42 <= 100, so the pipeline should produce a Doubled");
        assertEquals("demo.Doubled", ((Result.Ok<?, ?>) r).value().getClass().getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pipelineShortCircuitsOnGuardFailure() throws Exception {
        BytesClassLoader loader = loader();
        Object process = loader.loadClass("demo.process").getConstructor().newInstance();
        Result<?, ?> r = ((Behavior<Object, Object>) process).apply(amount(loader, 500));
        assertTrue(r.isErr(), "500 > 100, so guard fails and toDoubled never runs");
        // the failure value is a TooLarge, not a Doubled
        assertEquals("demo.TooLarge", ((Result.Err<?, ?>) r).error().getClass().getName());
    }

    @Test
    void requireWithoutResultReturnIsRejected() {
        String src = """
                module demo
                data A { value: Int }
                behavior bad(a: A) -> A {
                    require a.value <= 1 else A { value: 0 }
                    a
                }
                """;
        // `bad` returns A (not a Result), so a guard is a compile error.
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }
}
