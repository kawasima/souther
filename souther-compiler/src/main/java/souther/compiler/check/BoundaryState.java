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
 *   <li><b>Known</b> — some rule stopped it, and this is where.
 *   <li><b>Nothing left</b> — the rules stopped it past themselves, so no value of them is at it.
 *       A known range whose ends have crossed.
 *   <li><b>Open</b> — some rule stopped it somewhere nothing here worked out.
 * </ul>
 *
 * <p>A number can be known and open at once: a conjunction of a bound this reading placed and one
 * it could not is both, and each half is wanted by a different reader.
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
 * is told, and the branch stands or falls on what the values and the orders say. Read as nothing
 * said, the same three alternatives came to two answers depending on where the brackets were.
 *
 * @param known where each number some rule stopped is left, whether or not a value is there
 * @param open  every line on one of these numbers that nothing here placed, one for each place an
 *              author wrote one ({@link OpenEnd})
 */
record BoundaryState(Map<DerivedNumber, OrderedInterval> known, Set<OpenEnd> open) {

    /** What a leaf stating a line on none of these numbers leaves them, which is most leaves. */
    private static final BoundaryState NOTHING = new BoundaryState(Map.of(), Set.of());

    BoundaryState {
        known = known.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(known));
        open = open.isEmpty() ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(open));
    }

    /** A reading that stopped none of these numbers. */
    static BoundaryState nothing() {
        return NOTHING;
    }

    /** One number stopped inside {@code range}. */
    static BoundaryState bounded(DerivedNumber number, OrderedInterval range) {
        return new BoundaryState(Map.of(number, range), Set.of());
    }

    /** One line stated on a number and placed by nothing. */
    static BoundaryState leftOpen(OpenEnd end) {
        return new BoundaryState(Map.of(), Set.of(end));
    }

    /**
     * Where the rules leave {@code number}, which is everywhere none of them stopped it.
     *
     * <p>The range as the rules leave it, ends crossed and all. A caller drawing a line reads the
     * ends; that they have crossed is a fact about the rules and is that caller's to notice.
     */
    OrderedInterval at(DerivedNumber number) {
        return known.getOrDefault(number, OrderedInterval.OPEN);
    }

    /** Whether some rule stopped {@code number} anywhere a value of them is. */
    private boolean holdsDown(DerivedNumber number) {
        OrderedInterval range = known.get(number);
        return range != null && holdsAValue(range);
    }

    /** Whether the rules of this reading leave {@code number} no value at all. */
    private boolean leavesNothingAt(DerivedNumber number) {
        OrderedInterval range = known.get(number);
        return range != null && !holdsAValue(range);
    }

    /** Whether some line stated on {@code number} here was placed by nothing. */
    private boolean openAt(DerivedNumber number) {
        return open.stream().anyMatch(each -> each.number().equals(number));
    }

    /**
     * Both readings holding at once.
     *
     * <p>A number either of them stopped is one the pair stops, at the tighter of what they leave;
     * a line either of them left open is one the pair left open, because the end it did not work
     * out may be the one the values finally stop at.
     */
    BoundaryState both(BoundaryState other) {
        if (other == NOTHING) {
            return this;
        }
        if (this == NOTHING) {
            return other;
        }
        Map<DerivedNumber, OrderedInterval> out = new LinkedHashMap<>(known);
        other.known.forEach((number, range) -> out.merge(number, range, OrderedInterval::meet));
        Set<OpenEnd> ends = new LinkedHashSet<>(open);
        ends.addAll(other.open);
        return new BoundaryState(out, ends);
    }

    /**
     * Either of them, which is what a choice between two branches somebody can be in leaves.
     *
     * <p>Asked of one number at a time, over every number either side speaks of.
     *
     * <ul>
     *   <li>Where one branch leaves the number no value, the choice leaves what the other leaves:
     *       every value of the choice is in that other branch.
     *   <li>Where one branch says nothing of it, the choice says nothing: a value taking that
     *       branch stands anywhere on the number, so the choice does too, and no end of it is
     *       waiting on a reader.
     *   <li>Where both stopped it, the choice stops it at whichever reaches further out.
     *   <li>And a line one branch left open is one the choice leaves open, because the branch
     *       beside it does not put the number at every value — which is the one thing a choice can
     *       show about such a line.
     * </ul>
     */
    BoundaryState either(BoundaryState other) {
        Set<DerivedNumber> spoken = new LinkedHashSet<>(known.keySet());
        open.forEach(each -> spoken.add(each.number()));
        other.known.keySet().forEach(spoken::add);
        other.open.forEach(each -> spoken.add(each.number()));
        Map<DerivedNumber, OrderedInterval> out = new LinkedHashMap<>();
        Set<OpenEnd> ends = new LinkedHashSet<>();
        for (DerivedNumber number : spoken) {
            if (leavesNothingAt(number)) {
                other.keep(number, out, ends);
            } else if (other.leavesNothingAt(number)) {
                keep(number, out, ends);
            } else {
                chosen(number, other, out, ends);
            }
        }
        return new BoundaryState(out, ends);
    }

    /** What this branch leaves {@code number}, carried out whole because it is what the choice
     *  leaves: nobody is in the branch beside it. */
    private void keep(DerivedNumber number, Map<DerivedNumber, OrderedInterval> out,
                      Set<OpenEnd> ends) {
        OrderedInterval range = known.get(number);
        if (range != null) {
            out.put(number, range);
        }
        open.forEach(each -> {
            if (each.number().equals(number)) {
                ends.add(each);
            }
        });
    }

    /** What a choice between two branches somebody can be in leaves {@code number}. */
    private void chosen(DerivedNumber number, BoundaryState other,
                        Map<DerivedNumber, OrderedInterval> out, Set<OpenEnd> ends) {
        OrderedInterval here = known.get(number);
        OrderedInterval there = other.known.get(number);
        if (here != null && there != null) {
            out.put(number, here.join(there));
        }
        if (other.holdsDown(number) || other.openAt(number)) {
            open.forEach(each -> {
                if (each.number().equals(number)) {
                    ends.add(each);
                }
            });
        }
        if (holdsDown(number) || openAt(number)) {
            other.open.forEach(each -> {
                if (each.number().equals(number)) {
                    ends.add(each);
                }
            });
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

    private static boolean holdsAValue(OrderedInterval range) {
        return Endpoint.someValueLiesBetween(range.low(), range.high());
    }
}
