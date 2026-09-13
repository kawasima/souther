package souther.compiler.inputs;

import souther.compiler.check.PublishedDeclarations;
import souther.compiler.check.Shape;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeView;
import souther.compiler.types.Type;

/**
 * A position the partition derivation may be asked about, and proof that it is one.
 *
 * <p>Package-private, cases and all, so that holding one is the proof rather than a claim about
 * one: a public record is a canonical constructor anything can call, and a proof anything can write
 * down is not one.
 *
 * <p>Holding one of these is the proof. The derivation asks whether a position divides, and answers
 * that it does not — {@code Absent} — only where every producer it has was asked and none of them
 * answered. Which producers those are is a fact about this compiler; what this type adds is the
 * premise underneath it, that the position is one a value can stand at at all. That premise used to
 * be nowhere: the walk took a {@link Type}, and a type it had no arm for fell through the bottom of
 * a chain of {@code if}s into the same answer as a position the model really does not divide.
 *
 * <p>So the premise is the input's type rather than a check the walk repeats. A shape outside the
 * set cannot be carried here, so nothing downstream has to ask again, and what is left for an
 * absence is that the evidence phases were exhausted ({@link PendingPosition}).
 *
 * <p><b>The set is this package's, not the boundary's.</b> Which types reach a behavior's parameter
 * and which reach a data's field are two rules that disagree — an {@code Option} may be a field and
 * may not be a parameter — and neither of them is the question "what may a partition be derived
 * from". Deriving this set from either would make a change over there silently change what is
 * measured over here. Written out, a boundary that starts admitting a new shape stops this compiling
 * until the partition semantics of that shape have been decided.
 */
record ReadablePosition(TypeView view, Shape.ReadablePositionShape shape) {

    /**
     * How {@code type} is read at a position the derivation is about.
     *
     * <p>Exhaustive over {@link Shape}, with no {@code default}: a sixteenth case stops this
     * compiling rather than arriving somewhere further down as a position nothing divides.
     */
    static ReadablePosition of(Type type, Symbols symbols, PublishedDeclarations published) {
        return of(TypeView.asWritten(type, symbols, published));
    }

    /** The same, of a position already read. The reading is the expensive half and the walk has one
     *  of them per position, so what asks about the shape and what asks about the names it is
     *  written under ask of the same reading. */
    static ReadablePosition of(TypeView view) {
        return new ReadablePosition(view, admitted(view));
    }

    private static Shape.ReadablePositionShape admitted(TypeView view) {
        return switch (view.shape()) {
            case Shape.ReadablePositionShape admissible -> admissible;
            // Refused where a signature or a field is read, each by an exhaustive switch of its own
            // (SignatureBoundary, CodecShape), so a value of one reaching here is this compiler
            // disagreeing with itself about what a position can be — not a model to report on.
            case Shape.Cases _, Shape.Tuple _, Shape.Function _, Shape.Uninhabited _,
                 Shape.Bottom _, Shape.Erroneous _, Shape.Undecided _ ->
                    throw unreachableInput(view);
        };
    }

    /**
     * A shape that cannot stand at a position and did.
     *
     * <p>Not a refusal, not a derivation that stopped, and nothing a reader of a model can act on.
     * The boundary that admits what crosses and this one are separate exhaustive readings, and one
     * of them has let through what the other says cannot arrive.
     */
    private static IllegalStateException unreachableInput(TypeView view) {
        return new IllegalStateException(
                "`" + Type.show(view.declared()) + "` reads as " + view.shape()
                        + " at a position a partition is derived from; the boundary that admits a"
                        + " position and this disagree about what may stand at one");
    }
}
