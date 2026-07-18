package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Multi-module compilation with explicit imports and cyclic-import detection (spec 4, 22.11). */
class CompileModuleTest {

    private static final String EMPLOYEE = """
            module example.employee exposing (
                従業員ID
            )

            import String ( length )

            data 従業員ID = String
                invariant length(value) > 0
            """;

    private static final String TRIP = """
            module example.trip exposing (
                Trip
            )

            import example.employee (
                従業員ID
            )

            data Trip = { who: 従業員ID }
            """;

    @Test
    void crossModuleTypeResolvesAndRoundTrips() throws Exception {
        Map<String, byte[]> classes = Compiler.compileModules(List.of(EMPLOYEE, TRIP));
        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        // the imported 従業員ID class lives in the declaring module's package
        loader.loadClass("example.employee.従業員ID");

        Decoder tripDec = (Decoder) loader.loadClass("example.trip.Trip")
                .getMethod("decoder").invoke(null);
        Result r = tripDec.decode(Map.of("who", "e-1"), Path.ROOT);
        assertTrue(r instanceof Ok);

        Encoder enc = (Encoder) loader.loadClass("example.trip.Trip").getMethod("encoder").invoke(null);
        Map<?, ?> out = (Map<?, ?>) enc.encode(((Ok) r).value());
        assertEquals("e-1", out.get("who"));

        // the imported type's invariant still runs during cross-module decode
        assertTrue(tripDec.decode(Map.of("who", ""), Path.ROOT) instanceof Err);
    }

    @Test
    void cyclicImportIsE1501() {
        String a = """
                module m.a exposing ( A )
                import m.b ( B )
                data A = { b: B }
                """;
        String b = """
                module m.b exposing ( B )
                import m.a ( A )
                data B = String
                """;
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(a, b)));
        assertEquals("E1501", e.code());
    }
}
