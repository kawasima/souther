package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What one argument about a relation showed, which is every lack it showed and not one of them.
 *
 * <p>Nothing here is in an order. An argument that walks the sets a count is taken of reaches them
 * in the order the pairs were written, and two writings of one relation are one relation — so what
 * these are equal to is settled by which lacks they are and how each was reached, and by nothing
 * about where in a walk each was found.
 *
 * <p>Each lack once, with every route that reached it. Two readings put together can show one lack
 * by taking a block's values through different rules, and both lots of rules are what an author has
 * to answer for.
 *
 * <p><b>Which is why what is inside is not what is handed out.</b> These are held in the order they
 * arrived, because a lack about several blocks hashes through the set of blocks it names — measured
 * on the shape the walk is admitted at, and on the numbers a lack had then, putting them somewhere
 * that hashes them on the way in was what a reduction cost rather than a part of it. An order
 * nobody may read is not an order a value may show, so nothing here answers with one: a reader asks
 * what was claimed, what a report may name, or whether every lack is of some kind.
 *
 * <p>And what a lack claims is what these are looked up by, which is a question asked of every lack
 * of one of these against every lack of another wherever two are put together. Asked by walking,
 * that is the square of a relation's sets on a relation admitted for as many of them as the bound
 * allows — so which of these claims what is worked out once, when they are made.
 *
 * @param <A> what a position is called
 */
public final class Lacks<A> {

    /** What an argument that refused nothing showed, which is one value however its positions are
     *  named — see {@link #none()}. */
    private static final Lacks<?> NONE = new Lacks<>(List.of());

    /** In the order they arrived, which nothing reads. */
    private final List<Shown<A>> each;

    /** Which of them claims what, so that putting two of these together is a lookup apiece rather
     *  than a walk. */
    private final Map<RelationalLack<A>, Integer> claiming;

    private Lacks(List<Shown<A>> each) {
        this.each = List.copyOf(each);
        Map<RelationalLack<A>, Integer> claiming = new HashMap<>(this.each.size() * 2);
        for (int at = 0; at < this.each.size(); at++) {
            if (claiming.put(this.each.get(at).lack(), at) != null) {
                throw new IllegalArgumentException(
                        "a lack is claimed once, with every route that reached it: "
                                + this.each.get(at).lack());
            }
        }
        this.claiming = claiming;
    }

    /** These lacks, each of them claimed once. */
    static <A> Lacks<A> of(List<Shown<A>> each) {
        return new Lacks<>(each);
    }

    /**
     * None of them, which is what an argument that refused nothing showed.
     *
     * <p>One value for every naming of a position. What these hold is what a caller put in them,
     * so what an empty one holds is nothing whatever its positions are called — and what it is
     * equal to, hashes as and is written as is settled by that alone. Which is why a reader on the
     * path where a relation shows nothing is handed the same one every time rather than a new one:
     * that path is walked once for every pair of two readings' alternatives.
     */
    @SuppressWarnings("unchecked")
    public static <A> Lacks<A> none() {
        return (Lacks<A>) NONE;
    }

    /** The one lack an argument showed, of the blocks it names and of nothing else. */
    public static <A> Lacks<A> of(RelationalLack<A> lack) {
        return new Lacks<>(List.of(Shown.of(lack)));
    }

    /** The one lack an argument showed, and how it reached it. */
    public static <A> Lacks<A> of(RelationalLack<A> lack, RelationalEvidence<A> reached) {
        return new Lacks<>(List.of(new Shown<>(lack, reached)));
    }

    /** Whether nothing was shown. */
    public boolean isEmpty() {
        return each.isEmpty();
    }

    /** How many of them there are, which is how many lots of blocks the argument was short of. */
    public int size() {
        return each.size();
    }

    /**
     * The one lack these are, where they are one.
     *
     * @throws IllegalStateException where they are not one, since a reader that asked this of
     *         several would be reading whichever arrived first
     */
    public Shown<A> only() {
        if (each.size() != 1) {
            throw new IllegalStateException("one lack was asked for, and these are " + this);
        }
        return each.getFirst();
    }

    /** What each of them claims, less how any of them was reached. */
    public Set<RelationalLack<A>> claimed() {
        return Collections.unmodifiableSet(claiming.keySet());
    }

    /** Every block a report may name: what the lacks are about, and what the routes to them read. */
    public Set<Sameness.Block<A>> blocks() {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        each.forEach(shown -> {
            out.addAll(shown.lack().blocks());
            out.addAll(shown.reached().restingOn(shown.lack().blocks()));
        });
        return Collections.unmodifiableSet(out);
    }

    /** Whether every lack is something, which is how a reader asks what argument refused. */
    public boolean all(Predicate<RelationalLack<A>> asked) {
        return each.stream().allMatch(shown -> asked.test(shown.lack()));
    }

    /** Both of these: every lack either shows, each with every route that reached it. */
    public Lacks<A> and(Lacks<A> other) {
        List<Shown<A>> out = new ArrayList<>(each);
        other.each.forEach(shown -> {
            Integer at = claiming.get(shown.lack());
            if (at == null) {
                out.add(shown);
            } else {
                out.set(at, out.get(at).alsoReachedBy(shown));
            }
        });
        return new Lacks<>(out);
    }

    /** The lacks both of these claim, each with the routes both of them reached it by. */
    public Lacks<A> sharedWith(Lacks<A> other) {
        List<Shown<A>> out = new ArrayList<>();
        for (Shown<A> shown : each) {
            Integer at = other.claiming.get(shown.lack());
            if (at != null) {
                out.add(shown.alsoReachedBy(other.each.get(at)));
            }
        }
        return new Lacks<>(out);
    }

    /** The same lacks about the blocks {@code naming} calls these. */
    public <B> Lacks<B> renamed(Function<A, B> naming) {
        List<Shown<B>> out = new ArrayList<>();
        each.forEach(shown -> out.add(shown.renamed(naming)));
        return new Lacks<>(out);
    }

    /** The same lacks, each reached the same way, whichever order they arrived in. */
    @Override
    public boolean equals(Object said) {
        if (!(said instanceof Lacks<?> it) || each.size() != it.each.size()) {
            return false;
        }
        for (Shown<?> shown : it.each) {
            Integer at = claiming.get(shown.lack());
            if (at == null || !each.get(at).equals(shown)) {
                return false;
            }
        }
        return true;
    }

    /** What was shown, in no order — see {@link ValueHash}. */
    @Override
    public int hashCode() {
        int summed = 0;
        for (Shown<A> shown : each) {
            summed += shown.hashCode();
        }
        return ValueHash.ofWhatItHolds(Lacks.class, summed, each.size());
    }

    /** Written in one order whichever they arrived in — see {@link InOneOrder}. */
    @Override
    public String toString() {
        return InOneOrder.of(each);
    }
}
