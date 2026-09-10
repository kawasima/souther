package souther.compiler.reading;

import souther.compiler.core.Core;
import souther.compiler.coverage.ArmProbe;
import souther.compiler.coverage.ControlClaim;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.coverage.CoverageSites;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How each arm of a body is reached, one answer per arm the plan numbered.
 *
 * <p>Total over the arms of the behavior, which is what makes it worth asking. A measure that finds
 * an arm no row goes through hands the arm on to whatever composes rows, and an arm the reading had
 * nothing on used to be an arm nobody could say anything about: the absence was read as a fact about
 * the body — that nothing takes it — which is not what an absence says. Every arm the plan numbered
 * is here, and an arm this reading never got to says so in as many words.
 *
 * <p>By the number the plan gave the arm, which is the number the site carries and the number a
 * finding is written against. One identity, not two spellings that agree until one moves.
 *
 * <p>The arms and not the obligations. Two occurrences of one arm put together are one thing to
 * report and two places in the body, and what it takes to reach them is what differs — so an answer
 * held per obligation would be one occurrence's way in standing for another's.
 */
final class Arms {

    private final CoverageSites.Plan plan;

    private final Map<ArmProbe, PathAccess> byArm =
            new LinkedHashMap<>();

    Arms(CoverageSites.Plan plan) {
        this.plan = plan;
    }

    /**
     * How arm {@code part} of {@code fork} is reached, where the plan numbered that arm.
     *
     * <p>An arm no run could be recorded at is not one anything reports, so nothing is kept for it
     * — and it is the same answer that decides both: what a run at the arm would be seen doing is
     * what the reading here is handed on with, and where there is none there is nothing to hold a
     * row to.
     */
    void at(Core fork, int part, Reach reach) {
        ControlPlace.Arm[] arms = plan.armsOf(fork);
        if (arms == null || part < 0 || part >= arms.length || arms[part] == null) {
            return;
        }
        ControlClaim.of(arms[part]).ifPresent(arrivesAt ->
                byArm.put(arms[part].probe().get(), reach.told(arrivesAt)));
    }

    /**
     * One answer per arm of {@code behavior}, in the order the plan numbered them.
     *
     * <p>The plan's arms are the whole of it, and that the walk reached every one of them is
     * checked here rather than made true by filling in. An arm this reading never got to is not a
     * kind of answer — it is this reading falling short of what it says it does — and written as
     * one it would be a value a reader has to act on, indistinguishable from the arms whose way in
     * really is beyond what the path language states. The check would then hold of a walk that
     * stopped early, which is the whole of what it is for.
     */
    java.util.SequencedMap<ArmProbe, PathAccess> found(String behavior) {
        // Walked over the plan's own arms, so the order these come back in is the order the plan
        // holds the behavior's arms in. Which is what whoever asks for a row at each of them takes
        // as the order to ask in, and it is only that because this walk is where it comes from.
        java.util.SequencedMap<ArmProbe, PathAccess> out =
                new LinkedHashMap<>();
        for (CoverageSites.ArmSite arm : plan.arms(behavior)) {
            PathAccess access = byArm.get(arm.index());
            if (access == null) {
                throw new IllegalStateException("the reading of `" + behavior + "` did not reach"
                        + " arm " + arm.index() + " of " + arm.obligation().origin()
                        + "; every arm the plan numbered is one this walk goes to");
            }
            out.put(arm.index(), access);
        }
        return java.util.Collections.unmodifiableSequencedMap(out);
    }
}
