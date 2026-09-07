package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What each block of a relation is left, as one value.
 *
 * <p>A value and not a map a narrowing writes into. What a relation comes to is worked out by
 * narrowing what its blocks are left until nothing more goes, and a narrowing that wrote into what
 * it was reading would answer from how far it had got — a block read before its neighbour was cut
 * down holds one thing and the same block read after holds another. Held as a value, a narrowing
 * reads one of these and answers with the next, and what a step of it comes to is settled by what
 * it was handed.
 *
 * <p>Every block the relation names is here, including the ones whose values nobody wrote down:
 * which of the three answers a block has is {@link Admits}'s to say, and a block missing from the
 * map would be a fourth answer said by leaving something out.
 *
 * <p><b>And every one of them is left something.</b> A block left no value at all is that block's
 * own answer, reached before anything asks what the denials between blocks come to — and the rule
 * a narrowing applies is about what a neighbour leaves room for, which a block holding nothing
 * leaves for no value. Admitted here, a round reading one would take every value from every
 * neighbour of it, and what came back would be blocks the relation does not empty. So where a
 * narrowing leaves a block nothing is said by {@link Closure.Contradicted} and nowhere else.
 *
 * @param <A> what a position is called
 */
public record Domains<A>(Map<Sameness.Block<A>, Admits> byBlock) {

    public Domains {
        byBlock = Collections.unmodifiableMap(new LinkedHashMap<>(byBlock));
        byBlock.forEach((block, admits) -> {
            if (admits.isNone()) {
                throw new IllegalArgumentException(
                        "a block left no value at all is that block's own answer and is reached"
                                + " before a relation is asked what its denials come to: " + block);
            }
        });
    }

    /**
     * What {@code asked} says each of {@code blocks} is left.
     *
     * @param atMost how many values are worth counting, which is how many blocks the relation has
     */
    static <A> Domains<A> of(Set<Sameness.Block<A>> blocks,
                             Apartness.WhatABlockAdmits<A> asked, int atMost) {
        Map<Sameness.Block<A>, Admits> out = new LinkedHashMap<>();
        blocks.forEach(block -> out.put(block, asked.of(block, atMost)));
        return new Domains<>(out);
    }

    /** What each block is left, written in one order — see {@link InOneOrder}. */
    @Override
    public String toString() {
        return InOneOrder.of(byBlock);
    }

    /** What {@code block} is left. */
    public Admits of(Sameness.Block<A> block) {
        return byBlock.get(block);
    }

    /** Every block these are about. */
    public Set<Sameness.Block<A>> blocks() {
        return byBlock.keySet();
    }
}
