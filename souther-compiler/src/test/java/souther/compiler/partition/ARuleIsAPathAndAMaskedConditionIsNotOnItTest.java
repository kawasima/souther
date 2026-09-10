package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule of a body's decision is a path through it, and a condition the path settled before
 * reaching is absent from the rule rather than present with both answers.
 *
 * <p>{@code A && B} states three rules and not two. The answer for {@code A} failing never reads
 * {@code B}, so its rule says nothing about {@code B}; the answer for {@code B} failing says
 * {@code A} held. A reading that answered "one of them failed and neither is named" would have one
 * rule for the two, and an implementation that consults {@code B} only where {@code A} failed would
 * be indistinguishable from one that does not.
 *
 * <p>Held on the rules and not on a verdict, so it says the same thing whatever a build is refused
 * over.
 */
class ARuleIsAPathAndAMaskedConditionIsNotOnItTest {

    private static final String MODEL = """
            module example.short

            data Count = Int
                invariant value >= 0
            data Yes
            data No

            behavior both : (a: Count, b: Count) -> Yes | No
            let both (a, b) = if a.value > 5 && b.value > 5 then Yes else No

            behavior either : (a: Count, b: Count) -> Yes | No
            let either (a, b) = if a.value > 5 || b.value > 5 then Yes else No

            behavior one : (a: Count) -> Yes | No
            let one (a) = if a.value > 5 then Yes else No
            """;

    /**
     * The condition that decides a path is on that path, terminal or not.
     *
     * <p>Left off, both rules of {@code one} would carry the empty vector and one table would
     * answer two ways at one assignment — before any question about collapsing arises.
     */
    @Test
    void theConditionThatSelectedThePathIsOnIt() {
        List<DecisionRule> rules = rulesOf("one");
        assertEquals(2, rules.size(), "a body forking once states a rule per way through the fork");
        for (DecisionRule rule : rules) {
            assertEquals(1, rule.consulted().size(), "the fork's own condition is on its path");
        }
        assertNotEquals(rules.get(0), rules.get(1),
                "the two ways through one fork are two rules");
    }

    /** Three rules, and the one that never reached the second conjunct says nothing about it. */
    @Test
    void aConjunctionStatesThreeRules() {
        assertsAShortCircuit(rulesOf("both"), false);
    }

    /** The same the other way round: a disjunction settles on the left coming out true. */
    @Test
    void aDisjunctionStatesThreeRules() {
        assertsAShortCircuit(rulesOf("either"), true);
    }

    /**
     * The three rules a two-operand short-circuit states, and which of them is the masked one.
     *
     * @param settledBy the way the left operand coming out settles the whole condition, which is
     *                  the outcome the masking rule carries
     */
    private static void assertsAShortCircuit(List<DecisionRule> rules, boolean settledBy) {
        assertEquals(3, rules.size(), "a short-circuit over two operands states three rules");
        List<DecisionRule> masked = rules.stream()
                .filter(rule -> rule.consulted().size() == 1).toList();
        assertEquals(1, masked.size(), "one way through it reads one operand");
        DecidedCondition left = masked.get(0).inOrder().get(0);
        assertEquals(new DecidedCondition.Compared(
                        (DecisionCondition.AComparison) left.condition(), settledBy), left,
                "the operand that settled it is on the rule at the outcome that settled it");

        List<DecisionRule> read = rules.stream()
                .filter(rule -> rule.consulted().size() == 2).toList();
        assertEquals(2, read.size(), "two ways through it read both operands");
        assertEquals(read.get(0).consulted().keySet(), read.get(1).consulted().keySet(),
                "the two are answers about one pair of distinctions");
        assertTrue(read.get(0).consulted().keySet().contains(left.condition()),
                "the operand the masking rule names is one of the pair");
        for (DecisionRule rule : read) {
            assertEquals(new DecidedCondition.Compared(
                            (DecisionCondition.AComparison) left.condition(), !settledBy),
                    rule.at(left.condition()),
                    "reading the second operand takes the first not having settled it");
        }
        DecisionCondition right = read.get(0).consulted().keySet().stream()
                .filter(each -> !each.equals(left.condition())).findFirst().orElseThrow();
        assertEquals(Set.of(new DecidedCondition.Compared((DecisionCondition.AComparison) right,
                        true),
                        new DecidedCondition.Compared((DecisionCondition.AComparison) right,
                                false)),
                Set.of(read.get(0).at(right), read.get(1).at(right)),
                "the two differ in what the second operand came out as");
    }

    /** The decision {@code behavior} states, read the way whoever asks the account reads it. */
    private static List<DecisionRule> rulesOf(String behavior) {
        return DecisionReadings.readToTheEnd(MODEL, behavior);
    }
}
