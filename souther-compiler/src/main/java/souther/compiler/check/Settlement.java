package souther.compiler.check;

import souther.compiler.values.Realized;
import souther.compiler.values.UnreadReason;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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

    /** Both branches of one written choice, each aggregated over its occurrences. */
    record OfAChoice(Sided left, Sided right) {

        /** This choice with one more occurrence of it taken in, side by side. */
        OfAChoice alsoSeen(OfAChoice occurrence) {
            return new OfAChoice(left.alsoSeen(occurrence.left()),
                    right.alsoSeen(occurrence.right()));
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
            // branch — which is the same rule a choice of two dead branches is under.
            souther.compiler.values.Emptiness said = emptiness().joined(other.emptiness());
            Confinement.Admission<FactSubject> both = said == souther.compiler.values.Emptiness.EMPTY
                    ? Confinement.Admission.bothShown(shown, other.shown)
                    : Confinement.Admission.left(said);
            return new Sided(both, why, java.util.Collections.unmodifiableSet(asked), gaveUp);
        }
    }
}
