package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Date / DateTime arithmetic standard library ([#stdlib-date], [#stdlib-datetime]): pure
 *  calendar computation in the domain, not an injected behavior. */
class CompileDateLibTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void dateAndDateTimeArithmeticInABehavior() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                import Date ( addDays, addMonths, addYears, daysBetween )
                import DateTime ( addHours, addMinutes, minutesBetween, toDate )

                data In = {
                    start: Date
                    , at: DateTime
                }
                data Out = {
                    plusDays: Date
                    , plusMonths: Date
                    , plusYears: Date
                    , gap: Int
                    , plusHours: DateTime
                    , plusMinutes: DateTime
                    , mins: Int
                    , onlyDate: Date
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = Out {
                    plusDays = addDays(3, i.start),
                    plusMonths = addMonths(1, i.start),
                    plusYears = addYears(1, i.start),
                    gap = daysBetween(i.start, addDays(10, i.start)),
                    plusHours = addHours(2, i.at),
                    plusMinutes = addMinutes(90, i.at),
                    mins = minutesBetween(i.at, addMinutes(90, i.at)),
                    onlyDate = toDate(i.at)
                }
                """), getClass().getClassLoader());

        Decoder inDec = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) inDec.decode(Map.of(
                "start", LocalDate.parse("2026-07-19"),
                "at", LocalDateTime.parse("2026-07-19T10:30:45")), Path.ROOT)).value();
        Object out = ((Behavior<Object, Object>) loader.loadClass("demo.Run")
                .getConstructor().newInstance()).apply(in);

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        Map<?, ?> m = (Map<?, ?>) enc.encode(out);
        assertEquals("2026-07-22", m.get("plusDays"));
        assertEquals("2026-08-19", m.get("plusMonths"));
        assertEquals("2027-07-19", m.get("plusYears"));
        assertEquals(10L, m.get("gap"));
        assertEquals("2026-07-19T12:30:45", m.get("plusHours"));
        assertEquals("2026-07-19T12:00:45", m.get("plusMinutes"));
        assertEquals(90L, m.get("mins"));
        assertEquals("2026-07-19", m.get("onlyDate"));
    }
}
