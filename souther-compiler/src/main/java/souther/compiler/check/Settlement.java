package souther.compiler.check;

import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Emptiness.SidesShownEmpty;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.Realized;
import souther.compiler.values.UnreadReason;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;

/**
 * What settling the met-together reading came to: the values, and the fate of every branch.
 *
 * <p>One computation and two projections of its result. Working {@link StatedTogether} out under an
 * allowance is what decides which values every position admits, and the same work is the only thing
 * that can say whether anybody can be in a branch of a choice — so both come back from it together,
 * as a value, and nothing recomputes either. The account of what each rule took in
 * ({@link StatedByClauses.Reading#accountOf}) consumes the fates; it holds no machinery to decide
 * one.
 *
 * <p><b>A fate is an aggregate over every place distribution put the branch, not an attribute of
 * one place.</b> The same written choice stands inside each branch of every choice met with it, and
 * one copy's branch can admit something where another copy's admits nothing — a branch of {@code B}
 * inside the left of {@code A} is refined by {@code A}'s left, and the same branch inside the right
 * by {@code A}'s right. What the written branch's author can act on is the whole declaration's
 * answer: nobody can be in it only if nobody can be in it anywhere it stands. So the sides join over
 * occurrences by {@link souther.compiler.values.Emptiness#joined}, which is associative, commutative and idempotent — and
 * so is every other component of a side ({@link Sided#alsoSeen}), so the order the copies are met
 * in, and how the conjunctions were bracketed, cannot reach the answer.
 *
 * @param confinement the whole reading worked out — what every position may hold and where its
 *                    order stops — with what could not be built beside it
 * @param outcomes    the fate of both branches of every written choice, by its id
 */
record Settlement(Confinement.Worked<FactSubject> confinement,
                  Map<ChoiceId, OfAChoice> outcomes) {

    /** The values worked out, for a reader that asks what a position came to. */
    Realized<FactSubject> made() {
        return confinement.made();
    }


    /**
     * Both branches of one written choice, each aggregated over its occurrences, beside what the
     * width of the choice depends on and what each alternative holds down.
     *
     * <p>Made together and never apart. A fate says whether anybody can be in a branch; the
     * dependency says which positions would be narrower without one. A reader deciding what an
     * alternative nothing could read left open needs both about the same two branches, and made in
     * two places they would be two answers to one question.
     *
     * <p>{@link WhatTheAlternativesLeave} is beside the width rather than inside it, and the two are
     * siblings of one walk. What is asked of both alternatives at once is the width; what is asked
     * of one of them about the values it leaves is the other, and a reader of the second reaching
     * for it through the first would be taking a relation for an attribute. They are worked out
     * together because they read the same two branches once, and that is the whole of what they
     * share.
     */
    record OfAChoice(Sided left, Sided right, WidthDependency width,
                     WhatTheAlternativesLeave narrowed) {

        /**
         * What one occurrence of a choice between two branches came to, fates and all.
         *
         * <p><b>Where whether there is a choice at this copy is decided, and the one place it
         * is.</b> An occurrence one alternative of which nobody can be in is not a choice there:
         * what is left of it is the branch beside the dead one, and a rule written inside a branch
         * nobody can be in constrains nobody. Every fact held here about the alternatives turns on
         * that one question, so all of them are answered the same way at such a copy — said once
         * here rather than once per fact, since two statements of one rule are free to disagree and
         * a reader of either cannot tell which it was given.
         *
         * <p>Which matters because a branch is dead only where nobody can be in it anywhere. A copy
         * that is not a choice would otherwise put a position into what the branch holds down, and
         * the aggregate over the copies would take back what a copy that is a choice had shown.
         */
        static OfAChoice of(Sided left, StatedTogether.Said one,
                            Sided right, StatedTogether.Said other) {
            if (!souther.compiler.values.Emptiness.Alternatives
                    .from(SidesShownEmpty.of(left.emptiness(), right.emptiness())).bothStand()) {
                return new OfAChoice(left, right, WidthDependency.none(),
                        WhatTheAlternativesLeave.nothing());
            }
            return new OfAChoice(left, right,
                    WidthDependency.of(one.confinement(), other.confinement()),
                    WhatTheAlternativesLeave.of(one.confinement(), other.confinement()));
        }

        /** This choice with one more occurrence of it taken in, side by side. */
        OfAChoice alsoSeen(OfAChoice occurrence) {
            return new OfAChoice(left.alsoSeen(occurrence.left()),
                    right.alsoSeen(occurrence.right()), width.alsoSeen(occurrence.width()),
                    narrowed.alsoSeen(occurrence.narrowed()));
        }
    }

    /**
     * Which positions a choice may be as wide as it is at because of one of its alternatives, as
     * each reading measures it.
     *
     * <p>Two readings and one event. A choice offering an alternative nothing could read is one
     * thing that happened to one written clause; what it left open is a different set in each of
     * the two languages, because each of them measures what a branch leaves in its own terms and
     * has no word for what the other holds. So this is made once, where the branches are, and the
     * two halves are told apart by their type rather than by which caller happens to be holding
     * one.
     *
     * <p>Kept together for the same reason the fates are: a reader deciding what an unread
     * alternative left open needs both about the same two branches, and made in two places they
     * would be two answers to one question.
     *
     * <p><b>Nothing where either branch admits nothing here.</b> There is no choice at such an
     * occurrence — what it leaves is the branch beside the dead one — so its width rests on neither
     * alternative in either language. Another occurrence of the same written choice may still be
     * one both branches stand at, and what that one's width may rest on is joined in beside this
     * ({@link #alsoSeen}): a branch is dead for the author only where nobody can be in it anywhere,
     * and that is not this occurrence's to say.
     *
     * @param byValues what the reading of values could not show the alternatives preserve
     * @param byOrder  the same asked of where the orders stop, which is a different question about
     *                 the same two branches
     */
    record WidthDependency(Width<ReadingLanguage.Values> byValues,
                           Width<ReadingLanguage.Order> byOrder) {

        /** A choice shown to leave what it leaves without either of its alternatives. */
        static WidthDependency none() {
            return new WidthDependency(Width.none(), Width.none());
        }

        /**
         * What one occurrence of a choice between these two branches may be as wide as it is
         * because of, read off the descriptions and building nothing.
         *
         * <p>Each reading is asked about its own and neither is asked about the other's. Handed the
         * whole branch rather than one half of it, since what a reading leaves is the branch's and
         * a caller picking a half out is the place the two would come apart.
         *
         * <p>Whether there is a choice at this occurrence at all is not asked here. That is one
         * question about the two branches which every fact about them turns on, so it is decided
         * once, where the fates are ({@link OfAChoice#of}).
         */
        static WidthDependency of(Confinement.Planned<FactSubject> one,
                                  Confinement.Planned<FactSubject> other) {
            // The carriers of one side, which are the declaration's and so are both sides'. What a
            // position is ordered on is a fact about the vocabulary and not about the branch, and a
            // choice whose alternatives disagreed about it would be one this compiler built.
            return new WidthDependency(Width.ofValues(one.values(), other.values()),
                    Width.ofOrder(one.ordered(), other.ordered(), one.carriers()));
        }

        /** The width of one more occurrence of the same choice, taken in beside this. */
        WidthDependency alsoSeen(WidthDependency occurrence) {
            return new WidthDependency(byValues.alsoSeen(occurrence.byValues()),
                    byOrder.alsoSeen(occurrence.byOrder()));
        }
    }

    /**
     * One reading's half of that.
     *
     * <p>A relation between two branches and not an attribute of one, which is why it is here
     * rather than in {@link Sided}: what may rest on the left is read off what the <em>right</em>
     * branch leaves, and the other way round.
     *
     * <p><b>What a member and a non-member each say, which is not the same strength.</b> Write
     * {@code D} for the positions where the choice without a branch truly leaves less than the
     * choice with it. A position left out is one where the two are the same and dropping the branch
     * changes nothing — that is proven. A position kept is one where nothing here proved them the
     * same, which is weaker than their differing: two descriptions of one answer written
     * differently are kept apart. So {@code D} is contained in what is held and is not what is
     * held, and a reader may take a non-member as a fact and a member only as a question nobody
     * settled. Read the other way, a position no alternative was really answerable for would be
     * published as one an author has to look at.
     *
     * <p>Which is what lets the comparison be an equality of normalised descriptions and cost
     * nothing. A choice leaves whatever either of its branches leaves, so what it leaves without
     * one of them is contained in what it leaves with both, and equal descriptions say the same
     * thing — the proof runs in the direction a non-member is read in, and nothing is built to
     * decide the other.
     *
     * <p><b>An occurrence at a time, and a union over them.</b> The same written choice stands
     * wherever a conjunction beside it was distributed in, and a branch dropped is dropped at every
     * one of them — so a position any occurrence's width may rest on is one the whole answer's
     * may. Union is associative, commutative and idempotent, which is what lets the order the
     * copies are met in stay out of the answer, and it is the second reason a member is a question
     * rather than a fact: an occurrence's own widening can be covered by what another occurrence
     * leaves.
     *
     * <p><b>Which alternative went unread is not here.</b> That is what the account of the rules
     * says, and turning the two into what the choice left open is done where both are in hand
     * ({@code StatedByClauses#openedBy}). Answered here, the alternative would have to arrive as a
     * word for a side rather than as an account, and a word for a side says nothing about which
     * reading it came from.
     *
     * @param <L>            which reading measured this ({@link ReadingLanguage})
     * @param mayRestOnLeft  the positions where the choice without its left alternative was not
     *                       shown to leave what the choice with it leaves
     * @param mayRestOnRight the same for the right
     */
    record Width<L extends ReadingLanguage>(Set<FactSubject> mayRestOnLeft,
                                            Set<FactSubject> mayRestOnRight) {

        Width {
            mayRestOnLeft = Set.copyOf(mayRestOnLeft);
            mayRestOnRight = Set.copyOf(mayRestOnRight);
        }

        /** A choice this reading showed it leaves what it leaves without either alternative. */
        static <L extends ReadingLanguage> Width<L> none() {
            return new Width<>(Set.of(), Set.of());
        }

        /**
         * What the reading of values could not show the alternatives preserve.
         *
         * <p>Over the positions either of them narrowed, since a position neither did is one both
         * of them leave at every value and so is the choice, with or without either.
         */
        static Width<ReadingLanguage.Values> ofValues(PlannedValues<FactSubject> one,
                                                      PlannedValues<FactSubject> other) {
            Set<FactSubject> narrowed = new LinkedHashSet<>(one.adoptedAt());
            narrowed.addAll(other.adoptedAt());
            // Compared as the descriptions they are, which is what this language has: two plans
            // naming one set of values are told apart, and the contract says so — a position kept
            // is one nobody settled and never one shown to differ.
            return comparing(narrowed, one::at, other::at,
                    (here, there) -> AdmittedPlan.joining(List.of(here, there)),
                    Object::equals);
        }

        /**
         * The same asked of where the orders stop.
         *
         * <p>Complete as well as sound, which the reading of values is not. What each side is
         * compared as is the values it leaves the position on that position's own order
         * ({@link OrderedIntervals#valuesAt}), and two ranges leaving one set of values come back
         * as one ({@link OrderedInterval#sameValuesAs}) — so a position left out is exactly a
         * position the choice stops where it would without the branch. The contract this is
         * published under ({@link Opening}) is still the weaker one, since what a reader may act on
         * has to hold of every language that answers.
         *
         * <p><b>Which is a claim about the interpretation and not about the writing.</b> Compared
         * as the pairs of ends they are written as, a choice whose alternatives reach both ends of
         * a carrier leaves an {@code Int} at {@code [MIN..MAX]} while the branch beside it leaves
         * it at no ends at all, and the two say the same thing — so the completeness above holds of
         * {@code γ} of each side and of nothing this could read off the ends alone.
         *
         * <p>A position at a time, as the values are. What a choice comes to over a whole reading
         * is more than the ranges and is composed where both languages are held
         * ({@link Confinement.Planned}); what is asked here is one position of two branches whose
         * fates are settled and handed in.
         */
        static Width<ReadingLanguage.Order> ofOrder(OrderedIntervals<FactSubject> one,
                                                    OrderedIntervals<FactSubject> other,
                                                    Map<FactSubject, Carrier> carriers) {
            Set<FactSubject> bounded = new LinkedHashSet<>(one.boundedAt());
            bounded.addAll(other.boundedAt());
            return comparing(bounded,
                    position -> one.valuesAt(position, carriers),
                    position -> other.valuesAt(position, carriers),
                    OrderedInterval::join, OrderedInterval::sameValuesAs);
        }

        /**
         * Both sides of one comparison, whatever a reading leaves a position.
         *
         * <p>Private, and the reading it is a width of is settled by whichever of the two above
         * called it — each of them takes the descriptions of one language and nothing else, so
         * there is no call here at which the two could be swapped for one another.
         *
         * <p>Handed how to tell two of them apart as well as how to join them, because that is the
         * half the two languages differ over. The ends have an equality of the values they leave
         * and the plans have only the one they are written with, and a comparison that reached for
         * {@code equals} would give the ends the plans' answer and quietly weaken the stronger of
         * the two contracts.
         */
        private static <L extends ReadingLanguage, T> Width<L> comparing(
                Set<FactSubject> narrowed, Function<FactSubject, T> left,
                Function<FactSubject, T> right, BinaryOperator<T> joining,
                BiPredicate<T, T> saying) {
            Set<FactSubject> mayRestOnLeft = new LinkedHashSet<>();
            Set<FactSubject> mayRestOnRight = new LinkedHashSet<>();
            for (FactSubject position : narrowed) {
                // Asked of each side once. What a reading leaves a position is worked out and not
                // looked up — a description is met across every alternative the reading holds — so
                // a comparison that asked again for what it already had would pay for the branches
                // twice over.
                T here = left.apply(position);
                T there = right.apply(position);
                T both = joining.apply(here, there);
                // Saying one thing is a proof that dropping the branch leaves the position where it
                // was. Not saying it is not a proof of anything, and the position is kept as one
                // nobody settled.
                if (!saying.test(there, both)) {
                    mayRestOnLeft.add(position);
                }
                if (!saying.test(here, both)) {
                    mayRestOnRight.add(position);
                }
            }
            return new Width<>(mayRestOnLeft, mayRestOnRight);
        }

        /** The width of one more occurrence of the same choice, taken in beside this. */
        Width<L> alsoSeen(Width<L> occurrence) {
            Set<FactSubject> left = new LinkedHashSet<>(mayRestOnLeft);
            left.addAll(occurrence.mayRestOnLeft());
            Set<FactSubject> right = new LinkedHashSet<>(mayRestOnRight);
            right.addAll(occurrence.mayRestOnRight());
            return new Width<>(left, right);
        }
    }

    /**
     * One branch's fate, over every occurrence taken in so far.
     *
     * <p>{@code standing} and {@code unbuilt} carry what probing the occurrences could not build,
     * for the one case the account needs it: a branch kept without being shown live is kept with
     * the reason nobody knows, or the account would call a position open where the truth is that
     * nothing looked. Read only where the aggregate stays {@link souther.compiler.values.Emptiness#UNDECIDED}; a branch
     * shown live somewhere needs no excuse, and a branch dead everywhere takes its reasons with it.
     *
     * <p><b>Two halves and not one map, because they are routed and not distributed alike.</b> What
     * the answer at a position was short of holds of every rule whose question waited on that
     * answer, so it goes to each of them. What a machine somebody's pattern asked for was refused
     * for holds of the pattern that asked and of nothing else — every rule reaching a position pays
     * into one allowance, so a place cannot say which of them asked, and a half that travelled as a
     * position's reasons was read back as every rule's.
     *
     * <p>So there is no map here holding both. A position's own account is that projection
     * ({@link #asPositionStanding()}) and is made where a position is being described; nothing
     * builds an account of a rule out of it, which is the direction the loss runs in.
     */
    record Sided(Confinement.Admission<FactSubject> shown,
                 Map<FactSubject, List<UnreadReason>> answerStanding,
                 Set<souther.compiler.values.Unbuilt.RuleShortfall<FactSubject>> ruleShortfalls,
                 Set<FactSubject> unbuilt) {

        /** Whether anything satisfies this branch, as far as its occurrences settled it. */
        souther.compiler.values.Emptiness emptiness() {
            return shown.emptiness();
        }

        /** A branch nobody has probed yet, which everything joins onto. */
        static Sided settledAs(Confinement.Admission<FactSubject> shown) {
            return new Sided(shown, Map.of(), Set.of(), Set.of());
        }

        /**
         * What the position was left with, which is both halves said of the place.
         *
         * <p>The one direction that is allowed. A position is as wide as it is because a machine
         * was refused and because an answer was not built, and a reader of the place is owed both —
         * what is dropped on the way is which written thing asked, which is a fact about a rule and
         * not about the place. Read the other way, this is where the account of a rule came to be
         * built out of a place's reasons.
         */
        souther.compiler.values.Standing<FactSubject> asPositionStanding() {
            // Composed and handed on, never read back. What is made here is the place's account,
            // which the values interpret ({@code AdmissibleValues.whyUnread}); nothing takes it
            // apart again, and a rule's account is not built out of it — which is the direction
            // this record exists to refuse, and is now refused by the type rather than by saying so.
            //
            // Said in the vocabulary's declared order, as a joined side is. The two halves are put
            // together here and each arrived in its own — the machines in the order the copies were
            // met — so a place holding one of each would otherwise come out in the order this
            // method appended them, which is the order of the copies reaching the answer.
            Map<FactSubject, SortedSet<UnreadReason>> both = new LinkedHashMap<>();
            answerStanding.forEach((position, why) ->
                    both.computeIfAbsent(position, _ -> new TreeSet<>()).addAll(why));
            ruleShortfalls.forEach(each ->
                    both.computeIfAbsent(each.at(), _ -> new TreeSet<>()).add(each.why()));
            souther.compiler.values.Standing<FactSubject> out =
                    souther.compiler.values.Standing.nothing();
            for (Map.Entry<FactSubject, SortedSet<UnreadReason>> each : both.entrySet()) {
                for (UnreadReason why : each.getValue()) {
                    out = out.alsoAt(Set.of(each.getKey()), why);
                }
            }
            return out;
        }

        /**
         * The same branch with one more occurrence of it taken in.
         *
         * <p>Associative, commutative and idempotent in every component, which is what lets the
         * class doc promise that the order the copies are met in cannot reach the answer:
         * {@link souther.compiler.values.Emptiness#joined} is, a set union is, and the reasons are joined as a set and then
         * said in the vocabulary's declared order — kept in the order the occurrences were met,
         * they would be said in a neighbouring clause's order.
         */
        Sided alsoSeen(Sided other) {
            Set<FactSubject> gaveUp = new java.util.LinkedHashSet<>(unbuilt);
            gaveUp.addAll(other.unbuilt());
            Map<FactSubject, List<UnreadReason>> why = new java.util.LinkedHashMap<>();
            ReadByClauses.alsoSaying(answerStanding, other.answerStanding())
                    .forEach((position, reasons) ->
                            why.put(position, reasons.stream().sorted().toList()));
            // And the other half as a union, which is what this half is: a shortfall is one fact
            // about one pattern at one position, so two copies of one are one and two are two.
            // Held in the order the copies were met, a branch's aggregate would say which copy was
            // settled first, and the class above promises that it cannot.
            Set<souther.compiler.values.Unbuilt.RuleShortfall<FactSubject>> asked =
                    new java.util.LinkedHashSet<>(ruleShortfalls);
            asked.addAll(other.ruleShortfalls());
            // And what showed the branch empty, where both occurrences of it are. Where they were
            // shown by different things, or refused at different positions, neither speaks for the
            // branch.
            //
            // Asked of the join and not of the two sides. These are two occurrences of one written
            // branch rather than two alternatives, so there is no side here to be named: a branch
            // anybody can be in anywhere it stands is one nothing showed empty, and a proof survives
            // only where every occurrence was shown empty.
            souther.compiler.values.Emptiness said = emptiness().joined(other.emptiness());
            Confinement.Admission<FactSubject> both = said.isEmpty()
                    ? Confinement.Admission.bothShown(shown, other.shown)
                    : Confinement.Admission.left(said);
            return new Sided(both, why, java.util.Collections.unmodifiableSet(asked), gaveUp);
        }
    }
}
