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

        /** The blocks written in one order — see {@link InOneOrder}. */
        @Override
        public String toString() {
            return "AtEachOf" + InOneOrder.of(blocks);
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
     *
     * <p>Several of them, because one argument can show a lack about several lots of blocks at
     * once and the relation says nothing about which of them to carry.
     */
    record OfThemTogether<A>(Lacks<A> lacks) implements Refusal<A> {

        public OfThemTogether {
            if (lacks.isEmpty()) {
                throw new IllegalArgumentException(
                        "a lack about blocks together is shown by an argument, and none was given");
            }
        }
    }

    /**
     * The blocks a report may name, which is what the lack is about and what it was reached
     * through.
     *
     * <p>The route as well as the claim, and here rather than in the lack. What an author is sent
     * to read is the rules that leave the blocks nothing, and a block left nothing because its
     * neighbours were left one value each is a place whose own rules are fine with what they leave
     * it — so a report naming it alone would send the author nowhere useful. What two lacks are
     * compared by is the claim, which is what {@link #shownByBoth} asks and this does not.
     */
    default Set<Sameness.Block<A>> blocks() {
        return switch (this) {
            case Nowhere<A> _ -> Set.of();
            case AtEachOf<A> it -> it.blocks();
            case OfThemTogether<A> it -> it.lacks().blocks();
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
            case OfThemTogether<A> it -> new OfThemTogether<>(it.lacks().renamed(naming));
        };
    }

    /**
     * Where a conjunction with a side that holds nothing was refused.
     *
     * <p>A different question from {@link #shownByBoth}, and the answer is different. There, two
     * readings of one set of rules both hold nothing and what can be said is what they agree on;
     * here, a conjunction is refused because a side of it is, and where both sides are, both
     * reasons are true of it. So two lacks at blocks are a lack at all of them.
     *
     * <p>And two lacks about blocks together are both of them. Each side's argument holds of the
     * conjunction, so what it is refused by is what either of them showed — and a side whose lack
     * is the other's adds nothing, which is what a set of them says without anybody asking.
     *
     * <p>Nothing kept where one side is a lack at each of some blocks and the other a lack about
     * blocks together, unless the two are the same. The two are different claims about different
     * things, and naming one of them would be naming whichever side the caller wrote first — which
     * is a fact about the writing.
     */
    static <A> Refusal<A> eitherShown(Refusal<A> one, Refusal<A> other) {
        if (one.isNowhere()) {
            return other;
        }
        if (other.isNowhere()) {
            return one;
        }
        if (one instanceof AtEachOf<A> mine && other instanceof AtEachOf<A> theirs) {
            Set<Sameness.Block<A>> both = new LinkedHashSet<>(mine.blocks());
            both.addAll(theirs.blocks());
            return new AtEachOf<>(both);
        }
        if (one instanceof OfThemTogether<A> mine && other instanceof OfThemTogether<A> theirs) {
            return new OfThemTogether<>(mine.lacks().and(theirs.lacks()));
        }
        return one.equals(other) ? one : new Nowhere<>();
    }

    /**
     * Where two readings that both left nothing were both refused.
     *
     * <p>The blocks each was refused at, kept where both were refused there. A block one of them
     * stands at is not one the pair has nothing at, so what can be said is what they agree on —
     * and where they agree on none, what was shown is about the whole product and no block is why.
     *
     * <p><b>And two lacks about blocks together are the ones both readings show.</b> Such a lack is
     * not a lack at each of its blocks, so its blocks are not what is kept — what is, is the lack
     * itself, and a reading that showed it is a reading the pair may be said to have shown it.
     * Where they show none in common, nothing was shown of the pair.
     *
     * <p><b>What the two both showed is asked of the lacks and not of how either reached them.</b>
     * Two readings that leave one block no value have shown that block has none, whatever took the
     * values in each of them. Asked of the refusals whole, the two would be different wherever
     * their routes were, and a choice between two readings that both hold nothing would be
     * reported as holding something.
     *
     * <p>Which is why it is asked here and not by whether the two refusals are equal. A refusal is
     * a value and is equal to what it is: two of them that were reached differently are two
     * different values, and what they showed in common is this question rather than that one.
     */
    static <A> Refusal<A> shownByBoth(Refusal<A> one, Refusal<A> other) {
        if (one instanceof AtEachOf<A> mine && other instanceof AtEachOf<A> theirs) {
            Set<Sameness.Block<A>> both = new LinkedHashSet<>(mine.blocks());
            both.retainAll(theirs.blocks());
            return atEachOf(both);
        }
        if (one instanceof OfThemTogether<A> mine && other instanceof OfThemTogether<A> theirs) {
            Lacks<A> both = mine.lacks().sharedWith(theirs.lacks());
            return both.isEmpty() ? new Nowhere<>() : new OfThemTogether<>(both);
        }
        return one.equals(other) ? one : new Nowhere<>();
    }
}
