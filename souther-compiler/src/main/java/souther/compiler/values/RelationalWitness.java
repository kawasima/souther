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
 * away; a set of blocks with fewer values between them than there are blocks is refused by counting;
 * and blocks no assignment of what they hold tells apart are refused by looking for one. Held as the
 * counting one alone, the others would be reported as a shortage of values that no count was taken
 * of — and a reduction learned later is a case added here rather than a sentence somebody has to
 * rewrite.
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
            case NoAssignmentTellsThemApart<A> it -> {
                Set<Sameness.Block<B>> blocks = new LinkedHashSet<>();
                it.blocks().forEach(block -> blocks.add(block.renamed(naming)));
                yield new NoAssignmentTellsThemApart<>(blocks);
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
     * <p><b>Every block the argument rests on, and not the ones that took the last values.</b> A
     * neighbour holding one value may hold it because a rule said so or because its own neighbours
     * left it that, and the second kind is not a reason on its own — {@code q} left at one value by
     * {@code p} takes that value from {@code r}, and {@code q} with {@code r} alone is satisfiable.
     * Named as the neighbours in hand, this would say a lack is about two blocks where nothing is
     * refused until three of them are read, which is a sentence sending an author to a pair whose
     * own rules are fine.
     *
     * <p>Which is also what makes two of these comparable. {@link Refusal#shownByBoth} keeps a
     * collective lack where two readings show the same one, and that is only sound where the
     * witness is the whole argument — two lacks reached through different blocks are two lacks, and
     * they say so by naming them.
     *
     * @param block the block left nothing
     * @param by every other block the argument rests on, which is what forced the values out of it
     *           and what forced those in turn
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

    /**
     * Blocks no way of giving them values tells apart.
     *
     * <p>Shown by looking for one and running out, which is what a shortage cannot show and is not
     * a stronger version of it: {@code a /= b && b /= c && c /= d && d /= e && e /= a} over two
     * values has no set of three blocks all stated to differ and needs three values all the same.
     * So this is beside {@link TooFewValuesBetweenThem} and never a way of writing one — which
     * that one refuses to be written as, since it is given the values it counted and there are not
     * fewer of them here.
     *
     * <p><b>The blocks and no values beside them.</b> What has nothing is an assignment to all of
     * them, and no set of values stands for which assignments there were: the same blocks holding
     * the same values are refused in a ring of odd length and satisfied in a ring of even length,
     * so a reader handed the values could not tell the two apart. {@link TooFewValuesBetweenThem}
     * carries them because a shortage is a fact about how many there are; this is not, and carrying
     * them would be carrying what a count was not taken of.
     *
     * <p><b>Read as the same sentence as a shortage, and rightly.</b> What a report says of either
     * is that these positions are left no way of differing, and an author is sent to the same
     * rules. The two are told apart here because a proof is not a sentence — a reduction learned
     * later reads which argument refused — and not because the words would have to differ.
     *
     * <p>Several of them wherever the block each is left something on its own, which is what a
     * relation is asked about: a lack at one block is that block's own answer and is reached before
     * anything asks what the denials between blocks come to.
     *
     * @param blocks the blocks an assignment was looked for over, which are those whose values are
     *               written down: a block holding more of them than the relation has blocks was
     *               never going to run out and is not part of what has nothing
     */
    record NoAssignmentTellsThemApart<A>(
            Set<Sameness.Block<A>> blocks) implements RelationalWitness<A> {

        public NoAssignmentTellsThemApart {
            blocks = Collections.unmodifiableSet(new LinkedHashSet<>(blocks));
        }
    }
}
