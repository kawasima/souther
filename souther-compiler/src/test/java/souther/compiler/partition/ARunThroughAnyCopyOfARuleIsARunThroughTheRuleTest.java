package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.coverage.AlignedObservation;
import souther.compiler.coverage.ComparisonEmissionSite;
import souther.compiler.coverage.Numberings;
import souther.compiler.coverage.Runs;
import souther.compiler.coverage.SeenComparison;
import souther.compiler.coverage.SiteNumbering;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run that got an answer out of any copy of a rule got one out of the rule.
 *
 * <p>A library operation may evaluate a closure it was handed more than once — {@code
 * List.distinctBy} asks its key twice — so a comparison the author wrote once is written into the
 * tree that runs more than once and each copy is watched. The model states one rule: which of the
 * copies ran is the operation's business, and a row is owed a run through the rule rather than
 * through every copy the library happens to make.
 *
 * <p><b>Which is what the pair below is for.</b> Asked for all of them, the first case comes back
 * saying the row never reached the rule — a debt against how the library is written. Answered
 * without asking at all, the second comes back saying it did, and a row that ran nowhere near the
 * comparison would meet a line on it.
 */
class ARunThroughAnyCopyOfARuleIsARunThroughTheRuleTest {

    private static final SiteNumbering NUMBERING = Numberings.ofComparisons(2);

    private static final ComparisonEmissionSite ONE = NUMBERING.comparison(0);

    private static final ComparisonEmissionSite ANOTHER = NUMBERING.comparison(1);

    @Test
    void aRunRecordedAtOneOfThemGotAnAnswerOutOfTheRule() {
        AlignedObservation ran = Runs.of(NUMBERING, Set.of(),
                Set.of(new SeenComparison(ANOTHER, true)));

        assertTrue(StandingAtAPoint.gotAnAnswerOutOfTheRule(List.of(ONE, ANOTHER), ran),
                "the row ran through one copy of the comparison, which is the comparison");
    }

    @Test
    void aRunRecordedAtNoneOfThemDidNot() {
        AlignedObservation ran = Runs.nowhere(NUMBERING);

        assertFalse(StandingAtAPoint.gotAnAnswerOutOfTheRule(List.of(ONE, ANOTHER), ran),
                "a rule watched in two places is still one a run has to have reached");
    }
}
