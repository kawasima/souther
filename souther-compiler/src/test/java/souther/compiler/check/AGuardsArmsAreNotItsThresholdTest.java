package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.coverage.ControlPointId;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.reach.Reachability;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which values reach one arm of a guard, which is not which side of the line its value belongs to.
 *
 * <p>A threshold answers the second, and that is what decides where one class ends and the next
 * begins. It cannot answer the first: {@code x <= 5} and {@code x > 5} both put 5 on the low side,
 * and their {@code then} arms are opposite halves of the line. A measure that recovered the arms
 * from a threshold would have counted the wrong one.
 *
 * <p>Measured by putting each operator under a guard that has already settled the position, so that
 * exactly one of its two arms is one nothing reaches — and it is the other one each time. A reading
 * that took the arms off the threshold would prove the same arm for both.
 */
class AGuardsArmsAreNotItsThresholdTest {

    /** {@code n} is at least six by the time the second guard is read, so `<= 5` never holds and
     *  `> 5` always does. Which arm that kills is the whole question. */
    private static final String MODEL = """
            module demo

            data Count = Int invariant value >= 0
            data Low
            data High
            data Same

            behavior pick : (n: Count) -> Low | High | Same

            let pick (n) = {
                guard n.value >= 6 else Low
                %s
                Same
            }
            """;

    /** Whether each arm of the second guard is one nothing reaches, in the order the walk made
     *  them: the arm taken when the condition holds, then the arm taken when it does not. */
    private static List<Boolean> armsOfTheSecondGuard(String guard) {
        Compilation c = Compilation.ofSource(MODEL.formatted(guard), "demo");
        Map<String, PathReachability.Answers> byBehavior =
                c.db().ask(new Adequacy.PathReached("demo")).value();
        // In the order the emitter numbered them, which is the order the walk made them: every arm
        // of a guard is one a run can be recorded in, so the probe numbers put all four in that
        // order and there is nothing here for the reading's own answer to decide.
        List<Reachability> arms = byBehavior.get("pick").found().entrySet().stream()
                .filter(each -> each.getKey() instanceof ControlPointId.ArmPoint)
                .sorted(java.util.Comparator.comparingInt(each ->
                        ((ControlPointId.ArmPoint) each.getKey()).probe().orElseThrow().raw()))
                .map(Map.Entry::getValue)
                .toList();
        assertEquals(4, arms.size(), "two guards, two arms each");
        // then0, then1, else1, else0 — the inner fork is numbered inside the arm that holds it.
        return List.of(arms.get(1) instanceof Reachability.Unreachable,
                arms.get(2) instanceof Reachability.Unreachable);
    }

    @Test
    void twoOperatorsAgreeAboutTheValueAndTakeOppositeArms() {
        List<Boolean> low = armsOfTheSecondGuard("guard n.value <= 5 else High");
        List<Boolean> high = armsOfTheSecondGuard("guard n.value > 5 else High");

        assertEquals(List.of(true, false), low,
                "nothing at six or above is five or below, so the arm it holds on is the dead one");
        assertEquals(List.of(false, true), high,
                "and everything at six or above is above five, so the departure is the dead one");
        assertTrue(!low.equals(high),
                "both put five on the low side; a reading off the threshold would answer the same "
                        + "for each");
    }
}
