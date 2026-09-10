package souther.compiler.inputs;

import souther.compiler.check.DeclaredBounds;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;

/**
 * What a declaration's rules leave a position between, composed with everything else that reaches
 * it.
 *
 * <p>What the rules themselves say is {@link DeclaredBounds}'. This is the step after it: a
 * projection onto the position and what the term standing there guarantees of its own are read in
 * beside them, and only a partitioning has either.
 */
public final class TypeBounds {

    /**
     * What the position can hold: every rule reaching it, intersected, with what the term itself
     * guarantees taken in.
     *
     * <p>An end survives from whichever side has one, because a value outside it is refused whether
     * the type said so or the record did. That is the difference from {@link LocalInspection}'s axis bounds: a cap the
     * record alone imposes is not a line dividing this position, and it is still where its values
     * stop — so a guard beyond it divides nothing and is no edge either.
     *
     * <p>Numbers and no names. An end here says where the values stop; which rule put it there is a
     * cut's question, and answering it from a projection would name a rule that never mentioned this
     * position on its own.
     *
     * <p>A size is never negative and nothing has to write that down (spec
     * §invariant-discharge-terms). Kept here with the rules rather than at the boundary that reads
     * them, so that a guard at zero is refused its neighbour below by the same intersection that
     * refuses one outside an invariant.
     */
    public static NumericDomain.Bounds admissible(DeclaredBounds.Range own, NumericDomain.Bounds projected,
                                           NumericTerm term) {
        // A term with nothing to guarantee and no term at all are one answer here. What this returns
        // is read by callers that take a null for a position they know nothing about, which is not
        // the same as a range holding every value — kept apart there, and flattened once here rather
        // than at each of them.
        NumericDomain.Bounds guaranteed = term == null ? null : term.intrinsicBounds();
        NumericDomain.Bounds intrinsic =
                guaranteed == null || guaranteed.saysNothing() ? null : guaranteed;
        if (own == null) {
            return intrinsic;   // not a number of its own, so only what the term guarantees
        }
        Endpoint min = own.min();
        Endpoint max = own.max();
        NumericDomain.Bounds read = projected == null ? new NumericDomain.Bounds(min, max)
                : new NumericDomain.Bounds(Endpoint.lower(min, projected.min()),
                        Endpoint.upper(max, projected.max()));
        return intrinsic == null ? read
                : new NumericDomain.Bounds(Endpoint.lower(read.min(), intrinsic.min()),
                        Endpoint.upper(read.max(), intrinsic.max()));
    }

    /** The same, of a position no term of its own is measured at. */
    public static NumericDomain.Bounds admissible(DeclaredBounds.Range own, NumericDomain.Bounds projected) {
        return admissible(own, projected, null);
    }

    private TypeBounds() {}
}
