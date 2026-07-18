package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ordering ({@code <} {@code <=} {@code >} {@code >=}) on the JVM-Comparable primitives beyond Int:
 *  String, Decimal, Date, DateTime (spec §primitives). Souther orders these where Elm does not,
 *  because it rides the JVM rather than JavaScript. */
class CompileOrderingTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void decimalDateAndDateTimeOrdering() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data In = {
                    a: Decimal
                    b: Decimal
                    d1: Date
                    d2: Date
                    t1: DateTime
                    t2: DateTime
                }
                data Out = {
                    decEqByValue: Bool
                    decLt: Bool
                    dateBefore: Bool
                    timeAfter: Bool
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    decEqByValue: i.a >= i.b,
                    decLt: i.a < i.b,
                    dateBefore: i.d1 < i.d2,
                    timeAfter: i.t1 > i.t2
                }
                """), getClass().getClassLoader());

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) inDec.decode(Map.ofEntries(
                Map.entry("a", new BigDecimal("1.50")),   // equal to b by value, scale ignored
                Map.entry("b", new BigDecimal("1.5")),
                Map.entry("d1", LocalDate.parse("2026-01-01")),
                Map.entry("d2", LocalDate.parse("2026-07-18")),
                Map.entry("t1", LocalDateTime.parse("2026-07-18T10:00:00")),
                Map.entry("t2", LocalDateTime.parse("2026-07-18T09:00:00"))), Path.ROOT)).value();

        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Run").getConstructor().newInstance())
                .apply(in);
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        Map<?, ?> m = (Map<?, ?>) enc.encode(out);
        assertEquals(true, m.get("decEqByValue"), "1.50 >= 1.5 (scale ignored)");
        assertEquals(false, m.get("decLt"), "1.50 is not < 1.5");
        assertEquals(true, m.get("dateBefore"));
        assertEquals(true, m.get("timeAfter"));
    }

    @Test
    void sortRejectsANonOrderedElement() {
        // A list of data (or of a newtype) is not Comparable at runtime; reject it at compile time
        // rather than let it throw. The ordered field must be extracted first.
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                import List { sort }

                data 品目 = { コード: String }
                data In = { xs: List<品目> }
                data Out = { ys: List<品目> }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out { ys: sort(i.xs) }
                """));
        assertTrue(e.getMessage().contains("sort needs a list of ordered values"), e.getMessage());
    }
}
