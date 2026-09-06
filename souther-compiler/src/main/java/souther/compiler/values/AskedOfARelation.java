package souther.compiler.values;

/**
 * What the denials between one alternative's blocks come to, for a reader that has to ask it of
 * every alternative.
 *
 * <p>Beside {@link AskedOfEachBlock} and not instead of it. That one is a question about a block,
 * and every reading below it answers about one block — so a relation between two of them cannot be
 * asked that way, and widening the question until it could would make every reader of a block
 * answer for a pair. This is the other question the same walk is asked, and the walk asks the cheap
 * one first: an alternative some block is already refused at is one no relation has to be read for.
 *
 * <p><b>Asked of the whole alternative and answered before the blocks are.</b> A denial is settled
 * against what its blocks are left, and what one is left is not known until everything placing its
 * positions has been met with it — so the reader that answers this is the one holding the ranges
 * and the restrictions, and what it is handed is the relation and what the alternative says its
 * blocks hold.
 *
 * @param <A> what a position is called
 */
@FunctionalInterface
public interface AskedOfARelation<A> {

    /**
     * What {@code apart} comes to, where {@code product} is what the alternative says each of its
     * blocks holds.
     *
     * @param apart which of the alternative's blocks are stated to hold different values
     * @param product what the alternative says each block holds, before anything outside it has
     *                been met with that
     */
    Apartness.Reduction<A> of(Apartness<A> apart, AdmissibleValues.Box<A> product);
}
