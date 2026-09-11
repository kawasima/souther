package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.Towards;

/**
 * Where one conjunct of a numeric newtype's invariant leaves its value able to stop.
 *
 * <p>Read off the comparison. What a report measures a position at is where the rules about it stop,
 * and that is not the same question as which runtime check the rule becomes: the runtime states a
 * decimal above zero directly and has no word for a decimal above five, so a reader written for it
 * answers "no bound" to a rule that plainly draws one. Asked here of an ordering of
 * {@code value} against a literal, and of one end at a time. An equality states both ends at once
 * and is not read: it was not read before either, and giving a position one value is a different
 * answer from bounding it, which the report has nowhere to put yet.
 *
 * <p>Over whole numbers a strict comparison names the adjacent value, and the end lands on a value
 * the rule admits. Over decimals there is no adjacent value, so the end stays on the literal and says
 * that the literal is not one of its own.
 *
 * @param lower whether this bounds the value below; otherwise above
 */
public record InvariantBound(boolean lower, Endpoint end) {

    /**
     * What a reading of one ordered rule came to.
     *
     * <p>Three answers and not two. A rule either places an end, or states none this reads, or
     * states one the order does not reach — and the third was being given the second's answer. They
     * are opposite facts: nothing follows about a position from a rule nobody read, and everything
     * follows from a rule that steps past the last value there is. Told apart here, at the one place
     * an ordered rule is read, so that a reader cannot arrive at the wrong one by leaving a case out.
     */
    public sealed interface Read {

        /** The end the rule places. */
        record AnEnd(InvariantBound bound) implements Read {}

        /**
         * The rule states no end this reading has a word for.
         *
         * <p>An equality states both ends at once and a disequality states neither; a literal the
         * order does not read is not an end either. What they share is that this says nothing about
         * where the position stops, and a reader may narrow nothing by them.
         */
        record NoEnd() implements Read {}

        /**
         * The rule states an end past the last value of the order, so it admits nothing.
         *
         * <p>Reached where a strict end is sharpened onto the value beside the one named and the
         * order has none there: one past the last case of an enumeration, one past the last day a
         * calendar reaches, one past the greatest whole number. The position holds no value, which
         * is a fact about the rule and not a limit of this reading.
         */
        record PastWhereTheOrderStops() implements Read {}
    }

    private static final Read NO_END = new Read.NoEnd();
    private static final Read PAST_THE_END = new Read.PastWhereTheOrderStops();

    /**
     * The end an ordering places on a coordinate already recognised, or empty where the comparison
     * places none.
     *
     * <p>Entered with the coordinate settled. Which number a clause is about, and whether what it
     * states of that number is a cut at all, is the walk's answer: it goes inside the conjunct, so a
     * rule stated through a helper and one written as the denial of its opposite arrive here as the
     * cut they state. What is left is where the comparison leaves the end, which is one question
     * with one answer whatever recognised the coordinate.
     *
     * @param cut   what the comparison placed, stated of the coordinate
     * @param bound what the coordinate is compared against
     */
    static Read at(ComparisonClaim.Cut cut, Hir.Expr bound, Carrier carrier) {
        if (carrier == null || bound == null) {
            return NO_END;
        }
        Place at = carrier.literalOf(bound);
        return at == null ? NO_END : ordered(cut, at, carrier);
    }

    /**
     * One end, from what the comparison placed and how the carrier's counts are spaced.
     *
     * <p>Which end it is and whether the end admits the number are the claim's two answers: a rule
     * bounds a value below exactly where the values it admits are above the number it named, and
     * the end is the number itself exactly where the rule holds there. What is left for this to
     * decide is where a refused number leaves the end, which is a fact about the order and not
     * about the comparison.
     */
    private static Read ordered(ComparisonClaim.Cut cut, Place bound, Carrier carrier) {
        boolean lower = cut.satisfyingSide() == Towards.ABOVE;
        if (cut.holdsAtTheValue()) {
            return placed(lower, Endpoint.inclusive(bound));
        }
        if (carrier.spacing() != Granularity.DISCRETE) {
            return placed(lower, Endpoint.exclusive(bound));
        }
        Count number = Count.number(bound);
        return stepped(lower, carrier.onTheGrid(lower ? number.plus(1) : number.minus(1)));
    }

    private static Read placed(boolean lower, Endpoint end) {
        return new Read.AnEnd(new InvariantBound(lower, end));
    }

    /**
     * A strict bound moved onto the count beside it — where the carrier has one there.
     *
     * <p>Asked of the carrier, which is the one place that knows where its counts stop. Read off the
     * range of a {@code long} instead, an end one step past the last case of an enumeration was a
     * count no case is at: it reached a cut, an obligation, and the reader that writes an obligation
     * as the value it stands for, which asked the carrier for a case that is not there.
     *
     * <p>Where the carrier has no count there, the rule admits nothing. That is said as itself. It was
     * answered "no end this reading could make of it", which is what a rule of another shape gets —
     * so a position the rules leave empty read exactly like a position nothing was written about,
     * and the count deciding whether the type has any value never heard of the rule.
     */
    private static Read stepped(boolean lower, Place onto) {
        return onto == null ? PAST_THE_END
                : placed(lower, Endpoint.inclusive(onto));
    }

}
