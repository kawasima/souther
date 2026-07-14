package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;
import net.unit8.souther.runtime.Violation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A behavior may construct an invariant-bearing data as its result; an invariant violation
 * yields the built-in 制約違反 arm (runtime {@link Violation}) instead of the value (spec 9.4).
 */
class CompileInvariantBehaviorTest {

    private static final String MODULE = """
            module demo

            data Draft { cost: Int }

            data Adjusted {
                cost: Int
                invariant cost >= 0
            }

            behavior discount(d: Draft) -> Adjusted | 制約違反 constructs Adjusted {
                Adjusted { cost: d.cost - 2000 }
            }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object draft(BytesClassLoader loader, long cost) throws Exception {
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo.Draft").getMethod("decoder").invoke(null);
        return d.decode(Raw.integer(cost));
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructionSucceedsWhenInvariantHolds() throws Exception {
        BytesClassLoader loader = loader();
        Object discount = loader.loadClass("demo.discount").getConstructor().newInstance();
        Object r = ((Behavior<Object, Object>) discount).apply(draft(loader, 3000));
        assertEquals("demo.Adjusted", r.getClass().getName(), "3000 - 2000 = 1000 >= 0");

        Encoder enc = (Encoder) loader.loadClass("demo.Adjusted").getMethod("encoder").invoke(null);
        assertEquals(Raw.integer(1000), enc.encode(r));
    }

    @Test
    @SuppressWarnings("unchecked")
    void invariantViolationYieldsTheViolationArm() throws Exception {
        BytesClassLoader loader = loader();
        Object discount = loader.loadClass("demo.discount").getConstructor().newInstance();
        Object r = ((Behavior<Object, Object>) discount).apply(draft(loader, 100));
        // 100 - 2000 = -1900 violates cost >= 0, so the output is the 制約違反 arm
        assertInstanceOf(Violation.class, r);
    }

    @Test
    void constructingInvariantDataWithoutViolationArmIsE1003() {
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
