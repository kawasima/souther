package souther.compiler.values;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Taking from each block the values its denials leave it nowhere to hold.
 *
 * <p><b>What a denial says about one value.</b> {@code p /= q} lets {@code p} hold {@code v} where
 * {@code q} is left some value other than {@code v}, and refuses it where {@code q} is left only
 * {@code v} or nothing at all. So what a step takes away is the values a neighbour leaves no room
 * for, and a neighbour left one value is a case of that rather than what the rule is about. Written
 * as "a block left one value takes it from its neighbours", the same removals happen and the reason
 * is a shape the reading passed through, which is why the rule is said the first way.
 *
 * <p><b>A round reads one reading and writes the next.</b> Every removal a round makes is worked
 * out against what the blocks were left before that round, and none of them against what the round
 * itself has taken. Written into as it goes, a round would take from a block what a neighbour cut
 * down earlier in the same round leaves no room for, and how far it had got when it reached that
 * block is the order the denials happen to be stated in. What that costs is one more round, and
 * what it buys is that the answer is a fact about the relation.
 *
 * <p><b>Which is what makes an emptied block an answer rather than a stopping place.</b> Rounds go
 * on while something moves, and the first round that leaves a block no value at all is what the
 * relation comes to. Run past it, the rule above stops holding — a block left nothing leaves room
 * for no value at all, so it would take every value from every neighbour, and which blocks ended up
 * empty would be settled by which of them was emptied first.
 *
 * @param <A> what a position is called
 */
final class Narrowing<A> {

    /** Which blocks each is stated to differ from, less the pairs whose ends are one block. A
     *  block stated to differ from itself is refused by reading the rule, and read as a neighbour
     *  of its own it would be a block taking its values from itself. */
    private final Map<Sameness.Block<A>, Set<Sameness.Block<A>>> apart;

    private Narrowing(Map<Sameness.Block<A>, Set<Sameness.Block<A>>> apart) {
        this.apart = apart;
    }

    /** What narrowing {@code domains} against {@code relation} comes to. */
    static <A> Closure<A> of(Apartness<A> relation, Domains<A> domains) {
        // Walked once over the pairs rather than asked of each block, which reads them all again
        // per block. What a block is stated to differ from is the same answer either way.
        Map<Sameness.Block<A>, Set<Sameness.Block<A>>> apart = new LinkedHashMap<>();
        domains.blocks().forEach(block -> apart.put(block, new LinkedHashSet<>()));
        for (Apartness.Edge<A> edge : relation.edges()) {
            if (edge.isOfOneBlock()) {
                continue;
            }
            apart.get(edge.one()).add(edge.other());
            apart.get(edge.other()).add(edge.one());
        }
        return new Narrowing<>(apart).from(domains);
    }

    /**
     * Round after round, until nothing moves or a round leaves a block nothing.
     *
     * <p>A round that takes nothing writes nothing. What the blocks are left is read from one
     * reading and written into the next, and a round asks first whether it has anything to write:
     * a relation whose blocks all leave each other room is most of what a compilation reads, and
     * for one of those this walks the pairs and allocates nothing at all.
     */
    private Closure<A> from(Domains<A> domains) {
        Set<Provenance.Removal<A>> taken = new LinkedHashSet<>();
        Domains<A> here = domains;
        for (int round = 1; ; round++) {
            Map<Sameness.Block<A>, Admits> writing = null;
            Set<Sameness.Block<A>> emptied = null;
            for (Sameness.Block<A> block : here.blocks()) {
                if (!(here.of(block) instanceof Admits.These it) || !anythingGoesFrom(here, block)) {
                    continue;
                }
                Set<Value> keeping = new LinkedHashSet<>();
                for (Value value : it.values()) {
                    Set<Sameness.Block<A>> blockers = blocking(here, block, value);
                    if (blockers.isEmpty()) {
                        keeping.add(value);
                    } else {
                        taken.add(new Provenance.Removal<>(block, value, round, blockers));
                    }
                }
                if (writing == null) {
                    writing = new LinkedHashMap<>(here.byBlock());
                }
                writing.put(block, new Admits.These(keeping));
                if (keeping.isEmpty()) {
                    // Read off what is being written and not off a reading, because a block left
                    // nothing is not one a reading may hold: which blocks a round emptied is what
                    // this answers with, and every block of the reading it hands on is left a value.
                    if (emptied == null) {
                        emptied = new LinkedHashSet<>();
                    }
                    emptied.add(block);
                }
            }
            if (emptied != null) {
                return new Closure.Contradicted<>(emptied, new Provenance<>(taken));
            }
            if (writing == null) {
                return new Closure.Stable<>(here);
            }
            here = new Domains<>(writing);
        }
    }

    /** Whether any value {@code block} is left is one a neighbour leaves it no room for, which is
     *  asked before anything is built for the round to write. */
    private boolean anythingGoesFrom(Domains<A> here, Sameness.Block<A> block) {
        for (Value value : ((Admits.These) here.of(block)).values()) {
            for (Sameness.Block<A> next : apart.get(block)) {
                if (!leavesRoomFor(here.of(next), value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The neighbours of {@code block} that leave {@code value} nowhere to go, which are the ones
     *  left no value other than it. */
    private Set<Sameness.Block<A>> blocking(Domains<A> here, Sameness.Block<A> block, Value value) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        for (Sameness.Block<A> next : apart.get(block)) {
            if (!leavesRoomFor(here.of(next), value)) {
                out.add(next);
            }
        }
        return out;
    }

    /**
     * Whether a block left {@code admits} may hold something other than {@code value}.
     *
     * <p>A block holding more values than were counted has more of them than it has neighbours, so
     * it is left room for whatever it is asked about. A block whose values nobody wrote down is one
     * nothing is known of, and nothing is what it refuses: read as leaving no room, a relation over
     * strings would empty its neighbours and a lack would be reported that no rule shows.
     */
    private static boolean leavesRoomFor(Admits admits, Value value) {
        return switch (admits) {
            // Asked of how many there are and of whether this is one of them, which is the same
            // question as whether some value here is not this one and is two lookups rather than a
            // walk over the values.
            case Admits.These it -> it.values().size() > 1
                    || (it.values().size() == 1 && !it.values().contains(value));
            case Admits.MoreThanCounted _ -> true;
            case Admits.NotKnown _ -> true;
        };
    }
}
