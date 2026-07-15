package net.unit8.souther.compiler;


import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

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

                data Price { value: Decimal }
                data Quote { subtotal: Decimal  doubled: Decimal }

                behavior quote(a: Price, b: Price) -> Quote constructs Quote {
                    Quote {
                        subtotal: add(a.value, b.value),
                        doubled: multiply(add(a.value, b.value), a.value)
                    }
                }
                """), getClass().getClassLoader());

        Decoder<?> priceDec = (Decoder<?>) loader.loadClass("demo.Price").getMethod("decoder").invoke(null);
        Object a = priceDec.decode(Raw.decimal(new BigDecimal("1.5")));
        Object b = priceDec.decode(Raw.decimal(new BigDecimal("2.25")));

        Object behavior = loader.loadClass("demo.quote").getConstructor().newInstance();
        Object quote = behavior.getClass()
                .getMethod("apply", Object.class, Object.class).invoke(behavior, a, b);

        Encoder enc = (Encoder) loader.loadClass("demo.Quote").getMethod("encoder").invoke(null);
        Raw.ObjectValue out = (Raw.ObjectValue) enc.encode(quote);
        assertEquals(Raw.decimal(new BigDecimal("3.75")), out.value().get("subtotal"));
        assertEquals(Raw.decimal(new BigDecimal("5.625")), out.value().get("doubled")); // 3.75 * 1.5
    }

    @Test
    void comparesIntsInAnInvariant() throws Exception {
        // compare(a, b) yields -1/0/1; usable with the existing Int comparison operators
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data Ordered {
                    lo: Int
                    hi: Int
                    invariant compare(lo, hi) <= 0
                }
                """), getClass().getClassLoader());
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo.Ordered").getMethod("decoder").invoke(null);
        assertEquals(false, d.decode(Raw.object(java.util.Map.of("lo", Raw.integer(1), "hi", Raw.integer(2))))
                instanceof net.unit8.souther.runtime.DecodeFailure);
        assertEquals(true, d.decode(Raw.object(java.util.Map.of("lo", Raw.integer(5), "hi", Raw.integer(2))))
                instanceof net.unit8.souther.runtime.DecodeFailure);
    }
}
