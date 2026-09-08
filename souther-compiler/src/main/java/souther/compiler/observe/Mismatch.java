package souther.compiler.observe;

import java.util.List;

/**
 * Where what was stated and what was answered are not the same value.
 *
 * <p>The place inside the two values, what was stated there, what stood there, and which of the
 * questions being the same value is made of it fails.
 *
 * <p>All four are about one place. A difference reported at a place the values beside it are not
 * from is a reader told two things that do not go together — and where the difference is that one
 * side holds a place the other has not got, the place there is to report it at is the value whose
 * places they are ({@link Reason#SHAPE}).
 *
 * <p>{@link #position} is what the declaration says stands at that place. It is carried rather than
 * left to be worked out again, because what a value is there was already decided by the comparison:
 * an observation does not say whether its sequence is a list or a set, and a reader asking that
 * question a second time would be a second reading of it, free to answer differently from the
 * comparison that reported the difference.
 *
 * @param path     the steps from the whole value to the place they differ, empty where the two
 *                 differ as wholes
 * @param reason   which question they fail
 * @param expected what was stated at that place
 * @param observed what stood there
 * @param position what the declaration reads that place through
 */
public record Mismatch(List<PathElement> path, Reason reason, Expectation.Asserts expected,
                       ObservedValue observed, Position position) {

    public Mismatch {
        path = List.copyOf(path);
        if (reason == null || expected == null || observed == null || position == null) {
            throw new IllegalArgumentException("a difference is between two values, somewhere,"
                    + " for a reason");
        }
    }

    /**
     * Why two values are not the same value.
     *
     * <p>{@link #TYPE} and {@link #VALUE} are the distinction this is drawn for: something stating
     * {@code 1} where an {@code AmountN} comes out disagrees about what type stands there, which is
     * not the same disagreement as stating the wrong number.
     */
    public enum Reason {
        /** The two are of different types — a name against another name, a list against a set, or
         *  against no name at all. */
        TYPE,
        /** One type, different contents. */
        VALUE,
        /** The same kind of container, holding a different set of places. */
        SHAPE,
        /** One is absent where the other is present. */
        ABSENCE,
        /** One side could not be read back, so nothing about it can be compared. */
        UNREADABLE
    }
}
