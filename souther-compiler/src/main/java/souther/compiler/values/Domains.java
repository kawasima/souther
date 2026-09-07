package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * @param <A> what a position is called
 */
public record Domains<A>(Map<Sameness.Block<A>, Admits> byBlock) {

    public Domains {
        byBlock = Collections.unmodifiableMap(new LinkedHashMap<>(byBlock));
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

    /** What {@code block} is left. */
    public Admits of(Sameness.Block<A> block) {
        return byBlock.get(block);
    }

    /** Every block these are about. */
    public Set<Sameness.Block<A>> blocks() {
        return byBlock.keySet();
    }

    /** The blocks settled to hold no value at all. */
    public Set<Sameness.Block<A>> leftNothing() {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        byBlock.forEach((block, admits) -> {
            if (admits.isNone()) {
                out.add(block);
            }
        });
        return Collections.unmodifiableSet(out);
    }

    /** Whether any block is settled to hold no value at all. */
    public boolean holdNothingSomewhere() {
        return byBlock.values().stream().anyMatch(Admits::isNone);
    }
}
