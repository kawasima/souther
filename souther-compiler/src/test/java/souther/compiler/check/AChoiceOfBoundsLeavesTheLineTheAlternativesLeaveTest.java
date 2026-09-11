package souther.compiler.check;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A choice of bounds on one position leaves the line its alternatives leave together.
 *
 * <p>Every value satisfying {@code n >= 2 || n >= 3} is at least two, so the rules stop the position
 * at two and a row at two is a row at a line the model draws. Written with {@code &&} between them
 * the same two bounds are a line this compiler draws, and written alone either of them is — so a
 * choice coming back as a rule that draws none was a sentence about the model, and the model draws
 * one.
 *
 * <p><b>Not a list of shapes.</b> What each of these holds is where an answer comes from: what a
 * choice leaves a position is settled once, where the branches have their fate, and every reader
 * that draws a line is reading that one answer. A shape below that came back short would be a
 * second reading of the same choice, arrived at by whichever road the model happened to take.
 */
class AChoiceOfBoundsLeavesTheLineTheAlternativesLeaveTest {

    /**
     * A choice of two bounds on a number stops it where the wider of them does.
     *
     * <p>And says so however the alternatives are ordered, however many there are, and however they
     * are bracketed: what a choice leaves is what either alternative leaves, which reads the same
     * way round, and an answer that moved with the brackets would be an answer about the writing.
     */
    @Test
    void aChoiceOfBoundsOnANumberStopsItWhereTheWiderOfThemDoes() {
        assertEquals(
                List.of("n >= 2: 2",
                        "n >= 3 || n >= 2: 2",
                        "n >= 2 || n >= 3: 2",
                        "n >= 2 || n >= 3 || n >= 4: 2",
                        "(n >= 2 || n >= 3) || n >= 4: 2",
                        "n >= 2 || (n >= 3 || n >= 4): 2"),
                linesAt("n >= 2", "n >= 3 || n >= 2", "n >= 2 || n >= 3",
                        "n >= 2 || n >= 3 || n >= 4", "(n >= 2 || n >= 3) || n >= 4",
                        "n >= 2 || (n >= 3 || n >= 4)"),
                "each of them admits exactly the values from two up, so each draws the line at two");
    }

    /**
     * And an order that is not one of numbers is stopped the same way.
     *
     * <p>The reading that composes a choice is over the positions and not over the numbers among
     * them, so what settles a choice of bounds on a string is the answer that settles one on a
     * number. Read through the arithmetic alone, a rule about strings is a rule about no number
     * this could name — which is what left the choice below saying the model draws no line while
     * either alternative written by itself drew one.
     */
    @Test
    void andAnOrderThatIsNotOneOfNumbersIsStoppedTheSameWay() {
        assertEquals(
                List.of("s > \"a\": a", "s > \"a\" || s > \"b\": a", "s > \"b\" || s > \"a\": a"),
                linesAt("s > \"a\"", "s > \"a\" || s > \"b\"", "s > \"b\" || s > \"a\""),
                "every string above \"b\" is above \"a\", so the choice stops the position at \"a\"");
    }

    /**
     * And which position such a rule is about does not turn on which side it was written on.
     *
     * <p>What a leaf states is one question with one answer, and the reading that answers it for an
     * order the arithmetic has no words for asks the two sides whole. That is the shape a second,
     * narrower reading of the same question takes — so what is held here is that it is not one: the
     * four spellings below are one rule about the string at a position, and a choice offering any
     * of them draws the line that rule draws.
     */
    @Test
    void andWhichPositionItIsAboutDoesNotTurnOnWhichSideItIsWrittenOn() {
        assertEquals(
                List.of("s > \"a\" || s > \"b\": a",
                        "\"a\" < s || s > \"b\": a",
                        "s > \"a\" || \"b\" < s: a",
                        "\"a\" < s || \"b\" < s: a"),
                linesAt("s > \"a\" || s > \"b\"",
                        "\"a\" < s || s > \"b\"",
                        "s > \"a\" || \"b\" < s",
                        "\"a\" < s || \"b\" < s"),
                "each alternative holds the string at a position above a constant, whichever side"
                        + " of the comparison the author put the position on");
    }

    /**
     * And a choice bounds a position only where both of its alternatives do.
     *
     * <p>The control, and the one thing that tells reading the settled answer from gathering what
     * the branches said. A value taking the alternative that says nothing about the position stands
     * anywhere on it, so the choice does — and a reader that collected the bounds its branches
     * wrote would draw a line at two on a model that admits every number there is.
     */
    @Test
    void andAChoiceBoundsAPositionOnlyWhereBothAlternativesDo() {
        assertEquals(
                List.of("n >= 2 || s > \"a\": nothing", "s > \"a\" || n >= 2: nothing"),
                linesAt("n >= 2 || s > \"a\"", "s > \"a\" || n >= 2"),
                "either alternative leaves the position the other bounds at every value");
    }

    /**
     * And an alternative nobody can be in leaves the choice what the other one leaves.
     *
     * <p>Where the ends of a branch have crossed, every value of the choice is in the branch beside
     * it — so the line is that branch's, and a reading that let the empty branch widen the answer
     * would draw none at all.
     */
    @Test
    void andAnAlternativeNobodyCanBeInLeavesTheChoiceWhatTheOtherLeaves() {
        assertEquals(List.of("(n >= 5 && n <= 3) || n >= 2: 2"),
                linesAt("(n >= 5 && n <= 3) || n >= 2"),
                "nothing satisfies the first alternative, so the rules are the second alternative");
    }

    /**
     * And what the choice leaves is where the lines are and not which values stand there.
     *
     * <p>Two alternatives naming one value each leave the run between them, and no value has a
     * number in there. The outermost ends are two and five and the model draws a line at each; what
     * is between them is a run nothing can be written in, and it comes back saying so. Read as the
     * values the rules admit, the run would be offered as somewhere a row could stand.
     */
    @Test
    void andWhatTheChoiceLeavesIsWhereTheLinesAreAndNotWhichValuesStandThere() {
        List<String> lines = linesOf("n == 2 || n == 5");

        assertEquals(List.of("border      borders 2   obligations 0/0"),
                lines.stream().filter(each -> each.startsWith("border")).toList(),
                "the outermost ends of the two named values are the lines the rules draw");
        assertEquals(2, lines.stream()
                        .filter(each -> each.contains("nothing composed one")).count(),
                "and the runs between them hold no value, which is said rather than offered");
    }

    /**
     * And one line is drawn once, whatever else the rule says beside it.
     *
     * <p>Which conjunct a line is owed to is answered by reading the declaration again without it,
     * and the two readings are readings of one world only where the conjunct is out of both. While
     * the reading that composes the choices held the conjunct either way, no single conjunct moved
     * the end and the pair of them did — so the end came back owed to both, and the same line was
     * written twice.
     */
    @Test
    void andOneLineIsDrawnOnceWhateverElseTheRuleSaysBesideIt() {
        assertEquals(
                List.of("border      borders 1   obligations 0/0"),
                linesOf("(String.length(s) >= 2 || String.length(s) >= 3)"
                        + " && String.length(s) >= 1").stream()
                        .filter(each -> each.startsWith("border")).toList(),
                "the length stops at two, and the bound written beside the choice is not a second"
                        + " rule drawing the same line");
    }

    /** What the document says the model draws on the position each clause is about. */
    private static List<String> linesAt(String... clauses) {
        List<String> out = new java.util.ArrayList<>();
        for (String clause : clauses) {
            out.add(clause + ": " + drawnIn(clause));
        }
        return out;
    }

    /** Where the one line of {@code clause} falls, or {@code nothing} where it draws none. */
    private static String drawnIn(String clause) {
        List<String> at = linesOf(clause).stream()
                .filter(each -> each.startsWith("· no ") && each.contains(" point is owed at "))
                .map(each -> each.substring(each.indexOf(" at ") + " at ".length()))
                .map(each -> each.substring(each.indexOf('=') + 1, each.indexOf('(')).strip())
                .distinct()
                .toList();
        return at.isEmpty() ? "nothing" : String.join(", ", at);
    }

    private static List<String> linesOf(String clause) {
        Compilation compilation = Compilation.ofSource("""
                module demo
                data Yes
                data No
                data Answer = Yes | No
                data N = { n: Int, s: String }
                    invariant r = %s

                behavior check : (v: N) -> Answer
                let check (v) = Yes
                """.formatted(clause), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts())).lines()
                .map(String::strip)
                .toList();
    }
}
