package souther.compiler.values;

import souther.compiler.hash.ValueHash;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which positions a reading was told hold different values, as it was told them.
 *
 * <p>{@link Apartness} says which <em>blocks</em> differ, which is what a denial comes to once
 * everything the reading holds as one value is known. Conjoining readings is where that is found
 * out: each one is a coarsening of the last, so a relation between blocks has to be filed into the
 * new blocks at every step, and a reading told many denials files each of them as many times as
 * there are steps. Told as positions, a denial is what it was written as and nothing has to be
 * refiled — the blocks its ends are on are read off the relation the conjunction leaves, once.
 *
 * <p>So this is for conjunction and stops there. A choice reads what its branches both deny, which
 * pulls each relation back onto the finer blocks its ends are made of and intersects — an algebra
 * over blocks, and one that a union of what was stated has no answer for. What crosses that line is
 * {@link #quotientBy}, and above it there is only {@link Apartness}.
 *
 * <p><b>Composed in one step and compared in one step.</b> Nothing is copied when two of these are
 * said together, so what a caller says twice is reached twice and held once, and how many were said
 * and what they come to as one number are carried across each composition rather than walked for.
 * A reading is put in a set of the alternatives its conjunction leaves, so what it costs to hash one
 * is paid at every step of that conjunction and cannot be a walk of what it holds.
 *
 * @param <A> what a position is called
 */
final class StatedApartness<A> {

    private static final StatedApartness<?> NOTHING =
            new StatedApartness<>(null, null, null, null, 0, 0);

    /** The denial this is, where it is one, and null where it is none of them or both of two. */
    private final A one;
    private final A other;

    /** Both of them, where this is two said together. Null otherwise. */
    private final StatedApartness<A> left;
    private final StatedApartness<A> right;

    /** How many were said, counting a denial said twice twice — what is held is what was said. */
    private final int stated;

    /**
     * The denials this holds, added up.
     *
     * <p>Added rather than taken in order, because what this holds is a set and two callers reaching
     * the same denials by different compositions hold the same thing. Carried across a composition
     * instead of walked for, so that hashing one is what it costs to say one.
     */
    private final int mixed;

    /** The denials said, each once, worked out on the first question that needs them and kept. */
    private Set<Denial<A>> settled;

    private StatedApartness(A one, A other, StatedApartness<A> left, StatedApartness<A> right,
                            int stated, int mixed) {
        this.one = one;
        this.other = other;
        this.left = left;
        this.right = right;
        this.stated = stated;
        this.mixed = mixed;
    }

    /** Nothing stated to differ, which is what a reading that read no denial holds. */
    @SuppressWarnings("unchecked")
    static <A> StatedApartness<A> none() {
        return (StatedApartness<A>) NOTHING;
    }

    /** The two positions stated to hold different values. */
    static <A> StatedApartness<A> of(A one, A other) {
        return new StatedApartness<>(one, other, null, null, 1,
                new Denial<>(one, other).hashCode());
    }

    /**
     * The same relation, said of the positions the blocks it relates are made of.
     *
     * <p>For a reader coming back the other way: a choice reads what both its branches deny, which
     * is an algebra over blocks and is {@link Apartness}'s, and what it leaves is a reading that
     * conjunctions carry on from.
     *
     * <p>Every pair of the two ends and not one pair of representatives. A denial between two
     * blocks says that everything one of them holds differs from everything the other does, so a
     * reading that later holds those positions apart is owed all of it — said by one pair, what the
     * others were told would be gone the moment nothing held them together any more.
     */
    static <A> StatedApartness<A> of(Apartness<A> relation) {
        StatedApartness<A> out = none();
        for (Apartness.Edge<A> edge : relation.edges()) {
            for (A one : edge.one().members()) {
                for (A other : edge.other().members()) {
                    out = out.and(of(one, other));
                }
            }
        }
        return out;
    }

    /** Both of them said, which is what a conjunction of two readings was told. */
    StatedApartness<A> and(StatedApartness<A> more) {
        if (stated == 0) {
            return more;
        }
        if (more.stated == 0) {
            return this;
        }
        return new StatedApartness<>(null, null, this, more,
                stated + more.stated, mixed + more.mixed);
    }

    /** Whether nothing is stated to differ. */
    boolean isEmpty() {
        return stated == 0;
    }

    /**
     * The denials said, each of them once.
     *
     * <p>Walked with a stack of what is left rather than by calling down what was composed, and
     * passing a part it has already read. A reading states one denial at a time, so what a long one
     * composes is as deep as it is long; and nothing is copied when two are said together, so a
     * caller saying one thing twice reaches it twice and holds it once.
     */
    Set<Denial<A>> denials() {
        if (settled == null) {
            Set<Denial<A>> out = new LinkedHashSet<>();
            Set<StatedApartness<A>> read = Collections.newSetFromMap(new IdentityHashMap<>());
            Deque<StatedApartness<A>> toRead = new ArrayDeque<>();
            toRead.push(this);
            while (!toRead.isEmpty()) {
                StatedApartness<A> next = toRead.pop();
                if (!read.add(next)) {
                    continue;
                }
                if (next.left != null) {
                    toRead.push(next.right);
                    toRead.push(next.left);
                } else if (next.stated != 0) {
                    out.add(new Denial<>(next.one, next.other));
                }
            }
            settled = Collections.unmodifiableSet(out);
        }
        return settled;
    }

    /** Every position a denial names, which is every position this says anything about. */
    Set<A> positions() {
        Set<A> out = new LinkedHashSet<>();
        for (Denial<A> denial : denials()) {
            out.add(denial.one());
            out.add(denial.other());
        }
        return out;
    }

    /**
     * Whether some denial has both its ends on one block of {@code heldAsOne}, which is a value
     * stated to differ from itself and so a reading nothing satisfies.
     *
     * <p>Asked of what was stated rather than of the relation it comes to, because it is the one
     * thing a reading is asked about its denials before anything has worked out what the positions
     * they name admit. Built as a relation to answer it, a reading would pay for every pair it holds
     * every time it is asked whether it still stands, and it is asked that as each rule arrives.
     *
     * <p>Every part walked once, for {@link #denials}' reason, and nothing built: the answer is
     * whether there is one, so the walk stops at the first.
     */
    boolean contradicts(Sameness<A> heldAsOne) {
        if (stated == 0) {
            return false;
        }
        Set<StatedApartness<A>> read = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<StatedApartness<A>> toRead = new ArrayDeque<>();
        toRead.push(this);
        while (!toRead.isEmpty()) {
            StatedApartness<A> next = toRead.pop();
            if (!read.add(next)) {
                continue;
            }
            if (next.left != null) {
                toRead.push(next.right);
                toRead.push(next.left);
            } else if (heldAsOne.blockOf(next.one).equals(heldAsOne.blockOf(next.other))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The relation these denials come to, between the blocks {@code heldAsOne} holds their ends on.
     *
     * <p>Where this stops and {@link Apartness} begins. A denial names two positions and what it
     * says is about the values they are left, so once it is known which positions are one value the
     * denial is a pair of those — and both ends landing on one block is kept, since what it says
     * there is that a value differs from itself.
     */
    Apartness<A> quotientBy(Sameness<A> heldAsOne) {
        if (stated == 0) {
            return Apartness.nothing();
        }
        Set<Apartness.Edge<A>> edges = new LinkedHashSet<>();
        for (Denial<A> denial : denials()) {
            edges.add(new Apartness.Edge<>(heldAsOne.blockOf(denial.one()),
                    heldAsOne.blockOf(denial.other())));
        }
        return Apartness.ofEdges(edges);
    }

    @Override
    public boolean equals(Object said) {
        if (this == said) {
            return true;
        }
        return said instanceof StatedApartness<?> it && mixed == it.mixed
                && denials().equals(it.denials());
    }

    /** The denials it holds — see {@link ValueHash}. */
    @Override
    public int hashCode() {
        return ValueHash.ofWhatItHolds(StatedApartness.class, mixed, stated);
    }

    @Override
    public String toString() {
        return InOneOrder.of(denials());
    }

    /**
     * Two positions stated to hold different values.
     *
     * <p>Unordered, for {@link Apartness.Edge}'s reason: a denial is a pair, and which end an author
     * wrote first is a fact about the writing.
     */
    record Denial<A>(A one, A other) {

        @Override
        public boolean equals(Object said) {
            return said instanceof Denial<?> it
                    && ((one.equals(it.one) && other.equals(it.other))
                            || (one.equals(it.other) && other.equals(it.one)));
        }

        @Override
        public int hashCode() {
            return ValueHash.ofAnUnorderedPair(Denial.class, one.hashCode(), other.hashCode());
        }

        @Override
        public String toString() {
            String mine = String.valueOf(one);
            String theirs = String.valueOf(other);
            return mine.compareTo(theirs) <= 0 ? mine + " /= " + theirs : theirs + " /= " + mine;
        }
    }
}
