package souther.compiler.inputs;

import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.NarrowedBounds;
import souther.compiler.numeric.NumericDomain;

/**
 * What the rules leave one of a position's numbers.
 *
 * <p>One of these per number a position has, which its type settles: what stands there always, and
 * what an operation counts of it where the type declares one that counts its values. Which numbers
 * those are is not a question about the rules — a rule can write about a number and place no end,
 * and a number nobody wrote about is still a number of the position — so a position holds one of
 * these wherever it has a number, empty bounds and all.
 *
 * <p><b>The number is here, so a reader never works out which one this is about.</b> Held as a
 * range beside a term the caller happened to have, a range of the length of a string and a range of
 * the strings themselves are two values a reader tells apart by where it found them, and the two
 * are on different orders. Which end a rule placed, what the value this sits in projects, and where
 * the position stops once everything is taken in are all about one number, so they travel with it.
 *
 * @param term        the number this is about, taken of the position this sits under
 * @param admissible  which values of the number may stand, or null where nothing bounds them
 * @param ownEnds     where the position's own type says the number stops, with the declarations
 *                    that said so, or null where none does
 * @param narrowedEnds what the value the position sits in projects onto this number, and which
 *                    declarations hold each end of that
 * @param rangeLeft   where the number starts and stops once every rule reaching the value this sits
 *                    in has been taken in, which is not what {@link #ownEnds} says: a clause placing
 *                    an end at a value another clause takes away leaves the number stopping
 *                    somewhere no rule wrote
 */
public record PositionBounds(NumericTerm.FromOnePosition term,
                             NumericDomain.Bounds admissible,
                             DeclaredBounds.Bounds ownEnds,
                             NarrowedBounds narrowedEnds,
                             NumericDomain.Bounds rangeLeft) {

    public PositionBounds {
        if (term == null) {
            throw new IllegalArgumentException("bounds of no number");
        }
        // Nothing projected and nothing known about the projection are one answer here, and the
        // first is the one every reader below is written for. Left null, each of them would have to
        // decide what an absent projection means, and a position no record holds would read as one
        // whose projection nobody worked out.
        if (narrowedEnds == null) {
            throw new IllegalArgumentException(
                    term + " has no answer about what the value it sits in leaves it");
        }
    }
}
