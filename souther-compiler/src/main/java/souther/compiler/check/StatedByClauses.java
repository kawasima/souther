package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.types.Type;
import souther.compiler.values.AdmissibleValues;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Allowance;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.Realizations;
import souther.compiler.values.Realized;
import souther.compiler.values.Sameness;
import souther.compiler.values.Unbuilt;
import souther.compiler.values.UnreadReason;
import souther.compiler.values.ValueSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * What the clauses of one rule say, in each of the languages a clause is read in.
 *
 * <p>Two languages and one reading. Which values a position may take is a set, and where a position
 * stops is a range, and neither says what the other says — an ordering names no finite set, and a
 * set of values has no word for what lies between two of them. So a clause reaches whichever of them
 * has a word for it, and some clauses reach both.
 *
 * <p><b>The tree the author wrote, node for node.</b> A conjunction stands where it was written and
 * a choice's alternatives are what stands between its brackets ({@link #mirrors}). Which values a
 * position may take is another matter — settled by every clause together, so a conjunction there
 * distributes over a choice and a branch is refined by clauses written beside it — and that is a
 * rewriting the reading performs rather than one an author wrote. It happens in the projection onto
 * {@link StatedTogether} ({@link Reading#together}), which this type cannot be reached from: the
 * one thing coming back is the fate of each choice ({@link Settlement}).
 *
 * <p>So everything a choice is answerable for is asked here, of the alternatives as written. Asked
 * over the tree that derives values, a conjunct written beside a choice is a conjunct of both its
 * alternatives, and a form nothing reads written there makes both of them alternatives nothing
 * could read — sending an author to a choice that reads perfectly well.
 *
 * <p><b>One rule's, and never met with another rule's.</b> This tree answers what one rule's own
 * clauses took in, and that answer must come from those clauses alone — a constraint a neighbouring
 * rule states does not change what this one read, however much it changes what the value admits. The
 * conjunction across rules is written over {@link StatedTogether} and has no operation here to
 * arrive by.
 *
 * <p><b>The connectives are over this and not over either of them.</b> A choice between two
 * alternatives is a choice between two readings of the whole value, so an alternative that cannot be
 * taken is dropped by asking the whole of what is known about it
 * ({@link PlannedValues#holdsNothingAsBuilt}). Applied
 * inside each language on its own, the drop happened only where the language doing the joining was
 * also the one that could show the branch impossible: {@code s < "" || (b == true && b == false)}
 * has a branch no order admits beside a branch no set of values admits, and each language, joining
 * alone, found nothing wrong with the branch the other one had refused. The same shape appears
 * inside one language across two positions, since a join keeps only what both sides spoke about.
 *
 * <p><b>Two of the four domains of {@link ConstraintState} are not here, and it is not an
 * oversight.</b> The interval algebra and the predicates are what a construction owes
 * ({@link Predicates}), which is a different question about the same clause: an alternative owes a
 * construction nothing a guard could discharge, so neither of them ever reads a branch of a choice.
 * A branch impossible only by an arithmetic relation between two positions is one nothing here can
 * drop, and giving those two a reading of alternatives is its own change with its own reason.
 */
sealed interface StatedByClauses {

    /** One clause of no connective, in both languages, with the account of what each took in. */
    record Said(Confinement.Planned<FactSubject> confinement, Part took)
            implements StatedByClauses {

        /** What the positions of this reading may hold, still as descriptions. */
        PlannedValues<FactSubject> values() {
            return confinement.values();
        }
    }

    /** Both readings holding at once, as the author wrote them and in neither order. */
    record Both(StatedByClauses left, StatedByClauses right) implements StatedByClauses {

        public Both {
            if (left == null || right == null) {
                throw new IllegalArgumentException("a conjunction is between two readings");
            }
        }
    }

    /**
     * Either reading holding, at the {@code ||} an author wrote.
     *
     * <p>The alternatives are what stands between the brackets and nothing else. Everything this
     * choice is answerable for is asked of them, so a conjunction written beside the brackets must
     * not reach them — and there is no operation on this type by which one could.
     */
    record Either(ChoiceId id, Core writtenAt, StatedByClauses left, StatedByClauses right)
            implements StatedByClauses {

        public Either {
            if (id == null || writtenAt == null || left == null || right == null) {
                throw new IllegalArgumentException(
                        "a choice is between two named readings, written somewhere");
            }
        }
    }

    /**
     * The same reading, remembering that it is what {@code node} came to.
     *
     * <p>A wrapper and not a mark carried inside. What a written part came to is answered by the
     * reading under it, so recording one leaves the tree the shape the author wrote it — a part
     * pushed down into the branches of a choice would be a part of each of them, and every question
     * the choice asks of an alternative would be answerable from a clause written above it.
     */
    record CameFrom(Core node, StatedByClauses of) implements StatedByClauses {

        public CameFrom {
            if (node == null || of == null) {
                throw new IllegalArgumentException("a part is some node, read into something");
            }
        }
    }

    /**
     * What a reading of some clause took in, which is what a written part of it came to.
     *
     * <p>Worked out from the fates and not asked for again. Which branch of a choice anybody can be
     * in is settled by the rules of every clause together, and a part read again on its own is read
     * against a tree that decision never reached — so it answers about a branch the declaration has
     * already dropped, and it pays to find out.
     *
     * <p>What is here is what a reader of the account needs of a part, and every one of them is a
     * fact about this written part: how each language took it in, what it says about the strings,
     * which machines it asked for, and what a rule of it is answerable for. What the part finally
     * admits is not among them — that is the whole value's answer and belongs to the whole, and so
     * is what a position was left holding, which is kept once by the carrier that answers for the
     * position and copied nowhere.
     *
     * @param aboutStrings what this part states about the strings at each position it states a rule
     *                     about. The position is here whether or not the rule was read, because
     *                     which of a position's numbers a rule is written about is settled by the
     *                     call; what the rule leaves is the other half, and a rule this could not
     *                     read has none
     * @param ruleShortfalls what a rule is answerable for, each saying the written place the
     *                       reading decided it at. Made where the decision was made and never read
     *                       back out of what a position was left holding: a place holds the reasons
     *                       of every rule that reached it and names none of them.
     *
     *                       <p>Its algebra is the branch's. A branch nobody can be in has none —
     *                       there is no clause for an author to look at. A branch beside a dead one
     *                       keeps its own and takes nothing from the other. Two branches somebody
     *                       can be in have both, together with whatever the choice between them
     *                       raised
     */
    record Part(Adoption<FactSubject> byValues, Adoption<FactSubject> byOrder,
                Map<FactSubject, StringRestriction> aboutStrings,
                Set<AdmissibleReading.AskedAt> asked,
                Set<RuleShortfall> ruleShortfalls) {

        /** What a clause of no connective, that no reading has a word for, took in. */
        static Part nothing() {
            return new Part(Adoption.nothing(), Adoption.nothing(), Map.of(), Set.of(), Set.of());
        }

        /**
         * Both readings holding at once.
         *
         * <p>Nothing spoils anything and nothing is raised: both clauses are the author's, both
         * were read, and a conjunction offers no alternative for anything to have gone unread in.
         */
        Part both(Part other) {
            return new Part(byValues.both(other.byValues()), byOrder.both(other.byOrder()),
                    StringRestriction.over(aboutStrings, other.aboutStrings(), true),
                    askedIn(asked, other.asked()),
                    shortOf(ruleShortfalls, other.ruleShortfalls()));
        }

        /** The same part, in a branch nobody can be in — see {@link Adoption#inADeadBranch}. */
        Part inADeadBranch() {
            // The reasons go with it too. A rule of a branch nothing satisfies is not a rule of
            // this declaration that went unread; there is no branch for an author to look at.
            //
            // And neither is what it said about the strings at a position. A line drawn from a
            // branch nobody can be in is a line the model does not draw.
            // What it asked for is kept. A machine refused for a pattern in a branch nobody can be
            // in was still asked for by that pattern, and a part that forgot it would leave the
            // refusal with nothing to be about.
            // And what a rule is answerable for goes with them, for the same reason: there is no
            // branch for an author to look at, so there is nothing for a shortfall to send them to.
            return new Part(byValues.inADeadBranch(), byOrder.inADeadBranch(), Map.of(),
                    asked, Set.of());
        }

        /** This part of the branch that stands, beside the same part of one nobody can be in. */
        Part beside(Part gone) {
            // The standing branch's own, and nothing from the other. What a rule of a branch
            // nothing satisfies is answerable for is not a rule of this declaration at all.
            return new Part(byValues.beside(gone.byValues()), byOrder.beside(gone.byOrder()),
                    aboutStrings, askedIn(asked, gone.asked()), ruleShortfalls);
        }

        /** The same part of two branches, neither of which anybody can be in. */
        Part bothDead(Part other) {
            return new Part(byValues.bothDead(other.byValues()),
                    byOrder.bothDead(other.byOrder()), Map.of(),
                    askedIn(asked, other.asked()), Set.of());
        }

        /** The same part of two branches somebody can be in, under the choice between them. */
        Part either(RuleShortfall.Site.AtAChoice choice, AlternativeOpening opening, Part other) {
            // What a rule is answerable for is said of the choice, beside it and never out of it.
            // What happened is that this choice offered an alternative nothing could read, so an
            // author is sent to the choice — filed at a leaf under the branch that was read, they
            // would be sent to a clause nothing complained of. Made here, where the choice is: what
            // a position was left holding is the position's and names no choice.
            //
            // Which positions the choice opened is {@code opening}'s and is not worked out again
            // here: the same answer is what the positions themselves are told, and two of them
            // would agree only until somebody changed one.
            // An assertion because it is about this compiler and not about any model, and here
            // rather than in one test because every choice a corpus holds goes through it. What is
            // asked of one alternative below is asked of one because the other cannot be answerable
            // for the same written place; a tree that put one under both would leave the question
            // answered by whichever branch it was asked of.
            assert Collections.disjoint(ruleShortfalls, other.ruleShortfalls())
                    : "two alternatives of one choice are answerable for one written place";
            Set<RuleShortfall> shortfalls = new LinkedHashSet<>(ruleShortfalls);
            shortfalls.addAll(other.ruleShortfalls());
            leftOpenBy(choice, opening.byTheRightGoingUnread(), other.ruleShortfalls(), shortfalls);
            leftOpenBy(choice, opening.byTheLeftGoingUnread(), ruleShortfalls, shortfalls);
            return new Part(byValues.either(other.byValues()), byOrder.either(other.byOrder()),
                    StringRestriction.over(aboutStrings, other.aboutStrings(), false),
                    askedIn(asked, other.asked()), held(shortfalls));
        }

        /**
         * What the choice left open at each of {@code these} that {@code unread} does not already
         * account for, which is one fact about the choice.
         *
         * <p>The alternative nothing could read is what leaves these positions open, and at a
         * position that branch is answerable for something of its own it is the same shortfall
         * arriving by another road: {@code n /= 5 || Int.abs(n) >= 2} is one form nothing reads and
         * not a form and a choice, and counting both would have a reader lift the form and find the
         * question still there. Where the branch says nothing of its own about the position — the
         * other side of {@code left /= right || code == "A"} never names {@code code} — the choice
         * is the first thing to say anything, so it says it.
         *
         * <p><b>Asked of what that branch is answerable for and never of what the position holds.</b>
         * The place holds the reasons of every rule that reached it, so suppressing by it would
         * make a fact about a rule turn on what a neighbour wrote. The position's own account comes
         * to the same answer by a rule of its own ({@code Standing.across}), and the two are two
         * rules over two things.
         *
         * <p>What the alternative beside it is answerable for is not asked. It cannot be answerable
         * for the same thing: a written place is under one alternative or the other, and this tree
         * is the one the author wrote — so a shortfall of the branch that stands is a shortfall of
         * a clause the unread branch does not contain, and says nothing about whether the unread
         * one left the position open.
         *
         * <p>Whole shortfalls, so what an author wrote is part of the comparison and a copy of one
         * fact is told from two facts of the same shape. Never their reasons: a rule about which
         * reasons suppress which would be one more place the vocabulary has to be consulted, and a
         * reason added to it would quietly change what a choice says.
         */
        private static void leftOpenBy(RuleShortfall.Site.AtAChoice choice, Set<FactSubject> these,
                                       Set<RuleShortfall> unread, Set<RuleShortfall> out) {
            these.stream()
                    .filter(each -> !accountedFor(each, unread))
                    .forEach(each -> out.add(new RuleShortfall(each,
                            UnreadReason.ALTERNATIVE_NOT_READ, choice)));
        }

        /** Whether {@code unread} holds an account of {@code at}. */
        private static boolean accountedFor(FactSubject at, Set<RuleShortfall> unread) {
            return unread.stream().anyMatch(one -> one.position().equals(at));
        }

    }

    /**
     * What one written choice left open by offering an alternative nothing could read.
     *
     * <p>The one decision, made where the alternatives are what an author wrote between the
     * brackets, and read from two sides afterwards. The positions hear that they may be wider than
     * the rules leave them ({@code AdmissibleValues.openedByAlternative}); the account of the rule
     * hears which choice to send an author to. Worked out on either side instead, the side working
     * it out would be holding branches a conjunction beside the choice had been distributed into.
     *
     * <p>Both sides separately, because what suppresses one is not what suppresses the other: a
     * choice both of whose alternatives went unread is two things to answer for, and each is
     * weighed against what the branch beside it is already answerable for.
     *
     * <p><b>Two sets and not one, because the two sides ask two questions of it.</b> An author has
     * to look at this choice wherever the alternative beside the unread one <em>reached</em> a
     * position, whether or not the reading could promise anything there — that is a fact about
     * clauses somebody wrote. A position is left wider than the rules only where that alternative
     * <em>promised</em> it something the unread one takes back; an alternative holding a clause
     * nothing could read promises nothing, so it has nothing to be taken back and the position
     * keeps whatever account it already had. Made one, {@code (P(a) && f(b)) || (P(a) && f(b))}
     * came out with {@code a} reported wider than the rules hold it.
     *
     * @param byTheLeftGoingUnread  the positions the right alternative reached, where the left is
     *                              one nothing could read. Empty where it was read
     * @param byTheRightGoingUnread the same the other way round
     * @param positions             the positions the unread alternative left open, which is what
     *                              the one beside it promised
     */
    record AlternativeOpening(ChoiceId choice, Set<FactSubject> byTheLeftGoingUnread,
                              Set<FactSubject> byTheRightGoingUnread, Set<FactSubject> positions) {

        // Copied on the way in, as everything a reading publishes is. What is here is handed to the
        // positions and kept in what they came to, so a maker that went on writing to the set it
        // built one from would be changing what an answer already given says.
        public AlternativeOpening {
            if (choice == null) {
                throw new IllegalArgumentException("an opening is some choice's");
            }
            byTheLeftGoingUnread = held(byTheLeftGoingUnread);
            byTheRightGoingUnread = held(byTheRightGoingUnread);
            positions = held(positions);
        }

        private static Set<FactSubject> held(Set<FactSubject> these) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(these));
        }
    }

    /**
     * What one choice left open, out of what its two alternatives took in.
     *
     * <p>Over what each of them reached and did not merely settle: a position a dead branch settled
     * is an answer, and an alternative nothing could read widens a constraint rather than an
     * answer.
     *
     * <p>Of the reading of values alone, which is the one a choice is asked of. Where a position's
     * order stops is not what an alternative takes back — a range says nothing about which values
     * stand anywhere, so a branch nothing read leaves the ranges beside it saying what they said.
     */
    static AlternativeOpening opens(ChoiceId choice, Adoption<FactSubject> one,
                                    Adoption<FactSubject> other) {
        Set<FactSubject> opened = new LinkedHashSet<>();
        if (one.dropped()) {
            opened.addAll(promisedBy(other));
        }
        if (other.dropped()) {
            opened.addAll(promisedBy(one));
        }
        return new AlternativeOpening(choice,
                one.dropped() ? reachedBy(other) : Set.of(),
                other.dropped() ? reachedBy(one) : Set.of(),
                opened);
    }

    /**
     * The positions an alternative promised something at, which is what an unread one beside it
     * takes back.
     *
     * <p>Nothing where it holds a clause this could not read. What a branch promises is what it
     * promises having read everything it was given, and a conjunct nothing could read is exactly
     * what it was not given — so there is nothing there for the alternative beside it to widen, and
     * whatever account the position has is its own.
     */
    private static Set<FactSubject> promisedBy(Adoption<FactSubject> of) {
        return of.dropped() ? Set.of() : of.read();
    }

    /** The positions a reading reached and did not merely settle: what it constrained, and what it
     *  was about and could not manage. */
    private static Set<FactSubject> reachedBy(Adoption<FactSubject> of) {
        Set<FactSubject> out = new LinkedHashSet<>(of.read());
        out.addAll(of.missed());
        return out;
    }

    /** Nothing read, so nothing ruled out. */
    static StatedByClauses top(Map<FactSubject, Carrier> carriers) {
        return new Said(Confinement.Planned.top(carriers), Part.nothing());
    }

    /**
     * Whether {@code read} is the tree {@code clause} was written as.
     *
     * <p>What this type is for, said as a predicate. Every question a choice answers is asked of its
     * two alternatives, and an alternative is what stands between the brackets — so a reading that
     * moved a node across a connective would be answering those questions about a clause somebody
     * wrote somewhere else. The values are worked out over a tree that does move nodes across
     * connectives ({@link Reading#together}), which is why the two are two types.
     *
     * <p><b>Same nodes, same places, and not the same count of them.</b> Distribution leaves the
     * number of choices where it was — {@code (a || b) && c} and {@code (a && c) || (b && c)} are
     * one {@code ||} each — so what is held is which node stands under which, down to the identity
     * of every node an author wrote.
     */
    static boolean mirrors(Core clause, StatedByClauses read) {
        // The fold names the clause once as it starts and again as the shape of it is finished, so
        // the outermost of these is the one the caller put there.
        return read instanceof CameFrom it && it.node() == clause
                && mirrors(ClauseExpr.of(clause, true), it.of());
    }

    private static boolean mirrors(ClauseExpr shape, StatedByClauses read) {
        StatedByClauses under = read;
        // Every node the shape was spelled as, innermost first: that is the order the fold wrapped
        // them in, and a reading that recorded a part anywhere else is one this does not accept.
        List<Core> spelled = shape.spelled();
        for (int each = spelled.size() - 1; each >= 0; each--) {
            if (!(under instanceof CameFrom it) || it.node() != spelled.get(each)) {
                return false;
            }
            under = it.of();
        }
        return switch (shape) {
            case ClauseExpr.Leaf _ -> under instanceof Said;
            case ClauseExpr.Scoped it -> mirrors(it.body(), under);
            case ClauseExpr.Joined it -> switch (it.how()) {
                case BOTH -> under instanceof Both both
                        && mirrors(it.left(), both.left())
                        && mirrors(it.right(), both.right());
                case EITHER -> under instanceof Either choice && choice.writtenAt() == it.of()
                        && mirrors(it.left(), choice.left())
                        && mirrors(it.right(), choice.right());
            };
        };
    }

    /** The reading of one value's positions, made once and used over however many clauses reach it.
     *  Built per clause, this walk paid for a pair of readers at every clause of every value. */
    static Reading readingOf(Terms terms, Map<FactSubject, Type> byName,
                             Symbols symbols, Alternatives alternatives,
                             Allowance<FactSubject> allowed) {
        return new Reading(AdmissibleReading.of(terms, byName, symbols, allowed),
                OrderedReading.of(terms, byName, symbols), terms, byName, alternatives);
    }




    /**
     * The two readings of one clause tree, run together so that the connectives are the clause's,
     * and where they took a leaf in.
     *
     * <p>{@code adopted} is filled by the readings as they read, and is theirs to say. Read off
     * what a reading leaves a position instead, a clause it took in whole and narrowed nothing by
     * comes back unread: {@code value == 5 || value /= 5} is read at both leaves and joins to every
     * value there is. That is the same reconstruction {@code Predicates.Assumed} keeps a field of
     * its own to avoid, and the one this accounting was written against.
     *
     * <p><b>What a branch's fate is decided by is these two readings and nothing else.</b> A
     * conjunct of the clause reaches a branch by being met into it, and the choices are settled
     * while some of that is still to come — so a branch is refused here on less than the whole
     * declaration says about it, and every reading that could refuse it later will. What another
     * component of the state requires of a position is therefore not asked here: asked, a branch
     * would be refused by a bound its own clause was about to place, and the proof carried out of
     * the fold would say the readings needed something outside them when they did not.
     * ({@link ConstraintState#positionEnvelope} is asked where the readings have finished.)
     */
    record Reading(AdmissibleReading values, OrderedReading ordered, Terms terms,
                   Map<FactSubject, Type> byName, Alternatives alternatives)
            implements ClauseReading<StatedByClauses, Denotations> {

        /** What a binding under a clause is entered as, for a fold over this reading. The one
         *  answer there is, asked of the one place that gives it (ADR-0106). */
        ClauseScope<Denotations> scope() {
            return terms::inside;
        }

        @Override
        public StatedByClauses nothingSaid() {
            return top(ordered.carriers());
        }

        /** Either reading holding, as each language says it and the whole is held. */
        private Confinement.Planned<FactSubject> either(Confinement.Planned<FactSubject> one,
                                                       Confinement.Planned<FactSubject> other) {
            return one.either(other, alternatives == Alternatives.APART);
        }

        /**
         * What each language makes of one leaf, and where each of them took it in.
         *
         * <p>Which positions the leaf is about is the clause's own content and is read once here;
         * which of them a language managed is that language's, taken from what it produced at this
         * leaf and nowhere else. A leaf about a position that a language has no word for is one it
         * missed, and the other language answering for it is what makes them two accounts rather
         * than one.
         */
        @Override
        public StatedByClauses leaf(Core e, boolean positive, Denotations at) {
            PlannedValues<FactSubject> said = values.leaf(e, positive, at);
            OrderedIntervals<FactSubject> range = ordered.leaf(e, positive, at);
            Set<FactSubject> mentions = mentioned(e, at);
            return new Said(new Confinement.Planned<>(said, range, ordered.carriers()), new Part(
                    // Each language says whether it gave up on the leaf. The reading of values
                    // carries it; the reading of order has nothing to hand back but its ranges, and
                    // a leaf it read leaves at least one.
                    Adoption.at(mentions, said.adoptedAt(), values.gaveUpAt(e)),
                    Adoption.at(mentions, range.ranges().keySet(), range.ranges().isEmpty()),
                    // And what the leaf states about the strings at a position, where it is a rule
                    // about them. Asked of the reading that recognises one, so this is where the
                    // answer enters and the connectives below are what compose it.
                    values.stringRuleIn(e, said, at),
                    values.askedAt(e),
                    // And what a rule of this leaf is answerable for, asked of the reading that
                    // decided it and written down where it decided. Read off what the leaf leaves
                    // the positions instead, this would be a list of reasons and no clause.
                    held(values.shortfallsAt(e))));
        }

        /**
         * The positions of this value that {@code e} names.
         *
         * <p>A fact about the clause and not about either language, which is why it is read here
         * and once. A position names itself, and nothing under it is a position of its own.
         */
        private Set<FactSubject> mentioned(Core e, Denotations at) {
            Set<FactSubject> found = new LinkedHashSet<>();
            gather(e, found, at);
            return found;
        }

        private void gather(Core e, Set<FactSubject> found, Denotations at) {
            // Crossed as a binding, for the reason {@code AdmissibleReading.gather} gives.
            if (e instanceof Core.LetIn li) {
                gather(li.body(), found, terms.inside(li, at));
                return;
            }
            FactSubject here = terms.subjectOf(e, at);
            if (here != null && byName.containsKey(here)) {
                found.add(here);
                return;
            }
            Core.forEachChild(e, child -> gather(child, found, at));
        }

        /**
         * Both holding at once, written down as the conjunction it is.
         *
         * <p>Nothing is rewritten here. A conjunction of a choice is also the choice between the
         * conjunctions, and the values are worked out that way ({@link #together}) — but that is a
         * rewriting the reading performed and not one the author wrote, so it belongs to the tree
         * that derives values and not to this one. Distributed here, every conjunct written beside
         * a choice would be a conjunct of each of its alternatives, and each question the choice
         * asks of an alternative — whether anything read it, what it left open — would be answered
         * from a clause written outside the brackets.
         */
        @Override
        public StatedByClauses both(StatedByClauses one, StatedByClauses other) {
            return new Both(one, other);
        }

        /**
         * What was read, remembering that it is what {@code e} came to.
         *
         * <p>Kept in the reading so that the branch rules below reach it. A part is answered
         * differently in a branch that stands and in one nobody can be in, and which of those it
         * turned out to be is settled by every clause of the value together. Wrapped round what it
         * came to rather than pushed into it, so that recording a part leaves the tree the shape
         * the author wrote.
         */
        @Override
        public StatedByClauses from(Core e, StatedByClauses out) {
            return new CameFrom(e, out);
        }

        /**
         * Either alternative holding, at the {@code ||} an author wrote it with.
         *
         * <p>The id is minted here, where the two alternatives are what stands between the
         * brackets. Given no identity, an alternative nothing could read would have nowhere to
         * stand and an author would be sent to a branch that was read; given no place, nothing
         * downstream could put it in the order it was written in.
         *
         * <p>Which of the branches anybody can be in is not decided here. It is a question about
         * the values, settled where the values are worked out ({@link #together},
         * {@link #settling}), and what comes back to this tree is the fate.
         */
        @Override
        public StatedByClauses either(Core writtenAt, StatedByClauses one, StatedByClauses other) {
            return new Either(new ChoiceId(), writtenAt, one, other);
        }

        /**
         * The same clauses as the tree that derives values, with the account left behind.
         *
         * <p>This is where a conjunction distributes over a choice: which values a position may take
         * is settled by every clause together, so a branch is refined by the clauses written beside
         * it, and merged into one first the values would be answering about a branch that may not be
         * there. What is dropped on the way is the account — the tree above keeps it, and keeps it
         * over the alternatives as they were written.
         *
         * <p><b>Every alternative impossible is not one alternative impossible.</b> Where one of
         * them cannot be taken, the answer is the other and the first one's evidence goes with it —
         * nothing satisfies it, so what it said narrows nothing a value of this type is under. Where
         * <em>all</em> of them cannot be taken, no one of them speaks for the rest: taking the first
         * to be found impossible out of the answer would settle the proof by the order the operands
         * were written in, and the same model written two ways would be refused two ways.
         *
         * <p>Nor may they be met. A meet is a conjunction and the alternatives were never stated
         * together: {@code (a < "" && b == 0) || (a < "" && b == 1)} is impossible because of
         * {@code a}, and met it is a {@code b} bounded at 0 and at 1 — a contradiction neither
         * alternative contains, at a position the rules are fine with, and one the refusal would
         * then be written about.
         *
         * <p><b>So which of four a choice is is decided here, and the languages realise the
         * answer.</b> Every alternative dead is what both of them leave empty
         * ({@link Confinement.Planned#bothDead}); one alternative dead is the other alternative,
         * which is held already and asks the languages nothing; both standing is what two readings
         * somebody can be in come to ({@link Confinement.Planned#either}); and a fate nothing has
         * settled keeps the choice. What each language is asked is which of those it is realising,
         * and never which of them it is — a language may be perfectly happy with a branch the other
         * refused, so a language asked to decide would answer about a branch and not about the
         * choice.
         *
         * <p>What every alternative leaves empty is what the dead choice leaves empty, and where
         * that is no position, the choice admits nothing with none of them at fault. That is the
         * rule {@link Emptiness.AcrossEveryCase} states for a sum — what proves it has none is the
         * whole list — arrived at here for the same reason.
         *
         * <p>A choice this settles is settled once and for the whole rule, because it is settled
         * over the alternatives as written and there is one of those however many places
         * distribution afterwards puts the branch. What it decided is handed to {@code decided}, so
         * that the account is given a fate for every choice of the rule and never works one out.
         *
         * <p>And what each choice left open is decided here for the same reason, out of what the
         * alternatives took in. Here rather than in a walk of its own, because the answer has to be
         * about the branches this walk decides: worked out beforehand, a choice would be answerable
         * for a position only a branch it has already shown nobody can be in ever reached.
         */
        StatedTogether together(StatedByClauses read,
                                Map<ChoiceId, Settlement.OfAChoice> decided) {
            return switch (read) {
                case Said it -> new StatedTogether.Said(it.confinement());
                case CameFrom it -> together(it.of(), decided);
                case Both it -> together(it.left(), decided).meet(together(it.right(), decided));
                case Either it -> chosen(it, decided);
            };
        }

        private StatedTogether chosen(Either choice,
                                      Map<ChoiceId, Settlement.OfAChoice> decided) {
            StatedTogether one = together(choice.left(), decided);
            StatedTogether other = together(choice.right(), decided);
            if (one instanceof StatedTogether.Said here
                    && other instanceof StatedTogether.Said there) {
                Confinement.Admission<FactSubject> mine = here.confinement().admission();
                Confinement.Admission<FactSubject> theirs = there.confinement().admission();
                souther.compiler.values.Emptiness a = mine.emptiness();
                souther.compiler.values.Emptiness b = theirs.emptiness();
                // A branch the descriptions already show empty is decided here whichever way the
                // declaration holds its choices. The answer is definitive — a description empty
                // before anything is built admits nothing however the other clauses refine it —
                // and a clause written later can only show more branches impossible, never fewer,
                // so nothing a deferral waits for can reach a different decision. Deferred anyway,
                // the dead branch would widen the met-together tree for nothing.
                //
                if (a == souther.compiler.values.Emptiness.EMPTY && b == souther.compiler.values.Emptiness.EMPTY) {
                    decided.put(choice.id(), settled(mine, theirs));
                    return new StatedTogether.Said(here.confinement().bothDead(there.confinement(),
                            Confinement.Admission.bothShown(mine, theirs)));
                }
                if (a == souther.compiler.values.Emptiness.EMPTY) {
                    decided.put(choice.id(), settled(mine, theirs));
                    return other;
                }
                if (b == souther.compiler.values.Emptiness.EMPTY) {
                    decided.put(choice.id(), settled(mine, theirs));
                    return one;
                }
                // Two live branches are another matter: merging them is the one decision a clause
                // written later can be too late for, since it is the branch structure the later
                // clause's conjunction would have refined. Held apart, the choice is held open past
                // the clause and settled by the rules of every clause together; the expansion the
                // deferral costs was counted over the whole declaration before any clause was
                // read, which is what admitted the declaration as APART at all.
                if (alternatives == Alternatives.MERGED
                        && a == souther.compiler.values.Emptiness.NONEMPTY && b == souther.compiler.values.Emptiness.NONEMPTY) {
                    decided.put(choice.id(), settled(mine, theirs));
                    return new StatedTogether.Said(
                            either(here.confinement(), there.confinement()));
                }
            }
            // And where whether a branch can be taken is not settled, the question waits, and the
            // fate comes back from where the machines are made.
            return new StatedTogether.Choice(choice.id(), one, other);
        }

        /** The fate of a choice the descriptions alone decided, for the account to read. */
        private static Settlement.OfAChoice settled(Confinement.Admission<FactSubject> one,
                                                    Confinement.Admission<FactSubject> other) {
            return new Settlement.OfAChoice(Settlement.Sided.settledAs(one),
                    Settlement.Sided.settledAs(other));
        }

        /**
         * The met-together reading worked out, with the fate of every branch beside it.
         *
         * <p>Once, and this is where every choice is answered — over branches that are readings
         * rather than descriptions of them, by the same four rules a settled reading always used.
         * What comes back is a value: nothing downstream is left holding a decision, and nothing
         * recomputes one — the accounts ({@link #accountOf}) read the fates out of it.
         *
         * <p>A fate is aggregated over every place distribution put the branch — the reasoning is
         * {@link Settlement}'s. What is decided here per place is the emptiness of that occurrence,
         * and the join over occurrences is associative, commutative and idempotent, so the answer
         * cannot turn on the order the clauses were met in.
         *
         * <p>{@code decided} is what the projection already settled off the descriptions. Such a
         * choice reaches nothing here — it was answered before the tree was built, and its branches
         * are not in it — so the two never meet at one id, and the account is still given a fate
         * for every choice its rule wrote.
         */
        Settlement settle(StatedTogether read, Allowance<FactSubject> by,
                          Map<ChoiceId, Settlement.OfAChoice> decided) {
            Map<ChoiceId, Settlement.OfAChoice> outcomes = new LinkedHashMap<>(decided);
            StatedTogether.Said said = settling(read, by, outcomes);
            return new Settlement(said.confinement().resolve(by), outcomes);
        }

        /** The same reading with every choice in it decided, each occurrence noting its fate. */
        private StatedTogether.Said settling(StatedTogether read, Allowance<FactSubject> by,
                                             Map<ChoiceId, Settlement.OfAChoice> outcomes) {
            return switch (read) {
                case StatedTogether.Said it -> it;
                case StatedTogether.Choice it -> {
                    StatedTogether.Said one = settling(it.left(), by, outcomes);
                    StatedTogether.Said other = settling(it.right(), by, outcomes);
                    Settlement.Sided here = probed(one, by);
                    Settlement.Sided there = probed(other, by);
                    outcomes.merge(it.id(), new Settlement.OfAChoice(here, there),
                            Settlement.OfAChoice::alsoSeen);
                    yield decided(one, here, other, there);
                }
            };
        }

        /**
         * What one occurrence of a choice leaves the values, by the rule its two fates pick.
         *
         * <p>The four rules of a settled reading, over the values and the ranges alone: what each
         * language recorded as taken in is the account's, answered over the rule's own tree with
         * the aggregated fates, and is not here to be answered per occurrence.
         */
        private StatedTogether.Said decided(StatedTogether.Said one, Settlement.Sided here,
                                            StatedTogether.Said other, Settlement.Sided there) {
            if (here.emptiness() == souther.compiler.values.Emptiness.EMPTY && there.emptiness() == souther.compiler.values.Emptiness.EMPTY) {
                // The rule for a choice nobody can take, named rather than arrived at: a join is
                // what two branches somebody can take come to, and neither of these is one.
                return new StatedTogether.Said(one.confinement().bothDead(other.confinement(),
                        Confinement.Admission.bothShown(here.shown(), there.shown())));
            }
            if (here.emptiness() == souther.compiler.values.Emptiness.EMPTY) {
                return keptTogether(other, there);
            }
            if (there.emptiness() == souther.compiler.values.Emptiness.EMPTY) {
                return keptTogether(one, here);
            }
            StatedTogether.Said left = keptTogether(one, here);
            StatedTogether.Said right = keptTogether(other, there);
            return new StatedTogether.Said(either(left.confinement(), right.confinement()));
        }

        /**
         * A branch that is being kept, holding what working it out could not build.
         *
         * <p>A branch shown to admit something is kept and there is nothing more to say; a branch
         * nothing could work out is kept for the same reason nothing was dropped — nobody showed it
         * empty — and that is not the same fact. Kept without saying so, the reading would call a
         * position open where the truth is that nobody looked.
         */
        private StatedTogether.Said keptTogether(StatedTogether.Said read,
                                                 Settlement.Sided known) {
            if (known.emptiness() != souther.compiler.values.Emptiness.UNDECIDED) {
                return read;
            }
            return new StatedTogether.Said(
                    read.confinement().alsoStanding(known.asPositionStanding()));
        }

        /**
         * Whether anything satisfies a branch, asked of this occurrence of it worked out.
         *
         * <p>Three answers. A branch whose ordering admits nothing, or whose values came out empty,
         * is one nobody can be in here; a branch every position of which was worked out and which
         * admits something is one somebody can be in; and a branch with a position this compiler
         * could not build is neither, whatever the widened set it came back with says — and what
         * could not be built comes back with the answer, so a branch kept on it is kept saying so.
         *
         * <p>What the descriptions settle is settled before anything is built: a branch shown to
         * admit nothing, or to admit something, is decided without a machine, and only where the
         * descriptions leave the question open is the branch worked out to answer it.
         */
        private Settlement.Sided probed(StatedTogether.Said read,
                                        Allowance<FactSubject> by) {
            Confinement.Admission<FactSubject> said = read.confinement().admission();
            if (said.emptiness() != souther.compiler.values.Emptiness.UNDECIDED) {
                return Settlement.Sided.settledAs(said);
            }
            // Worked out, the descriptions become sets and every alternative can be asked where its
            // positions stop — the same question, and a different answer for having been asked of
            // what a pattern comes to.
            Confinement.Worked<FactSubject> worked = read.confinement().resolve(by);
            Confinement.Admission<FactSubject> admitted = worked.admission();
            if (admitted.emptiness() != souther.compiler.values.Emptiness.UNDECIDED) {
                return Settlement.Sided.settledAs(admitted);
            }
            Realized<FactSubject> made = worked.made();
            // Kept as the two it is. What the answer was short of is a fact about the place and
            // holds of everything waiting on it; what a pattern asked for and was refused is a fact
            // about the pattern, and travels to whoever asked rather than to the place they asked
            // about.
            Map<FactSubject, List<UnreadReason>> answered =
                    new LinkedHashMap<>();
            made.aboutTheAnswer().forEach(each -> answered.merge(each.at(),
                    List.of(each.why()), ReadByClauses::alsoSaying));
            return new Settlement.Sided(Confinement.Admission.left(souther.compiler.values.Emptiness.UNDECIDED), answered,
                    made.aboutARule(), made.unbuilt());
        }

        /**
         * What one rule's clauses took in, answered over that rule's own tree.
         *
         * <p>The one thing taken from outside the rule is the fate of its choices, which is the
         * whole declaration's to say and arrives settled ({@link Settlement}). Everything else —
         * what each part adopted, what stopped this reading of it — was recorded as the rule was
         * read, so nothing here builds a machine and nothing here can be widened by a constraint a
         * neighbouring rule stated: no neighbouring rule is in the tree.
         */
        Account accountOf(StatedByClauses rule, StatedTogether projected, Settlement made,
                          Allowance<FactSubject> by) {
            Taken took = accounted(rule, made.outcomes());
            return new Account(narrowedBy(alone(projected, by).confinement().values(), by),
                    partsOf(took, made), took.opened());
        }

        /**
         * What this rule's own clauses leave, with its choices decided by its own clauses and by
         * nothing that was built for the account.
         *
         * <p>The rule alone, which is what a reader asking what <em>this</em> rule did to a position
         * has to be given. A branch its neighbours refuse is theirs to refuse, and a reading that
         * took the declaration's fates would hand this rule their narrowing.
         *
         * <p>Its own branches are dropped where something has already established that nobody can be
         * in them — off the descriptions where they say so, and otherwise off what the position's
         * answer was built to be. Nothing is built here: an account that paid for a machine would be
         * spending the budget the answer is bounded by, and a limit met while attributing a reason
         * would come out as this compiler being less able to answer about the model.
         *
         * <p>So a branch nothing has shown impossible is kept, and the rule reads as one that leaves
         * the position wider. That is an account declining to claim what nothing established, which
         * is the direction an account has to fail in.
         *
         * <p><b>Both languages, and the whole of what is known, which is what a fate is asked of.</b>
         * A branch is one nobody can be in where either language says so, and each of them is short
         * of what the other holds: {@code (n < 0 || String.matches("C", s)) && n > 1} has a branch no
         * order admits, and a fold that asked the values alone would find nothing wrong with it and
         * lose what the rule states about {@code s}.
         */
        private StatedTogether.Said alone(StatedTogether read, Allowance<FactSubject> by) {
            return switch (read) {
                case StatedTogether.Said it -> it;
                case StatedTogether.Choice it -> {
                    StatedTogether.Said one = alone(it.left(), by);
                    StatedTogether.Said other = alone(it.right(), by);
                    if (nobodyIsIn(other, by)) {
                        yield one;
                    }
                    if (nobodyIsIn(one, by)) {
                        yield other;
                    }
                    yield new StatedTogether.Said(
                            either(one.confinement(), other.confinement()));
                }
            };
        }

        /**
         * Whether something has already established that nobody can be in {@code branch}.
         *
         * <p>The two languages together ({@link Confinement}), and established rather than worked
         * out: an order is bottom or it is not, a description of values says so or waits, and where
         * it waits this asks what was already built for the position rather than building it.
         */
        private static boolean nobodyIsIn(StatedTogether.Said branch,
                                          Allowance<FactSubject> by) {
            return branch.confinement().alreadyEstablished(by) == souther.compiler.values.Emptiness.EMPTY;
        }

        /**
         * The positions the rule itself holds to less than every value.
         *
         * <p>Off the rule settled on its own and not off the declaration's answer, because that one
         * is met from every rule that reached the position: read there, a rule that holds nothing
         * down is handed whatever its neighbours held down.
         *
         * <p><b>And settled, rather than composed and left at that.</b> A choice is decided by
         * whether anything satisfies each branch, which for a language is not known until the
         * machine exists — {@code (String.matches("A", t) && String.matches("B", t)) ||
         * String.matches("C", s)} holds {@code s} to one string, because nothing is both {@code "A"}
         * and {@code "B"} and the branch asking for both is one nobody can take. Joined without
         * deciding, the two branches leave {@code s} at every value and the rule looks like one that
         * states nothing.
         *
         * <p>Its own fates and not the declaration's, which is the whole of the distinction. A
         * branch this rule's own clauses rule out is this rule's work and belongs to what it did; a
         * branch that stands until a neighbour refuses it is the neighbour's, and lending it here is
         * how a rule that states nothing came to be reported as holding a position down.
         *
         * <p>Read against what was built and never by building. A pattern is a name for a machine
         * until one exists, so a format admitting every string and one admitting some read alike on
         * a description — and the position's answer, which was built, is where that is told apart.
         * Where nothing built it, this says nothing: a position the answer never worked out is not
         * one a rule can be said to have narrowed.
         */
        private Set<FactSubject> narrowedBy(
                PlannedValues<FactSubject> mine,
                Allowance<FactSubject> by) {
            Set<FactSubject> out = new LinkedHashSet<>();
            Sameness<FactSubject> heldAsOne = mine.sameness();
            for (FactSubject each : mine.adoptedAt()) {
                ValueSet known =
                        by.known(heldAsOne.blockOf(each), mine.at(each));
                if (known != null && !known.isAny() && !known.isEmpty()) {
                    out.add(each);
                }
            }
            return out;
        }

        private Map<Core, PartAccount> partsOf(Taken took, Settlement made) {
            Set<FactSubject> unbuilt = made.made().unbuilt();
            // And what could not be built is given up on here too. What a leaf said it adopted was
            // said before any machine was made, so a position whose answer the whole reading did
            // not work out is one the account still calls taken in — while the values beside it
            // say it holds every value because nobody worked it out.
            Map<Core, PartAccount> parts = new IdentityHashMap<>();
            took.parts().forEach((each, part) -> parts.put(each, new PartAccount(
                    part.byValues().unbuiltAt(unbuilt),
                    part.byOrder().unbuiltAt(unbuilt),
                    // What the part's own reading decided, and the machines it asked for that were
                    // refused while the positions were worked out. The second is routed by what the
                    // refusal says — the pattern and the position it was being built for — to the
                    // clause that asked for it, and reaches no clause that asked for something else.
                    shortOf(part.ruleShortfalls(),
                            askedFor(made.made().aboutARule(), part.asked())),
                    part.aboutStrings())));
            return parts;
        }

        /**
         * What the rule's clauses took in, over the tree its author wrote, with the fates applied.
         *
         * <p>The alternatives of a choice are what stands between its brackets, so this is the one
         * walk that can say what a choice is answerable for. Walked over the tree the values are
         * derived from, every conjunct written beside a choice would be a conjunct of both its
         * alternatives, and a choice would be answerable for what a clause outside it left open.
         */
        private Taken accounted(StatedByClauses read,
                                Map<ChoiceId, Settlement.OfAChoice> outcomes) {
            return switch (read) {
                case Said it -> new Taken(it.took(), Map.of(), Set.of());
                case CameFrom it -> accounted(it.of(), outcomes).alsoAt(it.node());
                case Both it -> accounted(it.left(), outcomes)
                        .both(accounted(it.right(), outcomes));
                case Either it -> {
                    Taken one = accounted(it.left(), outcomes);
                    Taken other = accounted(it.right(), outcomes);
                    Settlement.OfAChoice fate = outcomes.get(it.id());
                    if (fate == null) {
                        // Every choice of a rule is either settled off the descriptions or stands
                        // somewhere in the met-together reading, so a fate nobody settled is this
                        // compiler's arithmetic gone wrong and not a fact about any model.
                        throw new IllegalStateException(
                                "a choice of a rule was never met in the settled reading");
                    }
                    souther.compiler.values.Emptiness here = fate.left().emptiness();
                    souther.compiler.values.Emptiness there = fate.right().emptiness();
                    if (here == souther.compiler.values.Emptiness.EMPTY && there == souther.compiler.values.Emptiness.EMPTY) {
                        yield one.bothDead(other);
                    }
                    if (here == souther.compiler.values.Emptiness.EMPTY) {
                        yield keptAs(other, fate.right()).beside(one);
                    }
                    if (there == souther.compiler.values.Emptiness.EMPTY) {
                        yield keptAs(one, fate.left()).beside(other);
                    }
                    // Here, where both branches have their fate. What an alternative nothing could
                    // read left open turns on which of them anybody can be in: a branch shown dead
                    // by a clause written elsewhere takes what it could not read with it, and until
                    // the whole declaration is settled that is not known. Asked before, this choice
                    // is answerable for what a branch of a branch it has already lost ever reached.
                    Taken live = keptAs(one, fate.left());
                    Taken beside = keptAs(other, fate.right());
                    yield live.either(
                            new RuleShortfall.Site.AtAChoice(it.id(), it.writtenAt().pos()),
                            opens(it.id(), live.took().byValues(), beside.took().byValues()),
                            beside);
                }
            };
        }

        /**
         * A branch of the rule that is being kept, holding what the settlement could not build in
         * it.
         *
         * <p>{@link #keptTogether} for the account: the reasons and the given-up positions were
         * settled where the branch's occurrences were probed, and are applied to what this rule's
         * parts said — a part about a position nobody worked out is one the account still calls
         * taken in until this is applied.
         */
        private Taken keptAs(Taken read, Settlement.Sided known) {
            if (known.emptiness() != souther.compiler.values.Emptiness.UNDECIDED) {
                return read;
            }
            return read.mapped(part -> new Part(
                    part.byValues().unbuiltAt(known.unbuilt()),
                    part.byOrder().unbuiltAt(known.unbuilt()),
                    part.aboutStrings(), part.asked(),
                    // What a machine was refused for, said as what a rule is answerable for, at
                    // the clause that asked for it rather than at the place it was built for. What
                    // the answer itself was short of is not here: that holds of everything waiting
                    // on the position and is the position's own, kept by the carrier that answers
                    // for the position.
                    shortOf(part.ruleShortfalls(),
                            askedFor(known.ruleShortfalls(), part.asked()))));
        }
    }

    /**
     * What a reading of some subtree took in, and what each written part under it came to.
     *
     * <p>Both from one walk. The parts are disjoint across a conjunction and across a choice —
     * nothing is written under two branches of a tree — so putting two of these together is a union
     * and never a merge, which is what the tree keeping the author's shape buys.
     */
    record Taken(Part took, Map<Core, Part> parts, Set<FactSubject> opened) {

        /** The same, with what a written part came to filed under it. */
        Taken alsoAt(Core node) {
            Map<Core, Part> out = new IdentityHashMap<>(parts);
            out.put(node, took);
            return new Taken(took, out, opened);
        }

        /** Both holding at once, each part still the part it was. */
        Taken both(Taken other) {
            return new Taken(took.both(other.took()), joined(parts, other.parts()),
                    opened(opened, other.opened()));
        }

        /** Every part of this, answered again — for what a settlement could not build in it. */
        Taken mapped(UnaryOperator<Part> answer) {
            Map<Core, Part> out = new IdentityHashMap<>();
            parts.forEach((each, part) -> out.put(each, answer.apply(part)));
            return new Taken(answer.apply(took), out, opened);
        }

        /**
         * This branch of a choice, beside one nobody can be in.
         *
         * <p>What the dead one said goes with it, its unread rules included: nothing satisfies it,
         * so what it left a position is not something a value of this type is under. What it does
         * leave is that the positions it named are settled — the choice does nothing to them — and
         * that is an answer only a reading that got to the end of the branch could give.
         */
        Taken beside(Taken gone) {
            // And what a choice inside it left open goes with it too, for the same reason: there
            // is no branch there for a position to be open in.
            return new Taken(took.beside(gone.took()),
                    joined(parts, gone.mapped(Part::inADeadBranch).parts()), opened);
        }

        /**
         * A choice neither branch of which anybody can take.
         *
         * <p>No one of them speaks for the rest: taking the first to be found impossible out of the
         * answer would settle the proof by the order the operands were written in, and the same
         * model written two ways would be refused two ways.
         */
        Taken bothDead(Taken other) {
            return new Taken(took.bothDead(other.took()),
                    joined(mapped(Part::inADeadBranch).parts(),
                            other.mapped(Part::inADeadBranch).parts()),
                    Set.of());
        }

        /**
         * A choice both branches of which somebody can take, said of the choice it is.
         *
         * <p>Which choice, because that is what an alternative nothing could read is a fact about.
         * Asked of the two alternatives and of nothing beside them: what the choice raises turns on
         * whether an alternative went unread, and a clause written outside the brackets is not one.
         *
         * <p>The parts under the branches are not put together, because they are not the same
         * parts. Each of them is written under one alternative and is answered by what happened to
         * that alternative, which is nothing — both stand.
         */
        Taken either(RuleShortfall.Site.AtAChoice choice, AlternativeOpening opening, Taken other) {
            return new Taken(took.either(choice, opening, other.took()),
                    joined(parts, other.parts()),
                    opened(opened(opened, other.opened()), opening.positions()));
        }

        private static Map<Core, Part> joined(Map<Core, Part> these, Map<Core, Part> those) {
            if (those.isEmpty()) {
                return these;
            }
            Map<Core, Part> out = new IdentityHashMap<>(these);
            out.putAll(those);
            return out;
        }

        private static Set<FactSubject> opened(Set<FactSubject> these, Set<FactSubject> those) {
            if (those.isEmpty()) {
                return these;
            }
            Set<FactSubject> out = new LinkedHashSet<>(these);
            out.addAll(those);
            return out;
        }
    }

    /**
     * A machine that was refused, said at each clause that asked for it.
     *
     * <p>The refusal knows the pattern and the position it was being built for, and what asked for
     * that machine there is what a clause wrote. Two clauses writing one pattern about
     * one position asked for one machine and are two of these — two rules answerable for one
     * refusal, which is what writing the same clause twice comes to.
     */
    private static Set<RuleShortfall> askedFor(
            Set<Unbuilt.RuleShortfall<FactSubject>> refused,
            Set<AdmissibleReading.AskedAt> asked) {
        Set<RuleShortfall> out = new LinkedHashSet<>();
        refused.forEach(each -> asked.stream()
                .filter(one -> one.position().equals(each.at()) && one.plan().equals(each.asked()))
                .forEach(one -> out.add(new RuleShortfall(each.at(), each.why(), one.site()))));
        return held(out);
    }

    /** What either of two readings asked a machine for, which is what both of them asked for. */
    private static Set<AdmissibleReading.AskedAt> askedIn(Set<AdmissibleReading.AskedAt> these,
                                                         Set<AdmissibleReading.AskedAt> those) {
        if (those.isEmpty()) {
            return these;
        }
        Set<AdmissibleReading.AskedAt> out = new LinkedHashSet<>(these);
        out.addAll(those);
        return Collections.unmodifiableSet(out);
    }

    /**
     * What either of two readings is answerable for, which is what both of them are.
     *
     * <p>A union and nothing more. Each of these is one fact about one written place, so two copies
     * of one are one and two places are two — and nothing here puts them in an order, which is the
     * source's to say and is asked where a document is written.
     */
    private static Set<RuleShortfall> shortOf(Set<RuleShortfall> these,
                                              Set<RuleShortfall> those) {
        if (those.isEmpty()) {
            return these;
        }
        Set<RuleShortfall> out = new LinkedHashSet<>(these);
        out.addAll(those);
        return held(out);
    }

    /**
     * These facts, as a value nobody can add to.
     *
     * <p>The one way one of these sets is finished, and it keeps the order the facts were met in.
     * That order is no part of what the set means — which written place a reader is sent to first is
     * the source's to say and is settled where a document is written — but a projection out of the
     * set does read it ({@code RuleAccounting.Why}), and a copy that reordered would have the same
     * compiler over the same source publish two documents. {@code Set.copyOf} is such a copy: it
     * salts the iteration order per run.
     */
    private static Set<RuleShortfall> held(Collection<RuleShortfall> these) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(these));
    }

    /**
     * The clauses of one value as they were read, and every part of them, waiting to be worked out.
     *
     * <p>Held rather than asked. What a clause adopted, and what each part of it adopted, turn on
     * which branch of a choice anybody can be in — and that is not known until the machines are
     * made. Asked as each clause was read, the account answered from what the reading happened to
     * hold, and a rule of a branch nothing satisfies was written down as a rule of this declaration
     * that went unanswered.
     *
     * <p><b>And worked out together, once.</b> Everything here is the same declaration's answer and
     * draws on the same allowance, so the order they are worked out in is the order it is spent in.
     * Kept in the order the clauses were read and the parts were reached: a walk over a table keyed
     * by what a node happens to be would make what a declaration can be told exactly turn on where
     * its clauses landed in it.
     *
     * @param <K> what a caller files each clause under
     */
    final class Asked<K> {

        private final Map<K, Core> byClause = new LinkedHashMap<>();
        private final Map<K, List<Core>> byPart = new LinkedHashMap<>();
        private final Map<K, StatedByClauses> trees = new LinkedHashMap<>();

        /** One clause read from {@code at}, with the parts of it noted in the order the reading
         *  reached them. */
        StatedByClauses read(Reading reader, Denotations at, K key, Core clause) {
            List<Core> parts = new ArrayList<>();
            StatedByClauses one = reader.read(clause, true, at, reader.scope(),
                    (part, _) -> parts.add(part));
            // An assertion because it is about this compiler and not about any model, and here
            // rather than in one test because every clause a corpus holds is read through it.
            assert mirrors(clause, one)
                    : "the reading of a clause is not the tree its author wrote it as";
            byClause.put(key, clause);
            byPart.put(key, parts);
            trees.put(key, one);
            return one;
        }

        /**
         * All of it worked out, once, and every rule of it answered over its own tree.
         *
         * <p>One settlement because there is one value: the clauses are met over
         * {@link StatedTogether}, in the order they were read, and everything that has to be built
         * is built there, under the one allowance, with the fate of every branch coming back as
         * part of the result. Each rule's account is then its own tree with those fates applied —
         * its own tree, because what a rule took in is a fact about its clauses and not about the
         * constraints its neighbours distributed beside them; and the fates, because a branch of it
         * nobody anywhere can be in is not a branch whose rules went unread. Nothing in the second
         * step builds a machine.
         */
        Answered<K> resolve(Reading reader, Allowance<FactSubject> by,
                            Allowance<FactSubject> handingOn) {
            // The projection is made once per rule and kept, because the account needs the same
            // one: what the rule leaves on its own is read off the tree that derives values, and a
            // second projection would be a second answer that agrees only until somebody changes
            // one of them.
            Map<ChoiceId, Settlement.OfAChoice> decided = new LinkedHashMap<>();
            Map<K, StatedTogether> projected = new LinkedHashMap<>();
            StatedTogether whole = StatedTogether.top(reader.ordered().carriers());
            for (Map.Entry<K, StatedByClauses> each : trees.entrySet()) {
                StatedTogether one = reader.together(each.getValue(), decided);
                projected.put(each.getKey(), one);
                whole = whole.meet(one);
            }
            Settlement made = reader.settle(whole, by, decided);
            // What the choices of every rule left open, gathered as each rule is accounted for and
            // told to the positions once they all are. It cannot be known before: a branch a clause
            // written elsewhere shows dead takes what it could not read with it, and which branches
            // those are is what the settlement above answers.
            Set<FactSubject> opened = new LinkedHashSet<>();
            Map<Core, PartAccount> said = new IdentityHashMap<>();
            Map<K, Set<FactSubject>> narrowed = new LinkedHashMap<>();
            Adoption<FactSubject> byValues = Adoption.nothing();
            Adoption<FactSubject> byOrder = Adoption.nothing();
            // What the answer has left, before an account is made out of it. Every account below
            // reads what was built and builds nothing, so this is what it costs — and an account
            // that spent would be taking the budget the answer is bounded by to say which rule a
            // reason belongs to.
            //
            // Here rather than around the whole of this: the answer above is what the allowance is
            // for and spends it by design, so a check that started before it would be about
            // something else and would never fail.
            int unspent = spentBy(by);
            for (Map.Entry<K, StatedByClauses> each : trees.entrySet()) {
                // The rule on its own as well as in the declaration, because what it did to a
                // position and what the position came to are two questions. Its own choices are
                // decided by its own clauses against what the answer already established; met with
                // its neighbours first, a branch they refuse is dropped and the rule is credited
                // with a narrowing it did not do.
                Account mine = reader.accountOf(each.getValue(),
                        projected.get(each.getKey()), made, by);
                said.putAll(mine.parts());
                opened.addAll(mine.opened());
                PartAccount clause = mine.parts().get(byClause.get(each.getKey()));
                narrowed.put(each.getKey(), mine.narrowed());
                byValues = byValues.both(clause.byValues());
                byOrder = byOrder.both(clause.byOrder());
            }
            // An assertion because it is about this compiler and not about any model, and here
            // rather than in one test because every declaration a corpus holds goes through it.
            assert unspent == spentBy(by)
                    : "making the accounts of a declaration spent its allowance";
            // And here the answer is finished: the positions a choice left open are told so, out of
            // the walk that knows both the alternatives an author wrote and what became of them.
            // Nothing above reads how wide a position is — a rule is answered against what the
            // allowance built, and whether the answer is the whole of what the rules leave is asked
            // from here on.
            Confinement.Worked<FactSubject> answered = made.confinement().alsoOpenedAt(opened);
            // And here the reading stops being one and becomes an answer. What each rule about the
            // strings at a position leaves is worked out now, once, and a reader downstream is
            // handed the sets.
            //
            // Out of the allowance for handing rules on and not out of the answer's. The sets the
            // answer needed are met together and are already built; these are what each rule leaves
            // on its own, which the answer had no use for — charged to it, what a position is read
            // to admit would turn on what a reader downstream was promised, and the assertion above
            // would be true of the accounts and false of the reading as a whole.
            Map<Core, ReadByClauses.OfAPart> published =
                    published(said, handingOn, answered.values());
            // And the answer's allowance is where it was. What a position admits is answered under
            // the allowance for it and nothing else reaches that allowance, so what this
            // declaration can be told exactly is the same whether or not anybody is ever handed
            // anything — which is the whole of why the sets handed on have an allowance of their
            // own.
            assert unspent == spentBy(by)
                    : "handing the rules of " + made.made().values().subjects()
                            + " on spent the allowance for what they admit";
            Map<K, ReadByClauses.OfARule> clauses = new LinkedHashMap<>();
            narrowed.forEach((key, these) -> clauses.put(key,
                    new ReadByClauses.OfARule(these, published.get(byClause.get(key)))));
            ReadByClauses read = new ReadByClauses(answered, byValues, byOrder, published);
            Map<K, List<Map.Entry<Core, ReadByClauses.OfAPart>>> parts =
                    new LinkedHashMap<>();
            byPart.forEach((key, these) -> {
                List<Map.Entry<Core, ReadByClauses.OfAPart>> out =
                        new ArrayList<>();
                these.forEach(each -> {
                    ReadByClauses.OfAPart one = published.get(each);
                    if (one != null) {
                        out.add(Map.entry(each, one));
                    }
                });
                parts.put(key, out);
            });
            return new Answered<>(read, clauses, parts);
        }

        /**
         * Every part with what it says about the strings worked out, position by position.
         *
         * <p><b>The whole of a position at once ({@link Allowance
         * #realizeAll}).</b> What this reading promises a reader is what each of its parts admits,
         * and the promise is one thing: a position whose allowance cannot make every one of them
         * publishes none, so which rules a reader hears about is not decided by which plan the
         * building reached first. Made part by part, two rules of one position each affordable
         * alone would be published or not by the order this walk happened to take.
         *
         * <p>What was not read stays what was not read. A rule this compiler could not turn into a
         * set is a fact about the rule and was settled long before anything was built, so it
         * crosses unchanged — it is not a set nobody made, and a reader that met it as one would be
         * told the position's allowance ran out on a rule nothing ever asked to be built.
         *
         * <p>What the answer built is read and not built again ({@link Allowance#besides}). A rule
         * that stands alone at a position is the position's answer, and its machine exists by the
         * time this asks — so what is charged to the allowance for handing rules on is what the
         * answer had no use for, which is what that allowance is for.
         *
         * <p><b>And nothing is published at a position the answer does not speak for.</b> What each
         * rule leaves is a projection of what the position admits, and a projection may not answer
         * where the thing it projects could not: given its own allowance to try again, the reading
         * would be telling a reader which strings a rule leaves at a position it has just said it
         * cannot say what stands at, and the two would differ over exactly the patterns one of them
         * can afford — which is the arrangement this whole reading exists to remove. So the second
         * allowance makes up what the first had no use for, and never what it could not manage.
         *
         * <p>Whether a position was answered exactly is asked of the answer
         * ({@link AdmissibleValues#speaksFor}) and worked out from nothing here. A rule left
         * standing at a position is one of the things that question weighs and not the answer to
         * it: an alternative that covers the position leaves it exact with an unread rule beside
         * it, and a reading that read the reasons for itself would call such a position short of
         * something and publish nothing about it, under a word for an allowance nothing had spent.
         *
         * @param answered what the positions came to, asked whether each of them is exact
         */
        private Map<Core, ReadByClauses.OfAPart> published(
                Map<Core, PartAccount> said, Allowance<FactSubject> handingOn,
                AdmissibleValues<FactSubject> answered) {
            Map<FactSubject, Set<AdmittedPlan>> asked = new LinkedHashMap<>();
            said.values().forEach(part -> part.aboutStrings().forEach((position, stated) -> {
                if (stated instanceof StringRestriction.Admitting it) {
                    asked.computeIfAbsent(position, _ -> new LinkedHashSet<>()).add(it.plan());
                }
            }));
            Map<FactSubject, Realizations> answers = new LinkedHashMap<>();
            // Built for the block the position is on, which is what the machine is being made for:
            // positions the rules hold as one value have one answer between them, and one purse.
            asked.forEach((position, plans) -> answers.put(position,
                    answered.speaksFor(position)
                            ? handingOn.realizeAll(answered.blockOf(position), plans)
                            : new Realizations.NotBuilt()));
            Map<Core, ReadByClauses.OfAPart> out = new IdentityHashMap<>();
            said.forEach((each, part) -> out.put(each, new ReadByClauses.OfAPart(
                    part.byValues(), part.byOrder(), part.aboutARule(),
                    admitted(part.aboutStrings(), answers))));
            return out;
        }

        /**
         * What one part's rules about the strings came to, out of the answers the positions gave.
         *
         * <p>A rule of a position that did not publish keeps its place and is told that much and no
         * more. Which rules a clause states about the strings is what a walk over it asks, and an
         * entry dropped would be read as a clause that states none; a reason put here instead would
         * be the position's shortfall written out once per rule of it, over rules that may each
         * have been affordable.
         */
        private static Map<FactSubject, AdmittedStrings> admitted(
                Map<FactSubject, StringRestriction> stated,
                Map<FactSubject, Realizations> answers) {
            if (stated.isEmpty()) {
                return Map.of();
            }
            Map<FactSubject, AdmittedStrings> out = new LinkedHashMap<>();
            stated.forEach((position, said) -> {
                switch (said) {
                    case StringRestriction.NotKnown it ->
                            out.put(position, new AdmittedStrings.NotKnown(it.why()));
                    case StringRestriction.Admitting it ->
                            out.put(position, answers.get(position) instanceof Realizations.Exact made
                                    ? new AdmittedStrings.Admitting(made.of(it.plan()))
                                    : new AdmittedStrings.NotPublished());
                }
            });
            return Collections.unmodifiableMap(out);
        }
    }

    /**
     * What {@link Asked} came to: the whole, each clause, and each part of each clause.
     *
     * <p>The second and third are looked up in the first. Reading them any number of times spends
     * nothing, because the work was done once and what is here is what it came to.
     */
    record Answered<K>(ReadByClauses whole, Map<K, ReadByClauses.OfARule> perClause,
                       Map<K, List<Map.Entry<Core, ReadByClauses.OfAPart>>> perPart) {}

    /**
     * What one rule's own tree came to: what it leaves narrowed, what each of its parts took in,
     * and what its choices left open.
     *
     * <p>All from one walk of the rule. Asked apart, the tree would be answered over twice and the
     * two answers agree only for as long as nobody changes one of them.
     *
     * @param opened the positions this rule's choices left open by offering an alternative nothing
     *               could read, which the positions themselves are told once every rule has been
     *               walked ({@code AdmissibleValues.alsoOpenedAt}). The account of the rule hears
     *               the same decision as a shortfall at the choice
     */
    record Account(Set<FactSubject> narrowed, Map<Core, PartAccount> parts,
                   Set<FactSubject> opened) {}

    /**
     * What one part came to, with what it says about the strings still named as a plan.
     *
     * <p>One step short of {@link ReadByClauses.OfAPart}, and the step is the only difference: the
     * rules about the strings at a position are still what would be built rather than what was.
     * They are made into sets once, for the whole position, where the reading publishes what it
     * read — so that the plans never reach a reader and the position pays for its own answer in one
     * place.
     */
    record PartAccount(Adoption<FactSubject> byValues, Adoption<FactSubject> byOrder,
                       Set<RuleShortfall> aboutARule,
                       Map<FactSubject, StringRestriction> aboutStrings) {}

    /** How much the allowance has spent in all, for holding an account to spending nothing — see
     *  {@code InvariantChecker.spentBy}. */
    private static int spentBy(Allowance<FactSubject> by) {
        return by.spentSoFar();
    }
}
