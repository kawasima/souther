package souther.compiler.values;

import souther.compiler.hash.ValueHash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a reading holds while the values it describes have not been worked out.
 *
 * <p>The same two shapes a finished reading has: nothing satisfies the rules, or these alternatives
 * do. What is different is what a box may hold — a description of a set rather than the set — so a
 * box here may turn out to stand for nothing, where a settled one never does.
 *
 * <p>A choice this reading cannot settle is not one of these. Which branch of a choice can be taken
 * decides what the whole reading holds and what its alternatives owe, so an unsettled choice is a
 * state of the reading and is held there ({@code PlannedValues.Choice}) rather than as a third
 * thing a box could be inside.
 *
 * @param <A> what a position is called
 */
sealed interface PlannedHeld<A> {

    /** Nothing satisfies the rules, and that is settled. */
    record Nothing<A>() implements PlannedHeld<A> {}

    /**
     * The alternatives the rules leave, none of which is known to admit nothing.
     *
     * <p>What each position holds across them is not worked out here, unlike the settled reading's:
     * a join of two descriptions is a description, so there is nothing to pay for and nothing to
     * decide.
     */
    record Alternatives<A>(Set<Alternative<A>> boxes) implements PlannedHeld<A> {

        public Alternatives {
            if (boxes.isEmpty()) {
                throw new IllegalArgumentException("a reading holding no alternative is Nothing");
            }
            boxes = Collections.unmodifiableSet(new LinkedHashSet<>(boxes));
        }

        /**
         * Which positions every alternative holds as one value.
         *
         * <p>What each of them holds as one is its own, so what the choice can say of a position is
         * what every one of them says — read the other way round, a branch would lend its equality
         * to the branch beside it. {@link AdmissibleValues.Held.Alternatives#commonSameness} over
         * descriptions, and answered the same way.
         */
        Sameness<A> commonSameness() {
            Sameness<A> out = null;
            for (Alternative<A> box : boxes) {
                out = out == null ? box.sameness() : out.common(box.sameness());
            }
            return out == null ? Sameness.discrete() : out;
        }
    }

    /**
     * One alternative while its values are still descriptions: a product over its blocks, and
     * which of those blocks are stated to differ.
     *
     * <p>{@link AdmissibleValues.Alternative}'s two halves over descriptions rather than sets. The
     * relation is exact either side of {@link PlannedValues#resolve} — a denial is a denial whether
     * or not anybody has worked out what the blocks it names admit — so what changes across that
     * line is the product and not this.
     *
     * <p>The denials as they were stated, and not the relation between blocks they come to
     * ({@link StatedApartness}). Which blocks a denial names is settled by everything the
     * alternative holds as one value, and a conjunction is where that is still being found out.
     *
     * <p><b>And whether what it states contradicts, which it is asked as each rule arrives.</b>
     * Worked out where an alternative is made, since that is where what would have to be walked to
     * answer it is at its smallest: a conjunction holds the denials of both sides, and neither
     * side's answer changes unless the conjunction holds as one value something that side held
     * apart. Asked of the whole of what an alternative states instead, a reading would walk every
     * denial it holds for every denial it reads.
     */
    static final class Alternative<A> {

        private final Box<A> product;
        private final StatedApartness<A> stated;

        /** Whether some denial this states has both ends on one of this alternative's blocks. */
        private final boolean contradicts;

        private Alternative(Box<A> product, StatedApartness<A> stated, boolean contradicts) {
            this.product = product;
            this.stated = stated;
            this.contradicts = contradicts;
        }

        /** One alternative that states no denial. */
        static <A> Alternative<A> of(Box<A> product) {
            return new Alternative<>(product, StatedApartness.none(), false);
        }

        /** One alternative over the positions {@code stated} names and whatever {@code product}
         *  describes, which is what a reading of a denial holds. */
        static <A> Alternative<A> of(Box<A> product, StatedApartness<A> stated) {
            return new Alternative<>(product, stated, stated.contradicts(product.sameness()));
        }

        /** What each block is described as holding. */
        Box<A> product() {
            return product;
        }

        /** Which positions are stated to hold different values, as they were stated. */
        StatedApartness<A> stated() {
            return stated;
        }

        /** Whether what this states leaves nothing, which is a value stated to differ from
         *  itself. */
        boolean contradicts() {
            return contradicts;
        }

        /**
         * The relation this alternative's denials come to, between the blocks it holds as one
         * value.
         *
         * <p>Built where a relation is what is wanted — what a choice reads of two branches, and
         * what a refusal says about them. The questions a reading asks of its denials on the way
         * there are answered by {@link #contradicts} and by what was stated, so nothing that only
         * needs those pays for this.
         */
        Apartness<A> apart() {
            return stated.quotientBy(sameness());
        }

        /**
         * Where this alternative states a block to differ from itself, which is the whole of what
         * its denials show before anything has worked out what its positions admit.
         *
         * <p>Answered here because the answer to whether there is one is here: it was worked out
         * where this was made, and what it would cost to find out again is a walk of every denial
         * the alternative holds. {@link Apartness#apartFromThemselves} decides the same way from
         * what it was told when it was built.
         */
        Lacks<A> denialsApartFromThemselves() {
            return contradicts ? stated.apartFromThemselves(sameness()) : Lacks.none();
        }

        /** One alternative over positions that are each their own block, stating no denial. */
        static <A> Alternative<A> at(Map<A, AdmittedPlan> said) {
            return of(Box.at(said));
        }

        /** What each block is described as holding. */
        Map<Sameness.Block<A>, AdmittedPlan> at() {
            return product.at();
        }

        /**
         * Which positions this alternative holds as one value, which is what its product is over.
         *
         * <p>Its denials say nothing about it. A denial names two positions and says they differ,
         * which holds neither of them with anything — so a position a denial is all this says about
         * is a position of its own, which is what a relation answers for a position it never heard
         * of ({@link Sameness#blockOf}).
         */
        Sameness<A> sameness() {
            return product.sameness();
        }

        AdmittedPlan get(Sameness.Block<A> block) {
            return product.get(block);
        }

        /** What is described at {@code position}, which is what the block it is on describes. */
        AdmittedPlan get(A position) {
            return get(sameness().blockOf(position));
        }

        /** Every position this alternative says anything about, by describing it or by relating
         *  it. */
        Set<A> positions() {
            Set<A> out = new LinkedHashSet<>(product.positions());
            out.addAll(stated.positions());
            return out;
        }

        /**
         * Both alternatives holding at once, over what the two of them hold as one value —
         * see {@link AdmissibleValues.Alternative#narrowedWith}.
         *
         * <p>The denials of the two are put together and nothing else is done to them. What a
         * denial names is two positions, and the conjunction holds those positions wherever it
         * holds them — so there is no step here for a denial to be carried across, which is what
         * makes what a reading spends on its denials what it says rather than how it was bracketed.
         */
        Alternative<A> meet(Alternative<A> other) {
            Sameness<A> mine = sameness();
            Sameness<A> theirs = other.sameness();
            Sameness<A> heldAsOne = mine.meet(theirs);
            // A side whose blocks the conjunction leaves alone answers this the way it already
            // did: what its denials name is what they named. The one the conjunction coarsens is
            // walked, and it is walked against the relation the conjunction leaves rather than the
            // one it was read against.
            boolean both = (heldAsOne == mine ? contradicts : stated.contradicts(heldAsOne))
                    || (heldAsOne == theirs ? other.contradicts
                            : other.stated.contradicts(heldAsOne));
            return new Alternative<>(product.meet(other.product, heldAsOne),
                    stated.and(other.stated), both);
        }

        @Override
        public boolean equals(Object said) {
            return said instanceof Alternative<?> it
                    && product.equals(it.product) && stated.equals(it.stated);
        }

        /** What it describes and what it states to differ, which is the whole of what it is —
         *  {@link #contradicts} is worked out from those. */
        @Override
        public int hashCode() {
            return ValueHash.ofItsParts(Alternative.class, product.hashCode(), stated.hashCode());
        }

        @Override
        public String toString() {
            return stated.isEmpty() ? product.toString() : product + " with " + stated;
        }
    }

    /**
     * One product: what each block may hold, with every combination of them standing.
     *
     * <p>Over the positions this alternative holds as one value, for the reason
     * {@link AdmissibleValues.Box} gives: an equality between two positions is not a narrowing of
     * either, it is what the product is a product over.
     *
     * <p>A block of one position admitting every value is left out, as in the settled reading, and
     * a block of several is kept whatever it admits — what it says is said by its existing. What
     * is not refused here is a side admitting nothing: whether a description admits anything is the
     * question this whole arrangement exists to put off, so a box may hold one and be dropped when
     * the answer arrives.
     */
    record Box<A>(Map<Sameness.Block<A>, AdmittedPlan> at) {

        public Box {
            Map<Sameness.Block<A>, AdmittedPlan> said = new LinkedHashMap<>();
            at.forEach((block, plan) -> {
                if (!block.isOne() || !(plan instanceof AdmittedPlan.Everything)) {
                    said.put(block, plan);
                }
            });
            at = Collections.unmodifiableMap(said);
            // Read as the relation they are the classes of — see {@link AdmissibleValues.Box}.
            Sameness.of(at.keySet());
        }

        /** One alternative over positions that are each their own block. */
        static <A> Box<A> at(Map<A, AdmittedPlan> said) {
            Map<Sameness.Block<A>, AdmittedPlan> out = new LinkedHashMap<>();
            said.forEach((position, plan) -> out.put(Sameness.Block.of(position), plan));
            return new Box<>(out);
        }

        /** Which positions this alternative holds as one value, read off what it is a product
         *  over. */
        Sameness<A> sameness() {
            return Sameness.of(at.keySet());
        }

        AdmittedPlan get(Sameness.Block<A> block) {
            return at.getOrDefault(block, AdmittedPlan.ANY);
        }

        /** What is described at {@code position}, which is what the block it is on describes. */
        AdmittedPlan get(A position) {
            return get(sameness().blockOf(position));
        }

        /** Every position this alternative says anything about. */
        Set<A> positions() {
            Set<A> out = new LinkedHashSet<>();
            at.keySet().forEach(block -> out.addAll(block.members()));
            return out;
        }

        /**
         * Both alternatives holding at once, over what the two of them hold as one value.
         *
         * <p>{@link AdmissibleValues.Box#narrowedWith}'s rule over descriptions rather than sets:
         * the equalities are conjoined and closed, and what a block of the conjunction describes
         * is every description that reached the positions it holds. Nothing is built, so nothing is
         * charged and nothing can be refused.
         */
        Box<A> meet(Box<A> other, Sameness<A> heldAsOne) {
            Map<Sameness.Block<A>, List<AdmittedPlan>> parts = new LinkedHashMap<>();
            gathering(at, Refinement.of(sameness(), heldAsOne), parts);
            gathering(other.at, Refinement.of(other.sameness(), heldAsOne), parts);
            Map<Sameness.Block<A>, AdmittedPlan> out = new LinkedHashMap<>();
            parts.forEach((block, these) -> out.put(block,
                    these.size() == 1 ? these.getFirst() : AdmittedPlan.meeting(these)));
            return new Box<>(out);
        }

        private static <A> void gathering(Map<Sameness.Block<A>, AdmittedPlan> these,
                                          Refinement<A> into,
                                          Map<Sameness.Block<A>, List<AdmittedPlan>> parts) {
            these.forEach((block, plan) -> parts
                    .computeIfAbsent(into.coarseBlockOf(block), _ -> new ArrayList<>())
                    .add(plan));
        }
    }

    /** One alternative, which is what most readings hold. */
    static <A> PlannedHeld<A> one(Alternative<A> box) {
        return new Alternatives<>(Set.of(box));
    }

}
