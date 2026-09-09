package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.regex.PatternParser;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternRead;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Allowance;
import souther.compiler.values.AsACompilationAllows;
import souther.compiler.values.PlannedValues;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A reading with a position nobody could build says nothing about whether anything satisfies it.
 *
 * <p>What stands at such a position is every value, which is true and is wider than the rules. So a
 * walk that found no refusal there found none because nothing was asked, and the settled positive
 * answer it came back with is about less than the rules say.
 *
 * <p><b>The same rules twice, and only the allowance differs.</b> A reading whose values admit
 * nothing answers the same however little was built, and one nothing worked out was never going to
 * answer positively — so a fixture that showed either would show this rule holding where it does
 * nothing. What is asked here is a reading that does come out positive when there is room to build
 * it, asked again with room for less.
 *
 * <p>And asked wherever the reading is asked. What a reading could not build is a fact about that
 * reading and not about the question that reached it, so a conjunction that took the reading in
 * answers under it too — otherwise which answer comes back is settled by how far from the reading
 * the caller happens to be standing.
 */
class AReadingShortOfAPositionPublishesNoPositiveAnswerTest {

    private static final String HERE = "here";

    /** A machine of some hundreds of states, at the one position these readings are about. */
    private static PlannedValues<String> aPatternWorthBuilding() {
        PatternRead said = PatternParser.read("a{300}");
        return PlannedValues.at(HERE, new AdmittedPlan.Pattern(PatternPlan.of(
                assertInstanceOf(PatternRead.Read.class, said).syntax())));
    }

    /** Room for a machine of {@code states}, which is what settles whether the pattern is built. */
    private static Confinement.Worked<String> readWithRoomFor(int states) {
        return new Confinement.Planned<>(aPatternWorthBuilding(), OrderedIntervals.top(), Map.of())
                .resolve(Allowance.of(new PatternPlan.Budget(states, states)));
    }

    /** Room for the pattern, and room for anything else these readings ask for. */
    private static final int ENOUGH = 50_000;

    /** Room for neither the pattern nor what widening around it costs. */
    private static final int TOO_LITTLE = 20;

    @Test
    void theSameRulesComeOutPositiveOnlyWhereEveryPositionWasBuilt() {
        assertEquals(souther.compiler.values.Emptiness.NONEMPTY,
                readWithRoomFor(ENOUGH).admits(),
                "these rules admit something, where there was room to work out what");

        Confinement.Worked<String> shortOfAPosition = readWithRoomFor(TOO_LITTLE);

        assertEquals(Set.of(HERE), shortOfAPosition.made().unbuilt(),
                "and the same rules leave this position unworked-out at the smaller allowance");
        assertFalse(shortOfAPosition.made().values().isBottom(),
                "which is not the values coming out empty: what stands there is every value");
        assertEquals(souther.compiler.values.Emptiness.UNDECIDED, shortOfAPosition.admits(),
                "so nobody has shown that anything satisfies them");
    }

    @Test
    void andSaysTheSameToAConjunctionThatTookTheReadingIn() {
        Confinement.Worked<String> shortOfAPosition = readWithRoomFor(TOO_LITTLE);

        Confinement.Conjoined<String> took = Confinement.Conjoined.<String>top()
                .taking(shortOfAPosition, AsACompilationAllows.forAdmittedValues());

        assertEquals(souther.compiler.values.Emptiness.UNDECIDED, took.admits(),
                "a conjunction of one reading answers what that reading answers");
    }
}
