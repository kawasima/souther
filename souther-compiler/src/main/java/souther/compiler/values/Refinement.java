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
 * <p><b>Named apart, because a caller may take one of the steps and not the other.</b> Up is a
 * block; down is a set, and no caller of it can spell a block coming back. A pair of names that
 * differed only in which relation was asked would let a caller holding the wrong end read the
 * positions it happened to be given as though they were one value.
 *
 * <p>The warrant is here and not at each ask. Every block of the finer relation is walked when this
 * is made, and one whose positions the coarser relation holds in more than one block is refused —
 * so holding one of these is holding the fact that {@link #coarseBlockOf} answers, rather than a
 * rule each of its callers keeps. Each of the two asks for its own end besides: a block neither
 * relation has is a set of positions this says nothing about.
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
        this.up = up;
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
        // A relation holding no two positions as one has no block to walk and none to write down:
        // every position is its own block, and one position is inside one block of anything. Which
        // is what almost every step taken is between, so it is answered before anything is built.
        if (finer.isDiscrete()) {
            return new Refinement<>(finer, coarser, Map.of());
        }
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
        return new Refinement<>(finer, coarser, Collections.unmodifiableMap(up));
    }

    /** The relation the blocks below are read against. */
    public Sameness<A> coarser() {
        return coarser;
    }

    /**
     * The one block of the coarser relation holding every position of {@code block}.
     *
     * <p>Total over the blocks the finer relation has, and over those only. What was proved when
     * this was made is where each of those lands; a set of positions the finer relation does not
     * hold together is outside it, and an answer for one would be worked out from whichever
     * position was read first — which is the question this type exists to stop being asked.
     *
     * <p>A block of one position is a block of every relation that holds it with nothing, so it
     * lands without anything having been written down for it.
     */
    public Sameness.Block<A> coarseBlockOf(Sameness.Block<A> block) {
        Sameness.Block<A> there = up.get(block);
        if (there != null) {
            return there;
        }
        if (!finer.has(block)) {
            throw new IllegalArgumentException("the relation these positions are read from does"
                    + " not hold them as one value: " + block + ", which it holds as "
                    + InOneOrder.of(finer.holding(block)));
        }
        return coarser.blockOf(block.members().iterator().next());
    }

    /**
     * The blocks of the finer relation holding {@code block}'s positions.
     *
     * <p>More than one where the coarser relation states an equality the finer one does not, which
     * is what a conjunction of two readings leaves: {@code p == q} met with {@code q == r} holds
     * all three as one value, and a side that stated only the first holds them in two blocks. What
     * that side says about the three is what it says about both of those blocks together, and
     * asking one of them would be answering about the positions it happens to hold.
     *
     * <p>Of the blocks the coarser relation has, and of those only. Any other set of positions has
     * blocks under it too, and they would be an answer about a value nothing holds together.
     */
    public Set<Sameness.Block<A>> fineBlocksWithin(Sameness.Block<A> block) {
        if (!coarser.has(block)) {
            throw new IllegalArgumentException("the relation these positions are read against does"
                    + " not hold them as one value: " + block + ", which it holds as "
                    + InOneOrder.of(coarser.holding(block)));
        }
        return finer.holding(block);
    }

    @Override
    public String toString() {
        return finer + " read against " + coarser;
    }
}
