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
 * which of them a rule stopped somewhere nothing worked out.
 *
 * <p>Two halves of one reading and never two readings. A number no rule spoke of is absent from
 * both, one a rule stopped is in the first, and one a rule stopped somewhere this compiler could
 * not follow is in the second — read off a range alone, the first and the last are the same
 * absence, and a choice offering the last comes back saying the model draws no line.
 *
 * <p><b>Composed here and nowhere else.</b> What a conjunction or a choice of these comes to is one
 * question, and this is where it is answered — beside the values and the orders, under the fate
 * those two decide ({@link Confinement.Planned}). A second place putting either half of it together
 * is a second answer about one written choice, and the two would agree until somebody changed one.
 *
 * <p><b>An envelope, and no part of whether a value exists.</b> Two alternatives naming one size
 * each leave the run between them, and no value has a size in there — so this says where the
 * outermost ends are and is read where a line is looked for. It is asked nothing about whether
 * anybody can be in a branch, which is the values' and the orders' between them
 * ({@link Confinement#admission}): a number is bounded by rules the position's own readings have no
 * word for, so a branch this refused would be refused by a reading they cannot check.
 *
 * <p>Which is why {@link #either} joins nothing at a number either side's rules leave no value at.
 * That such ends have crossed is a sound proof that nobody is in that branch — and acting on it
 * here would be this reading deciding a fate, so what it does instead is decline to speak: a
 * number it says nothing about is one no line is drawn on, which is the answer this may always
 * give.
 *
 * @param known where each number a rule stopped is left, for the numbers some rule stopped
 * @param open  the numbers a rule stopped somewhere nothing here worked out
 */
record BoundaryState(Map<DerivedNumber, OrderedInterval> known, Set<DerivedNumber> open) {

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

    /** One number stopped somewhere nothing here worked out. */
    static BoundaryState leftOpen(DerivedNumber number) {
        return new BoundaryState(Map.of(), Set.of(number));
    }

    /** Whether some rule of this reading stopped {@code number} anywhere it could follow. */
    boolean bounds(DerivedNumber number) {
        return known.containsKey(number);
    }

    /** Where the rules leave {@code number}, which is everywhere none of them stopped it. */
    OrderedInterval at(DerivedNumber number) {
        return known.getOrDefault(number, OrderedInterval.OPEN);
    }

    /**
     * Both readings holding at once.
     *
     * <p>A number either of them stopped is one the pair stops, at the tighter of what they leave;
     * a number either left open is one the pair left open, because the end it did not work out may
     * be the one the values finally stop at.
     */
    BoundaryState both(BoundaryState other) {
        if (other == NOTHING) {
            return this;
        }
        if (this == NOTHING) {
            return other;
        }
        Map<DerivedNumber, OrderedInterval> out = new LinkedHashMap<>(known);
        other.known.forEach((number, range) ->
                out.merge(number, range, OrderedInterval::meet));
        return new BoundaryState(out, union(open, other.open));
    }

    /**
     * Either of them, which is what a choice between two branches somebody can be in leaves.
     *
     * <p>A number stopped in one branch and not in the other is left where it was: a value taking
     * the second owes the first nothing, so the choice stops it nowhere. Where both stopped it, the
     * choice stops it at whichever of the two reaches further out.
     *
     * <p>And a number either side's rules leave no value at is one this says nothing about — see
     * the note on this type. Nothing is joined there and nothing is claimed.
     *
     * <p><b>An end one branch left open is one the choice leaves open unless the branch beside it
     * stops the number nowhere.</b> That is the one thing a choice can show about it, and it is
     * shown by the other branch having been read to the end and stopped the number nowhere — so a
     * branch that stopped it, and a branch that left it open too, both leave the end standing.
     */
    BoundaryState either(BoundaryState other) {
        Map<DerivedNumber, OrderedInterval> out = new LinkedHashMap<>();
        known.forEach((number, range) -> {
            OrderedInterval there = other.known.get(number);
            if (there != null && holdsAValue(range) && holdsAValue(there)) {
                out.put(number, range.join(there));
            }
        });
        Set<DerivedNumber> left = new LinkedHashSet<>();
        open.forEach(number -> {
            if (other.bounds(number) || other.open.contains(number)) {
                left.add(number);
            }
        });
        other.open.forEach(number -> {
            if (bounds(number) || open.contains(number)) {
                left.add(number);
            }
        });
        return new BoundaryState(out, left);
    }

    /**
     * Two branches neither of which anybody can be in.
     *
     * <p>Nothing anyone is in is stopped anywhere, and no end of such a branch is one a reader is
     * owed: what is left of the choice is that nobody is in it, which is said by whoever showed it.
     */
    BoundaryState bothDead(BoundaryState other) {
        return NOTHING;
    }

    private static boolean holdsAValue(OrderedInterval range) {
        return Endpoint.someValueLiesBetween(range.low(), range.high());
    }

    private static Set<DerivedNumber> union(Set<DerivedNumber> these,
                                            Set<DerivedNumber> those) {
        if (those.isEmpty()) {
            return these;
        }
        if (these.isEmpty()) {
            return those;
        }
        Set<DerivedNumber> out = new LinkedHashSet<>(these);
        out.addAll(those);
        return out;
    }
}
