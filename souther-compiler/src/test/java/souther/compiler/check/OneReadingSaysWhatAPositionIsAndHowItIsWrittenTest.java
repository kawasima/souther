package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.frontend.CstFrontend;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a position is, and what it is written as, read once and answered apart.
 *
 * <p>The two used to be worked out by whoever needed them, and they disagreed: a
 * {@code data StageN = Stage} was a sum to the reader that drew a line on it and an opaque name to
 * the reader that asked what it divides into. The first half of that is fixed by there being no
 * newtype case to stop at — a name is never a shape — and the second half by the names staying
 * reachable, since a row writes {@code StageN(Prospecting)} and nothing else would build.
 */
class OneReadingSaysWhatAPositionIsAndHowItIsWrittenTest {

    private static final String MODULE = """
            module demo

            data Prospecting
            data Qualified
            data Won
            data Stage = Prospecting | Qualified | Won

            data StageN = Stage
            data StageNN = StageN
            data Flag = Bool
            data Count = Int
            data Cyclic = Cyclic

            data Slot = { hour: Int, room: String }
            data SlotN = Slot
            """;

    private final Symbols symbols = Symbols.of(resolved(), DefaultStdlib.get());

    private static Hir.Module resolved() {
        Ast.Module parsed = CstFrontend.parse(MODULE);
        return Resolve.module(parsed, SyntaxSymbols.of(parsed, DefaultStdlib.get()));
    }

    private TypeView view(String name) {
        return TypeView.of(Type.ref(TypeSymbols.declared(new TypeKey(symbols.module(), name))),
                symbols, ScopedDeclarations.of(symbols));
    }

    private TypeView view(Type type) {
        return TypeView.of(type, symbols, ScopedDeclarations.of(symbols));
    }

    // --- a name is never a shape ----------------------------------------------------------------

    /** The reading issue #631 is about. What the position is, is what the name wraps. */
    @Test
    void aNewtypeOverASumIsThatSum() {
        assertEquals(new Shape.Sum(TypeSymbols.declared(new TypeKey(symbols.module(), "Stage")),
                new Shape.CommonProduct.None()), view("StageN").shape());
    }

    @Test
    void aNewtypeOverAPrimitiveIsThatPrimitive() {
        assertEquals(new Shape.Scalar(Type.Prim.BOOL), view("Flag").shape());
        assertEquals(new Shape.Scalar(Type.Prim.INT), view("Count").shape());
    }

    @Test
    void aNewtypeOverARecordIsThatRecord() {
        Shape.Product product = assertInstanceOf(Shape.Product.class, view("SlotN").shape());
        assertEquals(TypeSymbols.declared(new TypeKey(symbols.module(), "Slot")), product.name());
        assertEquals(java.util.Set.of("hour", "room"), product.fields().keySet());
    }

    /** Every layer comes off, so what a reader sees does not depend on how many names were written
     *  round the value. */
    @Test
    void everyNameComesOffWhateverTheDepth() {
        assertEquals(view("Stage").shape(), view("StageN").shape());
        assertEquals(view("Stage").shape(), view("StageNN").shape());
    }

    /** There is no case to hold a newtype, so no reader can be handed one and decide for itself
     *  whether to look through it. Held over the shapes a name is written over here. */
    @Test
    void noShapeIsANewtype() {
        for (String name : new String[] {"StageN", "StageNN", "Flag", "Count", "SlotN"}) {
            Shape shape = view(name).shape();
            assertFalse(shape instanceof Shape.Unresolved,
                    name + " reads as a shape rather than as a name this could not resolve");
        }
    }

    // --- and the names stay reachable -----------------------------------------------------------

    /** A row writes `StageN(Prospecting)`, so what the position is written as has to survive the
     *  reading that says what it is. */
    @Test
    void theNamesWornAreKeptOutermostFirst() {
        assertEquals(List.of("StageNN", "StageN"),
                view("StageNN").wrappers().stream().map(TypeSymbol::name).toList());
        assertEquals(List.of("StageN"),
                view("StageN").wrappers().stream().map(TypeSymbol::name).toList());
    }

    @Test
    void aTypeUnderNoNameWearsNone() {
        assertTrue(view("Stage").wrappers().isEmpty());
        assertFalse(view("Stage").isWrapped());
        assertTrue(view("StageN").isWrapped());
    }

    @Test
    void theDeclaredTypeIsWhatTheSignatureWrote() {
        assertEquals(Type.ref(TypeSymbols.declared(new TypeKey(symbols.module(), "StageN"))), view("StageN").declared());
    }

    // --- the shapes a position can have ---------------------------------------------------------

    @Test
    void eachTypeConstructorReadsAsItsOwnShape() {
        assertEquals(new Shape.Scalar(Type.Prim.STRING), view(Type.STRING).shape());
        assertEquals(new Shape.Unit(TypeSymbols.declared(new TypeKey(symbols.module(), "Won"))), view("Won").shape());
        assertEquals(new Shape.Sum(TypeSymbols.declared(new TypeKey(symbols.module(), "Stage")),
                new Shape.CommonProduct.None()), view("Stage").shape());
        assertEquals(new Shape.Sequence(Shape.Sequence.Kind.LIST, Type.INT),
                view(Type.list(Type.INT)).shape());
        assertEquals(new Shape.Sequence(Shape.Sequence.Kind.SET, Type.INT),
                view(Type.set(Type.INT)).shape());
        assertEquals(new Shape.Mapping(Type.STRING, Type.INT),
                view(Type.map(Type.STRING, Type.INT)).shape());
        assertEquals(new Shape.Optional(Type.INT), view(Type.option(Type.INT)).shape());
        assertEquals(new Shape.Tuple(List.of(Type.INT, Type.STRING)),
                view(Type.tuple(List.of(Type.INT, Type.STRING))).shape());
        assertEquals(new Shape.Undecided(), view(Type.var("'a")).shape());
    }

    /**
     * The five that are not value shapes, each answered as itself. Two say something about the
     * values and three about this compiler, and a reader downstream may only tell them apart if
     * they arrive apart — which is the whole reason a position nothing could read stopped being
     * reported as one the model divides no way.
     */
    @Test
    void aShapeThisCouldNotDetermineSaysWhichOfTheFiveItIs() {
        assertEquals(new Shape.Uninhabited(), view(Type.NEVER).shape());
        assertEquals(new Shape.Bottom(), view(Type.NOTHING).shape());
        assertEquals(new Shape.Erroneous(), view(Type.ERRONEOUS).shape());
        assertEquals(new Shape.Undecided(), view(Type.var("'a")).shape());
        assertEquals(new Shape.Unresolved(TypeSymbols.declared(new TypeKey(symbols.module(), "Cyclic"))), view("Cyclic").shape());
    }

    @Test
    void aUnionNobodyNamedIsItsMembers() {
        Type union = Type.union(java.util.Set.of(TypeSymbols.declared(new TypeKey(symbols.module(), "Won")), TypeSymbols.declared(new TypeKey(symbols.module(), "Qualified"))));
        assertEquals(new Shape.Cases(java.util.Set.of(TypeSymbols.declared(new TypeKey(symbols.module(), "Won")), TypeSymbols.declared(new TypeKey(symbols.module(), "Qualified")))),
                view(union).shape());
    }

    /** A name reachable from itself ends the walk. There is no base to read, so the shape says the
     *  name resolved to nothing usable rather than standing in for a value. */
    @Test
    void aNewtypeReachingItselfIsUnresolvedRatherThanAShape() {
        assertEquals(new Shape.Unresolved(TypeSymbols.declared(new TypeKey(symbols.module(), "Cyclic"))), view("Cyclic").shape());
    }
}
