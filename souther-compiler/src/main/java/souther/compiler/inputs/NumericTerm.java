package souther.compiler.inputs;

import souther.compiler.check.Carrier;
import souther.compiler.check.NewtypeInners;
import souther.compiler.check.DefaultBoundOperationFacts;
import souther.compiler.check.NumericAnswers;
import souther.compiler.check.Symbols;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.observe.Incompleteness;
import souther.compiler.semantics.ConstantArguments;
import souther.compiler.semantics.ResultRange;
import souther.compiler.semantics.TakenAs;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

/**
 * The number a rule compares, and where that number is read from.
 *
 * <p>Not the position. A boundary is drawn on a number, and the number a rule names is sometimes the
 * content of a location and sometimes something taken of it — the length of a string, the size of a
 * container, which hour of its day a time falls in. The two were one thing here, so a position holding
 * a string was a position holding no number, and every rule written about its length came back as a
 * rule the model had not written. The discharge procedure has always kept them apart (spec
 * §invariant-discharge-terms); this is the same separation on the side that measures.
 *
 * <p><b>Two variants under one capability.</b> Both of the terms there are today are answered by a
 * single position of the input, and that is what {@link FromOnePosition} says — the capability a
 * reader needs before it may draw a line, classify what stands somewhere, or ask a search to write a
 * value. Whether such a number is what a location holds or what an operation answered of it is a
 * fact about the shape of the term. Which operation,
 * and therefore what its answer is measured by, where it runs, how it is read off a row and what
 * values answer a given number, are facts about the operation and are declared where those are
 * ({@code semantics.OperationFacts}). A variant per operation would put each of those answers back
 * inside the kind of term, which is what {@code SizeOf} was: a size is never negative was written
 * here, and the same proposition about {@code Int.abs} — declared in {@code semantics} — could not
 * be reached from a term at all (#1016, #1027).
 *
 * <p>So nothing below asks which operation it is holding. Every question a reader asks of a term is
 * either answered from the variant, where the variant is genuinely what settles it, or handed to the
 * operation. An operation added to the language is read by everything here without a line being
 * written for it.
 */
public sealed interface NumericTerm permits NumericTerm.FromOnePosition, NumericTerm.TakenOver {

    /**
     * A number a row can be asked for at one place, because one value stands there.
     *
     * <p>What separates these from a number read from somewhere else is not how many values the
     * operation looks at — a count reads every element of what it is given — but whether the term
     * is answered by one position of the input. That is the whole of what an axis, a threshold and
     * a search for a row need, and it is a capability rather than a shape: a reader that has one of
     * these may act on the position, and a reader holding a bare {@link NumericTerm} may not.
     */
    sealed interface FromOnePosition extends NumericTerm permits ValueOf, TakenOf {

        /**
         * The single input position this term is read from.
         *
         * <p>Not a claim that the number itself stands there. {@code Time.hour(slot.at)} is
         * answered from {@code slot.at} and no hour is written at it; what is written there is a
         * time. What the position gives is somewhere a row can be asked to hold a value, which is
         * what every reader of this method is doing with it.
         */
        TermPath position();

        @Override
        default TermPath subjectPath() {
            return position();
        }

    }

    /** The number a location holds: a numeric parameter, a field of one, a numeric newtype's value. */
    record ValueOf(TermPath position) implements FromOnePosition {

        @Override
        public String toString() {
            return position.toString();
        }
    }

    /**
     * A number taken of what a location holds: how long a string is, how many a container holds,
     * which hour of its day a time falls in.
     *
     * <p><b>Which operations those are is not written here.</b> They are the ones that declare an
     * account of what they take ({@code semantics.OperationFacts}), and a list of them on this side
     * would be a second list — right on the day it was written and wrong the day the declarations
     * moved, with nothing failing in between. This side names the question; that side is the list.
     * A description that named the operations went stale inside one change (#1027).
     *
     * <p>Keyed by the operation the call resolved to rather than by how it was written, so a term
     * here and an atom in the discharge procedure are the same term when they are the same operation
     * over the same location.
     *
     * <p><b>Only for an operation that has declared how its number is taken.</b> Checked here and
     * not at whichever factory happened to be reached: a record is constructible by anyone who can
     * name it, so a rule kept at the call sites is a rule until the next call site. What the
     * declaration settles is every other answer about the term, so a term without one is a term that
     * would be read as whatever the reader's default happened to be — which for a carrier is an end
     * moved onto a value the term never takes, and for a reading is a row classified against a
     * number the model never named, with nothing about either looking like a failure (#1027).
     *
     * <p><b>The operation and the location go together, and {@link #of} is what says so.</b> That
     * was a premise the two call sites carried between them, which is a claim about who builds one
     * today rather than a property of the term. Held at the way in, a term whose operation is not
     * what the location's shape is measured by cannot be made at all.
     *
     * <p>Not against the observed value, which would tell a string from a collection and leave the
     * one pair a reader might confuse undistinguished: a {@code List} and a {@code Set} are one
     * observation, and which of the two it was is the declared type's to say. So it is the declared
     * type that is asked, and the reading applies the account to the observation without asking
     * again.
     */
    final class TakenOf implements FromOnePosition {

        private final ValueName.Stdlib operation;
        private final TermPath position;

        /**
         * Built only where the operation and what stands at the location have been put to the one
         * predicate that says whether they go together.
         *
         * <p>Private, so {@link #of} is the way in and not merely the way in from outside this
         * package. A record's canonical constructor promises that any combination of its components
         * is a value, and these two are not: an account of what is taken is written for a shape, and
         * an operation over a shape the location does not have is a term whose reading would apply
         * that account to whatever happened to be there.
         *
         * <p>Package-private was the same premise a boundary further out — true of whoever writes in
         * this package rather than of the term — and this package's own tests were already going
         * round it (#1027).
         */
        private TakenOf(ValueName.Stdlib operation, TermPath position) {
            this.operation = java.util.Objects.requireNonNull(operation,
                    "a taken number is taken by an operation");
            this.position = java.util.Objects.requireNonNull(position, "and taken of somewhere");
        }

        /**
         * The term for what {@code operation} answers of what stands at {@code path}, or null where
         * the two do not go together.
         *
         * <p><b>The one way one of these is made.</b> Three things have to hold and each of them is
         * a proposition somebody already owns: the operation declares an account of what it takes
         * ({@code semantics.OperationFacts}), it answers a number ({@link NumericAnswers}), and what
         * stands at the location is what that account is taken of ({@link TakenAs#takenOf}). The
         * third was a premise the call sites carried — "the operation and the location agree, by
         * construction" — which is a claim about who happens to build one today and not an invariant
         * (#1027).
         *
         * <p>Null and not a refusal. Whether a call names a number the model has a term for is a
         * question every reader of an expression asks, and the answer "it does not" is one they all
         * have somewhere to put: no line is drawn and the rule is reported as one nothing read.
         *
         * <p>Asked of what the names wrap, since a name around a list is still a list — the same
         * reach {@link Carrier#ofValue} takes, and taken here so that no caller takes it itself.
         */
        public static TakenOf of(ValueName.Stdlib operation, TermPath position, Type at,
                                 NewtypeInners inners, Symbols symbols) {
            TakenAs how = DefaultBoundOperationFacts.get().takenAs(operation);
            // Of what stands here, because for an operation that walks a container the answer is
            // what the container holds. Asked of the operation alone, a sum answered no number this
            // could name and no term was made for any rule written on one.
            Type answers = NumericAnswers.typeOf(operation, at, inners, symbols);
            if (how == null || answers == null || at == null) {
                return null;
            }
            return how.takenOf(souther.compiler.check.TypeOps.base(at, inners), answers)
                    ? new TakenOf(operation, position) : null;
        }

        /** The operation whose answer this term is. */
        public ValueName.Stdlib operation() {
            return operation;
        }

        @Override
        public TermPath position() {
            return position;
        }

        /** What this operation takes of the value at {@link #position()}. Never null: one of these
         *  cannot be built for an operation that declares none. */
        public TakenAs takenAs() {
            return DefaultBoundOperationFacts.get().takenAs(operation);
        }

        /** By the operation and the location, which is what makes two of these one term. */
        @Override
        public boolean equals(Object other) {
            return other instanceof TakenOf taken
                    && operation.equals(taken.operation) && position.equals(taken.position);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(operation, position);
        }

        @Override
        public String toString() {
            return operation.qualified() + "(" + position + ")";
        }
    }

    /**
     * A number an operation took over a run of values, which no single position answers.
     *
     * <p>The dual of {@link TakenOf} and named for it: one takes a number of the value at a place,
     * this takes one over the values a walk was given. What differs is not how many values the
     * operation looks at — a count of a container reads every element of it — but whether there is
     * one place a row can be asked to hold the value the number is taken of. A list an operation
     * built stands nowhere a row writes, so there is none, and a line drawn on this divides no
     * position.
     *
     * <p><b>Where the values came from is not where a line falls.</b> {@link #subjectPath} says
     * where to look and where to send a reader; it is not a position whose values a class could be
     * a class of. Two lines of sixty and forty are on the boundary of a hundred as surely as one of
     * a hundred is, so a class at the element position would be a class about a rule the model does
     * not state — which is why this term has no {@link FromOnePosition#position}, and every reader
     * that would draw one is a reader that cannot hold this.
     *
     * <p>Only for an operation that has declared how its number is taken, as a taking of one value
     * is. What the declaration settles is every other answer about the term, and the account is
     * applied to the values of the run rather than to a value standing at a place.
     */
    final class TakenOver implements NumericTerm {

        private final ValueName.Stdlib operation;
        private final RunSource source;

        private TakenOver(ValueName.Stdlib operation, RunSource source) {
            this.operation = operation;
            this.source = source;
        }

        /**
         * The term for what {@code operation} answers over the values at {@code source}, or null
         * where the two do not go together.
         *
         * <p><b>The one way one of these is made</b>, for the reason {@link TakenOf#of} is: the
         * account the operation declares is what settles every other answer about the term, so one
         * built without putting the two to that account would be read as whatever the reader's
         * default happened to be. What is asked is the same question, of a container of the values
         * the run holds — which is what the operation was given.
         *
         * @param each what stands at the place the run's values are read from
         */
        public static TakenOver of(ValueName.Stdlib operation, RunSource source, Type each,
                                   NewtypeInners inners, Symbols symbols) {
            TakenAs how = DefaultBoundOperationFacts.get().takenAs(operation);
            // A container of what the run holds, with the names its values are written under taken
            // off — the same reach {@link TakenOf#of} takes of the value at a place, and for the
            // same reason: a name wrapped round a whole number is what the account is taken of.
            Type over = each == null ? null
                    : new Type.ListOf(souther.compiler.check.TypeOps.base(each, inners));
            Type answers = over == null ? null
                    : NumericAnswers.typeOf(operation, over, inners, symbols);
            if (how == null || answers == null || source == null) {
                return null;
            }
            return how.takenOf(over, answers) ? new TakenOver(operation, source) : null;
        }

        public ValueName.Stdlib operation() {
            return operation;
        }

        public RunSource source() {
            return source;
        }

        /** What this operation takes of what it is given. Never null: one of these cannot be made
         *  for an operation that declares none. */
        public TakenAs takenAs() {
            return DefaultBoundOperationFacts.get().takenAs(operation);
        }

        /** By the operation and where its values come from, which is what makes two of these one
         *  term. */
        @Override
        public boolean equals(Object other) {
            return other instanceof TakenOver over
                    && operation.equals(over.operation) && source.equals(over.source);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(operation, source);
        }

        @Override
        public TermPath subjectPath() {
            return source.subjectPath();
        }

        @Override
        public String toString() {
            return operation.qualified() + "(" + source + ")";
        }
    }

    /**
     * Where the subject of this number is read or reported from.
     *
     * <p>Not a position at which this term may be divided. What a reader may do with this is go and
     * look — read the values, ask what the declarations put there, send an author to it — and none
     * of those is drawing a line. A term whose values come from a run of a sequence answers here as
     * readily as one answered by a single place, and only the second of them has a position a class
     * can be a class of ({@link FromOnePosition#position}).
     *
     * <p>Never the identity of the term either: two terms can be read from one location, and
     * reading one as the other is what this type exists to stop.
     */
    TermPath subjectPath();

    /**
     * This number where one input position answers it, or null where no single place does.
     *
     * <p>The one place the capability is asked. Every reader that goes on to draw a line, classify
     * what stands somewhere or ask a search for a value needs it, and each of them working it out
     * from what kind of term it is holding is as many readings of one question as there are
     * readers — of which the day a term of a new kind arrives, some would say yes.
     *
     * <p>Exhaustive over the terms there are, with no {@code default}. A kind added is one this
     * question is answered for rather than one that falls to whichever side the last reader's
     * condition happened to leave it on.
     */
    default FromOnePosition atOnePosition() {
        return switch (this) {
            case FromOnePosition at -> at;
            // A run of values is answered by no single place, which is the whole of what this term
            // is. Every reader that goes on to draw a line or ask for a value gets the answer here.
            case TakenOver _ -> null;
        };
    }

    /**
     * The same number, of what stands at {@code other} — or null where this operation and what
     * stands there do not go together.
     *
     * <p>For a name that stands at more than one position. A field every case of a sum spreads is
     * one field written once, so a line drawn on a number of it is one line, and it falls on that
     * number under each case; what moves is where the number is taken, and the operation is what it
     * was.
     *
     * <p>Put to the same predicate the term was built under and not moved on the caller's word.
     * Nothing about the two positions being one field is checked here, so what would otherwise
     * arrive is a term whose account of what it takes was written for a shape the new location does
     * not have.
     *
     * @param at what stands at {@code other}, as the signature wrote it
     */
    default NumericTerm movedTo(TermPath other, Type at, NewtypeInners inners, Symbols symbols) {
        return switch (this) {
            case ValueOf _ -> new ValueOf(other);
            case TakenOf taken -> TakenOf.of(taken.operation(), other, at, inners, symbols);
            // What moves here is where a number is taken, and a run is not taken anywhere: its
            // values come from a place inside a sequence, and the name that would move is the
            // container's. Answered as "not there" rather than by rebuilding the run at a
            // location, which would be this reading inventing where a walk got its values.
            case TakenOver _ -> null;
        };
    }

    /**
     * What this term's values are, before any rule is read.
     *
     * <p>Open where the term is a location's own content: what an {@code Int} holds is what its type
     * holds, and a position nobody bounded is one the model draws no line through (ADR-0090). A term
     * that is what an operation answered is not that — a size is never negative, which is what keeps
     * a guard at zero from asking for a row one below it (spec §invariant-discharge-terms).
     *
     * <p><b>Asked of the operation, not answered here.</b> That a size is at or above nought is one
     * proposition with {@code Int.abs(x) >= 0}, and both are declared where what is true of the
     * language's operations is declared. Written out here instead, it was the same sentence in a
     * second vocabulary, and the half declared in {@code semantics} could not be reached from a term
     * (#1016). Nor is it read off what the operation takes: every operation sharing an account of
     * what it takes would carry one bound, which is the same defect one level along.
     *
     * <p>Read with nothing said about the arguments, because a term of this kind carries none: what
     * a size is taken of is a container and what a magnitude is taken of is the one number, and a
     * bound may only name an argument that is a number ({@code check.OperationFactBinder}), so
     * an operation like these declares no row that names one. A term whose operation does take
     * numbers would have to answer them here rather than be read the same way and quietly come back
     * wider.
     */
    default NumericDomain.Bounds intrinsicBounds() {
        return switch (this) {
            case ValueOf _ -> NumericDomain.Bounds.OPEN;
            case TakenOf taken -> ResultRange.of(
                    DefaultBoundOperationFacts.get().boundsOnTheResult(taken.operation()),
                    ConstantArguments.none());
            // Asked of the operation, as a taking is. That a total of non-negative amounts is
            // itself non-negative is not among the answers: it follows from what the values the run
            // walks guarantee, together with the value the operation starts from and the step it
            // repeats. That is a statement about the run and not about the operation, and it is the
            // run's own reading to make. Declared here as a range of the operation, it would be
            // wrong for a run whose elements may be negative.
            case TakenOver over -> ResultRange.of(
                    DefaultBoundOperationFacts.get().boundsOnTheResult(over.operation()),
                    ConstantArguments.none());
        };
    }

    /**
     * The count at a value, keeping why there is none where there is none.
     *
     * <p><b>At a value, which a reading of one always has.</b> Whether a walk arrived and what it
     * found are settled before a term is asked, so nothing here stands for a place nobody reached
     * or a position a row wrote nothing at — those are said where they happen, in the words their
     * own layer has for them. An arm for them here would be a word that means none of them and
     * takes all of them, which is what a reader downstream then has to unpick.
     */
    sealed interface Reading {

        record Number(Place value) implements Reading {}

        /**
         * A value was there and the observation of it did not come back whole.
         *
         * <p>What {@link Incompleteness.Code} names is what an observation met, so only an
         * observation may put one here. A walk that arrived at no value at all has met nothing and
         * never reaches this type: given a code instead, the nearest one is borrowed, and a reader
         * downstream that words a code as what an observation did then says an observation did
         * something that never happened.
         */
        record Missing(Incompleteness.Code code) implements Reading {}

        /** The value was read and this term is not a number of it. An answer about the value and not
         * about the observation: a {@code Text} where a number was expected is a class nothing holds,
         * and calling it unreadable would report a partition that does not fit its position as a row
         * nobody could read. */
        record NotNumber() implements Reading {}
    }
}
