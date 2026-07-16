package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Violation;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A behavior may construct an invariant-bearing data as its result; an invariant violation
 * yields the built-in 制約違反 arm (runtime {@link Violation}) instead of the value (spec 9.4).
 */
class CompileInvariantBehaviorTest {

    private static final String MODULE = """
            module demo

            data Draft = { cost: Int }

            data Adjusted = {
                cost: Int
                invariant cost >= 0
            }

            behavior discount = (d: Draft) -> Adjusted | 制約違反 constructs Adjusted {
                Adjusted { cost: d.cost - 2000 }
            }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object draft(BytesClassLoader loader, long cost) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo.Draft").getMethod("decoder").invoke(null);
        return ((Ok) d.decode(cost, Path.ROOT)).value();
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructionSucceedsWhenInvariantHolds() throws Exception {
        BytesClassLoader loader = loader();
        Object discount = loader.loadClass("demo.discount").getConstructor().newInstance();
        Object r = ((Behavior<Object, Object>) discount).apply(draft(loader, 3000));
        assertEquals("demo.Adjusted", r.getClass().getName(), "3000 - 2000 = 1000 >= 0");

        Encoder enc = (Encoder) loader.loadClass("demo.Adjusted").getMethod("encoder").invoke(null);
        assertEquals(1000L, enc.encode(r));
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

    /**
     * Regression: constructing inside {@code require ... else} skipped the invariant check
     * entirely and handed back a value that breaks it. The guard was a statement, and only the
     * result expression was railway-bound, so the else branch emitted a bare constructor call.
     * {@code require} now desugars to {@code if} (spec 16.4) and both branches are tail, so the
     * construction goes through {@code __construct} wherever it sits.
     */
    @Test
    @SuppressWarnings("unchecked")
    void invariantIsCheckedForAConstructionInsideAGuardElse() throws Exception {
        String src = """
                module demo
                data Draft = { cost: Int }
                data Adjusted = { cost: Int  invariant cost >= 0 }
                data Kept = { v: Int }

                behavior adjust = (d: Draft) -> Kept | Adjusted | 制約違反
                    constructs Kept, Adjusted
                {
                    require d.cost != 999 else Adjusted { cost: d.cost - 2000 }
                    Kept { v: d.cost }
                }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object adjust = loader.loadClass("demo.adjust").getConstructor().newInstance();
        Decoder dec = (Decoder) loader.loadClass("demo.Draft").getMethod("decoder").invoke(null);

        // the guard fails, so the else branch builds Adjusted { cost: 999 - 2000 = -1001 },
        // which breaks cost >= 0 — it must not come back as an Adjusted
        Object bad = ((Behavior<Object, Object>) adjust).apply(((Ok) dec.decode(999L, Path.ROOT)).value());
        assertInstanceOf(Violation.class, bad, "a guard's else branch must check the invariant too");

        Object ok = ((Behavior<Object, Object>) adjust).apply(((Ok) dec.decode(3000L, Path.ROOT)).value());
        assertEquals("demo.Kept", ok.getClass().getName());
    }

    /** The value a guard returns is constructed, so it needs declaring too (spec 12.3). */
    @Test
    void constructingTheGuardValueWithoutDeclaringItIsE1002() {
        String src = """
                module demo
                data Draft = { cost: Int }
                data Rejected = { why: String }
                behavior adjust = (d: Draft) -> Draft | Rejected {
                    require d.cost > 0 else Rejected { why: "nonpositive" }
                    d
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1002", e.code());
    }

    /**
     * `制約違反` is a built-in arm (spec 7.3), not a name the writer picks. The compiler cannot
     * tell which of a behavior's business arms is meant to carry a violation, so the destination
     * is fixed; a business-named arm does not qualify.
     */
    @Test
    void theViolationArmMustBeTheBuiltInOne() {
        String withBusinessName = """
                module demo
                data Draft = { cost: Int }
                data Adjusted = { cost: Int  invariant cost >= 0 }
                data 値引き超過
                behavior discount = (d: Draft) -> Adjusted | 値引き超過 constructs Adjusted {
                    Adjusted { cost: d.cost - 2000 }
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(withBusinessName));
        assertEquals("E1003", e.code(), "a business-named arm is not where a violation goes");
        assertTrue(e.getMessage().contains("制約違反"), e.getMessage());
    }

    /** The built-in arm is the runtime Violation, and it is routed like any other output arm. */
    @Test
    @SuppressWarnings("unchecked")
    void theViolationArmIsTheRuntimeViolationType() throws Exception {
        BytesClassLoader loader = loader();
        Object discount = loader.loadClass("demo.discount").getConstructor().newInstance();
        Object r = ((Behavior<Object, Object>) discount).apply(draft(loader, 100));
        assertInstanceOf(Violation.class, r);
        assertTrue(((Violation) r).message().contains("Adjusted"),
                "the message names the data whose invariant broke: " + ((Violation) r).message());
    }

    @Test
    void constructingInvariantDataWithoutViolationArmIsE1003() {
        String src = """
                module demo
                data Positive = { value: Int  invariant value > 0 }
                behavior make = (x: Int) -> Positive constructs Positive {
                    Positive { value: x }
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1003", e.code());
    }
}
