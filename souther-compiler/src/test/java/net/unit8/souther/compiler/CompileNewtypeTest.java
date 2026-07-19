package net.unit8.souther.compiler;

import net.unit8.raoh.Result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The explicit newtype form {@code data X = Y} (spec 8.7): a single implicit field {@code value}
 * of type {@code Y}, encoded as bare {@code Y} rather than an object, with an invariant allowed on
 * {@code value}. For now only a primitive inner type is derived.
 */
class CompileNewtypeTest {

    @Test
    void primitiveNewtypeRoundTripsBare() throws Exception {
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());

        // decodes from a bare integer (not an object), keeps it, and encodes back bare
        Object v = Codecs.decoded(loader, "demo.金額", 1500L);
        assertEquals("demo.金額", v.getClass().getName());
        assertEquals(1500L, Codecs.encode(loader, "demo.金額", v));
    }

    @Test
    void primitiveNewtypeInvariantIsChecked() throws Exception {
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        // -1 breaks value >= 0, so the decode fails (Raoh Err at the boundary, spec 9.4)
        Result<?> r = Codecs.decode(loader, "demo.金額", -1L);
        assertEquals("Err", r.getClass().getSimpleName());
    }

    @Test
    void stringNewtypeRoundTripsBare() throws Exception {
        String src = """
                module demo
                import String ( length )
                data 会員ID = String
                    invariant length(value) > 0
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());

        Object v = Codecs.decoded(loader, "demo.会員ID", "m-01");
        assertEquals("m-01", Codecs.encode(loader, "demo.会員ID", v));
    }

    @Test
    void bracedSingleFieldIsAnObjectNotBare() throws Exception {
        // spec 8.7: newtype-ness is decided by the `= Y` syntax, not the single-field shape. A
        // braced single field `{ value: T }` is always an object `{"value": ...}`, even though it
        // has one primitive field. Only `data X = T` is bare.
        String src = """
                module demo
                data Wrapped = { value: String }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());

        Object v = Codecs.decoded(loader, "demo.Wrapped", java.util.Map.of("value", "x"));
        Object out = Codecs.encode(loader, "demo.Wrapped", v);
        assertEquals(java.util.Map.of("value", "x"), out, "a braced single field is an object");
    }

    @Test
    void newtypeOverANamedDataDelegatesToItsCodec() throws Exception {
        // spec 8.7: `data X = Y` with a named-data Y wraps Y and reads/writes Y's representation.
        String src = """
                module demo
                data Inner = { a: Int, b: Int }
                data Wrap = Inner
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());

        Object v = Codecs.decoded(loader, "demo.Wrap", java.util.Map.of("a", 1L, "b", 2L));
        assertEquals("demo.Wrap", v.getClass().getName());
        assertEquals(java.util.Map.of("a", 1L, "b", 2L), Codecs.encode(loader, "demo.Wrap", v),
                "the newtype reads and writes the inner object's representation, not `{value: ...}`");
    }

    @Test
    void newtypeOverASumDelegatesToItsDiscriminator() throws Exception {
        // spec 8.7: when Y is a sum, X's representation is Y's discriminated form.
        String src = """
                module demo
                data 管理職
                data 一般社員
                data 役職 = 管理職 | 一般社員
                data 権限不足 = 役職
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());

        Object v = Codecs.decoded(loader, "demo.権限不足", java.util.Map.of("type", "管理職"));
        assertEquals("demo.権限不足", v.getClass().getName());
        assertEquals(java.util.Map.of("type", "管理職"), Codecs.encode(loader, "demo.権限不足", v));
    }
}
