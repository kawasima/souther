package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.core.Core;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.RulesWithNoLine;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A comparison in a body is filed at the quantity it cuts, exactly as a clause of a declaration is.
 *
 * <p>The two readers describe one kind of evidence (ADR-0090), so a position a canonical form
 * cancelled is one neither of them files at. Written down here because the agreement is what the
 * shared classifier is for: held only on the side that happened to have it, the readers would drift
 * apart again the next time one of them was changed.
 */
class ARuleInABodyIsFiledAtWhatItsQuantityIsAboutTest {

    /**
     * Where each rule this drew no line from was filed, spelled as a report names it.
     *
     * <p>Both of what a reading leaves, because where a rule is filed is one question and how far
     * the reading of it got is another: a walk that stopped is filed at the places it met, and this
     * is about which places those are.
     */
    private static List<String> filedAt(String condition) {
        RulesWithNoLine found = read(condition);
        List<String> out = new java.util.ArrayList<>();
        found.reported().forEach(each ->
                out.add(each.at() + "/" + each.why().getClass().getSimpleName()));
        found.unclassified().forEach(each ->
                out.add(each.at() + "/" + each.why().getClass().getSimpleName()));
        return List.copyOf(out);
    }

    /**
     * A position that cancels out of the form is not one the rule is filed at.
     *
     * <p>What survives relates two positions, so what the rule leaves at each of them is that; and
     * {@code t.y} is left alone, because the rule the arithmetic came to does not mention it.
     */
    @Test
    void aCancelledPositionIsFiledAtByNothing() {
        assertEquals(
                List.of("t.x/ComparisonBetweenPositions", "t.z/ComparisonBetweenPositions"),
                filedAt("t.x + t.y - t.y + t.z <= 10"));
    }

    private static RulesWithNoLine read(String condition) {
        String source = """
                module example.guarded

                data Triple = { x: Int, y: Int, z: Int }

                data Low
                data High

                behavior pick : (t: Triple) -> Low | High

                let pick (t) =
                    if CONDITION
                        then High
                        else Low
                """.replace("CONDITION", condition);
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, () -> "the model under test compiles: " + condition);
        Core body = checked.behaviorBodies().get("pick");
        assertNotNull(body);
        CoverageSites.Plan plan = checked.plan();
        return GuardThresholds.of("pick", body, plan,
                compilation.db().ask(new souther.compiler.query.Adequacy.Inputs(module))
                        .value().get("pick"), rules).noLine();
    }
}
