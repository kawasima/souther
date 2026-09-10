package souther.compiler.check;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The predicates a path's guards have settled, as they settled them.
 *
 * <p>What a predicate arriving costs is what this exists to settle. The predicates are asked about
 * once, where something asks them, and everything on the way there is one more of them being put
 * beside the ones already settled — so a predicate settled twice being settled once is a property of
 * what is read out of this rather than of every step taken into it. Held as the two sets a reader
 * wants, each step remakes them, and a path settling many predicates pays for each one as many times
 * as there are.
 *
 * <p>So there is nothing here but which predicates were settled, which way round, and in what order.
 * Settling one more and saying two paths' predicates together are the same shape and cost the same
 * nothing, and what a reader gets is {@link #distinct}, which is the settlings with the second
 * arrival of any of them left out.
 *
 * <p>Order is kept because a reader of these hands every subject to a renaming, and a renaming that
 * has to refuse two subjects under one name names whichever it reaches first. Read off a set whose
 * iteration order is salted once per run, which of two collisions is reported would move between
 * runs of the same compiler.
 *
 * <p><b>What a composition is is a graph and not a tree.</b> Nothing here is copied, so two paths
 * that carried on from one are two compositions holding the one they carried on from, and saying
 * those two together says it once and reaches it twice. A reader walking what it reaches would pay
 * for how a composition was arrived at rather than for what it holds, and doubling is a shape a
 * caller can write — which is the cost this exists to be rid of, moved to the other end.
 *
 * @param <A> what a fact is filed under
 */
sealed interface SettledPredicates<A> {

    /** A path whose guards have settled nothing. */
    record None<A>() implements SettledPredicates<A> {}

    /** One predicate, settled as holding where {@code positive}, and as failing where it is not. */
    record One<A>(A key, boolean positive) implements SettledPredicates<A> {}

    /** Everything on the left settled, and then everything on the right. */
    record Both<A>(SettledPredicates<A> left, SettledPredicates<A> right)
            implements SettledPredicates<A> {}

    /** The one of these there is to hold, since it holds nothing that is anybody's. */
    SettledPredicates<?> NOTHING = new None<>();

    @SuppressWarnings("unchecked")
    static <A> SettledPredicates<A> none() {
        return (SettledPredicates<A>) NOTHING;
    }

    static <A> SettledPredicates<A> of(A key, boolean positive) {
        return new One<>(key, positive);
    }

    /** These and then {@code other}, which is what a path that settled both of them settled. */
    default SettledPredicates<A> and(SettledPredicates<A> other) {
        if (this instanceof None<A>) {
            return other;
        }
        if (other instanceof None<A>) {
            return this;
        }
        return new Both<>(this, other);
    }

    /**
     * The settlings, each of them once, in the order they were first settled.
     *
     * <p>Walked with a stack of what is left rather than by calling down the composition. A path
     * settles one predicate at a time, so what a long path composes is as deep as it is long, and a
     * walk that went down it would be a limit on how many predicates a path may settle.
     *
     * <p>Each of them once, and each part of the composition once — the second is what makes this a
     * walk of what was settled rather than of how it was arrived at. A part reached again holds
     * nothing that has not been read: it was read to its end before anything beside it was reached,
     * so everything in it was first settled there or before it, and passing it leaves the order
     * alone.
     *
     * <p>What is passed is the part itself and not one equal to it. A composition compares by what
     * it holds, all the way down, so asking a set whether it has already seen one is asking the
     * question this walk is a stack for.
     */
    default List<One<A>> distinct() {
        Set<One<A>> out = new LinkedHashSet<>();
        Set<SettledPredicates<A>> read = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<SettledPredicates<A>> left = new ArrayDeque<>();
        left.push(this);
        while (!left.isEmpty()) {
            SettledPredicates<A> next = left.pop();
            if (!read.add(next)) {
                continue;
            }
            switch (next) {
                case None<A> ignored -> { }
                case One<A> one -> out.add(one);
                case Both<A> both -> {
                    left.push(both.right());
                    left.push(both.left());
                }
            }
        }
        return List.copyOf(out);
    }
}
