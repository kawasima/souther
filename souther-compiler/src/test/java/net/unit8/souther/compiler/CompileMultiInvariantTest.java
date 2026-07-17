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

            behavior make = (i: In) -> Amount constructs Amount
            fn make (i) = Amount { value: i.value }
            """;

    @SuppressWarnings("unchecked")
    private static Object make(long v, BytesClassLoader loader) throws Exception {
        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) inDec.decode(v, Path.ROOT)).value();
        Object make = loader.loadClass("demo.make").getConstructor().newInstance();
        return ((Behavior<Object, Object>) make).apply(in);
    }

    @Test
    void aValueInBothBoundsIsMinted() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(MODULE), CompileMultiInvariantTest.class.getClassLoader());
        Object amount = make(50L, loader);
        Encoder enc = (Encoder) loader.loadClass("demo.Amount").getMethod("encoder").invoke(null);
        assertEquals(50L, enc.encode(amount));
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
