package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Where a reading was left nothing, and what kind of lack it is.
 *
 * <p><b>Two quantifiers and not one set of blocks.</b> A lack at each of some blocks says of every
 * one of them that it holds nothing; a lack about several of them together says that no assignment
 * to all of them stands, while each of them is left values of its own. Held as one set, a reader
 * putting two lacks together intersects them — which is what the first kind means and is an
 * invention for the second, since two collective lacks over sets that overlap have shown nothing
 * about what the two happened to share.
 *
 * <p>So the two are told apart by being two, and a reader that has to put two of them together is
 * made to say what it means by it ({@link #shownByBoth}).
 *
 * @param <A> what a position is called
 */
public sealed interface Refusal<A> {

    /** Nowhere in particular, which is where a lack no block is answerable for is. */
    record Nowhere<A>() implements Refusal<A> {}

    /**
     * Each of these blocks is left nothing.
     *
     * <p>Never none of them: a lack at no block is a lack nowhere, which is the case beside this
     * one. Refused here rather than read as {@link Nowhere} by whoever holds one, so that "no block
     * is why" has one spelling — held as two, a reader has to ask both, and the one that forgets
     * reports a lack at nowhere in particular as a lack somewhere.
     */
    record AtEachOf<A>(Set<Sameness.Block<A>> blocks) implements Refusal<A> {

        public AtEachOf {
            blocks = Collections.unmodifiableSet(new LinkedHashSet<>(blocks));
            if (blocks.isEmpty()) {
                throw new IllegalArgumentException(
                        "a lack at no block is a lack nowhere, which is Nowhere");
            }
        }
    }

    /** A lack at each of {@code blocks}, which is nowhere where they are none. */
    static <A> Refusal<A> atEachOf(Set<Sameness.Block<A>> blocks) {
        return blocks.isEmpty() ? new Nowhere<>() : new AtEachOf<>(blocks);
    }

    /**
     * No assignment to these blocks together stands, and what showed it.
     *
     * <p>The whole argument and not the blocks alone, because what a report may say about them
     * turns on which argument refused them: a value stated to differ from itself and a set of
     * blocks with fewer values between them than there are blocks are two sentences.
     */
    record OfThemTogether<A>(RelationalWitness<A> why) implements Refusal<A> {}

    /** The blocks the lack is about, for a reader that only has to name places. */
    default Set<Sameness.Block<A>> blocks() {
        return switch (this) {
            case Nowhere<A> _ -> Set.of();
            case AtEachOf<A> it -> it.blocks();
            case OfThemTogether<A> it -> it.why().blocks();
        };
    }

    /** Whether nothing here names a place. */
    default boolean isNowhere() {
        return this instanceof Nowhere;
    }

    /** The same lack about the blocks {@code naming} calls these. */
    default <B> Refusal<B> renamed(Function<A, B> naming) {
        return switch (this) {
            case Nowhere<A> _ -> new Nowhere<>();
            case AtEachOf<A> it -> {
                Set<Sameness.Block<B>> out = new LinkedHashSet<>();
                it.blocks().forEach(block -> out.add(block.renamed(naming)));
                yield new AtEachOf<>(out);
            }
            case OfThemTogether<A> it -> new OfThemTogether<>(it.why().renamed(naming));
        };
    }

    /**
     * Where two readings both left nothing were both refused.
     *
     * <p>The blocks each was refused at, kept where both were refused there. A block one of them
     * stands at is not one the pair has nothing at, so what can be said is what they agree on —
     * and where they agree on none, what was shown is about the whole product and no block is why.
     *
     * <p><b>And nothing kept where either is a lack about blocks together, unless the two are the
     * same lack.</b> Such a lack is not a lack at each of its blocks, so intersecting it with
     * anything answers a question neither reading was asked.
     */
    static <A> Refusal<A> shownByBoth(Refusal<A> one, Refusal<A> other) {
        if (one instanceof AtEachOf<A> mine && other instanceof AtEachOf<A> theirs) {
            Set<Sameness.Block<A>> both = new LinkedHashSet<>(mine.blocks());
            both.retainAll(theirs.blocks());
            return atEachOf(both);
        }
        return one.equals(other) ? one : new Nowhere<>();
    }
}
