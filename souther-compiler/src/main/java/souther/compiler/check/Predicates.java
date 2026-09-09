package souther.compiler.check;

import souther.compiler.check.Combinators.Handed;
import souther.compiler.check.DischargeRules.Carrying;
import souther.compiler.check.DischargeRules.Projection;
import souther.compiler.semantics.ElementShape;
import souther.compiler.check.DischargeRules.Source;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.core.Core;
import souther.compiler.types.BindingId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a predicate is read: what a clause owes where a value is built, what a guard settles where one
 * is written, and the keys by which the two meet.
 *
 * <p>One language, read in two directions. An invariant clause and a guard are the same kind of
 * expression, and a clause is discharged exactly when a guard has stated what it owes — so what a
 * clause owes and what a guard establishes come out of one reading, differing only in which way it
 * runs. Written apart, the two would drift, and every drift is a clause an author guarded and the
 * check still reported.
 *
 * <p>Nothing here decides anything about a program. It answers what is owed and what is known, and
 * the walk that threads {@link Known} is what asks.
 */
final class Predicates {

    private final Terms terms;

    Predicates(Terms terms) {
        this.terms = terms;
    }

    /** The relations known of every element of {@code container}: those stated of it as written, and
     * those stated of a container it was built from that travel every construction in between. */
    List<Quantified> elementRelations(Core container, Known k, Denotations at) {
        List<Quantified> found = new ArrayList<>();
        Set<ElementShape> crossed = EnumSet.noneOf(ElementShape.class);
        Core source = container;
        while (true) {
            FactSubject key = terms.subjectOf(source, at);
            if (key != null) {
                for (Quantified q : k.quantified()) {
                    if (key.equals(q.container()) && q.through().containsAll(crossed)) {
                        found.add(q);
                    }
                }
            }
            if (!(source instanceof Core.PreservedCall call)) {
                return found;
            }
            Source built = DischargeRules.builtFrom(call);
            if (built == null) {
                return found;
            }
            crossed.add(built.shape());
            source = built.container();
        }
    }

    /** What {@code q} says of the value at {@code element}, taken into {@code k}. The predicate is
     * read again with the quantifier's own parameter standing for that element — the same reading the
     * seeding does of a type's invariant, over the clause a quantifier holds. A relation the
     * predicate in turn states of a container is recorded, so a container of containers reaches its
     * innermost element. */
    Known instantiate(Quantified q, Core element, Known k, Denotations at) {
        Core stated = Clauses.substituted(q.predicate().body(),
                Map.of(q.predicate().params().get(0).binding(), element));
        List<Quantified> nested = new ArrayList<>();
        quantifiedBy(stated, at, true, nested);
        // A quantifier is recorded by a guard and by a type's invariant alike, and carries no record
        // of which recorded it, so what it gives an element is taken as the path's. That is the
        // weaker of the two readings: a violation it decides is reported as one the values alone did
        // not decide, which holds either way. It is why nothing downstream says a guard decided it.
        return assume(assumed(stated, at, false), k, Known.Held.ON_THE_PATH).and(nested);
    }

    /** What {@code raw}, asserted with polarity {@code positive}, says of every element of a
     * container, added to {@code out}. Read over the clause's shape as {@link #obligations} is, so
     * the two agree about which parts a clause has by having been given the same answer. */
    void quantifiedBy(Core raw, Denotations at, boolean positive, List<Quantified> out) {
        out.addAll(new Quantifiers(this).read(raw, positive, at, terms::inside));
    }

    /**
     * The quantifiers a clause states, read over the shape it was written in.
     *
     * <p>Over the shape ({@link ClauseExpr}), so a clause under a binding is read where its names
     * mean something and a helper's expansion states what the same rule written out states. Read
     * as a tree of its own, a quantifier an author stated through a helper was owed by the reading
     * of what the clause owes and unknown to this one, which is a guarantee assembled from a clause
     * read to two depths.
     */
    private record Quantifiers(Predicates of)
            implements ClauseReading<List<Quantified>, Denotations> {

        /**
         * A conjunction states what both of its conjuncts state, and a choice is read whole.
         *
         * <p>Only a stated quantifier is recorded, and what a choice states is neither of its
         * sides — one of them holds and this cannot say which — so the whole of it is read as a
         * part, where nothing names a quantifier.
         */
        @Override
        public Descent<List<Quantified>> at(ClauseExpr.Joined join) {
            return switch (join.how()) {
                case BOTH -> new Descent.Into<>(Quantifiers::together);
                case EITHER -> new Descent.Whole<>();
            };
        }

        @Override
        public List<Quantified> whole(ClauseExpr.Part part, Denotations at) {
            return of.quantifierStatedBy(Conditions.asSizeComparison(part.of()), part.positive(),
                    at);
        }

        private static List<Quantified> together(List<Quantified> left, List<Quantified> right) {
            if (left.isEmpty() || right.isEmpty()) {
                return left.isEmpty() ? right : left;
            }
            List<Quantified> both = new ArrayList<>(left);
            both.addAll(right);
            return both;
        }
    }

    /**
     * The quantifier {@code e} is, where it is one, stated over the element it quantifies.
     *
     * <p>Denying one says some element fails the predicate, and which one is not something this
     * check can name, so a denied quantifier states nothing here.
     */
    private List<Quantified> quantifierStatedBy(Core e, boolean positive, Denotations at) {
        if (!positive || !(e instanceof Core.PreservedCall call)
                || !DischargeRules.isQuantifier(call.operation())) {
            return List.of();
        }
        Handed over = Combinators.handedTo(call, at);
        Carrying carried = DischargeRules.carried(call);
        if (over == null || carried == null || over.step().params().size() != 1) {
            return List.of();
        }
        // The container is the carrying rule's, which is the one argument this predicate is about.
        // What the operation hands its closure answers the same question about the same argument, and
        // is asked here only for the closure.
        FactSubject container = terms.subjectOf(carried.container(), at);
        if (container == null) {
            return List.of();
        }
        return List.of(new Quantified(container, carried.through(), over.step()));
    }

    /**
     * Whether {@code e} is, or names, one of the values {@code handed} stands for.
     *
     * <p>Asked of what each part of {@code e} denotes and not of the tree it is written as. The
     * values a site hands over stand in the clause, and a binding may stand between: an expansion
     * leaves {@code let $n = <value> in ...}, and what is written where the value was is a read of
     * the binder. Matched by the node, the clause a helper states came out naming none of them,
     * while the same clause written out named one — so a value no guard can be written about was
     * owed a guard for having been reached through a helper.
     *
     * <p>Which is the identity question every other reader here asks by subject, and it is asked the
     * same way: a binding is entered as what it was given ({@link Terms#inside}), so a read of the
     * binder is the value it was given, and the two spellings name the same thing.
     */
    private boolean names(Core e, Set<FactSubject> handed, Denotations at) {
        if (handed.contains(terms.subjectOf(e, at))) {
            return true;
        }
        // A binding is crossed as a binding. Its body is what the clause names, read inside it; what
        // it was given is reached through the name where the body reads it, and is not a part of the
        // clause on its own.
        if (e instanceof Core.LetIn li) {
            return names(li.body(), handed, terms.inside(li, at));
        }
        boolean[] found = {false};
        Core.forEachChild(e, child -> found[0] = found[0] || names(child, handed, at));
        return found[0];
    }

    /** What each of {@code values} is, where the site that hands them over stands. */
    private Set<FactSubject> handedOver(Set<Core> values, Denotations at) {
        Set<FactSubject> out = new java.util.LinkedHashSet<>();
        for (Core value : values) {
            out.add(terms.subjectOf(value, at));
        }
        return out;
    }

    /**
     * A predicate stated of a term. {@code keys} is the term as written first, then each container it
     * was built from by a construction that carries the predicate — any one of them settled is this
     * clause established. Refuting reads only the first: denying a predicate of a list says nothing
     * about a list built from it. {@code positive} is false for a clause written under a negation,
     * and such a clause carries nowhere, since the implication runs the other way.
     */
    record Fact(List<FactSubject> keys, boolean positive) {

        boolean entailedBy(PredicateFacts<FactSubject> facts) {
            for (FactSubject key : keys) {
                if (facts.entails(key, positive)) {
                    return true;
                }
            }
            return false;
        }

        boolean refutedBy(PredicateFacts<FactSubject> facts) {
            return facts.refutes(keys.get(0), positive);
        }
    }

    /**
     * One case of a clause read over an operation that answers one of the values it was given: the
     * clause with that value standing where the call did, and what holds of the arguments there.
     *
     * @param kinds how the values of every atom either of them names are spaced, so that a case can
     *              be taken into a domain without asking anything further of the caller
     */
    record Case(NumericConstraint numeric, List<NumericConstraint> given, Map<FactSubject, Granularity> kinds) {

        /**
         * Every atom this case is decided by.
         *
         * <p>Read off {@code kinds}, which is where the atoms of this case are registered as its
         * forms are built — the substituted clause, the equality saying the call answered this
         * argument, and each condition on the arguments. A reader listing those three would be a
         * second answer to keep in step with the building, and the one that would fall behind: what
         * a condition names and never answers is an atom of this case all the same, and today no
         * operation in the table has such an argument.
         */
        Set<FactSubject> atomsItIsDecidedBy() {
            return kinds.keySet();
        }

        /** {@code d} with this case's conditions on the arguments taken as holding. */
        private NumericDomain<FactSubject> in(NumericDomain<FactSubject> d) {
            NumericDomain<FactSubject> out = d;
            for (NumericConstraint one : given) {
                out = out.assume(one.form(), one.rel(), kinds);
            }
            return out;
        }
    }

    /**
     * A clause read as the cases the operation standing under it is defined in.
     *
     * <p>What a case holds is its condition on the arguments and the equality saying the call
     * answered that argument, and it is reached where those and what is known here can hold at once.
     * The equality is what ties the two readings of one value together: without it a guard written
     * about the call would stand outside every case, and this would answer about a value the rest of
     * the check is talking about separately.
     *
     * <p>So, over the cases that are reached: all of them proving the clause is the clause
     * established, all of them refuting it is the clause refused, and anything else is unsettled.
     * The cases are exhaustive — which the table they are read from states, and this rests on —
     * and that is what makes the first two statements about the result rather than about a case of
     * it.
     *
     * <p>No case reached is not the clause established. It says the guards cannot all hold, and a
     * value the program never builds establishes nothing — while the reading that takes the call as
     * an unknown still has whatever a guard said about it, and answers there. Calling it vacuously
     * proven would have this establishing a clause that reading refuses, which is the check
     * disagreeing with itself about one value rather than an answer. The same holds when the cases
     * come to be multiplied out.
     *
     * <p>One operation to a clause. Where a clause names two of them the cases multiply, and what
     * that costs is a decision this does not have to make yet — such a clause is read as it stands,
     * which proves less and states nothing false.
     */
    record Piecewise(List<Case> cases) {

        /** Every atom any case of this is decided by. A case that is never reached is one the
         * conditions rule out, and which those are is what the domain answers — so every case is
         * counted here, including the ones a reading will drop. */
        Set<FactSubject> atomsItIsDecidedBy() {
            Set<FactSubject> out = new LinkedHashSet<>();
            for (Case one : cases) {
                out.addAll(one.atomsItIsDecidedBy());
            }
            return out;
        }

        boolean entailedBy(NumericDomain<FactSubject> d) {
            boolean reached = false;
            for (Case one : cases) {
                NumericDomain<FactSubject> here = one.in(d);
                if (here.isBottom()) {
                    continue;
                }
                if (!here.entails(one.numeric().form(), one.numeric().rel())) {
                    return false;
                }
                reached = true;
            }
            return reached;
        }

        boolean refutedBy(NumericDomain<FactSubject> d) {
            boolean reached = false;
            for (Case one : cases) {
                NumericDomain<FactSubject> here = one.in(d);
                if (here.isBottom()) {
                    continue;
                }
                if (!here.refutes(one.numeric().form(), one.numeric().rel())) {
                    return false;
                }
                reached = true;
            }
            return reached;
        }
    }

    /** A clause that cannot hold, said in the language the domain reads: {@code -1 >= 0}. */
    static final Clause VIOLATED = new Clause(
            new NumericConstraint(LinearForm.constant(BigDecimal.ONE.negate()), Rel.GE), null, List.of(),
            null);

    /**
     * One clause of an invariant, and the two ways it can come out: a relation for the domain to
     * prove, and the key a guard restating it settles. Either may be absent, and where both are
     * present either one discharging the clause is enough — a guard is written one way and a clause
     * another, and which of the two routes carries it is not the author's concern.
     */
    record Clause(NumericConstraint numeric, Fact fact, List<NumericConstraint> known, Piecewise piecewise) {

        /**
         * Every atom a numeric domain could decide this clause differently by.
         *
         * <p>The question a reading asks before it decides what to prove about the arithmetic it
         * cannot carry: a bound derived for an atom no clause is decided by, and that the domain
         * does not speak of either, changes nothing here ({@link NumericDomain#atomsSpokenOf}).
         *
         * <p>What a guard settled by name is not this. {@link Fact} is answered by
         * {@link PredicateFacts} and never by the domain, so its keys are not atoms a bound could
         * reach — and counting them would put terms into a derivation's roots that no arithmetic is
         * read off.
         *
         * <p>{@code known} is here though a reading that takes it as holding first would reach those
         * atoms anyway. This says what the clause is decided by, which is a fact about the clause;
         * that a caller happens to have put the same atoms into its domain a line earlier is a fact
         * about the caller, and answering by what some caller does is how the answer comes to be
         * wrong for the next one.
         */
        Set<FactSubject> atomsItIsDecidedBy() {
            Set<FactSubject> out = new LinkedHashSet<>();
            if (numeric != null) {
                out.addAll(numeric.atoms());
            }
            for (NumericConstraint one : known) {
                out.addAll(one.atoms());
            }
            if (piecewise != null) {
                out.addAll(piecewise.atomsItIsDecidedBy());
            }
            return out;
        }

        boolean dischargedBy(NumericDomain<FactSubject> d, PredicateFacts<FactSubject> facts) {
            return (numeric != null && d.entails(numeric.form(), numeric.rel()))
                    || (fact != null && fact.entailedBy(facts))
                    || (piecewise != null && !decidedAsWritten(d, facts)
                            && piecewise.entailedBy(d));
        }

        boolean refutedBy(NumericDomain<FactSubject> d, PredicateFacts<FactSubject> facts) {
            return (numeric != null && d.refutes(numeric.form(), numeric.rel()))
                    || (fact != null && fact.refutedBy(facts))
                    || (piecewise != null && !decidedAsWritten(d, facts)
                            && piecewise.refutedBy(d));
        }

        /**
         * Whether the clause as written already came out one way or the other.
         *
         * <p>The cases are asked only where it did not. Both readings are sound, so they can differ
         * only where the path cannot be taken — the guards contradict, and each is answering about a
         * value the program never builds. Where that is visible in the guards it is said there
         * ({@link #noCaseSatisfies}) and neither reading is reached; where it is not, this is what
         * keeps one clause from coming out established and refused at once, which is a check
         * contradicting itself rather than an answer. What the cases are for is a clause the reading
         * that takes the call as an unknown cannot settle, and that is untouched.
         */
        private boolean decidedAsWritten(NumericDomain<FactSubject> d, PredicateFacts<FactSubject> facts) {
            return (numeric != null
                            && (d.entails(numeric.form(), numeric.rel())
                                    || d.refutes(numeric.form(), numeric.rel())))
                    || (fact != null && (fact.entailedBy(facts) || fact.refutedBy(facts)));
        }
    }

    /**
     * What a clause owes: its parts in the order they were read, and what the clause itself folded
     * to.
     *
     * <p>One list, with what was carried and what was not as two shapes in it. A conjunction can be
     * both at once — one conjunct discharged and the next unreadable — and a list of what was
     * carried beside one node for what was not says the invariant was proven where half of it was
     * never read, keeps only the first of two unreadable parts, and loses the order the author wrote
     * them in. All three go away by making them variants of one sequence.
     *
     * <p>{@code folded} is about the clause and not about a part of it. A conjunct folding the way
     * it is read owes nothing and contributes no part; the clause folds only as all of it does
     * ({@link Fold#and}), so this is beside the parts rather than one of them.
     *
     * @param parts  what the clause asks of a construction, each carried or not
     * @param folded whether the clause came out one way on its own, before any construction was
     *               looked at
     */
    record Owed(List<Part> parts, Fold folded) {

        /** Nothing is owed because the clause came out one way or the other on its own. */
        static Owed decided(boolean holds) {
            return new Owed(List.of(), holds ? Fold.HOLDS : Fold.FAILS);
        }

        /**
         * A part this reading was not asked for.
         *
         * <p>Nothing owed and nothing settled, which is a clause with that part taken out of it and
         * not a clause that holds. {@link Fold#HOLDS} would state the part rather than leave it
         * unread, and a reading asked what a clause comes to without one of its conjuncts would be
         * told the conjunct is true.
         */
        static Owed unread() {
            return new Owed(List.of(), Fold.NOT_DECIDED);
        }

        /** One part of the clause this could make nothing of, said as what stopped the reading. */
        static Owed unreadable(Core where) {
            return new Owed(List.of(new Part.Unread(FragmentReason.of(where))), Fold.NOT_DECIDED);
        }

        static Owed of(Clause clause) {
            return new Owed(List.of(new Part.Carried(clause)), Fold.NOT_DECIDED);
        }

        /** Whether a part of it names something the check cannot read here. */
        boolean unreadable() {
            for (Part each : parts) {
                if (each instanceof Part.Unread) {
                    return true;
                }
            }
            return false;
        }

        /** The same, having come out one way on its own as well. Said beside what it owes rather
         *  than instead of it: which of the two a caller acts on is the caller's, and a reading that
         *  answered only one of them decided that for every caller there will ever be. */
        Owed alsoFolded(Fold fold) {
            return fold == Fold.NOT_DECIDED ? this : new Owed(parts, fold);
        }

        /** Both conjuncts of one clause: every part either owes, in the order they were read, and
         *  folded only as both of them together fold. */
        Owed and(Owed other) {
            List<Part> both = new ArrayList<>(parts);
            both.addAll(other.parts);
            return new Owed(List.copyOf(both), folded.and(other.folded));
        }

        /**
         * What this states, as relations.
         *
         * <p>Here beside {@link Predicates#assume} because both read what a clause came to, and a
         * second place that knew the shape of a {@link Clause} would answer differently the day one
         * gains a part.
         *
         * <p>What is known of a size holds of the value whatever established the clause it was read
         * out of, which is why it stands beside what the clause itself states. A clause this reading
         * could not state as a relation is not here, and neither is one it read as a fact:
         * under-answering costs precision, and answering with something else would not be sound.
         */
        List<NumericConstraint> relations() {
            List<NumericConstraint> out = new ArrayList<>();
            for (Part each : parts) {
                if (!(each instanceof Part.Carried carried)) {
                    continue;
                }
                out.addAll(carried.clause().known());
                if (carried.clause().numeric() != null) {
                    out.add(carried.clause().numeric());
                }
            }
            return List.copyOf(out);
        }
    }

    /**
     * One thing a clause asks of a construction.
     *
     * <p>Carried or not, and the difference is the shape rather than a field beside a list. Held as
     * a list of what was carried with one node for what was not, a clause with two parts outside the
     * fragment kept the first and dropped the second — so an author acting on what they were shown
     * would fix one and find the construction still refused. The two also interleave, and a reader
     * showing them in the order they were written needs them in one list to do it.
     */
    sealed interface Part {

        /** The check carried it into a form a guard can be held against. */
        record Carried(Clause clause) implements Part {

            public Carried {
                if (clause == null) {
                    throw new IllegalArgumentException("a part that was carried has a form");
                }
            }
        }

        /**
         * The check made nothing of it, and {@code why} is what it stopped on, said as one of the
         * reasons there are.
         *
         * <p>The conclusion and not the node it was drawn from. Whoever reads this wants to say what
         * in the clause was not read, and working that out from the clause afterwards is a second
         * walk that can come back with a different answer from the one that gave up — which is how a
         * clause with nothing wrong in it came to be described as naming a term the check cannot
         * name. So the walk that gave up says why, where it gave up.
         *
         * <p>And carrying the node instead would put a term in an answer read at a call. What a
         * caller may assume is read for what it says ({@link TermMeaning}), and a part handing the
         * node back would let a reader of that answer ask where the term stands — which two readings
         * of one declaration compare equal about and would then disagree on.
         */
        record Unread(FragmentReason why) implements Part {

            public Unread {
                if (why == null) {
                    throw new IllegalArgumentException("a part nothing was made of says why");
                }
            }
        }
    }

    /**
     * Whether a clause came out one way or the other before any construction was looked at.
     *
     * <p>Read here, off what this walk read the clause as, rather than off the expression an author
     * wrote. {@code Int.compare(1, 2) >= 0} folds through nothing the library declares, and the
     * order it states of {@code 1} and {@code 2} is what settles it — so a reader folding the
     * written form sees a call, and the fold that matters happens once each reading of the clause is
     * in hand.
     */
    enum Fold {

        /** It reads something the construction decides. */
        NOT_DECIDED,

        /** It holds of every value, so no guard is asked for it. */
        HOLDS,

        /** It holds of none, so no guard establishes it. */
        FAILS;

        /** Both conjuncts: failing where either fails, holding only where both hold. */
        Fold and(Fold other) {
            if (this == FAILS || other == FAILS) {
                return FAILS;
            }
            return this == HOLDS && other == HOLDS ? HOLDS : NOT_DECIDED;
        }
    }

    /** What {@code inv} states where it is already established, with {@code decidesFalse} saying a
     * clause folding to the other answer than it is read with is this check's to report. A newtype's
     * constant construction is checked elsewhere, and that check names the clause that failed rather
     * than only saying one did, so it is left to say it.
     *
     * <p>No {@link Known}, and not because none is at hand. An assumption reads nothing off what was
     * already known ({@link Discharge}), so a reading that took one would let the knowledge a caller
     * happened to be holding decide what a declaration says. The parameter is gone rather than
     * ignored: what is not there cannot come to be read. */
    Owed assumed(Core inv, Denotations at, boolean decidesFalse) {
        return obligations(inv, at, Set.of(), true, decidesFalse, Discharge.AN_ASSUMPTION, null);
    }

    /**
     * The same, telling {@code per} what each part of the clause stated as it is read.
     *
     * <p>A conjunction is read a conjunct at a time, and what each conjunct came to is this
     * reading's own answer about that conjunct. Handed over here rather than asked again afterwards:
     * a second reading of a part is a second reader, and the two agree only for as long as nobody
     * changes one of them.
     */
    Owed assumed(Core inv, Denotations at, boolean decidesFalse, PerPart per) {
        return obligations(inv, at, Set.of(), true, decidesFalse, Discharge.AN_ASSUMPTION, per);
    }

    /**
     * The same over a shape the caller already read the clause into, so that what is said about an
     * occurrence is said in the numbering the clause handed out.
     */
    Owed assumed(ClauseExpr of, Denotations at, boolean decidesFalse, PerPart per) {
        return new Owing(this, Set.of(), decidesFalse, Discharge.AN_ASSUMPTION)
                .read(of, at, terms::inside, per == null ? null : per::read);
    }

    /** Told what one part of a clause owed, by the shape of the clause it was read at. */
    interface PerPart {
        void read(ClauseExpr of, Core part, Owed owed);
    }

    /**
     * Whether a relation this reading found may be taken in, asked of the atoms it rests on.
     *
     * <p>The one thing a clause read as something that makes knowledge and a clause read as
     * something that spends it cannot share. The two read one expression through one reader, which
     * is what keeps them from drifting, and this is where they part.
     *
     * <p>Carried as this answer and not as a flag beside the knowledge to answer it from. Held the
     * other way, every reading took a {@link Known} whether or not it could read one, and the reader
     * that may not read one was told so by a comment.
     */
    interface Discharge {

        /** Whether a relation over {@code form} is one this reading may take in. */
        boolean takesIn(LinearForm<FactSubject> form);

        /**
         * Knowledge being made: a guard, an invariant, a declared guarantee. Every relation is taken
         * in.
         *
         * <p>An assumption produces. A guard holds because the branch was taken, an {@code ensures}
         * holds because the callee established it, and an invariant holds because the value was
         * built through a checked constructor — none of them needs anything to have been said about
         * the value beforehand, and each is how something first comes to be said about it.
         *
         * <p>Requiring an assumption to spend what it produces is the circle it looks like: to be
         * spoken of, a value would have to already be spoken of. It is not a theoretical worry —
         * written that way, a declaration that refutes a construction stopped refusing it, because
         * the declaration was turned away for want of the knowledge it was carrying.
         */
        Discharge AN_ASSUMPTION = form -> true;

        /**
         * Knowledge being spent: a clause this construction has to account for, read against what
         * {@code k} speaks of.
         *
         * <p>An obligation spends. It asks the author to account for a value, and asking that about
         * a value nothing has ever said anything of is asking for something no guard they could
         * write would settle; the run-time check stands for such a clause instead.
         *
         * <p>The same question {@link Terms#reportableSite} asks of a value, asked here of the atoms
         * a clause is written over — and it has to be asked here as well, because a clause reaches
         * atoms the site's own value never mentions. An atom is either one the check writes about of
         * its own accord, which is what a place is, or one something on this path has spoken of.
         * There is no third kind, and for an atom the first reduces to a single test: only an atom
         * standing on one evaluation is outside what the seeding writes about.
         *
         * <p>Asked at all only because identity closed. A form being built used to be evidence that
         * something was known, because a value nothing could be said of could not be composed into a
         * form either; now every value has an identity and arithmetic composes over any of them, so
         * the coincidence is gone and the rule it stood for has to be stated. Left unstated, a
         * clause over a value nothing has ever mentioned would be owed here, and owing it would mean
         * reporting a construction no guard an author could write would ever settle.
         */
        static Discharge spending(Known k) {
            return form -> form.coefs().keySet().stream()
                    .allMatch(atom -> !atom.identity().standsOnAnEvaluation() || k.speaksOf(atom));
        }
    }

    /** What {@code inv} owes, where {@code unnamed} holds the values the site hands over that no
     * clause may be read against. */
    Owed obligations(Core inv, Known k, Denotations at, Set<Core> unnamed,
                     boolean decidesFalse) {
        // What each handed-over value is, worked out once and where the site stands. Read again
        // further down, a value under a binding would be asked about at names the site never had.
        return obligations(inv, at, handedOver(unnamed, at), true, decidesFalse,
                Discharge.spending(k), null);
    }

    /**
     * A clause is read as the comparison it states, and where an order call states one there are two
     * readings of it: the order the call decides, and the bound on the sign that decides it. Which of
     * them this construction can be read against is not known until it is read — a date the check can
     * name is one thing, and the date a day after it is another — so the one that answers is the one
     * taken. Reading a predicate never takes a reading away.
     */
    private Owed obligations(Core rawInv, Denotations at, Set<FactSubject> unnamed,
                             boolean positive, boolean decidesFalse, Discharge discharge,
                             PerPart per) {
        return new Owing(this, unnamed, decidesFalse, discharge)
                .read(rawInv, positive, at, terms::inside,
                        per == null ? null : per::read);
    }

    /**
     * What a clause owes, read over the shape it was written in.
     *
     * <p>Over the shape ({@link ClauseExpr}), so that what a connective composes is recognised in
     * one place and this reading agrees with every other by having been given the answer. A binding
     * is where the environment changes, which is why a rule stated through a helper owes what the
     * same rule written out owes — read as a shape with no word for it, such a rule owed nothing
     * and a construction the guards refute went unreported.
     *
     * <p>What it carries beside the clause is what a caller settled before the reading began: the
     * values the site hands over that no clause may be read against, whether a clause folding false
     * decides the construction, and which relations this reading may take in. None of them turns on
     * where in the clause the reading stands.
     */
    private record Owing(Predicates of, Set<FactSubject> unnamed, boolean decidesFalse,
                         Discharge discharge) implements ClauseReading<Owed, Denotations> {

        /**
         * A conjunction owes what both of its conjuncts owe, and a choice is read whole.
         *
         * <p>Each half of a conjunction on its own: an invariant is a set of things that hold, and
         * one the check cannot read leaves its own run-time check standing without costing the
         * others theirs. A choice states neither of its parts — one of them holds and this cannot
         * say which — so what is handed to the reader of comparisons is the whole of it.
         */
        @Override
        public Descent<Owed> at(ClauseExpr.Joined join) {
            return switch (join.how()) {
                case BOTH -> new Descent.Into<>(Owed::and);
                case EITHER -> new Descent.Whole<>();
            };
        }

        @Override
        public Owed whole(ClauseExpr.Part part, Denotations at) {
            return of.owing(Conditions.asSizeComparison(part.of()), at, unnamed, part.positive(),
                    decidesFalse, discharge);
        }
    }

    /** What one part of a clause owes, read as the comparison it states. */
    private Owed owing(Core inv, Denotations at, Set<FactSubject> unnamed,
                       boolean positive, boolean decidesFalse, Discharge discharge) {
        ComparisonReadings readings = Conditions.comparisonsStatedBy(terms, inv, at);
        if (readings.inReadingOrder().isEmpty()) {
            return owedBy(inv, at, unnamed, positive, decidesFalse);
        }
        // The first reading this construction can be read against is the one taken, which is what
        // the reading order is for. Reading a predicate never takes a reading away, so a reading
        // that came to nothing leaves the next one to answer rather than answering for it.
        Owed answer = null;
        for (StatedComparison stated : readings.inReadingOrder()) {
            answer = owedBy(stated, inv, at, unnamed, positive, decidesFalse, discharge);
            if (!answer.unreadable()) {
                return answer;
            }
        }
        return answer;
    }

    /** What a clause that states no comparison owes: it may fold, and it may be a predicate a guard
     *  settles by name. Stated as itself, because a condition that is not a comparison is the one
     *  value it names. */
    private Owed owedBy(Core inv, Denotations at, Set<FactSubject> unnamed, boolean positive,
                        boolean decidesFalse) {
        Boolean folded = decidedAt(inv, at);
        Owed decided = decidedBy(folded, positive, decidesFalse);
        return decided != null ? decided
                : owing(inv, foldOf(folded), null, null, new Conditions.Polar(inv, positive),
                        unnamed, at);
    }

    /** What a clause owes where it states {@code stated}, with {@code inv} the expression the caller
     *  was handed and so the one a report about the clause names. */
    private Owed owedBy(StatedComparison stated, Core inv, Denotations at, Set<FactSubject> unnamed,
                        boolean positive, boolean decidesFalse, Discharge discharge) {
        Boolean folded = decidedAt(stated, at);
        Owed decided = decidedBy(folded, positive, decidesFalse);
        if (decided != null) {
            return decided;
        }
        NumericConstraint numeric = null;
        Piecewise piecewise = null;
        LinearForm<FactSubject> la = terms.affineOf(stated.left(), at);
        LinearForm<FactSubject> ra = terms.affineOf(stated.right(), at);
        // Asked of the relation, not of its two sides. An atom on both sides cancels, and one
        // that is not in the relation is not something the relation depends on — turning a clause
        // away for a value it does not actually rest on would report nothing about a value the
        // author was never asked about.
        LinearForm<FactSubject> between = la == null || ra == null ? null : la.minus(ra);
        if (between != null && discharge.takesIn(between)) {
            numeric = new NumericConstraint(between, stated.relationUnder(positive));
            // The same clause read as the cases of whatever chooses inside it. Both readings are
            // kept: a guard may name the call itself, which the clause as it stands is what
            // settles, and reading it case by case never takes that away.
            piecewise = piecewiseOf(numeric, stated.left(), stated.right(), at);
        }
        return owing(inv, foldOf(folded), numeric, piecewise, Conditions.polar(stated, positive),
                unnamed, at);
    }

    /**
     * What a clause that did not settle on its own owes: what it states of the numbers, and what a
     * guard settling it by name would have to have settled.
     *
     * <p>{@code where} is the expression the caller was handed. A reading that made nothing of the
     * clause says where it stopped, and where it stopped is somewhere the author wrote — a statement
     * this reading composed stands nowhere and would name a comparison nobody can be shown.
     */
    private Owed owing(Core where, Fold fold, NumericConstraint numeric, Piecewise piecewise,
                       Conditions.Polar polar, Set<FactSubject> unnamed, Denotations at) {
        // A predicate over a value no guard could be written about is not a predicate a guard will
        // settle, so it is not owed as one — where the domain can say something of that value it has
        // already said it above, and where it cannot the run-time check stands for the clause.
        List<FactSubject> keys = !unnamed.isEmpty() && names(polar.expr(), unnamed, at)
                ? List.of() : factKeys(polar.expr(), at);
        boolean stated = polar.positive();
        Fact fact = keys.isEmpty() ? null : new Fact(stated ? keys : firstOnly(keys), stated);
        if (numeric == null && fact == null) {
            return Owed.unreadable(where).alsoFolded(fold);
        }
        // What the values this clause names carry, read off the atoms rather than off the tree: an
        // atom files what it carries where it is named, so having read the clause into forms is
        // having read them ({@link IntrinsicNumericFacts}).
        List<NumericConstraint> known = terms.carriedBy(namedBy(numeric, fact));
        return Owed.of(new Clause(numeric, fact, known, piecewise)).alsoFolded(fold);
    }

    /** What a clause folding to {@code folded}, read with polarity {@code positive}, comes to on its
     * own — or null where it owes what it states after all.
     *
     * <p>The clause folds once the construction's own expressions stand where it read a field.
     * Folding the way it is read owes nothing; folding the other way is a violation, and saying so
     * needs no term to be named. Read under a denial it is the other answer that discharges, which
     * is why the polarity is asked. A caller that does not report the other way round is answered
     * with nothing here and reads on. */
    private static Owed decidedBy(Boolean folded, boolean positive, boolean decidesFalse) {
        if (folded == null) {
            return null;
        }
        if (folded == positive) {
            return Owed.decided(true);
        }
        return decidesFalse ? Owed.decided(false).and(Owed.of(VIOLATED)) : null;
    }

    /** What a clause reading on says about its own fold: either it did not fold, or it folded the
     * other way and this caller does not report that as a violation. A reader classifying the clause
     * needs it, and taking the reading away would change what this caller is answered. */
    private static Fold foldOf(Boolean folded) {
        return folded == null ? Fold.NOT_DECIDED : Fold.FAILS;
    }

    /**
     * The atoms {@code cond} names as numbers, which for a condition that states no comparison is
     * the single value it is.
     *
     * <p>Naming and not walking. Reading either side into a form is what names every value in it,
     * and an atom files what it carries where it is named — so this asks for the names and takes what
     * was filed against them, where the same question used to be answered by descending the tree a
     * second time and reading the library's tables again.
     *
     * <p>What the forms name and what they reach, and nothing else. A number standing somewhere no
     * form reads it from — an argument of an operation that answers something the domain does not
     * carry — is named by this condition and left out here, where the walk down the tree would have
     * stated what it carries. Nothing is lost while a value the reading cannot name in a form is a
     * value no clause and no other guard can be written over either: a fact about it could not have
     * been the reason a construction was discharged. That stops being true the day a clause can be
     * decided by an atom no form of any condition reaches, and this is where to look when it is.
     */
    private Set<FactSubject> atomsNamedBy(Core cond, Denotations at) {
        Set<FactSubject> out = new LinkedHashSet<>();
        FactSubject atom = terms.atomOf(cond, at);
        if (atom != null) {
            out.add(atom);
        }
        return out;
    }

    /** The same, of a comparison: both of its sides, whatever it states of them. */
    private Set<FactSubject> atomsNamedBy(StatedComparison stated, Denotations at) {
        Set<FactSubject> out = new LinkedHashSet<>();
        for (Core side : List.of(stated.left(), stated.right())) {
            LinearForm<FactSubject> form = terms.affineOf(side, at);
            if (form != null) {
                out.addAll(form.coefs().keySet());
            }
        }
        return out;
    }

    /** The atoms a clause read this far names: the ones its relation is written over, and the one a
     * predicate it states is keyed on. */
    private static Set<FactSubject> namedBy(NumericConstraint numeric, Fact fact) {
        Set<FactSubject> out = new LinkedHashSet<>();
        if (numeric != null) {
            out.addAll(numeric.atoms());
        }
        if (fact != null) {
            out.addAll(fact.keys());
        }
        return out;
    }

    /** Whether {@code inv} is decided outright: the clause, with the construction's own values
     * already standing where it read a field, folded. {@code null} where it does not fold — which is
     * every clause reading anything computed at run time. */
    Boolean decidedAt(Core inv, Denotations at) {
        Object folded = Terms.folded(inv, terms.symbols(), at);
        return folded instanceof Boolean b ? b : null;
    }

    /**
     * The same, of a comparison a reading arrived at rather than of an expression.
     *
     * <p>Folded from the two sides and what the comparison places, because a statement has no node
     * to fold. Which is the whole of what folding a comparison is either way — an expression folds a
     * side at a time and puts the two together under the relation its operator placed
     * ({@link ConstEval#stands}) — and it is what lets a composed comparison be decided at all:
     * {@code Int.compare(1, 2) >= 0} folds through nothing the library declares, and the order it
     * states of {@code 1} and {@code 2} is settled here.
     */
    private Boolean decidedAt(StatedComparison stated, Denotations at) {
        Object left = Terms.folded(stated.left(), terms.symbols(), at);
        Object right = Terms.folded(stated.right(), terms.symbols(), at);
        return (left == null || right == null) ? null
                : ConstEval.stands(stated.claim().statedRelation(), left, right);
    }

    static List<FactSubject> firstOnly(List<FactSubject> keys) {
        return keys.isEmpty() ? keys : List.of(keys.get(0));
    }

    /**
     * What taking a condition as holding came to.
     *
     * <p>Two things and not one. A condition of a shape no rule here reads leaves what is known
     * exactly as it was, and so does one that was read and ruled nothing out — the states are
     * identical, and only the reading knows which happened. Answered here rather than guessed at
     * afterwards by comparing the state to what went in: that comparison says whether anything
     * changed, which is a third question and is the answer to neither.
     *
     * @param taken     whether any of these domains took the condition in
     * @param shapeRead whether a rule here read the shape it is written in at all
     */
    record Assumed(Known known, boolean taken, boolean shapeRead) {

        Assumed alsoRead(boolean moreTaken, boolean moreShape) {
            return (moreTaken && !taken) || (moreShape && !shapeRead)
                    ? new Assumed(known, taken || moreTaken, shapeRead || moreShape) : this;
        }
    }

    /** Refines {@code k} by asserting {@code cond} (or its negation): a comparison tightens the
     * numeric domain, a stdlib predicate settles a fact. A condition of neither shape, and an operand
     * outside the affine fragment, leave {@code k} unchanged (sound). */
    Assumed assumeCond(Core rawCond, Known k, Denotations at, boolean positive) {
        return new Assuming(this).read(rawCond, positive, at, terms::inside).from(k);
    }

    /**
     * What taking a clause as holding does to what is known, before it is given a state to do it to.
     *
     * <p>Threading a state through the parts is this reading's own algebra and not the shape's: the
     * right half of a conjunction is taken under what the left half left, so the two are read in
     * order. Held as a state the fold carried downward, every reading over a clause would be one
     * that runs left to right, which is true of this one and of none of the others.
     */
    @FunctionalInterface
    private interface Assumption {

        /** What this comes to, taken under {@code known}. */
        Assumed from(Known known);
    }

    /**
     * What a condition taken in makes known, read over the shape it was written in.
     *
     * <p>Over the shape ({@link ClauseExpr}) and not over the tree, so that what a connective
     * composes, where a denial goes and where a binding stands are recognised once and this reading
     * agrees with every other by having been given the answer. Read as a tree of its own, this had
     * words for a connective and a denial and none for a binding — so a rule an author stated by
     * naming it made nothing known, while the same rule written out made the comparison known, and
     * the one reader with no tree-rebuilding above it named a dead branch on one spelling only.
     */
    private record Assuming(Predicates of) implements ClauseReading<Assumption, Denotations> {

        /**
         * A conjunction is taken in a half at a time, and a choice whole.
         *
         * <p>One of a choice's parts holds and this cannot say which, so taking either of them in
         * would rule out values the condition admits. What is left of it is that the author named
         * the two, which the part it is read as says.
         */
        @Override
        public Descent<Assumption> at(ClauseExpr.Joined join) {
            return switch (join.how()) {
                case BOTH -> new Descent.Into<>(Assuming::both);
                case EITHER -> new Descent.Whole<>();
            };
        }

        @Override
        public Assumption whole(ClauseExpr.Part part, Denotations at) {
            return known -> of.taking(part, known, at);
        }

        /**
         * The right half under what the left half left.
         *
         * <p>Either side taken in is the condition taken in. A conjunction one half of which reads
         * is not one nothing was read of, and calling it that would name this compiler's limit
         * where the limit was reached on one operand only.
         */
        private static Assumption both(Assumption left, Assumption right) {
            return known -> {
                Assumed one = left.from(known);
                return right.from(one.known()).alsoRead(one.taken(), one.shapeRead());
            };
        }
    }

    /**
     * What taking one part of a condition as holding comes to.
     *
     * <p>Every reading of the comparison it states, because each of them holds of the same values:
     * the order a call decides, and the bound on the sign that decides it. Which one a clause is
     * read against is settled where the clause is read, so a guard states each of them rather than
     * choosing here.
     */
    private Assumed taking(ClauseExpr.Part part, Known k, Denotations at) {
        boolean positive = part.positive();
        Core cond = Conditions.asSizeComparison(part.of());
        // A connective taken whole states no comparison — that is what taking it whole means — so
        // it is not asked for one. What it names is the two halves the shape composed, and without
        // saying they were named a value one of them computes is one nothing has ever spoken of.
        if (part instanceof ClauseExpr.Joined join) {
            return taking(cond, join.writtenHalves(), k, at, positive);
        }
        List<StatedComparison> readings =
                Conditions.comparisonsStatedBy(terms, cond, at).inReadingOrder();
        if (readings.isEmpty()) {
            return taking(cond, List.of(), k, at, positive);
        }
        Assumed so = new Assumed(k, false, false);
        for (StatedComparison stated : readings) {
            Assumed one = taking(stated, so.known(), at, positive);
            so = one.alsoRead(so.taken(), so.shapeRead());
        }
        return so;
    }

    /**
     * What taking a condition that states no comparison as holding comes to.
     *
     * <p>What it can still say is what the value it names carries, what a quantifier over it states
     * of a container's elements, and that the condition itself holds — the last keyed on the
     * condition as written, which is what a guard settles a predicate by.
     *
     * <p>{@code mentioned} is what the author named here that nothing else on this path will
     * record: the two halves of a connective this reading states neither of. Neither half is read,
     * so without this a value one of them computes is one nothing has ever spoken of, and a clause
     * over it is left to the run-time check rather than asked of the author who did write about it.
     *
     * <p>Two halves and no further. What a condition names is the wider question — a call naming
     * three arguments names them as plainly, and none of them arrives here — and this is the
     * shape's answer to it, not the question's. Widening it widens what a clause may be owed for
     * and what a report may point at, which is its own change and not this reading's to make.
     */
    private Assumed taking(Core cond, List<Core> mentioned, Known k, Denotations at,
                           boolean positive) {
        Known out = spokenIn(k, mentioned, at);
        // What the values this condition names carry, whichever way the condition itself is read.
        // Read off the atoms the condition was named into and not by walking it again: naming an
        // expression is what files what its values carry, so the reading that named it has them
        // ({@link IntrinsicNumericFacts}).
        List<NumericConstraint> known = terms.carriedBy(atomsNamedBy(cond, at));
        out = carrying(out, known);
        List<Quantified> quantified = new ArrayList<>();
        quantifiedBy(cond, at, positive, quantified);
        out = out.and(quantified);
        // Two answers and not one, as everywhere here: what a proof may rest on, and what an
        // unsettled arm may be explained by. They move together on this route and are still asked
        // apart, because one of them coming to answer the other is how a limit of this compiler
        // gets reported as a fact about the model.
        boolean taken = !known.isEmpty() || !quantified.isEmpty();
        boolean shapeRead = !known.isEmpty() || !quantified.isEmpty();
        return settling(out, Conditions.Polar.itself(cond, positive), at, taken, shapeRead);
    }

    /**
     * What taking a condition stating {@code stated} as holding comes to.
     *
     * <p>Both routes, always: what the comparison says of the numbers, and that the canonical
     * comparison it comes to holds. Which one carries a clause is decided where the clause is read,
     * and a guard does not know which that will be.
     *
     * <p>No quantifier is asked for. What states one is a call to an operation over a container, and
     * a comparison is not one however it was arrived at — so asking would be asking a question whose
     * answer the shape already gives.
     */
    private Assumed taking(StatedComparison stated, Known k, Denotations at, boolean positive) {
        // Taken before the comparison is read at all, for the reason the reachability question
        // below is asked with them.
        List<NumericConstraint> known = terms.carriedBy(atomsNamedBy(stated, at));
        Known out = carrying(k, known);
        // A condition no case of what it is written over can satisfy is one this branch is never
        // entered under, and a value the program never builds is not one to report about. Asked of
        // everything the condition itself established and not only of what held on the way in: a
        // size and what an operation answers hold of the value however the condition comes out, and
        // a case read without them is one this would call reachable where the construction below,
        // which is handed the same facts, would not.
        if (noCaseSatisfies(stated, out, at, positive)) {
            // Read, and read to the end: what it comes to is that nothing enters here.
            return new Assumed(out.reachingNothing(), true, true);
        }
        boolean taken = !known.isEmpty();
        boolean shapeRead = !known.isEmpty();
        LinearForm<FactSubject> la = terms.affineOf(stated.left(), at);
        LinearForm<FactSubject> ra = terms.affineOf(stated.right(), at);
        if (la != null && ra != null) {
            LinearForm<FactSubject> compared = la.minus(ra);
            out = out.taking(compared, stated.relationUnder(positive), Known.Held.ON_THE_PATH,
                    terms.kindsOf(compared));
            taken = true;
            shapeRead |= readsItsShape(stated.left(), at) && readsItsShape(stated.right(), at);
        }
        // What the comparison named, recorded as spoken about: a construction from one of these
        // is one the author has said something about, whichever route ends up carrying it.
        Set<FactSubject> named = new HashSet<>(spokenOf(stated.left(), at, la));
        named.addAll(spokenOf(stated.right(), at, ra));
        out = out.speaking(named);
        return settling(out, Conditions.polar(stated, positive), at, taken, shapeRead);
    }

    /** {@code k} with each of {@code mentioned} recorded as one this condition named. No form is
     * read of them: what a connective stands between is a condition and not a number, so what is
     * recorded is that it was written and nothing about what it computes. */
    private Known spokenIn(Known k, List<Core> mentioned, Denotations at) {
        Set<FactSubject> named = new HashSet<>();
        for (Core each : mentioned) {
            named.addAll(spokenOf(each, at, null));
        }
        return k.speaking(named);
    }

    /** {@code k} holding what those values carry. A size is never negative whether or not the
     * condition holds, so this holds of the value and not of the path — the condition is only where
     * the container got named. */
    private Known carrying(Known k, List<NumericConstraint> known) {
        Known out = k;
        for (NumericConstraint c : known) {
            out = out.taking(c.form(), c.rel(), Known.Held.OF_THE_VALUE, terms.kindsOf(c.form()));
        }
        return out;
    }

    /** {@code out} also holding that {@code polar}'s condition came out the way it states, keyed on
     * what a guard settling it would settle. */
    private Assumed settling(Known out, Conditions.Polar polar, Denotations at, boolean taken,
                             boolean shapeRead) {
        FactSubject key = terms.subjectOf(polar.expr(), at);
        return key == null ? new Assumed(out, taken, shapeRead)
                : new Assumed(out.taking(key, polar.positive(), Known.Held.ON_THE_PATH), true,
                        shapeRead || readsItsShape(polar.expr(), at));
    }

    /**
     * Whether a rule here read the shape {@code e} is written in.
     *
     * <p>Not what a fact about {@code e} is filed under, which is {@link Terms#subjectOf}'s and is
     * now an answer for every expression there is. This is the other question, and the only place
     * the symbolic reading is the right one to ask: whether the check reached what the condition
     * <em>says</em>, or only which value it says it of.
     *
     * <p>What it decides is what an unsettled arm is explained by. A condition of a shape no rule
     * reads is this compiler's limit and widening the reading removes it; a condition read to no
     * effect is the ordinary state of a branch nobody built a value for, and no widening touches it.
     * Since identity closed, every condition names something and a fact about that something is
     * always recorded — so answering this from whether anything was recorded made the first kind
     * vanish, and every limit of this compiler was reported as a fact about the model. Measured:
     * over the whole suite the first kind stopped occurring at all.
     *
     * <p>The knowledge is taken in either way. What a guard says about a value is true whether or not
     * this check could read the shape it was written in, and a clause naming that value is still
     * discharged by it. This says only what may be claimed about the reading.
     */
    private boolean readsItsShape(Core e, Denotations at) {
        return terms.bodyKey(e, at) != null;
    }

    /** The terms one side of a compared pair names: the expression itself, and each atom of the form it
     * reduced to — {@code leftover + 1} says something about {@code leftover}. */
    Collection<FactSubject> spokenOf(Core side, Denotations at, LinearForm<FactSubject> form) {
        Set<FactSubject> named = new HashSet<>(form == null ? Set.of() : form.coefs().keySet());
        FactSubject written = terms.subjectOf(side, at);
        if (written != null) {
            named.add(written);
        }
        return named;
    }



    /**
     * The positions this reading took {@code owed}'s clause in about.
     *
     * <p>What the reading produced and not what it was handed. A comparison it could not put in
     * linear form still comes back as a statement — over an atom standing for the whole expression,
     * which is not the position — so a caller asking whether this reading adopted a clause at a
     * position asks this and gets the reading's own answer. Worked out from the clause's spelling
     * instead, {@code value * 2 >= 4} and {@code value * value >= 4} are one shape, and the first is
     * read whole while the second is read about nothing.
     */
    static Set<FactSubject> subjectsIn(Owed owed) {
        Set<FactSubject> named = new LinkedHashSet<>();
        for (Part eachC : owed.parts()) {
            if (!(eachC instanceof Part.Carried carriedC)) {
                continue;
            }
            Clause c = carriedC.clause();
            for (NumericConstraint known : c.known()) {
                named.addAll(known.form().coefs().keySet());
            }
            if (c.numeric() != null) {
                named.addAll(c.numeric().form().coefs().keySet());
            }
            if (c.fact() != null) {
                named.addAll(c.fact().keys());
            }
        }
        return named;
    }

    /**
     * The atoms a projection could narrow by, of what {@code owed} states.
     *
     * <p>{@link #subjectsIn} counts the key a guard settles by name as well, because what makes a
     * clause readable against a value is that something speaks of it, however it speaks. This
     * counts what the interval algebra was handed a form for, which is the narrower thing and the
     * one a range's exactness is about: {@code value == 3 || value == 5} arrives as a fact keyed on
     * the comparison and no form at all, so it names a subject there and narrows nothing here.
     */
    static Set<FactSubject> narrowableIn(Owed owed) {
        Set<FactSubject> named = new LinkedHashSet<>();
        for (Part eachC : owed.parts()) {
            if (!(eachC instanceof Part.Carried carriedC)) {
                continue;
            }
            Clause c = carriedC.clause();
            // What the clause states, and not what holds of the operations it names.
            // {@link Clause#known} is filled by {@link #sizeFacts} and {@link #resultFacts} with
            // relations that are true of every value — a size is never negative — so counting them
            // would call any rule mentioning a size a rule that narrowed the size, whatever it says
            // about it.
            if (c.numeric() != null) {
                named.addAll(c.numeric().atoms());
            }
        }
        return named;
    }

    /** {@code k} with everything {@code owed} states taken as holding, as far as {@code held}
     * reaches. What a clause owes at a construction is what it guarantees where it is already
     * established, so the two read the same clauses through the same rule and differ only in
     * direction. */
    Known assume(Owed owed, Known k, Known.Held held) {
        Known out = k;
        // Every subject this takes something in about is one something has been said about. Recorded
        // for the same reason a guard's subjects are: what makes a clause readable against a value is
        // that something speaks of it, and the seeding speaks. A place did not need it — the seeding
        // writes about places, which is a rule of its own — but an evaluation has no such rule, and
        // saying nothing here would leave a value its own type guarantees something about looking
        // like one nothing is known of.
        out = out.speaking(subjectsIn(owed));
        // What could not be read says nothing here: a clause left to the run-time check is not one
        // the seeding may assume, and the flag saying so is the construction site's to act on.
        for (Part eachC : owed.parts()) {
            if (!(eachC instanceof Part.Carried carriedC)) {
                continue;
            }
            Clause c = carriedC.clause();
            for (NumericConstraint known : c.known()) {
                // What is known of a size holds of the container itself, whatever established the
                // clause it was read out of.
                out = out.taking(known.form(), known.rel(), Known.Held.OF_THE_VALUE,
                        terms.kindsOf(known.form()));
            }
            if (c.numeric() != null) {
                out = out.taking(c.numeric().form(), c.numeric().rel(), held,
                        terms.kindsOf(c.numeric().form()));
            }
            if (c.fact() != null) {
                // What is guaranteed is guaranteed of the term as written; a container built from it
                // is another term, and reads the rules where it is constructed rather than here.
                out = out.taking(c.fact().keys().get(0), c.fact().positive(), held);
            }
        }
        return out;
    }

    // --- affine forms --------------------------------------------------------------------------


    /**
     * Whether {@code cond}, asserted with polarity {@code positive}, fails in every case an
     * operation inside it is defined in — so the branch it guards is not entered.
     *
     * <p>The same reading the construction below does, done where the condition is taken instead.
     * Without it what an operation answers is read at one of the two and not the other, and a path
     * the cases show cannot be taken is walked as though it could: the construction under it is then
     * reported against guards that cannot all hold, which is a diagnostic about a value the program
     * never builds.
     */
    private boolean noCaseSatisfies(StatedComparison stated, Known k, Denotations at,
                                    boolean positive) {
        LinearForm<FactSubject> la = terms.affineOf(stated.left(), at);
        LinearForm<FactSubject> ra = terms.affineOf(stated.right(), at);
        if (la == null || ra == null) {
            return false;
        }
        Piecewise cases = piecewiseOf(
                new NumericConstraint(la.minus(ra), stated.relationUnder(positive)),
                stated.left(), stated.right(), at);
        return cases != null && cases.refutedBy(k.numbers());
    }

    /**
     * {@code owed} read as the cases of the one operation inside it that answers a value it was
     * given, or null where there is no such operation, where there is more than one, or where a case
     * could not be read.
     *
     * <p>The substitution is made in the form and not in the expression. What the clause reads may be
     * the call as written or a name that was given it, and both arrive here as the one atom the call
     * keys as — so replacing the atom answers both, where rewriting the expression would answer the
     * first and leave the second saying nothing.
     */
    private Piecewise piecewiseOf(NumericConstraint owed, Core left, Core right, Denotations at) {
        Map<FactSubject, Choice> choosing = new LinkedHashMap<>();
        chosenCalls(left, at, choosing);
        chosenCalls(right, at, choosing);
        choosing.keySet().retainAll(owed.form().coefs().keySet());
        if (choosing.size() != 1) {
            return null;
        }
        FactSubject atom = choosing.keySet().iterator().next();
        BigDecimal coefficient = owed.form().coefs().get(atom);
        List<Case> cases = new ArrayList<>();
        for (Choice.Arm arm : choosing.get(atom).arms()) {
            LinearForm<FactSubject> answered = terms.affineOf(arm.answers(), at);
            if (answered == null) {
                return null;
            }
            LinearForm<FactSubject> instead = owed.form()
                    .minus(LinearForm.atom(atom).times(coefficient))
                    .plus(answered.times(coefficient));
            List<NumericConstraint> given = new ArrayList<>();
            Map<FactSubject, Granularity> kinds = new HashMap<>(terms.kindsOf(instead));
            // What the call answers here, said of the call itself: in this case the two are one
            // value. Without it a guard written about the call would stand outside the cases, and a
            // clause could come out established by these and refused by that. It belongs to this
            // reading and to no other — a recipe over the arms answers about the value itself and
            // has nothing to tie back — which is why what is shared with that reader stops at the
            // arms and what they settle.
            LinearForm<FactSubject> answeredHere = LinearForm.atom(atom).minus(answered);
            given.add(new NumericConstraint(answeredHere, Rel.EQ));
            kinds.putAll(terms.kindsOf(answeredHere));
            for (NumericConstraint stands : Conditions.settledBy(terms, arm.decidedBy(), at)) {
                given.add(stands);
                kinds.putAll(terms.kindsOf(stands.form()));
            }
            cases.add(new Case(new NumericConstraint(instead, owed.rel()), List.copyOf(given),
                    Map.copyOf(kinds)));
        }
        return new Piecewise(List.copyOf(cases));
    }

    /** Every call inside {@code e} that answers one of the values it was given, as the choice it is,
     * by the atom it keys as. A name is what it was given, as everywhere else a value is read.
     *
     * <p>Which calls those are is asked of {@link Choice} and not of the table it reads. A reader
     * here that knew the table would be a second interpretation of it, and the two would come apart
     * the day the library changed which argument a case answers. */
    private void chosenCalls(Core e, Denotations at, Map<FactSubject, Choice> out) {
        if (e instanceof Core.Read r && at.valueOf(r.binding()) != null) {
            chosenCalls(at.valueOf(r.binding()), at, out);
            return;
        }
        if (e instanceof Core.PreservedCall call) {
            Choice choice = Choice.of(call);
            FactSubject atom = choice == null ? null : terms.atomOf(call, at);
            if (atom != null) {
                out.put(atom, choice);
            }
        }
        Core.forEachChild(e, child -> chosenCalls(child, at, out));
    }


    /** The keys a guard could have settled to establish this clause: the predicate as written, and
     * the same predicate of each container the written one was built from by a construction that
     * carries it. Stating {@code List.all(p, xs)} is stating it of every sublist of {@code xs}. */
    List<FactSubject> factKeys(Core inv, Denotations at) {
        FactSubject written = terms.subjectOf(inv, at);
        if (written == null) {
            return List.of();
        }
        List<FactSubject> keys = new ArrayList<>();
        keys.add(written);
        if (!(inv instanceof Core.PreservedCall call)) {
            return keys;
        }
        Carrying carried = DischargeRules.carried(call);
        if (carried == null) {
            return keys;
        }
        // The predicate over each container the one it names was built from. The container is the
        // construction's own expression, so the operations peeled off are the ones the body wrote.
        Core container = carried.container();
        Core.PreservedCall stated = call;
        while (container instanceof Core.PreservedCall inner) {
            Source built = DischargeRules.builtFrom(inner);
            if (built == null) {
                break;
            }
            Core.PreservedCall next = carries(stated, carried, inner, built, at);
            if (next == null) {
                break;
            }
            Core source = built.container();
            FactSubject key = terms.subjectOf(carried.over(next, source), at);
            if (key == null) {
                break;
            }
            keys.add(key);
            stated = next;
            container = source;
        }
        return keys;
    }

    /**
     * The predicate as it applies to what {@code inner} was built from, or {@code null} when it does
     * not apply there. A construction the shape carries leaves the predicate as it was. A mapping
     * carries nothing on its own, but a predicate stated over a projection carries when the closure
     * copied that field across, over the field it came from.
     */
    Core.PreservedCall carries(Core.PreservedCall stated, Carrying carried,
                                       Core.PreservedCall inner, Source built, Denotations at) {
        if (carried.through().contains(built.shape())) {
            return stated;
        }
        Projection projection = DischargeRules.projectionOf(stated, at);
        if (built.shape() != ElementShape.MAPS || projection == null) {
            return null;
        }
        // Where the mapping's closure is written is already stated once, by the table that says which
        // argument each combinator hands its elements to.
        Handed combo = Combinators.handedTo(inner, at);
        if (combo == null) {
            return null;
        }
        Core traced = projectionThrough(projection.projection(), combo.step(), at);
        return traced == null ? null : projection.over(traced);
    }

    /**
     * The projection over an element that a projection over a mapped list reduces to, or
     * {@code null}.
     *
     * <p>{@code .product} over {@code List.map(r -> Line { product = r.product, ... }, xs)} is
     * {@code .product} over {@code xs}: the closure copied that field across, so two mapped elements
     * differ there exactly when the two they came from did. Bounded deliberately — a field a closure
     * computes from others is not this.
     *
     * <p>What comes back is a projection to be keyed and nothing else. A block keys its parameter by
     * where it is bound, so the chain built here is read at the position the closure's parameter
     * stands in, whatever it is called and whatever it was read from.
     */
    Core projectionThrough(Core.Block proj, Core.Block step, Denotations at) {
        if (proj.params().size() != 1 || step.params().size() != 1) {
            return null;
        }
        Core.Binder element = step.params().get(0);
        List<String> read = new Reads(proj.params().get(0).binding()).chain(proj.body());
        if (read == null) {
            return null;
        }
        Reads reads = new Reads(element.binding());
        Core made = reads.produced(step.body());
        List<String> traced;
        if (read.isEmpty()) {
            traced = reads.chain(made);   // the closure hands the element straight back
        } else {
            if (!(made instanceof Core.Construct nd)) {
                return null;
            }
            List<String> copied = null;
            for (Core.FieldValue given : nd.values()) {
                if (given.field().equals(read.get(0))) {
                    copied = reads.chain(given.value());
                }
            }
            if (copied == null) {
                return null;
            }
            traced = new ArrayList<>(copied);
            traced.addAll(read.subList(1, read.size()));
        }
        if (traced == null) {
            return null;
        }
        Core on = Terms.read(element, Terms.elementType(step.type()), step.pos());
        for (String field : traced) {
            on = new Core.FieldAccess(on, field, terms.fieldType(on.type(), field), step.pos());
        }
        return new Core.Block(List.of(element), on, step.type(), step.pos());
    }

    /**
     * What the names in a closure read off the element it is handed: a field chain, or nothing this
     * trace can follow. A binding introduces what it reads <em>where it is written</em> — an expansion
     * splices {@code let $0_r = r in ...} into a closure — so what a name denotes is settled against
     * the bindings before it and not reread later.
     */
    private static final class Reads {

        private final BindingId element;
        private final Map<BindingId, List<String>> chains = new HashMap<>();

        private Reads(BindingId element) {
            this.element = element;
        }

        /** The expression the body produces, with what the bindings on the way there read taken in. */
        Core produced(Core body) {
            Core cur = body;
            while (cur instanceof Core.LetIn li) {
                chains.put(li.binder().binding(), chain(li.value()));
                cur = li.body();
            }
            return cur;
        }

        /** The chain {@code e} reads off the element, or {@code null} if it reads anything else. */
        List<String> chain(Core e) {
            return switch (e) {
                case Core.LetIn li -> {
                    Reads inner = new Reads(element);
                    inner.chains.putAll(chains);
                    inner.chains.put(li.binder().binding(), chain(li.value()));
                    yield inner.chain(li.body());
                }
                case Core.FieldAccess fa -> {
                    List<String> head = chain(fa.target());
                    if (head == null) {
                        yield null;
                    }
                    List<String> out = new ArrayList<>(head);
                    out.add(fa.field());
                    yield out;
                }
                case Core.Read r when chains.containsKey(r.binding()) -> chains.get(r.binding());
                case Core.Read r -> r.binding().equals(element) ? List.of() : null;
                default -> null;
            };
        }
    }

}
