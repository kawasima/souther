package souther.compiler.coverage;

import souther.compiler.query.WeakeningSet;
import souther.compiler.types.WrittenOwner;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.ArmExclusion;
import souther.compiler.query.ArmSummary;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An obligation nothing settled is uncertain however many places it was counted at.
 *
 * <p>What an unsettled rule leaves undecided is how many rules the obligation stands for, and one
 * place can stand for several: a rule chosen while the behavior runs arrives at one call site, and
 * the arms one of them takes say nothing about the arms another would. Asked as "were several
 * places put together", such a place came back settled — its arms were judged as though one rule
 * had been through them, and the count called itself complete over two.
 *
 * <p>Built here rather than compiled from a model. A model whose rule is chosen while it runs is
 * written in this language and is the case this is about, but its rows cannot be read at all -- the
 * arms come back unavailable, so nothing downstream of them is reached. What this holds is the rule
 * the reading follows on the day they can be, and
 * {@code ARuleChosenWhileItRunsIsNeverACompleteMeasureTest} holds the end of it that a model can
 * reach today.
 */
class AnUnsettledDecisionIsUncertainHoweverManyPlacesItHasTest {

    private static final SourceConstructOrigin FORK =
            SourceConstructOrigin.written(new WrittenOwner.Body("m", "b"), 0,
                    SourceConstruct.IF);

    /** Three places of one numbering, so that arms put in one list are addresses of one. */
    private static final java.util.Map<Integer, ArmProbe> PLACES = Numberings.arms(3);

    private static CoverageSites.ArmSite arm(int index, DecidedBy decided) {
        return arm(index, FORK, index, decided);
    }

    private static CoverageSites.ArmSite arm(int index, SourceConstructOrigin fork, int part,
                                             DecidedBy decided) {
        return new CoverageSites.ArmSite("b",
                new SourceOutcome.Held(new SourceOutcome.HeldBy.Condition()),
                Numberings.armPlace(index, PLACES.get(index), fork, null), index,
                new CoverageSites.Obligation("b", fork, part, decided));
    }

    /** Two arms of one fork, neither of them reached. */
    private static Adequacy.BranchEvidence over(DecidedBy decided) {
        return Adequacy.BranchEvidence.measured("b",
                List.of(arm(0, decided), arm(1, decided)), Set.of(), Set.of(),
                souther.compiler.query.Adequacy.NOTHING_PROVEN, WeakeningSet.none());
    }

    /** One place whose rule nothing settled is a fork this cannot say how many rules it stands for. */
    @Test
    void onePlaceNothingSettledIsSaidToBeUncertain() {
        assertEquals(List.of(new ArmExclusion.OccurrencesNotToldApart(FORK)),
                over(DecidedBy.NOT_SAID).arms().exclusions(),
                "however many places it was counted at, and once for the fork rather than once"
                        + " per arm of it");
    }

    /** And its arms are out of the count, so nothing is owed a row over a rule that may not be one. */
    @Test
    void andItsArmsAreNotJudged() {
        ArmSummary arms = over(DecidedBy.NOT_SAID).arms();
        assertEquals(0, arms.counted(),
                "a row through one of them may or may not be a row through this obligation");
        assertEquals(2, arms.notCounted().size(),
                "both arms of the fork are out together, and each says why it is out");
    }

    /** Which is only worth saying beside what a settled one comes to over the same arms. */
    @Test
    void whileASettledOnesArmsAreOwedARow() {
        assertEquals(2, over(DecidedBy.THE_DECLARATION).arms().unmet().size(),
                "both arms of it, and no row goes through either");
    }

    /** A settled one is read like any other. */
    @Test
    void aSettledOneIsReadLikeAnyOther() {
        assertEquals(List.of(), over(DecidedBy.THE_DECLARATION).arms().exclusions(),
                "nothing about it is in doubt");
    }

    /**
     * And a fork nobody could tell apart says nothing about the fork beside it.
     *
     * <p>What is uncertain is how many rules the one fork stands for. The arms of the fork next to
     * it were read against every row there was and no row goes through them, which is a gap and is
     * one whatever this compiler could not work out elsewhere in the body.
     *
     * <p>The evidence is what says so. A finding is refused over by a build where the measurement
     * behind it went without nothing, and these arms were settled by a reading that went without
     * nothing — so the account hands that reading over, and the arm is a gap rather than a question
     * a build has to hold open. Handed the branch measurement instead, which carries every reason
     * about every arm, an arm the rows certainly do not reach was reported as one nobody could
     * decide.
     */
    @Test
    void anArmOfASettledForkBesideAnUnsettledOneIsStillAGap() {
        SourceConstructOrigin beside = SourceConstructOrigin.written(
                new WrittenOwner.Body("m", "b"), 1, SourceConstruct.IF);
        Adequacy.BranchEvidence measured = Adequacy.BranchEvidence.measured("b",
                List.of(arm(0, DecidedBy.NOT_SAID), arm(1, DecidedBy.NOT_SAID),
                        arm(2, beside, 0, DecidedBy.THE_DECLARATION)),
                Set.of(), Set.of(), souther.compiler.query.Adequacy.NOTHING_PROVEN,
                WeakeningSet.none());

        assertEquals(1, measured.arms().unmet().size(),
                () -> "the settled fork's arm is a gap: " + measured.arms().all());
        assertEquals(WeakeningSet.none(),
                measured.arms().unmet().getFirst().coverage().weakening(),
                "and what settled it went without nothing, which is what a build refuses over");
    }
}
