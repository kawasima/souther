package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The primitive types beyond Int/String — Bool, Decimal, Date, DateTime (spec section 7.1) —
 * must be usable as data fields and round-trip through the derived decoder/encoder.
 */
class CompilePrimitiveTypesTest {

    private BytesClassLoader loader(String module) {
        return new BytesClassLoader(Compiler.compile(module), getClass().getClassLoader());
    }

    private Decoder decoder(BytesClassLoader loader, String pkgType) throws Exception {
        return (Decoder) loader.loadClass(pkgType).getMethod("decoder").invoke(null);
    }

    private Encoder encoder(BytesClassLoader loader, String pkgType) throws Exception {
        return (Encoder) loader.loadClass(pkgType).getMethod("encoder").invoke(null);
    }

    @Test
    void boolFieldRoundTrips() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Flag { value: Bool }
                """);
        Decoder d = decoder(loader, "demo.Flag");

        Result r = d.decode(true, Path.ROOT);
        assertTrue(r instanceof Ok, "a bare bool decodes as a newtype");
        assertEquals(true, encoder(loader, "demo.Flag").encode(((Ok) r).value()));
    }

    @Test
    void boolFieldInsideAnObject() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Account { name: String  active: Bool }
                """);
        Result r = decoder(loader, "demo.Account")
                .decode(Map.of("name", "a", "active", false), Path.ROOT);
        assertTrue(r instanceof Ok);

        Map<?, ?> out = (Map<?, ?>) encoder(loader, "demo.Account").encode(((Ok) r).value());
        assertEquals(false, out.get("active"));
    }

    @Test
    void decimalFieldRoundTrips() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Price { value: Decimal }
                """);
        Result r = decoder(loader, "demo.Price").decode(new BigDecimal("12.50"), Path.ROOT);
        assertTrue(r instanceof Ok, "a bare decimal decodes as a newtype");
        assertEquals(new BigDecimal("12.50"), encoder(loader, "demo.Price").encode(((Ok) r).value()));
    }

    @Test
    void dateFieldReadsFromLocalDate() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Event { on: Date }
                """);
        Result r = decoder(loader, "demo.Event").decode(LocalDate.parse("2026-07-15"), Path.ROOT);
        assertTrue(r instanceof Ok, "a Date decodes from a LocalDate value");
        assertEquals("2026-07-15", encoder(loader, "demo.Event").encode(((Ok) r).value()));

        assertTrue(decoder(loader, "demo.Event").decode("not-a-date", Path.ROOT) instanceof Err);
    }

    @Test
    void dateTimeFieldReadsFromLocalDateTime() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Stamp { at: DateTime }
                """);
        Result r = decoder(loader, "demo.Stamp")
                .decode(LocalDateTime.parse("2026-07-15T10:30:45"), Path.ROOT);
        assertTrue(r instanceof Ok, "a DateTime decodes from a LocalDateTime value");
        assertEquals("2026-07-15T10:30:45", encoder(loader, "demo.Stamp").encode(((Ok) r).value()));
    }
}
