package souther.compiler.numeric;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules a path has been told, as it was told them.
 *
 * <p>What a rule arriving costs is what this exists to settle. A domain is asked what its rules
 * leave once, when something asks it, and everything on the way there is a rule being put beside the
 * ones already said — so a rule said twice being one rule is a property of what is read out of this
 * rather than of every step taken into it. Enforced at each step, saying a rule means looking for it
 * among all of them, and a path stating many of them pays for each one as many times as there are.
 *
 * <p>So there is nothing here but which rules were said and in what order. Saying one more and
 * saying two paths' rules together are the same shape and cost the same nothing, and what a reader
 * gets is {@link #distinct}, which is the rules with the second saying of any of them left out.
 *
 * <p>Order is kept because a reader of the rules writes them out. Two compiles of one model reach
 * the same rules in the same order, and a set that did not keep it would leave what is written out
 * of a domain to how the rules happened to hash.
 *
 * <p><b>What a composition is is a graph and not a tree.</b> Nothing here is copied, so two paths
 * that carried on from one are two compositions holding the one they carried on from, and saying
 * those two together says it once and reaches it twice. A reader walking what it reaches would pay
 * for how a composition was arrived at rather than for what it holds, and doubling is a shape a
 * caller can write — which is the cost this exists to be rid of, moved to the other end.
 *
 * @param <A> what a position is called
 */
sealed interface StatedRules<A> {

    /** A path that has been told nothing. */
    record None<A>() implements StatedRules<A> {}

    /** One rule. */
    record One<A>(AffineConstraint<A> rule) implements StatedRules<A> {}

    /** Everything on the left said, and then everything on the right. */
    record Both<A>(StatedRules<A> left, StatedRules<A> right) implements StatedRules<A> {}

    /** The one of these there is to hold, since it holds nothing that is anybody's. */
    StatedRules<?> NOTHING = new None<>();

    @SuppressWarnings("unchecked")
    static <A> StatedRules<A> none() {
        return (StatedRules<A>) NOTHING;
    }

    static <A> StatedRules<A> of(AffineConstraint<A> rule) {
        return new One<>(rule);
    }

    /** These and then {@code other}, which is what a path told both of them was told. */
    default StatedRules<A> and(StatedRules<A> other) {
        if (this instanceof None<A>) {
            return other;
        }
        if (other instanceof None<A>) {
            return this;
        }
        return new Both<>(this, other);
    }

    /** Whether nothing has been said, which is what a domain over every assignment holds. */
    default boolean isNothing() {
        return this instanceof None<A>;
    }

    /**
     * The rules said, each of them once, in the order they were first said.
     *
     * <p>Walked with a stack of what is left rather than by calling down the composition. A path
     * states one rule at a time, so what a long path composes is as deep as it is long, and a walk
     * that went down it would be a limit on how many rules a path may state.
     *
     * <p>Each of them once, and each part of the composition once — the second is what makes this a
     * walk of what was said rather than of how it was arrived at. A part reached again holds
     * nothing that has not been read: it was read to its end before anything beside it was reached,
     * so everything in it was first said there or before it, and passing it leaves the order alone.
     *
     * <p>What is passed is the part itself and not one equal to it. A composition compares by what
     * it holds, all the way down, so asking a set whether it has already seen one is asking the
     * question this walk is a stack for.
     */
    default List<AffineConstraint<A>> distinct() {
        Set<AffineConstraint<A>> out = new LinkedHashSet<>();
        Set<StatedRules<A>> read = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<StatedRules<A>> left = new ArrayDeque<>();
        left.push(this);
        while (!left.isEmpty()) {
            StatedRules<A> next = left.pop();
            if (!read.add(next)) {
                continue;
            }
            switch (next) {
                case None<A> ignored -> { }
                case One<A> one -> out.add(one.rule());
                case Both<A> both -> {
                    left.push(both.right());
                    left.push(both.left());
                }
            }
        }
        return List.copyOf(out);
    }
}
