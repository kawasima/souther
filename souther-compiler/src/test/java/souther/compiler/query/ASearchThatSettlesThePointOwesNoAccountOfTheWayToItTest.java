package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.partition.Generator;
import souther.compiler.partition.ConditionOccurrence;
import souther.compiler.partition.ConditionReportAnchor;
import souther.compiler.partition.OnTheWay;
import souther.compiler.partition.WayToTheBorder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a search left unaccounted for is asked of the search, and a search that settled the point
 * left nothing.
 *
 * <p>A walk of the whole of what the rules leave that reaches no value proves there is nothing to
 * find, and it proves it whether or not the box it walked held rows that never arrive:
 *
 * <pre>what reaches the point ⊆ the region searched, and the region searched is empty</pre>
 *
 * <p>so what reaches the point is empty as well. A condition on the way that nothing represented in
 * that region takes nothing away from the proof, and telling an author about it there would send
 * them looking for a row the model has already been shown not to hold.
 *
 * <p>Held here rather than against a report. Two outcomes of one search differ only in what the
 * search came to, and no pair of models produces that pair: a condition a reading takes in narrows
 * the region and draws a line of its own, so the two sides of a model-level comparison differ in
 * more than the thing under test. At this end the way is held still and the outcome varied, which is
 * the comparison the rule is about.
 *
 * <p>No region is built here, and that is the shape of the thing rather than an economy. What an
 * outcome carries is the account of the way to the point; where a row was looked for is worked out
 * from that account by whoever is looking, and a search that has finished is not.
 */
class ASearchThatSettlesThePointOwesNoAccountOfTheWayToItTest {

    private static final ConditionOccurrence MET = new ConditionOccurrence("b", 0);

    private static final OnTheWay.Declined LEFT_OUT = new OnTheWay.Declined(MET,
            new ConditionReportAnchor.WhereTheReadingMetIt("m", MET),
            new OnTheWay.Why.NoWordsForTheShape());

    /** One way to a point, with one condition on it that nothing took in. */
    private static WayToTheBorder way() {
        return new WayToTheBorder(List.of(LEFT_OUT));
    }

    private static ItemAssessment.Attempt came(Generator.UnresolvedCombination.Reason to) {
        return new ItemAssessment.Attempt.Unresolved(
                new Generator.UnresolvedCombination(List.of("p.x = 11"), to), way());
    }

    /**
     * A search that settled nothing owes the account: the box it looked in is half the answer.
     *
     * <p>Owed as what stage let the condition go and not as the walk's word for it alone. The same
     * list holds conditions this reading had no words for and conditions it stated that nothing
     * composed a value under, and a reader acts differently on the two — so each says which it is,
     * and a walk's decline arrives wearing the stage it happened at.
     */
    @Test
    void aSearchThatSettledNothingOwesWhatTheWayLeftOut() {
        assertEquals(List.of(new souther.compiler.partition.ReachabilityGap.Unstated(LEFT_OUT)),
                came(Generator.UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED).unaccountedFor());
        assertEquals(List.of(new souther.compiler.partition.ReachabilityGap.Unstated(LEFT_OUT)),
                came(Generator.UnresolvedCombination.Reason.NO_CERTIFIED_WITNESS).unaccountedFor());
        assertEquals(List.of(new souther.compiler.partition.ReachabilityGap.Unstated(LEFT_OUT)),
                came(Generator.UnresolvedCombination.Reason.LINKAGE_FAILED).unaccountedFor());
    }

    /** And one that proved there is nothing there owes none of it, over the same way. */
    @Test
    void aSearchThatProvedThereIsNothingThereOwesNothing() {
        assertEquals(List.of(), came(
                Generator.UnresolvedCombination.Reason.THE_RULES_LEAVE_NOTHING_THERE)
                .unaccountedFor());
    }

    /**
     * A search nobody made owes none of it either, and neither does one that produced a row.
     *
     * <p>A row at the point answers the question the search was asked; how it came to be looked for
     * there is then a fact about how it was found rather than a caveat on an answer nobody got.
     */
    @Test
    void anAnsweredPointAndAnUnmadeSearchOweNothing() {
        assertEquals(List.of(), ItemAssessment.Attempt.Built.certified(
                new Generator.GeneratedRow(
                        new Generator.Purpose.ForAPoint("p.x = 11"), List.of()), way())
                .unaccountedFor());
        assertEquals(List.of(), new ItemAssessment.Attempt.Unavailable(
                ItemAssessment.Attempt.Reason.NO_CLASSES).unaccountedFor());
    }
}
