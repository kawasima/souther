package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Whether some way of giving these blocks values tells every stated pair of them apart.
 *
 * <p>A question with two answers and no third. Whether an assignment exists is a fact about the
 * blocks and the values, so a reading that ran out of what it allowed itself to do would be
 * answering about its own budget — and which way it went would turn on where the walk started,
 * which is a fact about how the rules were written. So how much work this is, is decided before
 * there is a question to ask it of ({@link #isWorthLookingThrough}), and inside that it is walked to
 * the end.
 *
 * <p><b>Every block's values are written down.</b> A block whose values nobody counted, or whose
 * values are more than a caller was counting, is not one of these — which of those it was, and what
 * follows from leaving it out, belongs to whoever builds the question rather than here.
 *
 * @param <A> what a position is called
 */
final class TellingApart<A> {

    /** The blocks, in the order the search gives values out in, which {@link #over} settles from
     *  what each of them holds and not from the order the relation stated them. */
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
     * The question over {@code mayHold}, where {@code apartFrom} says which blocks of the whole
     * relation a block is stated to differ from.
     *
     * <p><b>The whole relation's neighbours, cut down here.</b> Which of them are part of this
     * question is settled by which blocks it is over, and that is one fact — handed a relation
     * already cut down to match, it would be two, and a caller that cut it somewhere else would be
     * asking a question with a pair missing. A missing pair is not a question that fails: it is one
     * that finds an assignment nothing stated forbids, which is the wrong answer in the direction
     * nothing else here would catch.
     *
     * <p>The blocks are put in the order a value is easiest to run out of: fewest values first, and
     * among those the one stated to differ from most of the others. Which order they are taken in
     * does not change the answer — the search runs to the end either way — and it changes how much
     * of it is reached before a branch is refused.
     */
    private static <A> TellingApart<A> over(
            Map<Sameness.Block<A>, Set<Value>> mayHold,
            Function<Sameness.Block<A>, Set<Sameness.Block<A>>> apartFrom) {
        Map<Sameness.Block<A>, Set<Sameness.Block<A>>> apart = new LinkedHashMap<>();
        for (Sameness.Block<A> block : mayHold.keySet()) {
            Set<Sameness.Block<A>> theirs = new LinkedHashSet<>(apartFrom.apply(block));
            theirs.retainAll(mayHold.keySet());
            apart.put(block, theirs);
        }
        List<Sameness.Block<A>> order = new ArrayList<>(mayHold.keySet());
        order.sort((one, other) -> {
            int fewest = Integer.compare(mayHold.get(one).size(), mayHold.get(other).size());
            return fewest != 0 ? fewest
                    : Integer.compare(apart.get(other).size(), apart.get(one).size());
        });
        List<List<Value>> values = new ArrayList<>();
        List<int[]> earlier = new ArrayList<>();
        for (int at = 0; at < order.size(); at++) {
            values.add(List.copyOf(mayHold.get(order.get(at))));
            Set<Sameness.Block<A>> theirs = apart.get(order.get(at));
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

    /**
     * How many assignments there may be for one of them to be looked for.
     *
     * <p>Every block's values against every other's, which the search reaches at most one of per
     * branch it takes to the end. Every branch stands on a block of that assignment at each step,
     * so what the search does is this many assignments times how many blocks there are — and not
     * twice this many, since a block left one value is a step it takes all the same and its
     * branches are not all two ways. Which is why the figure below is beside this one.
     *
     * <p>That holds one relation to a fraction of a second whatever shape it is. Measured on the
     * shapes that are actually hard — a relation with no three blocks all stated to differ that
     * three values are still not enough for — it is a few milliseconds, so what this bounds is the
     * case nobody has built rather than the ones there are.
     *
     * <p>Read the other way it is what a declaration may ask for: ten positions over a sum of four
     * cases, or twenty over two. A relation of more positions over a larger carrier is one this
     * says nothing about.
     */
    private static final long MOST_ASSIGNMENTS = 1L << 20;

    /**
     * And how many blocks a question may be over.
     *
     * <p>Beside the assignments and not instead of them, because the two bound different halves of
     * what this does. A block is a step on every branch the search takes and a pair between two
     * blocks is a thing checked at that step, so how many blocks there are decides what a branch
     * costs where the assignments decide how many branches there are. Bounded by the assignments
     * alone, a run of blocks each holding one value is a question of a single assignment and of as
     * many blocks as anybody cares to write — which is a search that costs whatever the model
     * costs, and the making of the question costs the square of it.
     *
     * <p>Which is the shape that showed the two were two. Nothing else here would have: a block
     * holding one value is what the argument above this one leaves behind, and it multiplies the
     * assignments by one.
     */
    private static final int MOST_BLOCKS = 64;

    /**
     * The question over {@code mayHold} where it is one worth looking through, and nothing where
     * it is not.
     *
     * <p><b>The only way to hold one of these.</b> Every figure the search is bounded by is asked
     * here, before the question is made, and there is no way of making one that does not come
     * through this — so a figure cannot be forgotten by a caller and the asking cannot be put after
     * the making. Both of those had happened while this was two calls with the order written
     * between them in a comment: a comment is not violated, it only stops being true.
     *
     * <p>Nothing where the shape is past what is looked through, which is the absence of a question
     * and never an answer to one. What the search answers stays two-valued; a shape nobody may ask
     * about is not a third thing it says.
     *
     * @param relation how large the relation this is a part of is, which the making of the question
     *                 reads once however few blocks the question is over
     */
    static <A> Optional<TellingApart<A>> lookingThrough(
            Apartness.Extent relation, Map<Sameness.Block<A>, Set<Value>> mayHold,
            Function<Sameness.Block<A>, Set<Sameness.Block<A>>> apartFrom) {
        return relation.isSmallEnoughToRead() && isWorthLookingThrough(mayHold)
                ? Optional.of(over(mayHold, apartFrom)) : Optional.empty();
    }

    /**
     * Whether a question over {@code mayHold} is one worth looking through.
     *
     * <p>Asked of the values alone and before anything is built, which is what makes it a bound.
     * Both figures are read off {@code mayHold} — how many blocks it has and what their values come
     * to between them — so nothing has to be assembled to find out whether assembling it was
     * allowed. Asked of a question already made, this would be a bound on the search and none at
     * all on the making of it, and the making is the square of the blocks.
     *
     * <p>Counted and compared in one place, so the figures are named once. Answered as numbers for
     * a caller to hold against its own bounds, each would be written twice — once to count up to
     * and once to compare with — and two spellings of one figure that drift apart make a question
     * looked through further than anything allowed.
     *
     * <p>A question over no blocks is within both, and is one an empty assignment answers.
     */
    private static <A> boolean isWorthLookingThrough(Map<Sameness.Block<A>, Set<Value>> mayHold) {
        if (mayHold.size() > MOST_BLOCKS) {
            return false;
        }
        long many = 1;
        for (Set<Value> these : mayHold.values()) {
            // Counted no further than the bound, because the product of enough sets of values is a
            // number no `long` holds and past the bound the answer is already settled.
            if (these.size() > MOST_ASSIGNMENTS / Math.max(many, 1)) {
                return false;
            }
            many *= these.size();
        }
        return true;
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
