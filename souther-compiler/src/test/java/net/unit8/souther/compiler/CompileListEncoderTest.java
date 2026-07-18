package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code List<T>} of a non-String/Int primitive (Decimal, Bool, Date, DateTime) must round-trip:
 * the encoder was only derived for String and Int elements, so such a field failed to compile even
 * though its decoder was fine (spec 7.2).
 */
class CompileListEncoderTest {

    @Test
    @SuppressWarnings("unchecked")
    void listsOfNonStringPrimitivesRoundTrip() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data Bag = {
                    prices: List<Decimal>
                    , flags:  List<Bool>
                    , days:   List<Date>
                }
                """), getClass().getClassLoader());
        Decoder dec = (Decoder) loader.loadClass("demo.Bag").getMethod("decoder").invoke(null);
        Encoder enc = (Encoder) loader.loadClass("demo.Bag").getMethod("encoder").invoke(null);

        Map<String, Object> in = Map.of(
                "prices", List.of(new BigDecimal("1.50"), new BigDecimal("2.25")),
                "flags", List.of(true, false),
                "days", List.of(LocalDate.parse("2026-07-17")));
        Result r = dec.decode(in, Path.ROOT);
        assertTrue(r instanceof Ok, "the decoder already handled these element types");

        Map<?, ?> out = (Map<?, ?>) enc.encode(((Ok) r).value());
        assertEquals(List.of(new BigDecimal("1.50"), new BigDecimal("2.25")), out.get("prices"));
        assertEquals(List.of(true, false), out.get("flags"));
        assertEquals(List.of("2026-07-17"), out.get("days"), "a Date element encodes to its ISO form");
    }
}
