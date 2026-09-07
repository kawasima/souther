package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What the denials between an alternative's blocks leave nothing of.
 *
 * <p>A claim and not the argument that reached it. Which blocks were read to show a lack, and in
 * what order their values went, is how it was reached; what it says is that these blocks are left
 * nothing. Two readings that reach one claim along different routes have shown one thing, and it is
 * the claim that {@link Refusal#shownByBoth} keeps — a route carried in here would make them two
 * claims and a choice between the readings would be answered as though neither had shown anything.
 * Where a route is wanted, it is {@link RelationalEvidence}'s.
 *
 * <p>A lack about several blocks together and not about any of them, in all but one of these. Each
 * of the blocks named is left values of its own; what has nothing is an assignment to all of them
 * at once, so a claim naming one of them would send an author to a place whose own rules are fine
 * with what they leave it. {@link NoValueLeftForIt} is the one that does name a block, and it says
 * of that block that the denials leave it nothing.
 *
 * <p><b>Not one shape, because it is not one argument.</b> A block stated to differ from itself is
 * refused by reading the rule; a block its neighbours leave no room for anything is refused by
 * narrowing; a set of blocks with fewer values between them than there are blocks is refused by
 * counting; and blocks no assignment of what they hold tells apart are refused by looking for one.
 * Held as the counting one alone, the others would be reported as a shortage of values that no
 * count was taken of — and a reduction learned later is a case added here rather than a sentence
 * somebody has to rewrite.
 *
 * @param <A> what a position is called
 */
public sealed interface RelationalLack<A> {

    /** Every block the lack is about, which is what a report has to name to say what has nothing. */
    Set<Sameness.Block<A>> blocks();

    /** The same argument about the blocks {@code naming} calls these. */
    default <B> RelationalLack<B> renamed(java.util.function.Function<A, B> naming) {
        return switch (this) {
            case ABlockApartFromItself<A> it ->
                    new ABlockApartFromItself<>(it.block().renamed(naming));
            case NoValueLeftForIt<A> it -> new NoValueLeftForIt<>(it.block().renamed(naming));
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
    record ABlockApartFromItself<A>(Sameness.Block<A> block) implements RelationalLack<A> {

        @Override
        public Set<Sameness.Block<A>> blocks() {
            return Set.of(block);
        }
    }

    /**
     * A block the denials leave no value at all.
     *
     * <p>The block and nothing beside it, because that is the whole of what is claimed. Which
     * blocks took its values, and which took theirs, is how the claim was reached — a route, and
     * two readings that reach one claim by two routes have shown the same thing. Carried here, the
     * route would be part of what {@link Refusal#shownByBoth} compares, and a choice between two
     * readings that each leave this block nothing would be answered as though neither had.
     *
     * <p>Where a report has to send an author somewhere, the blocks to read are asked of
     * {@link RelationalEvidence}, which is what carries the route.
     *
     * @param block the block left nothing
     */
    record NoValueLeftForIt<A>(Sameness.Block<A> block) implements RelationalLack<A> {

        @Override
        public Set<Sameness.Block<A>> blocks() {
            return Set.of(block);
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
                                      Set<Value> available) implements RelationalLack<A> {

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
     * <p><b>Shown of blocks an assignment was looked for over, or of blocks holding a set that was
     * looked over.</b> Three arguments reach this. A search over the whole relation runs out; a
     * matching over blocks all stated to differ runs out, where a count of their values came out
     * even and some part of them is short all the same; and blocks that several such sets are
     * short of together are refused by any one of those sets being short. So what the blocks are is
     * that no assignment gives all of them values telling every stated pair apart, and not that
     * each of them was a step of one walk.
     *
     * @param blocks the blocks no assignment tells apart, which are those whose values are written
     *               down: a block holding more of them than the relation has blocks was never going
     *               to run out and is not part of what has nothing
     */
    record NoAssignmentTellsThemApart<A>(
            Set<Sameness.Block<A>> blocks) implements RelationalLack<A> {

        public NoAssignmentTellsThemApart {
            blocks = Collections.unmodifiableSet(new LinkedHashSet<>(blocks));
        }
    }
}
