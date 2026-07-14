package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.DecodeError;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.NonEmptyList;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Result behavior may construct an invariant-bearing data as its result; the construction
 * is railway-bound, so an invariant violation short-circuits into a failure (spec 9.4, 12.2).
 */
class CompileInvariantBehaviorTest {

    private static final String MODULE = """
            module demo

            data Draft { cost: Int  decoder from Object { cost <- field("cost", int)  Draft { cost } } }

            data Overflow

            data Adjusted {
                cost: Int
                invariant cost >= 0
                encoder self { Int(self.cost) }
            }

            behavior discount(d: Draft) -> Result<Adjusted, Overflow> constructs Adjusted {
                Adjusted { cost: d.cost - 2000 }
            }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object draft(BytesClassLoader loader, long cost) throws Exception {
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo.Draft").getMethod("decoder").invoke(null);
        return ((Result.Ok<?, ?>) d.decode(Raw.object(Map.of("cost", Raw.integer(cost))))).value();
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructionSucceedsWhenInvariantHolds() throws Exception {
        BytesClassLoader loader = loader();
        Object discount = loader.loadClass("demo.discount").getConstructor().newInstance();
        Result<?, ?> r = ((Behavior<Object, Object>) discount).apply(draft(loader, 3000));
        assertTrue(r.isOk(), "3000 - 2000 = 1000 >= 0");

        Encoder enc = (Encoder) loader.loadClass("demo.Adjusted").getMethod("encoder").invoke(null);
        assertEquals(Raw.integer(1000), enc.encode(((Result.Ok<?, ?>) r).value()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void invariantViolationShortCircuitsToFailure() throws Exception {
        BytesClassLoader loader = loader();
        Object discount = loader.loadClass("demo.discount").getConstructor().newInstance();
        Result<?, ?> r = ((Behavior<Object, Object>) discount).apply(draft(loader, 100));
        assertTrue(r.isErr(), "100 - 2000 = -1900 violates cost >= 0");

        NonEmptyList<DecodeError> errors = (NonEmptyList<DecodeError>) ((Result.Err<?, ?>) r).error();
        assertEquals("invariant_violation", errors.head().code());
    }

    @Test
    void constructingInvariantDataInPureBehaviorIsStillE1003() {
        String src = """
                module demo
                data Positive { value: Int  invariant value > 0 }
                behavior make(x: Int) -> Positive constructs Positive {
                    Positive { value: x }
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1003", e.code());
    }
}
