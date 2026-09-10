package souther.compiler.query;

import souther.compiler.coverage.CoverageSites;
import souther.compiler.partition.DecisionCondition;
import souther.compiler.partition.ShownBy;
import souther.compiler.types.ModelOccurrence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One condition of a decision rule, as a reader is told about it.
 *
 * <p><b>{@code DecisionRule} is for identity; this is for explanation.</b> A rule is told apart by
 * the distinctions it consulted, written the one way round that makes a comparison and its denial
 * one column — so {@code n > 100} in the source is held as the proposition {@code n <= 100} denied.
 * That is what an account has to key on and it is not what an author wrote. Rendered into a
 * sentence, it would show somebody a comparison they did not write, the wrong way round, and the
 * canonicalisation this compiler does for its own sake would arrive as a claim about their model.
 *
 * <p>So a reader is sent to the construct instead. {@link ShownBy} names which construct of the
 * model each condition is and what a run down the path would be seen doing there; a sentence says
 * which construct and which way it went, and the author reads their own comparison at the place it
 * points to.
 *
 * <p>Made once for both surfaces. A report and a warning say this differently — a report can name a
 * file and a diagnostic cannot — and what they may not do is read the rule apart twice, which is
 * how one of them would come to word a condition the other does not have.
 *
 * <p><b>Nothing is dropped.</b> A rule's conditions are what tell it from the rules beside it, and
 * two findings whose sentences say only which behavior they are of are two findings a reader cannot
 * act on. A condition with no construct to point at is said as that rather than left out, and never
 * stood in for by the proposition the account keys on.
 */
public sealed interface DecisionRuleReading {

    /**
     * A comparison the author wrote, coming out one way.
     *
     * @param comparison which comparison of the model, which is what a reader is sent to
     * @param held       whether the path took it holding — of the comparison as written, not of the
     *                   proposition the rule is keyed on
     */
    record AComparisonCameOut(ModelOccurrence comparison, boolean held)
            implements DecisionRuleReading {

        public AComparisonCameOut {
            Objects.requireNonNull(comparison, "a comparison of the model is some construct");
        }
    }

    /**
     * A fork of the model, with the path going down one of its arms.
     *
     * <p>The site and not the fork and a number. Which arm a reader is told about is a thing the
     * coverage sites already have words for, and a sentence built from the number would be a second
     * naming of one arm, free to call it something the branch account does not.
     */
    record AForkTookAnArm(CoverageSites.ArmSite arm) implements DecisionRuleReading {

        public AForkTookAnArm {
            Objects.requireNonNull(arm, "an arm of the model is some site");
        }
    }

    /**
     * A condition of the rule this reading had no words for.
     *
     * <p>There is nothing to point at, and what a reader is owed is that the rule turns on
     * something this compiler cannot show them.
     *
     * <p>The condition is carried for whoever is debugging this compiler and is not what a sentence
     * says. Written into one, the proposition an account keys on would be shown to an author as
     * their own comparison — which is the one thing this whole reading is arranged against.
     */
    record AConditionIsNotShown(DecisionCondition condition) implements DecisionRuleReading {

        public AConditionIsNotShown {
            Objects.requireNonNull(condition, "a condition nothing shows is some condition");
        }
    }

    /**
     * A fork of the model this run has no site for.
     *
     * <p>Beside {@link AConditionIsNotShown} and not among it. That one is the reading of the body
     * falling short of saying what a condition means; this is the plan that numbered the places
     * having nothing under the construct the reading named — a different thing to chase, and a
     * reader told the first would go looking for a shape this compiler cannot read.
     */
    record AForkIsNotPlaced(ModelOccurrence fork, int part) implements DecisionRuleReading {

        public AForkIsNotPlaced {
            Objects.requireNonNull(fork, "an arm is an arm of some fork of the model");
        }
    }

    /**
     * Every condition of {@code ruled}, in the order the author wrote them.
     *
     * <p>One reading per condition and never fewer. The arms are resolved against the plan that
     * numbered the sites, which is where a fork of the model and a place a run is recorded at are
     * already related; a fork the plan has no site for is said as a condition nothing shows rather
     * than left out, so the count of what a reader is told matches the rule.
     */
    static List<DecisionRuleReading> of(souther.compiler.partition.DecisionReading.Ruled ruled,
                                        CoverageSites.Plan plan, String behavior) {
        List<DecisionRuleReading> out = new ArrayList<>();
        for (ShownBy each : ruled.shownBy()) {
            out.add(switch (each) {
                case ShownBy.AtAComparison(var comparison, var held) ->
                        new AComparisonCameOut(comparison, held);
                case ShownBy.AtAnArm(var fork, var part) -> armOf(plan, behavior, fork, part);
                case ShownBy.NothingIsRecorded(var condition) ->
                        new AConditionIsNotShown(condition);
            });
        }
        return List.copyOf(out);
    }

    /**
     * The site of one arm of one fork, or the fact that this run has no site for it.
     *
     * <p>Matched on the construct the source wrote, which is what the two trees agree about. A
     * helper carrying a fork stands in the running tree once per call site and those differ in
     * their lineage, while the arm a row is owed for is the construct — so an occurrence is the
     * wrong grain to match on here and would find nothing for the copies.
     */
    private static DecisionRuleReading armOf(CoverageSites.Plan plan, String behavior,
                                             ModelOccurrence fork, int part) {
        for (CoverageSites.ArmSite site : plan.arms(behavior)) {
            if (site.obligation().part() == part
                    && site.obligation().origin().equals(fork.origin())) {
                return new AForkTookAnArm(site);
            }
        }
        return new AForkIsNotPlaced(fork, part);
    }
}
