package souther.compiler.query;

import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.Position;
import souther.compiler.values.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a position admits is what every rule about it leaves, whichever of its numbers a rule is on.
 *
 * <p>A position is read in two vocabularies — the values that stand there, and the numbers taken of
 * them — and a rule lands in one of them. Held apart, what the position is said to admit is what
 * the values' own vocabulary left, and it holds values the rules about its numbers refuse. Every
 * reader that picks a value out of it then picks one the model does not have, and the one that
 * builds it is told so by a construction that fails rather than by the set it picked from.
 *
 * <p>Asked of the position and not of what a reader of it went on to do. A test that only watched
 * the rows a generator offers would go green again the day some other stage learned to avoid the
 * value, with the position still saying it admits one the model refuses.
 */
class WhatAPositionAdmitsTakesInEveryRuleAboutItTest {

    /** A rule on the length, and nothing on the strings. */
    private static final String A_LENGTH = """
            module example.admits

            data Ticket = String
                invariant String.length(value) >= 1
            data Seated = { ticket: Ticket }

            behavior seat : (t: Ticket) -> Seated
                constructs Seated

            let seat (t) = Seated { ticket = t }
            """;

    /** A rule on the length with both ends. */
    private static final String A_BAND = A_LENGTH.replace(
            "invariant String.length(value) >= 1",
            "invariant String.length(value) >= 2 && String.length(value) <= 3");

    private static Position positionOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(java.util.List.of(), compilation.errors(),
                "the model under test compiles");
        InputDomain domain = compilation.db()
                .ask(new Adequacy.Inputs(compilation.modules().get(0))).value().get("seat");
        return domain.positions().stream().filter(each -> each.path().toString().equals("t"))
                .findFirst().orElseThrow();
    }

    /** The strings a rule about the length refuses are not among what the position admits. */
    @Test
    void aRuleOnTheLengthNarrowsTheValues() {
        Position at = positionOf(A_LENGTH);

        assertFalse(at.admitted().approximation().has(new Value.Text("")),
                "nothing of no length is a ticket, and what the position admits says so");
        assertTrue(at.admitted().approximation().has(new Value.Text("x")));
        assertTrue(at.admitted().approximation().has(new Value.Text("xyz")));
    }

    /** And both ends of such a rule, so it is the run and not the one end that is taken in. */
    @Test
    void aBandOnTheLengthLeavesTheStringsInsideIt() {
        Position at = positionOf(A_BAND);

        assertFalse(at.admitted().approximation().has(new Value.Text("x")));
        assertTrue(at.admitted().approximation().has(new Value.Text("xy")));
        assertTrue(at.admitted().approximation().has(new Value.Text("xyz")));
        assertFalse(at.admitted().approximation().has(new Value.Text("wxyz")));
    }
}
