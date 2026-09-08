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
}
