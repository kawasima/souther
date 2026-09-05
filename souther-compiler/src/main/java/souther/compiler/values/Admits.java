package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which values one block of an alternative is left, for a reader relating it to another.
 *
 * <p>Three answers and not a set, because the two that are not a set are what let a relation be
 * decided without enumerating an order. Whether a denial between two blocks leaves anything turns
 * on which values each of them holds; a block holding more of them than the relation has blocks
 * never runs out, and a block whose values nothing here can write down is one the relation says
 * nothing about.
 *
 * <p><b>{@link MoreThanCounted} is not a set and not a refusal to answer.</b> It is the answer that
 * there are more values here than the caller said it would count — which is what a reader deciding
 * a relation between a handful of blocks needs, since such a block can always be given a value no
 * other took. Read as {@link NotKnown}, a relation over strings would be undecidable; read as a
 * set, it would be counted and the count would be wrong.
 */
public sealed interface Admits {

    /** These values and no others. */
    record These(Set<Value> values) implements Admits {

        public These {
            values = Collections.unmodifiableSet(new LinkedHashSet<>(values));
        }

        /** The one value this is, or null where it is not one value. */
        public Value only() {
            return values.size() == 1 ? values.iterator().next() : null;
        }
    }

    /** More values than the caller said it would count, which is as many as it needs. */
    record MoreThanCounted() implements Admits {}

    /**
     * These values, or that there are more of them than {@code atMost}.
     *
     * <p>The one place the bound is applied. Whoever works out which values a block is left counts
     * them as it goes and has to decide where to stop, and that decision written at each of those
     * places is the same rule spelled three ways — one stopping at the bound, one past it, one
     * calling the answer unknown. What "more than were counted" means belongs to this word.
     */
    static Admits of(Set<Value> values, int atMost) {
        return values.size() > atMost ? new MoreThanCounted() : new These(values);
    }

    /** Not something the reading that was asked can write down. */
    record NotKnown() implements Admits {}

    /** This without {@code value}, which is what a block held apart from one holding only that
     *  value is left. Taking one value from more than were counted leaves more than were counted
     *  less one, which is still more than none. */
    default Admits without(Value value) {
        if (!(this instanceof These it) || !it.values().contains(value)) {
            return this;
        }
        Set<Value> out = new LinkedHashSet<>(it.values());
        out.remove(value);
        return new These(out);
    }

    /** Whether this is settled to be no value at all. */
    default boolean isNone() {
        return this instanceof These it && it.values().isEmpty();
    }

    /** Whether which values these are is written down, which is what a count can be taken of. */
    default boolean isCounted() {
        return this instanceof These;
    }
}
