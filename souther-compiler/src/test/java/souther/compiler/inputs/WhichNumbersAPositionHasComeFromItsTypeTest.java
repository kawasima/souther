package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which numbers a position has is its type's answer, and no rule votes on it.
 *
 * <p>A {@code String} is a carrier with two of them — its own order, and how long it is — and both
 * are there wherever a string stands. What a rule does is say which of them it is about; it does
 * not make one of them the position's and take the other away, because there is nothing to take: an
 * axis at each is a run of classes and lines over that number's own values, and two of them at one
 * place are two measures rather than two candidates.
 *
 * <p>So a rule about the length and a rule about the order are not in each other's way, and neither
 * is a rule that writes about a number and places no end. Every one of them is filed at the number
 * it names.
 */
class WhichNumbersAPositionHasComeFromItsTypeTest {

    /** An ordering of the length, which places an end. */
    @Test
    void aRuleOrderingTheLengthLeavesBothNumbersStanding() {
        assertBothNumbers("String.length(value) >= 1");
    }

    /** One that holds the length away from a value, which places none. */
    @Test
    void aRuleHoldingTheLengthAwayFromAValueLeavesBothNumbersStanding() {
        assertBothNumbers("String.length(value) /= 0");
    }

    /** And one that names a length, which states both ends at once and so places neither. */
    @Test
    void aRuleNamingALengthLeavesBothNumbersStanding() {
        assertBothNumbers("String.length(value) == 5");
    }

    /** A rule on the string's own order does not take the length away either. */
    @Test
    void aRuleOnTheValuesOwnOrderLeavesBothNumbersStanding() {
        assertBothNumbers("value >= \"m\"");
    }

    /** And a string nobody wrote a rule about has both all the same: the numbers are the type's. */
    @Test
    void aStringNoRuleIsWrittenAboutHasBothNumbers() {
        assertEquals(List.of("n", "String.length(n)"), numbersOf(null),
                "both are numbers of the position wherever a string stands");
    }

    /**
     * A number nothing counts leaves the position one number.
     *
     * <p>An {@code Int} holds a number and nothing is taken of it, so there is the one to measure —
     * which is what makes the pair above the type's answer rather than a spare slot every position
     * carries.
     */
    @Test
    void aTypeNothingIsCountedOfHasTheOneNumber() {
        assertEquals(List.of("n"), numbersOf("Int", null),
                "an Int is its own number and nothing counts it");
    }

    private static void assertBothNumbers(String invariant) {
        assertEquals(List.of("n", "String.length(n)"), numbersOf(invariant),
                "the rule says which number it is about and leaves both of them the position's");
    }

    private static List<String> numbersOf(String invariant) {
        return numbersOf("String", invariant);
    }

    /** How the reading spells the numbers of a position of {@code carrier}, in order. */
    private static List<String> numbersOf(String carrier, String invariant) {
        String source = """
                module example.axis

                data Subject = %s
                    %s

                data Ok

                behavior take : (n: Subject) -> Ok
                let take (n) = Ok
                """.formatted(carrier, invariant == null ? "" : "invariant " + invariant);
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        InputDomain inputs = InputDomain.of(sigs.get("take"), rules,
                ReadAs.THE_COMPILATION_DOES);
        return inputs.at(TermPath.of("n")).numbers().stream().map(Object::toString).toList();
    }
}
