package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which values each position may hold, over all the rules a reading took in.
 *
 * <p>A state and not a set. A rule is written about a whole value and may name several of its
 * positions, and the connectives join whole readings rather than the answer at one position — so
 * what a conjunction and a disjunction are applied to is this, and the arithmetic at one position
 * is {@link ValueSet}.
 *
 * <h2>What is held</h2>
 *
 * <p>A union of products, or nothing. A product — a {@link Box} — is one set per position, standing
 * for every value with a value of each set at each place; a union of them is what a choice between
 * alternatives written at two positions leaves, and no one product holds it. How many of them a
 * reading may hold is what a caller bounds; held one at a time, the union is merged back into the
 * smallest box containing it, which is what a reading of these was before it held any.
 *
 * <p><b>Bottom is a state and not always a position.</b> The rules leave nothing where some position
 * is left no value — and also where every alternative of a choice is one nobody can take, which is
 * not a fact about any one position. So a reading that admits nothing is its own case
 * ({@link Held.Nothing}), and not an empty union: an empty union would say that no position can hold
 * anything, which is more than the rules were read to say.
 *
 * <p>Which of the two it is is a question about each position's own rules, and the alternatives
 * cannot answer it: a conjunction meets them pairwise and drops the pairs nothing stands in, so
 * what a dropped pair was going to say about a position leaves with it, and the answer would follow
 * the order the rules were met in. So the reading carries {@link #perPosition} beside them — every
 * position read on its own, by the same connectives — and that is what a reading admitting nothing
 * answers from.
 *
 * <p><b>A position no box holds is at {@link ValueSet#ANY}.</b> That is what makes the two
 * connectives what they are below, and it is the one thing to hold on to while reading them.
 *
 * <pre>
 *     meet             the keys of both, each side missing one standing at ANY
 *     join             the keys of both, each side missing one standing at ANY
 * </pre>
 *
 * <p>Which reads the same and is not: joining at a key one side does not hold is joining with ANY,
 * and that is ANY — so a join keeps only what both sides spoke about, and a meet keeps everything
 * either did. {@code value == "A" || something-this-cannot-read} has to come out saying nothing
 * about {@code value}, and a join written as a merge of the two maps says {@code "A"}.
 *
 * <h2>What the reading knows about itself</h2>
 *
 * <p>Beside the values, which positions this can speak for. A reading that could not take a rule in
 * leaves the values wider than the rules are, which is the safe direction for refusing a
 * declaration — nothing is admitted that the rules exclude — and the wrong direction for saying
 * that a model divides a position into these classes and no others. The two answers are different
 * questions and are kept apart: {@link #at} says which values, {@link #speaksFor} says whether that
 * is the whole of what the rules leave.
 *
 * <p>What a position holds is answered from two sides. {@link #at} is an upper approximation and
 * {@link #guaranteedAt} a lower one, and between them sits what the rules truly leave:
 *
 * <pre>
 *     guaranteedAt(p)   &#8838;   what is truly admitted at p   &#8838;   at(p)
 * </pre>
 *
 * <p>The lower one is what a choice needs. An alternative that admits every value at a position
 * settles it however little was read beside it, and a reading holding only "something went unread"
 * has thrown away what it would take to know that — which is why the two ends are carried and not
 * a flag standing for their difference.
 *
 * <p>Two things spoil it, and the second is the one that is easy to miss.
 *
 * <p>A rule this could not read spoils the positions it names, and what stopped it travels with
 * them ({@link UnreadReason}). It cannot spoil a position it does not name: nothing here relates
 * one position to another, so a rule that narrows a position names it — a rule relating two of them
 * names both, and is itself a rule this cannot read.
 *
 * <p>A rule this could not read spoils, under a disjunction, every position the other branch spoke
 * about, whether or not it names them. {@code value == "A" || opaque()} leaves {@code value} at ANY
 * because a value satisfying the second branch is under no obligation from the first — and ANY
 * arrived at that way is not the ANY of a position nothing was written about. Without this a
 * position would be reported as one the model draws no distinction at, when what happened is that
 * this reading could not follow the distinction the model draws.
 *
 * <p>Unless the alternatives already cover the position, which is the one thing that stops it.
 * {@code (value == "A" || value /= "A") || opaque()} leaves nothing for the unread branch to take
 * back: the two that were read admit every value between them, and a choice one of whose
 * alternatives admits every value at a position admits every value at it. That is what
 * {@link #guaranteedAt} is carried for, and holding "something went unread" alone would answer the
 * same clause two ways depending on where its brackets fell.
 */
public record AdmissibleValues<A>(Held<A> held, Map<A, ValueSet> perPosition,
                                  Standing<A> standing,
                                  Map<Sameness.Block<A>, ValueSet> guaranteed,
                                  ValueSet defaultGuaranteed,
                                  boolean guaranteedTogether,
                                  Set<Sameness.Block<A>> tangled,
                                  Set<Sameness.Block<A>> widened) {

    /**
     * The whole of what this reading is, written out for putting several of them in a work order.
     *
     * <p>Its own, because it is its own state. Written by whoever needed the order, this was a
     * second spelling of what a reading holds with nothing keeping the two in step — two components
     * were left out and two readings that differ came out alike, which put the order back where it
     * came from: the order they happened to arrive in.
     *
     * <p>So the components are walked and every one of them is answered for. A component added to
     * this record arrives here with nothing said about it and stops the walk, which is a question
     * somebody has to answer rather than a gap nobody sees.
     *
     * <p>What is left out is left out by name and for a reason. {@link #standing} is what the rules
     * of the model could not say and the order they were written in is the author's; ordering by it
     * would make how much a model costs turn on how its reasons were listed.
     */
    void schedulingForm(StringBuilder out) {
        for (String each : COMPONENTS) {
            switch (each) {
                case "held" -> PlanOrder.written(held, out);
                case "perPosition" -> PlanOrder.written(perPosition, out);
                case "guaranteed" -> PlanOrder.written(guaranteed, out);
                case "defaultGuaranteed" -> PlanOrder.write(defaultGuaranteed, out);
                case "guaranteedTogether" -> out.append(guaranteedTogether).append(';');
                case "tangled" -> named(tangled, out);
                case "widened" -> named(widened, out);
                // The author's, and not this. See above.
                case "standing" -> { }
                default -> throw new IllegalStateException(
                        "a reading holds " + each + " and nothing says how two readings holding"
                                + " different ones are put in an order");
            }
        }
    }

    /** Blocks by name, sorted, so that a set is written the same way however it was filled. */
    private void named(Set<Sameness.Block<A>> these, StringBuilder out) {
        out.append(these.size()).append(';');
        these.stream().map(String::valueOf).sorted().forEach(each -> out.append(each).append(';'));
    }

    /** Every part of a reading, in the order this record declares them. */
    private static final List<String> COMPONENTS =
            java.util.Arrays.stream(AdmissibleValues.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName).toList();

    /**
     * What the rules a reading took in leave: some alternatives, or nothing at all.
     *
     * <p>The two are not one shape with a flag. Nothing is not an empty union — read as one, a
     * position would come out unable to hold anything, and what is known is about the whole value.
     * And a union of alternatives is never empty, so nothing is left to mean by an empty one.
     */
    public sealed interface Held<A> {

        /**
         * Nothing satisfies the rules.
         *
         * <p>Which position is at fault, if any, is not held here. That is a question about each
         * position's own rules and is answered by {@link AdmissibleValues#perPosition} — the
         * alternatives cannot answer it, because a conjunction drops the pairs nothing stands in
         * and what a dropped pair was going to say about a position leaves with it.
         *
         * @param emptied the blocks of more than one position that were left no value, where that
         *                is what emptied the reading. Carried and not worked out again: an
         *                alternative is dropped where a side of it admits nothing, and the side is
         *                gone with it — asked afterwards, the answer would be that the values admit
         *                nothing, which is true and is the general form of what was shown.
         *
         *                <p>Only blocks of several positions. A lone position left no value is
         *                what {@link AdmissibleValues#perPosition} already answers, and a second
         *                account of it here would be the same fact in two spellings. What is new is
         *                a lack no position has on its own: the rules hold these positions as one
         *                value and leave that value nothing, while each of them on its own is left
         *                something
         */
        record Nothing<A>(Set<Sameness.Block<A>> emptied) implements Held<A> {

            public Nothing {
                emptied = Collections.unmodifiableSet(new LinkedHashSet<>(emptied));
                if (emptied.stream().anyMatch(Sameness.Block::isOne)) {
                    throw new IllegalArgumentException(
                            "a lone position left no value is what the positions' own rules say,"
                                    + " and is not a lack the block is answerable for");
                }
            }

            /** Nothing satisfies the rules, and no block of several positions is why. */
            public Nothing() {
                this(Set.of());
            }
        }

        /**
         * The alternatives the rules leave, no side of which admits nothing.
         *
         * <p>No side, and not none of which admits nothing. An alternative whose denials state a
         * value to differ from itself admits nothing and is one of these all the same: what says so
         * is the relation it carries, and it is read where a relation is read. Dropped where the
         * sides are put together, the rules that emptied it would be gone and a reader asking why
         * would be told the general answer.
         *
         * <p>A set and not a sequence: what is held is their union, so the same alternative written
         * twice is one alternative and the order two of them were met in is not part of the answer.
         * Iterated in the order they were found all the same — what is written out of a reading has
         * to come out the same on two compiles of one model.
         */
        final class Alternatives<A> implements Held<A> {

            private final Set<Alternative<A>> boxes;
            /**
             * Which positions every alternative holds as one value, which is the coordinate this
             * reading answers a position in.
             *
             * <p>What each alternative holds as one is its own — a branch may state an equality the
             * branch beside it does not — so what the reading can say of a position is what every
             * one of them says. Read the other way round, a branch would lend its equality to the
             * branch beside it and the choice would hold a rule neither alternative states.
             */
            private final Sameness<A> commonSameness;
            /** What each of those blocks holds across the alternatives, worked out where they were
             *  put together and looked up after. A block holding every value is left out, as it is
             *  everywhere else here. */
            private final Map<Sameness.Block<A>, ValueSet> across;

            private Alternatives(Set<Alternative<A>> boxes, Sameness<A> commonSameness,
                                 Map<Sameness.Block<A>, ValueSet> across) {
                if (boxes.isEmpty()) {
                    throw new IllegalArgumentException("a reading holding no alternative is Nothing");
                }
                this.boxes = Collections.unmodifiableSet(new LinkedHashSet<>(boxes));
                this.commonSameness = commonSameness;
                this.across = Collections.unmodifiableMap(new LinkedHashMap<>(across));
            }

            /** One alternative, which holds at each of its blocks what it says there. */
            public static <A> Alternatives<A> of(Alternative<A> box) {
                return new Alternatives<>(Set.of(box), box.sameness(), box.at());
            }

            /** Which positions every alternative holds as one value. */
            Sameness<A> commonSameness() {
                return commonSameness;
            }

            /** What every alternative holds as one, which is what a reading can say of a position
             *  it holds several alternatives of. */
            static <A> Sameness<A> commonTo(Collection<Alternative<A>> boxes) {
                Sameness<A> out = null;
                for (Alternative<A> box : boxes) {
                    out = out == null ? box.sameness() : out.common(box.sameness());
                }
                return out == null ? Sameness.discrete() : out;
            }

            /**
             * Several, with what each position holds across them already worked out.
             *
             * <p>For a caller that had the descriptions and put them together there
             * ({@link PlannedValues#resolve}). What a position holds across the alternatives is a
             * join, and a join of two languages is a machine — worked out from the sets after they
             * were built, it would be a machine nobody had described and nobody had counted.
             */
            static <A> Alternatives<A> of(Set<Alternative<A>> boxes, Sameness<A> commonSameness,
                                          Map<Sameness.Block<A>, ValueSet> across) {
                return new Alternatives<>(boxes, commonSameness, across);
            }

            /**
             * Several of them, and what each common block holds across them.
             *
             * <p><b>The one way to more than one alternative, and it takes a composer.</b> What a
             * block holds where a choice was held apart is the values either side leaves, which
             * is a join — and a join of two languages is a machine somebody has to pay for. Worked
             * out here, that happens once, while the alternatives are being put together and where
             * there is an allowance to charge; left to whoever asks {@link AdmissibleValues#at},
             * every reader of a reading would be doing it again, none of them counted.
             *
             * <p>Over the blocks every alternative holds as one and not over the positions. Two
             * positions the alternatives all hold as one have one answer between them, so one
             * machine is made and one purse pays for it; where the alternatives disagree about the
             * equality, the coordinate the union answers in is the finer one they agree on, and
             * that machine is its own.
             */
            static <A> Made<A> of(Set<Alternative<A>> boxes, Allowance<A> sets) {
                Sameness<A> common = commonTo(boxes);
                Map<Sameness.Block<A>, ValueSet> across = new LinkedHashMap<>();
                Set<Sameness.Block<A>> gaveUp = new LinkedHashSet<>();
                Set<Sameness.Block<A>> named = new LinkedHashSet<>();
                boxes.forEach(box -> box.positions()
                        .forEach(position -> named.add(common.blockOf(position))));
                for (Sameness.Block<A> block : named) {
                    List<ValueSet> these = boxes.stream().map(box -> box.get(
                            box.sameness().blockOf(block.members().iterator().next()))).toList();
                    // A block some alternative says nothing about is one a value satisfying that
                    // alternative may hold anything at, so the join is every value and is left out.
                    if (these.stream().anyMatch(ValueSet::isAny)) {
                        continue;
                    }
                    // Said as one plan over every alternative and worked out once. Folded over the
                    // alternatives two at a time, the order they were put together in was the order
                    // this happened to hold them — and a set is a set however it was filled, so the
                    // same alternatives would have cost two different things.
                    Allowance.Composed made = sets.joining(block, these);
                    if (made.gaveUp()) {
                        gaveUp.add(block);
                    }
                    if (!made.set().isAny()) {
                        across.put(block, made.set());
                    }
                }
                return new Made<>(new Alternatives<>(boxes, common, across), gaveUp);
            }

            /** The alternatives, and the blocks the exact answer across them was not built at. */
            record Made<A>(Alternatives<A> held, Set<Sameness.Block<A>> gaveUp) {}

            public Set<Alternative<A>> boxes() {
                return boxes;
            }

            /** What {@code atom} holds across the alternatives, which is the answer of the block
             *  it is on and is read rather than worked out. */
            ValueSet at(A atom) {
                return across.getOrDefault(commonSameness.blockOf(atom), ValueSet.ANY);
            }

            /** The same alternatives under other names, which moves no value and builds nothing. */
            <B> Alternatives<B> renamed(java.util.function.Function<A, B> naming) {
                Set<Alternative<B>> renamed = new LinkedHashSet<>();
                boxes.forEach(box -> renamed.add(box.renamed(naming)));
                Map<Sameness.Block<B>, ValueSet> out = new LinkedHashMap<>();
                across.forEach((block, set) -> out.put(block.renamed(naming), set));
                return new Alternatives<>(renamed, commonSameness.renamed(naming), out);
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof Alternatives<?> it && boxes.equals(it.boxes)
                        && commonSameness.equals(it.commonSameness) && across.equals(it.across);
            }

            @Override
            public int hashCode() {
                return (boxes.hashCode() * 31 + commonSameness.hashCode()) * 31 + across.hashCode();
            }

            @Override
            public String toString() {
                return "Alternatives" + boxes;
            }
        }
    }

    /**
     * One product: what each block may hold, with every combination of them standing.
     *
     * <p><b>A product over blocks and not over positions.</b> A rule stating that two positions
     * are equal does not narrow either of them; it says the two are one side of the product, so
     * what is stated about one of them afterwards is stated about the other. Held as a relation
     * beside a product over positions, that fact reaches whoever remembers to ask — and the reading
     * of a set is one place, the reading of a range another, so somebody forgets. Held as what the
     * product is indexed by, no reader can ask what one position admits without going through the
     * side it is on.
     *
     * <p>A block at {@link ValueSet#ANY} is left out where it is one position, so that what is
     * held is what was said. A block of several is kept whatever it admits: what it says is that
     * those positions are one value, and that is said by the block existing rather than by the set
     * it holds. Dropped for being wide, {@code p == r} on its own would leave a reading that had
     * read it and could not say so.
     *
     * <p>No block is left no value — a product with an empty side stands for nothing, which is not
     * an alternative but the absence of one, and a set of these is what a reading holds when
     * something does stand in it.
     *
     * <p>Refused here rather than remembered by whoever builds one. It is what lets a reading say it
     * admits nothing by being {@link Held.Nothing} and nothing else, so a caller that could put an
     * empty side in a box could make a reading that admits nothing and does not say so.
     */
    public record Box<A>(Map<Sameness.Block<A>, ValueSet> at) {

        public Box {
            at = stated(at);
            if (at.values().stream().anyMatch(ValueSet::isEmpty)) {
                throw new IllegalArgumentException(
                        "a product with an empty side stands for nothing, and is not an alternative");
            }
            // Read as the relation they are the classes of, which is what refuses two sides that
            // hold a position between them. Asked for here and not kept: a record holds what it was
            // given, and what this asks is whether what it was given is a product at all.
            Sameness.of(at.keySet());
        }

        /** One alternative over positions that are each their own block. */
        public static <A> Box<A> at(Map<A, ValueSet> said) {
            Map<Sameness.Block<A>, ValueSet> out = new LinkedHashMap<>();
            said.forEach((position, set) -> out.put(Sameness.Block.of(position), set));
            return new Box<>(out);
        }

        /** Which positions this alternative holds as one value, read off what it is a product
         *  over. */
        public Sameness<A> sameness() {
            return Sameness.of(at.keySet());
        }

        /** What this says stands at {@code block}, which is every value where it says nothing. */
        public ValueSet get(Sameness.Block<A> block) {
            return at.getOrDefault(block, ValueSet.ANY);
        }

        /** What stands at {@code position}, which is what the block it is on holds. */
        ValueSet get(A position) {
            return get(sameness().blockOf(position));
        }

        /** Every position this alternative says anything about. */
        Set<A> positions() {
            Set<A> out = new LinkedHashSet<>();
            at.keySet().forEach(block -> out.addAll(block.members()));
            return out;
        }

        /**
         * Both alternatives holding at once.
         *
         * <p>The equalities of the two are conjoined and closed first, and the sets are put
         * together over what that leaves: two blocks either side held apart are one block here if
         * anything holds their positions as one, and what the one block admits is what all of them
         * admitted. So {@code p == r} met with {@code p == Done} and with {@code r == Ready} is one
         * side of a product holding two sets that share no value, which is a box that stands for
         * nothing.
         *
         * <p>Said as one plan over every part rather than folded two at a time
         * ({@link Allowance#meeting}), so that a block gathering three sets costs one number
         * whichever order the equalities that made it were written in.
         *
         * <p>{@code heldAsOne} is handed in and not worked out here, because what the two of them
         * are a product over is not settled by their sides alone: a denial beside them names blocks
         * as well ({@link Alternative#sameness}). Asked of the sides, a side and a denial of one
         * conjunction would be filed a step apart.
         */
        Map<Sameness.Block<A>, ValueSet> narrowedWith(Box<A> other, Sameness<A> heldAsOne,
                                                      Allowance<A> sets,
                                                      Set<Sameness.Block<A>> gaveUp) {
            Map<Sameness.Block<A>, List<ValueSet>> parts = new LinkedHashMap<>();
            gathering(at, heldAsOne, parts);
            gathering(other.at, heldAsOne, parts);
            Map<Sameness.Block<A>, ValueSet> out = new LinkedHashMap<>();
            parts.forEach((block, these) -> {
                if (these.size() == 1) {
                    out.put(block, these.getFirst());
                    return;
                }
                Allowance.Composed made = sets.meeting(block, these);
                if (made.gaveUp()) {
                    gaveUp.add(block);
                }
                out.put(block, made.set());
            });
            return out;
        }

        /** Every side of one box filed under the block it is part of once the two are conjoined. */
        private static <A> void gathering(Map<Sameness.Block<A>, ValueSet> these,
                                          Sameness<A> heldAsOne,
                                          Map<Sameness.Block<A>, List<ValueSet>> parts) {
            these.forEach((block, set) -> parts.computeIfAbsent(
                            heldAsOne.blockOf(block.members().iterator().next()),
                            _ -> new ArrayList<>())
                    .add(set));
        }

        /** The same alternative under other names, which moves no value and builds nothing. */
        <B> Box<B> renamed(java.util.function.Function<A, B> naming) {
            Map<Sameness.Block<B>, ValueSet> out = new LinkedHashMap<>();
            at.forEach((block, set) -> out.put(block.renamed(naming), set));
            return new Box<>(out);
        }
    }

    /**
     * One alternative: a product over its blocks, and which of those blocks are stated to differ.
     *
     * <p>Two kinds of rule and one alternative. What each block may hold on its own is a product
     * and is {@link Box}; what a denial between two of them says is not a side of any product, so
     * it is beside it ({@link Apartness}) and the two together are what a value has to satisfy. A
     * value stands in this where it stands in the product and no two blocks the relation names hold
     * one value.
     *
     * <p><b>Which is why the relation is not in the box.</b> A box is every combination of its
     * sides standing, and a reader may take one side's value without asking about another's. A
     * denial makes some of those combinations stand for nothing, and a reader that could not see it
     * would go on reading the box as the set of what satisfies the rules.
     *
     * <p>The blocks this is a product over are its sides' and the relation's together. A denial
     * between two positions nothing else narrowed says nothing about what either admits, so the
     * product holds no side for them — and the alternative is still one whose answer at those
     * positions the relation speaks about.
     *
     * @param product what each block may hold, every combination of them standing
     * @param apart which of those blocks are stated to hold different values
     */
    public record Alternative<A>(Box<A> product, Apartness<A> apart) {

        /** One alternative that states no denial. */
        public static <A> Alternative<A> of(Box<A> product) {
            return new Alternative<>(product, Apartness.nothing());
        }

        /** One alternative over positions that are each their own block, stating no denial. */
        public static <A> Alternative<A> at(Map<A, ValueSet> said) {
            return of(Box.at(said));
        }

        /** What each block may hold, a block this says nothing about being left out. */
        public Map<Sameness.Block<A>, ValueSet> at() {
            return product.at();
        }

        /**
         * Which positions this alternative holds as one value, over its sides and its relation
         * alike.
         *
         * <p>Read off what it holds rather than kept beside it, which is what keeps the two from
         * disagreeing: a relation stored as a second account of the blocks would have to be moved
         * whenever the sides were, and the alternative would be a product over one thing and a
         * relation over another.
         */
        public Sameness<A> sameness() {
            Set<Sameness.Block<A>> named = new LinkedHashSet<>(product.at().keySet());
            named.addAll(apart.blocks());
            return Sameness.of(named);
        }

        ValueSet get(Sameness.Block<A> block) {
            return product.get(block);
        }

        /** What stands at {@code position}, which is what the block it is on holds. */
        ValueSet get(A position) {
            return get(sameness().blockOf(position));
        }

        /** Every position this alternative says anything about, by narrowing it or by relating
         *  it. */
        Set<A> positions() {
            Set<A> out = new LinkedHashSet<>(product.positions());
            apart.blocks().forEach(block -> out.addAll(block.members()));
            return out;
        }

        /**
         * Both alternatives holding at once.
         *
         * <p>{@link Box#narrowedWith}'s rule, and the relation carried onto the blocks that rule
         * leaves. The equalities of the two are conjoined and closed first, so a denial stated of
         * blocks either side was a product over is a denial of whatever those blocks are part of
         * here — and where the two ends land on one block, the conjunction says a value differs
         * from itself and nothing satisfies it.
         *
         * <p>Carried here and not by whoever meets two of them, because the blocks the sides are
         * filed under and the blocks the relation names are the same blocks: moved separately, the
         * two would be filed under coordinates a step apart and {@link Sameness#filing} would
         * refuse whichever of them was moved second.
         */
        Met<A> narrowedWith(Alternative<A> other, Allowance<A> sets,
                            Set<Sameness.Block<A>> gaveUp) {
            Sameness<A> heldAsOne = sameness().meet(other.sameness());
            return new Met<>(product.narrowedWith(other.product, heldAsOne, sets, gaveUp),
                    apart.and(other.apart).filedIn(heldAsOne));
        }

        /** What a conjunction of two alternatives came to, before anything asks whether a value
         *  stands in it. */
        record Met<A>(Map<Sameness.Block<A>, ValueSet> at, Apartness<A> apart) {

            /**
             * Whether a side of it is left no value, which is what makes it no alternative.
             *
             * <p>A side and not the relation. A conjunction that puts two blocks together makes a
             * denial between them a value stated to differ from itself, and nothing satisfies that
             * — but it is a fact the relation states rather than a product with an empty side, and
             * what states it is still there to be read. Dropped here, the alternative would go and
             * the rules that emptied it would go with it, and a reader asking why would be told
             * that the values admit nothing.
             */
            boolean standsForNothing() {
                return at.values().stream().anyMatch(ValueSet::isEmpty);
            }

            /** The alternative it is, which a caller asks for only where something stands in it. */
            Alternative<A> alternative() {
                return new Alternative<>(new Box<>(at), apart);
            }
        }

        /** The same alternative under other names, which moves no value and builds nothing. */
        <B> Alternative<B> renamed(java.util.function.Function<A, B> naming) {
            return new Alternative<>(product.renamed(naming), apart.renamed(naming));
        }

        @Override
        public String toString() {
            return apart.isEmpty() ? product.toString() : product + " with " + apart;
        }
    }

    /**
     * @param held what the rules leave: the alternatives, or nothing
     * @param perPosition what each position's own rules leave it, every alternative merged. Not
     *                what a position may hold — {@link #at} is narrower wherever the alternatives
     *                are held apart, which is the whole point of holding them — and not a second
     *                account of that either. It answers the one question the alternatives cannot:
     *                which position, if any, is why nothing is admitted.
     *
     *                <p>A conjunction meets the alternatives pairwise and drops the pairs nothing
     *                stands in, so what a dropped pair was going to say about a position leaves
     *                with it. Asked of what survived, the answer follows the order the rules were
     *                met in and blames a position no rule of theirs leaves empty. Read on its own
     *                by the same connectives it does not: a meet of these is their meet at each
     *                position, and that is the same however it is bracketed
     * @param standing what a rule left standing at each position, and what stopped the reading
     *                from taking it in. Why and not only which: two of these are lifted by
     *                different work and reported differently, and a reader handed the positions
     *                alone would have to go back to the rules to find out which it was.
     *
     *                <p><b>Every reason and not the first of them.</b> One position is named by as
     *                many parts of a clause as the author wrote about it, and two of those can stop
     *                this reading in two ways: {@code a /= b} relates the position to another and
     *                {@code String.matches(p, a)} is a form this has no word for, and each is
     *                lifted by different work. Kept as one, which of the two a reader was shown
     *                turned on the order the parts were met in, and the other was gone with nothing
     *                saying so. In the order they were met, which is the order the author wrote
     *                them, so that two runs over one model produce the same list.
     *
     *                <p><b>Not the positions this cannot speak for.</b> A rule left standing where
     *                the alternatives cover the position between them is one nothing there is
     *                answerable for, and it is held all the same, since whether they still cover it
     *                turns on rules stated beside the choice that have not been read yet.
     *                {@link #speaksFor} and {@link #whyUnread} are the readings; this is what they
     *                are read from, and it holds both kinds of evidence there are: a rule of the
     *                positions that went unread, and a position an alternative nothing could read
     *                left open
     * @param guaranteed which values each position is guaranteed to admit — read through
     *                {@link #guaranteedAt} rather than off this map, which holds a position whose
     *                guarantee is the default as well. Held that way on purpose: the keys are
     *                {@link #promisedAt}, the positions a rule of this reading reached, and
     *                dropping the ones that came to the default would make that set turn on which
     *                rules happened to leave a position where it started. A choice reads it twice
     *                over, and both readings would follow the brackets
     * @param defaultGuaranteed what a position this holds no guarantee for is guaranteed to admit.
     *                Which is why what a choice left open is carried and not read off this:
     *                {@code value == 5} joined with a rule nothing could read has this at
     *                {@link ValueSet#ANY}, because the alternative that was read guarantees every
     *                value at every position it says nothing about — and that a rule of the choice
     *                went unread is a different fact, which nothing about the guarantee says
     * @param guaranteedTogether whether one value may be taken from each position's guarantee and
     *                the whole of them stand together in this reading. What a conjunction needs of
     *                its sides and what a choice over more than one position does not leave
     * @param tangled the positions whose correlations this reading has lost. A union of products
     *                merged back into one product is where that happens, and a choice between
     *                alternatives written at two positions is a union no product states. Outside
     *                this set the relation factors into a product, which is what lets a position no
     *                choice reached keep its own answer
     * @param widened the positions whose {@link #at} cannot be guaranteed to be what the read rules
     *                leave them. A guarantee and not a fact: the rules below are sufficient and not
     *                necessary, so absence from this set is what is shown and presence is what is
     *                not shown either way, and a sharper reading later leaves a position out of it
     *                without anything here changing meaning.
     *
     *                <p>Held per position rather than as one answer for the reading, because the
     *                proposition is quantified over them. Read off a single flag, the only thing
     *                that can be said is that some position is not shown exact, and a reader asking
     *                about one of them is handed that sentence about each — which is the other
     *                quantifier and is false wherever a clause of its own answers for a position
     */
    public AdmissibleValues {
        // Every part is copied, and a reader wanting to know that they all are asks the record
        // rather than this list — `EveryPartOfAReadingIsAValue` counts the parts off the
        // declaration, because a list written here is one a part added later is missing from and
        // a list written there would be a copy of it with the same hole.
        perPosition = said(perPosition);
        // A guarantee empty at one position is empty at all of them. What is promised is one set
        // per position standing for the product of them, and a product with an empty side is
        // empty — so there is no value at any position that this can promise. This is also where a
        // reading that admits nothing arrives, by whichever way it got there: a leaf left no value,
        // two rules that cannot both hold, a caller that showed it from outside.
        if (held instanceof Held.Nothing || defaultGuaranteed.isEmpty()
                || guaranteed.values().stream().anyMatch(ValueSet::isEmpty)) {
            guaranteed = Map.of();
            defaultGuaranteed = ValueSet.NONE;
            guaranteedTogether = true;
        }
        guaranteed = Collections.unmodifiableMap(new LinkedHashMap<>(guaranteed));
        // Kept in the order they were recorded rather than as an immutable copy, whose iteration
        // order is salted per run of the JVM: what is written out of a reading has to come out the
        // same on two compiles of one model.
        tangled = Collections.unmodifiableSet(new LinkedHashSet<>(tangled));
        widened = Collections.unmodifiableSet(new LinkedHashSet<>(widened));
        Sameness<A> mine = held instanceof Held.Alternatives<A> it
                ? it.commonSameness() : Sameness.discrete();
        mine.filing(guaranteed.keySet(), tangled, widened);
    }

    /**
     * What an alternative states, which a block of one position states by narrowing and a block
     * of several states by being one.
     *
     * <p>Two rules and not one, and the difference is what the second kind of block is for. A lone
     * position holding every value is held by being absent, as everywhere else here, since holding
     * it would make one reading two states. Positions held as one value say that whatever they
     * admit, so a block of several is kept at {@link ValueSet#ANY} — dropped by the first rule,
     * an equality nothing else narrowed would be read and then forgotten.
     */
    private static <A> Map<Sameness.Block<A>, ValueSet> stated(Map<Sameness.Block<A>, ValueSet> at) {
        Map<Sameness.Block<A>, ValueSet> out = new LinkedHashMap<>();
        at.forEach((block, set) -> {
            if (!block.isOne() || !set.isAny()) {
                out.put(block, set);
            }
        });
        return Collections.unmodifiableMap(out);
    }

    /** What was said, which is what is not {@link ValueSet#ANY}: a position nothing narrowed is
     *  held by being absent, and holding it would make the same reading two states. */
    private static <A> Map<A, ValueSet> said(Map<A, ValueSet> at) {
        Map<A, ValueSet> out = new LinkedHashMap<>();
        at.forEach((atom, set) -> {
            if (!set.isAny()) {
                out.put(atom, set);
            }
        });
        return Collections.unmodifiableMap(out);
    }

    /**
     * Which values this reading can guarantee are admitted at {@code atom}, however much the rules
     * it could not read turn out to exclude.
     *
     * <p>A lower approximation where {@link #at} is an upper one, and equal to it at a position
     * this says that nothing left unread is why the answer is as wide as it is — which is what
     * {@link #speaksFor} is read from.
     *
     * <p><b>A choice composes these and a conjunction promises nothing.</b> Either alternative of a
     * choice holding is enough, so what the two of them promise a position is what either does, and
     * that is read at the position — {@code (value == 5 || value /= 5) || anything} promises every
     * value at {@code value}, whichever way its alternatives are bracketed.
     *
     * <p>A conjunction may compose them only where both sides promise their positions together.
     * What is promised is one set per position, and a choice over more than one position leaves no
     * such product: {@code (a == 5 && b == 0) || (a /= 5 && b == 1)} promises {@code a} every value
     * and {@code b} two, which as a product holds {@code a = 5, b = 1} — a pair neither alternative
     * stands for. Met with a rule admitting only {@code b = 0}, sets like that would say {@code a}
     * is still free while the rules hold it to 5. So {@link #guaranteedTogether} says whether the
     * promise is one about whole values, and a conjunction promises nothing where it is not.
     *
     * <p><b>A promise about what was read and not about what is held.</b> The alternatives above may
     * be held apart, and this is a product all the same. Which rules went unread is what it is about,
     * and holding a union does not read one of them.
     *
     * <p>Which is why {@link #speaksFor} is not answered from these under a conjunction either. A
     * rule stated beside others narrows rather than widens, so what an unread one costs there is
     * answered by the positions it names, and that is the account {@link #standing} has kept all
     * along.
     *
     * <p><b>What that costs is a promise, and it is paid across the whole value.</b> A conjunction
     * with a part nothing could read promises nothing anywhere, so a position covered inside one
     * clause is reported short of its rules once any clause of the same value goes unread —
     * {@code invariant said = (n == 5 || n /= 5) || f(n)} beside {@code invariant apart = g(m)}
     * leaves {@code n} partial, though nothing about {@code n} is what {@code g(m)} could narrow.
     * Telling the two apart wants a reading that remembers why it promises nothing, which is more
     * than a promise and less than this holds.
     */
    public ValueSet guaranteedAt(A atom) {
        return guaranteed.getOrDefault(blockOf(atom), defaultGuaranteed);
    }

    /**
     * Which positions this reading holds as one value, whatever alternative a value stands in.
     *
     * <p>The coordinate every answer of this reading is in. What stands at a position, what is
     * promised there, and whether either of those is exact are answers about the block the
     * position is on — so a caller asking about a position is asking about its block, and the
     * projection is here rather than in each of them.
     *
     * <p>A reading that admits nothing holds no block. There are no alternatives to agree, and an
     * agreement read off none of them would hold every position as one with every other — which is
     * every cross-position impossibility this compiler cannot show, claimed by an empty
     * intersection.
     */
    public Sameness<A> sameness() {
        return held instanceof Held.Alternatives<A> it ? it.commonSameness() : Sameness.discrete();
    }

    /** The block {@code position} is on, which is the position on its own wherever no equality
     *  reached every alternative. */
    public Sameness.Block<A> blockOf(A position) {
        return sameness().blockOf(position);
    }

    /**
     * Whether {@link #at} at {@code atom} can be guaranteed to be what the read rules leave it.
     *
     * <p>Asked of a reading that holds alternatives. Where it admits nothing, {@link #at} answers
     * from what the arithmetic was left holding and not from the relation, whose projections are
     * all empty — so there is no projection here for an answer to be exact about, and a caller
     * asking is asking about a position no value ever stands at.
     */
    public boolean projectionExactAt(A atom) {
        // A reading that admits nothing is exact everywhere. What stands at a position then is read
        // off what the arithmetic was left holding rather than off the alternatives, there being
        // none, so there is no projection here for an answer to be wider than.
        //
        // Asked of the block, because that is what the answer was built for. Two positions held as
        // one value have one machine between them, so one of them cannot be exact while the other
        // is: read per position, a block whose machine was given up on would report the position
        // the widening was recorded against as wide and the position beside it as exact, while
        // {@link #at} hands both of them the same set.
        return held instanceof Held.Nothing || !widened.contains(blockOf(atom));
    }

    /** Whether what this holds can be guaranteed to be the whole of what the read rules admit,
     *  which this reading guarantees where no union of alternatives was merged into one. The same
     *  proof state as above and about the same readings: one that admits nothing holds no
     *  relation. */
    public boolean relationExact() {
        return tangled.isEmpty();
    }

    /** Nothing read and nothing missed, which is what a reading starts from. */
    public static <A> AdmissibleValues<A> top() {
        return new AdmissibleValues<>(one(Box.at(Map.of())), Map.of(), Standing.nothing(),
                Map.of(), ValueSet.ANY, true, Set.of(), Set.of());
    }

    /**
     * This where it already admits nothing, and a state admitting nothing where it does not.
     *
     * <p>What a caller says when something outside this showed that nothing satisfies the rules —
     * another domain reading the same clause, say. Nothing is claimed about any position: what is
     * known is about the whole, and writing it at a position would name one the rules are fine with.
     */
    public AdmissibleValues<A> leavingNothing() {
        // What it was proved about goes with it into the coordinates a reading admitting nothing
        // has, which are the positions on their own: there are no alternatives left to agree that
        // two of them hold one value.
        return isBottom() ? this
                : new AdmissibleValues<>(new Held.Nothing<>(), Map.of(), standing,
                        Map.of(), ValueSet.NONE, true, eachApart(tangled), eachApart(widened));
    }

    /** The same blocks as the positions they are made of, which is the coordinate a reading with
     *  no alternatives left answers in. */
    private static <A> Set<Sameness.Block<A>> eachApart(Set<Sameness.Block<A>> these) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        these.forEach(block -> block.members().forEach(each -> out.add(Sameness.Block.of(each))));
        return out;
    }

    /**
     * The same blocks, said in {@code into}'s coordinates.
     *
     * <p>Both ways round, which is what a conjunction and a choice each need. A conjunction leaves
     * a coarser relation, so several of these arrive at one block; a choice leaves a finer one, so
     * one of them comes apart into the blocks it holds. Either way what is being said is about the
     * positions, and the positions are what carries it across.
     */
    private static <A> Set<Sameness.Block<A>> mapped(Set<Sameness.Block<A>> these,
                                                     Sameness<A> into) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        these.forEach(block ->
                block.members().forEach(each -> out.add(into.blockOf(each))));
        return out;
    }

    /** One position said to admit {@code set}, and nothing missed. */
    public static <A> AdmissibleValues<A> at(A atom, ValueSet set) {
        Map<A, ValueSet> said = Map.of(atom, set);
        return new AdmissibleValues<>(set.isEmpty() ? new Held.Nothing<>() : one(Box.at(said)),
                said, Standing.nothing(), Map.of(Sameness.Block.of(atom), set),
                ValueSet.ANY, true, Set.of(), Set.of());
    }

    /**
     * Two positions said to hold one value, which is what an equality between them states.
     *
     * <p>Nothing is narrowed anywhere: what this says is that the two are one side of the product,
     * so whatever either of them is stated to admit is what both admit. Which is why it is a
     * reading of the values at all — a rule relating two positions was one nothing here could take
     * in, and what it left was two positions with two answers and a rule between them that reached
     * nothing.
     */
    public static <A> AdmissibleValues<A> holdingAsOne(A here, A there) {
        Sameness.Block<A> block = Sameness.of(here, there).blockOf(here);
        // Promised at the block, though it narrows nothing there. The keys of the promise are the
        // footprint as well — the blocks a rule of this reading reached — and this rule reached
        // one: it shapes the relation without touching what any position admits. Left out, a
        // choice between two equalities would be a union of two relations that no product holds
        // and would say it lost nothing, since what it reads to decide that is this key set.
        return new AdmissibleValues<>(one(new Box<>(Map.of(block, ValueSet.ANY))), Map.of(),
                Standing.nothing(), Map.of(block, ValueSet.ANY), ValueSet.ANY, true,
                Set.of(), Set.of());
    }

    /**
     * Two positions said to hold different values, which is what a denial between them states.
     *
     * <p>Narrowing neither, the way an equality narrows neither: what a denial says is that no one
     * value stands at both, which is a relation between two sides of the product rather than a
     * statement about either. So it is held beside the product ({@link Apartness}) and what it
     * comes to is worked out where the values each side is left are in hand.
     *
     * <p><b>And guaranteeing nothing at either of them.</b> What is guaranteed is a lower bound —
     * these values are admitted whatever else is read — and no value can be shown admitted at one
     * of two positions held apart without an assignment for the other. Over a carrier of one value
     * a denial admits nothing at all, so a guarantee of every value would be false of a model
     * somebody can write. Said here rather than wherever a denial is met with something, because
     * meeting a guarantee with nothing leaves nothing: every conjunction this rule reaches has it
     * without a second rule saying so, and a choice beside it keeps what its other branch
     * guarantees, which is right — a value satisfying that branch is under no denial.
     */
    public static <A> AdmissibleValues<A> heldApart(A here, A there) {
        Sameness.Block<A> one = Sameness.Block.of(here);
        Sameness.Block<A> other = Sameness.Block.of(there);
        Map<Sameness.Block<A>, ValueSet> promised = new LinkedHashMap<>();
        promised.put(one, ValueSet.NONE);
        promised.put(other, ValueSet.NONE);
        return new AdmissibleValues<>(
                one(new Alternative<>(new Box<>(Map.of()), Apartness.of(here, there))),
                Map.of(), Standing.nothing(), promised, ValueSet.ANY, true, Set.of(), Set.of());
    }

    /**
     * A rule this could not read, which says nothing about any position and spoils the ones it
     * names.
     *
     * <p>{@code named} may be empty — a rule reaching no position this can name is still a rule that
     * was not read, and that it was is a fact about the clause somebody wrote rather than about any
     * position of this reading ({@code Adoption}).
     *
     * <p>Nothing here was opened by an alternative. What this leaves at the positions it names is
     * its own account of them, said directly; that a choice above holds this clause in one of its
     * branches is that choice's fact, and reaches the positions from where the choice is.
     */
    public static <A> AdmissibleValues<A> unreadable(Set<A> named, UnreadReason why) {
        // Nothing is guaranteed anywhere, and at the positions it does not name as much as at the
        // ones it does: what a rule this has no word for admits is not known, so a choice offering
        // it as an alternative is offering nothing that can be counted on.
        return new AdmissibleValues<>(one(Box.at(Map.of())), Map.of(),
                Standing.of(named, why), Map.of(),
                ValueSet.NONE, true, Set.of(), Set.of());
    }

    /**
     * What {@code atom} may hold, everything being admitted where nothing was said.
     *
     * <p>Over the alternatives, since a value standing in any of them stands in the reading. Which
     * is the projection of the union and is exact whatever they are written at — what a union
     * cannot state is which value at one position went with which at another, and that is not what
     * this asks.
     */
    public ValueSet at(A atom) {
        return switch (held) {
            case Held.Nothing<A> _ -> perPosition.getOrDefault(atom, ValueSet.ANY);
            // Read and not worked out. What a position holds across the alternatives was settled
            // where they were put together, which is where there was an allowance for the machine
            // it may take. See {@link Held.Alternatives#of}.
            case Held.Alternatives<A> it -> it.at(atom);
        };
    }

    /**
     * The positions this reading narrowed, which is what a reader asking what it took in is asking.
     *
     * <p>Narrowed by the reading and not by an alternative of it: a position one alternative names
     * and another says nothing about is left at every value by the choice, and a rule that narrowed
     * nothing is not one a question can be answered from.
     */
    public Set<A> adoptedAt() {
        Set<A> out = new LinkedHashSet<>();
        adopted().forEach(atom -> {
            if (!at(atom).isAny()) {
                out.add(atom);
            }
        });
        return out;
    }

    /**
     * Whether {@link #at} is the whole of what the rules leave {@code atom}, rather than a wider
     * answer this reading could not narrow.
     *
     * <p>Either nothing was left standing at the position, or what was left standing cannot be
     * answerable for the answer's width: the two ends meet there, so every value reported is one
     * this reading can promise and there is nothing between them for an unread rule to have been.
     *
     * <p><b>Asked of the reading in hand and not settled where a rule was left standing.</b> What an
     * alternative covers is what that alternative admits, and a rule stated beside the choice may
     * leave nothing of the alternative that did the covering. In {@code (a == 5 || a /= b) && a == 7}
     * the first alternative admits every {@code b}, and the second rule refuses every value it
     * admits — so what covered {@code b} is gone, and a reading that had already struck the rule off
     * would answer that the model leaves {@code b} every value.
     */
    public boolean speaksFor(A atom) {
        return unreadAffecting(atom).isEmpty();
    }

    /**
     * Everything that stopped this reading from speaking for {@code atom}, empty where nothing did.
     *
     * <p>Every one of them, in the order the parts of the clause were met. A part this could not
     * take in is not an account of the part written beside it, so a caller choosing among them is
     * choosing which of an author's rules to tell them about — and the choice would be made here,
     * where the only thing to choose by is which came first.
     *
     * <p>Which is why a choice that left the position open is what a position with no rule of its
     * own is told about, and nothing a position does have a rule of its own hears
     * ({@link Standing#across}): that is a choice between two kinds of thing rather than among an
     * author's rules, and it is made on what is held rather than on what arrived first.
     */
    public List<UnreadReason> whyUnread(A atom) {
        return unreadAffecting(atom);
    }

    /**
     * Everything that stops this reading from speaking for what stands at {@code atom}, which is
     * both of the questions above and is answered once.
     *
     * <p>Whether the answer is the whole of what the rules leave and why it is not are one fact,
     * so they are one derivation. Answered apart, the first was asked of the block and the second
     * read the position's own reasons, and a position held as one value with another came out
     * unanswerable with nothing to say for it.
     *
     * <p><b>Over the block and not the position.</b> A rule this could not read at one position is
     * a rule about the value that position holds, and where another position holds that same value
     * it is a rule about that one too. Read per position, {@code p == r && opaque(p)} reported that
     * every rule about {@code r} had been read while {@link #at} handed it the answer the unread
     * rule was going to narrow.
     *
     * <p><b>In one order, and it is the order the rules were written.</b> What the reading was
     * handed is one entry per rule it gave up on, holding every position that rule named
     * ({@link Standing}), so the order over several positions is the order they were met — the
     * author's. Filed by position instead, that order survives only inside one place, and a reader
     * shown two places would be shown them in an order this compiler invented.
     */
    private List<UnreadReason> unreadAffecting(A atom) {
        // The two ends meet, so every value reported is one this reading can promise and there is
        // nothing between them for an unread rule to have been. Asked of the block, which is what
        // both ends answer for.
        if (guaranteedAt(atom).equals(at(atom))) {
            return List.of();
        }
        return standing.across(blockOf(atom).members());
    }

    /** Whether nothing satisfies these rules, at a position or otherwise. */
    public boolean isBottom() {
        return held instanceof Held.Nothing;
    }

    /**
     * The blocks of more than one position the rules left no value, where that is what emptied
     * this reading.
     *
     * <p>Empty where the reading holds something, and empty where what emptied it is a position's
     * own rules rather than what several of them share. Read here rather than worked out from what
     * survived: nothing survived.
     */
    public Set<Sameness.Block<A>> emptiedBlocks() {
        return held instanceof Held.Nothing<A> it ? it.emptied() : Set.of();
    }

    /**
     * Whether an alternative survives a question asked of every position it names.
     *
     * <p>The walk this reading owns, for a question it does not. What the rules leave is a union of
     * products, and something said about the positions elsewhere — where their orders stop, say —
     * cuts each product on its own: an alternative stands where every position of it still admits
     * something, and the reading stands where any alternative does.
     *
     * <p><b>Per alternative and never per position.</b> The projection onto one position
     * ({@link #at}) is the union over the alternatives, and a question answered against that is a
     * question about a value no alternative stands for: {@code (x = A, y = B)} beside
     * {@code (x = C, y = D)} projects to {@code x} in {@code {A, C}} and {@code y} in
     * {@code {B, D}}, and asked position by position, a rule admitting {@code x = A} and
     * {@code y = D} finds something at each of them and nothing anywhere.
     *
     * <p>A position no alternative names is not asked about. What is held there is every value, and
     * a question that anything at all answers is answered by that — so what such a position could
     * contribute is settled by whoever asks, before the walk.
     *
     * <p>Three answers, because the question may be one that waits. An alternative is settled empty
     * where any of its positions is, and settled inhabited only where every one of them is; the
     * reading is settled empty only where every alternative is, since one nobody worked out may yet
     * hold something.
     */
    public Emptiness anyAlternativeAdmits(AskedOfEachBlock<A> asked, AskedOfARelation<A> relating) {
        if (held instanceof Held.Alternatives<A> it) {
            Emptiness any = Emptiness.EMPTY;
            for (Alternative<A> box : it.boxes()) {
                Emptiness stands = Emptiness.NONEMPTY;
                for (Map.Entry<Sameness.Block<A>, ValueSet> each : box.at().entrySet()) {
                    stands = stands.met(asked.of(each.getKey(), each.getValue()));
                    if (stands == Emptiness.EMPTY) {
                        break;
                    }
                }
                // And what its denials come to, asked after the blocks and not before. An
                // alternative already refused at a block is one no relation has to be read for,
                // and reading it first would spend on every alternative what one question settled.
                if (stands != Emptiness.EMPTY) {
                    stands = stands.met(relating.of(box.apart(), box.product()).emptiness());
                }
                any = any.joined(stands);
                if (any == Emptiness.NONEMPTY) {
                    return any;
                }
            }
            return any;
        }
        return Emptiness.EMPTY;
    }

    /**
     * The blocks every alternative is refused at, for a reader writing down where a reading was
     * left nothing.
     *
     * <p>Every alternative and not one of them. Where the alternatives are refused at different
     * blocks, no block is what the reading has no value at — each of them holds values some
     * alternative stands at — so what can be said is that nothing satisfies the rules, and naming
     * one would send an author after a rule the model does not contain. Which is why what is kept
     * across the alternatives is the blocks themselves: two of them refused at blocks that overlap
     * without being equal have shown nothing about what they share, and a set of positions
     * intersected would say they had.
     *
     * <p>Asked to write a proof and not to reach an answer, so it walks the whole of every
     * alternative where {@link #anyAlternativeAdmits} stops at the first block that settles one.
     * What it cannot do is disagree with that answer about anything a reader acts on: what comes
     * back is somewhere to name, and none of them is the general form.
     */
    public Refusal<A> refusedInEveryAlternativeAt(AskedOfEachBlock<A> asked,
                                                  AskedOfARelation<A> relating) {
        if (!(held instanceof Held.Alternatives<A> it)) {
            return new Refusal.Nowhere<>();
        }
        Refusal<A> everywhere = null;
        for (Alternative<A> box : it.boxes()) {
            Refusal<A> here = refusalIn(box, asked, relating);
            if (here.isNowhere()) {
                return new Refusal.Nowhere<>();
            }
            everywhere = everywhere == null ? here : Refusal.shownByBoth(everywhere, here);
            if (everywhere.isNowhere()) {
                return new Refusal.Nowhere<>();
            }
        }
        return everywhere == null ? new Refusal.Nowhere<>() : everywhere;
    }

    /**
     * Where one alternative was refused, which is at its blocks or about several of them together.
     *
     * <p>The blocks first, because a lack at a block is the nearer answer: it names a place whose
     * own rules leave it nothing, where a lack about several of them names no such place. An
     * alternative refused both ways is refused at the blocks, and the relation is what is left to
     * say where no block has nothing on its own.
     */
    private Refusal<A> refusalIn(Alternative<A> box, AskedOfEachBlock<A> asked,
                                 AskedOfARelation<A> relating) {
        Set<Sameness.Block<A>> here = new LinkedHashSet<>();
        // The block and not its positions. What was refused is the one value those positions
        // share, and each of them may be left something on its own — taken apart here, the
        // proof would say a lack is at a place whose own rules are fine with it.
        box.at().forEach((block, set) -> {
            if (asked.of(block, set) == Emptiness.EMPTY) {
                here.add(block);
            }
        });
        if (!here.isEmpty()) {
            return new Refusal.AtEachOf<>(here);
        }
        return relating.of(box.apart(), box.product()) instanceof Apartness.Reduction.Nothing<A> it
                ? new Refusal.OfThemTogether<>(it.why()) : new Refusal.Nowhere<>();
    }

    /**
     * Every subject this reading is filed under.
     *
     * <p>The vocabulary of one reading, and the one place it is answered. A caller relating several
     * readings has to know which of them speaks about what — to rename one without colliding with
     * another, and to conjoin two without expanding a product neither of them relates. Worked out
     * from the same six places {@link #renamed} rewrites, because a subject the two disagreed about
     * would be one filed under a name nothing else in the reading uses.
     *
     * <p><b>What a subject outside this is.</b> {@link #at} answers {@link ValueSet#ANY} for one: no
     * box holds it, so the join over the alternatives is the join of ANY, and a reading that admits
     * nothing has no alternatives to be asked about. So a reading says nothing about which values
     * stand at a subject it does not name, which is what lets two readings over disjoint
     * vocabularies be conjoined without being multiplied together.
     *
     * <p>Not the same of {@link #guaranteedAt}. What is guaranteed at a subject nothing was recorded
     * for is {@link #defaultGuaranteed}, which is a lower bound and is {@link ValueSet#NONE}
     * wherever nothing promised otherwise — weaker rather than absent, and never a refusal. A caller
     * wanting a guarantee about a subject asks the reading that names it.
     */
    public Set<A> subjects() {
        Set<A> out = new LinkedHashSet<>();
        if (held instanceof Held.Alternatives<A> alternatives) {
            alternatives.boxes().forEach(box -> out.addAll(box.positions()));
        }
        out.addAll(perPosition.keySet());
        out.addAll(standing.positions());
        members(guaranteed.keySet(), out);
        members(tangled, out);
        members(widened, out);
        return Collections.unmodifiableSet(out);
    }

    /** The positions the blocks are of, which is what a reading is filed under whatever it holds
     *  them as. */
    private static <A> void members(Set<Sameness.Block<A>> these, Set<A> out) {
        these.forEach(block -> out.addAll(block.members()));
    }

    /**
     * The same reading of the same positions, under the names {@code naming} gives them.
     *
     * <p>The naming has to name two positions two positions. Two of them arriving under one name
     * would hold each other's values — narrowed against each other where both were read, and given
     * the other's where one was not — which is the reading admitting or refusing values on the
     * strength of a rule written about somewhere else. Not checked here, because what a naming must
     * not collide over is every subject of every domain of one reading and no domain can see the
     * others; it is checked where a whole vocabulary is
     * ({@code souther.compiler.check.InjectiveRenaming}). Every position held here passes through
     * the naming, so a caller holding one of those sees all of them.
     *
     * <p>Every place a position is filed under, and not the alternatives alone. A position sits in
     * the boxes, in what was read of it on its own, in what stands unread, in what is guaranteed, and
     * in the two sets recording how it got there — a renaming that reached some of those would leave
     * the rest filed under names nothing else in the state uses, which reads as a position nobody
     * said anything about.
     */
    public <B> AdmissibleValues<B> renamed(java.util.function.Function<A, B> naming) {
        Held<B> renamedHeld = switch (held) {
            case Held.Nothing<A> it -> new Held.Nothing<B>(renamedNames(it.emptied(), naming));
            case Held.Alternatives<A> alternatives -> alternatives.renamed(naming);
        };
        return new AdmissibleValues<>(renamedHeld, renamedKeys(perPosition, naming),
                standing.renamed(naming),
                renamedBlocks(guaranteed, naming), defaultGuaranteed, guaranteedTogether,
                renamedNames(tangled, naming), renamedNames(widened, naming));
    }

    /** The same map, filed under what {@code naming} calls the positions of each of its blocks. */
    private static <A, B, V> Map<Sameness.Block<B>, V> renamedBlocks(
            Map<Sameness.Block<A>, V> of, java.util.function.Function<A, B> naming) {
        Map<Sameness.Block<B>, V> out = new LinkedHashMap<>();
        of.forEach((block, value) -> out.put(block.renamed(naming), value));
        return out;
    }

    /** The same map, filed under what {@code naming} calls each of its keys. */
    private static <A, B, V> Map<B, V> renamedKeys(Map<A, V> of,
                                                   java.util.function.Function<A, B> naming) {
        Map<B, V> out = new LinkedHashMap<>();
        of.forEach((position, value) -> out.put(naming.apply(position), value));
        return out;
    }

    /** The same set, of what {@code naming} calls the positions of each of its blocks. */
    private static <A, B> Set<Sameness.Block<B>> renamedNames(
            Set<Sameness.Block<A>> of, java.util.function.Function<A, B> naming) {
        Set<Sameness.Block<B>> out = new LinkedHashSet<>();
        of.forEach(block -> out.add(block.renamed(naming)));
        return out;
    }

    /**
     * Every one of them holding at once.
     *
     * <p><b>Two orders, and they are about different things.</b> What gets built first is settled
     * by what the readings are ({@link PlanOrder}), so the same readings cost the same and come out
     * exact or wide the same, whichever of them a caller happened to hold first. What is written
     * down about them — which rules went unread at which position, in what order — stays the order
     * they were read in, which is the order somebody wrote them.
     *
     * <p>Told apart because a reader uses them for different things. Folded in the order the
     * reasons should be written in, the work turned on where the readings came from; ordered by
     * content throughout, the reasons came out in an order nothing in the model accounts for and a
     * report would list them by a rule of this compiler's.
     *
     * <p>Still pairwise, so what is built is one meet after another rather than all of them at
     * once. Which pair is cheapest is a question about machines nobody has made, and the difference
     * is what this can answer exactly out of one allowance — not what it answers.
     */
    public static <A> AdmissibleValues<A> metAll(List<AdmissibleValues<A>> readings,
                                                 Allowance<A> sets) {
        if (readings.isEmpty()) {
            throw new IllegalArgumentException("a conjunction of no readings is top, said as top");
        }
        List<AdmissibleValues<A>> schedule = new ArrayList<>(readings);
        schedule.sort(Comparator.comparing(PlanOrder::of));
        AdmissibleValues<A> out = schedule.get(0);
        for (AdmissibleValues<A> each : schedule.subList(1, schedule.size())) {
            out = out.meet(each, sets);
        }
        return out.sayingWhatWasReadInTheOrderOf(readings);
    }

    /**
     * The same reading, with what is said about each position written in the order these were read.
     *
     * <p>Only the writing down. Every reason here is one of theirs or one the meet added, and which
     * they are does not change — what changes is that a reader is shown them in the order the rules
     * were written rather than in the order the work happened to be done.
     */
    private AdmissibleValues<A> sayingWhatWasReadInTheOrderOf(List<AdmissibleValues<A>> read) {
        // Theirs in the order they were read, and then the ones the meet itself added, which no
        // reading arrived with.
        Standing<A> out = standing.inTheOrderOf(read.stream().map(each -> each.standing).toList());
        Set<Sameness.Block<A>> widened = new LinkedHashSet<>();
        read.forEach(each -> widened.addAll(mapped(each.widened, sameness())));
        widened.addAll(this.widened);
        return new AdmissibleValues<>(held, perPosition, out, guaranteed,
                defaultGuaranteed, guaranteedTogether, tangled, widened);
    }

    /** Both readings holding at once. */
    public AdmissibleValues<A> meet(AdmissibleValues<A> other, Allowance<A> sets) {
        // Promising what both sides promise, where both promise their positions together. Where
        // one of them does not, the sets it holds are each true of some value and of no one value
        // at once, and met they would promise a combination neither reading has — so the
        // conjunction promises nothing. See {@link #guaranteedAt}.
        boolean apart = !guaranteedTogether || !other.guaranteedTogether;
        Set<Sameness.Block<A>> gaveUp = new LinkedHashSet<>();
        Held<A> both = met(other, sets, gaveUp);
        // The coordinates the conjunction answers in, which are the two readings' equalities
        // conjoined and closed. Everything said about a block of either side is said about the
        // block of this that holds those positions, so it is carried across before it is composed
        // — read in the coordinates it arrived in, a promise about {@code p} and a promise about
        // {@code r} would stay two promises where the conjunction has one value.
        Sameness<A> heldAsOne = both instanceof Held.Alternatives<A> it
                ? it.commonSameness() : Sameness.discrete();
        return new AdmissibleValues<>(both,
                narrowed(perPosition, other.perPosition, sets, heldAsOne, gaveUp),
                alsoStanding(standing.and(other.standing), gaveUp),
                // Either way what comes out is a promise about whole values, which is why a
                // conjunction never has to say it is not one. Two of them met is one — a value
                // taken from each position of both stands in both readings — and nothing promised
                // is one for want of anything to promise.
                apart ? Map.of() : guaranteedBy(this, other, heldAsOne, sets::meetPromised),
                // Nothing is recorded where this could not be built exactly, because what comes
                // back is nothing promised — and a reader short of a guarantee has been told no
                // more than the truth. The reasons below are about {@link #at}, which is an upper
                // bound and would be saying something false if it widened quietly.
                apart ? ValueSet.NONE
                        : sets.meetPromised(null, defaultGuaranteed, other.defaultGuaranteed).set(),
                true,
                // The intersection of two products is a product, and of anything else it need not
                // be. What each side could not state, the conjunction cannot state either.
                mapped(both(tangled, other.tangled), heldAsOne),
                // And a block the two of them are tangled at is where the intersection can come
                // back wider than the rules are: a pair they refuse between them is one neither
                // per-block meet excludes. Everywhere else the relation is a product and the
                // meet of a product is exact at each of its places, so those blocks keep what
                // they had.
                both(mapped(both(both(widened, other.widened), both(tangled, other.tangled)),
                        heldAsOne), gaveUp));
    }

    /**
     * The reasons, and one more at each position whose exact answer was not built.
     *
     * <p>Beside the widening and never after it. What {@link Sets} hands back at such a position is
     * every value, which is what {@link #at} would say of a position nobody wrote a rule about — so
     * the two have to arrive together or a reading says a rule admits everything and means that it
     * stopped counting.
     */
    private static <A> Standing<A> alsoStanding(Standing<A> standing,
                                                Set<Sameness.Block<A>> gaveUp) {
        Standing<A> out = standing;
        // One entry per block, naming every position of it. What was not built is the one answer
        // those positions share, so the widening is every one of theirs and a reader asking about
        // any of them is asking about the machine that was not made.
        for (Sameness.Block<A> block : gaveUp) {
            out = out.alsoAt(block.members(), UnreadReason.EXACT_VALUES_TOO_COSTLY);
        }
        return out;
    }

    /**
     * The alternatives of a conjunction: every pair of one from each side, the ones nothing stands
     * in dropped.
     *
     * <p>Distributed rather than merged first. A conjunction of a union is the union of the
     * conjunctions, and it is what leaves three of a choice's four pairs empty where the pairs are
     * held apart.
     *
     * <p>Where a side admits nothing the conjunction does, and what it is left holding is the pairs
     * it worked out all the same: a rule stated beside an impossible one is still a rule that was
     * stated, so the values it left a position are values the reading read and are answered with.
     * That is what parts a conjunction from a choice here — see {@link #join}, where nothing an
     * alternative said survives the alternative being one nobody can take.
     */
    private Held<A> met(AdmissibleValues<A> other, Allowance<A> sets,
                        Set<Sameness.Block<A>> gaveUp) {
        if (isBottom() || other.isBottom()) {
            // Emptied by whatever emptied the side that was empty. A conjunction with a side
            // nothing satisfies is empty for that side's reason, and working it out again from
            // what is left would find nothing left to work it out from.
            Set<Sameness.Block<A>> emptied = new LinkedHashSet<>(emptiedBlocks());
            emptied.addAll(other.emptiedBlocks());
            return new Held.Nothing<>(emptied);
        }
        Set<Alternative<A>> live = new LinkedHashSet<>();
        Set<Sameness.Block<A>> emptied = null;
        for (Alternative<A> here : alternatives()) {
            for (Alternative<A> there : other.alternatives()) {
                Alternative.Met<A> both = here.narrowedWith(there, sets, gaveUp);
                if (!both.standsForNothing()) {
                    live.add(both.alternative());
                    continue;
                }
                emptied = alsoEmptied(emptied, both.at());
            }
        }
        if (live.isEmpty()) {
            return new Held.Nothing<>(emptied == null ? Set.of() : emptied);
        }
        Held.Alternatives.Made<A> made = Held.Alternatives.of(live, sets);
        gaveUp.addAll(made.gaveUp());
        return made.held();
    }

    /**
     * Both sides holding at each position, each side missing one standing at ANY.
     *
     * <p>What each position's own rules leave it, which is a fact about the place somebody wrote
     * and stays filed under it. What it is built out of is charged to the block that position is
     * on, since that is the value being reasoned about and the one allowance it has.
     */
    private static <A> Map<A, ValueSet> narrowed(Map<A, ValueSet> these, Map<A, ValueSet> those,
                                                 Allowance<A> sets, Sameness<A> heldAsOne,
                                                 Set<Sameness.Block<A>> gaveUp) {
        Map<A, ValueSet> out = new LinkedHashMap<>(these);
        those.forEach((atom, set) -> out.merge(atom, set, (here, there) -> {
            Sameness.Block<A> block = heldAsOne.blockOf(atom);
            Allowance.Composed made = sets.meet(block, here, there);
            if (made.gaveUp()) {
                gaveUp.add(block);
            }
            return made.set();
        }));
        return out;
    }

    /**
     * Either reading holding.
     *
     * <p>Over the positions both spoke about, since a position one of them left open is one the two
     * of them together leave open.
     *
     * <p>What an alternative nothing could read left open is not said here, and cannot be. Which
     * positions those are turns on which branches anybody can be in, and that is settled over the
     * whole declaration — after this join, out of what it and every other one came to. So it
     * arrives afterwards ({@link #alsoOpenedAt}), from the one walk that knows both the alternatives
     * an author wrote and what became of them.
     */
    public AdmissibleValues<A> join(AdmissibleValues<A> other, Allowance<A> sets) {
        return joining(other, false, sets);
    }

    /**
     * The same reading, also unable to speak for {@code these} because a choice offered an
     * alternative nothing could read.
     *
     * <p>Evidence and not an explanation: {@link #speaksFor} weighs it exactly as it weighs a rule
     * of the positions that went unread, and what a reader is told is the nearer of the two
     * ({@link #whyUnread}). Added once, where the answer is finished — a reading handed on before
     * it would speak for a position it cannot.
     */
    public AdmissibleValues<A> alsoOpenedAt(Set<A> these) {
        return these.isEmpty() ? this
                : new AdmissibleValues<>(held, perPosition, standing.alsoOpenedAt(these),
                        guaranteed, defaultGuaranteed, guaranteedTogether, tangled, widened);
    }

    /**
     * Either reading holding, with the alternatives of the two held apart.
     *
     * <p>The same choice, read without merging what it leaves back into one product. A choice
     * between alternatives written at two positions is a union of two products and no product holds
     * it, so merging is where the relation goes — and it goes unnoticed, because the projections
     * survive a union and it is the next conjunction that spends what was lost.
     *
     * <p>Held apart, the conjunction meets the alternatives pairwise, the pairs nothing stands in
     * drop out, and what is left is what the rules leave. Which is why nothing is owed here: the
     * union of two products is what it is, and this states it rather than approximating it.
     *
     * <p>How many may be held is not this reading's to decide. What bounds them is settled from the
     * clauses before any of them is read ({@code ExpansionCost}), so that precision cannot turn on
     * how a fold was bracketed.
     */
    public AdmissibleValues<A> joinApart(AdmissibleValues<A> other, Allowance<A> sets) {
        return joining(other, true, sets);
    }

    private AdmissibleValues<A> joining(AdmissibleValues<A> other, boolean apart,
                                        Allowance<A> sets) {
        Set<Sameness.Block<A>> gaveUp = new LinkedHashSet<>();
        // An alternative nobody can take leaves the answer to the others. Both being that is a
        // different case: no side speaks for the other, and meeting them would state a conjunction
        // the alternatives never stood in. What the choice admits nothing at is what every
        // alternative admits nothing at, and where there is no such position the choice still
        // admits nothing.
        if (isBottom() && other.isBottom()) {
            // Emptied by what emptied both. A block one branch was left nothing at is one the
            // other may stand at, so what the choice is left nothing at is what neither of them
            // has a value for — the same rule the rest of a dead choice is put together by.
            Set<Sameness.Block<A>> emptied = new LinkedHashSet<>(emptiedBlocks());
            emptied.retainAll(other.emptiedBlocks());
            return new AdmissibleValues<>(new Held.Nothing<>(emptied), emptyInBoth(other),
                    standing.and(other.standing),
                    Map.of(), ValueSet.NONE, true,
                    eachApart(both(tangled, other.tangled)),
                    eachApart(both(widened, other.widened)));
        }
        if (isBottom()) {
            return other;
        }
        if (other.isBottom()) {
            return this;
        }
        // The alternatives of the choice, and with them the coordinates it answers in: what a
        // position holds under a choice is settled by what every alternative holds it as, so the
        // blocks are the ones both sides state and everything said of a coarser one comes apart
        // into them.
        Held<A> held = apart ? apart(other, sets, gaveUp) : merged(other, sets, gaveUp);
        Sameness<A> heldAsOne = held instanceof Held.Alternatives<A> it
                ? it.commonSameness() : Sameness.discrete();
        // What the alternatives guarantee between them, which is what settles whether anything is
        // left for an unread rule to have widened.
        Map<Sameness.Block<A>, ValueSet> covered =
                guaranteedBy(this, other, heldAsOne, sets::joinPromised);
        ValueSet coveredElsewhere =
                sets.joinPromised(null, defaultGuaranteed, other.defaultGuaranteed).set();
        Standing<A> spoiled = standing.and(other.standing);
        // What each rule left standing is kept whole. Whether a position is answerable for it is
        // read off the two ends where the question is asked ({@link #speaksFor}) rather than
        // settled here: what covers a position is an alternative, and a rule stated beside the
        // choice may leave nothing of that alternative.
        Set<Sameness.Block<A>> shapedBy = mapped(promisedAt(), heldAsOne);
        shapedBy.addAll(mapped(other.promisedAt(), heldAsOne));
        // A union of two products alike everywhere but at one place is the product with that place
        // widened, so the promise survives as one about whole values where the alternatives are
        // written at no more than one position between them. Anywhere else the union holds a value
        // from one alternative at one position beside a value from the other at another, which is a
        // combination neither of them stands for.
        //
        // Sufficient and not necessary, and deliberately so. A union is also a product where one
        // alternative promises everything the other does, and where the two differ at only one
        // position however many they are written at — and both of those compare the two boxes a
        // bracketing happened to put together, so a choice of three alternatives answers one way
        // written to the left and another to the right. Measured: both were tried and both broke
        // `AChoiceIsOneConnectiveAndNotATree`. Coarse and the same either way is the trade, and
        // what it costs is a promise this could have kept rather than one it could not.
        return new AdmissibleValues<>(held,
                widenedBy(perPosition, other.perPosition, sets, heldAsOne, gaveUp),
                alsoStanding(spoiled, gaveUp),
                covered, coveredElsewhere,
                guaranteedTogether && other.guaranteedTogether && shapedBy.size() <= 1,
                // Merging a union back into one product loses a relation among the blocks the
                // alternatives are written at, and outside those the two of them agree on
                // everything by saying nothing. Measured the same way the promise above is, and by
                // the same sufficient condition, so a choice at one block keeps both.
                apart || shapedBy.size() <= 1
                        ? mapped(both(tangled, other.tangled), heldAsOne)
                        : both(mapped(both(tangled, other.tangled), heldAsOne), shapedBy),
                // The projections survive whatever the alternatives are written at: the projection
                // of a union is the union of the projections.
                both(both(widened, other.widened), gaveUp));
    }

    /**
     * The one product holding both readings' alternatives, which is what a choice comes to while
     * the alternatives are held one at a time.
     *
     * <p>The keys of both and not of either: a position one side says nothing about is one the
     * choice says nothing about, since a value satisfying that side may hold anything there.
     *
     * <p>A product over what both sides hold as one value. An equality one branch states and the
     * other does not is not the choice's, so the merged product is over the finer of the two
     * relations — kept, the choice would hold positions as one value on the strength of a branch
     * somebody may not be in.
     */
    private Held<A> merged(AdmissibleValues<A> other, Allowance<A> sets,
                           Set<Sameness.Block<A>> gaveUp) {
        Sameness<A> heldAsOne = sameness().common(other.sameness());
        Map<Sameness.Block<A>, ValueSet> out = new LinkedHashMap<>();
        Set<Sameness.Block<A>> named = new LinkedHashSet<>();
        adopted().forEach(atom -> named.add(heldAsOne.blockOf(atom)));
        for (Sameness.Block<A> block : named) {
            A member = block.members().iterator().next();
            ValueSet there = other.at(member);
            if (there.isAny()) {
                // Nothing to put together, and the block is kept where it is more than one
                // position: what those positions being one value says stands whatever they admit.
                if (!block.isOne()) {
                    out.put(block, ValueSet.ANY);
                }
                continue;
            }
            Allowance.Composed made = sets.join(block, at(member), there);
            if (made.gaveUp()) {
                gaveUp.add(block);
            }
            out.put(block, made.set());
        }
        // Live by construction: a join of two sets is empty only where both are, and neither side
        // is bottom here. What it states of two blocks is what every alternative of both states of
        // them — a denial one branch reads is not the choice's, the same way an equality is not.
        return one(new Alternative<>(new Box<>(out),
                apartInEveryAlternative(heldAsOne)
                        .commonWith(other.apartInEveryAlternative(heldAsOne), heldAsOne)));
    }

    /**
     * What every alternative of this reading states to differ, said at {@code finer}.
     *
     * <p>Every one of them and not any: an alternative is one somebody may be in, so a denial one
     * of them states is a rule a value satisfying another need not meet. Read at the blocks the
     * caller answers in, because that is where the answer is filed — a denial stated where two
     * positions were one value says as much of each of them where they are two.
     */
    private Apartness<A> apartInEveryAlternative(Sameness<A> finer) {
        Apartness<A> out = null;
        for (Alternative<A> box : alternatives()) {
            out = out == null ? box.apart().readAt(finer)
                    : out.commonWith(box.apart(), finer);
        }
        return out == null ? Apartness.nothing() : out;
    }

    /**
     * The alternatives of both, which is what the choice leaves where they are held apart.
     *
     * <p>A set, so the same alternative offered twice is one. Neither side is bottom here, so every
     * box of either stands in the choice — nothing is dropped and nothing is merged.
     */
    private Held<A> apart(AdmissibleValues<A> other, Allowance<A> sets,
                          Set<Sameness.Block<A>> gaveUp) {
        Set<Alternative<A>> boxes = new LinkedHashSet<>(alternatives());
        boxes.addAll(other.alternatives());
        Held.Alternatives.Made<A> made = Held.Alternatives.of(boxes, sets);
        gaveUp.addAll(made.gaveUp());
        return made.held();
    }

    /**
     * The blocks of several positions one dropped alternative was left no value at, kept beside
     * what every other dropped alternative was.
     *
     * <p>What every one of them was left nothing at, and not what any of them was. An alternative
     * may be dropped for a reason of its own, so a block named by one of them is not why the
     * reading holds nothing — the same rule a choice between two dead branches is put together by.
     *
     * @param so what the alternatives before this one were left nothing at, or null where this is
     *           the first of them
     */
    static <A> Set<Sameness.Block<A>> alsoEmptied(Set<Sameness.Block<A>> so,
                                                  Map<Sameness.Block<A>, ValueSet> dropped) {
        Set<Sameness.Block<A>> here = new LinkedHashSet<>();
        dropped.forEach((block, set) -> {
            if (!block.isOne() && set.isEmpty()) {
                here.add(block);
            }
        });
        if (so == null) {
            return here;
        }
        so.retainAll(here);
        return so;
    }

    /** The alternatives this holds, which a reading that admits nothing has none of. */
    private Set<Alternative<A>> alternatives() {
        return held instanceof Held.Alternatives<A> it ? it.boxes() : Set.of();
    }

    /** What every alternative of both admits nothing at, which is what a choice between two
     *  impossible ones leaves — and nothing else. Values an alternative nobody can take left a
     *  position are under no obligation from the choice, so they leave with it. */
    private Map<A, ValueSet> emptyInBoth(AdmissibleValues<A> other) {
        Map<A, ValueSet> out = new LinkedHashMap<>();
        adopted().forEach(atom -> {
            if (at(atom).isEmpty() && other.at(atom).isEmpty()) {
                out.put(atom, ValueSet.NONE);
            }
        });
        return out;
    }

    /** The positions this holds an answer about, in the order they were read. */
    private Set<A> adopted() {
        Set<A> out = new LinkedHashSet<>();
        switch (held) {
            case Held.Nothing<A> _ -> out.addAll(perPosition.keySet());
            case Held.Alternatives<A> it -> it.boxes().forEach(box -> out.addAll(box.positions()));
        }
        return out;
    }

    /** One alternative, which is what most readings hold. Nothing is put together, so what each
     *  position holds across the alternatives is what the one of them says. */
    private static <A> Held<A> one(Alternative<A> box) {
        return Held.Alternatives.of(box);
    }

    /** One alternative that states no denial, which is what every reading of a single rule holds
     *  but the one that read a denial. */
    private static <A> Held<A> one(Box<A> box) {
        return one(Alternative.of(box));
    }

    /** Either side holding at each position, which is what both spoke about: a position one of
     *  them says nothing about is one a value satisfying that side may hold anything at. */
    private static <A> Map<A, ValueSet> widenedBy(Map<A, ValueSet> these, Map<A, ValueSet> those,
                                                  Allowance<A> sets, Sameness<A> heldAsOne,
                                                  Set<Sameness.Block<A>> gaveUp) {
        Map<A, ValueSet> out = new LinkedHashMap<>();
        these.forEach((atom, set) -> {
            ValueSet there = those.get(atom);
            if (there != null) {
                Sameness.Block<A> block = heldAsOne.blockOf(atom);
                Allowance.Composed made = sets.join(block, set, there);
                if (made.gaveUp()) {
                    gaveUp.add(block);
                }
                out.put(atom, made.set());
            }
        });
        return out;
    }

    /** Every position of either, in the order they were recorded. */
    private static <A> Set<A> both(Set<A> these, Set<A> those) {
        if (those.isEmpty()) {
            return these;
        }
        if (these.isEmpty()) {
            return those;
        }
        Set<A> out = new LinkedHashSet<>(these);
        out.addAll(those);
        return out;
    }

    /**
     * What both sides guarantee, at every block either of them holds a guarantee for, each side
     * missing one standing at its own default.
     *
     * <p>Said in {@code blocks}, which is the coordinate the answer being built is in. Each side
     * is asked what it promises there, and a side whose own blocks are coarser or finer answers
     * all the same: a promise about the value two positions share is a promise about that value
     * however the side that made it was holding those positions.
     *
     * <p>The keys are the footprint as well as the values — the blocks a rule of these readings
     * reached ({@link #promisedAt}) — so a block either side named is a key here whatever the
     * promise came to. Dropped for coming to the default, which blocks a rule reached would turn
     * on which rules happened to leave one where it started.
     */
    private static <A> Map<Sameness.Block<A>, ValueSet> guaranteedBy(
            AdmissibleValues<A> these, AdmissibleValues<A> those, Sameness<A> heldAsOne,
            Allowance.Composing<A> both) {
        Set<Sameness.Block<A>> named = mapped(these.guaranteed.keySet(), heldAsOne);
        named.addAll(mapped(those.guaranteed.keySet(), heldAsOne));
        Map<Sameness.Block<A>, ValueSet> out = new LinkedHashMap<>();
        // What could not be built exactly comes back as nothing promised, which is what a promise
        // widens to. Nothing is recorded: see {@link #meet}.
        named.forEach(each -> out.put(each,
                both.of(each, these.promisedFor(each), those.promisedFor(each)).set()));
        return out;
    }

    /** What this reading promises the value {@code block} stands for, whichever blocks of its own
     *  it holds those positions in. */
    private ValueSet promisedFor(Sameness.Block<A> block) {
        return guaranteed.getOrDefault(blockOf(block.members().iterator().next()),
                defaultGuaranteed);
    }

    /**
     * The positions an alternative beside this one may have widened.
     *
     * <p>Every position this reading's promise is written at, which is every position a rule of it
     * reached — narrowed there or not. Not the positions it narrows: a branch that read two rules
     * and came out admitting every value at a position narrows nothing there and had rules about it
     * all the same, and which of the two a branch looks like turns on where the brackets of the
     * choice fell. Asked of what a reading is about rather than of what it managed, the answer is
     * the same either way.
     *
     * <p>These are candidates and not the answer. What is recorded against them is that an
     * alternative went unread beside them; whether that is anything the position is answerable for
     * is settled by {@link #speaksFor}, which reads it off the two ends where the question is asked.
     * A position the alternatives cover between them carries a reason nobody is ever shown.
     */
    private Set<Sameness.Block<A>> promisedAt() {
        return guaranteed.keySet();
    }

}
