package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * How many blocks a relation may have for the sets of them all stated to differ to be looked
     * for.
     *
     * <p>A bound on the shape and never on a walk part-way through one. How far a walk gets by some
     * number of steps depends on which block it started from, and two writings of one relation are
     * one relation ({@link #equals}) that would then be decided one way written this way round and
     * another written the other. Read off the shape before anything is walked, the answer is a fact
     * about the relation.
     *
     * <p>Which is what gives this figure something to be derived from. {@link #grow} takes as its
     * pivot the block stated to differ from most of what may still be added, and the exponential
     * part of a maximal-clique walk that pivots this way is {@code 3^(n/3)} in the number of blocks
     * — the same figure as the most sets of pairwise-apart blocks that {@code n} blocks can have.
     * What is left to measure is how much a step of this walk costs, and on the shape that reaches
     * that bound thirty blocks is a tenth of a second, with every three blocks after it three times
     * as much.
     *
     * <p><b>So the pivot is part of this bound and not a way of going faster.</b> Taking the first
     * block still in play instead leaves the walk reaching one set once for every order its blocks
     * come in, and this figure would be a bound on nothing.
     */
    private static final int MOST_BLOCKS_WALKED = 30;

    /**
     * How many pairs a relation may leave out for its blocks to be walked however many there are.
     *
     * <p>The other way a walk over the sets is cheap, and it is not the bound above with a
     * different figure. Each pair a relation leaves out can double how many sets of blocks all
     * stated to differ it has — a relation leaving none has one — so a relation of many blocks
     * leaving few pairs out has as few of them to find as a small relation.
     *
     * <p><b>Which is what the figure is derived from, and not what it is.</b> How many sets there
     * are is not how much walking finding them is: this walk is not one whose steps a count of its
     * answers bounds, and a bound read off the first alone would be a bound on nothing. What the
     * left-out pairs give is the axis the work runs along; where on that axis to stop is measured,
     * on the shape that reaches the doubling — a relation leaving this many pairs out is walked in
     * about a fiftieth of a second, and every four more of them is sixteen times as much.
     */
    private static final int MOST_PAIRS_LEFT_OUT = 12;

    /**
     * And how large a relation may be for its shape to be looked at at all.
     *
     * <p>What the relation itself comes to as an adjacency and what each level of the walk costs,
     * which neither bound above reaches: a relation leaving few pairs out is walked in nearly one
     * path however many blocks it has, so it is admitted past the first and would otherwise be
     * admitted with no bound at all.
     *
     * <p>Measured: a relation of this many pairs all of which are stated is walked in about a
     * fiftieth of a second. A relation the block bound admits has at most a few hundred pairs, so
     * this is the one that reaches a relation admitted the other way.
     */
    private static final int MOST_EDGES_HELD = 5000;

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
     * A lack for each block the rules state differs from itself, and none where no pair's ends are
     * one block.
     *
     * <p>Every one of them. Two blocks each stated to differ from themselves are two lacks about
     * two blocks, and which of them a reader is handed would otherwise be settled by which pair was
     * written first.
     */
    public Lacks<A> apartFromThemselves() {
        List<Shown<A>> out = new ArrayList<>();
        edges.forEach(edge -> {
            if (edge.isOfOneBlock()) {
                out.add(Shown.of(new RelationalLack.ABlockApartFromItself<>(edge.one())));
            }
        });
        return Lacks.of(out);
    }

    /** How much walking this relation is, before any of it is walked. */
    Extent extent() {
        return new Extent(blocks().size(), edges.size());
    }

    /**
     * How large a relation is, as the numbers a reduction over it can be admitted by.
     *
     * <p>Read off the relation and not off a walk of it, which is what lets an admission be a fact
     * about the relation: a walk stopped part-way through has got as far as the order its pairs
     * were stated in took it, and one relation written two ways round would be decided one way and
     * not the other. So how much walking a shape is, is settled before there is a walk to stop.
     *
     * @param blocks how many blocks some pair names
     * @param edges how many pairs are stated
     */
    record Extent(int blocks, int edges) {

        /**
         * How many pairs of blocks are not stated to differ.
         *
         * <p>What decides the walk on a relation nearly all of whose pairs are stated, which the
         * count of blocks does not. {@link #grow} pivots on the block stated to differ from most of
         * what may still be added, so where a block is stated to differ from every other, each
         * level has one way in and the whole relation is one path however many blocks it has. Each
         * pair left out is a level with a way in beside that one, and can double how many sets
         * there are to find. How much walking that comes to is measured rather than read off the
         * doubling — how many answers a walk has is not how many steps it takes.
         *
         * <p>The property and not the shape that has none of them. A relation all of whose pairs
         * are stated is this at nothing, and one pair short of it is a relation the walk is as
         * cheap on — so an admission that named the first would be about an example rather than
         * about what makes it cheap, and would say nothing of the relation beside it.
         */
        long pairsLeftOut() {
            return (long) blocks * (blocks - 1) / 2 - edges;
        }

        /**
         * Whether every set of blocks all stated to differ can be found.
         *
         * <p>Two ways in, because two different things make this walk cheap and neither covers the
         * other. A relation of few blocks is cheap because there are few sets to find at all; a
         * relation of few pairs left out is cheap however many blocks it has, because the pivot
         * leaves almost no way in at each level. A relation of many blocks nearly all of whose
         * pairs are stated is the second and not the first.
         *
         * <p>And a bound on the relation itself beside them, which neither reaches: what it comes
         * to as an adjacency and what one level of the walk costs are set by how many pairs there
         * are, and a relation of few pairs left out has as many pairs as blocks allow.
         */
        boolean admitsCounting() {
            return isSmallEnoughToRead()
                    && (blocks <= MOST_BLOCKS_WALKED || pairsLeftOut() <= MOST_PAIRS_LEFT_OUT);
        }

        /**
         * Whether the relation itself is small enough for anything here to read its shape.
         *
         * <p>Asked by both of the things that read it and not by one of them. Every pair is
         * something a walk over the sets holds in an adjacency, and every pair is something the
         * making of a search question reads however few blocks that question is over — so a
         * relation past this is one neither may be handed, and a reader that asked only its own
         * figures would be reading a relation of any size at all.
         */
        boolean isSmallEnoughToRead() {
            return edges <= MOST_EDGES_HELD;
        }
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

    /**
     * What every one of these relations states, at the blocks {@code finer} holds.
     *
     * <p>What a choice between several alternatives states, read once. A denial one of them states
     * is not the choice's, so what survives is what they all state — and it survives at the blocks
     * the choice answers in, which are the finer ones the alternatives agree on.
     *
     * <p>Here rather than at each reader. A reading whose values are still descriptions and one
     * whose values are sets both merge their alternatives into one product, and both have to carry
     * this across it; written at each, the one that is added later carries nothing and the choice
     * quietly forgets a rule both branches state.
     *
     * <p>Nothing where there are none of them. A reading holding no alternative states no denial,
     * which is what a reading holding nothing does.
     */
    public static <A> Apartness<A> commonTo(Collection<Apartness<A>> these, Sameness<A> finer) {
        Apartness<A> out = null;
        for (Apartness<A> each : these) {
            out = out == null ? each.readAt(finer) : out.commonWith(each, finer);
        }
        return out == null ? nothing() : out;
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
     * <p>Walked to the end or not walked at all. Whether the shape is one worth walking is read off
     * the relation before there is a walk ({@link Extent#admitsCounting}).
     *
     * <p>Which is why nothing reaches the walk itself. What it costs is bounded by the shape and by
     * nothing it does itself, so an entry that left the asking to whoever called it would be a
     * bound held by whatever the caller remembered — which is what this was while the asking was
     * the caller's, written in the walk's own words for the caller to honour.
     *
     * <p>Nothing where the shape is past it, and never fewer of them. A walk that answered with
     * the sets it happened to reach would decide a declaration by how far it got, and the same
     * relation written the other way round would be answered differently.
     *
     * <p>Which of these a walk reaches first is a fact about the walk, so nothing may read the
     * order they come in. They are handed back in one all the same: each is reached once, so a set
     * would have nothing to remove and would hash every one of them against every other — and a
     * set of blocks hashes as the sum of what it holds, which is a figure the subsets of one
     * relation share. Measured on the shape the bound above admits, putting them in a set was what
     * a reduction cost rather than a part of it.
     *
     * <p>So what keeps the order out of an answer is the reading that takes these, which collects
     * what it finds rather than stopping at the first of them.
     */
    Optional<List<Set<Sameness.Block<A>>>> everySetWorthWalkingFor() {
        return extent().admitsCounting() ? Optional.of(everyPairwiseApartSet()) : Optional.empty();
    }

    private List<Set<Sameness.Block<A>>> everyPairwiseApartSet() {
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
                apart, found);
        return Collections.unmodifiableList(found);
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
                      List<Set<Sameness.Block<A>>> found) {
        if (may.isEmpty()) {
            if (taken.isEmpty() && sofar.size() > 1) {
                found.add(Collections.unmodifiableSet(new LinkedHashSet<>(sofar)));
            }
            return;
        }
        // The blocks one of them is not stated to differ from, and no others. Every set nothing can
        // be added to holds that one or something it does not differ from, so the rest are reached
        // through those — and a relation whose blocks are all stated to differ has one such set and
        // one way in, where taking each of them in turn is a way in for every block there is.
        Set<Sameness.Block<A>> around = apart.getOrDefault(pivot(may, taken, apart), Set.of());
        List<Sameness.Block<A>> ways = new ArrayList<>();
        may.forEach(each -> {
            if (!around.contains(each)) {
                ways.add(each);
            }
        });
        Set<Sameness.Block<A>> left = new LinkedHashSet<>(may);
        Set<Sameness.Block<A>> aside = new LinkedHashSet<>(taken);
        for (Sameness.Block<A> next : ways) {
            Set<Sameness.Block<A>> apartFromNext = apart.getOrDefault(next, Set.of());
            Set<Sameness.Block<A>> grown = new LinkedHashSet<>(sofar);
            grown.add(next);
            Set<Sameness.Block<A>> still = new LinkedHashSet<>(left);
            still.retainAll(apartFromNext);
            Set<Sameness.Block<A>> covered = new LinkedHashSet<>(aside);
            covered.retainAll(apartFromNext);
            grow(grown, still, covered, apart, found);
            left.remove(next);
            aside.add(next);
        }
    }

    /** Whichever of the blocks still in play is stated to differ from most of what may be added,
     *  which is what leaves the fewest ways in. */
    private Sameness.Block<A> pivot(Set<Sameness.Block<A>> may, Set<Sameness.Block<A>> taken,
                                    Map<Sameness.Block<A>, Set<Sameness.Block<A>>> apart) {
        Sameness.Block<A> best = null;
        int most = -1;
        for (Set<Sameness.Block<A>> these : List.of(may, taken)) {
            for (Sameness.Block<A> each : these) {
                Set<Sameness.Block<A>> around = apart.getOrDefault(each, Set.of());
                int reach = 0;
                for (Sameness.Block<A> one : may) {
                    if (around.contains(one)) {
                        reach++;
                    }
                }
                if (reach > most) {
                    most = reach;
                    best = each;
                }
            }
        }
        return best;
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
     * <p><b>Five arguments, in the order it tries them.</b> A block stated to differ from itself is
     * read off the rule. A relation whose blocks are each left more values than they are stated to
     * differ from blocks stands, and is read off the pairs. A block loses every value a neighbour
     * leaves it no room for, and where that leaves it none, nothing stands — narrowed round after
     * round, because a block that loses a value leaves less room for its own neighbours
     * ({@link Narrowing}). A set of blocks each stated to differ from every other needs a value
     * apiece, so where there are fewer values between them than there are blocks, nothing stands.
     * And what none of those reaches is looked for: whether some way of giving the blocks values
     * tells every stated pair apart.
     *
     * <p><b>Every lack the argument that answered shows, and not one of them.</b> Each of these
     * arguments can hold of several blocks at once, and the relation says nothing about which of
     * them to name — a relation two of whose blocks can be swapped for each other is that relation
     * swapped, so a rule choosing one would answer one way for it and another for its image. What
     * there would be to choose by is the order the denials were written or how the positions are
     * spelled, and neither is something the relation holds. So an answer is the whole of what the
     * argument that reached it shows.
     *
     * <p><b>Why the first three stay, once the fourth decides.</b> Not one reason but three.
     *
     * <p>Refusing by reading the rule and by narrowing is what says which blocks the lack is about
     * — one pair, or one block and the ones its values went to — where looking for an assignment
     * can only name the blocks it looked over. Reading the rule is also what the fourth rests on: a
     * block holding
     * more values than the relation has blocks is left out of the search because it can be given
     * one after every other block has, and a block stated to differ from itself is a block no such
     * argument holds for.
     *
     * <p>Counting decides where the fourth is not admitted to look, which is most of what a large
     * relation is. Blocks all stated to differ are refused however many of them there are, and a
     * search over that many blocks is past what it looks through several times over.
     *
     * <p>And narrowing makes the search smaller as well as refusing: a relation whose blocks lose
     * the values their neighbours leave no room for may be inside what the fourth looks through
     * where it was not before. So the three are what decides outside the fourth's reach and what
     * says the lack better inside it, and neither of those is being kept for the sake of the other.
     *
     * <p><b>What it still cannot.</b> A relation whose shape is past what either search is admitted
     * by and whose blocks do not each outnumber their neighbours, and a relation naming a block
     * whose values nothing wrote down. Both are {@link Reduction.NotKnown}, which says that this
     * did not settle it and never that something stands.
     *
     * @param admitting what each block is left, which is a question about a block and a range and
     *                  belongs to whoever holds both
     */
    public Reduction<A> reduce(WhatABlockAdmits<A> admitting) {
        if (isEmpty()) {
            return new Reduction.Standing<>();
        }
        Lacks<A> stated = apartFromThemselves();
        if (!stated.isEmpty()) {
            return new Reduction.Nothing<>(stated);
        }
        // How many values a block has to hold before it never runs out, which is how many blocks
        // there are: its neighbours take fewer values than that between them, so one is left.
        int atMost = blocks().size();
        Domains<A> left = Domains.of(blocks(), admitting, atMost);
        if (outnumberTheirNeighbours(left)) {
            return new Reduction.Standing<>();
        }
        return switch (Narrowing.of(this, left)) {
            case Closure.Contradicted<A> it -> emptied(it);
            case Closure.Stable<A> it -> whatIsLeftComesTo(it.domains());
        };
    }

    /**
     * Whether every block is left more values than it is stated to differ from blocks.
     *
     * <p>Such a relation stands, and nothing has to be narrowed, counted or looked through to say
     * so. Take the blocks in any order and give each one a value: what it is stated to differ from
     * has taken at most one value apiece and there are fewer of them than it holds, so one is
     * always free. What that argument needs of a block is a value nobody else took, and it needs it
     * of every block at once — which is why this is asked of all of them or not at all.
     *
     * <p><b>The argument the search already rests on, said of a block's neighbours rather than of
     * the relation's blocks.</b> A block holding more values than the relation has blocks is left
     * out of the search because it can be given one after every other block has; that is this, with
     * the loosest bound a block could be held to. Read against what each block is actually stated
     * to differ from, it decides the relation rather than one block of it.
     *
     * <p>Which is why it is asked before anything walks. What it costs is the pairs, read once; a
     * relation it admits is one the walk over the sets and the search would both reach the end of,
     * and a relation of a shape neither is admitted for is now answered where it was not.
     *
     * <p>Nothing where a block's values are not written down. Such a block may hold no value at
     * all, and an argument that gives it one last is one about a block whose values somebody
     * counted.
     */
    private boolean outnumberTheirNeighbours(Domains<A> left) {
        Map<Sameness.Block<A>, Integer> apart = new LinkedHashMap<>();
        left.blocks().forEach(block -> apart.put(block, 0));
        for (Edge<A> edge : edges) {
            apart.merge(edge.one(), 1, Integer::sum);
            apart.merge(edge.other(), 1, Integer::sum);
        }
        for (Sameness.Block<A> block : left.blocks()) {
            switch (left.of(block)) {
                case Admits.These it -> {
                    if (it.values().size() <= apart.get(block)) {
                        return false;
                    }
                }
                // More than the relation has blocks, which is more than any block has neighbours.
                case Admits.MoreThanCounted _ -> { }
                case Admits.NotKnown _ -> {
                    return false;
                }
            }
        }
        return true;
    }

    /** A lack at each block a narrowing left no value, beside the removals that left them so. */
    private Reduction<A> emptied(Closure.Contradicted<A> narrowed) {
        RelationalEvidence<A> reached = RelationalEvidence.of(narrowed.provenance());
        List<Shown<A>> lacks = new ArrayList<>();
        narrowed.leftNothing().forEach(block -> lacks.add(
                new Shown<>(new RelationalLack.NoValueLeftForIt<>(block), reached)));
        return new Reduction.Nothing<>(Lacks.of(lacks));
    }

    /** What the two arguments after a narrowing make of what it left. */
    private Reduction<A> whatIsLeftComesTo(Domains<A> left) {
        Lacks<A> counted = counting(left);
        if (!counted.isEmpty()) {
            return new Reduction.Nothing<>(counted);
        }
        return switch (projection(left)) {
            case Projection.TheWholeOfIt<A> it ->
                    lookedFor(it.mayHold(), new Reduction.Standing<>());
            case Projection.APartOfIt<A> it ->
                    lookedFor(it.mayHold(), new Reduction.NotKnown<>());
        };
    }

    /**
     * What looking for an assignment over {@code over} comes to, where finding one leaves
     * {@code found}.
     *
     * <p>Two answers from the search and three from here. Running out says nothing satisfies the
     * denials, which is true of the relation whichever part of it was searched; finding one says
     * what the caller passed in, which is what the two arms of a {@link Projection} differ about. A
     * shape the search is not admitted for is neither.
     */
    private Reduction<A> lookedFor(Map<Sameness.Block<A>, Set<Value>> mayHold, Reduction<A> found) {
        Optional<TellingApart<A>> asked =
                TellingApart.lookingThrough(extent(), mayHold, this::apartFrom);
        if (asked.isEmpty()) {
            return new Reduction.NotKnown<>();
        }
        TellingApart<A> over = asked.get();
        // The blocks the search was over, asked of the search. Read off what was handed to it
        // instead, this would be the same set worked out twice, and the day the two differ is the
        // day a lack names blocks nothing was looked for over.
        return over.isSatisfiable() ? found
                : new Reduction.Nothing<>(Lacks.of(
                        new RelationalLack.NoAssignmentTellsThemApart<>(over.blocks())));
    }

    /**
     * The blocks an assignment is looked for over, and whether finding one answers for the whole
     * relation.
     *
     * <p>Two blocks are left out, for reasons that are not each other's. A block holding more
     * values than the relation has blocks can be given one after every other block has — it has
     * more values than it has neighbours, so one of them is always free — which makes leaving it
     * out cost nothing in either direction. A block whose values nothing wrote down is left out
     * because there is nothing to search; and that is sound one way only, since such a block may
     * hold no value at all.
     *
     * <p>So the two are told apart by being two arms rather than by a condition somebody has to
     * remember to ask. Refusing carries from a part of the relation to the whole of it in both,
     * because an assignment to all the blocks is an assignment to some of them; standing carries
     * only from {@link Projection.TheWholeOfIt}.
     *
     * <p>The first of them is the argument {@link #reduce} refuses a block stated to differ from
     * itself before reaching: such a block has no free value however many it holds, and reading it
     * as one that can be given a value last is what leaving it out would be.
     */
    private Projection<A> projection(Domains<A> left) {
        Map<Sameness.Block<A>, Set<Value>> mayHold = new LinkedHashMap<>();
        boolean whole = true;
        for (Map.Entry<Sameness.Block<A>, Admits> each : left.byBlock().entrySet()) {
            switch (each.getValue()) {
                case Admits.These it -> mayHold.put(each.getKey(), it.values());
                case Admits.MoreThanCounted _ -> { }
                case Admits.NotKnown _ -> whole = false;
            }
        }
        return whole ? new Projection.TheWholeOfIt<>(mayHold) : new Projection.APartOfIt<>(mayHold);
    }

    /**
     * What of a relation an assignment is looked for over, and what finding one there shows.
     *
     * <p>Held as two arms and not as a set with a flag beside it, so that a reader is made to say
     * which of the two it has before it can read the blocks. Written as one, the condition that
     * tells them apart would be asked once for refusing and once for standing, and the two are not
     * the same condition.
     *
     * @param <A> what a position is called
     */
    private sealed interface Projection<A> {

        /** The whole relation: what is left out was going to be given a value whatever the rest
         *  held, so an assignment found here is an assignment to all of it. */
        record TheWholeOfIt<A>(Map<Sameness.Block<A>, Set<Value>> mayHold) implements Projection<A> {}

        /** A part of it: some block's values are not written down, so an assignment found here is
         *  one for the blocks it covers and says nothing about the block left out. */
        record APartOfIt<A>(Map<Sameness.Block<A>, Set<Value>> mayHold) implements Projection<A> {}
    }

    /**
     * Why no set of blocks all stated to differ can be given a value apiece, or none where they all
     * can.
     *
     * <p>Asked of the blocks whose values are written down and of no others. A block holding more
     * values than a caller counted never runs out, and a block this cannot say the values of is one
     * nothing is known about — dropped from the set either way, which leaves a smaller set, and a
     * shortage shown of fewer blocks is a shortage.
     *
     * <p>And asked only of a relation whose shape says the sets can all be found. A relation past
     * that is one this argument says nothing about, which is what it says of every relation whose
     * sets hold enough values.
     *
     * <p>Every lack the sets it was asked of showed, and not the first of them nor a weaker thing
     * they all have in common. Two sets short of values are two claims — each says that its own
     * blocks cannot be told apart, which is nearer than anything true of the two together — so what
     * a reader is handed is both, and which of them a walk reached first stays a fact about the
     * walk.
     *
     * <p>How many that can be is what admits the walk at all: a relation is asked of the sets
     * nothing can be added to, and how many of those there are is what
     * {@link #MOST_BLOCKS_WALKED} and {@link #MOST_PAIRS_LEFT_OUT} are derived from. So the lacks
     * are bounded wherever the walk is, and a relation past that is one this says nothing about.
     */
    private Lacks<A> counting(Domains<A> left) {
        Optional<List<Set<Sameness.Block<A>>>> walked = everySetWorthWalkingFor();
        if (walked.isEmpty()) {
            return Lacks.none();
        }
        List<Shown<A>> found = new ArrayList<>();
        // Each lack once. A set the walk found is cut down to the blocks whose values are written
        // down, and two of them can be cut down to one — a set of blocks all stated to differ
        // beside two blocks nothing wrote the values of is two sets that leave one. What the count
        // then shows of them is one lack, shown twice.
        //
        // Told by what is claimed and not by the blocks the count was taken of. A lack says where
        // it is filed ({@link RelationalLack#scattering}), where a set of blocks is a number its
        // subsets share — and the sets one relation is short of are subsets of the same few blocks.
        Set<RelationalLack<A>> already = new LinkedHashSet<>();
        for (Set<Sameness.Block<A>> apart : walked.get()) {
            Map<Sameness.Block<A>, Set<Value>> counted = new LinkedHashMap<>();
            apart.forEach(block -> {
                if (left.of(block) instanceof Admits.These it) {
                    counted.put(block, it.values());
                }
            });
            if (counted.size() < 2) {
                continue;
            }
            RelationalLack<A> why = shortage(counted);
            if (why != null && already.add(why)) {
                found.add(Shown.of(why));
            }
        }
        return Lacks.of(found);
    }

    /**
     * Why {@code counted} cannot all be given a value no other takes, or null where they can.
     *
     * <p><b>Two lacks and not one, because a set of blocks runs out of values two ways.</b> Where
     * every value any of them holds comes to fewer than there are blocks, what is wrong is how many
     * values there are, and that is a count anybody can take of the set as it stands. Where there
     * are values enough between them and no way of handing them out all the same, nothing was
     * counted — some part of the set is short and which part it is depends on how the values fall —
     * so what is shown is that no assignment tells the blocks apart, which is the other lack and is
     * the whole of what a matching that ran out shows.
     *
     * <p>Which is why the matching's own reach is not what is reported. Where a matching stops
     * depends on the order it took the blocks in, and a set read off that would be a lack about
     * whichever blocks it happened to walk through. Both lacks here are read off the set and its
     * values alone.
     */
    private RelationalLack<A> shortage(Map<Sameness.Block<A>, Set<Value>> counted) {
        Set<Value> available = new LinkedHashSet<>();
        counted.values().forEach(available::addAll);
        if (available.size() < counted.size()) {
            return new RelationalLack.TooFewValuesBetweenThem<>(counted.keySet(), available);
        }
        Map<Value, Sameness.Block<A>> taken = new LinkedHashMap<>();
        for (Sameness.Block<A> block : counted.keySet()) {
            if (!given(block, counted, taken, new LinkedHashSet<>())) {
                return new RelationalLack.NoAssignmentTellsThemApart<>(counted.keySet());
            }
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

        /**
         * Nothing satisfies the denials, and why.
         *
         * <p>Every lack the argument that answered shows. One of them is a lack about the blocks
         * it names and never the whole of what was shown, and choosing between several would be
         * choosing by something the relation does not hold.
         */
        record Nothing<A>(Lacks<A> lacks) implements Reduction<A> {

            public Nothing {
                if (lacks.isEmpty()) {
                    throw new IllegalArgumentException(
                            "nothing standing is shown by a lack, and none was given");
                }
            }
        }

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

    /** The pairs written in one order whichever order they were stated in — see
     *  {@link InOneOrder}. */
    @Override
    public String toString() {
        return InOneOrder.of(edges);
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
