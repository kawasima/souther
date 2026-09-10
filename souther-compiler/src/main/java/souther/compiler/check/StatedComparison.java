package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.numeric.Rel;

import java.util.function.Function;

/**
 * A comparison a reading arrived at: what it places, and the two values it places it on.
 *
 * <p>What every reader of a condition wants and all it wants. A relation is stated of two sides, and
 * which relation it is comes from what the comparison placed ({@link ComparisonClaim}) — so a reader
 * handed one of these has the whole of what a comparison says without going back to an operator.
 *
 * <p><b>Beside {@link Comparison} and not the same thing.</b> That one is a binary the source wrote,
 * recognised where it stands, and what it carries besides the claim is the node — which is what a
 * reader asking where a comparison is in the tree wants ({@link
 * souther.compiler.coverage.ComparisonCatalog}). This one is a statement, and a statement has no
 * place in the tree: a reading that works out what an operation answering an order proves about its
 * two arguments states a comparison no author wrote, and there is nowhere it stands.
 *
 * <p><b>And it carries no site.</b> A comparison is filed under what it places and the terms its two
 * sides are ({@link Terms}), so where a comparison came from, what it answers and where it stands
 * decide nothing a reader of this asks. Carrying them would leave one field whose only use is
 * putting the node back together, and putting the node back together is what a statement exists
 * instead of. Whoever reports about a clause holds the expression the source wrote and reports about
 * that.
 */
public record StatedComparison(ComparisonClaim claim, Core left, Core right) {

    /**
     * The relation this states, asserted with polarity {@code positive}.
     *
     * <p>Asserted false, what a comparison states is what holds where it does not — which is the
     * claim's own answer ({@link ComparisonClaim#denied}) and not a table of six turned round here.
     * Written once because every reader of a condition asks it under a polarity, and two of them
     * pairing the denial with the relation themselves are two places that can come to pair it
     * differently.
     */
    Rel relationUnder(boolean positive) {
        return (positive ? claim : claim.denied()).statedRelation();
    }

    /**
     * What the part states, or null where it states no comparison.
     *
     * <p>The one way from a part of a clause to what its comparison says. How the part stands is
     * the shape's answer ({@link ClauseExpr#positive}) and what the part is is the shape's too
     * ({@link ClauseExpr.Part#of}), so a reader that takes a part through here is reading the
     * comparison the clause states rather than the one an author happened to spell — and the
     * denial is spent here, once, instead of arriving at each reader as a flag it has to remember
     * to apply.
     */
    static StatedComparison of(ClauseExpr.Part part) {
        return part.of() instanceof Core.Binary bin ? of(bin, part.positive()) : null;
    }

    /** The same of a comparison held under {@code positive} by a reader whose polarity did not come
     *  from a clause's shape. */
    static StatedComparison of(Core.Binary bin, boolean positive) {
        Comparison read = Comparison.of(bin).orElse(null);
        return read == null ? null
                : new StatedComparison(positive ? read.claim() : read.claim().denied(),
                        read.left(), read.right());
    }

    /**
     * The same read from the side {@code named} recognises a number on, or null where it names one
     * on neither.
     *
     * <p>The second half of the crossing from an operator to a statement, and it is spent here for
     * the same reason the denial is. {@code 0 <= n} says what {@code n >= 0} says, and a reader
     * handed the two sides and told which of them bore the number is a reader that has to turn the
     * claim itself — where forgetting to states the comparison that holds exactly where this one
     * does not, which is the denial's failure again one step further on.
     *
     * <p>Which expressions are numbers is the reading's, and arrives as {@code named}. Nothing here
     * decides what a number is, so a reader holding the positions and one holding the lengths reach
     * this the same way and neither can reach the other's.
     */
    <K> Numbered<K> at(Function<Core, K> named) {
        K found = named.apply(left);
        if (found != null) {
            return new Numbered<>(claim, found, right);
        }
        K other = named.apply(right);
        return other == null ? null : new Numbered<>(claim.turned(), other, left);
    }

    /**
     * What one comparison states of one of a reading's numbers.
     *
     * <p>Neither the polarity the clause held it under nor which side it was written on is left
     * here: both are spent making it, and what is left is a claim about {@code number} and
     * {@code other} in that order. A reader below has nothing to apply and nothing to forget.
     *
     * @param other what the number is held against, which may be a constant, a number of the same
     *              reading, or something with no number in it at all — telling those apart is the
     *              reader's and not this
     */
    record Numbered<K>(ComparisonClaim claim, K number, Core other) {}
}
