package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A body deciding by one of the language's own operations states the rules the author wrote, with
 * the condition said to be one this compiler has no words for.
 *
 * <p>The operation is a call the model names, so what it answers is a value rather than a decision
 * the model states: its own forks are its implementation, and a caller answers for the rules a
 * caller wrote. Read where the operation is expanded, the ways through the fork are the operation's
 * and cannot all be written down — so the arms are reached by nothing anything can filter down to
 * what the model states, and the rules the author wrote go with them.
 *
 * <p>Read where it stands, the fork is one fork with two ways and a condition nothing here can
 * state. Which is a rule apiece carrying a column that says so, and a measurement that admits it
 * read less of the body than the body states.
 */
class AForkOnOneOfTheLanguagesOwnOperationsIsStillARuleTest {

    private static final String MODEL = """
            module example.operation

            data On
            data Off
            data Active = On | Off
            data Item = { active: Active }
            data Yes
            data No
            data Answer = Yes | No

            behavior anyActive : (items: List<Item>) -> Answer
            let anyActive (items) =
                if List.any(i -> i.active == On, items) then Yes else No
            """;

    @Test
    void theAuthorsForkStatesItsTwoRules() {
        List<DecisionRule> rules = DecisionReadings.readToTheEnd(MODEL, "anyActive");
        assertEquals(2, rules.size(), () -> "one fork, both ways: " + rules);
        for (DecisionRule rule : rules) {
            assertEquals(1, rule.consulted().size(),
                    () -> "and the fork's own condition is the whole of the path: " + rule);
            assertInstanceOf(DecisionCondition.AConditionNotRead.class,
                    rule.inOrder().get(0).condition(),
                    "what the operation answers is not a distinction this compiler can state");
        }
        assertNotEquals(rules.get(0), rules.get(1), "the two ways are two rules");
    }
}
