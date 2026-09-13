package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.DeclarationNewtypes;
import souther.compiler.check.ElementBindings;
import souther.compiler.check.ElementProvenance;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BinOp;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A name an operation handed an element on is answered by the container it came from, and by
 * nothing beside it.
 *
 * <p>A binding can be both. Two walks over one collection joined into one leave the second walk's
 * element bound to what the first walk's closure made of an element of the first — so the binding is
 * what an operation handed an element on, and it holds a value, and the value reaches the position
 * that closure read. Answered by whichever road reaches a position, the reading says the name
 * <em>is</em> what it was <em>made from</em>, and a rule about it draws a line at values it is not
 * about. An author cannot tell such a line from one their model states.
 *
 * <p>So the two are not roads to race. What the name is decides which step is taken, and where a
 * name is an element the container answers however that comes out — including when it comes out
 * saying nothing.
 *
 * <p>Written out rather than compiled, because the environment is what is being held to. The shape
 * is one a rewrite leaves and not one a body says, and a model that produces it today would be
 * evidence about that rewrite rather than about this rule.
 */
class AnElementIsAnsweredByItsContainerAndNotByWhatItHoldsTest {

    private static final SourcePos POS = new SourcePos(0, 0);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("example", "f");
    private static final BindingId PARAMETER = new BindingId(OWNER, 0);
    private static final BindingId ELEMENT = new BindingId(OWNER, 1);
    private static final BindingId CONTAINER = new BindingId(OWNER, 2);
    private static final BindingId LOCAL = new BindingId(OWNER, 3);

    private static Core.Read read(String name, BindingId binding) {
        return new Core.Read(name, binding, Type.INT, POS);
    }

    private static Core.Read parameter() {
        return read("n", PARAMETER);
    }

    private static PathResolution readingOf(Core e, ElementBindings elements,
                                            Map<BindingId, Core> bound) {
        return InputReads.written(Map.of(PARAMETER, TermPath.of("n")), bound, elements)
                .pathOf(e, DeclarationNewtypes.asWritten(Symbols.none(DefaultStdlib.get())));
    }

    private static PathResolution readingOf(Core e) {
        return readingOf(e, ElementBindings.NONE, Map.of());
    }

    /** The parameter itself is where it is. */
    @Test
    void aParameterIsAtItsPosition() {
        assertEquals(new PathResolution.At(TermPath.of("n")), readingOf(parameter()));
    }

    /** Arithmetic over it stands at no position, which is what the model says. */
    @Test
    void arithmeticOverAPositionStandsAtNone() {
        Core sum = new Core.Binary(BinOp.ADD, parameter(), new Core.Int(1, Type.INT, POS),
                ConstructOccurrence.unwritten(), Type.INT, POS);

        assertEquals(new PathResolution.NotAPosition(), readingOf(sum));
    }

    /**
     * An expression that binds a name of its own is read under that name.
     *
     * <p>Which is what a binding means, and what a helper applied to an argument is left as. A
     * splice leaves the helper's body under a name bound to the call's argument, so a reading that
     * stopped at the shape would have every claim inside an expanded helper about a position it
     * could not name.
     */
    @Test
    void anExpressionThatBindsANameOfItsOwnIsReadUnderThatName() {
        Core through = new Core.LetIn(new Core.Binder("x", LOCAL), parameter(),
                read("x", LOCAL), Type.INT, POS);

        assertEquals(new PathResolution.At(TermPath.of("n")), readingOf(through));
    }

    /**
     * However many of them there are.
     *
     * <p>The same answer at one binding and at four. How a model was written is not a fact about
     * what it says, and a reading that ran out somewhere between them would be reporting its own
     * reach as the model's silence.
     */
    @Test
    void howManyNamesAnExpressionBindsDoesNotDecideWhatIsRead() {
        assertEquals(readingOf(nested(1)), readingOf(nested(4)),
                "one name and four are the same reading");
        assertEquals(new PathResolution.At(TermPath.of("n")), readingOf(nested(4)));
    }

    /** The parameter under {@code depth} bindings, each naming the one outside it. */
    private static Core nested(int depth) {
        Core at = parameter();
        for (int i = 0; i < depth; i++) {
            BindingId binding = new BindingId(OWNER, 10 + i);
            at = new Core.LetIn(new Core.Binder("x" + i, binding), at,
                    read("x" + i, binding), Type.INT, POS);
        }
        return at;
    }

    /**
     * A name an operation handed an element on, bound to a value that reaches a position, is not at
     * that position.
     *
     * <p>The container this element came from is at none, so neither is the element — and what the
     * binding holds is not asked. That value is what the walk before this one made of an element of
     * its own container, and it stands where that walk read, not where these values are.
     */
    @Test
    void anElementBoundToAValueThatReachesAPositionIsNotThere() {
        assertEquals(new PathResolution.NotAPosition(),
                readingOf(read("x", ELEMENT), handedAnElementOf(read("xs", CONTAINER)),
                        Map.of(ELEMENT, parameter())));
    }

    /**
     * And the value it holds would have reached one.
     *
     * <p>The same environment with nothing saying the binding is an element. Without it the reading
     * follows what the name was given and arrives at the parameter, which is what the case above
     * refuses — so what that case holds is the ordering and not an absence somewhere else.
     */
    @Test
    void andWhatItHoldsWouldHaveReachedOne() {
        assertEquals(new PathResolution.At(TermPath.of("n")),
                readingOf(read("x", ELEMENT), ElementBindings.NONE,
                        Map.of(ELEMENT, parameter())));
    }

    /** {@code ELEMENT} handed an element of {@code container}, and nothing else recorded. */
    private static ElementBindings handedAnElementOf(Core container) {
        return new ElementBindings(Map.of(ELEMENT, List.of(container)), Map.of(),
                ElementProvenance.NONE, Map.of());
    }
}
