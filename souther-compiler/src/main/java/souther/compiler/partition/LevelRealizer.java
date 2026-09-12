package souther.compiler.partition;

import souther.compiler.check.Carrier;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.numeric.AdditiveImage;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.Place;
import souther.compiler.regex.Meter;
import souther.compiler.values.ValueSet;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Where each position has to stand for a row to be at one coverage item.
 *
 * <p><b>Apart from {@link BorderQuantity} on purpose.</b> What a border means and where a row at one
 * of its points is are two questions: the first is settled by the rule and the order it cut, and the
 * second depends on what every other rule leaves, on what this compiler can search, and on how long
 * it is willing to look. Answered together, a quantity would carry a solver and every reading of a
 * border would be paying for one.
 *
 * <p>What it is handed is a {@link Standing} — a constraint, not a shape of line — so a quantity
 * added later brings work here only where it needs a kind of search that is not already written.
 *
 * <p>Nothing here decides that an item cannot be reached. A refusal is a refusal of what was tried,
 * and {@link Realization} keeps that apart from a proof: read as one, a search that ran out said the
 * model refuses an edge it merely could not compose (ADR-0091).
 */
public final class LevelRealizer {

    /**
     * Where the positions have to stand for a row to be at this item, or why this found nowhere.
     *
     * <p>The region is handed in per item and is no part of this. Where a row may be written depends
     * on what the rules a row has to pass before it reaches this item leave, which is a fact about
     * where the item's rule is written rather than about the behavior — held here, one region would
     * answer for every item of a body and the search for a border deep in it would run over values
     * nothing arriving there can hold.
     *
     * @param within where a row for this item may be written. Never wider than what the declarations
     *               leave and never narrower than what reaches the item, which is what makes an
     *               exhausted walk of it a proof
     */
    public Realization realize(Standing standing, souther.compiler.inputs.SearchRegion within,
                               WitnessSearch looking) {
        if (within == null) {
            throw new IllegalArgumentException(
                    "a search looks inside a region, and there is always one: an item nothing on the"
                            + " way to it narrows is searched for in what the declarations leave,"
                            + " which is an answer and not an absence");
        }
        // The other vocabulary a position is read in, and it is not optional either. A search that
        // may be handed none is a search that composes without asking what the position holds,
        // which is how a boundary came to be offered a value the declarations refuse.
        if (looking == null) {
            throw new IllegalArgumentException(
                    "a value is composed at a position, and what that position admits is an answer"
                            + " the reading already has: a search that may be handed none is one"
                            + " that composes without asking");
        }
        return switch (standing) {
            case Standing.OfOneCoordinate one -> ofOne(one, within, looking);
            case Standing.OfTwoOnOneCarrier two -> ofTwo(two, within, looking);
            case Standing.OfAForm over -> ofAForm(over, within);
        };
    }

    /** One position at a place of its own carrier that the item accepts. */
    private Realization ofOne(Standing.OfOneCoordinate one,
                              souther.compiler.inputs.SearchRegion within,
                              WitnessSearch looking) {
        Place at = placeMeeting(one.where(), one.term(), one.of(), bounds(within, one.term()),
                looking);
        return at == null ? Realization.Unknown.nothingComposedOne()
                : found(Map.of(new RealizationTarget.AtOnePosition(one.term()), at), within);
    }

    /**
     * Two positions, both fixed at once.
     *
     * <p>Which is the whole of what makes the row one at the item. A search that settled one and left
     * the other to its own range would produce a row beside the line as readily as one on it.
     *
     * <p>The place the second stands at is a place both of them admit, which is the rules' answer
     * about the pair. Where they leave none, nothing is composed — and that is reported as a search
     * that found nothing rather than as a proof, because two ranges leaving no place in common is a
     * fact about the ranges and the pair may be refused or admitted by a rule neither range holds.
     */
    private Realization ofTwo(Standing.OfTwoOnOneCarrier two,
                              souther.compiler.inputs.SearchRegion within,
                              WitnessSearch looking) {
        NumericDomain.Bounds on = bounds(within, two.on());
        NumericDomain.Bounds together = commonRange(on, bounds(within, two.against()), two.of(),
                two.where().anchor().asACount());
        Outwards.Walked walked = alongTheLine(together, two.of());
        for (Place common : walked) {
            // Where the first has to stand relative to the second: the place the level's distance
            // from it, and then whatever the item asks of that place. Arithmetic on the carrier's
            // counts and not a walk along it — a walk is an addition that only exists where the
            // order has a smallest step, so a rule over two decimals had no pair anything could
            // compose.
            // Null where the carrier's arithmetic could not put the item's levels beside the place
            // the other position stands at — read on, an item with no level in it was handed to a
            // reader that asks where its level falls.
            Criterion here = relativeTo(two.where(), common, two.of());
            Place at = here == null ? null
                    : placeMeeting(here, two.on(), two.of(), on, looking);
            if (at == null) {
                continue;
            }
            Map<RealizationTarget, Place> fixing = new LinkedHashMap<>();
            fixing.put(new RealizationTarget.AtOnePosition(two.on()), at);
            fixing.put(new RealizationTarget.AtOnePosition(two.against()), common);
            if (found(fixing, within) instanceof Realization.Found made) {
                return made;
            }
        }
        // Nothing was composed, which is what a pair the ranges leave no place for comes to and
        // what this has always said. Where the walk stopped at its figure, that is said beside the
        // answer rather than in place of it.
        return Realization.Unknown.nothingComposedOne(walked.stoppedShort()
                ? java.util.Set.of(CompositionBudget.PLACES_A_PAIR_IS_TRIED_AT)
                : java.util.Set.of());
    }

    /**
     * How many places along a line a pair is tried at before this stops.
     *
     * <p>Small on purpose. What a range cannot say is that one of its values is missing, and a rule
     * that takes a value away takes one — everything that moves an end is in the range already. So
     * what this steps past is holes, and there are as many of those as the rules state.
     */
    private static final int HOW_MANY_PLACES_A_PAIR_IS_TRIED_AT =
            CompositionBudget.PLACES_A_PAIR_IS_TRIED_AT.maximum();

    /**
     * The places to try the pair at, from the one the ranges leave outward.
     *
     * <p>Every place on the line carries the pair as well as any other — where they stand is a
     * witness and the line is the item ({@link Standing.OfTwoOnOneCarrier}) — so one that the rules
     * refuse is one to step off rather than an answer.
     *
     * <p>Which is a distinction the ranges cannot make. A place is chosen from what the two ranges
     * leave and whether it stands is the rules' to say, and a range has no word for a value taken
     * out of the middle of it: before anything narrowed a search, nothing the ranges left was ever
     * refused and the two never disagreed. A region draws one value out and the pair at it is the
     * only pair on the line that cannot be written.
     *
     * <p>One place where the carrier's values do not count. There is no next place to step to, so
     * the one the ranges leave is the whole of what there is to try.
     */
    private static Outwards.Walked alongTheLine(NumericDomain.Bounds together, Carrier carrier) {
        // Nothing composed, which is this one's own answer and not something to ask a walk about.
        Place first = carrier.somethingInside(together.min(), together.max());
        return first == null ? new Outwards.Walked(List.of(), false)
                : Outwards.from(first, Count.of(1), carrier, together,
                        HOW_MANY_PLACES_A_PAIR_IS_TRIED_AT);
    }

    /**
     * The place {@code from} moved by the distance a level names, or null where the carrier holds
     * none there.
     *
     * <p>The distance is a number on the carrier's counts, so this is addition. Where the carrier's
     * values do not count there is no distance to add, and the only level such a quantity takes is
     * the one where the two meet — so the place is the one they meet at and any other level names
     * nothing.
     */
    private static Place movedBy(Place from, Level level, Carrier carrier) {
        Count apart = level.asACount();
        if (!carrier.counts()) {
            return apart.signum() == 0 ? from : null;
        }
        return carrier.onTheGrid(Count.number(from).plus(apart));
    }

    /**
     * Every position of a form, at values that put the form where the item asks.
     *
     * <p>Depth-first over the positions, each of them standing where {@link CandidateDomain} says it
     * may: inside the run its own rules leave, and on the coset the coefficients of the rest can
     * land on. Both are facts that settle the position without looking and both are in the set
     * rather than beside it — held as a test applied after choosing, the second one settled nothing
     * wherever the first left a single candidate to choose.
     *
     * <p>Over whole numbers and over decimals alike. A position whose values fill is held to a coset
     * that is dense and is still not every value, so what it can be given is one member of that and
     * never the next one along.
     *
     * <p><b>Three answers, and the difference between them is the point.</b> A walk of a position
     * every value of which could be tried and was proves the level is out of reach; running past the
     * budget, or standing a position somewhere it has more values than this looked at, proves
     * nothing at all (ADR-0091). Reported as one, a search that ran out would take a coverage item
     * away.
     *
     * <p>A side is asked for at the first level past the one it starts from, and then at the next,
     * for as many as {@link LevelCandidateSource} offers. Which levels those are, and how many, is
     * that one's answer: whether a row can be composed at one of them is this one's, and the two are
     * apart so that how long this is willing to look does not read as a fact about the order.
     */
    private Realization ofAForm(Standing.OfAForm over,
                                souther.compiler.inputs.SearchRegion within) {
        LevelSpace levels = over.levels();
        // In the form's own order and not the map's. A form is a map, so the order its coefficients
        // were recorded in is a hash order — and which position is solved last decides whether the
        // walk finds an answer inside its budget, so an answer that depended on it would depend on
        // nothing a reader can see.
        List<Map.Entry<RealizationTarget, java.math.BigDecimal>> terms = new java.util.ArrayList<>();
        for (Map.Entry<NumericTerm, java.math.BigDecimal> each
                : AffineReading.ordered(over.form())) {
            // Every number is realized by rebuilding one value, so what the walk assigns is a demand
            // and there is one for each term of the form. Whether anything writes such a value is
            // not asked here and is not this reader's to answer: a walk that turned a term away for
            // being of the wrong kind would be deciding what is buildable from the shape of the
            // number, which is the answer that went stale the first time something learned to build
            // one.
            terms.add(Map.entry(RealizationTarget.of(each.getKey()), each.getValue()));
        }
        boolean bounded = true;
        // Every budget any of the level walks ran out of. Collected and not chosen between: two
        // levels stopped by two figures are two things this compiler declined to do, and a reader
        // asking what would let the search go further is owed both.
        java.util.Set<CompositionBudget> stoppedBy =
                java.util.EnumSet.noneOf(CompositionBudget.class);
        LevelCandidateSource.Offered offered =
                LevelCandidateSource.forItem(over.where(), levels);
        if (offered.stoppedShort()) {
            stoppedBy.add(CompositionBudget.LEVELS_A_SIDE_IS_ASKED_AT);
        }
        for (Level level : offered.levels()) {
            Search search = new Search(terms, over.on(), within);
            Reached reached = search.solve(level.asACount());
            stoppedBy.addAll(search.stoppedBy());
            if (reached == Reached.FOUND) {
                Realization made = found(search.fixing(), within);
                if (made instanceof Realization.Found) {
                    return made;
                }
                // An assignment the walk reached that no row came of. Nothing about the level
                // follows from it: what refused the row is the writing of it and not the rules the
                // walk held the assignment to.
                bounded = false;
            } else {
                bounded &= reached == Reached.EXHAUSTED;
            }
        }
        // A point asks for one level and a side asks for any level past one, so only the first can
        // be settled by looking: a walk of the whole box that reaches the level nothing else does is
        // a proof, and a side that none of the levels tried reached is a side this stopped looking
        // at.
        if (bounded && over.where() instanceof Criterion.AtTheLevel) {
            return new Realization.Impossible();
        }
        // A side is never settled by looking, so what this comes back with is that something was
        // left untried — which is what it has always come back with, whether or not a figure of
        // this compiler's was reached. The figures are said beside that answer and do not choose
        // it, which is why the answer is not named for one of them running out.
        return Realization.Unknown.searchLeftSomethingUntried(stoppedBy);
    }

    /** How many assignments the search will try before it stops and says it did not settle it. */
    private static final int STEPS_A_SEARCH_MAY_TAKE =
            CompositionBudget.STEPS_A_SEARCH_MAY_TAKE.maximum();

    /**
     * How many values of a progression nothing bounds are tried.
     *
     * <p>Small on purpose, and it buys something narrower than it looks. The values are already the
     * ones that leave the rest a residue their coefficients land on, so what stepping along it walks
     * past is what the rules take out — and a rule takes values out one region at a time. Nothing
     * here is a proof at any length: a run without an end is not walked to the end.
     *
     * <p><b>Values and not distances.</b> Sixteen of the first is thirty-three of the second where
     * both sides are open, and the two units were mixed here once: this was written as a count of
     * distances and then handed to {@link Outwards}, which counts what it yields. Counted in values
     * for the same reason that one does — it is what a run gives up, and a bound on anything else
     * has to be turned into one before it means anything.
     *
     * <p><b>More than one is needed by models that exist.</b> A disequality takes one value out of
     * the middle of a run without moving either end, so a position carrying one looks unbounded to
     * anything reading ranges and is refused at exactly one place — which is where the coset's own
     * member can land. Cut to one value, {@code a + b = 0} over two positions held away from zero
     * comes back as a search that stopped, and the row at it is one step along.
     *
     * <p>How many past that is not measured. Every progression the suite reaches gives up its row by
     * the second value, and what would ask for a third is a run with two holes in it beside each
     * other.
     */
    private static final int VALUES_A_PROGRESSION_WITHOUT_AN_END_IS_TRIED_AT =
            CompositionBudget.VALUES_OF_AN_UNBOUNDED_PROGRESSION_TRIED.maximum();

    /**
     * What a walk of one position came to.
     *
     * <p><b>Three, because two of them mean opposite things about an empty hand.</b> A position
     * every value of which was tried leaves nothing more to try, and a walk of the whole form that
     * ends this way is what makes {@link Realization.Impossible} a proof. A position that ran on, or
     * that has no next value, leaves the question exactly where it was. Held as one boolean and set
     * from wherever a walk gave up, the two were the same answer and a level nothing reached could
     * not be told from a level nothing looked for.
     */
    private enum Reached {

        /** An assignment of every position, standing where the item asks. */
        FOUND,

        /** Every value there was to try was tried, and none of them was one. A proof. */
        EXHAUSTED,

        /** The walk stopped short of that, and nothing follows from its coming back empty. */
        INCOMPLETE
    }

    /** How often one search reads the declarations again with a position fixed. Each of them is a
     *  reading of every rule reaching the form's positions, and what it buys is skipping
     *  assignments the rules refuse — worth paying for a search that ends quickly and not for one
     *  walking a box a hundred thousand wide. */
    private static final int HOW_OFTEN_THE_RULES_ARE_ASKED_AGAIN =
            CompositionBudget.TIMES_THE_RULES_ARE_ASKED_AGAIN.maximum();


    /** How far a derived end is written out where the division that makes it does not end. Any
     *  number of them is sound while the rounding goes outward; this many keeps the bound close
     *  enough that the walk it bounds is still short. */
    private static final int DIGITS_A_DERIVED_END_KEEPS = 32;

    /**
     * The search itself: an assignment of every position of a form, or nothing.
     *
     * <p>Depth-first over the positions, each of them standing where {@link CandidateDomain} says
     * it may. Both of the things that settle a position without looking are in that set rather than
     * beside it: what the remaining positions can add up to, and what their coefficients can land on.
     * Held as a test the walk applied after choosing, the second one settled nothing wherever the
     * first left one candidate to choose.
     *
     * <p>What a walk comes to is {@link Reached}, and the three answers do not collapse. A position
     * whose values could all be tried and were is what makes an empty-handed walk a proof; one that
     * ran on, or that has no next value to step to, leaves the question open however far it was
     * taken.
     */
    private final class Search {

        private final List<Map.Entry<RealizationTarget, java.math.BigDecimal>> terms;
        /**
         * The order each position is read and written on, in the order the terms are walked.
         *
         * <p>One apiece rather than one for the form. Every question the walk asks of an order is
         * about a position — whether its values step, what its next value is, how far it may be
         * moved — and answering them from one order handed to the whole form walked a decimal
         * position over the whole numbers, or the other way about, wherever a form's positions were
         * not written back the same way.
         */
        private final Carrier[] carriers;
        /** Where a row for the item being searched for may be written. */
        private final souther.compiler.inputs.SearchRegion within;
        private final Place[] at;
        /**
         * Where each term runs before anything is fixed, worked out once.
         *
         * <p>What the positions from here on can add up to is asked at every step of the walk and
         * of every term still to be chosen, and it is the same answer every time: it is asked of the
         * rules as they stand and not of the rules as this walk has narrowed them. Asked afresh each
         * time, a bounded walk of a wide box pays for a projection per step per term where it used
         * to pay for a lookup.
         */
        private final NumericDomain.Bounds[] runsBetween;
        /**
         * What the positions from each one on can add up to, worked out once.
         *
         * <p>A form names no position twice and weighs none of them by nothing — {@link
         * LinearForm} drops a coefficient the moment it comes to zero — so every
         * suffix of it is a form and has an image.
         */
        private final AdditiveImage[] fromHere;
        private int taken;
        private int asked;
        /** Which budgets of this compiler's this walk ran out of, recorded where each ran out. What
         *  it came back with says that nothing was reached and not what kept it from reaching. */
        private final java.util.Set<CompositionBudget> stoppedBy =
                java.util.EnumSet.noneOf(CompositionBudget.class);

        java.util.Set<CompositionBudget> stoppedBy() {
            return stoppedBy;
        }

        /** Whether there is room for another assignment, marking the budget where there is not. */
        private boolean stepsLeft() {
            if (taken > STEPS_A_SEARCH_MAY_TAKE) {
                stoppedBy.add(CompositionBudget.STEPS_A_SEARCH_MAY_TAKE);
                return false;
            }
            return true;
        }

        Search(List<Map.Entry<RealizationTarget, java.math.BigDecimal>> terms,
               Map<NumericTerm, Carrier> on, souther.compiler.inputs.SearchRegion within) {
            this.terms = terms;
            this.carriers = new Carrier[terms.size()];
            for (int i = 0; i < terms.size(); i++) {
                carriers[i] = on.get(terms.get(i).getKey().term());
            }
            this.within = within;
            this.at = new Place[terms.size()];
            this.runsBetween = new NumericDomain.Bounds[terms.size()];
            for (int i = 0; i < terms.size(); i++) {
                runsBetween[i] = bounds(within, terms.get(i).getKey().term());
            }
            this.fromHere = new AdditiveImage[terms.size()];
            for (int i = 0; i < terms.size(); i++) {
                Map<NumericTerm, souther.compiler.numeric.Rational> coefs = new LinkedHashMap<>();
                for (int j = i; j < terms.size(); j++) {
                    coefs.put(terms.get(j).getKey().term(),
                            souther.compiler.numeric.Rational.of(terms.get(j).getValue()));
                }
                // Each term's own spacing, which is what the image was always asking for: a sum of
                // whole numbers lands on whole numbers, and one decimal among them makes it dense.
                fromHere[i] = AdditiveImage.of(coefs, term -> on.get(term).spacing());
            }
        }

        Reached solve(Count target) {
            return walk(0, target.at(), within);
        }

        /**
         * Where every position stands, which exists only after a walk that reached a row.
         *
         * <p>Refused otherwise rather than answered with the positions that happen to be fixed. A
         * walk that came back {@link Reached#EXHAUSTED} leaves the ones it gave up on standing
         * nowhere, and a map with nothing under a key is a row somebody is offered with a position
         * missing from it.
         */
        Map<RealizationTarget, Place> fixing() {
            Map<RealizationTarget, Place> out = new LinkedHashMap<>();
            for (int i = 0; i < terms.size(); i++) {
                if (at[i] == null) {
                    throw new IllegalStateException(
                            "a walk that did not reach a row has no assignment to hand over:"
                                    + " nothing stands at `" + terms.get(i).getKey() + "`");
                }
                out.put(terms.get(i).getKey(), at[i]);
            }
            return out;
        }

        /**
         * The walk from one position on, given what the positions before it left owed.
         *
         * <p>The last position is solved and every other is chosen from {@link CandidateDomain}. What
         * comes back says which of the three things happened, and the difference between the last two
         * is the whole reason this is not a boolean: a position all of whose values were tried leaves
         * an empty-handed walk a proof, and one that was cut short leaves it nothing at all.
         */
        private Reached walk(int i, java.math.BigDecimal owed,
                             souther.compiler.inputs.SearchRegion here) {
            taken++;
            if (!stepsLeft()) {
                return Reached.INCOMPLETE;
            }
            java.math.BigDecimal coef = terms.get(i).getValue();
            // Narrowed by what the positions after this one can add up to. Left at the position's own
            // ends, a box a million wide is walked a million times and the budget runs out on
            // `a + b <= 2000000` — an equation with one answer.
            NumericDomain.Bounds left =
                    leaving(i + 1, owed, coef, bounds(here, terms.get(i).getKey().term()));
            if (i == terms.size() - 1) {
                return solving(i, owed, coef, left);
            }
            // Where this position may stand: the run, and the values of it that leave the rest a
            // residue their coefficients land on. Both are proofs, and the second is in the set
            // rather than beside it — a residue off what the rest can reach is one no assignment of
            // them arrives at, and a position offering one candidate has nothing for a test applied
            // afterwards to leave.
            CandidateDomain may = CandidateDomain.of(
                    fromHere[i + 1].affinePreimage(
                            souther.compiler.numeric.Rational.of(coef),
                            souther.compiler.numeric.Rational.of(owed),
                            carriers[i].spacing()),
                    left);
            return switch (may) {
                case CandidateDomain.None _ -> Reached.EXHAUSTED;
                case CandidateDomain.One only -> trying(i, only.at().at(), owed, coef, here);
                // One value out of a coset whose values fill. There is no next one to step to, so
                // what this walked was never the whole of it however the value turned out.
                case CandidateDomain.Somewhere one ->
                        trying(i, one.at().at(), owed, coef, here) == Reached.FOUND
                                ? Reached.FOUND : Reached.INCOMPLETE;
                case CandidateDomain.Walking every -> walking(i, every, owed, coef, here);
                case CandidateDomain.Outward on -> outward(i, on, owed, coef, here);
            };
        }

        /**
         * This position held at one value, and the walk of everything after it.
         *
         * <p>A value the rules are left nothing beside is stepped past here rather than offered and
         * refused where the row is built: refused there, one candidate coming back rejected is
         * reported as every value having been tried. Nothing being left is proved by the rules, so
         * stepping past it takes nothing out of a walk that reaches the end.
         */
        private Reached trying(int i, java.math.BigDecimal x, java.math.BigDecimal owed,
                               java.math.BigDecimal coef, souther.compiler.inputs.SearchRegion here) {
            souther.compiler.inputs.SearchRegion next =
                    narrowing(here, terms.get(i).getKey().term(), x);
            if (next == null) {
                return Reached.EXHAUSTED;
            }
            at[i] = new Count(x);
            Reached reached = walk(i + 1, owed.subtract(coef.multiply(x)), next);
            if (reached != Reached.FOUND) {
                at[i] = null;
            }
            return reached;
        }

        /**
         * Every value of a run, in order, and what the walk of them all came to.
         *
         * <p>Only this and a position with one value or none can end in a proof. A walk that ends
         * because every value was tried is a proof exactly where each of those values was itself
         * walked to the end, which is why what comes back is the weakest of the children rather than
         * the last of them.
         */
        private Reached walking(int i, CandidateDomain.Walking every, java.math.BigDecimal owed,
                                java.math.BigDecimal coef,
                                souther.compiler.inputs.SearchRegion here) {
            Reached weakest = Reached.EXHAUSTED;
            for (java.math.BigDecimal x = every.first();
                    x.compareTo(every.last()) <= 0; x = x.add(every.by())) {
                Reached reached = trying(i, x, owed, coef, here);
                if (reached == Reached.FOUND) {
                    return Reached.FOUND;
                }
                if (reached == Reached.INCOMPLETE) {
                    weakest = Reached.INCOMPLETE;
                }
                if (!stepsLeft()) {
                    return Reached.INCOMPLETE;
                }
            }
            return weakest;
        }

        /**
         * A progression nothing bounds, from the value it names outward.
         *
         * <p>Never a proof. What is walked is a run without an end, so an empty-handed walk of as
         * many of its values as this is willing to take says only that those values were not the
         * one.
         *
         * <p>More than one of them, for what the rules can do to a value the arithmetic leaves: the
         * coset says which values leave the rest something they reach, and a rule the region carries
         * can refuse one of those without refusing the next. How many are worth trying is this
         * search's own answer — {@link Outwards} carries the order they are tried in and no
         * allowance of its own, and what a step past a refused value buys is not the same question
         * here as it is for a pair on a line.
         */
        private Reached outward(int i, CandidateDomain.Outward on, java.math.BigDecimal owed,
                                java.math.BigDecimal coef,
                                souther.compiler.inputs.SearchRegion here) {
            Outwards.Walked walked = Outwards.from(new Count(on.from()), new Count(on.by()),
                    carriers[i], on.within(), VALUES_A_PROGRESSION_WITHOUT_AN_END_IS_TRIED_AT);
            for (Place x : walked) {
                if (trying(i, Count.number(x).at(), owed, coef, here) == Reached.FOUND) {
                    return Reached.FOUND;
                }
                if (!stepsLeft()) {
                    return Reached.INCOMPLETE;
                }
            }
            // Never a proof either way: a run without an end is not walked to the end, and a walk
            // of every value this had is still a walk of a progression. What the figure adds is
            // only said where the run held one more and this did not take it — added whenever the
            // walk came back empty, it would name a figure over a run that had nothing further in
            // it, which is this compiler claiming to have been stopped where it was not.
            if (walked.stoppedShort()) {
                stoppedBy.add(CompositionBudget.VALUES_OF_AN_UNBOUNDED_PROGRESSION_TRIED);
            }
            return Reached.INCOMPLETE;
        }

        /**
         * The last position, solved rather than tried, and every way it can fail is a proof.
         *
         * <p>Where its values step, what is left over has to be its coefficient's multiple; where
         * they fill, it is a division and the answer is whatever number it comes to. A quotient with
         * no end is the one that used to be read as a search giving up, and it is not: a value a
         * model cannot write is a value the position does not hold, so what it says is that this
         * prefix has no last value and never that this compiler could not find one.
         *
         * <p>Then the ends themselves, which say whether they are their own values, and then the
         * rules with every position fixed — the one place a whole assignment exists to be held
         * against them. Each of the three refuses on something proved, so a walk that ends here
         * empty-handed has ended.
         */
        private Reached solving(int i, java.math.BigDecimal owed, java.math.BigDecimal coef,
                                NumericDomain.Bounds left) {
            java.math.BigDecimal solved;
            if (carriers[i].spacing() == souther.compiler.numeric.Granularity.DISCRETE) {
                java.math.BigDecimal[] divided = owed.divideAndRemainder(coef);
                if (divided[1].signum() != 0) {
                    return Reached.EXHAUSTED;
                }
                solved = divided[0];
            } else {
                try {
                    solved = owed.divide(coef);
                } catch (ArithmeticException noEnd) {
                    return Reached.EXHAUSTED;
                }
            }
            // Held against the ends themselves, which say whether they are their own values. A bound
            // of `> 0` leaves one and not zero, and rounding the end to a number first loses which of
            // the two it is.
            if (!left.admits(new Count(solved))) {
                return Reached.EXHAUSTED;
            }
            at[i] = new Count(solved);
            if (!theRulesHaveNotRefused()) {
                at[i] = null;
                return Reached.EXHAUSTED;
            }
            return Reached.FOUND;
        }

        /**
         * The rules with one more position fixed, or null where they are then left nothing.
         *
         * <p>Narrowing, and only that. Null is a proof and the value is skipped on it, so a walk
         * that skips every one of them has still walked everything there was.
         *
         * <p><b>Asked while it is worth asking.</b> Each of these reads the declarations reaching the
         * positions again, and a box wide enough to walk for a hundred thousand steps is wide enough
         * to read them a hundred thousand times. Past {@link #HOW_OFTEN_THE_RULES_ARE_ASKED_AGAIN}
         * the walk carries on against what the rules left before anything was fixed, which is wider
         * and is sound: it offers assignments this would have skipped, and skips none it would have
         * kept.
         *
         * <p>What giving this up may not give up is the answer. An assignment out of the wider box
         * that nothing held against the rules is one the record can refuse, and offered as a row it
         * comes back refused where it is built — which is the defect this reading exists to remove,
         * arriving by way of a budget. So the last step is {@link #theRulesHaveNotRefused} and is
         * not budgeted.
         */
        private souther.compiler.inputs.SearchRegion narrowing(souther.compiler.inputs.SearchRegion here, NumericTerm term,
                                    java.math.BigDecimal at) {
            if (asked >= HOW_OFTEN_THE_RULES_ARE_ASKED_AGAIN) {
                return here;
            }
            asked++;
            souther.compiler.inputs.SearchRegion next = here.given(term, new Count(at));
            return next.emptiness().isPresent() ? null : next;
        }

        /**
         * Whether the rules, with every position of the form fixed at what the walk chose, were not
         * shown to leave nothing.
         *
         * <p>Not that a value exists. Nothing here builds one, and the rules leaving something is
         * not the same as something being writable — what settles that is the row itself, where it
         * is built. What this refuses is narrower and is the whole of what was wrong: an assignment
         * the rules are already known to refuse, offered as a row and reported as though the point
         * had nothing at it.
         *
         * <p>Asked of the whole assignment and not of the last position, because that is what an
         * assignment is. Where the narrowing above ran out, the values chosen before this one were
         * never put to the rules at all, so asking about the last of them alone would hold the walk
         * to nothing it had not already checked.
         */
        private boolean theRulesHaveNotRefused() {
            java.util.Map<RealizationTarget, Place> all = new LinkedHashMap<>();
            for (int j = 0; j < terms.size(); j++) {
                all.put(terms.get(j).getKey(), at[j]);
            }
            return LevelRealizer.this.theRulesHaveNotRefused(all, within);
        }

        /**
         * The run this position has, narrowed by what the positions after it can add up to.
         *
         * <p>The interval half of where it may stand and not the whole of it. What the rest can add
         * up to is an interval; what this one has to contribute for the residue to land in it is
         * another; both are proofs, so the run is the tighter of them and not the position's own
         * ends. Which values of that run leave the rest a residue their coefficients land on is the
         * other half, and {@link CandidateDomain} is where the two meet.
         *
         * <p>Read off the rules as they stood before anything was fixed. A form's suffix has a run
         * inside the region at hand as well, and it is narrower wherever a rule relates the
         * positions — asking for that one is the change the walk's own cost note argues against, and
         * it is a question of its own rather than part of where a position may stand.
         */
        private NumericDomain.Bounds leaving(int rest, java.math.BigDecimal owed,
                                             java.math.BigDecimal coef,
                                             NumericDomain.Bounds within) {
            java.math.BigDecimal[] reach = reach(rest);
            if (reach == null || coef.signum() == 0) {
                return within;
            }
            // owed - coef * x must lie in [reach0, reach1], so coef * x lies in
            // [owed - reach1, owed - reach0].
            java.math.BigDecimal one = owed.subtract(reach[1]);
            java.math.BigDecimal other = owed.subtract(reach[0]);
            java.math.BigDecimal low = quotient(one, coef, java.math.RoundingMode.FLOOR)
                    .min(quotient(other, coef, java.math.RoundingMode.FLOOR));
            java.math.BigDecimal high = quotient(one, coef, java.math.RoundingMode.CEILING)
                    .max(quotient(other, coef, java.math.RoundingMode.CEILING));
            return new NumericDomain.Bounds(
                    Endpoint.lower(within.min(), Endpoint.inclusive(new Count(low))),
                    Endpoint.upper(within.max(), Endpoint.inclusive(new Count(high))));
        }

        /**
         * A quotient that is exact where the division ends, and rounded the way {@code towards} says
         * where it does not.
         *
         * <p><b>Outward and never inward.</b> What this bounds is a proof — a value outside it is one
         * no assignment of the rest completes — so a bound rounded the wrong way takes a value the
         * box holds out of the walk, and a walk that then finds nothing calls the level unreachable.
         * Rounded to sixteen digits at the nearest, {@code a = 10000000000000001} was rounded to
         * {@code 10000000000000000} and the one pair that meets the line was proved not to exist.
         */
        private static java.math.BigDecimal quotient(java.math.BigDecimal owed,
                                                     java.math.BigDecimal coef,
                                                     java.math.RoundingMode towards) {
            try {
                return owed.divide(coef);
            } catch (ArithmeticException noEnd) {
                return owed.divide(coef, DIGITS_A_DERIVED_END_KEEPS, towards);
            }
        }

        /**
         * The least and the greatest the positions from {@code i} on can add up to, or null where
         * one of them is unbounded and there is nothing to say.
         */
        private java.math.BigDecimal[] reach(int i) {
            java.math.BigDecimal least = java.math.BigDecimal.ZERO;
            java.math.BigDecimal most = java.math.BigDecimal.ZERO;
            for (int j = i; j < terms.size(); j++) {
                NumericDomain.Bounds within = runsBetween[j];
                java.math.BigDecimal coef = terms.get(j).getValue();
                java.math.BigDecimal low = numberOf(within.min());
                java.math.BigDecimal high = numberOf(within.max());
                if (low == null || high == null) {
                    return null;
                }
                java.math.BigDecimal one = coef.multiply(low);
                java.math.BigDecimal other = coef.multiply(high);
                least = least.add(one.min(other));
                most = most.add(one.max(other));
            }
            return new java.math.BigDecimal[] {least, most};
        }

        private static java.math.BigDecimal numberOf(Endpoint end) {
            return end == null || !(end.at() instanceof Count count) ? null : count.at();
        }

    }

    /**
     * The item read as a question about one place of one carrier, given where the other position
     * stands.
     *
     * <p>A level of a distance says nothing about a carrier's order until the other end of it is
     * known. Once it is, an item about the pair is an item about one place — which is why the two
     * are searched for by one procedure rather than by two that agreed by being written alike.
     */
    private static Criterion relativeTo(Criterion where, Place common, Carrier of) {
        // Every level of the item read as the place it lands on once the other end of the line is
        // known. Mapped one level at a time and not by handing one place to all of them: a run has
        // two ends and a line between them, and a mapping that gave them all the same place left a
        // run that said nothing about which side of the line it lay — so a search took whatever the
        // declared domain offered first and a row on the line came back for a point past it.
        // Null where the carrier's arithmetic puts a level nowhere. Which end that happens at
        // decides what it means: a line with no place is an item this order cannot be read as at
        // all, and a run's far end with no place is a run that reaches as far as the carrier does.
        java.util.function.UnaryOperator<Level> onto = level -> {
            Place at = movedBy(common, level, of);
            return at == null ? null : new Level.OnACarrier(of, at);
        };
        return switch (where) {
            case Criterion.AtTheLevel at -> only(new Criterion.AtTheLevel(onto.apply(at.at())),
                    onto.apply(at.at()));
            case Criterion.Within within -> {
                Band run = within.band().mappedBy(onto);
                yield run == null ? null : new Criterion.Within(run,
                        within.except() == null ? null : onto.apply(within.except()),
                        within.away());
            }
        };
    }

    /** An item, unless the level it is written against has no place on this order. */
    private static Criterion only(Criterion made, Level against) {
        return against == null ? null : made;
    }

    /**
     * A place of {@code carrier} the item accepts, or null where this composes none.
     *
     * <p>Composed and then asked. Which place stands at an item and whether a place is at that item
     * are two answers, and the second already exists — worked out apart, the two came apart. So what
     * is composed is put back to the item, and a place the item does not accept stands for nothing:
     * a row offered for a side that is really at the point against the line is a row an author pastes
     * and re-measures to find the item still uncovered.
     */
    private static Place placeMeeting(Criterion where, NumericTerm.FromOnePosition term,
                                      Carrier carrier, NumericDomain.Bounds bounds,
                                      WitnessSearch looking) {
        Place offered = switch (where) {
            // The level itself, and the set is not asked. A point on a line stands where the rule
            // wrote it; held to what the declarations admit, a line drawn at a value they refuse
            // would stop being an item rather than being reported as one nothing can stand at.
            case Criterion.AtTheLevel at -> placeOf(at.at());
            // Nothing composed where nothing worked out what the position holds. Which is this
            // compiler's own limit and is reported in the word it has for one: a run searched against
            // a set nobody established would offer a row at a position whose rules were never read.
            case Criterion.Within within ->
                    whatTheValuesAre(term, looking.admitted())
                            instanceof AdmittedValues.Admitted.Values(ValueSet admits)
                            ? someValueIn(within, carrier, bounds, admits, looking::meter)
                            : null;
        };
        if (offered == null) {
            return null;
        }
        // The grid is asked first and separately. What a carrier's values are spaced by says what a
        // place may be sharpened onto and does not promise that every number between two counts is
        // one of them, which is the carrier's question rather than the item's.
        Place onTheGrid = carrier.onTheGrid(offered);
        return onTheGrid != null && accepts(where, carrier, onTheGrid) ? onTheGrid : null;
    }

    /**
     * What the position admits, where the place being composed is a value of it.
     *
     * <p>Asked of the location the number is read from rather than of the number, because one location
     * is measured at as many numbers as the rules name of it and admits one set of values — a rule
     * about one of those numbers is what leaves the others short, which is the whole reason this set
     * is here.
     *
     * <p><b>And every value there is where the number is one taken of the position rather than its
     * own.</b> {@code String.length(code)} counts a string and the place composed for it is a count;
     * the set holds the strings. Put to it, every count would be refused for not being one of them,
     * and a boundary on a length would stop being offered a row at all. What a value carrying that
     * count looks like is asked where such a value is written ({@link Witnesses}) and the set reaches
     * it there.
     */
    private static AdmittedValues.Admitted whatTheValuesAre(NumericTerm.FromOnePosition term,
                                                            AdmittedValues admitted) {
        return term instanceof NumericTerm.ValueOf ? admitted.at(term.position())
                : new AdmittedValues.Admitted.Values(ValueSet.ANY);
    }

    /**
     * A place inside the run this item is, that the position's values take in.
     *
     * <p>The end the item is named for is tried first, in each run in turn. That is what makes the
     * row one beside the boundary rather than one at the far side of the partition, and the item
     * carries which end it is: asked for a second time by a caller, a run could be read from the end
     * it is not named for, which is one decision given from two places.
     *
     * <p>And the values themselves once every one of those ends is refused. Which is the same
     * arrangement {@link Carrier#somethingOtherThan} is under, reached for the same reason: the ends
     * read better in a row than anything worked out of a set, and they are not exhaustive — a run
     * bounded by a rule about one number of the position starts at the one value a rule about another
     * refuses, and nothing about the run says so. The set is crossed with the run rather than asked
     * on its own, so a value it holds inside the run is not lost to whichever value it names first.
     *
     * <p><b>An allowance per crossing, which is why what arrives is the way to get one.</b> An item
     * is a region and a region is as many runs as the rules leave it, so this crosses the set with
     * each of them in turn — and a meter spends down. Shared between the runs, what the first
     * crossing cost would be taken off what the second may spend, and whether a run is answered
     * would follow from where it came in the order they are looked at. Which order that is is a
     * searching policy and no answer about the values ({@link LevelRegion}).
     */
    private static Place someValueIn(Criterion.Within within, Carrier carrier,
                                     NumericDomain.Bounds bounds, ValueSet admits,
                                     Supplier<Meter> allowance) {
        LevelSpace space = LevelSpace.onACarrier(carrier);
        List<LevelInterval> runs = within.runsInside(carrier, bounds.min(), bounds.max());
        for (LevelInterval look : runs) {
            if (space.witness(look, within.away()).level() instanceof Level.OnACarrier on
                    && carrier.admitted(admits, on.at())) {
                return on.at();
            }
        }
        for (LevelInterval look : runs) {
            OrderedInterval run = runOf(look, carrier);
            Place held = run == null ? null
                    : carrier.somewhereIn(admits, run, List.of(), allowance.get());
            if (held != null) {
                return held;
            }
        }
        return null;
    }

    /**
     * One run of the levels as a run of the carrier's own places, or null where its ends are not
     * places of the position.
     *
     * <p>Null where an end says how much of the quantity a rule wrote rather than a value of it. Such
     * a run is in the written form's units, and a set of the position's values has nothing to say
     * about a number in them — put to one, the set would be answering about a value nobody holds.
     */
    private static OrderedInterval runOf(LevelInterval look, Carrier carrier) {
        Endpoint low = endOf(look.low(), carrier);
        Endpoint high = endOf(look.high(), carrier);
        boolean lost = (look.low() != null && low == null)
                || (look.high() != null && high == null);
        return lost ? null : new OrderedInterval(low, high);
    }

    /** One end of such a run, or null where it is not a place of the position. */
    private static Endpoint endOf(Bound end, Carrier carrier) {
        if (end == null || end.at().per().compareTo(BigDecimal.ONE) != 0) {
            return null;
        }
        return end.at().written() instanceof Level.OnACarrier on && on.of().equals(carrier)
                ? new Endpoint(on.at(), end.inclusive()) : null;
    }

    /**
     * Whether a place of {@code carrier} is at this item.
     *
     * <p>The item's own answer and not a second reading of it. Whether a value stands at an item is
     * one question with one answer ({@link Criterion#holds}); worked out again from an order and a
     * level the item was written against, the two came apart wherever the item is a run — a run has
     * two ends and a line, and one comparison cannot say all three.
     */
    private static boolean accepts(Criterion where, Carrier carrier, Place at) {
        return where.holds(new Level.OnACarrier(carrier, at));
    }

    /**
     * Where both positions of a line between them can stand, once the level's distance is taken off
     * the first.
     *
     * <p>What proves a row can be written on such a line. The line is where the two positions are
     * equal, so a row on it writes one place at both — and whether one exists is the two positions'
     * ranges read together, which is a question the rules answer without anything being built.
     *
     * <p>Null is not a proof of the opposite. Two ranges that leave no place leave none, and that is a
     * fact about the rules; a range this could not read in full is a range this did not read, and the
     * caller is the one holding whether that happened.
     */
    static NumericDomain.Bounds commonRange(NumericDomain.Bounds on, NumericDomain.Bounds against,
                                            Carrier carrier, Count apart) {
        NumericDomain.Bounds moved = carrier.counts() ? shifted(on, apart.negate()) : on;
        return new NumericDomain.Bounds(
                Endpoint.lower(moved == null ? null : moved.min(),
                        against == null ? null : against.min()),
                Endpoint.upper(moved == null ? null : moved.max(),
                        against == null ? null : against.max()));
    }

    /**
     * What the rules leave one position, read as what they leave the other one standing that far
     * from it.
     *
     * <p>The distance is part of the question. Two positions each left {@code [0, 100]} have every
     * place in common where the rule cuts where they meet, and only {@code [1, 100]} where it holds
     * them one apart — the pair at zero would put the first at minus one, which its own rules
     * refuse. Intersected without the distance, the search offered exactly that pair and the report
     * said every value tried had been refused.
     */
    private static NumericDomain.Bounds shifted(NumericDomain.Bounds bounds, Count by) {
        if (bounds == null) {
            return null;
        }
        return new NumericDomain.Bounds(moved(bounds.min(), by), moved(bounds.max(), by));
    }

    private static Endpoint moved(Endpoint end, Count by) {
        return end == null || !(end.at() instanceof Count count) ? end
                : new Endpoint(count.plus(by), end.inclusive());
    }

    /**
     * A placement handed back, or nothing composed where the rules were shown to leave none.
     *
     * <p><b>The one place a placement becomes an answer.</b> What a search may hand back is the same
     * thing whatever it was searching for — one position at a place of its carrier, two of them a
     * distance apart, a form at a level — and each of those was written on its own. Written on its
     * own, each also had to remember the last step, and two of the three did not: a shape whose
     * search is added later inherits the obligation only if there is one place that carries it.
     *
     * <p>What it means is that the rules were not shown to refuse this placement. Not that a value
     * exists — nothing here builds one, and an emptiness nobody proved is not a value proven to
     * exist. What it removes is narrower and is what a search over ranges gets wrong: a placement
     * the rules are already known to refuse, offered as a row and then reported as though the point
     * had nothing at it.
     */
    private Realization found(Map<RealizationTarget, Place> fixing,
                              souther.compiler.inputs.SearchRegion within) {
        return theRulesHaveNotRefused(fixing, within)
                ? new Realization.Found(fixing)
                : Realization.Unknown.nothingComposedOne();
    }

    /**
     * Whether the rules, with every position of an item fixed at what was chosen, were not shown to
     * leave nothing.
     *
     * <p>Not that a value exists. Nothing here builds one, and an emptiness nobody proved is not a
     * value proven to exist — what settles that is the row itself, where it is built. What this
     * refuses is narrower and is the whole of what a search over ranges gets wrong: an assignment
     * the rules are already known to refuse, offered as a row and then reported as though the point
     * had nothing at it.
     *
     * <p>Of the whole assignment, because that is what an assignment is. A relation between two
     * positions is in neither of their ranges, so a walk that held each of them to its own ends has
     * checked nothing about the pair.
     *
     * <p><b>Places that are not numbers settle nothing here, so a placement made of them is handed
     * back unheld.</b> A rule relating two strings is one the arithmetic has no word for, and what
     * it leaves them is not something this can be asked — so what is promised is that a placement
     * the rules were shown to refuse is not offered, and it is promised where the rules can be asked
     * about the values in it. Anything more would be a claim about an order this reading does not
     * reach.
     */
    private boolean theRulesHaveNotRefused(Map<RealizationTarget, Place> fixing,
                                           souther.compiler.inputs.SearchRegion within) {
        Map<NumericTerm, Count> counted = new LinkedHashMap<>();
        fixing.forEach((target, at) -> {
            if (at instanceof Count count) {
                counted.put(target.term(), count);
            }
        });
        return counted.isEmpty() || within.given(counted).emptiness().isEmpty();
    }

    /** The same, of the rules as some of the positions have been fixed. */
    private static NumericDomain.Bounds bounds(souther.compiler.inputs.SearchRegion rules,
                                               NumericTerm term) {
        NumericDomain.Bounds held = rules.runsBetween(term);
        return held == null ? new NumericDomain.Bounds(null, null) : held;
    }

    private static Place placeOf(Level level) {
        return switch (level) {
            case Level.OnACarrier on -> on.at();
            case Level.ACount count -> count.at();
        };
    }
}
