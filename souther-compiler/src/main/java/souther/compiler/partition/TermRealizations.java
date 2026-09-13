package souther.compiler.partition;

import souther.compiler.check.Carrier;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermOrders;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.CountDomain;
import souther.compiler.numeric.Dates;
import souther.compiler.numeric.Place;
import souther.compiler.semantics.TakenAs;
import souther.compiler.types.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The values that put a term at a number, which is the other direction of reading a
 * {@link NumericTerm} off an observation.
 *
 * <p><b>Not its inverse.</b> Reading is not injective and building cannot undo it: many strings are
 * five long, and every time in an hour falls in that hour. What holds is one way round — every value
 * built here reads back as the number it was built for. Written as an inverse, the second operation
 * to be added would have been the one that broke it, and the way it would have broken is a row
 * offered at an edge it does not stand on.
 *
 * <p>And whether anything exists to build is a third statement, declared of the operation
 * ({@code EveryAnswerItCanGiveHasASourceValue}) and asked where an edge is claimed to be writable.
 * A reader that took "there is an arm for this" for "this always builds" would promise a row for
 * every count of a {@code Set<Bool>}.
 *
 * <p>Here and not on the term. What a term is measured by and what it reads are answers about the
 * quantity; what a value of it looks like written down is the generator's, and a term that answered
 * it would name the generator's own vocabulary from {@code inputs} — a dependency the wrong way
 * round. What the term owes is the identity, which is the operation, and the arms below are keyed on
 * what {@code semantics} declares of it.
 */
final class TermRealizations {

    /** What building values that answer a number came to. */
    sealed interface Realization {

        /**
         * Values that answer it, and what was not built.
         *
         * <p>Two halves of one answer, as {@link Witnesses.Sized} keeps them. A caller reading only
         * the first says every value was refused where some were never built, which is a different
         * thing to tell an author.
         *
         * <p><b>Everything the offer leaves out, and not only what a figure refused.</b> A walk that
         * went everywhere it knows how to go and a walk a figure turned back both leave values a
         * reader was not shown, and the reader deciding whether every value was refused needs the
         * two alike. Which of them a figure came to be here by is what {@link Stopped} is told
         * apart by, and it is not this.
         */
        record Built(List<FixtureTemplate> values, java.util.Set<CompositionBudget> heldBack,
                     java.util.Set<CompositionRepertoire> notAllOf) implements Realization {

            public Built {
                values = List.copyOf(values);
                heldBack = java.util.Set.copyOf(heldBack);
                notAllOf = java.util.Set.copyOf(notAllOf);
                if (values.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a realization that built nothing is one that built none, and says why");
                }
            }

            Built(List<FixtureTemplate> values, java.util.Set<CompositionBudget> heldBack) {
                this(values, heldBack, java.util.Set.of());
            }

            static Built whole(List<FixtureTemplate> values) {
                return new Built(values, java.util.Set.of());
            }
        }

        /**
         * A budget of this compiler's stopped the composing, and no value came of it.
         *
         * <p>Apart from {@link None} and the difference is the whole of why this is here. Both are
         * nothing built; only this one is a policy of this compiler's having run out, and only a
         * policy running out leaves the question of whether a value exists open in a way somebody
         * could act on by raising it.
         *
         * <p><b>So a figure is here only where a candidate was in front of the walk and this figure
         * left no room for it.</b> Raise it and that candidate gets tried — which is what makes the
         * word one an author can act on. A population this compiler walks some of stopped nothing
         * and is never a figure: it travels beside them ({@code notAllOf}) where a figure was met as
         * well, and where none was it is {@link Unexhausted} rather than anything here.
         *
         * <p>Never where a value was built. A budget that cut an offering short after something was
         * composed is {@link Built#heldBack()}: what it stopped is the rest of the offer, and the
         * point it was composed for has a value at it either way.
         */
        record Stopped(java.util.Set<CompositionBudget> by,
                       java.util.Set<CompositionRepertoire> notAllOf) implements Realization {

            public Stopped {
                by = java.util.Set.copyOf(by);
                notAllOf = java.util.Set.copyOf(notAllOf);
                if (by.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a composing this compiler stopped says which budget stopped it");
                }
            }

            Stopped(java.util.Set<CompositionBudget> by) {
                this(by, java.util.Set.of());
            }
        }

        /**
         * Nothing was composed, and what this walked was some of what there is to walk.
         *
         * <p>Apart from {@link None}, and the difference is what a reader may conclude. Nothing was
         * refused by a figure, so there is no number to raise; and nothing here looked at every
         * value the point has, so an emptiness this came to establishes nothing about the model.
         * Told as {@code None}, a reader acts on a search that never wrote most of what it was
         * searching.
         *
         * <p>Apart from {@link Stopped} for the same reason in the other direction. A figure is
         * somebody's to raise and reaches what the search was holding; what reaches the rest of one
         * of these is somebody writing the rest, which is not work an author of a model can do.
         *
         * @param detail what this walk found, or null where it has nothing to add
         */
        record Unexhausted(java.util.Set<CompositionRepertoire> notAllOf, String detail)
                implements Realization {

            public Unexhausted {
                notAllOf = java.util.Set.copyOf(notAllOf);
                if (notAllOf.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a walk that says it saw some of them says some of what");
                }
            }
        }

        /**
         * Nothing here writes a value answering it, and no budget of this compiler's is why.
         *
         * <p>Which is a walk that looked everywhere it was going to look and found nothing, and not
         * one that declined to look. A figure may still have bounded what it offered — the ways a
         * total is spread are two of the many however far a search runs — and that is a thing to
         * say about an offer rather than a reason there is none.
         *
         * <p>The word is how a reader downstream treats it and {@code detail} is what happened in
         * this attempt, which is the arrangement {@link Generator.UnresolvedCombination} has. A
         * word read for the shape of the question outlives whatever made it true, so what a reader
         * is owed beyond it is evidence rather than another word.
         *
         * @param detail what this walk found, or null where it has nothing to add to the word. Two
         *               of these that came about differently do not carry the same sentence: a
         *               reader told the same thing twice is the reader working the difference out
         *               from an absence
         */
        record None(Generator.UnresolvedCombination.Reason why, String detail)
                implements Realization {

            None(Generator.UnresolvedCombination.Reason why) {
                this(why, null);
            }
        }
    }

    /**
     * Whether one value of the position is the only one that answers a given number.
     *
     * <p>Asked here and keyed on the account of what is taken, because that account <em>is</em> the
     * algorithm and whether its inverse is single-valued is a property of the algorithm. Read off
     * the kind of term instead, every operation answering a number of a location was many-valued —
     * which is true of the two there are and is not what being one of them means. An injective
     * intrinsic would be a term of the same kind and would have been treated as many-valued, with
     * nothing saying so: the same defect {@code SizeOf} was, at a smaller size (#1027).
     *
     * <p>Answered without building anything, because the readers that ask are deciding whether to
     * try. What a value looks like is {@link #at}'s and costs what it costs.
     */
    static boolean onlyOneValueAnswersIt(RealizationTarget target) {
        return switch (target.term()) {
            // The number is the value, so it is the one value there is.
            case NumericTerm.ValueOf _ -> true;
            case NumericTerm.TakenOf taken -> switch (taken.takenAs()) {
                // Every container of that many answers it, every time within that hour does, and
                // every date in that year falls in it. So does every container adding up to a
                // total: one element at the whole of it, or two that come to it between them.
                case TakenAs.HowManyItHolds _, TakenAs.TheSumOfWhatItHolds _,
                     TakenAs.PartOfTime _, TakenAs.PartOfDate _ -> false;
            };
            // Every container whose values come to it answers it, whichever account is taken over
            // them. A run is many values by construction, so no account of one is met by a single
            // container.
            case NumericTerm.TakenOver _ -> false;
        };
    }

    /**
     * The values to write at {@code target}'s root so that its number is {@code answer}, given what
     * the root holds.
     *
     * <p><b>The one owner of what puts a number where a search asked for it.</b> Which target it is
     * says which value is rebuilt; what is written into that value is the account the operation
     * declares. Exhaustive over both, with no {@code default}, so a term of a new kind and an
     * account added to the language are each questions this file has to answer rather than
     * conditions falling to whichever arm was written last.
     *
     * <p>A target that exists and a target nothing writes at are two different sentences, and both
     * of them are said here. {@link RealizationTarget} answers the first for every number there is;
     * the second is a {@link Realization.None}, a {@link Realization.Stopped} or a
     * {@link Realization.Unexhausted}, which differ in what a reader may do about it and not in
     * whether a value was written.
     */
    static Realization at(Type sourceType, TermOrders orders,
                          Place answer, souther.compiler.inputs.SearchRegion within,
                          RuleReadingContext reading) {
        if (sourceType == null) {
            return new Realization.None(
                    Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        RuleReadingSource ruleSource = reading.source();
        // Which number is being written for, read off the answer that says which number it is of.
        // Handed in beside it, it was a second name for the same thing and a caller could give two
        // — and this would then write a value for one number on the order of another.
        RealizationTarget target = RealizationTarget.of(orders.term());
        return switch (target.term()) {
            // Written by the carrier the line was drawn on, and wearing every name the position
            // declares. Read off the boundary's own shape instead, a count on one carrier could be
            // written as a literal of another — which is how a date-time's second count reached a
            // row as an `Int`, and the decoder refused it with the report saying only that every
            // value tried had been refused.
            case NumericTerm.ValueOf _ ->
                    oneValue(FixtureTemplate.on(orders.answered(), answer, ruleSource.symbols().scope()::reach),
                            sourceType, ruleSource);
            case NumericTerm.TakenOf taken -> taken(taken.takenAs(), sourceType, orders,
                    answer, within, reading);
            case NumericTerm.TakenOver over -> overARun(over.takenAs(), sourceType, orders,
                    answer, within, reading);
        };
    }

    /**
     * The values a given operation answers a number at.
     *
     * <p>One arm per declared account of what such an operation takes, and no default — the same
     * closure the reading is under, so an account added to {@code semantics} cannot be read off a
     * row without also being writable onto one. Split between two switches that did not have to
     * agree, an operation would have gained a boundary nobody could write a row for, and the report
     * would have said only that every value tried was refused.
     */
    private static Realization taken(TakenAs how, Type sourceType,
                                     TermOrders orders, Place answer,
                                     souther.compiler.inputs.SearchRegion within,
                                     RuleReadingContext reading) {
        RuleReadingSource ruleSource = reading.source();
        return switch (how) {
            // A container has no order of its own and is built out of what it holds, so this arm
            // takes none. That is the arm's own answer and not an order standing in for nothing.
            case TakenAs.HowManyItHolds _ -> holding(sourceType, answer, reading);
            // A container whose elements come to the total, which is what a row has to hold for
            // this number to be there. What that takes is choosing how many elements and what each
            // of them holds — one question whether the number is added up out of the container
            // itself or out of a path inside its elements, and answered for both in one place.
            case TakenAs.TheSumOfWhatItHolds _ -> ContainersAddingUp.to(answer, sourceType,
                    orders, within, reading);
            // And this one writes on the order the value is written on. Written on the order the
            // answer is measured on, the thirteenth hour would be offered as the thirteenth second —
            // the same mistake the reading makes in the other direction, which is why the pair
            // travels this far and the arm takes the end (#1027).
            case TakenAs.PartOfTime taken ->
                    atThatPart(taken.part(), sourceType, orders.observed(), answer, ruleSource);
            case TakenAs.PartOfDate taken ->
                    onThatPart(taken.part(), sourceType, orders.observed(), answer, ruleSource);
        };
    }

    /**
     * The values a walk over a run answers a number at.
     *
     * <p>One arm per account, and no default, the way the taking of one value beside it is — the
     * same closure the reading of a run is under ({@code NumericTerm.TakenOver.readOver}), so an
     * account added to {@code semantics} cannot be read over a run without also being writable into
     * one.
     *
     * <p>And the arms answer alike on both sides. An account of a number taken of one value says
     * nothing about a run of them — which hour a run of times falls in is not a question — so the
     * reading answers that this is no number of theirs, and nothing composes a container for a
     * number nothing reads.
     */
    private static Realization overARun(TakenAs how, Type sourceType,
                                        TermOrders orders, Place answer,
                                        souther.compiler.inputs.SearchRegion within,
                                        RuleReadingContext reading) {
        return switch (how) {
            case TakenAs.TheSumOfWhatItHolds _ -> ContainersAddingUp.to(answer, sourceType,
                    orders, within, reading);
            case TakenAs.HowManyItHolds _, TakenAs.PartOfTime _, TakenAs.PartOfDate _ ->
                    new Realization.None(
                            Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        };
    }

    /** Values of the position holding exactly that many, which is {@link Witnesses}' answer. */
    private static Realization holding(Type sourceType, Place answer, RuleReadingContext reading) {
        int many = CountDomain.asCount(answer);
        if (many < 0) {
            return new Realization.None(
                    Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        RuleReadingSource ruleSource = reading.source();
        TypeView holder = TypeView.of(sourceType, ruleSource.inners(), ruleSource.symbols(), ruleSource.published());
        // A name this module cannot write leaves no value to write, which is a position nothing
        // composes one for rather than a value written without the name. Asked of the position
        // before anything is built for it, since it is the same answer for every value.
        if (!(WornNames.of(holder.wrappers(), ruleSource) instanceof WornNames.Spelled worn)) {
            return new Realization.None(
                    Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        Witnesses.Sized built = Witnesses.ofSize(holder, many, reading, Set.of());
        if (built.values().isEmpty()) {
            // Read off the build that was already done. `Witnesses` keeps what it made and why it
            // stopped as two halves of one answer for exactly this, and asking it again would be
            // the same decision taken twice — the two could not disagree today and there is no
            // reason to leave a second taking of it here.
            return built.heldBack().isEmpty()
                    ? new Realization.None(
                            Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE)
                    : new Realization.Stopped(built.heldBack());
        }
        List<FixtureTemplate> out = new ArrayList<>();
        for (FixtureTemplate each : built.values()) {
            out.add(RepresentativeSource.under(worn.names(), each));
        }
        return new Realization.Built(out, built.heldBack());
    }

    /**
     * A time of day whose given part stands at that number, with the parts below it at nought.
     *
     * <p>One of the many, and not the many. Every time in that hour answers the same hour, and
     * which of them is offered is this reader's to choose — what it owes is that what it offers
     * reads back, not that it enumerates the inverse. Nought below is the plain choice: the hour on
     * the hour.
     *
 * <p>The order is handed in and not named here. That what this is taken of is a time is the
     * arm's own condition and the library is held to it, but which carrier a time is written on is
     * {@link Carrier}'s one answer — named here, this would be a second place saying what a time
     * counts, and the two would part the day the first one moved.
     */
    private static Realization atThatPart(TakenAs.TimePart part, Type sourceType, Carrier observed,
                                          Place answer, RuleReadingSource ruleSource) {
        if (observed == null || !(answer instanceof Count count) || !count.whole()
                || count.signum() < 0
                || count.at().compareTo(java.math.BigDecimal.valueOf(part.many())) >= 0) {
            // Outside the parts a day has. Not this reader's to report as a refusal: what a part
            // runs between is the operation's declared bound, and a number outside it is a number
            // nothing answers.
            return new Realization.None(
                    Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        Place seconds = Count.of(count.at()
                .multiply(java.math.BigDecimal.valueOf(part.seconds())));
        FixtureTemplate standing = WornNames.under(
                TypeView.of(sourceType, ruleSource.inners(), ruleSource.symbols(), ruleSource.published()).wrappers(),
                FixtureTemplate.on(observed, seconds, ruleSource.symbols().scope()::reach), ruleSource);
        return standing == null
                ? new Realization.None(
                        Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE)
                : Realization.Built.whole(List.of(standing));
    }

    /**
     * A date whose given part stands at that number, with the parts beside it at the first they run
     * from.
     *
     * <p>One of the many, as the time above offers one of the many. Every date in the year answers
     * the same year, and what this owes is that what it offers reads back — not that it enumerates
     * the dates that would. The first of January is the plain choice, and it is a choice about which
     * value to write down rather than anything the model said.
     *
     * <p>A day of the month is offered in the longest month there is, so every day a date can fall
     * on is a day of the one this writes. Offered in a short month, the last days of the long ones
     * would be days no date has, and a rule about the thirty-first would have no witness for a
     * reason that is about this choice rather than about the calendar.
     *
     * <p>Whether a date can have the part at all is asked of the calendar and not of the bound the
     * operation declares. A bound is what the model may assume of an answer; what dates there are is
     * what a witness can be built from, and reading the second off the first would make a bound
     * loosened by hand into dates that cannot be written.
     */
    private static Realization onThatPart(TakenAs.DatePart part, Type sourceType, Carrier observed,
                                          Place answer, RuleReadingSource ruleSource) {
        if (observed == null || !(answer instanceof Count count) || !count.whole()) {
            return new Realization.None(
                    Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        java.time.LocalDate on = dateOn(part, count.at());
        if (on == null) {
            // Outside the parts a date has. Not this reader's to report as a refusal: a number no
            // date answers is a number nothing composes one for.
            return new Realization.None(
                    Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        FixtureTemplate standing = WornNames.under(
                TypeView.of(sourceType, ruleSource.inners(), ruleSource.symbols(), ruleSource.published()).wrappers(),
                FixtureTemplate.on(observed, Dates.dayOf(on), ruleSource.symbols().scope()::reach),
                ruleSource);
        return standing == null
                ? new Realization.None(
                        Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE)
                : Realization.Built.whole(List.of(standing));
    }

    /**
     * The date this offers for a part standing at {@code answer}, or null where no date has that
     * part.
     *
     * <p>Asked of the calendar rather than tried and caught. What a year, a month and a day run
     * between is something {@code java.time} answers, and building a date to find out whether one
     * could be built is asking a question by reading the exception from the answer.
     */
    private static java.time.LocalDate dateOn(TakenAs.DatePart part, java.math.BigDecimal answer) {
        return switch (part) {
            case YEAR -> within(answer, java.time.LocalDate.MIN.getYear(),
                    java.time.LocalDate.MAX.getYear())
                    ? java.time.LocalDate.of(answer.intValueExact(), A_LONGEST_MONTH, FIRST_OF_THE_MONTH)
                    : null;
            case MONTH -> within(answer, java.time.temporal.ChronoField.MONTH_OF_YEAR)
                    ? java.time.LocalDate.of(A_YEAR, answer.intValueExact(), FIRST_OF_THE_MONTH)
                    : null;
            case DAY -> dayOfTheMonthItIsOfferedIn(answer);
        };
    }

    /**
     * The date a day of the month is offered on, or null where that month has no such day.
     *
     * <p>How far the days run is asked of the month this offers them in, and not of how far a day of
     * any month can run. The two agree only while that month is the longest there is, and a month
     * chosen here that was not would leave the check admitting days the date cannot be built for —
     * an answer that is refused rather than absent, at whichever value the two parted.
     */
    private static java.time.LocalDate dayOfTheMonthItIsOfferedIn(java.math.BigDecimal answer) {
        java.time.YearMonth month = java.time.YearMonth.of(A_YEAR, A_LONGEST_MONTH);
        return within(answer, 1, month.lengthOfMonth()) ? month.atDay(answer.intValueExact()) : null;
    }

    /** The year a month or a day is offered in. Every month is a month of every year, and the month
     *  below is as long in any of them, so which year this is says nothing. */
    private static final int A_YEAR = 2001;

    /** January, which has as many days as any month has, so every day-of-month a date can fall on is
     *  a day of this one. */
    private static final int A_LONGEST_MONTH = 1;

    /** The day a year or a month is offered on. */
    private static final int FIRST_OF_THE_MONTH = 1;

    private static boolean within(java.math.BigDecimal answer,
                                  java.time.temporal.ChronoField field) {
        return within(answer, field.range().getMinimum(), field.range().getMaximum());
    }

    private static boolean within(java.math.BigDecimal answer, long from, long to) {
        return answer.compareTo(java.math.BigDecimal.valueOf(from)) >= 0
                && answer.compareTo(java.math.BigDecimal.valueOf(to)) <= 0;
    }

    /** One value, wearing every name the position declares, or the reason there is none. */
    private static Realization oneValue(FixtureTemplate bare, Type sourceType, RuleReadingSource ruleSource) {
        FixtureTemplate standing = WornNames.under(
                TypeView.of(sourceType, ruleSource.inners(), ruleSource.symbols(), ruleSource.published()).wrappers(), bare, ruleSource);
        return standing == null
                ? new Realization.None(
                        Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE)
                : Realization.Built.whole(List.of(standing));
    }

    private TermRealizations() {}
}
