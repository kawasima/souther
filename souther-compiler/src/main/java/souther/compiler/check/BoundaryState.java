package souther.compiler.check;

import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What a reading leaves the numbers a value's operations answer: where each of them stops, and
 * which lines on them a rule stated that nothing here could place.
 *
 * <p>Four things a number can be to this reading, and telling them apart is the whole of it.
 *
 * <ul>
 *   <li><b>Unsaid</b> — no rule of this reading spoke of it, so it runs as far as it ever did. It
 *       is absent from both halves below.
 *   <li><b>{@link Left.Known}</b> — some rule stopped it, and this is where.
 *   <li><b>{@link Left.NothingLeft}</b> — the rules stopped it past themselves, so no value of
 *       them is at it.
 *   <li><b>Open</b> — some rule stated a line on it that nothing here worked out, once for each
 *       place an author wrote one ({@link OpenEnd}).
 * </ul>
 *
 * <p>A number can be known and open at once: a conjunction of a bound this reading placed and one
 * it could not is both, and each half is wanted by a different reader.
 *
 * <p><b>Nothing left is a state and not a shape a range happens to take.</b> Ends that have crossed
 * are how such a number arrives, and a reader that had to notice the crossing is a reader that can
 * forget to — which is what happened while the join and the question a choice asks the branch
 * beside it disagreed about whether such a number was bounded. What is written down is the answer.
 *
 * <p><b>One algebra, folded over the two trees this reading is folded over.</b> What the rules of a
 * whole declaration leave a number is settled over the tree the values are derived from, where a
 * conjunction has been distributed into the branches beside it ({@link Confinement.Planned}); what
 * a choice is answerable for is settled over the tree the author wrote, where an alternative is
 * what stands between the brackets ({@link StatedByClauses.Part}). They are two questions and the
 * same operations answer both — a second set of operations would be a second answer, which is what
 * this type exists to stop.
 *
 * <p>Asked of the derived tree, what a choice left open would be read off branches holding
 * conjuncts written outside the brackets: {@code (A || B) && C} distributes to
 * {@code (A && C) || (B && C)}, and a line {@code C} leaves open is then in both alternatives — so
 * the choice would be answerable for a line the author wrote nowhere near it.
 *
 * <p><b>An envelope, and no part of whether a value exists.</b> Two alternatives naming one size
 * each leave the run between them, and no value has a size in there — so this says where the
 * outermost ends are and is read where a line is looked for. It is asked nothing about whether
 * anybody can be in a branch, which is the values' and the orders' between them
 * ({@link Confinement#admission}): a number is bounded by rules the position's own readings have no
 * word for, so a branch this refused would be refused by a reading they cannot check.
 *
 * <p><b>Which is why nothing left is not nothing said.</b> That a branch's rules leave a number no
 * value is this reading's own knowledge, and it may act on it inside itself: a choice with such an
 * alternative leaves what the alternative beside it leaves, because every value of the choice is in
 * that one. What it may not do is hand the emptiness to the fates, and it does not — nobody outside
 * is told, and the branch stands or falls on what the values and the orders say.
 *
 * @param byNumber what the rules leave each number some rule of this reading spoke of
 * @param open     every line on one of these numbers that nothing here placed
 */
record BoundaryState(Map<DerivedNumber, BoundaryState.Left> byNumber, Set<OpenEnd> open) {

    /** What the rules of one reading leave a number they spoke of. */
    sealed interface Left {

        /** They stop it inside {@code range}, which holds a value. */
        record Known(OrderedInterval range) implements Left {}

        /** They stop it past themselves, so no value of them is at it. */
        record NothingLeft() implements Left {}
    }

    private static final Left NOTHING_LEFT = new Left.NothingLeft();

    /** What a leaf stating a line on none of these numbers leaves them, which is most leaves. */
    private static final BoundaryState NOTHING = new BoundaryState(Map.of(), Set.of());

    BoundaryState {
        byNumber = byNumber.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(byNumber));
        open = open.isEmpty() ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(open));
    }

    /** A reading that spoke of none of these numbers. */
    static BoundaryState nothing() {
        return NOTHING;
    }

    /**
     * One number the rules stop inside {@code range}.
     *
     * <p>Which of the two states that is is decided here, once, off the range — so nothing further
     * on has to look at the ends to find out.
     */
    static BoundaryState bounded(DerivedNumber number, OrderedInterval range) {
        return new BoundaryState(Map.of(number, leftBy(range)), Set.of());
    }

    /** One line stated on a number and placed by nothing. */
    static BoundaryState leftOpen(OpenEnd end) {
        return new BoundaryState(Map.of(), Set.of(end));
    }

    /**
     * Where the rules stop {@code number}, or null where they stop it nowhere a value of them is.
     *
     * <p>Null for a number nothing spoke of and for one the rules leave no value at, which are the
     * two a line may not be drawn from: the first has no ends and the second has ends nothing is
     * between. What has been read in the second case is that the rules contradict there, and that
     * is said by whoever answers whether a value exists.
     */
    OrderedInterval knownAt(DerivedNumber number) {
        return byNumber.get(number) instanceof Left.Known it ? it.range() : null;
    }

    /** Whether some rule stopped {@code number} anywhere a value of them is. */
    private boolean holdsDown(DerivedNumber number) {
        return byNumber.get(number) instanceof Left.Known;
    }

    /** Whether the rules of this reading leave {@code number} no value at all. */
    private boolean leavesNothingAt(DerivedNumber number) {
        return byNumber.get(number) instanceof Left.NothingLeft;
    }

    /** Whether this reading spoke of {@code number} at all. */
    private boolean spokeOf(DerivedNumber number) {
        return byNumber.containsKey(number) || openAt(number);
    }

    /** Whether some line stated on {@code number} here was placed by nothing. */
    private boolean openAt(DerivedNumber number) {
        return open.stream().anyMatch(each -> each.number().equals(number));
    }

    /**
     * Both readings holding at once.
     *
     * <p>A number either of them stopped is one the pair stops, at the tighter of what they leave —
     * and where the tighter of two leaves no value, the pair leaves none. A line either of them
     * left open is one the pair left open, because the end it did not work out may be the one the
     * values finally stop at.
     */
    BoundaryState both(BoundaryState other) {
        if (other == NOTHING) {
            return this;
        }
        if (this == NOTHING) {
            return other;
        }
        Map<DerivedNumber, Left> out = new LinkedHashMap<>(byNumber);
        other.byNumber.forEach((number, left) -> out.merge(number, left, BoundaryState::met));
        Set<OpenEnd> ends = new LinkedHashSet<>(open);
        ends.addAll(other.open);
        return new BoundaryState(out, ends);
    }

    /** What two readings that both stopped one number leave it. */
    private static Left met(Left here, Left there) {
        if (here instanceof Left.Known a && there instanceof Left.Known b) {
            return leftBy(a.range().meet(b.range()));
        }
        return NOTHING_LEFT;
    }

    /**
     * Either of them, which is what a choice between two branches somebody can be in leaves.
     *
     * <p>Asked of one number at a time, over every number either side spoke of.
     *
     * <ul>
     *   <li>Where one branch leaves the number no value, the choice leaves what the other leaves:
     *       every value of the choice is in that other branch. Where both do, so does the choice,
     *       and neither branch's lines are lines anybody is owed.
     *   <li>Where one branch says nothing of it, the choice says nothing: a value taking that
     *       branch stands anywhere on the number, so the choice does too, and no line on it is
     *       waiting on a reader.
     *   <li>Where both stopped it, the choice stops it at whichever reaches further out.
     *   <li>And a line one branch left open is one the choice leaves open, because the branch
     *       beside it does not put the number at every value — which is the one thing a choice can
     *       show about such a line.
     * </ul>
     *
     * <p>Every one of those reads the same either way round, which is what a choice is.
     */
    BoundaryState either(BoundaryState other) {
        Set<DerivedNumber> spoken = new LinkedHashSet<>(byNumber.keySet());
        open.forEach(each -> spoken.add(each.number()));
        other.byNumber.keySet().forEach(spoken::add);
        other.open.forEach(each -> spoken.add(each.number()));
        Map<DerivedNumber, Left> out = new LinkedHashMap<>();
        Set<OpenEnd> ends = new LinkedHashSet<>();
        for (DerivedNumber number : spoken) {
            boolean here = leavesNothingAt(number);
            boolean there = other.leavesNothingAt(number);
            if (here && there) {
                // Neither branch has a value at the number, so the choice has none — and a line
                // either of them left open is still one nothing placed, since neither puts the
                // number at every value. Both sides', so that a choice between one reading and
                // itself is that reading.
                keep(number, out, ends);
                other.keep(number, out, ends);
            } else if (here) {
                other.keep(number, out, ends);
            } else if (there) {
                keep(number, out, ends);
            } else {
                chosen(number, other, out, ends);
            }
        }
        return new BoundaryState(out, ends);
    }

    /** What this branch leaves {@code number}, carried out whole because it is what the choice
     *  leaves: nobody is in the branch beside it. */
    private void keep(DerivedNumber number, Map<DerivedNumber, Left> out, Set<OpenEnd> ends) {
        Left left = byNumber.get(number);
        if (left != null) {
            out.put(number, left);
        }
        open.forEach(each -> {
            if (each.number().equals(number)) {
                ends.add(each);
            }
        });
    }

    /** What a choice between two branches somebody can be in leaves {@code number}. */
    private void chosen(DerivedNumber number, BoundaryState other,
                        Map<DerivedNumber, Left> out, Set<OpenEnd> ends) {
        // A branch that says nothing of the number puts it at every value, so the choice does too
        // and nothing about it is waiting on a reader.
        if (!spokeOf(number) || !other.spokeOf(number)) {
            return;
        }
        OrderedInterval here = knownAt(number);
        OrderedInterval there = other.knownAt(number);
        if (here != null && there != null) {
            out.put(number, leftBy(here.join(there)));
        }
        if (other.holdsDown(number) || other.openAt(number)) {
            keep(number, new LinkedHashMap<>(), ends);
        }
        if (holdsDown(number) || openAt(number)) {
            other.keep(number, new LinkedHashMap<>(), ends);
        }
    }

    /**
     * Two branches neither of which anybody can be in.
     *
     * <p>Nothing anyone is in is stopped anywhere, and no line of such a branch is one a reader is
     * owed: what is left of the choice is that nobody is in it, which is said by whoever showed it.
     */
    BoundaryState bothDead(BoundaryState other) {
        return NOTHING;
    }

    /** Which of the two a range is, asked once where a range becomes a state. */
    private static Left leftBy(OrderedInterval range) {
        return Endpoint.someValueLiesBetween(range.low(), range.high())
                ? new Left.Known(range) : NOTHING_LEFT;
    }
}
