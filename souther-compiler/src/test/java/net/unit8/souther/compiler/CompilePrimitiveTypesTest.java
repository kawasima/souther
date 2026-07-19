package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

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

    @Test
    void boolFieldRoundTrips() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Flag = Bool
                """);

        Result<?> r = Codecs.decode(loader, "demo.Flag", true);
        assertTrue(r instanceof Ok, "a bare bool decodes as a newtype");
        assertEquals(true, Codecs.encode(loader, "demo.Flag", ((Ok<?>) r).value()));
    }

    @Test
    void boolFieldInsideAnObject() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Account = { name: String, active: Bool }
                """);
        Result<?> r = Codecs.decode(loader, "demo.Account", Map.of("name", "a", "active", false));
        assertTrue(r instanceof Ok);

        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Account", ((Ok<?>) r).value());
        assertEquals(false, out.get("active"));
    }

    @Test
    void decimalFieldRoundTrips() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Price = Decimal
                """);
        Result<?> r = Codecs.decode(loader, "demo.Price", new BigDecimal("12.50"));
        assertTrue(r instanceof Ok, "a bare decimal decodes as a newtype");
        assertEquals(new BigDecimal("12.50"), Codecs.encode(loader, "demo.Price", ((Ok<?>) r).value()));
    }

    @Test
    void dateFieldReadsFromLocalDate() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Event = Date
                """);
        Result<?> r = Codecs.decode(loader, "demo.Event", LocalDate.parse("2026-07-15"));
        assertTrue(r instanceof Ok, "a Date decodes from a LocalDate value");
        assertEquals("2026-07-15", Codecs.encode(loader, "demo.Event", ((Ok<?>) r).value()));

        assertTrue(Codecs.decode(loader, "demo.Event", "not-a-date") instanceof Err);
    }

    @Test
    void dateTimeFieldReadsFromLocalDateTime() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Stamp = DateTime
                """);
        Result<?> r = Codecs.decode(loader, "demo.Stamp", LocalDateTime.parse("2026-07-15T10:30:45"));
        assertTrue(r instanceof Ok, "a DateTime decodes from a LocalDateTime value");
        assertEquals("2026-07-15T10:30:45", Codecs.encode(loader, "demo.Stamp", ((Ok<?>) r).value()));
    }
}
