package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A rule written under a denial draws the line the rule states, not the one its operator does.
 *
 * <p>A conjunct that placed no end is handed to this reading, which reads what it compares and what
 * it claims of the two sides. Handed the expression instead, this reading recognised the comparison
 * again off the operator — and the operator of a rule written under a denial states the comparison
 * that holds exactly where the rule does not, so {@code not (lo /= hi)} would draw the line of
 * {@code lo /= hi}: the values it parts are the ones the rule admits.
 *
 * <p>An ordering between two positions, because that is the shape this reading draws a line for. A
 * rule naming a value of the number two positions stand apart parts that number from every other
 * one and keeps no end of it, so a denial exchanged there leaves nothing for a line to be the wrong
 * way round about.
 *
 * <p>Read through to the lines rather than at the hand-over, because the hand-over is not where the
 * two readings could differ — it is where one of them stops and the other starts, and a value
 * carried across correctly and read for the wrong thing is a defect this would not see.
 */
class ADeniedComparisonDrawsTheLineItStatesTest {

    /** The same rule written twice: as itself, and as the denial of the comparison that holds
     *  exactly where it does not. */
    @Test
    void anOrderWrittenAsADeniedOppositeDrawsOneLine() {
        assertEquals(linesOf("lo <= hi"), linesOf("Bool.not(lo > hi)"),
                "the denial of an order is the order that holds where it does not, and it keeps"
                        + " that order's end");
    }

    @Test
    void andTheSameForTheOrderThatRefusesItsOwnValue() {
        assertEquals(linesOf("lo < hi"), linesOf("Bool.not(lo >= hi)"),
                "and the same where the rule refuses the value it names");
    }

    /**
     * And an order is not its opposite, which is what makes the pairs above say something.
     *
     * <p>A metamorphic pair over a projection that lost the distinction passes whatever the
     * projection does with it. What is asserted here is that the projection has not lost it: the
     * line {@code lo <= hi} draws and the line {@code lo > hi} draws are different lines, so a
     * reading that took one for the other would have to show up above.
     */
    @Test
    void anOrderAndItsOppositeDoNotDrawTheSameLine() {
        assertFalse(linesOf("lo <= hi").equals(linesOf("lo > hi")),
                "the two keep opposite ends of the number the positions stand apart");
    }

    /** What the reading that draws lines makes of a declaration whose rule is {@code clause}. */
    private static List<LineDrawn> linesOf(String clause) {
        String source = """
                module example.denied

                data R = { lo: Int, hi: Int }
                    invariant same = %s

                data Taken

                behavior take : (r: R) -> Taken
                let take (r) = Taken
                """.formatted(clause);
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model under test is a program that can be written");
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        InputDomain inputs =
                compilation.db().ask(new Adequacy.Inputs(module)).value().get("take");
        List<LineDrawn> drawn = DeclaredThresholds.between("take", inputs.reading(rules));
        assertFalse(drawn.isEmpty(),
                "a rule relating two positions reaches the reading that draws lines: " + clause);
        return drawn;
    }
}
