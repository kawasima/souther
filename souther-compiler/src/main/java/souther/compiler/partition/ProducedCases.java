package souther.compiler.partition;

import souther.compiler.core.Core;
import souther.compiler.check.PathReachability;
import souther.compiler.coverage.ControlPointId;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The cases of a behavior's output that no row could carry, because everything answering with them is
 * behind an arm nothing reaches.
 *
 * <p>Taken away and not counted up. A case a body has no producer for stays owed: that a signature
 * says {@code A | B} and an implementation never answers {@code B} is a gap between the two, and it is
 * the kind of gap a coverage measure is for. What is not a gap is a case something does answer with,
 * at a place the model's own rules prove nothing reaches — nobody can write that row, and asking is
 * asking for work nobody can do.
 *
 * <pre>
 * a reachable producer, or one this cannot read   keep
 * producers, and every one of them unreachable    take away
 * no producer at all                              keep
 * </pre>
 *
 * <p>The middle line is the only one that takes anything, and it is the one #479 is about. The first
 * covers everything unreadable — a call, a value bound elsewhere, a body that is a function — so a
 * helper that might answer anything keeps every case owed, which is the safe direction: a case left in
 * is a case the report asks for, and an author reading a gap they cannot fill has at least been told
 * something true about their model.
 *
 * <p>Only a condition's arm takes a producer away. A {@code match} arm is left alone here: which
 * cases of a sum can arrive is a different question, asked of the reading of the position matched
 * on, and an arm of one taken as a refusal would drop a case of the output for a fact about the
 * input. Which construct an arm is one of is the origin's answer and not the lowered tree's — an
 * {@code if} and a {@code guard} are one node by the time this walks.
 */
public final class ProducedCases {

    /**
     * The cases {@code body} can produce, which is the declared ones less those a guard's arm
     * proves unreachable.
     *
     * @param declared what the output type's cases are, which is what this answers where nothing is
     *                 taken away
     */
    public static Set<TypeSymbol> of(Core body, CoverageSites.Plan plan, PathReachability.Answers arrives,
                                   Set<TypeSymbol> declared) {
        // Nothing proven is nothing to take away: what this returns is `declared` less the cases whose
        // every producer is behind a proven arm, and with no such arm there are none. Skipped rather
        // than walked to the same answer.
        if (body == null || declared.isEmpty() || arrives.provesNothingUnreached()) {
            return declared;
        }
        Seen seen = new Seen();
        walk(body, List.of(), plan, arrives, declared, seen);
        if (seen.anythingUnreadable) {
            return declared;   // something reachable answers with what this cannot name
        }
        Set<TypeSymbol> out = new LinkedHashSet<>(declared);
        seen.behindAProvenArm.stream().filter(each -> !seen.reachable.contains(each)).toList()
                .forEach(out::remove);
        // The order the cases were declared in, which is the order they are named in. `Set.copyOf`
        // keeps the values and not the order, so a case taken out here would have reordered the ones
        // left — and what a report lists is read against the declaration it came from.
        return java.util.Collections.unmodifiableSet(out);
    }

    /** Where a case was answered with, which is what decides whether it can be answered at all. */
    private static final class Seen {

        /** Answered with somewhere no proof rules out. */
        private final Set<TypeSymbol> reachable = new LinkedHashSet<>();
        /** Answered with behind an arm nothing reaches, which is only worth acting on where the case
         * is answered with nowhere else. */
        private final Set<TypeSymbol> behindAProvenArm = new LinkedHashSet<>();
        /** A producer that could answer with anything, at a place something reaches. */
        private boolean anythingUnreadable;
    }

    /**
     * One tail position, and the arms it sits under.
     *
     * <p>Tail positions only. A construction evaluated on the way to somewhere else — an argument, the
     * value a {@code let} binds — is not what the behavior answers with, and counting it would keep a
     * case owed because the body happened to build one on its way past.
     */
    private static void walk(Core e, List<ControlPointId.ArmOccurrence> under,
                             CoverageSites.Plan plan,
                             PathReachability.Answers arrives, Set<TypeSymbol> declared, Seen seen) {
        if (seen.anythingUnreadable) {
            return;   // nothing further can be taken away
        }
        switch (e) {
            case Core.Unreachable _ -> { }   // answers nothing, so it produces nothing
            case Core.LetIn li -> walk(li.body(), under, plan, arrives, declared, seen);
            case Core.If iff -> {
                ControlPointId.ArmOccurrence[] arms = plan.armsOf(iff);
                walk(iff.then(), beneath(under, arms, 0), plan, arrives, declared, seen);
                walk(iff.els(), beneath(under, arms, 1), plan, arrives, declared, seen);
            }
            case Core.Match m -> {
                ControlPointId.ArmOccurrence[] arms = plan.armsOf(m);
                for (int i = 0; i < m.cases().size(); i++) {
                    walk(m.cases().get(i).body(), beneath(under, arms, i), plan, arrives, declared,
                            seen);
                }
            }
            case Core.IfConstructed ic -> {
                ControlPointId.ArmOccurrence[] arms = plan.armsOf(ic);
                walk(ic.then(), beneath(under, arms, 0), plan, arrives, declared, seen);
                for (int i = 0; i < ic.els().size(); i++) {
                    walk(ic.els().get(i).body(), beneath(under, arms, i + 1), plan, arrives,
                            declared, seen);
                }
            }
            case Core.UnitValue u -> produce(u.data(), under, arrives, declared, seen);
            case Core.Construct nd -> produce(nd.typeName(), under, arrives, declared, seen);
            // Everything else answers something this cannot name: a call, a name read from a binding,
            // a function value. The top of the lattice, which keeps every case owed.
            case null, default -> produce(null, under, arrives, declared, seen);
        }
    }

    /** Where one producer puts the case it answers with. */
    private static void produce(TypeSymbol built, List<ControlPointId.ArmOccurrence> under,
                                PathReachability.Answers arrives,
                                Set<TypeSymbol> declared, Seen seen) {
        boolean proven = under.stream().anyMatch(arm ->
                arrives.at(arm) instanceof souther.compiler.reach.Reachability.Unreachable);
        if (built == null || !declared.contains(built)) {
            // Not a case this can name. Reachable, it could be any of them and nothing is taken away;
            // behind a proven arm it answers nothing and says nothing about any case either.
            seen.anythingUnreadable |= !proven;
            return;
        }
        if (proven) {
            seen.behindAProvenArm.add(built);
        } else {
            seen.reachable.add(built);
        }
    }

    /**
     * The arms of an inner fork that refuse a producer under them, added to the ones already above.
     *
     * <p>Whether a run can be recorded in the arm is not among the questions. That is what an arm
     * is instrumented for, and what this asks is whether anything arrives — a place with no probe
     * is a place all the same, and the reading answers about it like any other.
     */
    private static List<ControlPointId.ArmOccurrence> beneath(
            List<ControlPointId.ArmOccurrence> under,
            ControlPointId.ArmOccurrence[] arms, int index) {
        if (arms == null || index >= arms.length || arms[index] == null
                || !takesAProducerAway(arms[index])) {
            return under;
        }
        List<ControlPointId.ArmOccurrence> out = new ArrayList<>(under);
        out.add(arms[index]);
        return List.copyOf(out);
    }

    /**
     * Whether a proof about this arm says anything about what the body can answer with.
     *
     * <p>Asked of the construct the author wrote and not of the shape it was lowered to, which is
     * what {@link SourceConstructOrigin} carries the construct for. A {@code match} arm is not one of
     * these: which cases of a sum can arrive is asked of the reading of the input, and an arm of
     * one taken to refuse a producer would take a case away for a fact about the scrutinee.
     *
     * <p>An arm no source wrote takes nothing away either, and an arm carrying nothing that says
     * what wrote it is one of those. What this walk answers about is what the author's body can
     * answer with, and a fork nothing wrote is not their construct. Refused rather than answered
     * for, this would hold a place to more than what makes one: an occurrence names an origin where
     * it has one, and a reader wanting a construct is a reader that can be told there is none.
     */
    private static boolean takesAProducerAway(ControlPointId.ArmOccurrence arm) {
        SourceConstructOrigin origin = arm.origin();
        if (origin == null) {
            return false;   // nothing here says what wrote it, which is not a construct either
        }
        return switch (origin.kind()) {
            case IF, GUARD, COMPREHENSION -> true;
            case MATCH, NOT_WRITTEN -> false;
            // Not an arm of anything. Every arm is one of a fork, and no fork is written as a
            // comparison or as an application — so a value arriving here as one was built by
            // nothing that makes arms, and either answer about it would be an answer about the
            // author's body made out of that.
            case BINARY, CALL -> throw new IllegalStateException(
                    "an arm of " + origin.kind() + " at " + arm.at()
                            + "; an arm is one of a fork the author wrote");
        };
    }

    private ProducedCases() {}
}
