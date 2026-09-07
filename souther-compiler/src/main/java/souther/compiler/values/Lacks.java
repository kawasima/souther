package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What one argument about a relation showed, which is every lack it showed and not one of them.
 *
 * <p>Nothing here reads the order these come in. An argument that walks the sets a count is taken
 * of reaches them in the order the pairs were written, and two writings of one relation are one
 * relation — so what is equal to what is settled by which lacks these are and how each was
 * reached, and by nothing about where in the walk each was found.
 *
 * <p>Each lack once, with every route that reached it. Two readings put together can show one lack
 * by taking a block's values through different rules, and both lots of rules are what an author has
 * to answer for.
 *
 * <p><b>Which is not the same as holding them in a {@link Set}.</b> A lack about several blocks
 * hashes through the set of blocks it names, and a set of blocks hashes as the sum of what it
 * holds; the sets of blocks one relation is short of are subsets of the same few blocks, so their
 * sums fall together and every one of them would be compared with every other on the way in.
 * Measured on the shape the walk is admitted at, putting them in a set was what a reduction cost
 * rather than a part of it.
 *
 * @param <A> what a position is called
 * @param each the lacks, in whatever order the argument that showed them reached them
 */
public record Lacks<A>(List<Shown<A>> each) {

    public Lacks {
        each = List.copyOf(each);
    }

    /** None of them, which is what an argument that refused nothing showed. */
    public static <A> Lacks<A> none() {
        return new Lacks<>(List.of());
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

    /** What each of them claims, less how any of them was reached. */
    public Set<RelationalLack<A>> claimed() {
        Set<RelationalLack<A>> out = new LinkedHashSet<>();
        each.forEach(shown -> out.add(shown.lack()));
        return Collections.unmodifiableSet(out);
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
        other.each.forEach(shown -> put(out, shown));
        return new Lacks<>(out);
    }

    /** The lacks both of these claim, each with the routes both of them reached it by. */
    public Lacks<A> sharedWith(Lacks<A> other) {
        List<Shown<A>> out = new ArrayList<>();
        each.forEach(shown -> other.showing(shown.lack())
                .ifPresent(theirs -> put(out, shown.alsoReachedBy(theirs))));
        return new Lacks<>(out);
    }

    /** How {@code lack} was reached here, or nothing where it is not claimed. */
    private Optional<Shown<A>> showing(RelationalLack<A> lack) {
        return each.stream().filter(shown -> shown.lack().equals(lack)).findFirst();
    }

    /** {@code shown} into {@code out}, put together with what already claims its lack. */
    private static <A> void put(List<Shown<A>> out, Shown<A> shown) {
        for (int at = 0; at < out.size(); at++) {
            if (out.get(at).lack().equals(shown.lack())) {
                out.set(at, out.get(at).alsoReachedBy(shown));
                return;
            }
        }
        out.add(shown);
    }

    /** The same lacks about the blocks {@code naming} calls these. */
    public <B> Lacks<B> renamed(Function<A, B> naming) {
        List<Shown<B>> out = new ArrayList<>();
        each.forEach(shown -> out.add(shown.renamed(naming)));
        return new Lacks<>(out);
    }

    /**
     * The same lacks whichever order the argument reached them in.
     *
     * <p>Asked by holding each of one against the other, and not by putting them somewhere that
     * hashes them: what these hold hashes alike across one relation, so a reader that asked this
     * question by building a set would pay for the collisions here as well as where they were made.
     */
    @Override
    public boolean equals(Object said) {
        return said instanceof Lacks<?> it && each.size() == it.each.size()
                && each.containsAll(it.each);
    }

    @Override
    public int hashCode() {
        int out = 0;
        for (Shown<A> shown : each) {
            out += shown.hashCode();
        }
        return out;
    }

    @Override
    public String toString() {
        return each.toString();
    }
}
