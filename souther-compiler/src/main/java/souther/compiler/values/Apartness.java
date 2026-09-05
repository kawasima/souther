package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Which blocks of one alternative are stated to hold different values.
 *
 * <p>Beside the product and not a side of it. An equality between two positions says the two are
 * one subject, which is a congruence and can be what a product is indexed by ({@link Sameness}); a
 * denial says neither position is the other's, which removes the diagonal from a product of two
 * sides and makes no side. So it is held here, as a relation over the blocks the alternative is a
 * product over, and what it comes to is worked out where the values each of those blocks is left
 * are in hand.
 *
 * <p><b>Blocks and not positions.</b> An alternative holding {@code p} and {@code q} as one value
 * has one answer between them, so a denial reaching either of them is a denial about that one
 * answer. Held between positions, {@code p == q && q /= r} would be a rule about {@code q} that
 * nothing said of {@code p}, and the two would disagree about a value they are one of.
 *
 * <p>Exact. Nothing here is widened or given up on: what an alternative was told is what it holds,
 * and how much of it a reader can decide is that reader's own limit. So a graph this cannot be
 * reduced by is still a graph it carries, and a reduction learned later reads what is already here.
 *
 * @param <A> what a position is called
 */
public final class Apartness<A> {

    /** In the order they were stated, so that what is written out of a reading comes out the same
     *  on two compiles of one model. */
    private final Set<Edge<A>> edges;

    private Apartness(Set<Edge<A>> edges) {
        this.edges = Collections.unmodifiableSet(new LinkedHashSet<>(edges));
    }

    /** No two blocks stated to differ, which is what an alternative that read no denial holds. */
    public static <A> Apartness<A> nothing() {
        return new Apartness<>(Set.of());
    }

    /** The two positions stated to hold different values, each on the block it is its own of. */
    public static <A> Apartness<A> of(A one, A other) {
        return new Apartness<>(Set.of(new Edge<>(Sameness.Block.of(one), Sameness.Block.of(other))));
    }

    /** Whether nothing is stated to differ from anything. */
    public boolean isEmpty() {
        return edges.isEmpty();
    }

    /** Every pair stated to differ. */
    public Set<Edge<A>> edges() {
        return edges;
    }

    /** Every block some pair names, which is what an alternative is a product over beyond what its
     *  own sides say. */
    public Set<Sameness.Block<A>> blocks() {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        edges.forEach(edge -> {
            out.add(edge.one());
            out.add(edge.other());
        });
        return Collections.unmodifiableSet(out);
    }

    /** The blocks {@code block} is stated to differ from. */
    public Set<Sameness.Block<A>> apartFrom(Sameness.Block<A> block) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        edges.forEach(edge -> {
            if (edge.one().equals(block)) {
                out.add(edge.other());
            } else if (edge.other().equals(block)) {
                out.add(edge.one());
            }
        });
        return Collections.unmodifiableSet(out);
    }

    /** Whether some pair states a block differs from itself, which nothing satisfies. */
    public boolean holdsABlockApartFromItself() {
        return edges.stream().anyMatch(Edge::isOfOneBlock);
    }

    /**
     * Both alternatives' denials, filed under the blocks {@code heldAsOne} holds.
     *
     * <p>The union pushed forward and not the union. A conjunction leaves a coarser relation — an
     * equality read beside these puts two blocks together — so a pair stated of the blocks either
     * side was a product over is a pair of whatever those blocks are part of here. Left where they
     * were, a denial would name a block this alternative does not answer in, which is what
     * {@link Sameness#filing} refuses.
     *
     * <p>A pair both of whose ends land on one block is kept and not dropped. What it says is that
     * a value differs from itself, which nothing satisfies — read as a pair to discard, the
     * alternative would go on standing and the rules that emptied it would be gone.
     */
    public Apartness<A> filedIn(Sameness<A> heldAsOne) {
        Set<Edge<A>> out = new LinkedHashSet<>();
        for (Edge<A> edge : edges) {
            out.add(new Edge<>(under(edge.one(), heldAsOne), under(edge.other(), heldAsOne)));
        }
        return new Apartness<>(out);
    }

    /** Both of them, before anything says what blocks the two together are a product over. */
    public Apartness<A> and(Apartness<A> other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        Set<Edge<A>> out = new LinkedHashSet<>(edges);
        out.addAll(other.edges);
        return new Apartness<>(out);
    }

    /**
     * What both alternatives state, over the blocks a choice between them leaves.
     *
     * <p>Read at {@code finer}, which is what the two agree a block is ({@link Sameness#common}).
     * A pair one alternative states of a block the other holds apart is still a pair of everything
     * that block is made of there — {@code p == q} beside {@code p /= r} says {@code q /= r} as
     * well — so each pair is pulled back onto the finer blocks its ends are made of before the two
     * are compared. Compared where they were stated, one alternative's coarser pair would match
     * nothing on the other side and a denial both of them state would be lost.
     *
     * <p>What is left is what both hold, because a choice states what neither branch denies only
     * where both branches deny it.
     */
    public Apartness<A> commonWith(Apartness<A> other, Sameness<A> finer) {
        if (isEmpty() || other.isEmpty()) {
            return nothing();
        }
        Set<Edge<A>> mine = pulledBackTo(finer);
        Set<Edge<A>> theirs = other.pulledBackTo(finer);
        Set<Edge<A>> out = new LinkedHashSet<>(mine);
        out.retainAll(theirs);
        return new Apartness<>(out);
    }

    /**
     * The same relation, said of the finer blocks {@code finer} cuts its ends into.
     *
     * <p>For a reader taking a relation stated where two positions were one value into a reading
     * that does not hold them as one. What a pair said of a block says of everything that block is
     * made of, so nothing is lost and nothing is invented — and a pair left where it was would name
     * a block the reading below does not answer in.
     */
    public Apartness<A> readAt(Sameness<A> finer) {
        return isEmpty() ? this : new Apartness<>(pulledBackTo(finer));
    }

    /** Every pair this states, said of the finer blocks each of its ends is made of. */
    private Set<Edge<A>> pulledBackTo(Sameness<A> finer) {
        Set<Edge<A>> out = new LinkedHashSet<>();
        for (Edge<A> edge : edges) {
            for (Sameness.Block<A> one : partsOf(edge.one(), finer)) {
                for (Sameness.Block<A> other : partsOf(edge.other(), finer)) {
                    out.add(new Edge<>(one, other));
                }
            }
        }
        return out;
    }

    /** The blocks {@code finer} cuts {@code block} into, which is {@code block} itself where it
     *  cuts it nowhere. */
    private static <A> Set<Sameness.Block<A>> partsOf(Sameness.Block<A> block, Sameness<A> finer) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        block.members().forEach(each -> out.add(finer.blockOf(each)));
        return out;
    }

    /** The same relation between the same blocks, under the names {@code naming} gives their
     *  positions. */
    public <B> Apartness<B> renamed(Function<A, B> naming) {
        Set<Edge<B>> out = new LinkedHashSet<>();
        edges.forEach(edge -> out.add(edge.renamed(naming)));
        return new Apartness<>(out);
    }

    /** The block {@code block} is part of here, asked of any of its positions: a block is inside
     *  one block of a coarser relation, so which member is asked does not decide the answer. */
    private static <A> Sameness.Block<A> under(Sameness.Block<A> block, Sameness<A> heldAsOne) {
        return heldAsOne.blockOf(block.members().iterator().next());
    }

    /**
     * Every set of blocks this states to differ from each other, largest first.
     *
     * <p>What a counting reduction is asked of. A set whose blocks are all stated apart from each
     * other needs a value each and no two the same, so how many values there are between them
     * decides whether anything stands — and a set some pair of which this says nothing about needs
     * no such thing, since two blocks nothing holds apart may hold one value.
     *
     * <p>Which is why the answer is not the parts the relation falls into. {@code p /= q && q /= r}
     * relates all three and states nothing of {@code p} and {@code r}, so two values are enough;
     * read as one set of three, it would be refused over a carrier of two and no rule says so.
     *
     * <p>Bounded by {@code most}, and by refusing rather than by answering with less: how many of
     * these there are is not something the rules bound, and a reading that stopped partway would
     * decide a declaration by how far it happened to get. A relation too large to walk is one this
     * says nothing about, which is what it says about every relation it has no reduction for.
     */
    public List<Set<Sameness.Block<A>>> everyPairwiseApartSet(int most) {
        Map<Sameness.Block<A>, Set<Sameness.Block<A>>> apart = new LinkedHashMap<>();
        for (Edge<A> edge : edges) {
            if (edge.isOfOneBlock()) {
                continue;
            }
            apart.computeIfAbsent(edge.one(), _ -> new LinkedHashSet<>()).add(edge.other());
            apart.computeIfAbsent(edge.other(), _ -> new LinkedHashSet<>()).add(edge.one());
        }
        List<Set<Sameness.Block<A>>> found = new ArrayList<>();
        List<Sameness.Block<A>> named = new ArrayList<>(apart.keySet());
        for (Sameness.Block<A> block : named) {
            grow(new LinkedHashSet<>(Set.of(block)), apart.get(block), apart, found, most);
            if (found.size() > most) {
                return List.of();
            }
        }
        found.sort(Comparator.comparingInt((Set<Sameness.Block<A>> each) -> each.size()).reversed());
        return found;
    }

    /** Every set {@code sofar} grows into by taking blocks every one of its members is apart
     *  from. */
    private void grow(Set<Sameness.Block<A>> sofar, Set<Sameness.Block<A>> may,
                      Map<Sameness.Block<A>, Set<Sameness.Block<A>>> apart,
                      List<Set<Sameness.Block<A>>> found, int most) {
        if (found.size() > most) {
            return;
        }
        if (sofar.size() > 1) {
            found.add(Collections.unmodifiableSet(new LinkedHashSet<>(sofar)));
        }
        for (Sameness.Block<A> next : may) {
            Set<Sameness.Block<A>> grown = new LinkedHashSet<>(sofar);
            if (!grown.add(next)) {
                continue;
            }
            Set<Sameness.Block<A>> still = new LinkedHashSet<>(may);
            still.retainAll(apart.getOrDefault(next, Set.of()));
            grow(grown, still, apart, found, most);
            if (found.size() > most) {
                return;
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Apartness<?> it && edges.equals(it.edges);
    }

    @Override
    public int hashCode() {
        return edges.hashCode();
    }

    @Override
    public String toString() {
        return edges.toString();
    }

    /**
     * Two blocks stated to hold different values.
     *
     * <p>Unordered: {@code p /= q} and {@code q /= p} are one rule, so the two ends are put in one
     * order whichever way round they were written and the pair is equal to itself written the
     * other way. Compared by its ends and by nothing else, so a rule stated twice is stated once.
     */
    public record Edge<A>(Sameness.Block<A> one, Sameness.Block<A> other) {

        public Edge {
            if (String.valueOf(one).compareTo(String.valueOf(other)) > 0) {
                Sameness.Block<A> swapped = one;
                one = other;
                other = swapped;
            }
        }

        /** Whether both ends are one block, which is a value stated to differ from itself. */
        public boolean isOfOneBlock() {
            return one.equals(other);
        }

        /** The same pair between the blocks {@code naming} calls these. */
        public <B> Edge<B> renamed(Function<A, B> naming) {
            return new Edge<>(one.renamed(naming), other.renamed(naming));
        }

        @Override
        public String toString() {
            return one + " /= " + other;
        }
    }
}
