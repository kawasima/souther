package souther.compiler.partition;

import souther.compiler.inputs.NumericTerm;

import java.util.List;

/**
 * What a position's own type and rules divide it into.
 *
 * <p>Two answers about the reading of this position's own declarations, and neither is a verdict
 * about the model. {@link Open} says this reading found no division, which is what licenses asking
 * what a body's rules and the position's own questions come to; whether anything about the model
 * follows from there being no class is answered where those are ({@link PendingPosition}).
 *
 * <p>Neither carries the reading it came from. A reading is completed and the answer derived from
 * it ({@link LocalInspection}), so an {@code Open} has nothing of its own with which to claim the
 * reading ran to the end. A third thing to read arrives as another way to be {@link Divided}, not
 * as another empty list to remember to check.
 */
public sealed interface LocalPartition {

    /**
     * The model divides the position: into classes, by lines, or both.
     *
     * <p>One entry per number the declarations measured, and never none. A value of this carrying no
     * measure would be an open position dressed as a divided one, and the phase after this one would
     * never be reached for it.
     *
     * <p><b>A list because a position has numbers rather than a number.</b> A rule naming the values
     * a string holds divides what stands there; a rule bounding the length of it draws a line on the
     * length; the two are about different numbers and neither is a competitor of the other. Held as
     * one bag of classes beside one bag of lines, the pair has to be given a single number, and
     * whichever is chosen the other's evidence is filed under a number it was never about.
     */
    record Divided(List<DeclaredMeasure> measures) implements LocalPartition {

        public Divided {
            measures = List.copyOf(measures);
            if (measures.isEmpty()) {
                throw new IllegalArgumentException(
                        "nothing divides this position, which is a different answer");
            }
            // What tells two measures of one position apart is the number each is of. Two under one
            // number is one of them standing for the other wherever they are looked up by it, which
            // is everywhere downstream.
            java.util.Set<NumericTerm.FromOnePosition> named = new java.util.LinkedHashSet<>();
            for (DeclaredMeasure each : measures) {
                if (!named.add(each.term())) {
                    throw new IllegalArgumentException(
                            "this position is measured twice at " + each.term());
                }
            }
        }
    }

    /**
     * Nothing this reading found divides the position.
     *
     * <p>About this reading and not about the model. It says nothing about the rules a behavior's
     * body writes, and nothing about whether the rules of the position leave a question standing —
     * so it is what licenses the questions after it rather than a verdict of its own.
     */
    record Open() implements LocalPartition {}
}
