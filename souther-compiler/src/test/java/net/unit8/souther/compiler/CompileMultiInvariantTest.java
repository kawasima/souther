package net.unit8.souther.compiler;

import net.unit8.souther.runtime.ConstraintViolation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A data may state more than one {@code invariant} line; every one must hold. Earlier only the
 * last survived (each line overwrote the previous), so a value breaking an earlier one was minted
 * anyway. All lines are now conjoined, so any single violation aborts (spec 9, 19.7).
 */
class CompileMultiInvariantTest {

    private static final String MODULE = """
            module demo

            data In = Int
            data Amount = Int
                invariant value > 0
                invariant value < 100

            behavior make : (i: In) -> Amount constructs Amount
            let make (i) = Amount { value = i.value }
            """;

    private static Object make(long v, BytesClassLoader loader) throws Exception {
        Object in = Codecs.decoded(loader, "demo.In", v);
        Object make = loader.loadClass("demo.Make" + "$Impl").getConstructor().newInstance();
        return Codecs.apply(make, in);
    }

    @Test
    void aValueInBothBoundsIsMinted() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(MODULE), CompileMultiInvariantTest.class.getClassLoader());
        Object amount = make(50L, loader);
        assertEquals(50L, Codecs.encode(loader, "demo.Amount", amount));
    }

    @Test
    void breakingTheFirstInvariantAborts() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(MODULE), CompileMultiInvariantTest.class.getClassLoader());
        // value > 0 is the first line; only the last (value < 100) used to be enforced
        assertThrows(ConstraintViolation.class, () -> make(0L, loader));
    }

    @Test
    void breakingTheLastInvariantAborts() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(MODULE), CompileMultiInvariantTest.class.getClassLoader());
        assertThrows(ConstraintViolation.class, () -> make(150L, loader));
    }
}
