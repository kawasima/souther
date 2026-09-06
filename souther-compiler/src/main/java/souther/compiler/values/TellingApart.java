package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whether some way of giving these blocks values tells every stated pair of them apart.
 *
 * <p>A question with two answers and no third. Whether an assignment exists is a fact about the
 * blocks and the values, so a reading that ran out of what it allowed itself to do would be
 * answering about its own budget — and which way it went would turn on where the walk started,
 * which is a fact about how the rules were written. So how much work this is, is decided before it
 * begins ({@link #assignments}), and inside that it is walked to the end.
 *
 * <p><b>Every block's values are written down.</b> A block whose values nobody counted, or whose
 * values are more than a caller was counting, is not one of these — which of those it was, and what
 * follows from leaving it out, belongs to whoever builds the question rather than here.
 *
 * @param <A> what a position is called
 */
final class TellingApart<A> {

    /** The blocks, in the order the relation stated them, so that two runs over one relation take
     *  them the same way round. */
    private final List<Sameness.Block<A>> blocks;

    /** What each of them may hold, indexed as the blocks are. */
    private final List<List<Value>> mayHold;

    /** Which earlier blocks each is stated to differ from, indexed as the blocks are. Earlier
     *  only, because a pair is checked once and the walk gives values out in this order. */
    private final List<int[]> apartFromEarlier;

    private TellingApart(List<Sameness.Block<A>> blocks, List<List<Value>> mayHold,
                         List<int[]> apartFromEarlier) {
        this.blocks = blocks;
        this.mayHold = mayHold;
        this.apartFromEarlier = apartFromEarlier;
    }

    /**
     * The question over {@code mayHold}, where {@code apart} says which blocks a block is stated to
     * differ from.
     *
     * <p>The blocks are put in the order a value is easiest to run out of: fewest values first, and
     * among those the one stated to differ from most of the others. Which order they are taken in
     * does not change the answer — the walk runs to the end either way — and it changes how much of
     * the walk is reached before a branch is refused.
     */
    static <A> TellingApart<A> over(Map<Sameness.Block<A>, Set<Value>> mayHold,
                                    Map<Sameness.Block<A>, Set<Sameness.Block<A>>> apart) {
        List<Sameness.Block<A>> order = new ArrayList<>(mayHold.keySet());
        order.sort((one, other) -> {
            int fewest = Integer.compare(mayHold.get(one).size(), mayHold.get(other).size());
            return fewest != 0 ? fewest
                    : Integer.compare(apart.getOrDefault(other, Set.of()).size(),
                            apart.getOrDefault(one, Set.of()).size());
        });
        List<List<Value>> values = new ArrayList<>();
        List<int[]> earlier = new ArrayList<>();
        for (int at = 0; at < order.size(); at++) {
            values.add(List.copyOf(mayHold.get(order.get(at))));
            Set<Sameness.Block<A>> theirs = apart.getOrDefault(order.get(at), Set.of());
            List<Integer> before = new ArrayList<>();
            for (int other = 0; other < at; other++) {
                if (theirs.contains(order.get(other))) {
                    before.add(other);
                }
            }
            int[] indices = new int[before.size()];
            for (int each = 0; each < indices.length; each++) {
                indices[each] = before.get(each);
            }
            earlier.add(indices);
        }
        return new TellingApart<>(order, values, earlier);
    }

    /** The blocks an assignment is being looked for over. */
    Set<Sameness.Block<A>> blocks() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(blocks));
    }

    /** Whether there is nothing to look for, which is what no block at all leaves. */
    boolean isNothingToAsk() {
        return blocks.isEmpty();
    }

    /**
     * How many assignments there are, which is how much walking this can be.
     *
     * <p>Every block's values against every other's, which the walk reaches at most one of per
     * branch it takes to the end. Counted up to {@code most} and no further: past there the answer
     * is that it is more than that, and the product of enough sets of values is a number no
     * {@code long} holds.
     */
    long assignments(long most) {
        long many = 1;
        for (List<Value> these : mayHold) {
            if (these.size() > most / Math.max(many, 1)) {
                return most + 1;
            }
            many *= these.size();
        }
        return many;
    }

    /** Whether some assignment gives every block a value no block it is stated to differ from
     *  takes. */
    boolean isSatisfiable() {
        return given(0, new Value[blocks.size()]);
    }

    /** Whether the blocks from {@code at} on can be given values, where the ones before it have
     *  them. */
    private boolean given(int at, Value[] taken) {
        if (at == blocks.size()) {
            return true;
        }
        for (Value value : mayHold.get(at)) {
            if (free(at, value, taken)) {
                taken[at] = value;
                if (given(at + 1, taken)) {
                    return true;
                }
            }
        }
        taken[at] = null;
        return false;
    }

    /** Whether no block before {@code at} that it is stated to differ from holds {@code value}. */
    private boolean free(int at, Value value, Value[] taken) {
        for (int other : apartFromEarlier.get(at)) {
            if (value.equals(taken[other])) {
                return false;
            }
        }
        return true;
    }
}
