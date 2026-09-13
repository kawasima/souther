package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.PathResolution;
import souther.compiler.inputs.TermPath;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.Type;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An edge the question being asked does not cross is not an edge nobody wrote down.
 *
 * <p>Both leave a walk with no binding to go on to, and only one of them leaves it free to look
 * elsewhere. Where nothing was recorded, what the binding holds is the only thing there is to read
 * and reading it is how a container bound to a value is followed. Where an edge says the values were
 * made from another binding's, the walk after which position a value <em>is</em> has been told to
 * stop — and what the binding holds is the very thing it was told to stop before, since the value a
 * rewrite left under such a name is what the walk before it made.
 *
 * <p>Answered alike, the stop is read as an absence and the second road is taken: the reading
 * arrives at the position the earlier walk read, which is the one answer this must never give. That
 * a body compiled today does not reach it is a fact about the shape one rewrite happens to leave,
 * which is what nothing here is allowed to depend on.
 *
 * <p>Written out rather than compiled, because what is held is that the two answers are different
 * whatever a body can be written to say.
 */
class WhatAQuestionMayNotCrossIsNotWhatWasNeverRecordedTest {

    private static final SourcePos POS = new SourcePos(0, 0);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("example.walks", "f");

    /** The parameter the container's binding was bound to, and where the second road would end. */
    private static final BindingId HELD = new BindingId(OWNER, 0);

    /** The parameter the edge runs to, and where a reading of provenance ends. */
    private static final BindingId MADE_FROM = new BindingId(OWNER, 1);

    /** The container: made from one parameter, and bound to the other. */
    private static final BindingId CONTAINER = new BindingId(OWNER, 2);

    /** An element an operation handed out of that container. */
    private static final BindingId ELEMENT = new BindingId(OWNER, 3);

    private static Core.Read read(String name, BindingId binding) {
        return new Core.Read(name, binding, Type.INT, POS);
    }

    /** The names, with {@code provenance} said of the container. */
    private static InputReads reads(ElementProvenance provenance) {
        ElementBindings elements = new ElementBindings(
                Map.of(ELEMENT, List.of(read("xs", CONTAINER))),
                Map.of(CONTAINER, read("held", HELD)),
                provenance, Map.of());
        return InputReads.ofParameters(Map.of(HELD, "held", MADE_FROM, "made"), elements);
    }

    private static ElementProvenance madeFrom() {
        ElementProvenance.Builder builder = new ElementProvenance.Builder();
        builder.derivesFrom(CONTAINER, MADE_FROM);
        return builder.built();
    }

    private static PathResolution namedPositionOf(ElementProvenance provenance) {
        return reads(provenance).pathOf(read("x", ELEMENT),
                DeclarationNewtypes.asWritten(Symbols.none(DefaultStdlib.get())));
    }

    /**
     * The walk after which position a value is stops at the edge, and does not go on to read what
     * the binding holds.
     */
    @Test
    void aRefusedEdgeIsNotAWayToLookElsewhere() {
        assertEquals(new PathResolution.NotAPosition(), namedPositionOf(madeFrom()),
                "the elements were made from somewhere, so an element of them is at no position —"
                        + " and what the container holds is what that making read");
    }

    /**
     * And where nothing was recorded the second road is the only road, so it is taken.
     *
     * <p>The same environment with the edge removed. Without this, the case above would hold as
     * well if the walk had simply stopped reading — what it holds is that the stop is the edge's and
     * not a capability missing somewhere else.
     */
    @Test
    void andWhereNothingWasRecordedWhatTheBindingHoldsIsRead() {
        assertEquals(new PathResolution.At(TermPath.of("held").element()),
                namedPositionOf(ElementProvenance.NONE),
                "nothing says where these elements came from, so the container is what it holds");
    }

    /** And the walk after where a value came from crosses the edge, which is what it is for. */
    @Test
    void andAReadingOfProvenanceCrossesIt() {
        assertEquals(new PathResolution.At(TermPath.of("made").element()),
                reads(madeFrom()).cameFrom(read("x", ELEMENT),
                        DeclarationNewtypes.asWritten(Symbols.none(DefaultStdlib.get()))),
                "the values came from there, which is what a rule about them was written about");
    }
}
