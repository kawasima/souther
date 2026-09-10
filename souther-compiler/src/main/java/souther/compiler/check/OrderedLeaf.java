package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;

import souther.compiler.numeric.Place;

import java.util.function.Function;

/**
 * What one comparison leaves the number it names, for a reading that holds an order.
 *
 * <p>One procedure and two readings of it. Where the values at a position stop is one order and how
 * long the string standing there is is another, and each is read by whoever owns it — but what a
 * comparison does to an order does not change with which order it is: what the comparison states of
 * the number it names is asked once ({@link StatedComparison}), and an end is read against the
 * carrier. Written once per reading, the two would be one algorithm with two accounts, and the
 * reading that was not kept in step would answer about a comparison this compiler reads.
 *
 * <p>Which numbers a reading has is the reading's, and arrives as {@code named} and
 * {@code carrierOf}. Nothing here decides what a number is, so neither reading can reach the
 * other's: a caller holding the positions finds no length and a caller holding the lengths finds no
 * position, and each of them says so with the answer for a leaf it could not follow.
 */
final class OrderedLeaf {

    private OrderedLeaf() {}

    /**
     * What the comparison came to, and whether it holds the number it names against another of the
     * same reading's.
     *
     * @param againstAnother a fact about the two sides and about neither claim. A rule holding one
     *                       of this reading's numbers to another draws its line between them rather
     *                       than at either, so nothing about where this one stops waits on a reader
     *                       — which stays true where the reading has no range for the rule
     */
    record Read<K>(Left<K> left, boolean againstAnother) {}

    /** Which of the three a comparison came to. */
    sealed interface Left<K> {

        /** A rule this reading lost the thread of: a shape it has no word for, a number it cannot
         *  name, a bound that is no literal of the order. */
        record NotFollowed<K>() implements Left<K> {}

        /** A rule it followed to the end that stops the values nowhere — what a disequality
         *  states, which is a set of values and not a range. */
        record PlacesNoEnd<K>() implements Left<K> {}

        /**
         * A rule it followed, leaving {@code number} inside {@code range}.
         *
         * <p>What the rule states and not what the order holds. A reading that says where a value
         * may stand wants the two together — what is below the empty string is nothing, and a range
         * open below would take {@code value < ""} for a rule leaving room underneath — and a
         * reading looking for the line an author drew wants this one: the largest whole number is
         * where every count stops whether or not anybody wrote a rule, and a line there is a row
         * owed at a place no clause put an edge at.
         *
         * @param carrier what {@code number} is ordered on, for a reader that wants the order's own
         *                ends as well
         */
        record Leaves<K>(K number, Carrier carrier, OrderedInterval range) implements Left<K> {

            /** The same, held inside what the order itself holds. */
            OrderedInterval within() {
                return carrier.extent().meet(range);
            }

            /**
             * The ends this rule states, each held no further out than the order reaches, and none
             * of the order's own added.
             *
             * <p>{@link #within} does two things and a reader looking for the line an author drew
             * wants one of them. A rule naming a size past the last whole number states a line the
             * order does not reach, and clamping it is what keeps a border off a place no value is
             * at; a rule bounding a size below states nothing above, and the largest whole number
             * arriving as its upper end is a line nobody wrote.
             *
             * <p>Nothing at all where the two share no value, which is the same answer
             * {@link #within} gives: a rule whose end lies wholly past the order holds no value of
             * it, and an end pulled back to the order's would come back holding the values the rule
             * refuses.
             */
            OrderedInterval stated() {
                OrderedInterval held = within();
                if (!Endpoint.someValueLiesBetween(held.low(), held.high())) {
                    return held;
                }
                return new OrderedInterval(range.low() == null ? null : held.low(),
                        range.high() == null ? null : held.high());
            }
        }
    }

    /** A leaf that is no comparison at all, which every reading loses the thread of. */
    static <K> Read<K> notFollowed() {
        return new Read<>(new Left.NotFollowed<>(), false);
    }

    /**
     * What {@code bin} leaves, read against the numbers {@code named} knows.
     *
     * @param positive  whether the comparison stands as written or under a denial. Denied, a
     *                  comparison is the one that leaves what it leaves out: {@code !(x /= v)}
     *                  places both ends and {@code !(x == v)} places none, which is what each of
     *                  them states written directly
     * @param named     which of this reading's numbers an expression is, or null where it is none
     * @param carrierOf what that number's values are ordered on
     */
    static <K> Read<K> of(Core.Binary bin, boolean positive, Denotations at, Terms terms,
                          Function<Core, K> named, Function<K, Carrier> carrierOf) {
        StatedComparison stated = StatedComparison.of(bin, positive);
        if (stated == null) {
            // Written with an operator and not a comparison. The same as a rule of another shape.
            return notFollowed();
        }
        // What the comparison states of the number it names, with the denial and the side it was
        // written on both already spent ({@link StatedComparison#at}). Nothing below applies either
        // of them, so there is nothing below for either to be forgotten at.
        StatedComparison.Numbered<K> said = stated.at(named);
        Carrier carrier = said == null ? null : carrierOf.apply(said.number());
        if (carrier == null) {
            // Neither side is a number this reading has. The rule may still be about one — a
            // length, an absolute value, a reversal — and what it holds that number to is then
            // something this reading cannot follow rather than something it found to be nothing.
            return notFollowed();
        }
        // Whether this rule holds the number to another of the same reading's, which is a fact
        // about the two sides and about neither claim. Written down here, where both sides are in
        // hand and before either is read for what it leaves: put inside what one claim does with
        // its side, the same rule written with a different operator is a rule nobody read
        // ({@code n == m} beside {@code n < m}).
        boolean against = named.apply(said.other()) != null;
        Hir.Expr written = Terms.asWrittenValue(said.other(), at);
        return new Read<>(switch (said.claim()) {
            case ComparisonClaim.Singled singled -> singled.holdsAtTheValue()
                    ? onlyTheValue(said.number(), carrier, written)
                    : new Left.PlacesNoEnd<>();
            case ComparisonClaim.Cut cut ->
                    ends(said.number(), carrier, InvariantBound.at(cut, written, carrier));
        }, against);
    }

    /** The range of one value, or nothing where the rule meets the number at something this order
     *  has no literal for. */
    private static <K> Left<K> onlyTheValue(K number, Carrier carrier, Hir.Expr written) {
        Place only = written == null ? null : carrier.literalOf(written);
        return only == null ? new Left.NotFollowed<>()
                : leaves(number, carrier,
                        new OrderedInterval(Endpoint.inclusive(only), Endpoint.inclusive(only)));
    }

    /** What the end an ordering placed leaves the number. */
    private static <K> Left<K> ends(K number, Carrier carrier, InvariantBound.Read read) {
        return switch (read) {
            case InvariantBound.Read.AnEnd it -> leaves(number, carrier, it.bound().lower()
                    ? new OrderedInterval(it.bound().end(), null)
                    : new OrderedInterval(null, it.bound().end()));
            // The rule names an end the order does not reach, so the number holds nothing. Said as
            // a range of this order with no value in it, which is the same kind of answer two rules
            // whose ends cross come to.
            case InvariantBound.Read.PastWhereTheOrderStops _ ->
                    leaves(number, carrier, carrier.nothing());
            // A cut on a number this reading has, against something the order has no literal for.
            // The other reasons NoEnd stands for are answered before this call, so what arrives is
            // always this one.
            case InvariantBound.Read.NoEnd _ -> new Left.NotFollowed<>();
        };
    }

    private static <K> Left<K> leaves(K number, Carrier carrier, OrderedInterval range) {
        return new Left.Leaves<>(number, carrier, range);
    }
}
