package net.unit8.souther.compiler;

import net.unit8.souther.runtime.DecodeFailure;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

    private Decoder<?> decoder(BytesClassLoader loader, String pkgType) throws Exception {
        return (Decoder<?>) loader.loadClass(pkgType).getMethod("decoder").invoke(null);
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
        Decoder<?> d = decoder(loader, "demo.Flag");

        Object ok = d.decode(Raw.bool(true));
        assertTrue(!(ok instanceof DecodeFailure), "a bare bool decodes as a newtype");
        assertEquals(Raw.bool(true), encoder(loader, "demo.Flag").encode(ok));
    }

    @Test
    void boolFieldInsideAnObject() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Account { name: String  active: Bool }
                """);
        Object ok = decoder(loader, "demo.Account")
                .decode(Raw.object(Map.of("name", Raw.text("a"), "active", Raw.bool(false))));
        assertTrue(!(ok instanceof DecodeFailure));

        Raw.ObjectValue out = (Raw.ObjectValue) encoder(loader, "demo.Account").encode(ok);
        assertEquals(Raw.bool(false), out.value().get("active"));
    }

    @Test
    void decimalFieldRoundTrips() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Price { value: Decimal }
                """);
        Object ok = decoder(loader, "demo.Price").decode(Raw.decimal(new BigDecimal("12.50")));
        assertTrue(!(ok instanceof DecodeFailure), "a bare decimal decodes as a newtype");
        assertEquals(Raw.decimal(new BigDecimal("12.50")), encoder(loader, "demo.Price").encode(ok));
    }

    @Test
    void dateFieldReadsFromIsoText() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Event { on: Date }
                """);
        Object ok = decoder(loader, "demo.Event").decode(Raw.text("2026-07-15"));
        assertTrue(!(ok instanceof DecodeFailure), "a Date decodes from an ISO8601 text value");
        assertEquals(Raw.text("2026-07-15"), encoder(loader, "demo.Event").encode(ok));

        assertTrue(decoder(loader, "demo.Event").decode(Raw.text("not-a-date")) instanceof DecodeFailure);
    }

    @Test
    void dateTimeFieldReadsFromIsoText() throws Exception {
        BytesClassLoader loader = loader("""
                module demo

                data Stamp { at: DateTime }
                """);
        Object ok = decoder(loader, "demo.Stamp").decode(Raw.text("2026-07-15T10:30:45"));
        assertTrue(!(ok instanceof DecodeFailure), "a DateTime decodes from an ISO8601 text value");
        assertEquals(Raw.text("2026-07-15T10:30:45"), encoder(loader, "demo.Stamp").encode(ok));
    }
}
