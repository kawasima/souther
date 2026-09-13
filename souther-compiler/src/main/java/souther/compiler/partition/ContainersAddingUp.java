package souther.compiler.partition;

import souther.compiler.check.Carrier;
import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.Shape;
import souther.compiler.check.NumericMeasures;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.BoundaryDomain;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.Requirements;
import souther.compiler.inputs.SearchRegion;
import souther.compiler.inputs.TermOrders;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.CountDomain;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Place;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Containers filled so that the occurrences of a path inside them come to a number.
 *
 * <p><b>One question, and the two ways a total is written are cases of it.</b> A total of a list at
 * a position adds up what the list holds, and a total over a run adds up what stands at a path
 * inside each element — the container is the same thing either way, and what differs is how far
 * down the number is. Written as two, the second would be the first's algorithm with an extra step,
 * free to disagree with it about how many elements a total is worth.
 *
 * <p><b>The elements are chosen against what the rules leave them.</b> How many the container may
 * hold is what its own type declares and what the record holding it says, tightened together; what
 * one occurrence may be is what the region leaves the number at that path. A container built without
 * asking either is one the decoder refuses, and the point it was built for then reads as an edge
 * every value was refused at rather than as one this did not fill.
 *
 * <p><b>And a range is not an existence proof.</b> That {@code k} elements between {@code lo} and
 * {@code hi} could reach the total is arithmetic on the ends, and the values between them are the
 * carrier's rather than every number in the interval. So a count the ends admit is a count to try,
 * and one whose decomposition does not land on the carrier is stepped over — the next count up is
 * asked the same question. Read as a proof, the first hole in an order would be a total nothing
 * composes a row for.
 *
 * <p>Nothing here decides whether the model admits what was built. Every element is a whole value
 * composed under its own type's rules, and the row it goes into is put through the module's own
 * decoder like any other — which is what makes the offer a row rather than a shape carrying a
 * number.
 */
final class ContainersAddingUp {

    /**
     * Containers whose occurrences of {@code target}'s path come to {@code answer}, or why there are
     * none.
     *
     * @param container what the target's root is declared to hold, which is what is written there
     * @param orders    the ends the term stands on. The elements are written on the order the answer
     *                  is measured on, which is the order the reading adds them up on — taken apart,
     *                  a container built here would be read as a different total than it was built
     *                  for
     */
    static TermRealizations.Realization to(Place answer, Type container,
                                           TermOrders orders, SearchRegion within,
                                           RuleReadingContext reading) {
        RuleReadingSource ruleSource = reading.source();
        // Which number is being built for, read off the answer that says which number it is of.
        // Named beside it, the two were free to be about two numbers and this would fill a
        // container found under one path with elements counted on another's order.
        RealizationTarget target = RealizationTarget.of(orders.term());
        Carrier elements = orders.answered();
        if (!(answer instanceof Count total) || elements == null) {
            return none(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        // The position, read once. What it holds, how many of them its rules leave it holding, and
        // the names the container is written under are three questions of one reading, and asking
        // the type again for any of them is another answer to which names it wears.
        TypeView view = TypeView.of(container, ruleSource.inners(), ruleSource.symbols(),
                ruleSource.published());
        if (!(view.shape() instanceof Shape.Sequence holding)) {
            // A total is taken of a container, and what is declared at the root is not one. Which is
            // a term nobody should have been able to build; said here rather than by composing a
            // value of whatever shape is there.
            return none(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        DeclaredBounds.CountRange howMany = howMany(view, target.writeRoot(), within, reading);
        NumericDomain.Bounds runs =
                within.runsBetween(new NumericTerm.ValueOf(occurrences(target)));
        Ends ends = Ends.of(runs == null ? NumericDomain.Bounds.OPEN : runs, elements);
        if (ends == null) {
            // Nowhere for an element to stand. Which is the rules leaving the elements nothing, and
            // is said as a container this composed none of rather than as a total nothing reaches.
            return none(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        // How a value of the element is built with the number written where the total reads it,
        // asked of the plan and once per way down. A sum puts nothing under it until a case is
        // named, so what comes back is one way per case and the walk offers each of them.
        Ways ways = waysDown(holding.element(), target.writeRoot().element(), occurrences(target),
                reading);
        // Nothing to fill a container along, so there is no count and no shape of one to try. Said
        // before the counts are walked rather than as a condition on each figure below: a figure
        // reached where no container could have been built either way is one raising takes nothing
        // away from, and the walks below are what the figures are about.
        if (ways.filled().isEmpty()) {
            return ways.cutBy().isEmpty()
                    ? new TermRealizations.Realization.None(
                            Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                            ways.said(occurrences(target)))
                    : new TermRealizations.Realization.Stopped(ways.cutBy());
        }
        WhatWasLeft left = new WhatWasLeft();
        // What the planning gave up at, which is a way that was there to try and was not. A way down
        // that was never planned is a value never composed, and a reader told only that nothing was
        // composed would look for the rule that refuses it.
        for (CompositionBudget cut : ways.cutBy()) {
            left.refused(cut);
        }
        WhatIsOffered offered = new WhatIsOffered();
        Makings makings = new Makings(total.at(), ends, holding, view, elements, ways, reading);
        // Every count the rules leave, which are all the counts there are: what the container may
        // hold is what the rules say, so a walk that runs out of them has run out of the population
        // and not only of what this compiler writes.
        asFarAs(countsAdmitted(howMany), new HowManyElements(makings, offered, left), left);
        List<FixtureTemplate> built = offered.built();
        if (!built.isEmpty()) {
            return new TermRealizations.Realization.Built(built, left.refused(), left.notAllOf());
        }
        // Nothing was composed, and what a reader may make of that is what these two say. A figure
        // that refused a candidate is why nothing came of it and is somebody's to raise; a
        // population this walks some of leaves an emptiness nothing established, and neither is the
        // other. Where there is neither, what this looked at was everything it could have.
        if (!left.refused().isEmpty()) {
            return new TermRealizations.Realization.Stopped(left.refused(), left.notAllOf());
        }
        if (!left.notAllOf().isEmpty()) {
            return new TermRealizations.Realization.Unexhausted(left.notAllOf(),
                    ways.said(occurrences(target)));
        }
        return new TermRealizations.Realization.None(
                Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                ways.said(occurrences(target)));
    }

    /**
     * What the walks over one total left behind, which is two facts and not one.
     *
     * <p>A figure that refused a piece and a population this walks some of are different things to
     * know, and they are held in different vocabularies so that nothing downstream can put them in
     * one set. The first is somebody's to raise; raising anything reaches none of the second.
     *
     * <p>Both leave an offer that is not the whole of what there is, which is the one thing they
     * have in common and the reason both travel. A reader told every value was refused, on an offer
     * that was never everything, has been handed a sentence to act on that nothing here established.
     */
    static final class WhatWasLeft {

        private final Set<CompositionBudget> refused = EnumSet.noneOf(CompositionBudget.class);
        private final Set<CompositionRepertoire> notAllOf =
                EnumSet.noneOf(CompositionRepertoire.class);

        /** A piece was in front of a walk and this figure left no room for it. */
        void refused(CompositionBudget figure) {
            refused.add(figure);
        }

        /** A walk over this population went everywhere it knows how to, which was not everywhere. */
        void notAllOf(CompositionRepertoire population) {
            notAllOf.add(population);
        }

        /** The figures that refused a piece, which are the ones a composing was stopped by. */
        Set<CompositionBudget> refused() {
            return Set.copyOf(refused);
        }

        /** The populations an offer made under these walks holds some of and not all of. */
        Set<CompositionRepertoire> notAllOf() {
            return Set.copyOf(notAllOf);
        }
    }

    /**
     * A consumer that does as many pieces as one figure of this compiler's leaves it room for.
     *
     * <p>{@link Taken#NOT_TAKEN} only where {@link #figure()} leaves no room for the piece in front
     * of it. That is the whole of what this adds to {@link Taking}: a walk reads the refusal as the
     * figure being reached, so a consumer refusing a piece for any other reason would file its own
     * answer under somebody else's number.
     *
     * <p>And the room is worked out from {@link #figure()} rather than from a number beside it.
     * Read from a second place, what the consumer has room for and what it reports having run out of
     * are two declarations of one policy, and they part the first time one is raised.
     */
    interface Spending<T> extends Taking<T> {

        /** The figure this has no room past. */
        CompositionBudget figure();
    }

    /**
     * Every piece to the consumer, and the figure where the walk was left holding one.
     *
     * <p><b>The one place a figure is recorded.</b> A piece the consumer would not do is a piece
     * this was holding, which is what makes the figure a search declining to go on rather than a
     * property of how the search was set up. Worked out anywhere else, the record is a reading of
     * the arrangement — an unbounded container always has counts above the figure, so a walk that
     * asked whether it had them said a figure had been reached before trying anything.
     *
     * <p>Nothing is said here about whether the pieces were the whole of their kind. That is not
     * something a walk over them can see, and it is not a figure: what it takes to reach the rest
     * of a population is somebody writing the rest, so it is claimed and shown where the pieces are
     * chosen ({@link CompositionRepertoire}).
     *
     * <p>What a caller does with {@link Traversal#SATISFIED} is its own. It is not this walk that
     * left something: a consumer that has what it came for says so, and where that is because a
     * walk further in stopped, that walk has already recorded its figure here.
     */
    static <T> Traversal asFarAs(Iterable<T> pieces, Spending<T> doing, WhatWasLeft left) {
        for (T each : pieces) {
            switch (doing.take(each)) {
                case NOT_TAKEN -> {
                    left.refused(doing.figure());
                    return Traversal.STOPPED;
                }
                case AND_DONE -> {
                    return Traversal.SATISFIED;
                }
                case AND_MORE -> { }
            }
        }
        return Traversal.EXHAUSTED;
    }

    /**
     * The counts the rules leave the container, from the least of them upward.
     *
     * <p>All of them and not the ones a figure allows. What bounds this walk is the consumer, which
     * is handed a count and answers whether it has room for one that size — so the count that is
     * refused is a count the rules admit, and cutting the list to the figure first would leave
     * nothing for it to refuse.
     *
     * <p>Which is also what ends this where the rules cap nothing. A range the rules leave open at
     * the top runs to the largest count there is, and what stops the walk before then is the
     * consumer having no room, as it is at every other length.
     */
    private static Iterable<Integer> countsAdmitted(DeclaredBounds.CountRange howMany) {
        int from = Math.max(howMany.least(), 0);
        return () -> new Iterator<>() {

            private int at = from;

            @Override
            public boolean hasNext() {
                return at <= howMany.most();
            }

            @Override
            public Integer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return at++;
            }
        };
    }

    /**
     * The makings of one container: where its elements stand, what holds them and how to fill them.
     *
     * <p>Everything about the total that neither of the walks below changes. Held together because
     * both of them fill from it and neither owns it — the counts walk hands a size to the shapes
     * walk, and what a shape of that size is made of is the same either way down.
     *
     * @param total     the number the elements are to come to
     * @param container the position the container stands at, read once and worn by what is built
     */
    private record Makings(BigDecimal total, Ends ends, Shape.Sequence holding, TypeView container,
                           Carrier elements, Ways ways, RuleReadingContext reading) {}

    /**
     * How many elements the container is filled with, walked from the least the rules leave upward.
     *
     * <p>The figure is on the count and not on how many counts were tried. What it says is how many
     * elements a row is worth carrying, so a count past it is a count with no room whatever came
     * before it — and a walk counting its own steps would have taken sixty-four counts from a rule
     * that starts at a hundred.
     *
     * <p>Its shapes are a walk of their own at every count. Held as one across the counts, the
     * figure would be how many decompositions the whole total is offered in rather than how many one
     * count is spread as, which is a different policy nobody wrote down.
     */
    private static final class HowManyElements implements Spending<Integer> {

        private final Makings makings;
        private final WhatIsOffered offered;
        private final WhatWasLeft left;

        HowManyElements(Makings makings, WhatIsOffered offered, WhatWasLeft left) {
            this.makings = makings;
            this.offered = offered;
            this.left = left;
        }

        @Override
        public Taken take(Integer many) {
            if (many > figure().maximum()) {
                return Taken.NOT_TAKEN;
            }
            // Said of the shapes this count is spread in, before any of them is walked, because it
            // is about which arrangements there are rather than about what came of trying two of
            // them. Nothing bounds this loop but the shapes there are to write, so a shape is never
            // refused here and no figure of this compiler's is reached by spreading.
            if (!coversEveryArrangement(many)) {
                left.notAllOf(CompositionRepertoire.WAYS_A_TOTAL_IS_SPREAD);
            }
            for (Spread how : Spread.values()) {
                List<BigDecimal> split = splitting(makings.total(), many, makings.ends(), how,
                        makings.elements());
                if (split == null) {
                    continue;
                }
                // Every way down at this count, and the whole container by one of them. Which case
                // an element is is a fact about the value, so a container whose elements are of
                // several would be offering a shape nothing asked for; what the walk owes is to
                // offer each case and to say the rest were never made.
                //
                // An offer with no room for another container has none at any count, so this is the
                // end of the counts as well and not only of the shapes.
                if (asFarAs(containers(split), offered, left) == Traversal.STOPPED) {
                    return Taken.AND_DONE;
                }
            }
            return Taken.AND_MORE;
        }

        /**
         * The containers this split fills, one per way down that builds one.
         *
         * <p>Built as the walk asks for them. Made in a list first, the ways after the one that
         * filled the offer would be values composed for nobody, which is work at exactly the moment
         * this compiler has said it will do no more.
         */
        private Iterable<FixtureTemplate> containers(List<BigDecimal> split) {
            return () -> makings.ways().filled().stream()
                    .map(each -> filled(split, makings.holding(), makings.container(), each,
                            makings.elements(), makings.reading()))
                    .filter(Objects::nonNull)
                    .iterator();
        }

        /**
         * Whether the shapes written here are every arrangement of this many elements there is.
         *
         * <p><b>Shown, or not said.</b> An offer that claims to hold every way of reaching the
         * total is a claim about all the arrangements, and two shapes are two of the many — so this
         * answers for the counts where the many are provably not more than two, and every other
         * count is one this walks some of.
         *
         * <p>Two showings, and both are arithmetic on what the elements stand on. A count of one
         * takes the whole of the difference and a count of nought takes none of it, so there is the
         * one arrangement either way and both shapes are it. And where every element moved as far
         * as its order allows still does not reach the total, no arrangement of that many does —
         * the shapes cover the arrangements by there being none of them.
         *
         * <p>Which way it errs is the whole point. A count whose arrangements this does happen to
         * cover is walked as one it does not, and an offer says it holds less than it does; the
         * other way round, a reader is told every value was refused by a walk that never wrote most
         * of them.
         */
        private boolean coversEveryArrangement(int many) {
            return many <= 1 || !makings.ends().reaches(makings.total(), many);
        }

        @Override
        public CompositionBudget figure() {
            return CompositionBudget.ELEMENTS_A_TOTAL_IS_SPREAD_OVER;
        }
    }

    /**
     * The containers offered for the total, as many of them as the figure is written at.
     *
     * <p>A container alike to one already offered is not another of them, and it is not a piece this
     * had no room for either: what the figure counts is what a reader is handed, and a walk that met
     * the figure on a repeat would name it having refused nothing.
     */
    static final class WhatIsOffered implements Spending<FixtureTemplate> {

        private final List<FixtureTemplate> built = new ArrayList<>();

        @Override
        public Taken take(FixtureTemplate one) {
            if (built.contains(one)) {
                return Taken.AND_MORE;
            }
            if (built.size() == figure().maximum()) {
                return Taken.NOT_TAKEN;
            }
            built.add(one);
            return Taken.AND_MORE;
        }

        /** What was offered, in the order it was built. */
        List<FixtureTemplate> built() {
            return List.copyOf(built);
        }

        @Override
        public CompositionBudget figure() {
            return CompositionBudget.SHAPES_OF_A_TOTAL_OFFERED;
        }
    }

    /**
     * How many the container may hold, by the rules and by what reaches the point.
     *
     * <p>Both, and the tighter of them. What the declarations say about a type is one rule and what
     * a record says about the field holding it is another, and a container built against either
     * alone is refused by the other — a rule asking a ledger for two lines is written where the
     * ledger is, and a reading that knew only {@code List<Item>} would offer one.
     *
     * <p>The second is asked of the region rather than read out of the declarations a second time.
     * How many a location holds is a number like any other, so the region already answers it, and it
     * answers it as narrowed by whatever the row had to pass to get here. Read here instead, this
     * would be a second reading of the rules, free to part from the one the search was run against.
     */
    private static DeclaredBounds.CountRange howMany(TypeView container, TermPath root,
                                                     SearchRegion within,
                                                     RuleReadingContext reading) {
        Symbols symbols = reading.source().symbols();
        DeclaredBounds.CountRange declared =
                DeclaredBounds.countsHeld(container, reading, null);
        ValueName.Stdlib counts = NumericMeasures.takenOf(container.declared(), symbols);
        NumericTerm.FromOnePosition term = counts == null ? null
                : NumericTerm.TakenOf.of(counts, root, container.declared(), symbols);
        NumericDomain.Bounds runs = term == null ? null : within.runsBetween(term);
        if (runs == null) {
            return declared;
        }
        int least = Math.max(declared.least(), CountDomain.leastFrom(runs.min()));
        int most = Math.min(declared.most(), CountDomain.mostFrom(runs.max()));
        return new DeclaredBounds.CountRange(least, most);
    }

    /**
     * The path whose occurrences inside the root are added up.
     *
     * <p>The element itself where the total is of what a location holds, and the run's own path
     * where it is over a run. One question about two terms and not two questions: what is added up
     * is what stands at a path under the container, and where the path stops is the whole
     * difference.
     */
    private static TermPath occurrences(RealizationTarget target) {
        return switch (target) {
            case RealizationTarget.AtOnePosition one -> one.term().position().element();
            case RealizationTarget.OverARun over -> over.term().source().subjectPath();
        };
    }

    /**
     * How the difference between a starting point and the total is spread over the elements.
     *
     * <p>Two, because the shapes a decomposition can take are what the rules the elements are under
     * tell apart. A rule taking one number out of the middle of a run refuses the container that put
     * the whole difference on one element and admits the one that shared it, and neither shape is
     * more of a decomposition than the other. What is not here is a search: these are two of the
     * many, and what the walk owes is to say that the rest were never made.
     */
    private enum Spread {

        /** The whole difference on as few elements as will carry it, the rest where they started. */
        MASSED,

        /** As near an equal share each as the order allows, the last taking what division left. */
        LEVEL
    }

    /**
     * The numbers {@code many} elements hold for the container to come to {@code total}, or null
     * where this shape reaches none.
     *
     * <p>Every element starts where {@link Ends#from} puts it and moves by its share of what is
     * still owed. <b>Either way along the order</b>: a total below where the elements start is
     * reached by moving down as readily as one above it is reached by moving up, and a run open
     * below or held away from a value has no least element to start from at all. Read as a floor,
     * the start was a floor only for the ranges that have one — and a list of whole numbers had no
     * container coming to less than nothing.
     *
     * <p>How far one element may move is the distance to the end it is moving toward, and nothing at
     * all where that end names no value: an order open that way, or a dense one held away from a
     * value it excludes, has no number to move up to. What comes of that is put back to the range
     * and to the carrier, which is what makes a start that is not the least, and a distance that is
     * not a bound, safe to work with.
     *
     * <p>Null where this shape reaches no decomposition. Which is not that none exists — another
     * shape or another count may — and the walk that asked says so.
     */
    private static List<BigDecimal> splitting(BigDecimal total, int many, Ends ends, Spread how,
                                              Carrier elements) {
        if (many == 0) {
            return total.signum() == 0 ? List.of() : null;
        }
        BigDecimal owed = total.subtract(ends.from().multiply(BigDecimal.valueOf(many)));
        List<BigDecimal> split = new ArrayList<>();
        for (int i = 0; i < many; i++) {
            BigDecimal wanted = how == Spread.MASSED ? owed : shared(owed, many - i, elements);
            BigDecimal add = toward(wanted, ends);
            BigDecimal at = ends.from().add(add);
            // Put back to the rules and to the carrier, which are the two things a number has to be
            // to be a value here. Where an element starts and how far it may be moved are worked out
            // from the ends and are this reader's arithmetic; whether what came of them is a value
            // is not, and a decomposition that reads its own workings back would be sound only for
            // as long as the workings are.
            if (!ends.runs().admits(Count.of(at))
                    || !(elements.onTheGrid(Count.of(at)) instanceof Count on)
                    || on.at().compareTo(at) != 0) {
                return null;
            }
            split.add(at);
            owed = owed.subtract(add);
        }
        return owed.signum() == 0 ? List.copyOf(split) : null;
    }

    /**
     * One element's share of what is still owed, over the elements still to be given one.
     *
     * <p>Toward nought, so the elements before the last take no more than their share and the last
     * takes what division left over. Which is what makes the shares add up without a remainder to
     * place: the last element is given whatever is still owed, whatever the division came to.
     *
     * <p>An order that steps divides to a whole number of its counts; one that does not is divided
     * as far as the numbers being added were written, since a share finer than that is a value the
     * total was never stated to a.
     */
    private static BigDecimal shared(BigDecimal owed, int among, Carrier elements) {
        BigDecimal by = BigDecimal.valueOf(among);
        return elements.spacing() == Granularity.DISCRETE
                ? owed.divideToIntegralValue(by)
                : owed.divide(by, Math.max(owed.scale(), 1), RoundingMode.DOWN);
    }

    /**
     * As much of {@code wanted} as one element may move, in the direction it is asking to move.
     *
     * <p>The end an element is moving toward is the one that bounds it, and the other says nothing
     * about the move. Bounded by whichever end happened to be named, an element moving down was held
     * to how far it could go up.
     */
    private static BigDecimal toward(BigDecimal wanted, Ends ends) {
        if (wanted.signum() >= 0) {
            return ends.upTo() == null ? wanted
                    : wanted.min(ends.upTo().subtract(ends.from()));
        }
        return ends.downTo() == null ? wanted
                : wanted.max(ends.downTo().subtract(ends.from()));
    }

    /**
     * The container holding an element for each number, or null where one of them cannot be built.
     *
     * <p>Whole elements, each composed under its own type's rules with the number written where the
     * total reads it. What the rest of an element holds is that type's question and is asked of the
     * one reader that answers it.
     *
     * <p><b>A list, and nothing else.</b> What a set holds is as many values as no two of which are
     * equal, and the elements this composes are alike wherever their numbers are — so a set of them
     * is a set of fewer, and the total it comes to is not the one it was built for. Which is work
     * for whatever first takes a total of a set: nothing does, because no operation declares a sum
     * of one and neither the walk that adds nor the walk that maps takes a set at all. Refused
     * rather than composed as a list, so that the day one arrives it arrives here.
     */
    private static FixtureTemplate filled(List<BigDecimal> split, Shape.Sequence holding,
                                          TypeView container, Filling filling,
                                          Carrier elements, RuleReadingContext reading) {
        if (holding.kind() != Shape.Sequence.Kind.LIST) {
            return null;
        }
        RuleReadingSource ruleSource = reading.source();
        List<FixtureTemplate> values = new ArrayList<>();
        for (BigDecimal each : split) {
            FixtureTemplate one = PlanComposer.compose(filling.plan().root(),
                    new ValuesCarryingANumber(filling.fixed(),
                            FixtureTemplate.on(elements, Count.of(each),
                                    ruleSource.symbols().scope()::reach),
                            reading),
                    reading);
            if (one == null) {
                return null;
            }
            values.add(one);
        }
        return WornNames.under(container.wrappers(), FixtureTemplate.collection(values), ruleSource);
    }

    /**
     * One way of building an element with the number in it: the plan, and the position the number
     * is written at under it.
     *
     * <p>Both, because the second is what the first was planned against. A narrowing is written into
     * the path — {@code items[*].kind@Card.amount} — so a plan and a path from another way down are
     * a plan with nowhere to put the value.
     */
    private record Filling(ConstructionPlan plan, TermPath fixed) {}

    /**
     * The ways down, and what the planning gave up at.
     *
     * @param filled  one per way that planned, in the order they were reached
     * @param cutBy   the figures the planning stopped at, which are this compiler's
     */
    private record Ways(List<Filling> filled, Set<CompositionBudget> cutBy,
                        List<TermPath> nothingStandsAt) {

        Ways {
            filled = List.copyOf(filled);
            cutBy = Set.copyOf(cutBy);
            nothingStandsAt = List.copyOf(nothingStandsAt);
        }

        /**
         * What this walk found, said where a reader is told nothing was composed.
         *
         * <p>One sentence per thing that happened, and never one for two of them. A way that was
         * walked and built nothing says the ways are exhausted; a position nothing stands under
         * says there was never a way to try. Told the same thing, a reader would be working out
         * which of them it was from what the sentence left out.
         */
        String said(TermPath demand) {
            if (!filled.isEmpty()) {
                return spelling(filled.stream().map(Filling::fixed).toList())
                        + (filled.size() == 1 ? " was a way down to `" : " were ways down to `")
                        + demand + "`, and none of them composed a value";
            }
            return nothingStandsAt.isEmpty()
                    ? "nothing here reaches `" + demand + "`"
                    : "nothing standing at " + spelling(nothingStandsAt)
                            + " gives a way down to `" + demand + "`";
        }

        private static String spelling(List<TermPath> paths) {
            return paths.stream().map(each -> "`" + each + "`")
                    .collect(Collectors.joining(", "));
        }
    }

    /**
     * Every way an element of {@code element} holds the number at {@code demand}.
     *
     * <p>One where the way down is a record's fields all the way, and one per case where it crosses
     * a position that holds nothing until a narrowing says what stands there. The plan is what says
     * which of those it is: a demand under such a position comes back as the position and what it
     * stands at, and this states one of them and asks again — so which cases there are, and which
     * of them a field is under, are read where a position's divisions are read and nowhere here.
     *
     * <p>Bounded by its own figure, and the figure travels so that a reader is told the rest were
     * never tried.
     *
     * <p><b>One occurrence of the number per element, and nothing here has to check it.</b> The
     * split this fills a container from is one number per element, so a way down that passed
     * through a container of its own would build a value coming to a multiple of the total. No such
     * way is asked about: a run is read from a path standing inside one sequence
     * ({@link souther.compiler.inputs.RunSource}), and a total of what a location holds is read at
     * the element itself — so a position under the element with a sequence on the way to it is not
     * a number this is ever asked to write for. Guarded here as well, the guard would be one
     * nothing can reach and nothing could show wrong.
     *
     * <p>The narrowings of one position in the order the declarations write them, and a second
     * position's under whichever of the first's it was reached by. Nothing here orders the ways of
     * two positions against each other: what a caller does with them is try each, and the figure
     * above is what says how many.
     */
    private static Ways waysDown(Type element, TermPath at, TermPath demand,
                                 RuleReadingContext reading) {
        List<Filling> found = new ArrayList<>();
        List<TermPath> nothingStandsAt = new ArrayList<>();
        Asking asking = new Asking(element, at, reading);
        asking.add(demand);
        for (ConstructionPlan.Result answer = asking.next(); answer != null;
                answer = asking.next()) {
            TermPath fixed = asking.asked();
            switch (answer) {
                // A way whose number stands inside a container of its own holds as many occurrences
                // of it as that container's rules ask for, and the split above is one number per
                // element of the outer one. So a value built along it comes to a multiple of the
                // total it was built for, and nothing composes one until what is decomposed is the
                // occurrences rather than the elements.
                case ConstructionPlan.Result.Planned(ConstructionPlan plan) ->
                        found.add(new Filling(plan, fixed));
                // A narrowing to state, and the walk states each of them. Asked again rather than
                // planned around: a second narrowing may stand under the first, and which one that
                // is depends on the case this settled on.
                case ConstructionPlan.Result.Unnarrowed(TermPath where, List<Refinement> narrowings) -> {
                    // A position nothing stands under is not a way that was tried and refused.
                    // Kept as itself so that a reader is told there was never a way down rather
                    // than that the ways came to nothing.
                    if (narrowings.isEmpty()) {
                        nothingStandsAt.add(where);
                    }
                    for (Refinement narrowing : narrowings) {
                        asking.add(narrowed(fixed, where, narrowing));
                    }
                }
                case ConstructionPlan.Result.Beyond(Set<CompositionBudget> by) ->
                        asking.gaveUpAt(by);
                case ConstructionPlan.Result.Refused(ConstructionPlan.ModelRefusal why) -> {
                    switch (why) {
                        // Two narrowings at one position, which nothing here writes: every one of
                        // them was stated by this walk, one at a time, at a position the plan
                        // named.
                        case ConstructionPlan.ModelRefusal.Conflict conflict ->
                                throw new IllegalStateException("`" + conflict.at() + "` would have"
                                        + " to be both " + conflict.one().spelled() + " and "
                                        + conflict.other().spelled()
                                        + ", though one narrowing was stated");
                        // A collection on the way down holds nothing, so nothing stands inside it
                        // and there was never a way to try. Which is what the list beside this one
                        // already says: a position nothing stands under is a position nothing
                        // stands under whether the declarations put nothing there or the rules
                        // leave no room for it, and a walk that never had a way is the same news
                        // either way.
                        case ConstructionPlan.ModelRefusal.NoRoom noRoom ->
                                nothingStandsAt.add(noRoom.at());
                    }
                }
            }
        }
        return new Ways(found, asking.stoppedBy(), nothingStandsAt);
    }

    /**
     * The ways this walk has left to ask the plan about, and the figure that says how many it may.
     *
     * <p><b>The asking and the counting are one thing.</b> What multiplies here is the cases of
     * every position that has to be narrowed, and what bounds the walk is therefore how many times
     * it asks — not how many answers it keeps, which is nothing at all where no branch plans. Held
     * apart, the figure was written against whichever count was nearest the top of the loop, and a
     * walk that kept none of what it asked about ran through the whole cross product saying nothing
     * had stopped it.
     *
     * <p>So a caller cannot ask without being counted: there is one way in, and a second place that
     * reached the plan another way would be a second walk rather than an unbounded one.
     */
    private static final class Asking {

        private final Type element;
        private final TermPath at;
        private final RuleReadingContext reading;
        private final Deque<TermPath> left = new ArrayDeque<>();
        private final Set<CompositionBudget> stoppedBy =
                EnumSet.noneOf(CompositionBudget.class);
        private TermPath asked;
        private int asks;

        Asking(Type element, TermPath at, RuleReadingContext reading) {
            this.element = element;
            this.at = at;
            this.reading = reading;
        }

        /** One more way to ask about, which is what stating a narrowing leaves. */
        void add(TermPath way) {
            left.addLast(way);
        }

        /**
         * What the plan says about the next way, or null where there is no next one to ask about.
         *
         * <p>Null for two reasons and the figures tell them apart: nothing is left to ask, or this
         * has asked as often as it may — which {@link #stoppedBy()} says and a reader is owed.
         */
        ConstructionPlan.Result next() {
            if (left.isEmpty()) {
                return null;
            }
            // Read here and handed on, so that one place decides both that this asks no more and
            // which figure it stopped at. Named again where the answer is made, the two could part.
            CompositionBudget tried = CompositionBudget.WAYS_DOWN_TO_A_TOTAL_TRIED;
            if (asks == tried.maximum()) {
                stoppedBy.add(tried);
                return null;
            }
            asks++;
            asked = left.removeFirst();
            return ConstructionPlan.of(element, at, reading.source().inners(),
                    reading.source().symbols(),
                    reading.source().published(), Set.of(asked), Requirements.NONE,
                    (_, building) -> Partitions.heldRange(building, reading, null));
        }

        /** The way the last answer is about. */
        TermPath asked() {
            return asked;
        }

        /** What the planning of one way gave up at, which is this compiler's as the figure here is. */
        void gaveUpAt(Set<CompositionBudget> by) {
            stoppedBy.addAll(by);
        }

        /** Every figure reached, whether by the planning or by this walk. */
        Set<CompositionBudget> stoppedBy() {
            return stoppedBy;
        }
    }

    /**
     * {@code demand} with {@code narrowing} written in at {@code where}.
     *
     * <p>Written into the path rather than carried beside it, because a path states the
     * requirements for the position it names to exist ({@link TermPath#requirements}) and the plan
     * names every position below a narrowing under the narrowed spelling. Kept beside it, the value
     * would be fixed at a position the plan has none of.
     */
    private static TermPath narrowed(TermPath demand, TermPath where, Refinement narrowing) {
        TermPath out = where.refine(narrowing);
        for (TermPath.Step step
                : demand.steps().subList(where.steps().size(), demand.steps().size())) {
            out = switch (step) {
                case TermPath.Step.Field(String name) -> out.then(name);
                case TermPath.Step.Element _ -> out.element();
                case TermPath.Step.Refine(Refinement already) -> out.refine(already);
            };
        }
        return out;
    }

    /**
     * Where an element starts, how far it may be moved either way, and what it has to be inside.
     *
     * <p>The first three are how a decomposition is arrived at and the last is what it is held to.
     * A range open at an end names no value there, and a dense one names none beside a value it
     * excludes — so what an element starts at is a value the carrier picks out of the range rather
     * than an end read off it, and how far it may be moved is nothing at all where the end it is
     * moving toward cannot be named. None of that makes a decomposition wrong on its own, which is
     * why the range travels with them and every number that comes out is put back to it.
     *
     * <p>Both ends and not the top alone. An element moves down as readily as up, and an element
     * held to the distance to the top while moving down is one that walks out of the range and is
     * caught by it — which is a decomposition lost for a reason about this reader.
     *
     * @param downTo null where nothing floors an element, or where the floor is a value the order
     *               has no value beside
     * @param upTo   the same at the other end
     */
    record Ends(BigDecimal from, BigDecimal downTo, BigDecimal upTo,
                NumericDomain.Bounds runs) {

        /**
         * Whether {@code many} elements standing on these ends can come to {@code total} at all.
         *
         * <p>Arithmetic on the ends and nothing else. Every element starts at {@link #from} and may
         * be moved as far as the end it is moving toward, so the furthest {@code many} of them
         * reach together is that distance taken {@code many} times — and a total past it is one no
         * arrangement of that many comes to, whatever the arrangement.
         *
         * <p>True where the end it would be moving toward names no value, since an order open that
         * way puts nothing in the way of reaching any total. And true wherever the sums allow it
         * without asking whether the values between are the carrier's: what this is asked for is
         * whether there is nothing to reach, so admitting a total the carrier turns out to refuse
         * costs a claim of completeness nobody had to make, and denying one it admits would be a
         * claim of completeness nothing showed.
         */
        boolean reaches(BigDecimal total, int many) {
            BigDecimal owed = total.subtract(from.multiply(BigDecimal.valueOf(many)));
            if (owed.signum() == 0) {
                return true;
            }
            BigDecimal end = owed.signum() > 0 ? upTo : downTo;
            return end == null
                    || owed.abs().compareTo(
                            end.subtract(from).abs().multiply(BigDecimal.valueOf(many))) <= 0;
        }

        static Ends of(NumericDomain.Bounds runs, Carrier elements) {
            // A value of the carrier that the rules leave, which is the one reader of that question.
            // Read as "the least the range names", a range open below had no start and a dense one
            // held away from a value had the wrong one.
            if (!(elements.somethingInside(runs.min(), runs.max()) instanceof Count from)) {
                return null;
            }
            return new Ends(from.at(), inward(runs.min(), elements, true),
                    inward(runs.max(), elements, false), runs);
        }

        /**
         * The value of an end that is inside it, or null where the order names none.
         *
         * <p>An end written exclusively is a value taken out of the range, and the value beside it
         * is the carrier's to name — asked of {@link BoundaryDomain}, which is where that question
         * has its answer and where a carrier with no smallest step says it has none.
         */
        private static BigDecimal inward(Endpoint end, Carrier elements, boolean upward) {
            if (end == null || !(end.at() instanceof Count at)) {
                return null;
            }
            if (end.inclusive()) {
                return at.at();
            }
            BoundaryDomain beside = BoundaryDomain.on(elements);
            Optional<Place> next =
                    upward ? beside.successor(at) : beside.predecessor(at);
            return next.orElse(null) instanceof Count on ? on.at() : null;
        }
    }

    private static TermRealizations.Realization none(
            Generator.UnresolvedCombination.Reason why) {
        return new TermRealizations.Realization.None(why);
    }

    private ContainersAddingUp() {}
}
