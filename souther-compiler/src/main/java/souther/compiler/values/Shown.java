package souther.compiler.values;

import java.util.function.Function;

/**
 * One lack, and how the readings that showed it reached it.
 *
 * <p>The unit an argument about a relation answers in. A refusal carrying one route for all its
 * lacks could not say which of them that route reached — so two readings refused at different
 * blocks through different rules would have to drop both routes to stay honest, and what an author
 * is sent to read is the rules that took the values rather than a block whose own rules leave it
 * everything.
 *
 * <p>Which is also what lets a refusal be equal to what it is. Held apart from its lack, a route
 * is something a value has to leave out of its own equality to be compared by what it claims;
 * held here, what two readings both showed is asked of the lacks by whoever is asking
 * ({@link Refusal#shownByBoth}), and every value stays equal to what it holds.
 *
 * @param <A> what a position is called
 * @param lack what is claimed
 * @param reached how it was arrived at, which is nothing where the argument read no further than
 *                the blocks the lack names
 */
public record Shown<A>(RelationalLack<A> lack, RelationalEvidence<A> reached) {

    /** A lack shown of the blocks it names and of nothing else. */
    public static <A> Shown<A> of(RelationalLack<A> lack) {
        return new Shown<>(lack, RelationalEvidence.none());
    }

    /** The same lack, reached by what reached either of these. */
    public Shown<A> alsoReachedBy(Shown<A> other) {
        if (!lack.equals(other.lack)) {
            throw new IllegalArgumentException(
                    "two lacks are two claims, and only one claim has routes to it: "
                            + lack + " and " + other.lack);
        }
        return new Shown<>(lack, reached.and(other.reached));
    }

    /** The same, about the blocks {@code naming} calls these. */
    public <B> Shown<B> renamed(Function<A, B> naming) {
        return new Shown<>(lack.renamed(naming), reached.renamed(naming));
    }

    /**
     * The lack and how it was reached, each in its own place — see {@link ValueHash}.
     *
     * <p>Said here rather than left to what a record answers, because these are what an argument
     * holds several of and what several of them come to is their numbers added. A record's own
     * carries its last component up unchanged, so two of these would come to one number whenever
     * the same lacks and the same routes were shared out between them the other way — and which
     * route reached which lack is what an author is sent to read.
     */
    @Override
    public int hashCode() {
        return ValueHash.ofItsParts(Shown.class, lack.hashCode(), reached.hashCode());
    }

    /** The lack it claims and the routes that reached it, which is what it holds. Spelled out
     *  beside the number, so that the two are read together wherever either is changed. */
    @Override
    public boolean equals(Object said) {
        return said instanceof Shown<?> it && lack.equals(it.lack) && reached.equals(it.reached);
    }
}
