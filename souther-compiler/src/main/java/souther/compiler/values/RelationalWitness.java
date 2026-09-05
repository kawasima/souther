package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Why the denials between an alternative's blocks leave nothing.
 *
 * <p>A lack about several blocks together and not about any of them. Each of the blocks named here
 * is left values of its own; what has nothing is an assignment to all of them at once, so a proof
 * naming one of them would send an author to a place whose own rules are fine with what they leave
 * it.
 *
 * <p><b>Not one shape, because it is not one argument.</b> A block stated to differ from itself is
 * refused by reading the rule; a block left no value by its neighbours is refused by taking values
 * away; a set of blocks with fewer values between them than there are blocks is refused by counting.
 * Held as the counting one alone, the first two would be reported as a shortage of values that no
 * count was taken of — and a reduction learned later is a case added here rather than a sentence
 * somebody has to rewrite.
 *
 * @param <A> what a position is called
 */
public sealed interface RelationalWitness<A> {

    /** Every block the lack is about, which is what a report has to name to say what has nothing. */
    Set<Sameness.Block<A>> blocks();

    /** The same argument about the blocks {@code naming} calls these. */
    default <B> RelationalWitness<B> renamed(java.util.function.Function<A, B> naming) {
        return switch (this) {
            case ABlockApartFromItself<A> it ->
                    new ABlockApartFromItself<>(it.block().renamed(naming));
            case NoValueLeftBetweenThem<A> it -> {
                Set<Sameness.Block<B>> by = new LinkedHashSet<>();
                it.by().forEach(block -> by.add(block.renamed(naming)));
                yield new NoValueLeftBetweenThem<>(it.block().renamed(naming), by);
            }
            case TooFewValuesBetweenThem<A> it -> {
                Set<Sameness.Block<B>> blocks = new LinkedHashSet<>();
                it.blocks().forEach(block -> blocks.add(block.renamed(naming)));
                yield new TooFewValuesBetweenThem<>(blocks, it.available());
            }
        };
    }

    /**
     * The rules hold two positions as one value and state that they differ.
     *
     * <p>One block and a lack about it all the same: what has nothing is the value those positions
     * are, and each of them is left everything on its own.
     */
    record ABlockApartFromItself<A>(Sameness.Block<A> block) implements RelationalWitness<A> {

        @Override
        public Set<Sameness.Block<A>> blocks() {
            return Set.of(block);
        }
    }

    /**
     * A block whose neighbours take every value it was left.
     *
     * @param block the block left nothing
     * @param by the blocks holding one value each, whose values are the ones taken
     */
    record NoValueLeftBetweenThem<A>(Sameness.Block<A> block,
                                     Set<Sameness.Block<A>> by) implements RelationalWitness<A> {

        public NoValueLeftBetweenThem {
            by = Collections.unmodifiableSet(new LinkedHashSet<>(by));
        }

        @Override
        public Set<Sameness.Block<A>> blocks() {
            Set<Sameness.Block<A>> out = new LinkedHashSet<>();
            out.add(block);
            out.addAll(by);
            return Collections.unmodifiableSet(out);
        }
    }

    /**
     * Blocks stated to differ from each other, with fewer values between them than there are of
     * them.
     *
     * <p>Every one of them needs a value no other takes, and {@code available} is every value any
     * of them may hold — so a value each is more than the rules leave. The blocks are pairwise
     * apart and not merely related: {@code p /= q && q /= r} relates three and states nothing of
     * {@code p} and {@code r}, so two values are enough for it and this is not what it comes to.
     *
     * @param blocks the blocks, each stated to differ from every other
     * @param available every value any of them may hold
     */
    record TooFewValuesBetweenThem<A>(Set<Sameness.Block<A>> blocks,
                                      Set<Value> available) implements RelationalWitness<A> {

        public TooFewValuesBetweenThem {
            blocks = Collections.unmodifiableSet(new LinkedHashSet<>(blocks));
            available = Collections.unmodifiableSet(new LinkedHashSet<>(available));
            if (available.size() >= blocks.size()) {
                throw new IllegalArgumentException("blocks stated to differ are refused by there"
                        + " being fewer values than blocks, and " + blocks + " have " + available);
            }
        }
    }
}
