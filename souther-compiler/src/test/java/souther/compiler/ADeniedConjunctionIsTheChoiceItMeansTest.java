package souther.compiler;

import souther.compiler.diag.Severity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * What a rule written as a denied conjunction states, which is a choice between its halves denied.
 *
 * <p>The mirror of what a denied disjunction states. One connective and one polarity say what a
 * condition gives its reader, and the answer does not turn on which of the two ways round they
 * arrive — so a rule written {@code Bool.not(a && b)} is owed exactly what the same rule written
 * {@code !a || !b} is owed.
 *
 * <p>What the node underneath still spells is the conjunction. A reader that asks the operator what
 * it composes is told a conjunction where the rule is a choice, and what that decides is what a
 * half is answerable for: both halves of a conjunction hold, so a reading may take either as
 * standing for the whole; one alternative of a choice holds, so neither does. The composition is
 * settled where a clause is read out of the tree and nowhere else, which is what the licences over
 * that reading hold; this is the same fact where an author can see it.
 */
class ADeniedConjunctionIsTheChoiceItMeansTest {

    private static long warnings(String module) {
        return Compiler.compileWithWarnings(module).warnings().stream()
                .filter(d -> d.severity() == Severity.WARNING).count();
    }

    /** A rule of a type, written as the choice it is. */
    private static final String AS_A_CHOICE = """
            module demo
            data TooNear
            data Away = Int
                invariant value < 1 || value > 10
            behavior toAway : (n: Int) -> Away | TooNear
                constructs Away
            let toAway (n) = {
                guard n < 1
                    else TooNear
                Away(n)
            }
            """;

    /** And the same rule written as the conjunction it denies. */
    private static final String AS_A_DENIED_CONJUNCTION =
            AS_A_CHOICE.replace("value < 1 || value > 10",
                    "Bool.not(value >= 1 && value <= 10)");

    /** The same model with a rule the guard settles outright, which is what a compiler that read
     *  the rule says nothing about. */
    private static final String SETTLED = AS_A_CHOICE
            .replace("value < 1 || value > 10", "value <= 10")
            .replace("guard n < 1", "guard n <= 10");

    @Test
    void aDeniedConjunctionIsOwedWhatTheChoiceItMeansIsOwed() {
        assertEquals(warnings(AS_A_CHOICE), warnings(AS_A_DENIED_CONJUNCTION),
                "a conjunction denied is the choice between its halves denied, so the two"
                        + " spellings are one rule and neither owes what the other does not");
    }

    /**
     * And the control: what the pair above agree on is not what every model says.
     *
     * <p>Without it they would agree on a compiler that reported nothing whatever it read, and the
     * agreement would be about the reporting rather than about the rule.
     */
    @Test
    void andWhatTheyAgreeOnIsNotWhatEveryModelSays() {
        assertEquals(0, warnings(SETTLED),
                "a rule the guard settles outright leaves nothing to say");
        assertNotEquals(warnings(SETTLED), warnings(AS_A_CHOICE),
                "and the rule above is not settled by its guard, so the agreement is about two"
                        + " spellings of it and not about a compiler with nothing to say");
    }
}
