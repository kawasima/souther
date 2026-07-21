package net.unit8.souther.compiler;

import net.unit8.raoh.Err;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Decimal arithmetic (spec 18.3): add/subtract/multiply/compare as total operations. */
class CompileDecimalMathTest {

    @Test
    void addsAndMultipliesDecimals() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Decimal ( add, multiply )

                data Price = Decimal
                data Quote = { subtotal: Decimal, doubled: Decimal }

                behavior makeQuote : (a: Price, b: Price) -> Quote constructs Quote

                let makeQuote (a, b) = Quote {
                    subtotal = add(a.value, b.value),
                    doubled = multiply(add(a.value, b.value), a.value)
                }
                """), getClass().getClassLoader());

        Object a = Codecs.decoded(loader, "demo.Price", new BigDecimal("1.5"));
        Object b = Codecs.decoded(loader, "demo.Price", new BigDecimal("2.25"));

        Object behavior = loader.loadClass("demo.MakeQuote" + "$Impl").getConstructor().newInstance();
        Object quote = behavior.getClass()
                .getMethod("apply", Object.class, Object.class).invoke(behavior, a, b);

        java.util.Map<?, ?> out = (java.util.Map<?, ?>) Codecs.encode(loader, "demo.Quote", quote);
        assertEquals(new BigDecimal("3.75"), out.get("subtotal"));
        assertEquals(new BigDecimal("5.625"), out.get("doubled")); // 3.75 * 1.5
    }

    @Test
    void comparesIntsInAnInvariant() throws Exception {
        // compare(a, b) yields -1/0/1; usable with the existing Int comparison operators
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Int ( compare )

                data Ordered = {
                    lo: Int
                    , hi: Int
                } invariant compare(lo, hi) <= 0
                """), getClass().getClassLoader());
        assertEquals(false, Codecs.decode(loader, "demo.Ordered", java.util.Map.of("lo", 1L, "hi", 2L)) instanceof Err);
        assertEquals(true, Codecs.decode(loader, "demo.Ordered", java.util.Map.of("lo", 5L, "hi", 2L)) instanceof Err);
    }
}
