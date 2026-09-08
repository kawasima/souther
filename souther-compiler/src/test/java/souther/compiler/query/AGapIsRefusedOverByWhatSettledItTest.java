package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.coverage.ArmProbe;
import souther.compiler.types.WrittenOwner;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.coverage.DecidedBy;
import souther.compiler.coverage.Numberings;
import souther.compiler.coverage.SourceOutcome;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.source.SourceId;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a build refuses over is what settled the arm, not what the measure over every arm went
 * without.
 *
 * <p>A finding carries the measurement behind it, and a build refuses over the ones nothing
 * weakened: a gap a measure established is work to do, and a gap from a measure that could not be
 * finished is a question. Which measurement is behind an arm no row goes through is the reading of
 * the rows against that arm — and handed the branch measure instead, an arm the rows certainly do
 * not reach stopped being refused over because a fork elsewhere in the body stood for a number of
 * rules nothing established. The arms were still named; a build held to them let them through.
 *
 * <p>Built here rather than compiled from a model. A fork whose rule is chosen while it runs is
 * written in this language and is the case this is about, and its rows cannot be read at all — the
 * arms come back unavailable, so no compile reaches an account holding one. What the account does
 * with such an arm is settled where the account is made, and this is that seam.
 */
class AGapIsRefusedOverByWhatSettledItTest {

    private static final SourceConstructOrigin SETTLED =
            SourceConstructOrigin.written(new WrittenOwner.Body("m", "b"), 0,
                    SourceConstruct.IF);
    private static final SourceConstructOrigin UNSETTLED =
            SourceConstructOrigin.written(new WrittenOwner.Body("m", "b"), 1,
                    SourceConstruct.IF);

    /** Four places of one numbering, so that arms put in one list are addresses of one. */
    private static final Map<Integer, ArmProbe> PLACES = Numberings.arms(4);

    private static CoverageSites.ArmSite arm(SourceConstructOrigin fork, int index, DecidedBy decided) {
        return new CoverageSites.ArmSite("b",
                new SourceOutcome.Held(new SourceOutcome.HeldBy.Condition()),
                Numberings.armPlace(index, PLACES.get(index), fork,
                        Citation.of(new SourcePos(1, 1, new SourceId("0")))),
                index,
                new CoverageSites.Obligation("b", fork, index, decided));
    }

    /** Every row read; one fork settled with a row through one of its arms, and one fork beside it
     *  whose rule nothing worked out. */
    private static ArmSummary arms() {
        return Adequacy.BranchEvidence.measured("b",
                List.of(arm(SETTLED, 0, DecidedBy.THE_DECLARATION),
                        arm(SETTLED, 1, DecidedBy.THE_DECLARATION),
                        arm(UNSETTLED, 2, DecidedBy.NOT_SAID),
                        arm(UNSETTLED, 3, DecidedBy.NOT_SAID)),
                Set.of(PLACES.get(0)), Set.of(), Adequacy.NOTHING_PROVEN, WeakeningSet.none()).arms();
    }

    @Test
    void theArmNothingReachesIsAGapABuildRefusesOver() {
        List<Adequacy.Finding> found = Adequacy.Findings.armFindings("b", arms());

        assertEquals(1, found.size(), () -> "one arm, and the fork beside it is not counted: "
                + found);
        assertEquals(Adequacy.Finding.Disposition.REFUSED,
                found.getFirst().disposition(Adequacy.AdequacyBar.RELIABLE_DOMAIN),
                "the rows were read against this arm and none goes through it");
        assertTrue(found.getFirst().isAdequacyGap(Adequacy.AdequacyBar.RELIABLE_DOMAIN),
                "which is the same answer, asked the way a verdict asks it");
    }

    /** And the measure over all of them still says it is short of something, which is the other
     *  half: the numbers hold what they hold, and the arm is answered for on its own. */
    @Test
    void whileTheMeasureOverEveryArmIsStillShortOfSomething() {
        Adequacy.BranchEvidence measured = Adequacy.BranchEvidence.measured("b",
                List.of(arm(SETTLED, 0, DecidedBy.THE_DECLARATION),
                        arm(SETTLED, 1, DecidedBy.THE_DECLARATION),
                        arm(UNSETTLED, 2, DecidedBy.NOT_SAID),
                        arm(UNSETTLED, 3, DecidedBy.NOT_SAID)),
                Set.of(PLACES.get(0)), Set.of(), Adequacy.NOTHING_PROVEN, WeakeningSet.none());

        assertEquals(WeakeningSet.of(new Weakening.ArmsUnsettled(UNSETTLED)),
                measured.measured().weakening(),
                "the fork nothing tells apart is what the measure went without");
        assertEquals(2, measured.arms().counted(),
                "and what the numbers hold is the arms a row can be owed for");
    }
}
