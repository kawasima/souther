package souther.compiler.examples;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.CheckedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.ObservedValue;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a mismatch writes the two sides as.
 *
 * <p>The encoder is not this. It writes a value as the representation it crosses a boundary in, and a
 * newtype's representation is the base it wraps — so a row writing {@code 1} where an {@code AmountN}
 * came out had both sides written {@code 1}, and the difference the row was reported for could not be
 * read in what it was shown. What is fixed here is that the name survives.
 */
class ValueRenderingTest {

    private static ValueRendering rendering() {
        Symbols symbols = Symbols.none(DefaultStdlib.get());
        // No module is being read, so nothing here declares a data whose fields could be asked for.
        return new ValueRendering(new NeutralForm(symbols,
                FieldTypes.over(new CheckedDeclarations(_ -> null, _ -> null))));
    }

    private static String show(ObservedValue v) {
        return rendering().show(v);
    }

    private static String type(ObservedValue v) {
        return rendering().typeShown(v);
    }

    private static ObservedValue n(long v) {
        return new ObservedValue.Integer(v);
    }

    @Test
    void aScalarIsWrittenAsARowWritesOne() {
        assertEquals("1", show(n(1)));
        assertEquals("\"a\"", show(new ObservedValue.Text("a")));
        assertEquals("true", show(new ObservedValue.Bool(true)));
        assertEquals("1.50", show(new ObservedValue.Decimal(new BigDecimal("1.50"))));
        assertEquals("None", show(new ObservedValue.Absent()));
    }

    @Test
    void aTemporalIsWrittenAsTheConstructionThatBuildsOne() {
        // So it is never read as the text that spells it, which is the difference a row writing a
        // date as a string is reported for.
        assertEquals("Date(\"2026-07-25\")", show(new ObservedValue.Temporal("2026-07-25")));
        assertEquals("DateTime(\"2026-07-25T09:00\")",
                show(new ObservedValue.Temporal("2026-07-25T09:00")));
        assertEquals("\"2026-07-25\"", show(new ObservedValue.Text("2026-07-25")));
    }

    @Test
    void aConstructionKeepsItsName() {
        Map<String, ObservedValue> fields = new LinkedHashMap<>();
        fields.put("total", n(1));
        assertEquals("Receipt { total = 1 }",
                show(new ObservedValue.Constructed(TypeSymbols.declared(new TypeKey("demo", "Receipt")), fields)));
        assertEquals("Ok", show(new ObservedValue.Unit(TypeSymbols.declared(new TypeKey("demo", "Ok")))));
    }

    @Test
    void aCollectionIsWrittenElementByElement() {
        assertEquals("[]", show(new ObservedValue.Sequence(List.of())));
        assertEquals("[ 1, 2 ]", show(new ObservedValue.Sequence(List.of(n(1), n(2)))));
        assertEquals("[ (1, \"a\") ]", show(new ObservedValue.Mapping(
                List.of(new ObservedValue.Entry(n(1), new ObservedValue.Text("a"))))));
    }

    @Test
    void whatAValueIsNamedIsWhatAMismatchSays() {
        assertEquals("Int", type(n(1)));
        assertEquals("String", type(new ObservedValue.Text("a")));
        assertEquals("Date", type(new ObservedValue.Temporal("2026-07-25")));
        assertEquals("DateTime", type(new ObservedValue.Temporal("2026-07-25T09:00")));
        assertEquals("Instant", type(new ObservedValue.Temporal("2026-07-25T09:00:00Z")));
        assertEquals("Time", type(new ObservedValue.Temporal("09:00")));
        assertEquals("AmountN", type(new ObservedValue.Constructed(TypeSymbols.declared(new TypeKey("demo", "AmountN")),
                Map.of("value", n(1)))));
        assertEquals("None", type(new ObservedValue.Absent()));
    }
}
