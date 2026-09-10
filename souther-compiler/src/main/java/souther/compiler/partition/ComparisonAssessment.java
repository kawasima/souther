package souther.compiler.partition;

import souther.compiler.check.StatedComparison;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.diag.Citation;
import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.InputReading;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Quantities;
import souther.compiler.inputs.FilingCoordinate;
import souther.compiler.numeric.Place;
import souther.compiler.reach.ComparisonArrival;
import souther.compiler.types.BindingId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

/**
 * What one comparison comes to on the input space: one reading, and everything read off it.
 *
 * <p><b>One decision with several projections, and not several decisions about one comparison.</b>
 * What a comparison cuts is {@link Cutting}'s one answer, taken from the canonical form the
 * arithmetic reads. What the comparison is a rule <em>about</em> used to be worked out beside that,
 * from the operands as they were written — and the two disagreed. {@code a + 1 <= 10} cut position
 * {@code a} at nine and was classified as naming no position, so a border was drawn and no
 * obligation recorded against it; {@code a <= b - b + 9} cut the same position at the same value and
 * was classified as a rule about a pair, so the question raised was about a place that rule never
 * stopped. A rule was measured by one reading and reported by the other.
 *
 * <p>So the subject is derived here, from the same value the line is, and the disagreement has
 * nowhere to live. Which positions the quantity is over decides both what the rule is about and
 * where the border goes, because both come off {@link Cutting#dividedPosition()} and it is asked
 * once.
 *
 * <p><b>And what a read comparison owes is owed by having been read.</b> The rows at the value it
 * singles out, and the rows in the classes its line makes, are asked for by the reading that found
 * the line — there is no moment at which such a demand is outstanding. So they are the partition's
 * geometry and not a coverage question standing against an answer: carried as both, one decision
 * had two representations again, and the second had no reader once it could never go unanswered.
 *
 * <p><b>Six ways a comparison leaves the positions nothing, and they are six.</b> Read to the end
 * and cutting nothing, naming no position at all, reading the answer, cutting where the quantity
 * does not run, cutting where the rows that arrive stop short, and not read — each is a different
 * sentence to whoever is told it, and only the last is about a limit of this compiler. Held as one,
 * a tautology was owed a row where the relation changes and a rule this could not read was
 * described as naming no position.
 */
sealed interface ComparisonAssessment {

    /**
     * What a comparison does to the quantity it cuts.
     *
     * <p>Read off the canonical quantity together with the operator, and read once. The operator
     * alone does not answer it: {@code 2 * a == 8} names four and {@code 2 * a == 9} names no whole
     * number at all, under one operator and one shape of rule.
     */
    enum Places {

        /** An order across the line, so rows are owed either side of it and the two sides have
         *  roles. */
        ACROSS_THE_VALUE,

        /** One value put in a class of its own, which has no sides: the values either side of it
         *  are one class. */
        AT_THE_VALUE,

        /** A value the quantity does not hold, which puts nothing in a class of its own. */
        AT_NO_VALUE
    }

    /**
     * The comparison cuts one position's own values.
     *
     * @param cutting  the line, which is what a threshold and a border are read off
     * @param position the position it divides
     * @param value    the value of the position the classes meet at, or null where the position
     *                 holds none there
     */
    record AtAPosition(Cutting cutting, NumericTerm.FromOnePosition position, Place value,
                       Places places)
            implements ComparisonAssessment {

        public AtAPosition {
            if (cutting == null || position == null || places == null) {
                throw new IllegalArgumentException("a line on a position names the position");
            }
        }
    }

    /**
     * The comparison cuts a form over more than one position, so it divides none of them.
     *
     * <p>A distance and a general form alike. What coverage asks of both is one question — no
     * position is divided, and the place is the comparison's to name — and which of the two shapes
     * the quantity is stays with the quantity.
     */
    record AcrossPositions(Cutting cutting, Citation at, Places places)
            implements ComparisonAssessment {

        public AcrossPositions {
            if (cutting == null || at == null || places == null) {
                throw new IllegalArgumentException("a line over a form names the form");
            }
        }

        /** Whether what it cuts is a number read over a run of values rather than a form over
         *  positions, which is a different thing to tell a reader who found no partition. */
        boolean overARun() {
            return cutting.quantity().direction().keySet().stream()
                    .anyMatch(term -> term.atOnePosition() == null);
        }
    }

    /** The comparison reads what the behavior answers. */
    record AnswerDependent() implements ComparisonAssessment {}

    /** The comparison names no position of the behavior's input. */
    record NoInput() implements ComparisonAssessment {}

    /**
     * Read to the end, and the quantity it cuts is nothing: the positions cancel.
     *
     * <p>{@code filedAt} is where the rule is said to have cut nothing, and it comes off the
     * reading rather than off a second walk over the operands. What is left of the quantity is not
     * what the rule is about — {@code a - a <= 0} is about {@code a} and cuts nothing — so the
     * coordinates are the numbers the reading named, whether or not they survived, and they are the
     * numbers rather than the places they sit at ({@link AffineReading#filedAt}).
     */
    record CutsNothing(List<FilingCoordinate> filedAt) implements ComparisonAssessment {

        public CutsNothing {
            filedAt = List.copyOf(filedAt);
        }
    }

    /** Read in full, and the quantity does not run as far as the line the rule draws. */
    record OutsideTheDomain(Cutting cutting) implements ComparisonAssessment {

        public OutsideTheDomain {
            if (cutting == null) {
                throw new IllegalArgumentException("a line outside the domain is still a line");
            }
        }
    }

    /**
     * Read in full, the quantity runs as far as the line — and no row that arrives at the
     * comparison holds a value at it.
     *
     * <p>Its own answer and not {@link OutsideTheDomain}, which is a fact about the declarations
     * alone and holds wherever the comparison stands. This one is about the place: the guards above
     * the comparison rule the line's values out, so the classes it would make are classes of
     * nothing and the rows they would ask for are rows nothing can write. An author told the first
     * would look at the rule for a line their declarations refuse, and the line is fine — what
     * refuses it is on the way.
     *
     * <p>Only a proof lands here: the whole state at the comparison shown empty, or the values that
     * arrive shown to stop short of the line. A comparison nothing could project an arrival for
     * keeps its line ({@link ComparisonArrival.NoProjection}).
     */
    record NothingArrivesAtItsLine(Cutting cutting) implements ComparisonAssessment {

        public NothingArrivesAtItsLine {
            if (cutting == null) {
                throw new IllegalArgumentException(
                        "a line nothing arrives at is still a line somebody wrote");
            }
        }
    }

    /**
     * Read in full, and the rules leave no input for any line to be about.
     *
     * <p>Its own answer and not {@link OutsideTheDomain}, which says the quantity exists and does
     * not run as far as this rule's line. Here nothing runs anywhere: the declarations reaching this
     * input admit no value at all, so neither this line nor any other is outside anything. Said as
     * the first, an author is sent to look at one rule for a contradiction that is not in it — two
     * clauses each admitting values are empty together, and neither of them is the one that failed.
     *
     * <p>Whose emptiness it is is the input's and not this comparison's. A quantity is a function of
     * the input, so a quantity's values are empty exactly when the input's are — asked of the
     * quantity, this would be a second reader deciding what the rules admit, and the two would
     * disagree about a model wherever one of them read a rule the other did not.
     */
    record NoFeasibleInput(souther.compiler.inputs.EmptyInput why, Cutting cutting)
            implements ComparisonAssessment {

        public NoFeasibleInput {
            if (why == null || cutting == null) {
                throw new IllegalArgumentException(
                        "an input the rules leave empty is still a line somebody wrote");
            }
        }
    }

    /**
     * The comparison names a position and the reading of it stopped.
     *
     * <p>Only a reason that says a reading stopped, which the type is what enforces. A rule read to
     * the end that divides no one position has its own arms here, and their reasons say nothing
     * fell short — handed to this one, a comparison whose carrier could not be read was described
     * as relating two positions, and a model short of a border came back complete.
     *
     * <p>One reason per place and not one for the comparison. Where a reading stopped there is no
     * quantity for the places to be one subject of, so each of them says what stopped it there — a
     * position met inside an expression this did not take apart is told nothing about what it
     * carries, and one asked about for itself is.
     *
     * @param why where the reading was looking and what it left there. The places are diagnostic
     *            positions and never the subject of a question: what such a rule is about is the
     *            part that was not read
     */
    record Unread(java.util.SequencedMap<FilingCoordinate,
            BlockReason.RuleReadingStopped> why) implements ComparisonAssessment {

        public Unread {
            if (why == null || why.isEmpty()) {
                throw new IllegalArgumentException("a reading that stopped says where and why");
            }
            why = java.util.Collections.unmodifiableSequencedMap(
                    new java.util.LinkedHashMap<>(why));
        }
    }

    /**
     * What {@code comparison} comes to, whoever wrote it.
     *
     * <p>The one way in. {@code answer} is the binding a clause calls what the behavior answers, or
     * null where the comparison is written in a body and there is nothing to be the answer.
     *
     * <p><b>The arithmetic answers before anything else is asked.</b> Which positions a rule is
     * about is what the quantity it cuts is over: every atom of a form is a number of a location
     * ({@link souther.compiler.inputs.InputNumber}), so a comparison that came to a line is about
     * the positions of that line and there is nothing left to look up. What the walk over the
     * expression says a side is made of is the answer for a rule that came to no line — where it is
     * the only account there is — and asked first it would be a second reading standing in front of
     * the first, able to veto it and never to add to it: two members of a written list holding one
     * form written two ways agree as arithmetic and are made of different things, so the rule they
     * state would come back as one about no input at all.
     */
    static ComparisonAssessment of(String behavior, StatedComparison comparison, Citation at,
                                   InputReading read, InputReads reads,
                                   BindingId answer,
                                   boolean drawnByAnInvariant) {
        Quantities quantities = read.quantities();
        // Asked first, and of the whole comparison. A rule that reads the answer anywhere in it is
        // one this reading does not put on the input space, whichever side the answer is on and
        // whatever else stands beside it: `value.n + query.offset <= 20` is about the answer and
        // about an input, and the input is no more measurable here for the input being named.
        if (readsAnswer(comparison.left(), answer) || readsAnswer(comparison.right(), answer)) {
            return new AnswerDependent();
        }
        return switch (Cutting.read(behavior, comparison, read, reads)) {
            case Cutting.Read.Cuts cuts ->
                    onTheQuantity(at, cuts.cutting(), quantities, drawnByAnInvariant);
            // Read to the end and cutting nothing, which is a fact about the rule and not a limit
            // of this compiler: `a <= a` holds of every row. Where the comparison names no position
            // either, there is no rule about a position to say it of — `2 > 1` is a comparison of
            // constants and states nothing anywhere.
            case Cutting.Read.CutsNothing over -> over.read().isEmpty()
                    ? aboutNoPosition(comparison, reads, read.symbols())
                    : new CutsNothing(AffineReading.filedAt(over.read()));
            // And where the reading stopped, its own answer for having stopped — decided where it
            // stopped rather than worked out again from the comparison afterwards. Here the walk
            // over the expression is the only account of what the rule is about, which is what it
            // is for.
            case Cutting.Read.Stopped stopped -> stopped.why().isEmpty()
                    ? aboutNoPosition(comparison, reads, read.symbols())
                    : new Unread(stopped.why());
            // And where the quantity was read and stands on no order this counts, the carrier is
            // what a reader is owed — at the quantity's own coordinates, because the quantity is
            // what such a rule is about. The word is what the reading established and not what is
            // left when several answers were absent: it says the values here carry no order to
            // draw a line on, and that is exactly what was found.
            case Cutting.Read.NoOrderToCountOn over -> over.over().isEmpty()
                    ? aboutNoPosition(comparison, reads, read.symbols())
                    : new Unread(atEachOf(over.over(),
                            new BlockReason.UnreadComparisonDomain()));
        };
    }

    /**
     * The same reading, on the narrower domain the run leaves at the comparison's line.
     *
     * <p>The declarations first and the place second, because the two are different sentences and
     * the first holds wherever the comparison stands. What is asked here is the same predicate on
     * the narrower domain, not a second reading of the rule — so a line the declarations already
     * dropped is not asked about again, and what arrives cannot put one back.
     *
     * <p><b>Apart from the reading, because the two are read off different trees.</b> What the rule
     * states is read where the language's operations stand; what arrives at a place is read where
     * they are expanded, which is where a run has places at all. Asked inside the reading, the
     * reading would be one no tree could answer on its own.
     *
     * <p>Only a proof drops a line. An arrival nothing could project restricts nothing and the line
     * stands, which is what {@link ComparisonArrival.NoProjection} says —
     * and it is not what a comparison the emitter numbered nothing for says, because that one is
     * not asked this at all.
     */
    static ComparisonAssessment narrowedByWhatArrives(
            ComparisonAssessment read,
            List<ComparisonArrival> arrivals,
            boolean drawnByAnInvariant) {
        Cutting cutting = switch (read) {
            case AtAPosition at -> at.cutting();
            case AcrossPositions across -> across.cutting();
            // Every other reading is one the declarations settled without reaching a line, and
            // there is nothing for a narrower domain to settle differently.
            default -> null;
        };
        // A rule watched nowhere is not asked this. What the places are is
        // {@link souther.compiler.coverage.EmittedComparisonState.Instrumented}, which is never
        // empty, and a comparison the emitter numbered nothing for is the other arm of that and
        // never reaches here. Answered with none, "all of them proved nothing arrives" is true of
        // no place at all, and the line would go for want of a proof rather than by one.
        if (arrivals.isEmpty()) {
            throw new IllegalArgumentException(
                    "a rule watched at no place is not one to ask what arrives at: " + read);
        }
        if (cutting == null) {
            return read;
        }
        // Every place the rule is watched at, and the line goes only where all of them proved
        // nothing reaches it. One rule may be written into the tree that runs more than once, and a
        // run through any of the copies is a run through the rule — so a proof about one of them is
        // a proof about that copy, and the line is what the model states about all of them.
        //
        // Which is why one place that could not be projected leaves the line where it was: what a
        // walk did not settle is not a proof that nothing arrives, and the line has to be dropped by
        // a proof rather than by the absence of one.
        for (ComparisonArrival arrival : arrivals) {
            boolean reaches = switch (arrival) {
                case ComparisonArrival.NothingArrives _ -> false;
                case ComparisonArrival.Values values ->
                        Border.reaches(cutting.at(), cutting.seam(), cutting.claim(),
                                drawnByAnInvariant, cutting.withinGiven(values));
                case ComparisonArrival.NoProjection _ -> true;
            };
            if (reaches) {
                return read;
            }
        }
        return new NothingArrivesAtItsLine(cutting);
    }

    /**
     * One answer at every one of {@code places}, kept in the order they were given.
     *
     * <p>Sound where the places are one subject: the coordinates of one quantity, or the places a
     * statement nothing read at all names. A reading that stopped has no such subject, and its
     * places are answered one at a time where it stopped.
     */
    static <R extends BlockReason.RuleWithoutLineReason> java.util.SequencedMap<FilingCoordinate, R>
            atEachOf(List<FilingCoordinate> places, R why) {
        java.util.SequencedMap<FilingCoordinate, R> out = new java.util.LinkedHashMap<>();
        places.forEach(each -> out.putIfAbsent(each, why));
        return out;
    }

    /**
     * What a comparison naming no position of the input comes to.
     *
     * <p>Two answers and not one. A comparison of constants is about nowhere, and there is nothing
     * for a sentence about a position to be about. A comparison over values an operation answered
     * is about somewhere — the position those values came from — and what the rule says about the
     * values <em>there</em> is what would take inverting whatever the operation did. Held alike,
     * the second went out as a rule about nothing, and the position it plainly concerns came back
     * as one the model states nothing about.
     */
    private static ComparisonAssessment aboutNoPosition(StatedComparison comparison,
                                                        InputReads reads, Symbols symbols) {
        SequencedMap<FilingCoordinate, BlockReason.RuleReadingStopped> why =
                new LinkedHashMap<>();
        GuardThresholds.cameFrom(comparison, reads, symbols, why);
        return why.isEmpty() ? new NoInput() : new Unread(why);
    }

    /** What a line comes to on the input space, from the quantity it is on. */
    private static ComparisonAssessment onTheQuantity(
            Citation at, Cutting cutting, Quantities quantities,
            boolean drawnByAnInvariant) {
        // Whether there is an input at all, before anything is asked about where its values run.
        // A quantity is a function of the input, so where the rules admit no input they admit no
        // value of any quantity — and every question below is about one quantity's values against
        // one rule's line, which is a question about a model that has some.
        java.util.Optional<souther.compiler.inputs.EmptyInput> empty = quantities.emptiness();
        if (empty.isPresent()) {
            return new NoFeasibleInput(empty.get(), cutting);
        }
        // The line and not one of its points. A rule drawing where the quantity never reaches
        // divides the position into nothing, and a reader told that the rule went unread would go
        // looking for a limit of this compiler that is not there.
        if (!Border.reaches(cutting.at(), cutting.seam(), cutting.claim(), drawnByAnInvariant,
                cutting.within())) {
            return new OutsideTheDomain(cutting);
        }
        NumericTerm.FromOnePosition divided = cutting.dividedPosition();
        if (divided == null) {
            // Named by the comparison that drew it, which is the one thing about such a place this
            // compiler can always say exactly. It is on no position, and writing it out would be as
            // much of it as a pretty-printer got.
            return new AcrossPositions(cutting, at, places(cutting));
        }
        Place value = cutting.singles() ? cutting.singledValue() : cutting.dividedValue();
        return new AtAPosition(cutting, divided, value, places(cutting));
    }

    /**
     * What the rule does to the quantity, from the canonical quantity and the operator together.
     *
     * <p>Not from the operator alone. {@code 2 * a == 8} names four and {@code 2 * a == 9} names no
     * whole number at all, under one operator: whether the value the rule wrote is one the quantity
     * holds is the quantity's answer, and it is asked of the quantity.
     *
     * <p>Asked of whether a value of a <em>position</em> could be written instead, a form over
     * several positions has none at all — so {@code a + b == 10}, which takes ten, came back naming
     * no value the quantity holds, alongside {@code 2 * a + 2 * b == 9}, which does not.
     */
    private static Places places(Cutting cutting) {
        if (!cutting.singles()) {
            return Places.ACROSS_THE_VALUE;
        }
        return cutting.takesTheValueItNames() ? Places.AT_THE_VALUE : Places.AT_NO_VALUE;
    }

    /**
     * Where a reader is sent for what this leaves the positions with, or empty where it leaves them
     * nothing.
     *
     * <p><b>Whose answer it is turns on whether the quantity was reached.</b> A rule that was read
     * is about its quantity, so the positions it is filed at are the quantity's — {@code a + b - b
     * + c <= 10} is {@code a + c <= 10}, and a note at {@code b} would say the rule relates a
     * position it does not mention. A reading that stopped has no quantity to be about, so the
     * positions the walk met are the whole of what can be said, and nothing here may be read as
     * what the rule is about.
     *
     * <p>A quantity that came out empty is the third: there are no coordinates to file at, and the
     * positions the comparison names are what makes {@code a <= a} worth saying at all — the model
     * names a position there and cuts nothing.
     *
     * <p>Answered here so that no caller chooses. Chosen at the two producers, one of them reached
     * for the walk's positions because that was the helper in hand, and a rule read from end to end
     * was filed at a position its arithmetic had cancelled.
     *
     * <p>Asked of nothing. Each case was filed where it was read and holds where; a comparison, an
     * environment and the module's names are what it takes to work that out, and working it out is
     * not what happens here.
     */
    default List<FilingCoordinate> filedAt() {
        return switch (this) {
            case AcrossPositions over -> over.cutting().over();
            case OutsideTheDomain outside -> outside.cutting().over();
            case NothingArrivesAtItsLine unarrived -> unarrived.cutting().over();
            // The positions its quantity is over, as every read rule's are. That the rules leave
            // the input empty says nothing about which positions this rule is about.
            case NoFeasibleInput none -> none.cutting().over();
            case Unread unread -> List.copyOf(unread.why().keySet());
            case CutsNothing cuts -> cuts.filedAt();
            case AtAPosition _, AnswerDependent _, NoInput _ -> List.of();
        };
    }

    /**
     * What the reading of lines leaves at each place this comparison is filed at, and empty where
     * it leaves nothing.
     *
     * <p><b>Named for the reading it is the answer of, and not for the assessment it is read
     * off.</b> What a comparison comes to is one decision; what a reading makes of it is that
     * reading's own, and the two do not agree even about one comparison — a rule relating two
     * positions is read here from end to end and places no line, and the reading that turns clauses
     * into sets of values gets nothing it can hold from the same rule. A name saying only that a
     * reason was read off an assessment would be reached for by that reader too, and the answer it
     * would take is the one that says nothing fell short.
     *
     * <p><b>Per place, and the same at every place only where the places are one subject.</b> A
     * comparison whose arithmetic reached a quantity is about that quantity, so its places are the
     * quantity's coordinates and one answer holds at all of them. A reading that stopped has no
     * quantity to be about, and each place it was left at says what stopped it there — handed one
     * answer, a position met inside an expression this did not take apart was told what another
     * position's carrier carries.
     *
     * <p>Answered once, here. Both producers of this evidence — a clause of an {@code ensures} and a
     * {@code guard}'s comparison — worked the same table out separately, so a case added to
     * {@link ComparisonAssessment} had to be answered twice and the two could disagree about one
     * comparison. That is the shape this whole type was made to have none of.
     *
     * <p>Empty rather than null, and no {@code default} on the switch. A comparison that drew a
     * line, one about no position of the input, and one about what the behavior answers each leave
     * nothing for a reader to be told, and saying so with an absent value made the absence a
     * sentinel one caller had to remember to test for. An arm added is a compile error in this
     * method and in the value reading's own beside it, which is the point of neither having a
     * default.
     */
    default java.util.SequencedMap<FilingCoordinate, BlockReason.RuleWithoutLineReason>
            whatEachPlaceIsLeftWith() {
        return switch (this) {
            // Which of the two a form that divides nothing is: a line over a run is one number and
            // one line with no position under it, and a line over several positions is a relation
            // between them. Answered from what the quantity is over rather than by the count of its
            // terms, since a form of one term is either.
            case AcrossPositions across -> sameAtEachPlace(across.overARun()
                    ? new BlockReason.ComparisonOverARun()
                    : new BlockReason.ComparisonBetweenPositions());
            case CutsNothing _ -> sameAtEachPlace(new BlockReason.ComparisonCuttingNothing());
            case OutsideTheDomain _ ->
                    sameAtEachPlace(new BlockReason.ComparisonCuttingOutsideDomain());
            // Not the reason above: there the declarations never run as far as the line, and here
            // they do — what stops short of it is the values that arrive at the comparison, ruled
            // out by the guards on the way. An author reading the first would look at one rule for
            // a contradiction with their declarations that is not in it.
            case NothingArrivesAtItsLine _ ->
                    sameAtEachPlace(new BlockReason.ComparisonNothingArrivesAtItsLine());
            // Its own answer for having stopped, decided where it stopped and at each place it was
            // left at. Worked out again from the comparison afterwards, one whose carrier stopped
            // the reading came back as a rule that relates two positions — a sentence saying no
            // measure is short of anything, over a model missing a border.
            case Unread unread -> new java.util.LinkedHashMap<>(unread.why());
            // Nothing about this rule fell short, and nothing about this rule is what happened. The
            // rules of the input admit no value between them, which is one fact about the behavior
            // and not one per rule at each position it names — said here, a model with two clauses
            // and four positions would be told eight times, and each time about a rule that is not
            // the one at fault.
            case NoFeasibleInput _, AtAPosition _, NoInput _, AnswerDependent _ ->
                    new java.util.LinkedHashMap<>();
        };
    }

    /** The same answer at every place this is filed at, which are one quantity's coordinates. */
    private java.util.SequencedMap<FilingCoordinate, BlockReason.RuleWithoutLineReason>
            sameAtEachPlace(BlockReason.RuleWithoutLineReason why) {
        return atEachOf(filedAt(), why);
    }

    /**
     * Whether this line is one a border is built on.
     *
     * <p>Only where the rule orders the values around it. A value singled out has no sides — the
     * values either side of it are one class — so there is nothing for a border to owe a row away
     * from, and a rule that names a value the quantity does not hold names no place at all.
     */
    default boolean drawsABorder() {
        return switch (this) {
            case AtAPosition at -> at.places() == Places.ACROSS_THE_VALUE;
            case AcrossPositions over -> over.places() == Places.ACROSS_THE_VALUE;
            case AnswerDependent _, NoInput _, CutsNothing _, OutsideTheDomain _,
                 NothingArrivesAtItsLine _, NoFeasibleInput _, Unread _ -> false;
        };
    }

    /**
     * Whether anything in {@code e} reads the binding a rule calls the answer.
     *
     * <p>A mechanical predicate over the tree, and it classifies nothing by itself. Two readers ask
     * it and reach different conclusions: {@link #of} answers that the comparison raises no
     * input-coverage obligation, and {@link EnsuresThresholds} answers that a rule about the answer
     * is not one this compiler failed to read. Written twice, the two came apart — the second had
     * the predicate and the first did not — so it is written once and neither reader owns what the
     * other makes of it.
     *
     * <p>Syntactic. {@code value.n - value.n + query.limit.value <= 20} does not depend on the
     * answer once the arithmetic is read, and this answers that it does. The atoms of the canonical
     * form are the input's positions, so the arithmetic cannot see through a read of the answer at
     * all; a predicate that quietly did some of the cancelling would be a second reading of the
     * comparison, which is the shape this whole assessment is written against.
     *
     * <p>False where there is no answer to read, which is every comparison written in a body.
     */
    static boolean readsAnswer(Core e, BindingId answer) {
        if (answer == null) {
            return false;
        }
        if (e instanceof Core.Read read && answer.equals(read.binding())) {
            return true;
        }
        boolean[] found = {false};
        Core.forEachChild(e, child -> found[0] |= readsAnswer(child, answer));
        return found[0];
    }
}
