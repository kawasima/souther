package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The explicit newtype form {@code data X = Y} (spec 8.7): a single implicit field {@code value}
 * of type {@code Y}, encoded as bare {@code Y} rather than an object, with an invariant allowed on
 * {@code value}. For now only a primitive inner type is derived.
 */
class CompileNewtypeTest {

    @Test
    @SuppressWarnings("unchecked")
    void primitiveNewtypeRoundTripsBare() throws Exception {
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Decoder dec = (Decoder) loader.loadClass("demo.金額").getMethod("decoder").invoke(null);
        Encoder enc = (Encoder) loader.loadClass("demo.金額").getMethod("encoder").invoke(null);

        // decodes from a bare integer (not an object), keeps it, and encodes back bare
        Object v = ((Ok) dec.decode(1500L, Path.ROOT)).value();
        assertEquals("demo.金額", v.getClass().getName());
        assertEquals(1500L, enc.encode(v));
    }

    @Test
    void primitiveNewtypeInvariantIsChecked() throws Exception {
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Decoder dec = (Decoder) loader.loadClass("demo.金額").getMethod("decoder").invoke(null);
        // -1 breaks value >= 0, so the decode fails (Raoh Err at the boundary, spec 9.4)
        Object r = dec.decode(-1L, Path.ROOT);
        assertEquals("Err", r.getClass().getSimpleName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void stringNewtypeRoundTripsBare() throws Exception {
        String src = """
                module demo
                import String ( length )
                data 会員ID = String
                    invariant length(value) > 0
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Decoder dec = (Decoder) loader.loadClass("demo.会員ID").getMethod("decoder").invoke(null);
        Encoder enc = (Encoder) loader.loadClass("demo.会員ID").getMethod("encoder").invoke(null);

        Object v = ((Ok) dec.decode("m-01", Path.ROOT)).value();
        assertEquals("m-01", enc.encode(v));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bracedSingleFieldIsAnObjectNotBare() throws Exception {
        // spec 8.7: newtype-ness is decided by the `= Y` syntax, not the single-field shape. A
        // braced single field `{ value: T }` is always an object `{"value": ...}`, even though it
        // has one primitive field. Only `data X = T` is bare.
        String src = """
                module demo
                data Wrapped = { value: String }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Decoder dec = (Decoder) loader.loadClass("demo.Wrapped").getMethod("decoder").invoke(null);
        Encoder enc = (Encoder) loader.loadClass("demo.Wrapped").getMethod("encoder").invoke(null);

        Object v = ((Ok) dec.decode(java.util.Map.of("value", "x"), Path.ROOT)).value();
        Object out = enc.encode(v);
        assertEquals(java.util.Map.of("value", "x"), out, "a braced single field is an object");
    }

    @Test
    @SuppressWarnings("unchecked")
    void newtypeOverANamedDataDelegatesToItsCodec() throws Exception {
        // spec 8.7: `data X = Y` with a named-data Y wraps Y and reads/writes Y's representation.
        String src = """
                module demo
                data Inner = { a: Int, b: Int }
                data Wrap = Inner
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Decoder dec = (Decoder) loader.loadClass("demo.Wrap").getMethod("decoder").invoke(null);
        Encoder enc = (Encoder) loader.loadClass("demo.Wrap").getMethod("encoder").invoke(null);

        Object v = ((Ok) dec.decode(java.util.Map.of("a", 1L, "b", 2L), Path.ROOT)).value();
        assertEquals("demo.Wrap", v.getClass().getName());
        assertEquals(java.util.Map.of("a", 1L, "b", 2L), enc.encode(v),
                "the newtype reads and writes the inner object's representation, not `{value: ...}`");
    }

    @Test
    @SuppressWarnings("unchecked")
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
        Decoder dec = (Decoder) loader.loadClass("demo.権限不足").getMethod("decoder").invoke(null);
        Encoder enc = (Encoder) loader.loadClass("demo.権限不足").getMethod("encoder").invoke(null);

        Object v = ((Ok) dec.decode(java.util.Map.of("type", "管理職"), Path.ROOT)).value();
        assertEquals("demo.権限不足", v.getClass().getName());
        assertEquals(java.util.Map.of("type", "管理職"), enc.encode(v));
    }
}
