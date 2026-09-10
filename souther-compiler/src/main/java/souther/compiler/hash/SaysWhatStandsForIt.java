package souther.compiler.hash;

/**
 * A value that names the value standing for it: what its own number is taken from, and what a walk
 * proving a number is taken from values reads in its place.
 *
 * <p>Said by the value and not about it. A table of class names and part names kept somewhere else
 * is a second writing of what a value holds, and the two come apart the first time a value is given
 * a part: the class compiles, the hash is over what it was, and the walk proves something about a
 * value nobody builds. What is named here is named where the parts are, so a part joins both at
 * once or neither.
 *
 * <p><b>What may be named is anything the value's equality already agrees with.</b> Two values that
 * are equal name things that are equal — that is the whole of what a hash owes an equality — and a
 * value told apart by more than what stands for it is free to name the less. So this is not a
 * second spelling of the equality: it is what may be hashed without giving two equal values two
 * numbers, and where a value is told apart by which object it is, that is anything about the value
 * that does not move.
 *
 * <p><b>Held, not worked out when asked.</b> A value is asked this once for every number taken of
 * it, which for a value used as a key is far more often than one is made. One built at the ask
 * would put the walk back that naming it is for.
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
