package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.DeclarationNewtypes;
import souther.compiler.check.ElementBindings;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A field read back out of a construction is at the position the construction was given it.
 *
 * <p>The construction and the projection cancel. What a construction was handed is the value a field
 * of it comes back as, so a reader asking which position that field is at is asking about the value
 * that was handed in — and it is at the position that value is at. Resolved as a path of the
 * construction and deepened by the field, the answer is that a value built here is at no position:
 * true of the construction, and not what the projection above it reads.
 *
 * <p><b>The pair and not the construction.</b> A construction on its own is at no position, and that
 * arm does not move. The values at a position are what the input holds there, and what a construction
 * built out of them is a value of its own — a rule about it is not a rule about the strings or the
 * numbers standing at the position it was given. Only the elimination that takes the given value back
 * out says the two are one value, and only about the field it names.
 *
 * <p>Written out rather than compiled, because the environment is what is being held to: which value
 * a name was given is an answer this reading is handed, and a model that happens to produce the shape
 * today would be evidence about the rewrite that produced it.
 */
class AProjectionOutOfAConstructionIsWhereItWasGivenItTest {

    private static final SourcePos POS = new SourcePos(0, 0);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("example", "f");
    private static final BindingId LEFT = new BindingId(OWNER, 0);
    private static final BindingId RIGHT = new BindingId(OWNER, 1);
    private static final BindingId LOCAL = new BindingId(OWNER, 2);

    private static final TypeSymbol.AtModule CODE =
            TypeSymbols.declared(new TypeKey("example", "Code"));
    private static final TypeSymbol.AtModule PAIR =
            TypeSymbols.declared(new TypeKey("example", "Pair"));
    private static final TypeSymbol.AtModule OUTER =
            TypeSymbols.declared(new TypeKey("example", "Outer"));

    private static Core.Read parameter(String name, BindingId binding) {
        return new Core.Read(name, binding, Type.STRING, POS);
    }

    private static Core.Read a() {
        return parameter("a", LEFT);
    }

    private static Core.Read b() {
        return parameter("b", RIGHT);
    }

    /** {@code Code(a)}, the one-field construction a newtype is written as. */
    private static Core.Construct code(Core given) {
        return new Core.Construct(CODE, List.of(new Core.FieldValue("value", given, POS)),
                Type.ref(CODE), POS);
    }

    /** {@code Pair { left = a, right = b }}. */
    private static Core.Construct pair(Core left, Core right) {
        return new Core.Construct(PAIR, List.of(new Core.FieldValue("left", left, POS),
                new Core.FieldValue("right", right, POS)), Type.ref(PAIR), POS);
    }

    /**
     * {@code target.field}, typed as what the field holds.
     *
     * <p>Written out because a projection carries the type of the value it reads, and a tree that
     * carries another is one the checker never built. What this walk asks of a type is which fields
     * are steps of a path, so a fixture typed by what the assertion wanted would be holding the
     * reading to a shape nothing else in the compiler answers about.
     */
    private static Core.FieldAccess field(Core target, String field, Type holds) {
        return new Core.FieldAccess(target, field, holds, POS);
    }

    private static PathResolution readingOf(Core e, Map<BindingId, Core> bound) {
        return InputReads.written(Map.of(LEFT, TermPath.of("a"), RIGHT, TermPath.of("b")),
                        bound, ElementBindings.NONE)
                .pathOf(e, DeclarationNewtypes.asWritten(Symbols.none(DefaultStdlib.get())));
    }

    private static PathResolution readingOf(Core e) {
        return readingOf(e, Map.of());
    }

    /** The value a newtype's construction was given, read back out of it, is where that value is. */
    @Test
    void aFieldReadOutOfAConstructionWrittenWhereItStandsIsAtThePositionItWasGiven() {
        assertEquals(new PathResolution.At(TermPath.of("a")),
                readingOf(field(code(a()), "value", Type.STRING)));
    }

    /**
     * And the same where the construction stands behind a name.
     *
     * <p>Which is how a body writes one. The name is answered the way every name is — by what it was
     * given — and the projection is then over the construction that answer is.
     */
    @Test
    void aFieldReadOutOfAConstructionANameHoldsIsAtThePositionItWasGiven() {
        Core held = new Core.Read("code", LOCAL, Type.ref(CODE), POS);

        assertEquals(new PathResolution.At(TermPath.of("a")),
                readingOf(field(held, "value", Type.STRING), Map.of(LOCAL, code(a()))));
    }

    /**
     * And the field asked for is the one answered about.
     *
     * <p>What tells this rule from a construction read as one value. A construction given two
     * positions holds the values of each under the field it was given them for, and a rule about one
     * of those fields is about the values at that position and not at the other.
     */
    @Test
    void oneFieldOfTwoIsAtItsOwnPosition() {
        assertEquals(new PathResolution.At(TermPath.of("a")),
                readingOf(field(pair(a(), b()), "left", Type.STRING)));
        assertEquals(new PathResolution.At(TermPath.of("b")),
                readingOf(field(pair(a(), b()), "right", Type.STRING)));
    }

    /** However many constructions stand between the projection and the value. */
    @Test
    void aProjectionOfAConstructionGivenAConstructionReachesTheValueInside() {
        Core.Construct outer = new Core.Construct(OUTER,
                List.of(new Core.FieldValue("inner", code(a()), POS)), Type.ref(OUTER), POS);

        assertEquals(new PathResolution.At(TermPath.of("a")),
                readingOf(field(field(outer, "inner", Type.ref(CODE)), "value", Type.STRING)));
    }

    /**
     * A construction itself is at no position, whatever it was given.
     *
     * <p>The arm this rule does not touch. What stands at the position is what the construction was
     * handed, and the construction is a value of its own — so a rule about it is a rule about values
     * the position does not hold, and a line drawn there would be at values the rule is not about.
     * What makes the projection different is the elimination: it hands the given value back, and
     * nothing else here does.
     */
    @Test
    void aConstructionItselfIsAtNoPosition() {
        assertEquals(new PathResolution.NotAPosition(), readingOf(code(a())));
        assertEquals(new PathResolution.NotAPosition(), readingOf(pair(a(), b())));
    }

    /** And a name holding one is at none either, for the same reason. */
    @Test
    void aNameHoldingAConstructionIsAtNoPosition() {
        Core held = new Core.Read("code", LOCAL, Type.ref(CODE), POS);

        assertEquals(new PathResolution.NotAPosition(),
                readingOf(held, Map.of(LOCAL, code(a()))));
    }
}
