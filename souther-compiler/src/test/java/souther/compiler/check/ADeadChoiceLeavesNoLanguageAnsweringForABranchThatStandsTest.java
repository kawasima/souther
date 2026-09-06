package souther.compiler.check;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.values.Allowance;
import souther.compiler.values.AsACompilationAllows;
import souther.compiler.values.PlannedValues;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a choice nobody can take leaves each language a clause is read in.
 *
 * <p>Which alternatives anybody can take is settled over the languages together, and one of them
 * may have found nothing wrong with a branch the other refused. So a branch is dead while the
 * ranges of it are ends somebody could be at, and the pair has to say so to both of them: told
 * only the language that refused it, the other goes on answering for the branch it read while the
 * pair says nobody is in it.
 *
 * <p>The ranges of such a choice reach nothing that reads them — the proof travels beside them and
 * every reader asks that — so what is checked here is what the next question comes to. Met with a
 * rule about the same position, ranges left standing rule that rule out, and ranges that were told
 * do not.
 */
class ADeadChoiceLeavesNoLanguageAnsweringForABranchThatStandsTest {

    private static final String POSITION = "b";

    private static final Allowance<String> SETS = AsACompilationAllows.forAdmittedValues();

    private static OrderedInterval from(int low, int high) {
        return new OrderedInterval(Endpoint.inclusive(Count.of(low)),
                Endpoint.inclusive(Count.of(high)));
    }

    /** A branch the values refused, whose ranges are ends somebody could be at. */
    private static Confinement.Planned<String> refusedByItsValues(OrderedInterval range) {
        return new Confinement.Planned<>(PlannedValues.<String>top().leavingNothing(),
                OrderedIntervals.at(POSITION, range), Map.of());
    }

    /**
     * A choice both alternatives of which the values refused, met afterwards with a rule about the
     * position their ranges spoke about.
     *
     * <p>The rule is one the standing ends of either alternative would rule out, and one that
     * nothing about a choice nobody can take should have an opinion on.
     */
    private Set<String> whatIsLeftEmptyAfterMeeting(OrderedInterval left, OrderedInterval right,
                                                    OrderedInterval afterwards) {
        Confinement.Planned<String> dead = refusedByItsValues(left).bothDead(
                refusedByItsValues(right),
                Confinement.Admission.left(souther.compiler.values.Emptiness.EMPTY));
        Confinement.Planned<String> met = dead.meet(new Confinement.Planned<>(
                PlannedValues.top(), OrderedIntervals.at(POSITION, afterwards), Map.of()));
        return met.resolve(SETS).holdingNothing();
    }

    /**
     * Neither alternative's ends are left answering for the choice.
     *
     * <p>The rule met afterwards is one both alternatives' ends exclude. Kept, they make the
     * position one the rules leave no value at, and the refusal of a declaration is then written
     * about a position that is nobody's fault — the choice is empty because every alternative of it
     * is, and no alternative said anything about {@code b} that this rule contradicts.
     */
    @Test
    void theRangesOfADeadChoiceRuleNothingOutAfterwards() {
        assertEquals(Set.of(), whatIsLeftEmptyAfterMeeting(from(100, 200), from(300, 400),
                from(1, 2)));
    }

    /**
     * And the control: the same rule against a choice whose alternatives stand.
     *
     * <p>The ends are the same ones. What differs is that somebody can be in these branches, so
     * their ranges are theirs to answer with and the rule does contradict them.
     */
    @Test
    void theRangesOfAChoiceSomebodyCanTakeDoRuleItOut() {
        Confinement.Planned<String> live = new Confinement.Planned<>(
                PlannedValues.top(), OrderedIntervals.at(POSITION, from(100, 200)), Map.of());
        Confinement.Planned<String> met = live.meet(new Confinement.Planned<>(
                PlannedValues.top(), OrderedIntervals.at(POSITION, from(1, 2)), Map.of()));

        assertEquals(Set.of(POSITION), met.resolve(SETS).holdingNothing());
    }
}
