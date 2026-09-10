package souther.compiler.values;

import souther.compiler.hash.ValueHash;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Which values a narrowing took from which blocks, and what left them nowhere else to go.
 *
 * <p>Every removal is filed under the round it happened in, and what blocked a value is read off
 * the round before it. So the blocks a lack rests on are reached by walking back through rounds
 * that only ever decrease, and a block that came to hold one value after the removal it is being
 * offered as a reason for is not a reason for it.
 *
 * <p><b>Which is what a final reading of the blocks cannot say.</b> Two neighbours may each hold
 * one value once a narrowing has stopped and only one of them have held it when the value went, and
 * the sentence naming both says a block took a value it had not yet come to hold. The rounds are
 * what tell those two apart, so they are carried rather than worked out again from where the
 * narrowing stopped.
 *
 * <p><b>And the blockers of one removal are alternatives and not a conjunction.</b> Two neighbours
 * each holding the same one value each take it on their own, so a report that read them as what
 * together took it would be describing an argument that was never made. What a reader wants of
 * them is that the value had nowhere to go, which is what any one of them shows.
 *
 * <p><b>Filed under the block each was taken from.</b> What a report asks of these is what one
 * block's emptying rests on, which is a question about that block and the ones behind it — asked of
 * the removals one at a time, a report about a relation whose blocks were all emptied at once reads
 * every removal once per block, and the blocks a relation may have and the removals it may make are
 * the same number. So which removals took from which block is worked out when these are made.
 *
 * @param <A> what a position is called
 */
public final class Provenance<A> {

    private final Set<Removal<A>> removals;

    /** Which removals took from each block, so that walking back from one is a lookup apiece. */
    private final Map<Sameness.Block<A>, List<Removal<A>>> from;

    public Provenance(Set<Removal<A>> removals) {
        this.removals = Collections.unmodifiableSet(new LinkedHashSet<>(removals));
        Map<Sameness.Block<A>, List<Removal<A>>> from = new HashMap<>();
        this.removals.forEach(removal ->
                from.computeIfAbsent(removal.block(), _ -> new ArrayList<>()).add(removal));
        this.from = from;
    }

    /** The removals one walk made, in the order it made them, which nothing reads. */
    static <A> Provenance<A> of(List<Removal<A>> made) {
        return new Provenance<>(new LinkedHashSet<>(made));
    }

    /** Every removal, which is what one narrowing took. */
    public Set<Removal<A>> removals() {
        return removals;
    }

    /** The removals, written in one order whichever order they were made in — see
     *  {@link InOneOrder}. */
    @Override
    public String toString() {
        return InOneOrder.of(removals);
    }

    /** Nothing was taken from anything, which is what a relation narrowed by no round has. */
    static <A> Provenance<A> nothing() {
        return new Provenance<>(Set.of());
    }

    /**
     * One value taken from one block, and what left it nowhere to go.
     *
     * @param block the block the value was taken from
     * @param value the value
     * @param round which round of the narrowing took it, counting the first from one
     * @param blockers the neighbours that were left only this value the round before, each of
     *                 which is on its own why the value could not stay
     */
    public record Removal<A>(Sameness.Block<A> block, Value value, int round,
                             Set<Sameness.Block<A>> blockers) {

        public Removal {
            blockers = Collections.unmodifiableSet(new LinkedHashSet<>(blockers));
        }

        /** The blockers written in one order — see {@link InOneOrder}. */
        @Override
        public String toString() {
            return block + " lost " + value + " in round " + round + " to "
                    + InOneOrder.of(blockers);
        }

        /**
         * What was taken, from where, when and by what — each in its own place, see
         * {@link ValueHash}.
         *
         * <p>Said here rather than left to what a record answers, because a route holds several of
         * these and what several of them come to is their numbers added. The blockers are a set and
         * a record carries its last component up unchanged, so two removals would come to one
         * number whenever the same blockers were shared out between them the other way — and what
         * a removal says is which of them left this value nowhere to go.
         */
        @Override
        public int hashCode() {
            return ValueHash.ofItsParts(Removal.class, block.hashCode(), value.hashCode(), round,
                    blockers.hashCode());
        }
    }

    /**
     * Every block the emptying of {@code block} rests on, which is what took its values and what
     * left those blocks holding what they took them with.
     *
     * <p>Walked back through the rounds and never across one. A neighbour that blocked a value in
     * round {@code n} was left one value by rounds before {@code n}, so what that neighbour rests
     * on is asked of those rounds alone — and the walk ends because a round is a number that goes
     * down.
     *
     * <p>{@code block} itself is not among them: what a report says is that this block is left
     * nothing, and the blocks named beside it are the ones an author is sent to read.
     */
    public Set<Sameness.Block<A>> restingOn(Sameness.Block<A> block) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        Deque<Asked<A>> asking = new ArrayDeque<>();
        Set<Asked<A>> already = new LinkedHashSet<>();
        asking.add(new Asked<>(block, Integer.MAX_VALUE));
        while (!asking.isEmpty()) {
            Asked<A> here = asking.removeFirst();
            if (!already.add(here)) {
                continue;
            }
            for (Removal<A> removal : from.getOrDefault(here.block(), List.of())) {
                if (removal.round() >= here.before()) {
                    continue;
                }
                for (Sameness.Block<A> blocker : removal.blockers()) {
                    out.add(blocker);
                    asking.add(new Asked<>(blocker, removal.round()));
                }
            }
        }
        out.remove(block);
        return Collections.unmodifiableSet(out);
    }

    /** The same removals, about the blocks {@code naming} calls these. */
    public <B> Provenance<B> renamed(Function<A, B> naming) {
        Set<Removal<B>> out = new LinkedHashSet<>();
        for (Removal<A> removal : removals) {
            Set<Sameness.Block<B>> blockers = new LinkedHashSet<>();
            removal.blockers().forEach(block -> blockers.add(block.renamed(naming)));
            out.add(new Removal<>(removal.block().renamed(naming), removal.value(),
                    removal.round(), blockers));
        }
        return new Provenance<>(out);
    }

    /** The same removals whichever order they were made in. */
    @Override
    public boolean equals(Object said) {
        return said instanceof Provenance<?> it && removals.equals(it.removals);
    }

    /** The removals it holds, in no order — see {@link ValueHash}. */
    @Override
    public int hashCode() {
        return ValueHash.ofWhatItHolds(Provenance.class, removals.hashCode(), removals.size());
    }

    /** One block, asked of the rounds before {@code before}. */
    private record Asked<A>(Sameness.Block<A> block, int before) {}
}
