package souther.compiler.check;

import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How far the values at each position of one declaration reach, once every connective its clauses
 * are written with has been spent.
 *
 * <p>An observation of a reading that is over, and not a reading. What a choice of two bounds
 * leaves a position is worked out where the branches have their fate ({@link Confinement.Planned});
 * this is that answer read off and carried to where a line is drawn, in the vocabulary a reader of
 * the declaration's names has. Nothing here composes anything, and no operation takes one of these
 * and answers with another — a second composition of one choice is two answers about it, and the
 * cost of that is what {@link BoundaryState} was written to stop on the numbers beside these.
 *
 * <p><b>An envelope, and never the values.</b> {@code n == 2 || n == 5} leaves the outermost ends
 * at two and five, and nothing stands at three: what is here is the smallest run covering what
 * either alternative reaches, which is where the lines are and is not what the rules admit. Which
 * values may stand at a position is the values reading's, and a caller that took this for it would
 * offer a row at a value every rule refuses.
 *
 * <p><b>And no part of whether a value exists.</b> A position the rules leave nothing at is absent
 * here, told apart from one no rule spoke of by nothing — both are positions no line may be drawn
 * on, and that is the whole of what a reader of this asks. The distinction the composition needed
 * ({@link BoundaryState.Left.NothingLeft}) was needed because a choice between an empty branch and
 * a live one leaves what the live one leaves, and there is no choice left to take here. So the
 * losing of it is deliberate: what cannot be read back cannot be read for an answer that belongs to
 * whoever settles whether anybody is in a branch.
 */
final class SettledOrderEnvelope {

    /** Every position some rule stopped, and where. A position absent from this is one no line may
     *  be drawn on, whether because nothing spoke of it or because nothing is left at it. */
    private final Map<RuleKey, OrderedInterval> byPosition;

    private SettledOrderEnvelope(Map<RuleKey, OrderedInterval> byPosition) {
        this.byPosition = byPosition;
    }

    /** What a declaration none of whose positions any rule stopped leaves them. */
    private static final SettledOrderEnvelope NOTHING = new SettledOrderEnvelope(Map.of());

    /** The same, for a caller with no reading to project. */
    static SettledOrderEnvelope nothing() {
        return NOTHING;
    }

    /**
     * The envelope of {@code ordered}, spelled by the names {@code aliases} files each position
     * under.
     *
     * <p>Made from an order and not from a map of ranges, so that what is here came from a reading
     * that had composed the connectives: the one type holding an order is the one that composes it
     * ({@code WhatDecidesWhetherAValueExistsHoldsBothLanguagesTest}), and a caller cannot arrive
     * with an envelope of its own.
     */
    static <A> SettledOrderEnvelope of(OrderedIntervals<A> ordered, Map<A, Carrier> carriers,
                                       Map<RuleKey, ? extends Collection<A>> aliases) {
        Map<RuleKey, OrderedInterval> out = new LinkedHashMap<>();
        aliases.forEach((position, subjects) -> {
            OrderedInterval reach = acrossAliases(ordered, subjects);
            // A position the rules stop nowhere runs as far as it ever did, and one they stop past
            // themselves has no value for a line to fall at. Neither is written down: what a reader
            // here does with a position it does not find is leave the order where it was, which is
            // the answer to the first — and the second is a contradiction, which is said by whoever
            // answers whether a value exists and never by a line drawn where the order does not
            // reach.
            if (reach.holdsNothing()) {
                return;
            }
            OrderedInterval stated = stated(reach, orderOf(carriers, subjects));
            if (stated.equals(OrderedInterval.OPEN)) {
                return;
            }
            out.put(position, stated);
        });
        return out.isEmpty() ? NOTHING : new SettledOrderEnvelope(out);
    }

    /**
     * The ends of {@code reach} some rule put there, which are the ones it does not share with the
     * order the position runs on.
     *
     * <p>Every whole number stops at the largest one whether or not a rule was written, and this is
     * read for the line an author drew. Taken whole, every choice of two bounds on a number would
     * put an end where the order already was — and a reader downstream cannot tell that end from one
     * a rule states, so it would draw a line at it and send somebody to a rule that says nothing
     * about it.
     *
     * <p>The rule itself is {@link OrderedInterval#endsStatedWithin}, where it is one operation on
     * the writing rather than a second reader's copy of one.
     */
    private static OrderedInterval stated(OrderedInterval reach, Carrier on) {
        if (on == null) {
            // A position ordered on nothing this reading names is one no rule of the order reached
            // either, so there are no ends here to be held against anything. Where some rule did
            // reach it, this compiler is holding a range on an order it cannot name, which is what
            // asking the same question of the ranges says
            // ({@link OrderedIntervals#valuesAt}) and is said the same way here.
            if (!reach.equals(OrderedInterval.OPEN)) {
                throw new IllegalStateException(
                        "the rules stopped a position on an order nothing here names: " + reach);
            }
            return OrderedInterval.OPEN;
        }
        return reach.endsStatedWithin(on.extent());
    }

    /** What the position is ordered on, or null where nothing here says. Every name of one answers
     *  the same, as they are names of one position. */
    private static <A> Carrier orderOf(Map<A, Carrier> carriers, Collection<A> subjects) {
        for (A each : subjects) {
            Carrier carrier = carriers.get(each);
            if (carrier != null) {
                return carrier;
            }
        }
        return null;
    }

    /**
     * What the order leaves one position, taken over every name it answers to.
     *
     * <p>Not a conjunction. The names are one position's, filed as two because a clause is filed
     * under whichever of them the reading that read it recognised — so each is a reading of the
     * same values and every value of the position is inside both, which is what makes the tighter
     * of them an answer about the position rather than about a pair of them.
     */
    private static <A> OrderedInterval acrossAliases(OrderedIntervals<A> ordered,
                                                     Collection<A> subjects) {
        OrderedInterval reach = OrderedInterval.OPEN;
        for (A each : subjects) {
            // The ends as written, since what is read off this is which of them a rule put there
            // and the answer is held against the order a line further on. Read as values, every
            // position of a carrier that stops would arrive carrying that carrier's own ends and
            // the question below would have nothing left to strike off.
            OrderedInterval stated = ordered.statedAt(each);
            if (stated != null) {
                reach = reach.meet(stated);
            }
        }
        return reach;
    }

    /** Where the rules stop {@code position}, or null where no line may be drawn on it. */
    OrderedInterval knownAt(RuleKey position) {
        return byPosition.get(position);
    }

    /** Whether the rules stopped no position of this declaration anywhere, which is most of them. */
    boolean isEmpty() {
        return byPosition.isEmpty();
    }

    @Override
    public String toString() {
        return byPosition.toString();
    }
}
