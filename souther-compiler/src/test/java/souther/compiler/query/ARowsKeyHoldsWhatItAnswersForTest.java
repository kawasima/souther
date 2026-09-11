package souther.compiler.query;

import org.junit.jupiter.api.Test;
import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.Generator;
import souther.compiler.partition.InjectedAnswer;
import souther.compiler.partition.DecisionArgument;
import souther.compiler.partition.StoodInAnswer;
import souther.compiler.types.ValueName;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * What tells one piece of work from another holds what each row answers for, and not only what it
 * answers.
 *
 * <p>Two rows may state one set of values for one dependency at two different calls — a table keyed
 * on one pair of calls and a table keyed on another. Those are two rows: each runs in an
 * environment the other does not have, and each was certified in its own. Keyed by the values
 * alone, the second is folded into the first before anything reads the table it needed, and the
 * table it needed cannot be recovered afterwards.
 */
class ARowsKeyHoldsWhatItAnswersForTest {

    private static final ValueName.Behavior LOOKUP =
            new ValueName.Behavior("example.keys", "lookup");

    @Test
    void twoRowsAnsweringTwoCallsAlikeAreTwoRows() {
        Generator.GeneratedRow atOneAndTwo = row(asking(1, "Found"), asking(2, "Missing"));
        Generator.GeneratedRow atThreeAndFour = row(asking(3, "Found"), asking(4, "Missing"));

        assertNotEquals(RowKey.of("decides", atOneAndTwo), RowKey.of("decides", atThreeAndFour),
                "the calls a row answers for are part of what makes it that row");
    }

    /** And two rows answering the same calls the same way are one. */
    @Test
    void twoRowsAnsweringOneSetOfCallsAlikeAreOneRow() {
        assertEquals(RowKey.of("decides", row(asking(1, "Found"), asking(2, "Missing"))),
                RowKey.of("decides", row(asking(1, "Found"), asking(2, "Missing"))),
                "and nothing beside that tells them apart");
    }

    /** An answer for every call is told apart by what it answers, there being no call to name. */
    @Test
    void anAnswerForEveryCallIsToldApartByWhatItAnswers() {
        assertNotEquals(RowKey.of("decides", row(everyCall("Found"))),
                RowKey.of("decides", row(everyCall("Missing"))),
                "two rows standing one dependency in two ways are two rows");
    }

    private static Generator.GeneratedRow row(StoodInAnswer... answers) {
        return new Generator.GeneratedRow(
                List.of(new Generator.Purpose.ForAClass(
                        new souther.compiler.partition.AxisId("decides", "at"), "one", "one")),
                List.of(FixtureTemplate.integer(0)), List.of(answers));
    }

    private static StoodInAnswer asking(long at, String answers) {
        InjectedAnswer answer = new InjectedAnswer(LOOKUP,
                List.of(new DecisionArgument.OfANumber(BigDecimal.valueOf(at))));
        return new StoodInAnswer(LOOKUP,
                new StoodInAnswer.Asking.OfOne(answer, List.of(FixtureTemplate.integer(at))),
                FixtureTemplate.string(answers));
    }

    private static StoodInAnswer everyCall(String answers) {
        return new StoodInAnswer(LOOKUP, new StoodInAnswer.Asking.ForEveryCall(),
                FixtureTemplate.string(answers));
    }
}
