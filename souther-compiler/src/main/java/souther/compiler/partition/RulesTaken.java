package souther.compiler.partition;

import souther.compiler.core.Core;
import souther.compiler.coverage.AlignedObservation;
import souther.compiler.coverage.ArmEmissionIndex;
import souther.compiler.coverage.ComparisonEmissionIndex;
import souther.compiler.coverage.ControlClaim;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.coverage.CoverageSites;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which rule of a body's decision each run took.
 *
 * <p>What discharges a decision rule is a row whose evaluation takes that path, so this is where a
 * run and a rule meet. The rule is read off the tree the language's operations stand in and the run
 * is recorded where they are expanded; what the two agree about is the construct of the model, and
 * the plan says where the emitter numbered each one.
 *
 * <p><b>A rule is taken where every condition on its path was seen coming out the way the rule
 * says.</b> Not where some of them were: the conditions of a path are what makes it that path, and
 * a run that matched all but one took a different one.
 *
 * <p><b>And a rule no run can be recognised at is not refuted by any run.</b> A path carrying a
 * condition with no construct of the model to be seen at, or one the emitter numbered no site for,
 * is one this compiler cannot tell a run took — so it is set aside rather than answered no, which
 * would report the rules a body has as rules its rows never reach.
 */
public final class RulesTaken {

    /**
     * Which rule a run took, or that this compiler could not tell.
     *
     * <p>Three answers rather than two, because what a reader does with them differs. A run whose
     * rule is known is evidence that something stands there; a run whose rule could not be told is
     * this compiler falling short and says nothing about the model; and a run matching no rule at
     * all is the reading and the run disagreeing about the body, which is worth knowing.
     */
    public sealed interface WhichRule {

        /** The rule this run took. */
        record TookThis(DecisionRule rule) implements WhichRule {}

        /** No rule this reading can recognise a run at matched, and why that is not a fact about
         *  the model. */
        record CouldNotTell(Why why) implements WhichRule {}

        /** Why a run's rule could not be told. */
        enum Why {
            /** Every rule of the body carries a condition no run through it is recorded at. */
            NO_RULE_IS_RECOGNISABLE,
            /** The rules that can be recognised were each missing something the run did not do. */
            NO_RECOGNISABLE_RULE_MATCHES,
            /** More than one rule matched, which one run cannot have done. */
            MORE_THAN_ONE_RULE_MATCHES
        }
    }

    /**
     * One rule and what a run through it has to be seen doing.
     *
     * <p>Each condition of the path is one thing to have been seen, and a condition materialised
     * more than once in the tree that runs is seen at whichever of them the run passed — a helper
     * carrying a fork is expanded per call site, and a row through any of those copies went through
     * the arm the model states.
     */
    private record Recognised(DecisionRule rule, List<List<ControlClaim>> conditions) {

        boolean satisfiedBy(AlignedObservation seen) {
            for (List<ControlClaim> alternatives : conditions) {
                boolean any = false;
                for (ControlClaim each : alternatives) {
                    any |= each.satisfiedBy(seen);
                }
                if (!any) {
                    return false;
                }
            }
            return true;
        }
    }

    private final List<Recognised> recognisable;

    private RulesTaken(List<Recognised> recognisable) {
        this.recognisable = recognisable;
    }

    /**
     * The rules of {@code read}, against the tree that runs and the plan that numbered it.
     *
     * <p>Both trees are asked for because the crossing is about the pair: the rules say which
     * constructs of the model they turn on, and the plan says which of those a run is recorded at.
     */
    public static RulesTaken of(DecisionReading read, Core emitted, CoverageSites.Plan plan) {
        ComparisonEmissionIndex comparisons = ComparisonEmissionIndex.ofBody(emitted, plan);
        ArmEmissionIndex arms = ArmEmissionIndex.ofBody(emitted, plan);
        List<Recognised> recognisable = new ArrayList<>();
        for (DecisionReading.Ruled ruled : read.found()) {
            List<List<ControlClaim>> conditions = new ArrayList<>();
            boolean everyOne = true;
            for (ShownBy each : ruled.shownBy()) {
                List<ControlClaim> alternatives = claimsFor(each, comparisons, arms, plan);
                everyOne &= !alternatives.isEmpty();
                conditions.add(alternatives);
            }
            if (everyOne) {
                recognisable.add(new Recognised(ruled.rule(), List.copyOf(conditions)));
            }
        }
        return new RulesTaken(List.copyOf(recognisable));
    }

    /**
     * Where a run through one condition of a path is recorded, one entry per materialisation the
     * emitter numbered.
     *
     * <p>Empty says this compiler cannot recognise a run through it, which is one answer over three
     * causes: the condition has no construct of the model, the emitted tree holds no materialisation
     * of that construct, and the plan numbered no site for the ones it holds. None of them is
     * anything about the model, so they arrive here as one.
     */
    private static List<ControlClaim> claimsFor(ShownBy shown,
                                                ComparisonEmissionIndex comparisons,
                                                ArmEmissionIndex arms,
                                                CoverageSites.Plan plan) {
        List<ControlClaim> out = new ArrayList<>();
        switch (shown) {
            case ShownBy.AtAComparison at -> {
                for (ComparisonEmissionIndex.EmittedComparison made
                        : comparisons.madeFor(at.comparison())) {
                    // Asked of the plan, which is the one maker of a place a comparison comes out
                    // one way: it takes the address for the comparison being asked about, so the
                    // two are one answer rather than a pair assembled here.
                    plan.outcomeOf(made.occurrence(), at.held())
                            .flatMap(ControlClaim::of).ifPresent(out::add);
                }
            }
            case ShownBy.AtAnArm at -> {
                for (ControlPlace.Arm arm : arms.madeFor(
                        new ArmEmissionIndex.ArmOfTheModel(at.fork(), at.part()))) {
                    ControlClaim.of(arm).ifPresent(out::add);
                }
            }
            case ShownBy.NothingIsRecorded _ -> { }
        }
        return List.copyOf(out);
    }

    /** Which rule {@code seen} took. */
    public WhichRule takenBy(AlignedObservation seen) {
        if (recognisable.isEmpty()) {
            return new WhichRule.CouldNotTell(WhichRule.Why.NO_RULE_IS_RECOGNISABLE);
        }
        DecisionRule took = null;
        for (Recognised each : recognisable) {
            if (!each.satisfiedBy(seen)) {
                continue;
            }
            if (took != null) {
                return new WhichRule.CouldNotTell(WhichRule.Why.MORE_THAN_ONE_RULE_MATCHES);
            }
            took = each.rule();
        }
        return took == null
                ? new WhichRule.CouldNotTell(WhichRule.Why.NO_RECOGNISABLE_RULE_MATCHES)
                : new WhichRule.TookThis(took);
    }

    /**
     * Which of the body's rules the runs {@code seen} took, and how many runs took each.
     *
     * <p>Over the runs and not over the rules: what a rule is owed is settled elsewhere, and this
     * says only what was seen. A rule nothing took is absent rather than present with nothing, for
     * the reason a reading answers the absence of a fact rather than a count of zero.
     */
    public Map<DecisionRule, List<AlignedObservation>> takenBy(List<AlignedObservation> seen) {
        Map<DecisionRule, List<AlignedObservation>> out = new LinkedHashMap<>();
        for (AlignedObservation each : seen) {
            if (takenBy(each) instanceof WhichRule.TookThis took) {
                out.computeIfAbsent(took.rule(), _ -> new ArrayList<>()).add(each);
            }
        }
        return out;
    }
}
