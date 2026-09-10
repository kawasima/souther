package souther.compiler.hash;

/**
 * A value that names the value a walk may follow in its place.
 *
 * <p>What the walk is is the one that proves a number is taken from values and never from which
 * object something is. It stops at a class that answers a number of its own, so a class that has to
 * answer one names what it is over instead, and the walk goes through that.
 *
 * <p>Said by the value and not about it. A table of class names and part names kept somewhere else
 * is a second writing of what a value holds, and the two come apart the first time a value is given
 * a part: the class compiles, the number is over what it was, and the walk proves something about a
 * value nobody builds. What is named here is named where the parts are, so a part joins both at
 * once or neither.
 *
 * <p><b>What may be named is anything the value's equality already agrees with.</b> Two values that
 * are equal name things that are equal — that is the whole of what a number owes an equality — and a
 * value told apart by more than what stands for it is free to name the less. So this is not a second
 * spelling of the equality, and it is not a statement about what the value's own
 * {@code hashCode} reads: an evaluation is told apart by which object it is, answers the number
 * {@code Object} gives it, and still names what may be walked in its place.
 *
 * <p><b>Nor is it a statement that the value keeps a number.</b> A value that does is a
 * {@link KeepsTheNumberItIsAskedFor}, which says so and is held to it.
 *
 * <p><b>Held, not worked out when asked.</b> A value is asked this once for every number a term
 * takes of it, which is far more often than one is made. One built at the ask would put back the
 * walk that naming it is for, and would hand out a different object each time to a reader that has
 * no way to know it.
 */
public interface SaysWhatStandsForIt {

    /**
     * The value that stands for this one.
     *
     * <p>Answered with a type of its own rather than with {@code Object}, so that what a walk over
     * types reads is the same thing the walk over values will meet. A value holding its parts in a
     * record of its own answers with that record.
     */
    Object standsFor();
}
