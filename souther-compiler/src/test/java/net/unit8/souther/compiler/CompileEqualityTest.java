package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ==} is value equality (spec 7.1), and the generated equals/hashCode agree with it.
 *
 * <p>For {@code Decimal} that means ignoring scale: {@code BigDecimal.equals} calls 1.0 and 1.00
 * different, but the same amount arrives with a different scale depending on whether it was read
 * from JSON or a DB column. Equality and the hash have to move together — Groovy changed {@code ==}
 * and left hashCode alone, and that bug has been open since 2007.
 */
class CompileEqualityTest {

    private static final String MODULE = """
            module demo

            data Money = { amount: Decimal }
            data Id = String
            data Pair = { a: Id, b: Id }
            data Yes
            data No
            data Answer = Yes | No
            data R = { v: Bool }

            behavior 同じか : (p: Pair) -> R constructs R
            behavior 違うか : (p: Pair) -> R constructs R
            behavior 金額が同じか : (x: Money, y: Money) -> R constructs R
            behavior 答えが同じか : (x: Answer, y: Answer) -> R constructs R

            let 同じか (p) = R { v = p.a == p.b }
            let 違うか (p) = R { v = p.a /= p.b }
            let 金額が同じか (x, y) = R { v = x.amount == y.amount }
            let 答えが同じか (x, y) = R { v = x == y }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object make(BytesClassLoader loader, String type, Object... args) throws Exception {
        Constructor<?> c = loader.loadClass("demo." + type).getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(args);
    }

    private boolean run(BytesClassLoader loader, String name, Object... args) throws Exception {
        Class<?> c = loader.loadClass("demo." + name + "$Impl");
        Class<?>[] ps = new Class<?>[args.length];
        java.util.Arrays.fill(ps, Object.class);
        Object r = c.getMethod("apply", ps).invoke(c.getConstructor().newInstance(), args);
        Field f = r.getClass().getDeclaredField("v");
        f.setAccessible(true);
        return (Boolean) f.get(r);
    }

    @Test
    void dataComparesByItsFieldsNotByReference() throws Exception {
        BytesClassLoader loader = loader();
        Object a = make(loader, "Id", "e1");
        Object b = make(loader, "Id", "e1");
        assertFalse(a == b, "distinct instances — a reference comparison would say not equal");
        assertTrue(run(loader, "同じか", make(loader, "Pair", a, b)));
    }

    /** {@code /=} is inequality (Elm/Haskell form; ADR-0028), the negation of {@code ==}. */
    @Test
    void slashEqualsIsInequality() throws Exception {
        BytesClassLoader loader = loader();
        Object a = make(loader, "Id", "e1");
        Object b = make(loader, "Id", "e1");
        Object c = make(loader, "Id", "e2");
        assertFalse(run(loader, "違うか", make(loader, "Pair", a, b)), "e1 /= e1 is false");
        assertTrue(run(loader, "違うか", make(loader, "Pair", a, c)), "e1 /= e2 is true");
    }

    /** The old {@code !=} inequality no longer lexes (ADR-0028 pairs {@code /=} with {@code ==}). */
    @Test
    void oldBangEqualsNoLongerParses() {
        String src = MODULE.replace("p.a /= p.b", "p.a != p.b");
        org.junit.jupiter.api.Assertions.assertThrows(
                CompileException.class, () -> Compiler.compile(src));
    }

    @Test
    void aSumComparesByCaseAndContents() throws Exception {
        BytesClassLoader loader = loader();
        assertTrue(run(loader, "答えが同じか", make(loader, "Yes"), make(loader, "Yes")));
        assertFalse(run(loader, "答えが同じか", make(loader, "Yes"), make(loader, "No")));
    }

    /** 1.0 and 1.00 are the same amount; only their scale differs. */
    @Test
    void decimalIgnoresScale() throws Exception {
        BytesClassLoader loader = loader();
        Object m1 = make(loader, "Money", new BigDecimal("1.0"));
        Object m2 = make(loader, "Money", new BigDecimal("1.00"));
        assertTrue(run(loader, "金額が同じか", m1, m2), "1.0 == 1.00");
        assertFalse(new BigDecimal("1.0").equals(new BigDecimal("1.00")),
                "…which is not what BigDecimal.equals says, so we are not just inheriting it");
    }

    /**
     * The half Groovy skipped: if equality ignores scale the hash must too, or a data holding a
     * Decimal stops working as a Map key.
     */
    @Test
    void theHashAgreesWithEqualityAcrossScales() throws Exception {
        BytesClassLoader loader = loader();
        Object m1 = make(loader, "Money", new BigDecimal("1.0"));
        Object m2 = make(loader, "Money", new BigDecimal("1.00"));

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode(), "equal values must hash the same");

        Map<Object, String> map = new HashMap<>();
        map.put(m1, "x");
        assertEquals("x", map.get(m2), "a Map keyed by Money must find 1.00 under 1.0");
        assertEquals(1, new HashSet<>(List.of(m1, m2)).size());
    }

    /** The edges: zero has many scales, and stripTrailingZeros normalises 10 to 1E+1. */
    @Test
    void zeroAndNegativeScalesAgreeToo() throws Exception {
        BytesClassLoader loader = loader();
        Object z1 = make(loader, "Money", new BigDecimal("0"));
        Object z2 = make(loader, "Money", new BigDecimal("0.00"));
        assertEquals(z1, z2);
        assertEquals(z1.hashCode(), z2.hashCode());

        Object t1 = make(loader, "Money", new BigDecimal("10"));
        Object t2 = make(loader, "Money", new BigDecimal("1E+1"));
        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }
}
