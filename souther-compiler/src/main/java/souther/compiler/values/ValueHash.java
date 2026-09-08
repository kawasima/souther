package souther.compiler.values;

/**
 * How a value here is hashed from what it holds.
 *
 * <p>A value whose hash is worked out from its parts is nearly always handed to something that adds
 * hashes up: a set of them sums what it holds, a map sums its entries, and a record built over one
 * carries its last component into its own hash unchanged. So a hash that is an affine function of
 * its parts — a sum of them, or a sum with each part multiplied by something — is a hash whose
 * parts are still separable once it arrives there, and the sum above cancels what the value was
 * built to state. Two pairs over four blocks come to one number whenever the same four blocks are
 * paired the other way round; two lacks over four blocks come to one number whenever the same
 * blocks are shared out between them differently. What is lost in each is the pairing, which is the
 * one thing those values exist to say.
 *
 * <p><b>So the parts are gathered and then finished, once, at the boundary of the value that owns
 * them.</b> Finishing is not distributive over the sum above it, which is what stops the two levels
 * cancelling. Where the finishing is matters as much as that it happens: finishing a whole set of
 * lacks after the sum has already been taken finishes a number the pairing has already left, and
 * recovers nothing. Every value gathers its own parts and finishes its own number, and hands the
 * finished one up.
 *
 * <p><b>What a caller names here is the shape of the value's equality, not a mixing step.</b> A
 * roled pair and an unordered pair are hashed differently because they are equal differently, and a
 * caller that reached for a mixing function would still have to decide how to gather two parts
 * symmetrically — which is the decision that went wrong. So there is no mixing step to call: each
 * of these takes the parts and answers the whole number, and a value's hash is one call to one of
 * them. Which one is the same question as which equality the value has, and is readable beside it.
 *
 * <p>The kind is taken in because two values of different kinds holding alike are two values, and
 * where both are cases of one sum type nothing else here tells them apart — a block stated apart
 * from itself and a block left no value name the same block and claim different things.
 */
final class ValueHash {

    /** What a part already gathered is multiplied by before the next joins it, so that the parts
     *  keep their places. Odd, so no bit of what is already there is lost in the multiplication. */
    private static final int GATHER = 31;

    /** The two odd multipliers the finishing is built from. Each carries what the shift before it
     *  folded downwards back up into the high bits, which is what leaves the answer depending on
     *  every bit of what was gathered rather than on the low ones a multiplication reaches. */
    private static final int SCATTER = 0x85ebca6b;

    private static final int SPREAD = 0xc2b2ae35;

    private ValueHash() {
    }

    /** A value of {@code kind} holding one thing. */
    static int ofOnePart(Class<?> kind, int part) {
        return finished(GATHER * seed(kind) + part);
    }

    /** A value of {@code kind} holding two, each in its own place: exchanging them is another
     *  value, and this answers another number. */
    static int ofItsParts(Class<?> kind, int first, int second) {
        return finished(GATHER * (GATHER * seed(kind) + first) + second);
    }

    /** A value of {@code kind} holding three, each in its own place. */
    static int ofItsParts(Class<?> kind, int first, int second, int third) {
        return finished(GATHER * (GATHER * (GATHER * seed(kind) + first) + second) + third);
    }

    /**
     * A value of {@code kind} that is two things with no order between them, so that it is hashed
     * alike whichever way round its two ends were written.
     *
     * <p>Gathered from the two numbers a pair of numbers has that do not depend on their order:
     * what they come to added, and what they come to exclusive-ored. The sum alone is what an
     * unordered pair is most easily hashed by and it is what makes such a pair collapse — but here
     * it is what the finishing is given rather than what is handed up, so what the second number
     * adds is not that. It is that two pairs adding alike are two numbers here: {@code 0} with
     * {@code 4} and {@code 1} with {@code 3} add to one number and exclusive-or to two, and a
     * relation's pairs are drawn from few blocks, so pairs adding alike is what it has.
     */
    static int ofAnUnorderedPair(Class<?> kind, int one, int other) {
        return finished(GATHER * (GATHER * seed(kind) + (one + other)) + (one ^ other));
    }

    /**
     * A value of {@code kind} that is however many things with no order between them, from what
     * they come to summed and how many of them there are.
     *
     * <p>Which is what a set or a map hands over: its own hash is the sum of what it holds, and a
     * sum is what a reader wants of it, since two collections holding alike in different orders are
     * one value and have to be one number. The count joins it so that two of them whose contents
     * sum alike are still told apart where they hold different numbers of things.
     */
    static int ofWhatItHolds(Class<?> kind, int summed, int size) {
        return finished(GATHER * (GATHER * seed(kind) + summed) + size);
    }

    /** Which kind of value it is, which is a fact about the program and the same on every run —
     *  unlike what {@code Object.hashCode} would answer about the class. */
    private static int seed(Class<?> kind) {
        return kind.getName().hashCode();
    }

    /** What the gathered parts come to, taken so that adding this to another of these does not
     *  give back a function of the parts. */
    private static int finished(int gathered) {
        int out = gathered ^ (gathered >>> 16);
        out *= SCATTER;
        out ^= (out >>> 13);
        out *= SPREAD;
        return out ^ (out >>> 16);
    }
}
