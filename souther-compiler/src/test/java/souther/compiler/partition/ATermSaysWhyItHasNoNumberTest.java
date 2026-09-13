package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Carrier;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.ObservedValue;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A term with no number for a value says which of the two happened.
 *
 * <p>They license different sentences and are not shades of one another. An observation the run
 * could not read leaves a measure undecided: the row that was cut short may be the row at the value,
 * and calling it absent would report a gap that nothing established. A value that was read and is
 * not a number of this term is neither — it is a value this term does not hold, and calling that
 * unreadable would report a term that does not fit its position as a row nobody could read.
 *
 * <p>Fixed here rather than at each reader. A class asks this question and so does a boundary, and
 * folding the two answers into a number-or-null is how the second of them came to say "undecided"
 * to both.
 */
class ATermSaysWhyItHasNoNumberTest {

    private static final NumericTerm.FromOnePosition VALUE =
            new NumericTerm.ValueOf(TermPath.of("x"));
    private static final NumericTerm.FromOnePosition LENGTH = NumericTerm.TakenOf.of(
            ValueName.Stdlib.operation("String", "length"), TermPath.of("x"),
            souther.compiler.types.Type.STRING,
            souther.compiler.check.NewtypeInners.NONE,
            souther.compiler.check.Symbols.none(souther.compiler.DefaultStdlib.get()));
    private static final TypeSymbol.AtModule WRAPPER = TypeSymbols.declared(new TypeKey("example", "Wrapped"));

    /** The two orders a whole-number position stands on, which are one order. */
    private static final souther.compiler.inputs.TermOrders AS_A_NUMBER =
            souther.compiler.inputs.TermOrdersFixtures.itself(VALUE, Carrier.WHOLE);

    /** And the two a length stands on: the string is read as text, the count as a whole number. */
    private static final souther.compiler.inputs.TermOrders AS_A_LENGTH =
            souther.compiler.inputs.TermOrdersFixtures.orders(LENGTH, Carrier.TEXT, Carrier.WHOLE);

    @Test
    void anObservationThatDidNotArriveIsMissing() {
        assertEquals(Incompleteness.Code.VALUE_TRUNCATED,
                ((NumericTerm.Reading.Missing) AS_A_NUMBER.read(new ObservedValue.Truncated())).code());
        assertInstanceOf(NumericTerm.Reading.Missing.class,
                AS_A_LENGTH.read(new ObservedValue.Truncated()));
        assertInstanceOf(NumericTerm.Reading.Missing.class,
                AS_A_NUMBER.read(new ObservedValue.Unknown("gone")));
    }

    /** One layer in, which is where a newtype puts it. The construction reads perfectly well and the
     * number is what is missing, so a walk that only looked at the outside would call this a value
     * the term does not hold. */
    @Test
    void anObservationCutShortInsideANewtypeIsMissingToo() {
        ObservedValue wrapped = new ObservedValue.Constructed(WRAPPER,
                Map.of("value", new ObservedValue.Truncated()));
        assertInstanceOf(NumericTerm.Reading.Missing.class, AS_A_NUMBER.read(wrapped));
        assertInstanceOf(NumericTerm.Reading.Missing.class, AS_A_LENGTH.read(wrapped));
    }

    /** A value that was read, and that this term is not a number of. Not the same answer. */
    @Test
    void aValueThatWasReadAndIsNotThisTermsNumberIsNotMissing() {
        assertInstanceOf(NumericTerm.Reading.NotNumber.class,
                AS_A_NUMBER.read(new ObservedValue.Text("abc")));
        assertInstanceOf(NumericTerm.Reading.NotNumber.class,
                AS_A_LENGTH.read(new ObservedValue.Integer(3)));
        assertInstanceOf(NumericTerm.Reading.NotNumber.class,
                AS_A_LENGTH.read(new ObservedValue.Bool(true)));
    }

    /** And what each term does read, so that none of the above passes by reading nothing at all. */
    @Test
    void eachTermReadsItsOwnNumber() {
        assertEquals(Count.of(3),
                ((NumericTerm.Reading.Number) AS_A_NUMBER.read(new ObservedValue.Integer(3))).value());
        assertEquals(Count.of(3),
                ((NumericTerm.Reading.Number) AS_A_LENGTH.read(new ObservedValue.Text("abc"))).value());
        assertEquals(Count.of(3),
                ((NumericTerm.Reading.Number) AS_A_LENGTH.read(new ObservedValue.Constructed(
                        WRAPPER, Map.of("value", new ObservedValue.Text("abc"))))).value());
    }

    /** A string counts in code points, as `Strings.length` does. Counted in UTF-16 units this is 2,
     * and a boundary at 1 would be a row the rule that drew it never asked for. */
    @Test
    void aStringIsCountedTheWayTheLanguageCountsIt() {
        assertEquals(Count.of(1),
                ((NumericTerm.Reading.Number) AS_A_LENGTH.read(new ObservedValue.Text("😀")))
                        .value());
    }
}
