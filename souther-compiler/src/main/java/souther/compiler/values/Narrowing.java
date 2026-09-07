package souther.compiler.values;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 * <p><b>Walked over the blocks as numbers.</b> A round asks what each block's neighbours are left,
 * which is a question about the same few blocks over and over; read through the blocks themselves
 * it is a lookup apiece, and a reading built for each round is a map of them all copied for what a
 * round changed. So the blocks are numbered once, what they are left is an array, and a reading is
 * made where one is answered with — which is where a reader has one.
 *
 * @param <A> what a position is called
 */
final class Narrowing<A> {

    /** The blocks, in the order the reading gives them. */
    private final List<Sameness.Block<A>> blocks;

    /** Which blocks each is stated to differ from, as their numbers here, and less the pairs whose
     *  ends are one block. A block stated to differ from itself is refused by reading the rule, and
     *  read as a neighbour of its own it would be a block taking its values from itself. */
    private final int[][] apart;

    private Narrowing(List<Sameness.Block<A>> blocks, int[][] apart) {
        this.blocks = blocks;
        this.apart = apart;
    }

    /** What narrowing {@code domains} against {@code relation} comes to. */
    static <A> Closure<A> of(Apartness<A> relation, Domains<A> domains) {
        List<Sameness.Block<A>> blocks = new ArrayList<>(domains.blocks());
        Map<Sameness.Block<A>, Integer> numbered = new HashMap<>(blocks.size() * 2);
        for (int at = 0; at < blocks.size(); at++) {
            numbered.put(blocks.get(at), at);
        }
        // The pairs read once, as the two blocks each names and how many pairs name each block.
        // Which blocks a block is stated to differ from is then filled in without asking what a
        // block is called again: a block is a set of positions, so asking is what this walk costs.
        int[] ends = new int[2 * relation.edges().size()];
        int[] many = new int[blocks.size()];
        int said = 0;
        for (Apartness.Edge<A> edge : relation.edges()) {
            if (edge.isOfOneBlock()) {
                continue;
            }
            int one = numbered.get(edge.one());
            int other = numbered.get(edge.other());
            ends[said++] = one;
            ends[said++] = other;
            many[one]++;
            many[other]++;
        }
        int[][] apart = new int[blocks.size()][];
        for (int at = 0; at < apart.length; at++) {
            apart[at] = new int[many[at]];
            many[at] = 0;
        }
        for (int at = 0; at < said; at += 2) {
            int one = ends[at];
            int other = ends[at + 1];
            apart[one][many[one]++] = other;
            apart[other][many[other]++] = one;
        }
        return new Narrowing<>(blocks, apart).from(domains);
    }

    /**
     * What narrowing comes to, and the removals that reached it where it reached a refusal.
     *
     * <p><b>A walk holds a round and the one before it, and nothing else.</b> What a narrowing
     * takes is worked out against the reading a round was handed, so two readings are what it needs
     * at once — and a chain of blocks each left two values is narrowed one block per round, so a
     * walk that kept what every round left would hold a reading of the whole relation for every
     * block of it. That the rounds are what a narrowing is said in is a fact about the operation
     * and not a reason to keep them.
     *
     * <p>So a report's removals are read by walking again. Nothing is written down while an answer
     * is being reached, and where the answer is that a block is left nothing — which is where a
     * reader has a report to write — the same walk is made again and asked to say what it takes as
     * it takes it. What that costs is one walk, on the answer that is about to be written up.
     */
    private Closure<A> from(Domains<A> domains) {
        Walked<A> said = walk(domains, null);
        if (said.emptied() == null) {
            return new Closure.Stable<>(said.reading());
        }
        List<Provenance.Removal<A>> taken = new ArrayList<>();
        walk(domains, taken);
        return new Closure.Contradicted<>(said.emptied(), Provenance.of(taken));
    }

    /**
     * Round after round, until nothing moves or a round leaves a block nothing.
     *
     * <p>A round that takes nothing writes nothing. What the blocks are left is read from one round
     * and written into the next, and a round asks first whether it has anything to write: a
     * relation whose blocks all leave each other room is most of what a compilation reads, and for
     * one of those this walks the pairs and hands back the reading it was given.
     *
     * @param taken where each removal is written down as it is made, or null for a walk that is
     *              only being asked what the relation comes to
     */
    private Walked<A> walk(Domains<A> domains, List<Provenance.Removal<A>> taken) {
        Admits[] left = new Admits[blocks.size()];
        for (int at = 0; at < left.length; at++) {
            left[at] = domains.of(blocks.get(at));
        }
        boolean anythingWent = false;
        for (int round = 1; ; round++) {
            Admits[] next = null;
            Set<Sameness.Block<A>> emptied = null;
            for (int at = 0; at < left.length; at++) {
                if (!(left[at] instanceof Admits.These it) || !anythingGoesFrom(left, at)) {
                    continue;
                }
                Set<Value> keeping = new LinkedHashSet<>();
                for (Value value : it.values()) {
                    if (!blocked(left, at, value)) {
                        keeping.add(value);
                    } else if (taken != null) {
                        taken.add(new Provenance.Removal<>(
                                blocks.get(at), value, round, blocking(left, at, value)));
                    }
                }
                if (next == null) {
                    next = left.clone();
                }
                next[at] = new Admits.These(keeping);
                if (keeping.isEmpty()) {
                    if (emptied == null) {
                        emptied = new LinkedHashSet<>();
                    }
                    emptied.add(blocks.get(at));
                }
            }
            if (next == null) {
                return new Walked<>(anythingWent ? reading(left) : domains, null);
            }
            if (emptied != null) {
                return new Walked<>(null, emptied);
            }
            left = next;
            anythingWent = true;
        }
    }

    /**
     * What one walk came to: the reading nothing more can be taken from, or the blocks a round left
     * nothing.
     *
     * @param reading what the blocks are left, where no round emptied one
     * @param emptied the blocks the first round to empty anything emptied, or null where none did
     */
    private record Walked<A>(Domains<A> reading, Set<Sameness.Block<A>> emptied) {}

    /** What the blocks are left, as a reading for whoever is answered with one. */
    private Domains<A> reading(Admits[] left) {
        Map<Sameness.Block<A>, Admits> out = new LinkedHashMap<>();
        for (int at = 0; at < left.length; at++) {
            out.put(blocks.get(at), left[at]);
        }
        return new Domains<>(out);
    }

    /** Whether any value the block numbered {@code at} is left is one a neighbour leaves it no room
     *  for, which is asked before anything is built for the round to write. */
    private boolean anythingGoesFrom(Admits[] left, int at) {
        for (Value value : ((Admits.These) left[at]).values()) {
            if (blocked(left, at, value)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a neighbour of the block numbered {@code at} leaves {@code value} nowhere to go. */
    private boolean blocked(Admits[] left, int at, Value value) {
        for (int next : apart[at]) {
            if (!leavesRoomFor(left[next], value)) {
                return true;
            }
        }
        return false;
    }

    /** The neighbours of the block numbered {@code at} that leave {@code value} nowhere to go,
     *  which are the ones left no value other than it. */
    private Set<Sameness.Block<A>> blocking(Admits[] left, int at, Value value) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        for (int next : apart[at]) {
            if (!leavesRoomFor(left[next], value)) {
                out.add(blocks.get(next));
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
            case Admits.These it -> {
                int many = it.values().size();
                yield many > 1 || (many == 1 && !it.values().contains(value));
            }
            case Admits.MoreThanCounted _ -> true;
            case Admits.NotKnown _ -> true;
        };
    }
}
