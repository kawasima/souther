package souther.compiler.numeric;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the ordered rules leave each position, over all the rules a reading took in.
 *
 * <p>A state and not an interval. A rule is written about a whole value and may name several of its
 * positions, and the connectives join whole readings rather than the range at one position — so what
 * a conjunction and a disjunction are applied to is this, and the arithmetic at one position is
 * {@link OrderedInterval}.
 *
 * <p><b>Bottom is a state and not always a position.</b> The rules leave nothing where some position
 * is left no value — and also where every alternative of a choice is one nobody can take, which is
 * not a fact about any one position. The second is {@code nothing}, and it is why the two cannot be
 * one answer: a choice between {@code a < ""} and {@code b < ""} admits nothing, and neither
 * {@code a} nor {@code b} is a position the choice leaves empty, since each alternative admits every
 * value of the other's.
 *
 * <p><b>A position not held here is every value its order has.</b> Which is what makes a meet with
 * what the rules said the whole answer, and what makes a join drop a position only one side bounded:
 * joining with everything is everything.
 *
 * <p><b>One direction only.</b> This exists to decide that a position holds no value, so every
 * operation that cannot be exact widens. A choice between two ranges is the ends around both, which
 * admits values neither alternative does; a state that narrowed there would refuse a model somebody
 * can write.
 *
 * <p>Apart from {@link NumericDomain}, which relates positions to each other by differences and can
 * only do so over numbers a model adds. This relates no two positions and holds every order there
 * is — so a rule comparing a date against a written one lands here, and one comparing two fields of
 * a record lands there. Both may hold the same rule about an {@code Int}, and neither is the other's
 * copy: what each of them can show is its own, and a contradiction shown anywhere is a contradiction.
 *
 * <p><b>The states are the ones these operations reach.</b> The two parts depend on each other —
 * {@code nothing} says the whole is empty where no position is, and a position left an empty range
 * says it where the whole need not carry it — so a pair of them written down side by side is a
 * combination nothing read. What the parts are is this one's own; a caller reaches a state by doing
 * to a reading what the state says was done to it.
 */
@souther.compiler.reading.StateOfAReading
public final class OrderedIntervals<A> {

    /**
     * The parts, together, so that everything answered from all of them is answered from one place.
     *
     * <p>Equality and the parts a reader may be shown are the whole of what this is, and both of
     * them are the record's. A part added here arrives in each of them the day it is declared, which
     * is what keeps a state that differs from telling a caller it does not.
     */
    private record Parts<A>(Map<A, OrderedInterval> ranges, boolean nothing) {

        private Parts {
            // Kept in the order the positions were read. What is written out of these has to come
            // out the same on two runs of the compiler, and the iteration order of an immutable
            // copy does not.
            ranges = Collections.unmodifiableMap(new LinkedHashMap<>(ranges));
        }
    }

    private final Parts<A> parts;

    /** The one constructor there is, and it takes the parts as one — see {@code AdmissibleValues},
     *  where a maker handed the parts is what a reading may not be come by. */
    private OrderedIntervals(Parts<A> parts) {
        this.parts = parts;
    }

    /** Nothing read, so every position holds every value its order has. */
    public static <A> OrderedIntervals<A> top() {
        return new OrderedIntervals<>(new Parts<>(Map.of(), false));
    }

    /** One position said to lie inside {@code range}. */
    public static <A> OrderedIntervals<A> at(A position, OrderedInterval range) {
        return new OrderedIntervals<>(new Parts<>(Map.of(position, range), false));
    }

    private Map<A, OrderedInterval> ranges() {
        return parts.ranges();
    }

    private boolean nothing() {
        return parts.nothing();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OrderedIntervals<?> it && parts.equals(it.parts);
    }

    @Override
    public int hashCode() {
        return parts.hashCode();
    }

    @Override
    public String toString() {
        return parts.toString();
    }

    /**
     * The positions these rules bounded, which is what a caller asking what this took in is asking.
     *
     * <p>The positions and not the ranges, which are this one's own: what a rule left a position is
     * read through {@link #at}, and a caller holding the map would be holding a state to work its
     * own answers out of.
     */
    public Set<A> boundedAt() {
        return ranges().keySet();
    }

    /**
     * The ends these rules put on {@code position}, or null where they put none.
     *
     * <p>The writing and not the values. Null is the absence itself, said as itself: answered with
     * a pair of absent ends, a position no rule spoke of would be handed back as a range, and a
     * range is read against an order — so the answer would be right about a decimal, wrong about an
     * {@code Int}, and indistinguishable either way. Which values the position is left is
     * {@link #valuesAt}, and it is the question a reader comparing two readings has.
     */
    public OrderedInterval statedAt(A position) {
        return ranges().get(position);
    }

    /**
     * Which values of its own order {@code position} is left.
     *
     * <p>What the class above promises: a position not held here is every value its order has, and
     * that is what comes back for one — not a pair of absent ends, which is every value of an order
     * that stops nowhere and is more than an order that stops has.
     *
     * <p>The one reading a caller may compare, and the reason the two are separate methods. A
     * choice between {@code n >= 2} and {@code n <= 0} leaves an {@code Int} where it found it, and
     * a reading that answered from the ends alone would have {@code [MIN..MAX]} on one side and a
     * pair of absent ends on the other and call them two answers — which is how a border nobody
     * drew was reported as one this compiler could not measure.
     *
     * <p>Handed the order rather than an interval standing for it, so that a caller cannot hand in
     * the extent of a position beside this one: what a position is ordered on is the carrier's to
     * say, and there is no reading of this that is about two carriers.
     *
     * <p>A null order is a position ordered on nothing this reading names, which is a state a
     * vocabulary is in and not a caller forgetting to say. There is no order to hold the ends
     * against, so what comes back is what was written and nothing added — every value where nothing
     * was written, which is the one answer that cannot be wrong about an order nobody named.
     */
    public OrderedInterval valuesAt(A position, ValueOrder onItsOrder) {
        OrderedInterval stated = ranges().get(position);
        if (onItsOrder == null) {
            return stated == null ? OrderedInterval.OPEN : stated;
        }
        OrderedInterval extent = onItsOrder.extent();
        return stated == null ? extent : extent.meet(stated);
    }

    /**
     * The positions these rules leave holding less than every value of their own order.
     *
     * <p>Where an end becomes a line, which is the one thing a reader of the ends is really asking
     * and the one thing a set of bounded positions cannot say. A pair of bounds reaching both ends
     * of a carrier is two rules about the position and stops it nowhere, and a caller that took
     * {@link #boundedAt} for this would credit such a rule with a line nobody draws.
     *
     * <p>Over the positions the rules bounded, since a position they bounded nowhere is left every
     * value its order has. A position ordered on nothing {@code orders} names is one there is no
     * order to hold the ends against, and it is here: what its rules leave cannot be shown to be
     * all of anything.
     */
    public Set<A> stoppedShortOfTheirOrders(Map<A, ? extends ValueOrder> orders) {
        Set<A> out = new LinkedHashSet<>();
        ranges().forEach((position, range) -> {
            ValueOrder on = orders.get(position);
            if (on == null || !on.extent().meet(range).sameValuesAs(on.extent())) {
                out.add(position);
            }
        });
        return Collections.unmodifiableSet(out);
    }

    /** Whether nothing satisfies these rules, at a position or otherwise. */
    public boolean isBottom() {
        return nothing() || ranges().values().stream().anyMatch(OrderedInterval::holdsNothing);
    }

    /**
     * Every position the rules leave no value at.
     *
     * <p>All of them and not one. Which of several a refusal is written about is settled by the
     * order the positions are declared in, which is a fact about the declaration and not about this
     * — a state answering with one of them would settle it by the order the clauses happened to be
     * read in.
     */
    public Set<A> holdingNothing() {
        Set<A> out = new LinkedHashSet<>();
        ranges().forEach((position, range) -> {
            if (range.holdsNothing()) {
                out.add(position);
            }
        });
        return Collections.unmodifiableSet(out);
    }

    /** Both readings holding at once. */
    public OrderedIntervals<A> meet(OrderedIntervals<A> other) {
        Map<A, OrderedInterval> out = new LinkedHashMap<>(ranges());
        other.ranges().forEach((position, range) ->
                out.merge(position, range, OrderedInterval::meet));
        return new OrderedIntervals<>(new Parts<>(out, nothing() || other.nothing()));
    }

    /**
     * The same bounds on the same positions, under the names {@code naming} gives them.
     *
     * <p>The naming has to name two positions two positions. Two of them arriving under one name
     * would be bounded by each other's rules, which narrows a position by a rule nobody wrote about
     * it — and this is a state whose whole purpose is deciding that a position holds no value, so a
     * narrowing invented here refuses a model somebody can write. Not checked here, because what a
     * naming must not collide over is every subject of every domain of one reading and no domain can
     * see the others; it is checked where a whole vocabulary is
     * ({@code souther.compiler.check.InjectiveRenaming}). Every position held here passes through
     * the naming, so a caller holding one of those sees all of them.
     *
     * <p>Apart from {@link NumericDomain#over}, which is a fold and adds the coefficients of two
     * atoms that arrive at one name. That is right of a form, where a caller may have written two
     * spellings of one number, and it is not what this is.
     */
    public <B> OrderedIntervals<B> renamed(java.util.function.Function<A, B> naming) {
        Map<B, OrderedInterval> out = new LinkedHashMap<>();
        ranges().forEach((position, range) -> out.put(naming.apply(position), range));
        return new OrderedIntervals<>(new Parts<>(out, nothing()));
    }

    /**
     * A choice no alternative of which anybody can take, said of the ranges.
     *
     * <p>No side speaks for the other, so answering with either would settle which position is
     * named by the order the two were written in. Nor may the two be met: a meet is a conjunction,
     * and the alternatives were never stated together — {@code (a < "" && b == 0) || (a < "" && b
     * == 1)} met is a {@code b} bounded at 0 and at 1, which is a contradiction neither alternative
     * contains and a position the rules are fine with.
     *
     * <p>What the choice leaves empty is what <em>every</em> alternative leaves empty, which is the
     * positions in both. Where there are none, the choice still admits nothing and no position is at
     * fault, and that is said as itself.
     *
     * <p>This realises a decision rather than making one, and it does not ask what it was made of.
     * Which alternatives nobody can take turns on what every language reading the clause left, and
     * a branch these ranges are perfectly happy with is one the values may have refused — so what
     * arrives is the ranges as they were read, of a branch something else settled. What is answered
     * is what the two of them leave empty between them, and the whole is said to hold nothing
     * because the choice does, not because these ranges show it.
     *
     * <p>Which is why there is no question here about the sides. Asked whether they hold nothing,
     * this would be deciding what it was called to realise; told a side that stands, it answers
     * that a choice somebody can take admits nothing, which is the one direction a state deciding
     * emptiness may not move in. What keeps that from arriving is the caller having settled it,
     * and there is no reading of a caller that has not.
     */
    public OrderedIntervals<A> bothDead(OrderedIntervals<A> other) {
        Map<A, OrderedInterval> both = new LinkedHashMap<>();
        for (A position : holdingNothing()) {
            if (other.holdingNothing().contains(position)) {
                // Both hold the position, since both leave it no value, so the ends are there to
                // be met and no order has to be named to find them.
                both.put(position, statedAt(position).meet(other.statedAt(position)));
            }
        }
        return new OrderedIntervals<>(new Parts<>(both, true));
    }

    /**
     * Either reading holding, both alternatives being ones somebody can take.
     *
     * <p>Over the positions both spoke about, since a position one of them left open is one the two
     * of them together leave open.
     *
     * <p>Which alternatives those are is not decided here, for the reason {@link #bothDead} gives:
     * a branch nobody can be in is shown by what every language left, and a choice settled from the
     * ranges alone would drop a branch no order admits while keeping one no set of values admits.
     * A choice every alternative of which is dead is {@link #bothDead}, and a choice with one dead
     * alternative is the other alternative, which the caller holds already.
     *
     * <p>An assertion and not a branch, because a caller passing a side nobody can take is this
     * compiler disagreeing with itself rather than anything a model says. With assertions off the
     * hull is taken over what the two spoke about, which admits everything either of them did: an
     * empty range at a position both named contributes nothing to the hull, and a position only the
     * live side named is left out and so left open. What is lost is what the dead side proved, and
     * no end is invented — which is the direction this state is only ever allowed to move in.
     */
    public OrderedIntervals<A> joinLive(OrderedIntervals<A> other) {
        assert !isBottom() && !other.isBottom()
                : "a choice of two live alternatives was asked of " + this + " and " + other;
        Map<A, OrderedInterval> out = new LinkedHashMap<>();
        ranges().forEach((position, range) -> {
            OrderedInterval there = other.ranges().get(position);
            if (there != null) {
                out.put(position, range.join(there));
            }
        });
        return new OrderedIntervals<>(new Parts<>(out, false));
    }
}
