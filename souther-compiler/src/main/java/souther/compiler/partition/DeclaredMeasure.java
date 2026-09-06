package souther.compiler.partition;

import souther.compiler.check.NarrowedBounds;
import souther.compiler.inputs.NumericTerm;

import java.util.List;

/**
 * What a position's own declarations came to about one of its numbers.
 *
 * <p>The form an {@link Axis} takes before a behavior is named, and the same thing: a run of classes
 * over one number's values, the lines drawn on it, and what the value the position sits in leaves
 * it. Kept apart from the axis so that the reading of the declarations answers in its own words and
 * a projection is all that stands between the two.
 *
 * <p><b>The number is what this is filed under, and it is the number the evidence itself names.</b>
 * The classes a type states divide what stands at the position; a line is on whichever number the
 * rule that placed it named. A reading collecting the two into one bag and labelling the bag with a
 * number the position was said to be measured at hands a class of the strings to an axis that
 * counts characters — and the pair of readers an axis exists to keep together is left with nothing
 * to compare.
 *
 * @param term     the number this is a measure of
 * @param classes  what the declarations divide that number's values into, empty where they divide
 *                 them nowhere
 * @param cuts     the lines drawn on it, and how certain the rules that placed them are
 * @param narrowed what the value the position sits in leaves this number, and who holds each end
 */
record DeclaredMeasure(NumericTerm.FromOnePosition term, List<PartitionClass> classes,
                       CutEvidence cuts, NarrowedBounds narrowed) {

    DeclaredMeasure {
        classes = List.copyOf(classes);
        if (term == null) {
            throw new IllegalArgumentException("a measure of no number");
        }
        // A measure with neither is a number nothing measured, which is what a position keeps to
        // itself. Held here, it would become an axis measuring nothing — a location counted among
        // the measures.
        if (classes.isEmpty() && cuts instanceof CutEvidence.None) {
            throw new IllegalArgumentException(
                    "nothing divides " + term + ", which is a different answer");
        }
        // The one place the two vocabularies meet. A class says what it means and the number it is
        // a class of is said where it was built, so a measure holding one of another number is a
        // filing error at the point of filing rather than an axis that cannot be compared with
        // anything later.
        for (PartitionClass one : classes) {
            if (!term.equals(one.of())) {
                throw new IllegalArgumentException("`" + one.id() + "` is a class of " + one.of()
                        + ", and this measures " + term);
            }
        }
    }
}
