package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.query.Compilation;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.Value;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The types whose values can be written out, which is what makes a rule saying what a position is
 * not into a rule saying what it is.
 *
 * <p>{@code /= true} beside {@code /= false} leaves nothing, and {@code /= "A"} beside
 * {@code /= "B"} leaves nearly everything. The difference is not the shape of the rules — it is
 * whether the values are something this can count out and take away from.
 *
 * <p>Asked of the type and not of what carries it. Two questions come apart here: a boolean has two
 * values and no order, an enumeration has both, and a string has an order and no end of values. A
 * reading that answered this with the carrier said a boolean was a position nothing could be
 * counted of.
 */
class WhichTypesHaveValuesThatCanBeWrittenOutTest {

    private static Symbols symbolsOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream)
                .map(each -> each.diagnostic().code())
                .toList(), "the model this reads has to be one somebody could write");
        return Scopes.derived(compilation.db(), "demo").value();
    }

    private static List<Value> of(String source, String named) {
        Symbols symbols = symbolsOf(source);
        return ValueUniverse.of(
                Type.ref(TypeSymbols.declared(new TypeKey(symbols.module(), named))), symbols,
                ScopedDeclarations.kindsOf(symbols), ScopedDeclarations.of(symbols));
    }

    @Test
    void aBooleanIsTwoValues() {
        assertEquals(List.of(Value.truth(false), Value.truth(true)),
                ValueUniverse.of(Type.BOOL, symbolsOf("module demo\n\ndata U\n"),
                        DeclarationKinds.NONE, PublishedDeclarations.NONE));
    }

    /** And a name wrapped round one is the same two: wearing a name is not being another type. */
    @Test
    void aNameWrappedRoundABooleanIsTheSameTwo() {
        assertEquals(List.of(Value.truth(false), Value.truth(true)), of("""
                module demo

                data Flag = Bool
                """, "Flag"));
    }

    /** An enumeration is its cases, in the order the model writes them. */
    @Test
    void anEnumerationIsItsCasesInTheOrderTheyAreWritten() {
        Symbols symbols = symbolsOf("""
                module demo

                data Red
                data Green
                data Blue

                data Colour = Red | Green | Blue
                """);
        assertEquals(
                List.of(named(symbols, "Red"), named(symbols, "Green"), named(symbols, "Blue")),
                ValueUniverse.of(
                        Type.ref(TypeSymbols.declared(new TypeKey(symbols.module(), "Colour"))),
                        symbols, ScopedDeclarations.kindsOf(symbols),
                        ScopedDeclarations.of(symbols)));
    }

    private static Value named(Symbols symbols, String data) {
        return Value.of(TypeSymbols.declared(new TypeKey(symbols.module(), data)));
    }

    /**
     * A string is not written out, and neither is a number.
     *
     * <p>An {@code Int} between two ends is finitely many values and is still not this: what a rule
     * leaves it is read as an interval, and writing the values out as well would give one position
     * two readings to be kept agreeing.
     */
    @Test
    void thePositionsWithNoValuesToWriteOutAreAnsweredNothing() {
        Symbols symbols = symbolsOf("module demo\n\ndata U\n");
        assertNull(ValueUniverse.of(Type.STRING, symbols, DeclarationKinds.NONE, PublishedDeclarations.NONE));
        assertNull(ValueUniverse.of(Type.INT, symbols, DeclarationKinds.NONE, PublishedDeclarations.NONE));
        assertNull(ValueUniverse.of(Type.DECIMAL, symbols, DeclarationKinds.NONE, PublishedDeclarations.NONE));
        assertNull(ValueUniverse.of(Type.DATE, symbols, DeclarationKinds.NONE, PublishedDeclarations.NONE));
    }

    /** A sum whose cases hold something is not an enumeration, so its values are not written out. */
    @Test
    void aSumOfCasesThatHoldSomethingIsNotWrittenOut() {
        assertNull(of("""
                module demo

                data Leaf = { n: Int }
                data Stop

                data Shape = Leaf | Stop
                """, "Shape"));
    }

    /** And a record is not one either. */
    @Test
    void aRecordIsNotWrittenOut() {
        assertNull(of("""
                module demo

                data Point = { x: Int, y: Int }
                """, "Point"));
    }
}
