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

    /**
     * How many sets of blocks all stated to differ this will look at before it stops.
     *
     * <p>A stated limit and not a figure anything derives. How many such sets a relation has is not
     * bounded by how many rules were written, and a reduction that walked all of them would make
     * what a declaration costs turn on a shape nothing else here charges for. Reaching it is this
     * saying nothing, which is what it says of every relation it has no argument for.
     */
    private static final int SETS_LOOKED_AT = 4096;

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
     * <p><b>Only the ones nothing can be added to.</b> A set of blocks all stated to differ is
     * refused by there being fewer values between them than there are blocks, and a set inside one
     * of these is refused only where this one is: a value apiece for the larger set is a value
     * apiece for every part of it. So the parts are covered by the whole and emitting them as well
     * is the same question asked again — once per subset, which is as many as there are subsets.
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
        grow(new LinkedHashSet<>(), new LinkedHashSet<>(apart.keySet()), new LinkedHashSet<>(),
                apart, found, most);
        if (found.size() > most) {
            return List.of();
        }
        found.sort(Comparator.comparingInt((Set<Sameness.Block<A>> each) -> each.size()).reversed());
        return found;
    }

    /**
     * Every set {@code sofar} grows into that nothing can be added to, each reached once.
     *
     * <p>{@code may} is what can still be added and {@code taken} is what was tried and set aside.
     * A set is one nothing can be added to where both are empty; where {@code taken} holds
     * something, every set this branch could reach was reached already through that block, and
     * walking on would find the same sets by another road. Without it a set of {@code k} blocks is
     * found once for every order its blocks can be taken in, and each of those is the same question
     * asked again.
     *
     * @param sofar the blocks taken so far, all stated to differ from each other
     * @param may the blocks every one of those is stated to differ from
     * @param taken those of them already set aside, which some earlier branch has covered
     */
    private void grow(Set<Sameness.Block<A>> sofar, Set<Sameness.Block<A>> may,
                      Set<Sameness.Block<A>> taken,
                      Map<Sameness.Block<A>, Set<Sameness.Block<A>>> apart,
                      List<Set<Sameness.Block<A>>> found, int most) {
        if (found.size() > most) {
            return;
        }
        if (may.isEmpty()) {
            if (taken.isEmpty() && sofar.size() > 1) {
                found.add(Collections.unmodifiableSet(new LinkedHashSet<>(sofar)));
            }
            return;
        }
        Set<Sameness.Block<A>> left = new LinkedHashSet<>(may);
        Set<Sameness.Block<A>> aside = new LinkedHashSet<>(taken);
        for (Sameness.Block<A> next : may) {
            Set<Sameness.Block<A>> apartFromNext = apart.getOrDefault(next, Set.of());
            Set<Sameness.Block<A>> grown = new LinkedHashSet<>(sofar);
            grown.add(next);
            Set<Sameness.Block<A>> still = new LinkedHashSet<>(left);
            still.retainAll(apartFromNext);
            Set<Sameness.Block<A>> covered = new LinkedHashSet<>(aside);
            covered.retainAll(apartFromNext);
            grow(grown, still, covered, apart, found, most);
            if (found.size() > most) {
                return;
            }
            left.remove(next);
            aside.add(next);
        }
    }

    /**
     * What these denials come to, against what each block they name is left.
     *
     * <p>Three answers, and the two that are not "nothing stands here" are two different things. An
     * assignment found is a value for every block that no denial refuses, which is what makes
     * {@link Reduction.Standing} a claim rather than a failure to refuse; anything else is
     * {@link Reduction.NotKnown}, which says that this reduction did not settle it and never that
     * something stands.
     *
     * <p><b>What it can refuse, in the order it tries.</b> A block stated to differ from itself is
     * read off the rule. A block whose neighbours each hold one value loses those values, and where
     * that leaves it none, nothing stands — run to a fixpoint, because a block cut down to one
     * value cuts down its own neighbours. And a set of blocks each stated to differ from every
     * other needs a value apiece, so where there are fewer values between them than there are
     * blocks, nothing stands.
     *
     * <p><b>What it cannot.</b> Which values a general relation leaves is a colouring, and this is
     * not one: {@code a /= b && b /= c && c /= d && d /= e && e /= a} over two values is refused by
     * no pair and by no set of blocks that are all apart, and this says nothing about it. That is a
     * widening like every other here — the relation is carried whole, and what a later reduction
     * shows is shown of what is already held.
     *
     * @param admitting what each block is left, which is a question about a block and a range and
     *                  belongs to whoever holds both
     */
    public Reduction<A> reduce(WhatABlockAdmits<A> admitting) {
        if (isEmpty()) {
            return new Reduction.Standing<>();
        }
        // How many values a block has to hold before it never runs out, which is how many blocks
        // there are: its neighbours take fewer values than that between them, so one is left.
        int atMost = blocks().size();
        for (Edge<A> edge : edges) {
            if (edge.isOfOneBlock()) {
                return new Reduction.Nothing<>(
                        new RelationalWitness.ABlockApartFromItself<>(edge.one()));
            }
        }
        Map<Sameness.Block<A>, Admits> left = new LinkedHashMap<>();
        for (Sameness.Block<A> block : blocks()) {
            left.put(block, admitting.of(block, atMost));
        }
        RelationalWitness<A> why = takingWhatOneValueBlocksHold(left);
        if (why == null) {
            why = counting(left);
        }
        if (why != null) {
            return new Reduction.Nothing<>(why);
        }
        return assignable(left, atMost) ? new Reduction.Standing<>() : new Reduction.NotKnown<>();
    }

    /**
     * Every block's values less the ones its one-valued neighbours take, to a fixpoint, and why
     * where that leaves one of them nothing.
     *
     * <p>Until nothing moves, because taking values away makes more one-valued blocks: a block left
     * two values one of which a neighbour holds is left one, and what it then holds is taken from
     * its own neighbours.
     *
     * <p><b>No model here needs the second round.</b> A sweep takes the blocks in the order the
     * denials were stated and writes what it finds as it goes, so a chain running that way is
     * followed to its end within one sweep — and a refusal by taking values away is a chain from a
     * block left one value, which is the only shape this argument refuses. What a second round can
     * still do is tighten a set the count below then reads. It is here for that and because a
     * single sweep would make the answer turn on the order the denials happen to be stated in;
     * measured, removing it leaves the whole suite green, so nothing yet holds it to what it is
     * for.
     */
    private RelationalWitness<A> takingWhatOneValueBlocksHold(Map<Sameness.Block<A>, Admits> left) {
        // The blocks and not the entries, because taking a value away writes back into the map this
        // is walking. Which blocks there are does not change while it runs — a reduction takes
        // values away and never adds a block — so the list is made once.
        List<Sameness.Block<A>> named = new ArrayList<>(left.keySet());
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Sameness.Block<A> block : named) {
                Value only = left.get(block) instanceof Admits.These it ? it.only() : null;
                if (only == null) {
                    continue;
                }
                for (Sameness.Block<A> next : apartFrom(block)) {
                    Admits was = left.get(next);
                    Admits now = was.without(only);
                    if (now.equals(was)) {
                        continue;
                    }
                    left.put(next, now);
                    moved = true;
                    if (now.isNone()) {
                        return new RelationalWitness.NoValueLeftBetweenThem<>(next,
                                oneValued(apartFrom(next), left));
                    }
                }
            }
        }
        return null;
    }

    /** Which of {@code these} hold one value, which are the blocks that took the values away. */
    private Set<Sameness.Block<A>> oneValued(Set<Sameness.Block<A>> these,
                                             Map<Sameness.Block<A>, Admits> left) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        these.forEach(block -> {
            if (left.get(block) instanceof Admits.These it && it.only() != null) {
                out.add(block);
            }
        });
        return out;
    }

    /**
     * Why a set of blocks all stated to differ has fewer values between them than there are of
     * them, or null where none has.
     *
     * <p>Asked of the blocks whose values are written down and of no others. A block holding more
     * values than a caller counted never runs out, and a block this cannot say the values of is one
     * nothing is known about — dropped from the set either way, which leaves a smaller set, and a
     * shortage shown of fewer blocks is a shortage.
     */
    private RelationalWitness<A> counting(Map<Sameness.Block<A>, Admits> left) {
        for (Set<Sameness.Block<A>> apart : everyPairwiseApartSet(SETS_LOOKED_AT)) {
            Map<Sameness.Block<A>, Set<Value>> counted = new LinkedHashMap<>();
            apart.forEach(block -> {
                if (left.get(block) instanceof Admits.These it) {
                    counted.put(block, it.values());
                }
            });
            if (counted.size() < 2) {
                continue;
            }
            RelationalWitness<A> why = shortage(counted);
            if (why != null) {
                return why;
            }
        }
        return null;
    }

    /**
     * Which of {@code counted} cannot all be given a value no other takes, or null where they can.
     *
     * <p>A value apiece and no two the same, which is a matching between the blocks and the values.
     * Where one is found, the blocks are satisfiable together; where the search for one runs out at
     * a block, what it reached is a set of blocks and every value any of them holds — one fewer
     * value than there are blocks, which is what the shortage is.
     */
    private RelationalWitness<A> shortage(Map<Sameness.Block<A>, Set<Value>> counted) {
        Map<Value, Sameness.Block<A>> taken = new LinkedHashMap<>();
        for (Sameness.Block<A> block : counted.keySet()) {
            Set<Value> reached = new LinkedHashSet<>();
            if (given(block, counted, taken, reached)) {
                continue;
            }
            Set<Sameness.Block<A>> blocks = new LinkedHashSet<>();
            blocks.add(block);
            reached.forEach(value -> blocks.add(taken.get(value)));
            return new RelationalWitness.TooFewValuesBetweenThem<>(blocks, reached);
        }
        return null;
    }

    /** Whether {@code block} can be given a value, moving the blocks already holding one along. */
    private boolean given(Sameness.Block<A> block, Map<Sameness.Block<A>, Set<Value>> counted,
                          Map<Value, Sameness.Block<A>> taken, Set<Value> reached) {
        for (Value value : counted.get(block)) {
            if (!reached.add(value)) {
                continue;
            }
            Sameness.Block<A> holder = taken.get(value);
            if (holder == null || given(holder, counted, taken, reached)) {
                taken.put(value, block);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every block can be given a value no block it is stated to differ from takes.
     *
     * <p>Taken in the order the blocks were named, which is enough to show an assignment and not
     * enough to show there is none: a run that fails here is one this says nothing about. A block
     * holding more values than the whole relation has blocks is given one of them without naming
     * it — its neighbours take fewer values than it holds, so one is free.
     */
    private boolean assignable(Map<Sameness.Block<A>, Admits> left, int atMost) {
        if (atMost < left.size()) {
            return false;
        }
        Map<Sameness.Block<A>, Object> given = new LinkedHashMap<>();
        for (Map.Entry<Sameness.Block<A>, Admits> each : left.entrySet()) {
            Set<Object> away = new LinkedHashSet<>();
            apartFrom(each.getKey()).forEach(next -> {
                Object held = given.get(next);
                if (held != null) {
                    away.add(held);
                }
            });
            switch (each.getValue()) {
                case Admits.These it -> {
                    Value free = it.values().stream().filter(one -> !away.contains(one))
                            .findFirst().orElse(null);
                    if (free == null) {
                        return false;
                    }
                    given.put(each.getKey(), free);
                }
                // More values than there are blocks, so more than its neighbours can have taken.
                case Admits.MoreThanCounted _ -> given.put(each.getKey(), new Object());
                case Admits.NotKnown _ -> {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * What one block of an alternative is left, asked by a relation reading it.
     *
     * <p>The bound is handed over and not the asked reader's own. How many values a block has to
     * hold before a relation stops caring is how many blocks the relation has, which is a fact
     * about the relation — worked out on the other side, it would be the same rule written where
     * nothing keeps the two in step.
     *
     * @param <A> what a position is called
     */
    @FunctionalInterface
    public interface WhatABlockAdmits<A> {

        /**
         * Which values {@code block} is left.
         *
         * @param atMost how many values are worth counting, which is how many blocks the relation
         *               asking has: a block holding more than that never runs out of values its
         *               neighbours have not taken
         */
        Admits of(Sameness.Block<A> block, int atMost);
    }

    /** What a relation was found to come to, against what its blocks are left. */
    public sealed interface Reduction<A> {

        /** Every block can be given a value no block it is stated to differ from takes. */
        record Standing<A>() implements Reduction<A> {}

        /** Neither shown, which is what this reduction says of every relation it has no argument
         *  for. */
        record NotKnown<A>() implements Reduction<A> {}

        /** Nothing satisfies the denials, and why. */
        record Nothing<A>(RelationalWitness<A> why) implements Reduction<A> {}

        /** What this comes to where a reader wants the three answers a reading gives about one
         *  block. */
        default Emptiness emptiness() {
            return switch (this) {
                case Standing<A> _ -> Emptiness.NONEMPTY;
                case NotKnown<A> _ -> Emptiness.UNDECIDED;
                case Nothing<A> _ -> Emptiness.EMPTY;
            };
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

        /** Whether both ends are one block, which is a value stated to differ from itself. */
        public boolean isOfOneBlock() {
            return one.equals(other);
        }

        /** The same pair between the blocks {@code naming} calls these. */
        public <B> Edge<B> renamed(Function<A, B> naming) {
            return new Edge<>(one.renamed(naming), other.renamed(naming));
        }

        /**
         * The same pair whichever end was written first.
         *
         * <p>Said here and not by putting the ends in an order when one is made. An order over the
         * blocks would have to come from something, and what there is to order them by is how they
         * are spelled — so two blocks that render alike would be one end, and this rule would be
         * about a rendering rather than about the blocks. What makes the pair unordered is that it
         * is a pair, which is what this says.
         */
        @Override
        public boolean equals(Object said) {
            return said instanceof Edge<?> it
                    && ((one.equals(it.one) && other.equals(it.other))
                            || (one.equals(it.other) && other.equals(it.one)));
        }

        @Override
        public int hashCode() {
            return one.hashCode() + other.hashCode();
        }

        /** The two ends, written in one order whichever way round they were stated. Which end is
         *  written first is a fact about the reading and not about the rule, so it is settled here
         *  and nowhere the rule is compared. */
        @Override
        public String toString() {
            String mine = String.valueOf(one);
            String theirs = String.valueOf(other);
            return mine.compareTo(theirs) <= 0 ? mine + " /= " + theirs : theirs + " /= " + mine;
        }
    }
}
