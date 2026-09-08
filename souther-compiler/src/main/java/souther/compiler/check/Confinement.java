package souther.compiler.check;

import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.regex.Meter;
import souther.compiler.regex.PatternPlan;
import souther.compiler.values.AdmissibleValues;
import souther.compiler.values.Allowance;
import souther.compiler.values.AskedOfEachBlock;
import souther.compiler.values.ConjoinedAdmissibleValues;
import souther.compiler.values.Admits;
import souther.compiler.values.AskedOfARelation;
import souther.compiler.values.StringMachineAnswers;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.Realized;
import souther.compiler.values.Refusal;
import souther.compiler.values.Sameness;
import souther.compiler.values.ValueSet;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Which values the positions of one value may take and where their orders stop, as one answer.
 *
 * <p>Two readings and one question. Which values a position may hold is a set, and where its order
 * stops is a range, and neither has a word for what the other holds — a pattern names no ends and an
 * ordering names no finite set. So a clause reaches whichever of them can say something about it,
 * and whether anything satisfies the rules is about the two of them together.
 *
 * <p><b>Which is why nothing else holds them side by side.</b> Asked of each in turn, the answer is
 * that something exists whenever the reading that was asked had nothing to say about the rules, and
 * that sentence was written in five places. Forbidding the spelling leaves a sixth to be written; so
 * the halves are not separately in hand, and the question has one implementation
 * ({@link #admission}) that no holder can answer around.
 *
 * <p><b>Nothing is admitted where the ranges refuse every alternative, and something is admitted
 * only where they were asked.</b> That asymmetry is the whole of what went wrong. Either half
 * holding nothing leaves the pair nothing, so {@link souther.compiler.values.Emptiness#EMPTY} is sound from either of them;
 * {@link souther.compiler.values.Emptiness#NONEMPTY} is a claim about the pair and can only come out of the walk that was
 * handed the ranges. A reading that answered it from the values alone settled a branch as one
 * somebody can be in before anything asked where its positions stop.
 *
 * <p>What is not promised is that a pair nothing showed empty holds anything. The other components
 * of {@link ConstraintState} stay outside this one. Where the state asks whether they can all hold
 * together, what those components can prove about where each position sits is handed in as a
 * {@link PositionEnvelope.Restrictions} and met into this same walk; they are not copied into
 * either reading, and the alternatives are not walked a second time to ask about them.
 *
 * <p>So this owns only which values a position may take and where its order stops, and the walk
 * over the alternatives that both are asked along. {@link ConstraintState} owns the reduction
 * between that answer and the components beside it, and calling this the whole of a conjunction's
 * satisfiability would still be a claim the reductions in it do not carry.
 *
 * @param <A> what a position is called
 */
sealed interface Confinement<A> {

    /**
     * Whether anything satisfies both readings once {@code outside} has placed the positions, and
     * what showed it where nothing does.
     *
     * <p>The envelope is the question and never a reading of this one. What it holds is what the
     * components beside this can prove about where a position sits, and it is asked here because
     * this is where the alternatives are: a restriction met against what a position admits across
     * all of them is met against a value no alternative stands for, and every one of them has to be
     * asked whole.
     */
    Admission<A> admission(PositionEnvelope.Restrictions<A> outside, StringMachineAnswers machines);

    /** The same, paying for every machine the asking needs. */
    default Admission<A> admission(PositionEnvelope.Restrictions<A> outside) {
        return admission(outside, StringMachineAnswers.NONE);
    }

    /** The same, with nothing placed from outside: what these two readings show on their own. */
    default Admission<A> admission() {
        return admission(PositionEnvelope.Restrictions.nothingSpokenOf());
    }

    /** The same with nothing placed from outside, borrowing what {@code machines} has made. */
    default Admission<A> admission(StringMachineAnswers machines) {
        return admission(PositionEnvelope.Restrictions.nothingSpokenOf(), machines);
    }

    /** Whether anything satisfies both readings. */
    default souther.compiler.values.Emptiness admits() {
        return admission().emptiness();
    }

    /** Whether it is settled that nothing does. */
    default boolean holdsNothing() {
        return admits().isEmpty();
    }

    /**
     * What the two readings leave, and which of them left it so.
     *
     * <p>One computation and two things read off it. Whether anything satisfies the pair and which
     * reading is why it does not are the same walk: the second was worked out a second time from
     * the first's ingredients, and a rule assembled twice is the shape this whole change is about.
     *
     * <p>{@code by} is a fact about the readings and not a sentence about the model. Which proof an
     * author is told is chosen from it in one place, and what is chosen is not decided here.
     *
     * @param how whether these two readings showed it between them, or only once something outside
     *            them said where the positions sit. Two different facts about the model and not two
     *            spellings of one: the first is a declaration whose own values and ends share
     *            nothing, and the second is one whose values are fine until a rule read somewhere
     *            else is asked with them.
     *
     *            <p>Which is why {@code by} is read only where this says the pair showed it alone.
     *            That word names which of the two readings left the pair nothing, and where the
     *            answer needed a restriction from outside, neither of them did
     *
     * @param site where the lack is, and whether it is a lack at each of some blocks or a lack
     *             about several of them together — see {@link Refusal}
     */
    record Admission<A>(souther.compiler.values.Emptiness emptiness, EmptyBy by, Refusal<A> site, Shown how) {

        /** The same, where what was refused is places rather than values several of them share. */
        static <A> Admission<A> at(souther.compiler.values.Emptiness emptiness, EmptyBy by, Set<A> positions, Shown how) {
            Set<Sameness.Block<A>> blocks = new LinkedHashSet<>();
            positions.forEach(each -> blocks.add(Sameness.Block.of(each)));
            return new Admission<>(emptiness, by, Refusal.atEachOf(blocks), how);
        }

        /** The same, at each of these blocks. */
        static <A> Admission<A> eachOf(souther.compiler.values.Emptiness emptiness, EmptyBy by,
                                       Set<Sameness.Block<A>> blocks, Shown how) {
            return new Admission<>(emptiness, by, Refusal.atEachOf(blocks), how);
        }

        /**
         * Something may satisfy the pair, so nothing emptied it.
         *
         * <p>Which is why the settled answer that nothing is admitted is refused rather than left
         * to whoever calls this. The three words beside the verdict say a lack was shown by
         * nothing, at no position, out of the readings; with that verdict they are a proof no walk
         * could have reached, and a choice realised from it answers for a branch nothing was shown
         * about. What a pair shown empty is shown by is what showed the readings it was composed of
         * — {@link #bothShown} for two of them, and the walk itself where one reading is the whole
         * of it.
         */
        static <A> Admission<A> left(souther.compiler.values.Emptiness emptiness) {
            if (emptiness.isEmpty()) {
                throw new IllegalArgumentException(
                        "a pair nothing emptied is not a pair shown to admit nothing");
            }
            return new Admission<>(emptiness, EmptyBy.NOTHING_SHOWN, Refusal.nowhere(),
                    Shown.BY_THE_READINGS);
        }

        /** Whether it is settled that nothing satisfies what was asked. */
        boolean holdsNothing() {
            return emptiness.isEmpty();
        }

        /** Whether these two readings are the whole of what showed it. */
        boolean byTheReadings() {
            return how == Shown.BY_THE_READINGS;
        }

        /**
         * Two readings both shown to hold nothing, as one.
         *
         * <p>What emptied them is what emptied both, and where is where both were refused: an
         * alternative of one and an alternative of the other are refused at a position only if that
         * position is in each of their answers. Where they disagree about either, no one of them
         * speaks for the pair — the first found would settle the proof by the order the operands
         * were written in.
         */
        static <A> Admission<A> bothShown(Admission<A> one, Admission<A> other) {
            // And shown by the readings alone only where both of them were, which is the rule
            // however these two were shown. Nothing hands a branch's fate a restriction from
            // outside today ({@link StatedByClauses.Reading}), so both of these are the readings'
            // own; said the other way, this would be a fact about the pair that neither half is.
            return new Admission<>(souther.compiler.values.Emptiness.EMPTY,
                    one.by == other.by ? one.by : EmptyBy.RULES_TOGETHER,
                    Refusal.shownByBoth(one.site, other.site),
                    one.byTheReadings() && other.byTheReadings()
                            ? Shown.BY_THE_READINGS : Shown.ONCE_THE_POSITIONS_ARE_PLACED);
        }
    }


    /**
     * What an emptiness was shown by: these two readings, or these two asked with what stands
     * outside them.
     *
     * <p>Not a second kind of proof for every kind there already is. A component added beside these
     * readings does not multiply this: what it can say about a position arrives as one more
     * restriction in the envelope, and every emptiness that needed any of them is the second of
     * these.
     */
    enum Shown {

        /** The values and the ends of one declaration, between them. */
        BY_THE_READINGS,

        /** Those, asked where something outside them requires the positions to be. */
        ONCE_THE_POSITIONS_ARE_PLACED
    }

    /** Which reading left the pair nothing. */
    enum EmptyBy {

        /** Nothing did: something may satisfy the pair, or nothing showed that nothing does. */
        NOTHING_SHOWN,

        /** The values admit nothing, whatever the ranges hold. */
        VALUES,

        /**
         * Positions the rules hold as one value are left no value they can all hold.
         *
         * <p>Nearer than {@link #VALUES}, and told from it by there being a place to name. Each of
         * those positions is left something on its own; what has nothing is the one value they are
         * said to be, so the lack is theirs together and the proof says so.
         */
        POSITIONS_HELD_AS_ONE,

        /**
         * Positions the rules state to hold different values are left no way of differing.
         *
         * <p>Not a lack at any of them, which is what tells it from every other arm here. Each of
         * those blocks is left values of its own and what has nothing is an assignment to all of
         * them at once — so the proof is about them together, and what it is read off is the
         * argument that refused them rather than a set of places.
         */
        POSITIONS_HELD_APART,

        /** Some position's order holds no value, whatever the values admit. */
        ORDER,

        /** Each of them holds something and no alternative has a value in both. */
        SET_AND_RANGE,

        /** Two readings shown empty in ways that are not one way. */
        RULES_TOGETHER
    }

    /** What each position is ordered on, which is what a range and a set of values are put
     *  together over. */
    Map<A, Carrier> carriers();

    /**
     * The one implementation of the question, whatever the values are held as.
     *
     * <p>The ranges reach every position of every alternative, so what comes back is about the pair
     * — which is what makes {@link souther.compiler.values.Emptiness#NONEMPTY} sayable at all. A holder whose values are
     * still descriptions answers as much of it as needs no machine and leaves the rest open; a
     * holder whose values are sets answers all of it.
     *
     * <p>A position on no order is one nothing here can refuse: what it admits is a set and there is
     * no range to share a value with.
     */
    static <A> Admission<A> admission(OrderedIntervals<A> ordered, Map<A, Carrier> carriers,
                                      PositionEnvelope.Restrictions<A> outside,
                                      Admitting<A> admitting,
                                      Refusing<A> refused,
                                      Refusal<A> alreadyShown, StringMachineAnswers machines) {
        // The ends holding a position nothing, which is that reading's own answer whatever else
        // places the position: a reading left with no range at all names none, and where it names
        // one, that is where the lack is.
        if (ordered.isBottom()) {
            return Admission.at(souther.compiler.values.Emptiness.EMPTY, EmptyBy.ORDER, ordered.holdingNothing(),
                    Shown.BY_THE_READINGS);
        }
        // A meter of this asking, spent on every question asked in it. What may be built to decide
        // whether a declaration has a value cannot come out of a position's allowance: the same
        // rules would then be decided differently depending on what the readings before them had
        // already built.
        Meter meter = PatternPlan.Budget.OF_WHAT_A_SET_AND_A_RANGE_SHARE.meter();
        AskedOfEachBlock<A> byTheReadings = asking(carriers, ordered::at, meter, machines);
        AskedOfEachBlock<A> narrowed = asking(carriers, position ->
                ordered.at(position).meet(outside.at(position).interval()), meter, machines);
        // What a relation between two blocks comes to, which is settled against what each of them
        // is left once everything placing its positions has been met with it. Built here for the
        // same reason the question above is: the values and the ranges are put together in one
        // place, and a relation read against the values alone would answer one way for a block
        // pinned to one value by a written value and another for a block pinned to one by its ends.
        AskedOfARelation<A> relating = relating(carriers, position ->
                ordered.at(position).meet(outside.at(position).interval()));
        AskedOfARelation<A> byTheReadingsRelating = relating(carriers, ordered::at);
        // And a block nothing outside places is the question above and not another one, so it is
        // answered once however many askings reach it. That is what lets the two share a meter: a
        // machine a question builds is built for the block it is about, and asking about the same
        // block again is reading what it came to. Spent per asking instead, what an answer costs
        // would turn on how many ways it had to be asked to be written down — and worse, the
        // second asking of one question would be answered out of what the first had left.
        AskedOfEachBlock<A> placed = outside.saysNothing() ? byTheReadings
                : (block, set) -> places(block, outside)
                        ? narrowed.of(block, set) : byTheReadings.of(block, set);
        // One walk, and it is asked where the positions are: what a reading leaves is what it
        // leaves once everything that places its positions has been met with it, and a walk per
        // asking would be an alternative visited twice by two questions that have to agree.
        souther.compiler.values.Emptiness said = admitting.of(placed, relating);
        if (!said.isEmpty()) {
            // And a position with nowhere to be once everything placing it is met, which is a lack
            // the alternatives never hear about: a position no alternative names is not one they
            // are asked about, so where what is required of it does not reach where its own ends
            // leave it, nothing else here will say so.
            //
            // Asked after them and not before. What one of these readings shows on its own is what
            // an author is told, and a scan that ran first would answer for a declaration whose
            // values already left nothing — sending them to a position placed from outside while
            // the rules they wrote about another one are what cannot hold.
            Set<A> nowhere = new LinkedHashSet<>();
            carriers.keySet().forEach(position -> {
                if (ordered.at(position).meet(outside.at(position).interval()).holdsNothing()) {
                    nowhere.add(position);
                }
            });
            return nowhere.isEmpty() ? Admission.left(said)
                    : Admission.at(souther.compiler.values.Emptiness.EMPTY, EmptyBy.ORDER, nowhere,
                            Shown.ONCE_THE_POSITIONS_ARE_PLACED);
        }
        // What showed it, which is a second question and is asked where a proof is written rather
        // than where the answer is reached. These readings show it on their own or they do not, and
        // where they do, that is what an author is told — read off the walk that was answered, a
        // pair whose own set and range share no value would be reported against bounds derived
        // somewhere else, which is true and is not what they wrote.
        Shown how = outside.saysNothing()
                || admitting.of(byTheReadings, byTheReadingsRelating).isEmpty()
                ? Shown.BY_THE_READINGS : Shown.ONCE_THE_POSITIONS_ARE_PLACED;
        // The values holding no alternative at all is the values' own answer, and asking anything
        // of the ranges would not have changed it. Told apart here rather than by a second reader
        // reassembling the same three facts — and said to be the readings' whichever asking reached
        // it, since a proof that consults no range is one no placing of a position took part in.
        //
        // The blocks it was asked at, which is what the question was about. A block of several
        // positions is refused as one value and not as each of them: {@code p == r && p < "b" && r
        // > "y"} leaves each position a range with something in it, and what has nothing is the
        // range the two of them share.
        //
        // Asked of the question that showed it, so that the blocks named are the ones refused by
        // what the proof says refused them.
        Refusal<A> where = how == Shown.BY_THE_READINGS
                ? refused.of(byTheReadings, byTheReadingsRelating)
                : refused.of(placed, relating);
        // A lack about several blocks together, which is nearer than either of the answers below
        // and is not one of them: each of those blocks is left values of its own, and what has
        // nothing is an assignment to all of them at once.
        if (where.nearest() == Refusal.Nearest.OF_THEM_TOGETHER) {
            return new Admission<>(souther.compiler.values.Emptiness.EMPTY, EmptyBy.POSITIONS_HELD_APART, where, how);
        }
        // The values holding no alternative at all is the values' own answer, and asking anything
        // of the ranges would not have changed it. Told apart here rather than by a second reader
        // reassembling the same three facts — and said to be the readings' whichever asking reached
        // it, since a proof that consults no range is one no placing of a position took part in.
        if (admitting.of((_, _) -> souther.compiler.values.Emptiness.NONEMPTY,
                relating(carriers, _ -> OrderedInterval.OPEN)).isEmpty()) {
            // With where the reading was refused, where that is nearer than the general answer.
            // Each of those places holds something on its own, so "the values admit nothing" is
            // true of the declaration and says less than what was shown — and which of the two
            // nearer sentences it is is the refusal's to say and not a second reading of it.
            return new Admission<>(souther.compiler.values.Emptiness.EMPTY,
                    switch (alreadyShown.nearest()) {
                        case NOWHERE -> EmptyBy.VALUES;
                        case AT_EACH_OF -> EmptyBy.POSITIONS_HELD_AS_ONE;
                        case OF_THEM_TOGETHER -> EmptyBy.POSITIONS_HELD_APART;
                    }, alreadyShown, Shown.BY_THE_READINGS);
        }
        return new Admission<>(souther.compiler.values.Emptiness.EMPTY, EmptyBy.SET_AND_RANGE, where, how);
    }

    /**
     * What the denials between an alternative's blocks come to, where {@code sits} puts its
     * positions.
     *
     * <p>The relation asked against what each of its blocks is left, which is the values that
     * block admits met with where its positions sit. Which is why it is built beside the question
     * about one block rather than inside the reading that holds the relation: the reading holds
     * the values and nothing else, and a denial settled against those alone would answer one way
     * for a block a written value pins and another for a block its ends pin.
     */
    private static <A> AskedOfARelation<A> relating(Map<A, Carrier> carriers,
                                                    Function<A, OrderedInterval> sits) {
        return (apart, product) -> apart.reduce((block, atMost) ->
                admits(carriers, sits, block, product.get(block), atMost));
    }

    /** Which values {@code block} is left, where {@code sits} puts its positions. */
    private static <A> Admits admits(Map<A, Carrier> carriers, Function<A, OrderedInterval> sits,
                                     Sameness.Block<A> block, ValueSet set, int atMost) {
        Placed placed = placedAt(carriers, sits, block);
        return Carrier.leftAt(placed.carrier(), set, placed.within(), atMost);
    }

    /** The two questions one walk over the alternatives is asked: what each block leaves, and what
     *  the denials between them come to. */
    @FunctionalInterface
    interface Admitting<A> {
        souther.compiler.values.Emptiness of(AskedOfEachBlock<A> blocks, AskedOfARelation<A> relation);
    }

    /** The same two questions, asked to write down where a reading was left nothing. */
    @FunctionalInterface
    interface Refusing<A> {
        Refusal<A> of(AskedOfEachBlock<A> blocks, AskedOfARelation<A> relation);
    }

    /**
     * What one block of an alternative leaves, against one placing of the positions in it.
     *
     * <p>The question and not the walk. Which alternatives there are belongs to whoever holds them
     * ({@link AskedOfEachBlock}), and this is the other half: what a block comes to once the values
     * it admits are met with where its positions are.
     *
     * @param sits where a position is, which is where its own reading of the ends leaves it or that
     *             met with what is required of it elsewhere
     */
    private static <A> AskedOfEachBlock<A> asking(Map<A, Carrier> carriers,
                                                 Function<A, OrderedInterval> sits, Meter meter,
                                                 StringMachineAnswers machines) {
        // What each block this is asked about came to, so that it is worked out once. A question
        // put to a block is a machine built for it, and the walks that read an answer back ask
        // about the very blocks the walk that reached it did — by the set in hand and not by what
        // a set is equal to, since two sets that are equal are two answers only if somebody built
        // them twice.
        Map<Sameness.Block<A>, Map<ValueSet, souther.compiler.values.Emptiness>> asked = new LinkedHashMap<>();
        return (block, set) -> asked
                .computeIfAbsent(block, _ -> new IdentityHashMap<>())
                .computeIfAbsent(set, it -> answered(carriers, sits, meter, machines, block, it));
    }

    /** Whether {@code block} admits anything where {@code sits} puts its positions. */
    private static <A> souther.compiler.values.Emptiness answered(Map<A, Carrier> carriers,
                                          Function<A, OrderedInterval> sits, Meter meter,
                                          StringMachineAnswers machines,
                                          Sameness.Block<A> block, ValueSet set) {
        Placed placed = placedAt(carriers, sits, block);
        return placed.carrier() == null ? souther.compiler.values.Emptiness.NONEMPTY
                : placed.carrier().meets(set, placed.within(), meter, machines);
    }

    /**
     * What a block is ordered on and where its positions leave it.
     *
     * <p>Worked out once, because two questions are asked of a block against it — whether it admits
     * anything, and what a denial between it and another comes to — and both need the same two
     * facts. Written at each of them, the assertion below is on one and the other answers a block
     * this compiler is wrong about without saying so.
     *
     * <p>The range is every one of the positions' at once. Positions an alternative holds as one
     * value have one value between them, so asking one member would answer {@code p == r && p < d1
     * && r > d2} against half of what the rules say and the pair would come back holding something.
     */
    private static <A> Placed placedAt(Map<A, Carrier> carriers, Function<A, OrderedInterval> sits,
                                       Sameness.Block<A> block) {
        Carrier carrier = null;
        OrderedInterval within = OrderedInterval.OPEN;
        for (A position : block.members()) {
            Carrier here = carriers.get(position);
            // One carrier, and an assertion because it is about this compiler rather than about any
            // model: a rule holding two positions as one value is one an equality between them
            // typed, and an equality types only where the two are of one type. A block whose
            // members disagreed would be answered by whichever of them the members happen to be
            // read in the order of, which is a fact about how they are spelled.
            assert carrier == null || here == null || carrier.equals(here)
                    : "positions held as one value are ordered on " + carrier + " and " + here;
            if (here != null) {
                carrier = here;
            }
            within = within.meet(sits.apply(position));
        }
        return new Placed(carrier, within);
    }

    /** A block's order and the range its positions leave it, the order being null where nothing
     *  orders it. */
    record Placed(Carrier carrier, OrderedInterval within) {}

    /** Whether anything outside these readings places a position of {@code block}. */
    private static <A> boolean places(Sameness.Block<A> block,
                                      PositionEnvelope.Restrictions<A> outside) {
        return block.members().stream().anyMatch(each -> !outside.at(each).saysNothing());
    }

    /** What showed a conjunction of two readings empty, where either of them was. */
    static <A> Admission<A> eitherShown(Admission<A> one, Admission<A> other) {
        if (one.emptiness() != souther.compiler.values.Emptiness.EMPTY) {
            return other.emptiness() == souther.compiler.values.Emptiness.EMPTY ? other : null;
        }
        return other.emptiness() == souther.compiler.values.Emptiness.EMPTY ? Admission.bothShown(one, other) : one;
    }

    /** What each position is ordered on, both tables put together. */
    static <A> Map<A, Carrier> both(Map<A, Carrier> these, Map<A, Carrier> those) {
        if (these.isEmpty()) {
            return those;
        }
        if (those.isEmpty()) {
            return these;
        }
        Map<A, Carrier> out = new LinkedHashMap<>(these);
        out.putAll(those);
        return Collections.unmodifiableMap(out);
    }

    /**
     * A reading whose values are still descriptions, beside where its positions stop.
     *
     * <p>What a position admits is a plan and not a set, so a description nobody has worked out is
     * one this cannot ask the ranges about — and it says so rather than answering from the half it
     * can read. Nothing here builds: the budget the answer is bounded by is spent where the values
     * are worked out, and a reading that spent it to settle a branch would make what a declaration
     * costs depend on how its author bracketed it.
     */
    final class Planned<A> implements Confinement<A> {

        private final PlannedValues<A> values;
        private final OrderedIntervals<A> ordered;
        private final Map<A, Carrier> carriers;
        /**
         * What already showed this holds nothing, or null where nothing has.
         *
         * <p>A branch taken as holding nothing keeps no rules to be asked again, so what it was
         * shown by is only knowable while it is being dropped. Carried from there, the proof
         * survives the join that drops it and reaches the declaration's refusal; worked out again
         * afterwards, the answer would be that the values admit nothing — which is true, and is the
         * general form of what was actually shown.
         */
        private final Admission<A> shown;

        Planned(PlannedValues<A> values, OrderedIntervals<A> ordered, Map<A, Carrier> carriers) {
            this(values, ordered, carriers, null);
        }

        private Planned(PlannedValues<A> values, OrderedIntervals<A> ordered,
                        Map<A, Carrier> carriers, Admission<A> shown) {
            this.values = values;
            this.ordered = ordered;
            this.carriers = Collections.unmodifiableMap(new LinkedHashMap<>(carriers));
            this.shown = shown;
        }

        /** Nothing read, so nothing ruled out. */
        static <A> Planned<A> top(Map<A, Carrier> carriers) {
            return new Planned<>(PlannedValues.top(), OrderedIntervals.top(), carriers);
        }

        PlannedValues<A> values() {
            return values;
        }

        /**
         * Where this reading's orders stop, for a caller asking a question only that reading
         * answers.
         *
         * <p>Beside {@link #values()} and on the same terms. Neither is handed out for the question
         * this type owns — whether anything satisfies the pair is {@link #admission} and is not
         * askable of one half — and a caller comparing what one alternative leaves against what the
         * choice leaves is asking each reading about its own, which is a question the other has no
         * word for.
         */
        OrderedIntervals<A> ordered() {
            return ordered;
        }

        @Override
        public Map<A, Carrier> carriers() {
            return carriers;
        }

        @Override
        public Admission<A> admission(PositionEnvelope.Restrictions<A> outside,
                                      StringMachineAnswers machines) {
            return shown != null ? shown : Confinement.admission(ordered, carriers, outside,
                    // The relation is not asked on this side: what a denial comes to is settled
                    // against the values its blocks are left, and those are descriptions here. The
                    // walk says so of each alternative that carries one.
                    (asked, _) -> values.anyAlternativeAdmits(asked),
                    (asked, _) -> values.refusedInEveryAlternativeAt(asked),
                    values.refusedBy(), machines);
        }

        /**
         * The same question out of what something has already built.
         *
         * <p>For a caller deciding whether to keep a branch while a reading is still being put
         * together. What it may spend is the answer's own allowance, so what is asked is what is
         * established and a branch nothing established is kept.
         */
        souther.compiler.values.Emptiness alreadyEstablished(Allowance<A> by) {
            souther.compiler.values.Emptiness said = admits();
            if (said.isDecided()) {
                return said;
            }
            return values.holdsNothingAsBuilt(by) ? souther.compiler.values.Emptiness.EMPTY : souther.compiler.values.Emptiness.UNDECIDED;
        }

        /**
         * Both readings holding at once.
         *
         * <p>A conjunction with a side that holds nothing holds nothing, and by what that side was
         * shown by — so the proof crosses the meet rather than being worked out again from a
         * reading that has nothing left in it to be asked.
         */
        Planned<A> meet(Planned<A> other) {
            return new Planned<>(values.meet(other.values), ordered.meet(other.ordered),
                    Confinement.both(carriers, other.carriers),
                    eitherShown(admission(), other.admission()));
        }

        /** Either of them, as each reading says it. Both sides are ones somebody can be in, so
         *  nothing has shown the choice empty. */
        Planned<A> either(Planned<A> other, boolean apart) {
            return new Planned<>(
                    apart ? values.joinLiveApart(other.values) : values.joinLive(other.values),
                    ordered.joinLive(other.ordered),
                    Confinement.both(carriers, other.carriers));
        }

        /**
         * Two branches neither of which anybody can be in, said as that.
         *
         * <p>What the choice was shown by is what both of them were shown by, and where is where
         * both were refused. Neither speaks for the other: alternatives refused at different
         * positions leave a choice no position is why, which is what the proof has to say.
         *
         * <p>Each language is asked what two dead branches leave it, of the readings as they were
         * read. Neither is asked whether they are dead — a language may be perfectly happy with a
         * branch the other refused, and asked through the entry for a choice that stands it would
         * answer for that branch with the ends it read. Neither is told, either: what showed the
         * choice empty arrives as {@code shown} and is written into the whole here, and each
         * language names a position only where every alternative of that language left it empty,
         * which is a question about what it read and not about what became of the branch.
         */
        Planned<A> bothDead(Planned<A> other, Admission<A> shown) {
            return new Planned<>(values.bothDead(other.values),
                    ordered.bothDead(other.ordered),
                    Confinement.both(carriers, other.carriers), shown);
        }

        /** This, holding what working it out could not build. */
        Planned<A> alsoStanding(souther.compiler.values.Standing<A> standing) {
            return new Planned<>(values.alsoStanding(standing), ordered, carriers, shown);
        }

        /** The values worked out, under {@code by}, and the same ranges beside them. */
        Worked<A> resolve(Allowance<A> by) {
            return new Worked<>(values.resolve(by), ordered, carriers, shown);
        }

        @Override
        public String toString() {
            return values + " within " + ordered;
        }
    }

    /**
     * A reading whose values have been worked out, beside where its positions stop.
     *
     * <p>The sets are in hand, so every alternative can be asked where its positions stop and the
     * answer is settled either way.
     *
     * <p>Nothing here composes two readings. Every connective a clause is read by is spent by the
     * time one of these exists — the conjunction that outlives it is the one between declarations,
     * and a caller relating two holds {@link Conjoined}. What is left to do to one of these is ask
     * it, and tell it what a choice already settled left open.
     */
    final class Worked<A> implements Confinement<A> {

        private final Realized<A> made;
        private final OrderedIntervals<A> ordered;
        private final Map<A, Carrier> carriers;
        /** What already showed this holds nothing — see {@link Planned#shown}. */
        private final Admission<A> shown;

        Worked(Realized<A> made, OrderedIntervals<A> ordered, Map<A, Carrier> carriers,
               Admission<A> shown) {
            this.made = made;
            this.ordered = ordered;
            this.carriers = Collections.unmodifiableMap(new LinkedHashMap<>(carriers));
            this.shown = shown;
        }

        Realized<A> made() {
            return made;
        }

        AdmissibleValues<A> values() {
            return made.values();
        }

        /**
         * The same answer, unable to speak for {@code these} because a choice offered an
         * alternative nothing could read.
         *
         * <p>Which positions those are turns on which branches anybody can be in, so it is known
         * only once this is — see {@link AdmissibleValues#alsoOpenedAt}. What comes back is what a
         * reader is handed; nothing reads how wide a position is off the answer before it.
         */
        Worked<A> alsoOpenedAt(Set<A> these) {
            return these.isEmpty() ? this
                    : new Worked<>(made.alsoOpenedAt(these), ordered, carriers, shown);
        }

        @Override
        public Map<A, Carrier> carriers() {
            return carriers;
        }

        @Override
        public Admission<A> admission(PositionEnvelope.Restrictions<A> outside,
                                      StringMachineAnswers machines) {
            if (shown != null) {
                return shown;
            }
            Admission<A> said = Confinement.admission(ordered, carriers, outside,
                    made.values()::anyAlternativeAdmits,
                    made.values()::refusedInEveryAlternativeAt,
                    made.values().refusedBy(), machines);
            // A position nobody could build is one what stands there is wider than the rules, so a
            // pair the ranges did not refuse may still hold nothing. Settled empty is settled all
            // the same: a narrower reading refuses no less.
            return said.emptiness() == souther.compiler.values.Emptiness.NONEMPTY && !made.unbuilt().isEmpty()
                    ? Admission.left(souther.compiler.values.Emptiness.UNDECIDED) : said;
        }

        /** The positions the order leaves no value at, for a reader writing down where. */
        Set<A> holdingNothing() {
            return ordered.holdingNothing();
        }

        @Override
        public String toString() {
            return made.values() + " within " + ordered;
        }
    }

    /**
     * The readings of several declarations' clauses, conjoined, beside where their orders stop.
     *
     * <p>What a caller relating values holds. The conjunction is kept factored, so an alternative of
     * it is one alternative of each factor side by side and the walk asks each factor's own.
     */
    final class Conjoined<A> implements Confinement<A> {

        private final ConjoinedAdmissibleValues<A> values;
        private final OrderedIntervals<A> ordered;
        private final Map<A, Carrier> carriers;
        /** What already showed this holds nothing — see {@link Planned#shown}. */
        private final Admission<A> shown;

        Conjoined(ConjoinedAdmissibleValues<A> values, OrderedIntervals<A> ordered,
                  Map<A, Carrier> carriers) {
            this(values, ordered, carriers, null);
        }

        private Conjoined(ConjoinedAdmissibleValues<A> values, OrderedIntervals<A> ordered,
                          Map<A, Carrier> carriers, Admission<A> shown) {
            this.values = values;
            this.ordered = ordered;
            this.carriers = Collections.unmodifiableMap(new LinkedHashMap<>(carriers));
            this.shown = shown;
        }

        /** Nothing read, so nothing ruled out. */
        static <A> Conjoined<A> top() {
            return new Conjoined<>(ConjoinedAdmissibleValues.top(), OrderedIntervals.top(),
                    Map.of());
        }

        ConjoinedAdmissibleValues<A> values() {
            return values;
        }

        @Override
        public Map<A, Carrier> carriers() {
            return carriers;
        }

        @Override
        public Admission<A> admission(PositionEnvelope.Restrictions<A> outside,
                                      StringMachineAnswers machines) {
            return shown != null ? shown : Confinement.admission(ordered, carriers, outside,
                    values::anyAlternativeAdmits, values::refusedInEveryAlternativeAt,
                    values.refusedBy(), machines);
        }

        /** The positions the order leaves no value at. */
        Set<A> holdingNothing() {
            return ordered.holdingNothing();
        }

        /** Which values may stand at one position. */
        ValueSet at(A position) {
            return values.at(position);
        }

        /**
         * Both conjunctions holding at once, in both languages.
         *
         * <p>What the two readings come to where their vocabularies meet is a set neither of them
         * holds, so it belongs to the answer being built out of the pair — and {@code sets} is what
         * that answer may spend, handed over by whoever is building it.
         */
        Conjoined<A> meet(Conjoined<A> other, Allowance<A> sets) {
            return new Conjoined<>(values.meet(other.values, sets), ordered.meet(other.ordered),
                    Confinement.both(carriers, other.carriers),
                    eitherShown(admission(), other.admission()));
        }

        /** The same rules about the same positions, under the names {@code naming} gives them. */
        <B> Conjoined<B> renamed(Function<A, B> naming) {
            Map<B, Carrier> out = new LinkedHashMap<>();
            carriers.forEach((position, carrier) -> out.put(naming.apply(position), carrier));
            Admission<B> said = shown == null ? null
                    : new Admission<>(shown.emptiness(), shown.by(),
                            shown.site().renamed(naming), shown.how());
            return new Conjoined<>(values.renamed(naming), ordered.renamed(naming), out, said);
        }

        /** The same, with {@code bounded} taken as holding of the positions it bounds, on the
         *  orders {@code on} says they are counted by. */
        Conjoined<A> taking(OrderedIntervals<A> bounded, Map<A, Carrier> on) {
            return new Conjoined<>(values, ordered.meet(bounded), Confinement.both(carriers, on),
                    shown);
        }

        /**
         * The same, with one declaration's reading taken in — both languages at once.
         *
         * <p>One call and not two. What a declaration's clauses left is one answer with two faces,
         * and taking it in a face at a time is what let a caller hold half of it: the values met
         * here and the ranges met somewhere else, with a state in between that says a value exists
         * because the half it was given does.
         *
         * <p>Met with what nothing read leaves rather than assigned, which is not the same answer. A
         * reading a choice reached across two positions promises nothing about whole values, and a
         * conjunction with one of those promises nothing anywhere.
         */
        Conjoined<A> taking(Worked<A> read, Allowance<A> sets) {
            return taking(read, sets, StringMachineAnswers.NONE);
        }

        /** The same, borrowing what {@code machines} has already made where what either side
         *  was shown empty by takes a machine. */
        Conjoined<A> taking(Worked<A> read, Allowance<A> sets, StringMachineAnswers machines) {
            // The allowance the reading was worked out under, and not a fresh one: what a later
            // reader builds out of it is more of the same answer at the same positions.
            //
            // Said once, and what stands here until it is said is what nothing read leaves. Saying
            // it twice is this compiler disagreeing with itself rather than anything a model says,
            // which is why it is an assertion. With assertions off the second reading is left out
            // and what was already read stands, so the failure cannot make an answer more precise
            // or say anything no reading said — whatever the second reading is. Settled here, in
            // one line, rather than out of what each part of a state does with a reading met over
            // another: the values would forget the first, the ranges would keep both, and the
            // carrier of a position both name would be the later of the two. None of those is the
            // same direction, and a reason resting on all three would be as good as the next edit
            // to any of them.
            if (values.hasReadings()) {
                assert false : "the values of a state are read once, and these were read over "
                        + values;
                return this;
            }
            return new Conjoined<>(
                    ConjoinedAdmissibleValues.of(
                            AdmissibleValues.<A>top().meet(read.values(), sets)),
                    ordered.meet(read.ordered), Confinement.both(carriers, read.carriers()),
                    // What the reading was already shown empty by, which is a fact about the rules
                    // and travels with them. Left behind, a declaration refused because two of its
                    // branches share no value between their sets and their ranges would be reported
                    // as one whose values admit nothing, which is what dropping the branches left.
                    eitherShown(admission(machines), read.admission(machines)));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Conjoined<?> it && values.equals(it.values)
                    && ordered.equals(it.ordered) && carriers.equals(it.carriers)
                    && Objects.equals(shown, it.shown);
        }

        @Override
        public int hashCode() {
            return Objects.hash(values, ordered, carriers, shown);
        }

        @Override
        public String toString() {
            return values + " within " + ordered;
        }
    }
}
