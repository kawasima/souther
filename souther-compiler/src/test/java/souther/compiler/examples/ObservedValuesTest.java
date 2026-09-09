package souther.compiler.examples;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.CheckedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.Limits;
import souther.compiler.observe.ObservedValue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reading a decoded value into the form the compiler owns: the scalars it recognises, the limits that
 * stop it, and what it says when it cannot read something. The cases that need generated classes — a
 * data, a unit case, an absent optional — are exercised through the rows that build them.
 */
class ObservedValuesTest {

    private static final Limits WIDE = new Limits(16, 10_000, 64, 1024);

    private static ObservedValue observe(Object live, Limits limits) {
        Symbols symbols = Symbols.none(DefaultStdlib.get());
        // No module is being read, so nothing here declares a data whose fields could be asked for.
        return ObservedValues.of(live, symbols,
                new NeutralForm(symbols,
                        FieldTypes.over(new CheckedDeclarations(_ -> null, _ -> null))), limits);
    }

    private static ObservedValue observe(Object live) {
        return observe(live, WIDE);
    }

    @Test
    void aScalarIsReadAsItself() {
        assertEquals(new ObservedValue.Integer(7L), observe(7L));
        assertEquals(new ObservedValue.Integer(7L), observe(7));
        assertEquals(new ObservedValue.Bool(true), observe(true));
        assertEquals(new ObservedValue.Decimal(new BigDecimal("1.50")), observe(new BigDecimal("1.50")));
        assertEquals(new ObservedValue.Text("hello"), observe("hello"));
    }

    /**
     * A temporal is a temporal and not the text of one.
     *
     * <p>Spelled to the second where the type is held to it, which is what writes the value
     * everywhere else. {@code toString} drops the seconds at zero, and an observation carrying
     * {@code 09:00} sat in the same report as a line drawn at the same value and named
     * {@code 09:00:00}.
     */
    @Test
    void aTemporalIsNotText() {
        assertEquals(new ObservedValue.Temporal("2026-07-25"), observe(LocalDate.parse("2026-07-25")));
        assertEquals(new ObservedValue.Temporal("09:00:00"),
                observe(java.time.LocalTime.parse("09:00")));
        assertEquals(new ObservedValue.Temporal("2026-07-25T09:00:00"),
                observe(LocalDateTime.parse("2026-07-25T09:00")));
    }

    @Test
    void aListIsASequenceAndAMapIsAMapping() {
        assertEquals(new ObservedValue.Sequence(List.of(new ObservedValue.Integer(1L),
                        new ObservedValue.Integer(2L))),
                observe(List.of(1L, 2L)));

        Map<Object, Object> counts = new LinkedHashMap<>();
        counts.put("bug", 2L);
        assertEquals(new ObservedValue.Mapping(List.of(
                        new ObservedValue.Entry(new ObservedValue.Text("bug"),
                                new ObservedValue.Integer(2L)))),
                observe(counts));
    }

    @Test
    void aNullIsUnknownRatherThanAbsent() {
        assertInstanceOf(ObservedValue.Unknown.class, observe(null));
    }

    /** The content goes with it, which is the whole reason the walk is bounded. */
    @Test
    void textPastTheLimitDoesNotKeepItsContent() {
        String long_ = "x".repeat(50);

        assertInstanceOf(ObservedValue.Truncated.class,
                observe(long_, new Limits(16, 10_000, 64, 10)));
    }

    /** Whole, because a prefix read back is a collection nobody wrote and nothing downstream could
     * tell it from one somebody did. */
    @Test
    void aCollectionPastTheLimitIsDroppedWhole() {
        List<Long> many = new ArrayList<>();
        for (long i = 0; i < 20; i++) {
            many.add(i);
        }
        assertInstanceOf(ObservedValue.Truncated.class,
                observe(many, new Limits(16, 10_000, 4, 1024)));

        Map<Object, Object> big = new LinkedHashMap<>();
        for (long i = 0; i < 20; i++) {
            big.put("k" + i, i);
        }
        assertInstanceOf(ObservedValue.Truncated.class,
                observe(big, new Limits(16, 10_000, 4, 1024)));
    }

    @Test
    void tooDeepBecomesTruncatedRatherThanRecursingOn() {
        Object nested = List.of(List.of(List.of(List.of(1L))));
        ObservedValue.Sequence outer = assertInstanceOf(ObservedValue.Sequence.class,
                observe(nested, new Limits(2, 10_000, 64, 1024)));
        ObservedValue.Sequence second = assertInstanceOf(ObservedValue.Sequence.class,
                outer.elements().get(0));
        ObservedValue.Sequence third = assertInstanceOf(ObservedValue.Sequence.class,
                second.elements().get(0));
        assertInstanceOf(ObservedValue.Truncated.class, third.elements().get(0));
    }

    @Test
    void oneNodeBudgetCoversTheWholeWalk() {
        ObservedValue.Sequence observed = assertInstanceOf(ObservedValue.Sequence.class,
                observe(List.of(1L, 2L, 3L, 4L), new Limits(16, 3, 64, 1024)));
        assertEquals(new ObservedValue.Integer(1L), observed.elements().get(0));
        assertInstanceOf(ObservedValue.Truncated.class, observed.elements().get(2));
    }

    /** Being a record is not being immutable: what an answer reported must not change under it. */
    @Test
    void anObservationDoesNotShareTheCollectionItWasGiven() {
        List<ObservedValue> given = new ArrayList<>(List.of(new ObservedValue.Integer(1L)));
        ObservedValue.Sequence sequence = new ObservedValue.Sequence(given);
        given.add(new ObservedValue.Integer(2L));
        assertEquals(1, sequence.elements().size());
        assertThrows(UnsupportedOperationException.class,
                () -> sequence.elements().add(new ObservedValue.Integer(3L)));

        Map<String, ObservedValue> fields = ObservedValue.fields();
        fields.put("a", new ObservedValue.Integer(1L));
        ObservedValue.Constructed constructed =
                new ObservedValue.Constructed(souther.compiler.types.TypeSymbols.declared(new souther.compiler.types.TypeKey("m", "T")), fields);
        fields.put("b", new ObservedValue.Integer(2L));
        assertEquals(1, constructed.fields().size());
    }
}
