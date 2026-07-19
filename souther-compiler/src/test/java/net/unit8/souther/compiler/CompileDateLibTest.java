package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Date / DateTime arithmetic standard library ([#stdlib-date], [#stdlib-datetime]): pure
 *  calendar computation in the domain, not an injected behavior. */
class CompileDateLibTest {

    @Test
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

        Object in = Codecs.decoded(loader, "demo.In", Map.of(
                "start", LocalDate.parse("2026-07-19"),
                "at", LocalDateTime.parse("2026-07-19T10:30:45")));
        Object out = Codecs.apply(
                loader.loadClass("demo.Run").getConstructor().newInstance(), in);

        Map<?, ?> m = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
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
