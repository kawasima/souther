package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.DateTimes;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Instants;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.Text;
import souther.compiler.numeric.Times;
import souther.compiler.observe.ObservedValue;
import souther.compiler.query.Compilation;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Which types carry a value, asked once and answered for both directions at once.
 *
 * <p>Reading a rule and writing a value at what it bounds were two types for a while, because a
 * {@code Time} and an {@code Instant} could be read and could not be written: each had an
 * order-preserving count and neither had the way back. The way back is one conversion each, and
 * leaving them out cost a rule written over either type its line (issue #846). With them written
 * every ordered type answers both questions, so there is one table again and this is it.
 *
 * <p>The round trip is the property that makes the pair a carrier rather than two functions: a
 * count written as a value and read back is the count it started as. A conversion nobody checks
 * that way is what {@link Times} declined to write while nothing called it.
 */
class EveryOrderedTypeCarriesAValueBothWaysTest {

    private static final String MODEL = """
            module example.scales

            data Red
            data Green
            data Blue
            data Colour = Red | Green | Blue
            """;

    private static Symbols symbols() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        return Scopes.derived(compilation.db(), compilation.modules().get(0)).value();
    }

    private static Type colour() {
        return Type.ref(TypeSymbols.declared(new TypeKey("example.scales", "Colour")));
    }

    /** Every ordered type this language has. */
    private static List<Type> ordered() {
        return List.of(Type.INT, Type.DECIMAL, Type.DATE, Type.DATETIME, Type.TIME, Type.INSTANT,
                Type.STRING, colour());
    }

    /**
     * Every ordered type is one a value can be written back at.
     *
     * <p>A {@code Time} and an {@code Instant} are the two this closed. Each was ordered and read,
     * and a rule written over either came back saying no line could be drawn on values the language
     * orders — a fact about the compiler printed as a fact about the model.
     */
    @Test
    void everyOrderedTypeCarriesAValue() {
        Symbols symbols = symbols();

        for (Type each : ordered()) {
            assertNotNull(Carrier.ofValue(each, ScopedDeclarations.wrapsOf(symbols), symbols, ScopedDeclarations.kindsOf(symbols), ScopedDeclarations.of(symbols)), each + " carries a value");
        }

        assertNull(Carrier.ofValue(Type.BOOL, ScopedDeclarations.wrapsOf(symbols), symbols, ScopedDeclarations.kindsOf(symbols), ScopedDeclarations.of(symbols)), "a `Bool` is not ordered");
        assertNull(Carrier.ofValue(Type.RAW, ScopedDeclarations.wrapsOf(symbols), symbols, ScopedDeclarations.kindsOf(symbols), ScopedDeclarations.of(symbols)), "and neither is a `Raw`");
    }

    /**
     * A count written as a value and read back is the count it started as.
     *
     * <p>What makes the two conversions one carrier. Asked at the ends of each order and at a count
     * inside it, because an end is where a writer that floors or overflows gives itself away.
     */
    @Test
    void aCountWrittenAsAValueIsReadBackAsItself() {
        Symbols symbols = symbols();

        for (Type each : ordered()) {
            Carrier carrier = Carrier.ofValue(each, ScopedDeclarations.wrapsOf(symbols), symbols, ScopedDeclarations.kindsOf(symbols), ScopedDeclarations.of(symbols));
            for (Place count : places(carrier)) {
                ObservedValue written = carrier.valueOf(count);
                assertEquals(count, carrier.placeOf(written),
                        each + ": " + carrier.written(count) + " is read back as its own count");
            }
        }
    }

    /** The ends of an order and a place inside it, as places the carrier holds. */
    private static List<Place> places(Carrier carrier) {
        OrderedInterval extent = carrier.extent();
        List<Place> out = new java.util.ArrayList<>();
        for (Endpoint end : java.util.Arrays.asList(extent.low(), extent.high())) {
            if (end != null && end.inclusive()) {
                out.add(end.at());
            }
        }
        Place inside = carrier.somethingInside(extent.low(), extent.high());
        if (inside != null) {
            out.add(inside);
        }
        assertFalse(out.isEmpty(), "every order offers a place of its own");
        return out;
    }

    /**
     * Where each order stops, which is the third thing reading a rule needs.
     *
     * <p>Not derivable from the spacing and not the same question. A rule stating an end below the
     * least value of an order admits nothing, and a reading starting every position at an unbounded
     * range has no way to find that out: {@code value < ""} leaves a range open below, which is a
     * range holding everything under a string that has nothing under it.
     */
    @Test
    void everyCarrierSaysWhereItsOrderStops() {
        Symbols symbols = symbols();
        Map<Type, OrderedInterval> expected = new LinkedHashMap<>();
        expected.put(Type.INT, between(Count.of(Long.MIN_VALUE), Count.of(Long.MAX_VALUE)));
        expected.put(Type.DECIMAL, new OrderedInterval(null, null));
        expected.put(Type.DATE, between(Count.of(LocalDate.MIN.toEpochDay()),
                Count.of(LocalDate.MAX.toEpochDay())));
        expected.put(Type.DATETIME, between(DateTimes.MIN, DateTimes.MAX));
        expected.put(Type.STRING, new OrderedInterval(Endpoint.inclusive(Text.of("")), null));
        expected.put(Type.TIME, between(Times.MIN, Times.MAX));
        expected.put(Type.INSTANT, between(Instants.MIN, Instants.MAX));
        expected.put(colour(), between(Count.of(0), Count.of(2)));

        expected.forEach((type, extent) -> assertEquals(extent,
                Carrier.ofValue(type, ScopedDeclarations.wrapsOf(symbols), symbols, ScopedDeclarations.kindsOf(symbols), ScopedDeclarations.of(symbols)).extent(), "where " + type + " stops"));
    }

    /**
     * A temporal is written the way a model writes one.
     *
     * <p>Spelled to the second for a time of day, as a date-time is, so a line printed at one is
     * text the author could have written. A moment keeps what it was given, which is the sub-second
     * reading a {@code Time} refuses (spec §an-instant-carries-what-a-timestamp-said).
     */
    @Test
    void aTemporalIsWrittenTheWayAModelWritesOne() {
        Symbols symbols = symbols();

        Carrier time = Carrier.ofValue(Type.TIME, ScopedDeclarations.wrapsOf(symbols), symbols, ScopedDeclarations.kindsOf(symbols), ScopedDeclarations.of(symbols));
        assertEquals("16:00:00", time.written(Times.secondOf("16:00:00")));
        assertEquals("00:00:00", time.written(Times.MIN));
        assertEquals("23:59:59", time.written(Times.MAX));

        Carrier moment = Carrier.ofValue(Type.INSTANT, ScopedDeclarations.wrapsOf(symbols), symbols, ScopedDeclarations.kindsOf(symbols), ScopedDeclarations.of(symbols));
        assertEquals("2026-08-01T00:00:00Z",
                moment.written(Instants.nanoOf("2026-08-01T00:00:00Z")));
        assertEquals("2026-07-31T23:59:59.999999999Z",
                moment.written(Instants.nanoOf("2026-08-01T00:00:00Z").minus(1)));
        // Before the epoch, where a count divided towards zero lands in the second after the one
        // the moment is in. The epoch itself minus a nanosecond is the shortest way to reach it.
        assertEquals("1969-12-31T23:59:59.999999999Z",
                moment.written(Instants.nanoOf("1970-01-01T00:00:00Z").minus(1)));
        assertEquals("1969-12-31T23:59:58.999999999Z",
                moment.written(Instants.nanoOf("1970-01-01T00:00:00Z").minus(1_000_000_001L)));
    }

    /** A count no value of the order stands for is not one of its places. */
    @Test
    void aCountTheOrderDoesNotReachIsNoPlaceOfIts() {
        Symbols symbols = symbols();

        Carrier time = Carrier.ofValue(Type.TIME, ScopedDeclarations.wrapsOf(symbols), symbols, ScopedDeclarations.kindsOf(symbols), ScopedDeclarations.of(symbols));
        assertNull(time.onTheGrid(Times.MAX.plus(1)), "a day runs out at its last second");
        assertNull(time.onTheGrid(Count.of(new java.math.BigDecimal("0.5"))),
                "and half a second is a number and no time of day");

        Carrier moment = Carrier.ofValue(Type.INSTANT, ScopedDeclarations.wrapsOf(symbols), symbols, ScopedDeclarations.kindsOf(symbols), ScopedDeclarations.of(symbols));
        assertNull(moment.onTheGrid(Instants.MAX.plus(1)), "the timeline stops where it stops");
    }

    private static OrderedInterval between(Place low, Place high) {
        return new OrderedInterval(Endpoint.inclusive(low), Endpoint.inclusive(high));
    }
}
