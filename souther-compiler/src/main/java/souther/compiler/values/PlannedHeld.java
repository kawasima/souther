package souther.compiler.values;

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
     * @param product what each block is described as holding
     * @param apart which of those blocks are stated to hold different values
     */
    record Alternative<A>(Box<A> product, Apartness<A> apart) {

        /** One alternative that states no denial. */
        static <A> Alternative<A> of(Box<A> product) {
            return new Alternative<>(product, Apartness.nothing());
        }

        /** One alternative over positions that are each their own block, stating no denial. */
        static <A> Alternative<A> at(Map<A, AdmittedPlan> said) {
            return of(Box.at(said));
        }

        /** What each block is described as holding. */
        Map<Sameness.Block<A>, AdmittedPlan> at() {
            return product.at();
        }

        /** Which positions this alternative holds as one value, over its sides and its relation
         *  alike — see {@link AdmissibleValues.Alternative#sameness}. */
        Sameness<A> sameness() {
            Set<Sameness.Block<A>> named = new LinkedHashSet<>(product.at().keySet());
            named.addAll(apart.blocks());
            return Sameness.of(named);
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
            apart.blocks().forEach(block -> out.addAll(block.members()));
            return out;
        }

        /** Both alternatives holding at once, over what the two of them hold as one value —
         *  see {@link AdmissibleValues.Alternative#narrowedWith}. */
        Alternative<A> meet(Alternative<A> other) {
            Sameness<A> heldAsOne = sameness().meet(other.sameness());
            return new Alternative<>(product.meet(other.product, heldAsOne),
                    apart.and(other.apart).filedIn(heldAsOne));
        }

        @Override
        public String toString() {
            return apart.isEmpty() ? product.toString() : product + " with " + apart;
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
            gathering(at, heldAsOne, parts);
            gathering(other.at, heldAsOne, parts);
            Map<Sameness.Block<A>, AdmittedPlan> out = new LinkedHashMap<>();
            parts.forEach((block, these) -> out.put(block,
                    these.size() == 1 ? these.getFirst() : AdmittedPlan.meeting(these)));
            return new Box<>(out);
        }

        private static <A> void gathering(Map<Sameness.Block<A>, AdmittedPlan> these,
                                          Sameness<A> heldAsOne,
                                          Map<Sameness.Block<A>, List<AdmittedPlan>> parts) {
            these.forEach((block, plan) -> parts.computeIfAbsent(
                            heldAsOne.blockOf(block.members().iterator().next()),
                            _ -> new ArrayList<>())
                    .add(plan));
        }
    }

    /** One alternative, which is what most readings hold. */
    static <A> PlannedHeld<A> one(Alternative<A> box) {
        return new Alternatives<>(Set.of(box));
    }

    /** One alternative that states no denial. */
    static <A> PlannedHeld<A> one(Box<A> box) {
        return one(Alternative.of(box));
    }
}
