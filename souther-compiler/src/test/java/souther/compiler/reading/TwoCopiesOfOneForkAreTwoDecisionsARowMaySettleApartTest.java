package souther.compiler.reading;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.coverage.ControlClaim;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ModelOccurrence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two copies of one fork are two decisions, and a row may settle them apart.
 *
 * <p>An operation that asks the block it was handed twice settles the fork inside it twice, on a
 * value of its own each time, so going one way in the first copy and the other way in the second is
 * a row doing two things. Read as one decision it is a condition settled two ways, which is no path
 * — and the path goes rather than being refused, so nothing anywhere says a way through the body
 * was thrown away.
 *
 * <p>Beside the reason it would go: opposite arms of one copy really are one decision settled twice,
 * and the answer for those has to stay no path. The two together are what say the conditions are
 * told apart by which copy they are of rather than by nothing at all.
 */
class TwoCopiesOfOneForkAreTwoDecisionsARowMaySettleApartTest {

    /** One fork, written in a key an operation asks twice. */
    private static final String MODEL = """
            module m

            data Low
            data High

            behavior pick : (xs: List<Bool>) -> Low | High
            let pick (xs) =
                if List.length(List.distinctBy(x -> if x then 1 else 0, xs)) > 1
                then High
                else Low
            """;

    @Test
    void oppositeArmsOfTwoCopiesAreAPathAndOfOneCopyAreNot() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        CoverageSites.Plan plan = checked.plan();

        List<Core> copies = theForkTheOperationCopies(checked.behaviorBodies().get("pick"));

        ControlPlace.Arm[] first = plan.armsOf(copies.get(0));
        ControlPlace.Arm[] second = plan.armsOf(copies.get(1));
        assertTrue(first[0].isMeasured() && first[1].isMeasured() && second[1].isMeasured(),
                "a run through any arm of this fork can be recorded");

        Decision held = decision(first[0]);
        assertNotNull(CoverageNaming.merge(List.of(held), List.of(decision(second[1]))),
                "the copies are settled on values of their own, so a row can take one way through"
                        + " the first and the other through the second");
        assertNull(CoverageNaming.merge(List.of(held), List.of(decision(first[1]))),
                "and one copy going both ways is one decision settled twice");
    }

    private static Decision decision(ControlPlace.Arm arm) {
        return new Decision(new Condition.Arm(arm.arm()),
                ControlClaim.of(arm).orElseThrow(
                        () -> new AssertionError("an arm with a probe can be claimed")));
    }

    /**
     * The two forks of {@code body} the model states as one, which is the fork written in the key.
     *
     * <p>Found by which fork of the model each is rather than by where it stands: what makes the
     * pair the subject here is that the author wrote one of them.
     */
    private static List<Core> theForkTheOperationCopies(Core body) {
        Map<ModelOccurrence, List<Core>> byModel = new LinkedHashMap<>();
        forks(body, byModel);
        List<Core> copied = byModel.values().stream().filter(each -> each.size() > 1)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "the operation copies the fork written in its key: " + byModel));
        return copied;
    }

    private static void forks(Core e, Map<ModelOccurrence, List<Core>> out) {
        if (e instanceof Core.If iff) {
            ConstructOccurrence which = iff.occurrence();
            ModelOccurrence.statedAt(which).ifPresent(stated ->
                    out.computeIfAbsent(stated, any -> new ArrayList<>()).add(iff));
        }
        Core.forEachChild(e, child -> forks(child, out));
    }
}
