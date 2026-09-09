package souther.compiler.numeric;

/**
 * What is left of one position's order once the rules about it have been taken in.
 *
 * <p>A pair of ends, either of which may be absent for no end there. Apart from
 * {@link NumericDomain.Bounds}, which holds the same two things and answers a different question:
 * that one is what a projection of the interval algebra left, and its {@code saysNothing} says that
 * nothing bounds the position at all. Here an empty interval is the opposite — a position the rules
 * leave no value at — and one type answering both would be a word that means "everything" to one
 * reader and "nothing" to the next.
 *
 * <p>Both ends are places on one order. Nothing here compares across two, and a pair built from two
 * scales' places says so by throwing where they are compared ({@link Place#notOneOrder}).
 */
public record OrderedInterval(Endpoint low, Endpoint high) {

    /** Every value of the order, for a scale that stops nowhere. */
    public static final OrderedInterval OPEN = new OrderedInterval(null, null);

    /**
     * Whether the rules leave this position no value.
     *
     * <p>Asked of the ends and not of the numbers between them. Over a scale whose values step, a
     * strict end is moved onto the value beside it where it is read, so what reaches here is two
     * ends and the question of whether anything is inside both.
     */
    public boolean holdsNothing() {
        return !Endpoint.someValueLiesBetween(low, high);
    }

    /**
     * Whether this and {@code other} leave the same values of the order they are both ranges of.
     *
     * <p>{@link Object#equals} asked of the values rather than of the writing, and one layer short
     * of the whole question. Two ends at one place are one end however the rule that put them there
     * spelled the number, which {@link Endpoint#sameAs} decides and a record's derived equality does
     * not — it reaches {@link java.math.BigDecimal#equals} and tells {@code 3.0} from {@code 3.00}.
     * And a range with no value in it leaves what every other empty range leaves, whichever ends it
     * arrived with.
     *
     * <p><b>The order's own values and not a carrier's.</b> An absent end is no end and is not the
     * same end as one at the last count a carrier has, so {@code [MIN..MAX]} and a pair of absent
     * ends come back different here — which is right of the pairs and is not the question a reader
     * asking about a position has. That reader interprets both against what its order holds
     * ({@link OrderedIntervals#valuesAt}) and asks this of the results.
     *
     * <p>Beside {@code equals} rather than replacing it. What is written down is what a report
     * writes back, and a value used as a map key is keyed by how it was written unless somebody
     * canonicalised it — so the derived equality stays the structural one, and this is the question
     * a reader about values asks by name.
     */
    public boolean sameValuesAs(OrderedInterval other) {
        if (holdsNothing()) {
            return other.holdsNothing();
        }
        return !other.holdsNothing()
                && sameEnd(low, other.low) && sameEnd(high, other.high);
    }

    /** Whether two ends are the same end, an absent one being no end and so the same as no other. */
    private static boolean sameEnd(Endpoint one, Endpoint other) {
        return one == null ? other == null : one.sameAs(other);
    }

    /**
     * The ends of this that {@code order} does not already stop the values at.
     *
     * <p>What a rule states, out of what a reading of it left. Every whole number stops at the
     * largest one whether or not anybody wrote a rule, so an end there is not a line an author
     * drew — and a reader downstream cannot tell that end from one a rule states, so it would draw
     * a line at it and send somebody to a rule that says nothing about it.
     *
     * <p>Nothing struck off where the range holds no value. Such a range is a rule stepping past
     * the end of its own order, and an end pulled off it would come back holding the values the
     * rule refuses.
     *
     * <p>A question about the writing and not about the values, which is why it is here and not
     * beside {@link #sameValuesAs}: what comes back leaves more values than this does, and is read
     * by whoever is looking for the line somebody wrote.
     *
     * <p><b>Where an end coincides with the order's, and not where clamping put one there.</b> The
     * two part company over a rule that names the order's own last value: this strikes that end off
     * whoever wrote it, and a reader holding the rule as written can tell the author's from the
     * clamp's and keeps it ({@code OrderedLeaf.Left.Leaves#stated}). What has been through a
     * connective has no such reader — the ends of a join are nobody's in particular — which is what
     * this is for.
     */
    public OrderedInterval endsStatedWithin(OrderedInterval order) {
        if (holdsNothing()) {
            return this;
        }
        return new OrderedInterval(sameEnd(low, order.low()) ? null : low,
                sameEnd(high, order.high()) ? null : high);
    }

    /**
     * Whether {@code at} is inside both ends.
     *
     * <p>Asked of the ends, because whether an end is one of the places it stops at is what the
     * place alone does not say.
     */
    public boolean admits(Place at) {
        return Endpoint.someValueLiesBetween(low, Endpoint.inclusive(at))
                && Endpoint.someValueLiesBetween(Endpoint.inclusive(at), high);
    }

    /** Both, which is what a conjunction of rules leaves: a value inside this one and inside the
     *  other. */
    public OrderedInterval meet(OrderedInterval other) {
        return new OrderedInterval(Endpoint.lower(low, other.low),
                Endpoint.upper(high, other.high));
    }

    /**
     * Either, as far as a pair of ends can say it.
     *
     * <p>The hull and not the union. A choice between two ranges is not a range where they do not
     * touch, and the ends around both admit everything either of them does — which is the safe
     * direction here, since what this decides is that a position has no value.
     *
     * <p>An empty side contributes no value to the hull, so the hull of one with a non-empty side is
     * the other side. Hulled as ends instead, an empty range would stretch the answer over values
     * neither side holds — its ends are places on the order all the same, and nothing lies between
     * them.
     *
     * <p>Whether an alternative is one anybody can take is not asked here, and not by
     * {@link OrderedIntervals} either. That is about a whole reading of a clause, which is more than
     * the ranges: it is settled where the languages a clause is read in are held together, and what
     * reaches this is one position of a branch whose fate is known.
     */
    public OrderedInterval join(OrderedInterval other) {
        if (holdsNothing()) {
            return other;
        }
        if (other.holdsNothing()) {
            return this;
        }
        return new OrderedInterval(widest(low, other.low, true),
                widest(high, other.high, false));
    }

    /** The looser of two ends, a {@code null} being no end at all and so the loosest there is. */
    private static Endpoint widest(Endpoint a, Endpoint b, boolean lower) {
        if (a == null || b == null) {
            return null;
        }
        Endpoint tighter = lower ? Endpoint.lower(a, b) : Endpoint.upper(a, b);
        return tighter.equals(a) ? b : a;
    }
}
