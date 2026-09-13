package souther.compiler.examples;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.CheckedDeclarations;
import souther.compiler.check.ScopedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.observe.Alignment;
import souther.compiler.observe.Asserted;
import souther.compiler.observe.Comparisons;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.Position;
import souther.compiler.observe.ValueTypes;
import souther.compiler.types.Type;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pairs of an answer's map are shown where the row read against it put them.
 *
 * <p>A map is a value with no order, and what came out of a run holds its pairs in whatever the
 * runtime's table walks — a fact about the keys' numbers. Written out that way beside a row the
 * author did write in an order, a reader is handed two sequences that agree about nothing, one of
 * them about no part of the program.
 *
 * <p><b>Which of the answer's pairs is which of the row's is not decided here.</b> The comparison
 * settles it and the rendering is handed the correspondence, so a key the comparison matched is a
 * key the report puts in place — including one written two ways, which is what a decimal is.
 *
 * <p>Held over the correspondence the comparison actually makes, and not over one this test builds
 * for it: what is being held is that the two answer alike, which a fixture pairing them itself
 * could not tell.
 */
class AnAnswersPairsAreShownWhereTheRowPutThemTest {

    private static final Type OF_TEXT = Type.map(Type.STRING);
    private static final Type OF_DECIMAL = Type.map(Type.DECIMAL, Type.STRING);
    private static final Type OF_MAPS = Type.map(Type.STRING, Type.map(Type.STRING));
    private static final Type SET_OF_MAPS = Type.set(Type.map(Type.STRING));

    private static ValueRendering rendering() {
        Symbols symbols = Symbols.none(DefaultStdlib.get());
        return new ValueRendering(new NeutralForm(symbols, ScopedDeclarations.of(symbols),
                ScopedDeclarations.kindsOf(symbols),
                FieldTypes.over(new CheckedDeclarations(_ -> null, _ -> null))));
    }

    private static ValueTypes types() {
        return ValueTypes.over(FieldTypes.over(new CheckedDeclarations(_ -> null, _ -> null)));
    }

    /** What a reader is shown of {@code answered}, beside a row that stated {@code wrote}. */
    private static String shown(ObservedValue answered, Asserted wrote, Type position) {
        Alignment alignment =
                Comparisons.alignment(wrote, answered, types(), Position.at(position));
        return rendering().show(answered, position, alignment);
    }

    private static ObservedValue text(String s) {
        return new ObservedValue.Text(s);
    }

    private static ObservedValue.Mapping answered(String... keys) {
        List<ObservedValue.Entry> out = new ArrayList<>();
        for (String key : keys) {
            out.add(new ObservedValue.Entry(text(key), text(key + "!")));
        }
        return new ObservedValue.Mapping(out);
    }

    private static Asserted.Entry entry(BigDecimal key, String value) {
        return new Asserted.Entry(new Asserted.Value(new ObservedValue.Decimal(key)),
                new Asserted.Value(text(value)));
    }

    private static ObservedValue.Entry pair(BigDecimal key, String value) {
        return new ObservedValue.Entry(new ObservedValue.Decimal(key), text(value));
    }

    private static Asserted wrote(String... keys) {
        List<Asserted.Entry> out = new ArrayList<>();
        for (String key : keys) {
            out.add(new Asserted.Entry(new Asserted.Value(text(key)),
                    new Asserted.Value(text(key + "!"))));
        }
        return new Asserted.Entries(false, out);
    }

    /**
     * The control. An answer whose pairs came out in the row's order already would be shown that way
     * by a rendering that decides nothing, so the claim below would hold of the one this is about.
     */
    @Test
    void theAnswersOwnOrderIsNotTheRowsToBeginWith() {
        Set<String> walked = new LinkedHashSet<>();
        walked.add(rendering().show(answered("b", "a"), OF_TEXT));
        walked.add(rendering().show(answered("a", "b"), OF_TEXT));

        assertTrue(walked.size() > 1,
                () -> "the two answers are written out alike before anything is read against them,"
                        + " so what is shown below would be shown anyway: " + walked);
    }

    /** The row's order, and not the one the answer happens to hold its pairs in. */
    @Test
    void thePairsComeInTheOrderTheRowWroteThem() {
        assertEquals("[ (\"b\", \"b!\"), (\"a\", \"a!\") ]",
                shown(answered("a", "b"), wrote("b", "a"), OF_TEXT));
    }

    /** However the answer holds them. */
    @Test
    void theOrderTheAnswerHoldsThemInDecidesNothing() {
        assertEquals(shown(answered("a", "b"), wrote("b", "a"), OF_TEXT),
                shown(answered("b", "a"), wrote("b", "a"), OF_TEXT));
    }

    /**
     * A key the row wrote another way is still the key the row wrote.
     *
     * <p>A decimal is the amount it stands for, so {@code 1.0} and {@code 1.00} are one key and the
     * comparison pairs them. Found again from what the two are written as, they are two, and the
     * answer's pair would be shown as one no row mentions — with the row's own pair nowhere.
     */
    @Test
    void aKeyWrittenTwoWaysIsOneKey() {
        // The key written two ways is written first, and two more follow it, so that failing to
        // find it is not written the same as finding it: unplaced pairs go after the placed ones.
        Asserted row = new Asserted.Entries(false, List.of(
                entry(new BigDecimal("1.0"), "one"),
                entry(new BigDecimal("2"), "two"),
                entry(new BigDecimal("3"), "three")));
        ObservedValue answer = new ObservedValue.Mapping(List.of(
                pair(new BigDecimal("3"), "three"),
                pair(new BigDecimal("1.00"), "one"),
                pair(new BigDecimal("2"), "two")));

        assertEquals("[ (1.00, \"one\"), (2, \"two\"), (3, \"three\") ]",
                shown(answer, row, OF_DECIMAL));
    }

    /**
     * An element of a set is read against the one the comparison found for it, and not the one that
     * happens to stand where it does.
     *
     * <p>A set has no order either, so the element of the answer standing in a place is not the
     * element of the row standing in that place. Paired by where they stand, the map inside one
     * element would be put in the order of a map inside another — a report writing an answer in the
     * shape of a value it is not about.
     */
    @Test
    void anElementOfASetIsReadAgainstTheOneItStandsFor() {
        // Each row element writes its pairs the other way round from the alphabet, so that being
        // paired with the wrong one is not written the same as being paired with the right one:
        // a map nothing states is written out in order, which is what the alphabet would give.
        Asserted row = new Asserted.Elements(Asserted.Container.SET, List.of(
                wrote("y", "x"), wrote("q", "p")));
        ObservedValue answer = new ObservedValue.Sequence(List.of(
                answered("p", "q"), answered("x", "y")));

        assertEquals("Set.fromList([ [ (\"q\", \"q!\"), (\"p\", \"p!\") ],"
                        + " [ (\"y\", \"y!\"), (\"x\", \"x!\") ] ])",
                shown(answer, row, SET_OF_MAPS));
    }

    /**
     * A pair the row did not write follows the ones it did, all the way down.
     *
     * <p>Nothing of the author's says where it goes, so it is written in the one form two runs of it
     * agree on — and a map it holds of its own is in that form too. Left as the run's table walked
     * it, the number is back in the report one level below where it was taken out.
     */
    @Test
    void aPairTheRowDidNotWriteIsWrittenTheOneWayAllTheWayDown() {
        Asserted row = new Asserted.Entries(false, List.of(
                new Asserted.Entry(new Asserted.Value(text("kept")), wrote("a", "b"))));
        ObservedValue answer = new ObservedValue.Mapping(List.of(
                new ObservedValue.Entry(text("extra"), answered("z", "y")),
                new ObservedValue.Entry(text("kept"), answered("b", "a"))));

        assertEquals("[ (\"kept\", [ (\"a\", \"a!\"), (\"b\", \"b!\") ]),"
                        + " (\"extra\", [ (\"y\", \"y!\"), (\"z\", \"z!\") ]) ]",
                shown(answer, row, OF_MAPS));
    }

    /** And with no row read against it, the answer is written as it stands. */
    @Test
    void withNothingWrittenBesideItTheAnswerIsShownAsItIs() {
        assertEquals("[ (\"b\", \"b!\"), (\"a\", \"a!\") ]",
                rendering().show(answered("b", "a"), OF_TEXT));
    }
}
