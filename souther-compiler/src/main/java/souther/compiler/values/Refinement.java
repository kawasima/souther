package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One relation holding as one everything a second one does, said as the way between their blocks.
 *
 * <p>Two questions are asked across that step and they are not the same question. A block of the
 * finer relation is inside one block of the coarser, so what was said about it is said about that
 * one block — a total function up. A block of the coarser relation is made of several of the finer,
 * so what the finer says about it is what it says about every one of them — a set, and not a block.
 *
 * <p><b>Which is why the two are named apart.</b> Both were written as a member handed to
 * {@link Sameness#blockOf}: one position taken out of the block, the relation asked about that
 * one. Up, that is an answer any member gives and the pick is only a pick. Down, the members answer
 * differently and the pick decides — so one spelling stood for a lookup that is warranted and for
 * one that is not, and which of the two a line was doing could not be read off it. Named apart, a
 * caller asking down cannot spell a block coming back.
 *
 * <p>The warrant is here and not at each ask. Every block of the finer relation is walked when this
 * is made, and one whose positions the coarser relation holds in more than one block is refused —
 * so holding one of these is holding the fact that {@link #coarseBlockOf} answers, rather than a
 * rule each of its callers keeps.
 *
 * @param <A> what a position is called
 */
public final class Refinement<A> {

    private final Sameness<A> finer;
    private final Sameness<A> coarser;

    /** Where each block of the finer relation lands, worked out where this is made. A block of one
     *  is absent: it is inside one block of anything, so the coarser relation answers for it. */
    private final Map<Sameness.Block<A>, Sameness.Block<A>> up;

    private Refinement(Sameness<A> finer, Sameness<A> coarser,
                       Map<Sameness.Block<A>, Sameness.Block<A>> up) {
        this.finer = finer;
        this.coarser = coarser;
        this.up = Collections.unmodifiableMap(up);
    }

    /**
     * {@code coarser} read as what it does to {@code finer}'s blocks.
     *
     * <p>Refused where it holds some block's positions apart, which is not a coarsening at all:
     * {@code finer} states an equality {@code coarser} does not, and there is no block up there for
     * what that equality named. A conjunction leaves a coarser relation and a choice a finer one,
     * so the two are in hand this way round wherever one is asked of the other — and a caller
     * holding them the other way round is asking {@link #fineBlocksWithin}.
     */
    public static <A> Refinement<A> of(Sameness<A> finer, Sameness<A> coarser) {
        Map<Sameness.Block<A>, Sameness.Block<A>> up = new LinkedHashMap<>();
        for (Sameness.Block<A> block : finer.joined()) {
            Set<Sameness.Block<A>> there = coarser.holding(block);
            if (there.size() != 1) {
                // Said as where each position landed, because the blocks alone are two renderings
                // beside each other and one block of those positions is written the same way.
                Map<A, Sameness.Block<A>> each = new LinkedHashMap<>();
                block.members().forEach(member -> each.put(member, coarser.blockOf(member)));
                throw new IllegalArgumentException("positions held as one at " + block
                        + " are held apart by the relation they are read against, which holds "
                        + InOneOrder.of(each));
            }
            up.put(block, there.iterator().next());
        }
        return new Refinement<>(finer, coarser, up);
    }

    /** The relation the blocks below are read against. */
    public Sameness<A> coarser() {
        return coarser;
    }

    /**
     * The one block of the coarser relation holding every position of {@code block}.
     *
     * <p>Total over the blocks the finer relation has, which is what making this proved. A block
     * it does not have is one position on its own, and one position is in one block of anything.
     */
    public Sameness.Block<A> coarseBlockOf(Sameness.Block<A> block) {
        Sameness.Block<A> there = up.get(block);
        return there != null ? there : coarser.blockOf(block.members().iterator().next());
    }

    /**
     * The blocks of the finer relation holding {@code block}'s positions.
     *
     * <p>More than one where the coarser relation states an equality the finer one does not, which
     * is what a conjunction of two readings leaves: {@code p == q} met with {@code q == r} holds
     * all three as one value, and a side that stated only the first holds them in two blocks. What
     * that side says about the three is what it says about both of those blocks together, and
     * asking one of them would be answering about the positions it happens to hold.
     */
    public Set<Sameness.Block<A>> fineBlocksWithin(Sameness.Block<A> block) {
        return finer.holding(block);
    }

    @Override
    public String toString() {
        return finer + " read against " + coarser;
    }
}
