package souther.compiler.partition;

import org.junit.jupiter.api.Test;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A way is told apart by the rule it is, and not by the order the walk met its conditions in.
 *
 * <p>What a rule is is its columns and what each came out as, which {@link DecisionRule} answers as
 * a map. A path compared as the sequence it was collected in would be a second answer to that, and
 * the two would part over a body whose walk reaches one vector two ways — the reading of the ways
 * holds two arrivals apart when their paths differ, so it would carry one rule twice.
 *
 * <p>Written against the values rather than against a body, because what is under test is the
 * identity and not which bodies produce it. No source is known to reach one vector by two orders,
 * and that is why this is here: the law has to hold before anything does.
 */
class OneRuleIsOneWayHoweverTheWalkMetItsConditionsTest {

    private static final DecisionPath.Consulted OVER_FIVE = compared("a", 5, true);

    private static final DecisionPath.Consulted OVER_TEN = compared("b", 10, false);

    @Test
    void twoOrdersOfOneVectorAreOneWay() {
        DecisionPath one = way(OVER_FIVE, OVER_TEN);
        DecisionPath other = way(OVER_TEN, OVER_FIVE);
        assertEquals(one, other, "the same columns with the same answers are one way");
        assertEquals(one.hashCode(), other.hashCode(), "and hash alike");
        assertEquals(one.rule(), other.rule(), "which is the rule they are");
    }

    @Test
    void andTwoAnswersAboutOneColumnAreNot() {
        assertNotEquals(way(OVER_FIVE), way(compared("a", 5, false)),
                "one column coming out two ways is two ways");
    }

    private static DecisionPath way(DecisionPath.Consulted... consulted) {
        DecisionPath out = DecisionPath.NOWHERE;
        for (DecisionPath.Consulted each : consulted) {
            out = out.and(each.answer(), each.shown(), each.states());
        }
        return out;
    }

    /** One comparison of {@code head} against {@code against}, coming out {@code held}. */
    private static DecisionPath.Consulted compared(String head, int against, boolean held) {
        LinearForm<DecisionAtom> form =
                LinearForm.<DecisionAtom>constant(BigDecimal.valueOf(-against))
                        .plus(LinearForm.atom(new DecisionAtom.OfTheInput(
                                new NumericTerm.ValueOf(TermPath.of(head)))));
        DecisionCondition.AComparison column = new DecisionCondition.AComparison(form, Rel.GT);
        return new DecisionPath.Consulted(new DecidedCondition.Compared(column, held),
                new ShownBy.NothingIsRecorded(column),
                new OnTheWay.Declined(new ConditionOccurrence("b", against),
                        new ConditionReportAnchor.WhereTheReadingMetIt(
                                "b", new ConditionOccurrence("b", against)),
                        new OnTheWay.Why.NoWordsForTheShape()));
    }
}
