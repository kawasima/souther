package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fork on a sum is one distinction of the scrutinee's position, and its arms are the answers to
 * it.
 *
 * <p>A column apiece would make a table where two arms of one fork are both taken, which is an
 * assignment no row can be written at and nothing can show impossible — so the column is the
 * position and what each rule carries is which of its cases the path took.
 *
 * <p>And a fork inside an arm is consulted only on the paths that take that arm. The rule for the
 * outer case that answers straight away says nothing about the inner position, which is the same
 * masking a short-circuit gets and is here for a fork rather than for an operator.
 */
class AForksArmsAreAnswersAboutOnePositionTest {

    private static final String MODEL = """
            module example.forks

            data Everyone
            data FollowersOnly
            data Audience = Everyone | FollowersOnly
            data Free
            data Paid
            data Pricing = Free | Paid
            data Story =
                { audience: Audience
                , pricing: Pricing
                }
            data Allowed
            data Refused

            behavior look : (story: Story) -> Allowed | Refused
            let look (story) = match story.audience with
                | Everyone -> Allowed
                | FollowersOnly
                    -> match story.pricing with
                        | Free -> Allowed
                        | Paid -> Refused
            """;

    @Test
    void theArmsOfOneForkShareOneColumn() {
        List<DecisionRule> rules = DecisionReadings.readToTheEnd(MODEL, "look");
        assertEquals(3, rules.size(), "one way out of the first fork and two out of the second");

        Set<DecisionCondition> columns = rules.stream()
                .flatMap(rule -> rule.consulted().keySet().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(2, columns.size(), "two forks state two distinctions");
        assertTrue(columns.stream().allMatch(DecisionCondition.ACase.class::isInstance),
                "a fork on a sum is a distinction of a subject: " + columns);

        DecisionCondition audience = rules.stream()
                .filter(rule -> rule.consulted().size() == 1)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no rule answers out of the first fork alone"))
                .inOrder().get(0).condition();
        assertEquals(3, rules.stream().filter(rule -> rule.at(audience) != null).count(),
                "every path is asked the first fork's question");

        DecisionCondition pricing = columns.stream()
                .filter(each -> !each.equals(audience)).findFirst().orElseThrow();
        List<DecisionRule> inside = rules.stream()
                .filter(rule -> rule.at(pricing) != null).toList();
        assertEquals(2, inside.size(), "the second fork is asked on the paths that reach it");
        assertNotEquals(inside.get(0).at(pricing), inside.get(1).at(pricing),
                "its two arms are two answers about it");
        assertEquals(inside.get(0).at(audience), inside.get(1).at(audience),
                "both stand under one case of the first fork");
    }

    /** The path that answers out of the first fork never reaches the second, so its rule is silent
     *  about it rather than carrying both of its cases. */
    @Test
    void aForkInsideAnArmIsMaskedOnTheOtherPaths() {
        List<DecisionRule> rules = DecisionReadings.readToTheEnd(MODEL, "look");
        DecisionRule answered = rules.stream()
                .filter(rule -> rule.consulted().size() == 1)
                .findFirst().orElseThrow();
        DecisionCondition pricing = rules.stream()
                .flatMap(rule -> rule.consulted().keySet().stream())
                .filter(each -> !each.equals(answered.inOrder().get(0).condition()))
                .findFirst().orElseThrow();
        assertNull(answered.at(pricing),
                "a path that settled before a fork says nothing about it");
    }
}
