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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A name whose value is read through itself is not a name that reaches no position.
 *
 * <p>The two answers are told apart because what a reader is told about the model depends on it.
 * Arithmetic over a position stands nowhere, and a rule about it is reported as being about a value
 * made from somewhere else — a sentence about the model, and a true one. A binding that holds a
 * value read through itself is not a model at all: the lineage this compiler builds runs one way,
 * so a value read through itself is a representation it made and says it does not make.
 *
 * <p>Handed one of those, the reading raises rather than answering. What it must not do is come back
 * with the answer a plain expression gets, which would put a report of this compiler's own state
 * into a document about somebody's model — the thing this reading was made to stop doing.
 *
 * <p>No source builds one, which is why the environment is written out here: what is being held is
 * the reading's behavior over an environment it may be handed, and a walk over the models that
 * compile could never reach it.
 */
class AValueReadThroughItselfIsNotAPositionNothingNamesTest {

    private static final SourcePos POS = new SourcePos(0, 0);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("example", "f");
    private static final BindingId FIRST = new BindingId(OWNER, 1);
    private static final BindingId SECOND = new BindingId(OWNER, 2);
    private static final BindingId PARAMETER = new BindingId(OWNER, 0);
    private static final TypeSymbol.AtModule CODE =
            TypeSymbols.declared(new TypeKey("example", "Code"));

    /**
     * {@code name}, typed as what the binding holds.
     *
     * <p>Written out at each call because a read carries the type of the value it names. The one
     * thing wrong with these environments is the way the binding graph runs back on itself, and a
     * read typed by something other than what its binding was given would be a second thing wrong
     * with them — one the raising could be coming from instead.
     */
    private static Core.Read read(String name, BindingId binding, Type holds) {
        return new Core.Read(name, binding, holds, POS);
    }

    private static InputReads reads(Map<BindingId, Core> bound) {
        return InputReads.written(Map.of(PARAMETER, TermPath.of("n")), bound,
                ElementBindings.NONE);
    }

    private static DeclarationNewtypes newtypes() {
        return DeclarationNewtypes.asWritten(Symbols.none(DefaultStdlib.get()));
    }

    /** A name bound to what a second holds, and that one bound back to the first. */
    private static InputReads twoNamesHoldingEachOther() {
        Map<BindingId, Core> bound = new LinkedHashMap<>();
        bound.put(FIRST, read("b", SECOND, Type.INT));
        bound.put(SECOND, read("a", FIRST, Type.INT));
        return reads(bound);
    }

    /** A name bound to a construction one of whose fields reads the name back. */
    private static InputReads aNameHoldingAConstructionOfItself() {
        Core.FieldAccess ofItself =
                new Core.FieldAccess(read("c", FIRST, Type.ref(CODE)), "value", Type.INT, POS);
        Core.Construct wrapping = new Core.Construct(CODE,
                java.util.List.of(new Core.FieldValue("value", ofItself, POS)),
                Type.ref(CODE), POS);
        return reads(Map.of(FIRST, wrapping));
    }

    /**
     * And the same where the way back to the binding runs through a construction it holds.
     *
     * <p>The projection and the construction cancel, so the walk goes on with what the field was
     * given — and that is the name it crossed to get here. Every step of that is one traversal: the
     * binding is on the way while the value inside the construction is being read, not only while the
     * construction is being found. Let go of in between, this arrives back at the binding with
     * nothing on the way and runs until the stack is gone, which is a report about this compiler that
     * no reader can act on.
     */
    @Test
    void readingAValueThroughAConstructionItHoldsIsRaisedToo() {
        InputReads names = aNameHoldingAConstructionOfItself();

        assertThrows(BindingTrail.ReadThroughItself.class,
                () -> names.pathOf(new Core.FieldAccess(read("c", FIRST, Type.ref(CODE)), "value",
                        Type.INT, POS), newtypes()));
    }

    /** Raised where a value is read through itself, and named as this compiler's own state. */
    @Test
    void readingAValueThroughItselfIsRaised() {
        InputReads names = twoNamesHoldingEachOther();

        BindingTrail.ReadThroughItself raised = assertThrows(BindingTrail.ReadThroughItself.class,
                () -> names.pathOf(read("a", FIRST, Type.INT), newtypes()));

        assertInstanceOf(IllegalStateException.class, raised,
                "a lineage that runs back to where it started is a graph nothing here builds, so"
                        + " what is wrong is this compiler's own state and not what a caller"
                        + " handed over");
    }

    /** And a name that reaches no position answers that, which is the other thing entirely. */
    @Test
    void aNameThatReachesNoPositionSaysSoInstead() {
        InputReads names = reads(Map.of());

        assertEquals(new PathResolution.NotAPosition(),
                names.pathOf(read("a", FIRST, Type.INT), newtypes()));
    }
}
