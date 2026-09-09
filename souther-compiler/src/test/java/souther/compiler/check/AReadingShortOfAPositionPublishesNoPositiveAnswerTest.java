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

    /**
     * And to a conjunction met with that one, from either side.
     *
     * <p>Two conjunctions are met where a caller holds the readings of two declarations and wants
     * what they come to, and the one that took the short reading in is the only one carrying the
     * reason to doubt. Kept on the side it arrived on, which side the caller wrote first would
     * settle whether the answer is one anybody worked out.
     */
    @Test
    void andToAConjunctionMetWithThatOneFromEitherSide() {
        Confinement.Conjoined<String> took = Confinement.Conjoined.<String>top()
                .taking(readWithRoomFor(TOO_LITTLE), AsACompilationAllows.forAdmittedValues());
        Confinement.Conjoined<String> nothingRead = Confinement.Conjoined.top();

        assertEquals(souther.compiler.values.Emptiness.UNDECIDED,
                took.meet(nothingRead, AsACompilationAllows.forAdmittedValues()).admits());
        assertEquals(souther.compiler.values.Emptiness.UNDECIDED,
                nothingRead.meet(took, AsACompilationAllows.forAdmittedValues()).admits());
    }

    /**
     * And to the same conjunction said again, however it was said again.
     *
     * <p>The two ways one of these is made without a reading arriving: the same rules under other
     * names, and the same rules with a range taken as holding of the positions it bounds. Neither
     * asks anything of the readings, so neither can have worked out what one of them could not
     * work out — and a conjunction that answered positively after being renamed would be one whose
     * answer turned on having been carried across a boundary.
     */
    @Test
    void andToTheSameConjunctionSaidAgain() {
        Confinement.Conjoined<String> took = Confinement.Conjoined.<String>top()
                .taking(readWithRoomFor(TOO_LITTLE), AsACompilationAllows.forAdmittedValues());

        assertEquals(souther.compiler.values.Emptiness.UNDECIDED,
                took.renamed(position -> position).admits(),
                "renaming the positions works nothing out");
        assertEquals(souther.compiler.values.Emptiness.UNDECIDED,
                took.taking(OrderedIntervals.top(), Map.of()).admits(),
                "and neither does taking a range as holding of them");
    }
}
