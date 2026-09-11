package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.ReadAs;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of the names a value wears has its rules read when a value is proposed for it.
 *
 * <p>All of them. A value written under a name is written under every name the position wears, and
 * the rules of every one of those names are rules its value is under — so each of them proposes a
 * value, and the value comes out wearing all the names whichever of them asked for it.
 *
 * <p>Held here because it is the difference between two readings of one position. The names a value
 * wears and what is left under all of them are one answer; walking to what a single name wraps is
 * another, and the two part exactly where a name wraps a name. Read the second way, a rule was
 * about whatever the walk had reached when it stopped, which was one name deep — so a name over a
 * name proposed nothing of its own, and the position was offered the value it would have had with
 * no rule written on it at all.
 *
 * <p>Innermost first, which is an order over the proposals and not over the rules. Every name's
 * rules govern the value whichever end they are read from; what the order decides is which proposal
 * a bounded search reaches before it stops.
 */
class WhichNamesRulesAreReadWhenAValueIsProposedTest {

    private static List<String> proposedFor(String declarations, String type) {
        RuleReadingSource rules = RuleReadings.ofSource("""
                module demo

                import String ( startsWith, endsWith )

                """ + declarations);
        TypeSymbol at = TypeSymbols.declared(new TypeKey(rules.symbols().module(), type));
        return Partitions.representativesOf(Type.ref(at),
                        RuleReadingContext.unshared(rules, ReadAs.THE_COMPILATION_DOES),
                        null, Set.of())
                .stream().map(FixtureTemplate::text).toList();
    }

    /** A rule on the name the string is directly under is read, and proposes what it asks for. */
    @Test
    void aRuleOnTheNameOverTheStringProposesAValue() {
        assertEquals(List.of("A(\"X\")", "A(\"x\")"), proposedFor("""
                data A = String
                    invariant tagged = startsWith("X", value)
                """, "A"));
    }

    /** The same rule under a second name: the value comes out under both, and the proposal is the
     *  inner name's. */
    @Test
    void aRuleOnTheInnerNameIsReadThroughTheOuterOne() {
        assertEquals(List.of("A(B(\"X\"))", "A(B(\"x\"))"), proposedFor("""
                data B = String
                    invariant tagged = startsWith("X", value)

                data A = B
                """, "A"));
    }

    /**
     * A rule on a name that wraps a name proposes the value it asks for.
     *
     * <p>What the rule is about is the value under every name, and how the value is written is every
     * name the position wears. Read as one question, the second answer decided the first: the rule
     * was about whatever a single name wrapped, which here is another name and not the string.
     */
    @Test
    void aRuleOnANameOverANameProposesTheValueItAsksFor() {
        List<String> proposed = proposedFor("""
                data B = String

                data A = B
                    invariant tagged = startsWith("X", value)
                """, "A");

        assertEquals(List.of("A(B(\"X\"))", "A(B(\"x\"))"), proposed);
        assertTrue(proposed.stream().anyMatch(each -> each.contains("X")),
                "the rule on the outer name proposed what it asks for: " + proposed);
    }

    /** Both names carrying a rule: each proposes what it asks for, innermost first. */
    @Test
    void whereBothNamesCarryARuleEachProposesInnermostFirst() {
        assertEquals(List.of("A(B(\"X\"))", "A(B(\"Z\"))", "A(B(\"x\"))"), proposedFor("""
                data B = String
                    invariant tagged = startsWith("X", value)

                data A = B
                    invariant ended = endsWith("Z", value)
                """, "A"));
    }

    /** And the names go back on in the order they were read off, however many there are. */
    @Test
    void theValueComesOutUnderEveryNameThePositionWears() {
        assertEquals(List.of("A(B(\"x\"))"), proposedFor("""
                data B = String

                data A = B
                """, "A"));
    }
}
