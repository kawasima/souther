package souther.compiler.report;

import org.junit.jupiter.api.Test;
import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A page that names a position a rule divided says which rule, and where it is.
 *
 * <p>The classes of such a position are made by the rules that compose them, and a reader told that
 * no row is in one of them is being sent to write it. What they have to read first is the rule that
 * made the class — it says which values the class holds — and a page naming the class alone leaves
 * them a name of the model's with nowhere to look it up.
 *
 * <p><b>Which rule, and not which reading.</b> A helper is expanded at each call, so one rule the
 * author wrote is read at several places and each reading divides the position it names. What a
 * reader is sent to is the rule, so the page writes one sentence for it however many readings of it
 * this compiler met.
 */
class ARuleThatDividedAPositionIsShownWithAPlaceTest {

    /**
     * A predicate over the strings at a position, which tells a set of its values from the rest.
     *
     * <p>The rule is on the sixth line and its call begins at the nineteenth column, counted from
     * one: {@code let f (code) = if } is eighteen characters.
     */
    private static final String A_PREDICATE = """
            module example.codes

            data Answer = Yes | No

            behavior f : (code: String) -> Answer
            let f (code) = if String.startsWith("JP", code) then Yes else No
            """;

    /** Two rules over the strings at one position, which compose its classes together. */
    private static final String TWO_PREDICATES = """
            module example.codes

            data Answer = Yes | No

            behavior f : (code: String) -> Answer
            let f (code) =
                if String.startsWith("JP", code) then Yes
                else if String.endsWith("X", code) then Yes
                else No
            """;

    /** A comparison, which puts a line on the order the values are counted on rather than telling
     *  a set of them from the rest. */
    private static final String A_LINE = """
            module example.codes

            data Low
            data High

            behavior f : (n: Int) -> Low | High
            let f (n) = if n > 10 then High else Low
            """;

    /** The rule is named where the classes it composed are, and by a place a reader can open. */
    @Test
    void theRuleThatComposedThePositionsClassesIsNamedWithItsPlace() {
        assertTrue(page(A_PREDICATE).contains("· code is divided by predicate@6:19"),
                () -> "the page says which rule divided `code`:\n" + page(A_PREDICATE));
    }

    /** One sentence per rule, so a position two of them compose is a reader's two rules to read. */
    @Test
    void aPositionTwoRulesComposeNamesBoth() {
        assertEquals(2, page(TWO_PREDICATES).lines()
                        .filter(each -> each.contains("is divided by")).count(),
                () -> "one sentence per rule that composed the classes:\n"
                        + page(TWO_PREDICATES));
    }

    /**
     * And a position whose rules draw a line says nothing here.
     *
     * <p>The control. A line is not what composed the classes — the classes on an order are the
     * runs of values between the cuts — and a page that wrote this sentence for every position with
     * a rule about it would be saying the same thing of two different answers.
     */
    @Test
    void aPositionARuleDrewALineOnIsNotSaidToBeDividedByIt() {
        assertFalse(page(A_LINE).contains("is divided by"),
                () -> "a line is not what composes a position's classes:\n" + page(A_LINE));
    }

    private static String page(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation)
                .human(SourceRendering.namedByIdentity(compilation.texts()));
    }
}
