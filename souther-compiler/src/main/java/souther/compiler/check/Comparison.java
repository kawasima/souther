package souther.compiler.check;

import souther.compiler.core.Core;

import java.util.Objects;
import java.util.Optional;

/**
 * A comparison of a checked body: what its operator placed, and the two values it placed it on.
 *
 * <p><b>The three are one value because they have to agree.</b> What a rule places is a function of
 * the operator it was written with, and every reader below the point where a node was recognised as
 * a comparison needs both: the two sides to read, and the claim to say what the rule states about
 * the line. Passed as separate arguments, the set is only as true as each caller made it; held
 * here, there is nowhere for a claim of one operator to arrive beside the sides of another.
 *
 * <p><b>Made in one place, which is what carries the recognition.</b> {@link #of} is the only way
 * in, so a value of this type is itself the evidence that the operator compares — a reader holding
 * one has no case left for an operator that placed nothing, and no reason to go back to the operator
 * and ask again. A {@code boolean} answering the same question leaves the fact behind at the
 * {@code if}, which is how the readings below came to carry the wider classification and answer for
 * a case none of them could be handed.
 *
 * <p><b>And it holds no node.</b> The binary it was recognised from is one call away from the
 * operator, so a reader holding the node can read again what the recognition already answered, and
 * what stopped one doing so was that nobody had yet. It is also how a reader said <em>which</em>
 * comparison it was talking about — two readers meant one place when they had been handed the same
 * object — and that question has an answer of its own now
 * ({@link souther.compiler.types.ConstructOccurrence}), which the node carries. So there are three
 * questions and three answers: what is compared, which comparison it is, and where it is written.
 */
public final class Comparison {

    private final ComparisonClaim claim;
    private final Core left;
    private final Core right;

    private Comparison(ComparisonClaim claim, Core left, Core right) {
        this.claim = claim;
        this.left = left;
        this.right = right;
    }

    /** {@code at} as a comparison, or nothing where its operator compares no values. */
    public static Optional<Comparison> of(Core.Binary at) {
        return switch (ComparisonPlacement.of(at.op())) {
            case ComparisonPlacement.Nothing _ -> Optional.empty();
            case ComparisonClaim claim ->
                    Optional.of(new Comparison(claim, at.left(), at.right()));
        };
    }

    /** What its operator placed on the values. */
    public ComparisonClaim claim() {
        return claim;
    }

    /** The side the claim is stated of. */
    public Core left() {
        return left;
    }

    /** What that side is compared against. */
    public Core right() {
        return right;
    }

    /**
     * The statement this comparison makes, which is what it placed and the two values it placed it
     * on.
     *
     * <p>What a reader wants where the recognition itself decides nothing it asks. A reading that
     * works out what a rule does to a quantity is the same reading whether the comparison was
     * spelled by an author or arrived at from what the rules proved, so it is written against the
     * statement; this is the way over from the one that was spelled. Nothing is dropped that such a
     * reader had a use for — the node it was recognised from is what a report about the clause
     * holds, and a report holds it already.
     */
    public StatedComparison stated() {
        return new StatedComparison(claim, left, right);
    }

    /** Everything this holds, which is what an identity is of. The claim is read off the operator
     *  the sides were written with, so two of these over one binary carry one claim — and it is
     *  read here all the same, because a field left out of an identity is a field a reader of the
     *  identity is told has not changed. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Comparison that && claim.equals(that.claim)
                && left.equals(that.left) && right.equals(that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(claim, left, right);
    }

    @Override
    public String toString() {
        return "Comparison[" + left + " " + claim + " " + right + "]";
    }
}
