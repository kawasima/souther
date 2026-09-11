package souther.compiler;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.Note;
import souther.compiler.diag.msg.MatchMessage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which match an arm belongs to is settled by the layout rule (spec §match): a {@code |} indented
 * past the arms of the match around it belongs to the inner one. So an arm written for an outer
 * match can land in an inner one, and the report about it says so.
 *
 * <p>What says it is happening is not how the two are laid out. It is that the case the arm names is
 * one the match around this one has — the arm makes sense there and nowhere else, which is what
 * being written for it means. An arm naming a case of some third sum is a mistake of its own however
 * the two matches are written, and a report telling its author about the layout rule would send them
 * looking at the indentation of something that is indented correctly.
 */
class AnArmAnInnerMatchTookFromTheMatchAroundItSaysSoTest {

    private static final String SUMS = """
            module m

            data A1 = { a: Int }
            data A2 = { a: Int }
            data Outer = A1 | A2

            data B1 = { b: Int }
            data B2 = { b: Int }
            data Inner = B1 | B2

            data C1 = { c: Int }
            data C2 = { c: Int }
            data Third = C1 | C2

            behavior pick : (o: Outer, i: Inner) -> Int
            """;

    private static CompileException err(String body) {
        return assertThrows(CompileException.class, () -> Compiler.compile(SUMS + body));
    }

    private static boolean saysTheInnerMatchTookIt(CompileException e) {
        for (Note note : e.diagnostic().notes()) {
            if (note.said() instanceof MatchMessage.AMatchInAnArmTakesTheArmsAfterIt) {
                return true;
            }
        }
        return false;
    }

    /** The inner match opened on the arm's own line takes every {@code |} after it on that line. */
    @Test
    void anArmTakenBecauseTheInnerMatchWasOpenedOnItsLine() {
        CompileException e = err("""
                let pick (o, i) =
                    match o with
                    | A1 as x -> match i with | B1 as y -> 1 | B2 as z -> 2 | A2 as w -> 3
                    | A2 as v -> 4
                """);
        assertInstanceOf(MatchMessage.NotACaseOf.class, e.diagnostic().said());
        assertTrue(saysTheInnerMatchTookIt(e),
                "the arm names a case of the match around this one, so it was written for that one");
    }

    /**
     * And one taken for the reason the rule is written in terms of, with the two matches on lines of
     * their own: the arm is indented past the arms of the match around it, so the inner one has it.
     */
    @Test
    void anArmTakenBecauseItIsIndentedPastTheArmsOfTheMatchAroundIt() {
        CompileException e = err("""
                let pick (o, i) =
                    match o with
                    | A1 as x ->
                        match i with
                        | B1 as y -> 1
                        | B2 as z -> 2
                      | A2 as w -> 3
                    | A2 as v -> 4
                """);
        assertInstanceOf(MatchMessage.NotACaseOf.class, e.diagnostic().said());
        assertTrue(saysTheInnerMatchTookIt(e),
                "sharing a line is one way to be indented past the arms around it and not the rule");
    }

    /**
     * An arm naming a case of a sum neither match is over is a mistake of its own. It is still said
     * which sum the name belongs to — that is what the author wrote and is worth telling them — and
     * nothing is said about the layout, which took nothing from anybody here.
     */
    @Test
    void anArmNamingACaseOfSomeThirdSumIsNotAboutTheLayout() {
        CompileException e = err("""
                let pick (o, i) =
                    match o with
                    | A1 as x -> match i with | B1 as y -> 1 | B2 as z -> 2 | C1 as w -> 3
                    | A2 as v -> 4
                """);
        assertInstanceOf(MatchMessage.NotACaseOf.class, e.diagnostic().said());
        assertTrue(e.diagnostic().notes().stream()
                        .anyMatch(note -> note.said() instanceof MatchMessage.ItIsACaseOfAnotherSum),
                "which sum the name is a case of is what the author asked about");
        assertFalse(saysTheInnerMatchTookIt(e),
                "no match around this one has that case, so the layout took nothing");
    }
}
