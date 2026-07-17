package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Decimal arithmetic (spec 18.3): add/subtract/multiply/compare as total operations. */
class CompileDecimalMathTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void addsAndMultipliesDecimals() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data Price = Decimal
                data Quote = { subtotal: Decimal  doubled: Decimal }

                behavior makeQuote = (a: Price, b: Price) -> Quote constructs Quote

                fn makeQuote (a, b) = Quote {
                    subtotal: add(a.value, b.value),
                    doubled: multiply(add(a.value, b.value), a.value)
                }
                """), getClass().getClassLoader());

        Decoder priceDec = (Decoder) loader.loadClass("demo.Price").getMethod("decoder").invoke(null);
        Object a = ((Ok) priceDec.decode(new BigDecimal("1.5"), Path.ROOT)).value();
        Object b = ((Ok) priceDec.decode(new BigDecimal("2.25"), Path.ROOT)).value();

        Object behavior = loader.loadClass("demo.MakeQuote").getConstructor().newInstance();
        Object quote = behavior.getClass()
                .getMethod("apply", Object.class, Object.class).invoke(behavior, a, b);

        Encoder enc = (Encoder) loader.loadClass("demo.Quote").getMethod("encoder").invoke(null);
        java.util.Map<?, ?> out = (java.util.Map<?, ?>) enc.encode(quote);
        assertEquals(new BigDecimal("3.75"), out.get("subtotal"));
        assertEquals(new BigDecimal("5.625"), out.get("doubled")); // 3.75 * 1.5
    }

    @Test
    void comparesIntsInAnInvariant() throws Exception {
        // compare(a, b) yields -1/0/1; usable with the existing Int comparison operators
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data Ordered = {
                    lo: Int
                    hi: Int
                    invariant compare(lo, hi) <= 0
                }
                """), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.Ordered").getMethod("decoder").invoke(null);
        assertEquals(false, d.decode(java.util.Map.of("lo", 1L, "hi", 2L), Path.ROOT) instanceof Err);
        assertEquals(true, d.decode(java.util.Map.of("lo", 5L, "hi", 2L), Path.ROOT) instanceof Err);
    }
}
