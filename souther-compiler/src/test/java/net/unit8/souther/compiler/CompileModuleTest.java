package net.unit8.souther.compiler;

import net.unit8.souther.runtime.DecodeFailure;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Multi-module compilation with explicit imports and cyclic-import detection (spec 4, 22.11). */
class CompileModuleTest {

    private static final String EMPLOYEE = """
            module example.employee exposing {
                従業員ID,
                従業員ID.decoder
            }

            data 従業員ID { value: String  invariant length(value) > 0 }
            """;

    private static final String TRIP = """
            module example.trip exposing {
                Trip,
                Trip.decoder
            }

            import example.employee {
                従業員ID
            }

            data Trip { who: 従業員ID }
            """;

    @Test
    void crossModuleTypeResolvesAndRoundTrips() throws Exception {
        Map<String, byte[]> classes = Compiler.compileModules(List.of(EMPLOYEE, TRIP));
        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());

        // the imported 従業員ID class lives in the declaring module's package
        loader.loadClass("example.employee.従業員ID");

        Decoder<?> tripDec = (Decoder<?>) loader.loadClass("example.trip.Trip")
                .getMethod("decoder").invoke(null);
        Object trip = tripDec.decode(Raw.object(Map.of("who", Raw.text("e-1"))));
        assertTrue(!(trip instanceof DecodeFailure));

        Encoder enc = (Encoder) loader.loadClass("example.trip.Trip").getMethod("encoder").invoke(null);
        Raw.ObjectValue out = (Raw.ObjectValue) enc.encode(trip);
        assertEquals(Raw.text("e-1"), out.value().get("who"));

        // the imported type's invariant still runs during cross-module decode
        assertTrue(tripDec.decode(Raw.object(Map.of("who", Raw.text("")))) instanceof DecodeFailure);
    }

    @Test
    void cyclicImportIsE1501() {
        String a = """
                module m.a exposing { A }
                import m.b { B }
                data A { b: B }
                """;
        String b = """
                module m.b exposing { B }
                import m.a { A }
                data B { value: String }
                """;
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(a, b)));
        assertEquals("E1501", e.code());
    }
}
