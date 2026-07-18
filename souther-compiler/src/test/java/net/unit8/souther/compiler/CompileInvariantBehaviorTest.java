package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.ConstraintViolation;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A behavior may construct an invariant-bearing data as its result. When the invariant holds the
 * value is returned; when it is violated the computation aborts by throwing a runtime
 * {@link ConstraintViolation} — a violation is a model bug, not a business case (spec 7.3, 9.4).
 * The output type carries no 制約違反 case.
 */
class CompileInvariantBehaviorTest {

    private static final String MODULE = """
            module demo

            data Draft = Int

            data Adjusted = Int
                invariant value >= 0

            behavior discount : (d: Draft) -> Adjusted constructs Adjusted

            let discount (d) = Adjusted { value: d.value - 2000 }
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
        Object discount = loader.loadClass("demo.Discount").getConstructor().newInstance();
        Object r = ((Behavior<Object, Object>) discount).apply(draft(loader, 3000));
        assertEquals("demo.Adjusted", r.getClass().getName(), "3000 - 2000 = 1000 >= 0");

        Encoder enc = (Encoder) loader.loadClass("demo.Adjusted").getMethod("encoder").invoke(null);
        assertEquals(1000L, enc.encode(r));
    }

    @Test
    @SuppressWarnings("unchecked")
    void invariantViolationAborts() throws Exception {
        BytesClassLoader loader = loader();
        Object discount = loader.loadClass("demo.Discount").getConstructor().newInstance();
        // 100 - 2000 = -1900 violates cost >= 0, so the construction aborts rather than returning
        ConstraintViolation v = assertThrows(ConstraintViolation.class,
                () -> ((Behavior<Object, Object>) discount).apply(draft(loader, 100)));
        // the message names the data whose invariant broke
        org.junit.jupiter.api.Assertions.assertTrue(v.getMessage().contains("Adjusted"), v.getMessage());
    }

    /**
     * Regression: constructing inside {@code require ... else} skipped the invariant check
     * entirely and handed back a value that breaks it. The guard was a statement, and only the
     * result expression was railway-bound, so the else branch emitted a bare constructor call.
     * {@code require} now desugars to {@code if} (spec 16.4) and both branches are tail, so the
     * construction goes through {@code __construct} wherever it sits — and aborts on violation.
     */
    @Test
    @SuppressWarnings("unchecked")
    void invariantIsCheckedForAConstructionInsideAGuardElse() throws Exception {
        String src = """
                module demo
                data Draft = Int
                data Adjusted = { cost: Int  invariant cost >= 0 }
                data Kept = { v: Int }

                behavior adjust : (d: Draft) -> Kept | Adjusted
                    constructs Kept, Adjusted

                let adjust (d) = {
                    require d.value /= 999 else Adjusted { cost: d.value - 2000 }
                    Kept { v: d.value }
                }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object adjust = loader.loadClass("demo.Adjust").getConstructor().newInstance();
        Decoder dec = (Decoder) loader.loadClass("demo.Draft").getMethod("decoder").invoke(null);

        // the guard fails, so the else branch builds Adjusted { cost: 999 - 2000 = -1001 },
        // which breaks cost >= 0 — it must abort, not come back as an Adjusted
        assertThrows(ConstraintViolation.class,
                () -> ((Behavior<Object, Object>) adjust).apply(((Ok) dec.decode(999L, Path.ROOT)).value()),
                "a guard's else branch must check the invariant too");

        Object ok = ((Behavior<Object, Object>) adjust).apply(((Ok) dec.decode(3000L, Path.ROOT)).value());
        assertEquals("demo.Kept", ok.getClass().getName());
    }

    /** The value a guard returns is constructed, so it needs declaring too (spec 12.3). */
    @Test
    void constructingTheGuardValueWithoutDeclaringItIsE1002() {
        // the `require ... else Rejected` builds `Rejected`: `Flagged` is declared but `Rejected` is
        // also built, so the undeclared guard value `Rejected` is E1002.
        String src = """
                module demo
                data Draft = { cost: Int }
                data Rejected = { why: String }
                data Flagged
                behavior adjust : (d: Draft) -> Draft | Rejected | Flagged constructs Flagged

                let adjust (d) = {
                    require d.cost > 0 else Rejected { why: "nonpositive" }
                    require d.cost < 1000 else Flagged
                    d
                }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1002", e.code());
    }

    /**
     * Constructing invariant-bearing data no longer requires a 制約違反 output case — the
     * declaration below compiles, and a violation would abort at run time (spec 7.3, 9.4).
     */
    @Test
    void constructingInvariantDataNeedsNoViolationCase() {
        String src = """
                module demo
                data Positive = { value: Int  invariant value > 0 }
                behavior make : (x: Int) -> Positive constructs Positive

                let make (x) = Positive { value: x }
                """;
        // compiles without error (no E1003, which is retired)
        Compiler.compile(src);
    }
}
