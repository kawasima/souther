package souther.compiler.partition;

import org.junit.jupiter.api.Test;
import souther.compiler.check.PathReachability;
import souther.compiler.core.Core;
import souther.compiler.coverage.ControlPointId;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.TypeSymbol;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A case is taken out of what a behavior owes for a proof about a condition, and never for one about
 * a {@code match} arm.
 *
 * <p>Which cases of a sum can arrive at an arm is a fact about the position matched on, and what a
 * body can answer with is a fact about the body. The two met here because both are proofs on the one
 * reading: an arm of a {@code match} the rules refuse is proven unreachable like any other arm, and
 * taken as a refusal it drops a case of the <em>output</em> for something established about the
 * <em>input</em>.
 *
 * <p>Which construct an arm belongs to is asked of what wrote it and not of the tree it was lowered
 * to. A {@code guard} is an {@code if} by then, and a comprehension's condition is a fork nobody
 * wrote as one.
 */
class AProofAboutAMatchArmSaysNothingAboutWhatIsAnsweredWithTest {

    /**
     * {@code Off} is a case the rules refuse at {@code f}, and {@code No} is answered with nowhere
     * else.
     */
    private static final String REFUSED_CASE = """
            module example.refused

            data On
            data Off
            data Pending
            data Flag = On | Off | Pending
            data Active = Flag invariant value /= Off
            data Yes
            data No

            behavior pick : (f: Active) -> Yes | No

            let pick (f) = match f.value with
                | On      -> Yes
                | Pending -> Yes
                | Off     -> No

            example pick
                | "on" : (Active(On)) -> Yes
            """;

    /**
     * The same shape written as a condition: nothing at or above the cap reaches the arm that
     * answers {@code No}.
     */
    private static final String REFUSED_CONDITION = """
            module example.capped

            data Count = Int
                invariant lower = value >= 0
                invariant cap = value <= 10
            data Yes
            data No

            behavior pick : (c: Count) -> Yes | No

            let pick (c) =
                if c.value >= 50
                    then No
                    else Yes

            example pick
                | "small" : (Count(1)) -> Yes
            """;

    @Test
    void aRefusedMatchArmLeavesTheCaseItAnswersWithOwed() {
        assertEquals(souther.compiler.reach.Reachability.Unreachable.class,
                armThatIsRefused(REFUSED_CASE, SourceConstruct.MATCH).getClass(),
                "the arm for the case the rules refuse is proven unreachable");
        assertEquals(Set.of("example.refused.Yes", "example.refused.No"),
                declaredOutputCasesOf(REFUSED_CASE).stream().map(TypeSymbol::toString)
                        .collect(Collectors.toSet()),
                "and the case answered under it is still one this behavior owes a row for");
    }

    @Test
    void anArmNothingReachesTakesTheCaseOnlyItAnswersWith() {
        assertEquals(souther.compiler.reach.Reachability.Unreachable.class,
                armThatIsRefused(REFUSED_CONDITION, SourceConstruct.IF).getClass(),
                "the arm the condition cannot come out into is proven unreachable");
        assertEquals(List.of("example.capped.Yes"), declaredOutputCasesOf(REFUSED_CONDITION).stream()
                        .map(TypeSymbol::toString).toList(),
                "and nobody can write a row for what only it answers with");
    }

    /**
     * The proof about an arm is read whether or not a run through the arm could be recorded.
     *
     * <p>The same compile as above with one thing changed: the arm nothing reaches carries no probe.
     * Everything else — the fork, the places, the proofs, the body — is what the compiler made, so
     * what is under test is the walk's answer to instrumentation being absent and nothing else.
     *
     * <p>Built rather than found, because no model produces this today: an arm loses its probe when
     * it answers no value or stands where nothing does, and nothing under such an arm is what the
     * behavior answers with. That is a fact about the numbering as it stands and not about what this
     * walk owes — asked by probe, it would go back to answering "nothing is recorded here" to a
     * question about what arrives.
     */
    @Test
    void aProofIsReadAtAnArmNoRunCouldBeRecordedIn() {
        Compilation compilation = compiled(REFUSED_CONDITION);
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        Core body = checked.behaviorBodies().get("pick");
        PathReachability.Answers arrives = compilation.db()
                .ask(new Adequacy.PathReached(module)).value().get("pick");
        Set<TypeSymbol> answersWith = casesNamedIn(body);

        ControlPointId.ArmOccurrence refused = arrives.found().entrySet().stream()
                .filter(each -> each.getValue() instanceof souther.compiler.reach.Reachability.Unreachable)
                .map(Map.Entry::getKey)
                .filter(ControlPointId.ArmOccurrence.class::isInstance)
                .map(ControlPointId.ArmOccurrence.class::cast)
                .findFirst().orElseThrow();
        assertTrue(refused.probe().isPresent(), "the compiler numbered the arm it proved dead");

        ControlPointId.ArmOccurrence silent = new ControlPointId.ArmOccurrence(
                refused.controlId(), java.util.Optional.empty(), refused.at(), refused.origin());

        assertEquals(ProducedCases.of(body, checked.plan(), arrives, answersWith),
                ProducedCases.of(body, planWith(checked.plan(), refused, silent),
                        arrivalsWith(arrives, refused, silent), answersWith),
                "what the body can answer with does not turn on where a run could be recorded");
        assertEquals(List.of("example.capped.Yes"),
                ProducedCases.of(body, planWith(checked.plan(), refused, silent),
                                arrivalsWith(arrives, refused, silent), answersWith).stream()
                        .map(TypeSymbol::toString).toList(),
                "and the case only the dead arm answers with is still taken away");
    }

    /**
     * {@code plan} with {@code now} standing where {@code was} stood, and nothing else moved.
     *
     * <p>Matched by what an occurrence is and not by which object it is: a plan derived a second
     * time from one module holds equal places rather than the same ones, and the reading this is
     * held against was made against a derivation of its own.
     */
    private static CoverageSites.Plan planWith(CoverageSites.Plan plan,
                                               ControlPointId.ArmOccurrence was,
                                               ControlPointId.ArmOccurrence now) {
        IdentityHashMap<Core, ControlPointId.ArmOccurrence[]> arms = new IdentityHashMap<>();
        plan.armsByNode().forEach((node, held) -> {
            ControlPointId.ArmOccurrence[] out = held.clone();
            for (int at = 0; at < out.length; at++) {
                if (out[at].equals(was)) {
                    out[at] = now;
                }
            }
            arms.put(node, out);
        });
        return new CoverageSites.Plan(plan.sites(), plan.guards(), plan.byNode(),
                plan.byComparison(), arms, plan.controlByComparison(), plan.mayRepeat(),
                plan.forkByNode(), plan.comparisons(), plan.numbering());
    }

    /** The same answers, filed under {@code now} where they were filed under {@code was}. */
    private static PathReachability.Answers arrivalsWith(PathReachability.Answers answers,
                                                         ControlPointId.ArmOccurrence was,
                                                         ControlPointId.ArmOccurrence now) {
        Map<ControlPointId, souther.compiler.reach.Reachability> found = new LinkedHashMap<>();
        answers.found().forEach((where, said) -> found.put(where.equals(was) ? now : where, said));
        return new PathReachability.Answers(found, answers.arriving());
    }

    /**
     * Every case named anywhere in {@code body}, which for this fixture is the pair its signature
     * declares.
     *
     * <p>Wider than what a measure is handed, and enough here: what the two calls below are
     * compared on is the one set, and the body of this model builds nothing that is not one of its
     * output's cases. A body that did would want the declared cases, which are the signature's to
     * say and not this walk's.
     */
    private static Set<TypeSymbol> casesNamedIn(Core body) {
        Set<TypeSymbol> out = new LinkedHashSet<>();
        gather(body, out);
        return out;
    }

    private static void gather(Core e, Set<TypeSymbol> out) {
        if (e == null) {
            return;
        }
        switch (e) {
            case Core.UnitValue value -> out.add(value.data());
            case Core.Construct built -> out.add(built.typeName());
            default -> { }
        }
        Core.forEachChild(e, child -> gather(child, out));
    }

    /** What the reading says about the one arm of {@code kind} it proves nothing arrives at. */
    private static souther.compiler.reach.Reachability armThatIsRefused(String model, SourceConstruct kind) {
        Compilation compilation = compiled(model);
        Map<String, PathReachability.Answers> answers = compilation.db()
                .ask(new Adequacy.PathReached(compilation.modules().get(0))).value();
        return answers.get("pick").found().entrySet().stream()
                .filter(each -> each.getKey() instanceof ControlPointId.ArmOccurrence arm
                        && arm.origin().kind() == kind)
                .map(Map.Entry::getValue)
                .filter(souther.compiler.reach.Reachability.Unreachable.class::isInstance)
                .findFirst().orElseThrow(() ->
                        new AssertionError("no arm of a " + kind + " is proven unreachable"));
    }

    /** The cases of the output this behavior is measured against. */
    private static Set<TypeSymbol> declaredOutputCasesOf(String model) {
        AdequacyReport report = AdequacyReport.of(compiled(model));
        return report.modules().get(0).behaviors().stream()
                .filter(behavior -> behavior.name().equals("pick"))
                .findFirst().orElseThrow()
                .evidence().signature().output().declared();
    }

    private static Compilation compiled(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.answerEverything();
        return compilation;
    }
}
