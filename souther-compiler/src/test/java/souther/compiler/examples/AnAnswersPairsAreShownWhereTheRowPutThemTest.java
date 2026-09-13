package souther.compiler.examples;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.CheckedDeclarations;
import souther.compiler.check.ScopedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.observe.Asserted;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.ObservedValue;
import souther.compiler.types.Type;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pairs of an answer's map are shown where the row that is read against it put them.
 *
 * <p>A map is a value with no order, and what came out of a run holds its pairs in whatever the
 * runtime's table walks — a fact about the keys' numbers. Written out that way beside a row the
 * author did write in an order, a reader is handed two sequences that agree about nothing and one
 * of them is about no part of the program.
 *
 * <p><b>Held as a rule about showing two together.</b> Nothing here makes the value ordered: the
 * order comes from the row, reaches as far as the row does, and is the author's because they wrote
 * it. A pair the row does not write has no place of theirs and follows, written out the one way so
 * that two runs agree.
 */
class AnAnswersPairsAreShownWhereTheRowPutThemTest {

    private static final Type MAP = Type.map(Type.STRING);

    private static ValueRendering rendering() {
        Symbols symbols = Symbols.none(DefaultStdlib.get());
        return new ValueRendering(new NeutralForm(symbols, ScopedDeclarations.of(symbols),
                ScopedDeclarations.kindsOf(symbols),
                FieldTypes.over(new CheckedDeclarations(_ -> null, _ -> null))));
    }

    private static ObservedValue text(String s) {
        return new ObservedValue.Text(s);
    }

    /** An answer holding these pairs, in the order they are given. */
    private static ObservedValue answered(String... keys) {
        List<ObservedValue.Entry> out = new ArrayList<>();
        for (String key : keys) {
            out.add(new ObservedValue.Entry(text(key), text(key + "!")));
        }
        return new ObservedValue.Mapping(out);
    }

    /** A row writing these pairs, in the order they are given. */
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
     * by a renderer that decides nothing, so the claim below would hold of the one this is about.
     */
    @Test
    void theAnswersOwnOrderIsNotTheRowsToBeginWith() {
        Set<String> walked = new LinkedHashSet<>();
        walked.add(rendering().show(answered("b", "a"), MAP));
        walked.add(rendering().show(answered("a", "b"), MAP));

        assertTrue(walked.size() > 1,
                () -> "the two answers are written out alike before anything is read against them,"
                        + " so what is shown below would be shown anyway: " + walked);
    }

    /** The row's order, and not the one the answer happens to hold its pairs in. */
    @Test
    void thePairsComeInTheOrderTheRowWroteThem() {
        assertEquals("[ (\"b\", \"b!\"), (\"a\", \"a!\") ]",
                rendering().show(answered("a", "b"), MAP, wrote("b", "a")));
    }

    /** However the answer holds them. */
    @Test
    void theOrderTheAnswerHoldsThemInDecidesNothing() {
        assertEquals(rendering().show(answered("a", "b"), MAP, wrote("b", "a")),
                rendering().show(answered("b", "a"), MAP, wrote("b", "a")));
    }

    /**
     * A pair the row did not write follows the ones it did.
     *
     * <p>Nothing of the author's says where it goes, so it is not put among what they wrote — which
     * would be this compiler deciding a place and showing it as the row's — and it is written out
     * the one way, so that two runs of it agree.
     */
    @Test
    void aPairTheRowDidNotWriteFollowsTheOnesItDid() {
        assertEquals("[ (\"b\", \"b!\"), (\"a\", \"a!\"), (\"c\", \"c!\"), (\"d\", \"d!\") ]",
                rendering().show(answered("d", "a", "c", "b"), MAP, wrote("b", "a")));
    }

    /** And with no row read against it, the answer is written as it stands. */
    @Test
    void withNothingWrittenBesideItTheAnswerIsShownAsItIs() {
        assertEquals("[ (\"b\", \"b!\"), (\"a\", \"a!\") ]",
                rendering().show(answered("b", "a"), MAP));
    }
}
