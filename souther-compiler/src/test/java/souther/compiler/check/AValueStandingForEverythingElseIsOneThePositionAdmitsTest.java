package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.Text;
import souther.compiler.regex.CodePoints;
import souther.compiler.regex.Meter;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternSyntax;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The value standing for everything but the ones singled out is one the position holds.
 *
 * <p>A position is read in two vocabularies — the values that stand there, and the numbers taken of
 * them — and a rule lands in one of them. Worked out from the ends of the number alone, the value
 * is offered without the rules about the values ever reaching the choice: a string bounded below by
 * its length is offered the empty string, and what says so is a construction that fails somewhere
 * else rather than the set it was picked from.
 *
 * <p>Asked here, where the choice is made. A model asking for the rows nothing covers goes through
 * the reading of the declarations, the measure, the search and the rendering, so what it says when
 * it fails is that one of those did — and the combinations below are what tells them apart.
 */
class AValueStandingForEverythingElseIsOneThePositionAdmitsTest {

    /** What writing one value out is allowed to cost, which is what the caller of this spends. */
    private static Meter meter() {
        return PatternPlan.Budget.OF_A_WITNESS.meter();
    }

    /** The strings a rule about how many a value holds leaves, as the position admits them. */
    private static ValueSet lengths(int least, int most) {
        return ValueSet.matching(PatternPlan.of(new PatternSyntax.Repeated(
                        new PatternSyntax.Symbols(CodePoints.EVERYTHING), least, most))
                .compile(meter()));
    }

    private static Place otherThan(Carrier carrier, List<Place> singled,
                                   NumericDomain.Bounds within, ValueSet admits) {
        return carrier.somethingOtherThan(singled, within, admits, meter());
    }

    /**
     * A rule on the length reaches the choice, and the empty string is not what is offered.
     *
     * <p>The value the order has to offer for a string is its least, and the rules about the
     * number the strings are counted on say nothing about how many a value holds. So this is the
     * case where reading one vocabulary answers with a value the other refuses.
     */
    @Test
    void aRuleOnTheLengthTakesTheEmptyStringOutOfTheChoice() {
        Place at = otherThan(Carrier.TEXT, List.of(Text.of("spring")), null,
                lengths(1, PatternSyntax.Repeated.NO_CEILING));

        assertNotNull(at, "every string of a length the rule allows but `spring` stands for this");
        assertNotEquals("", Carrier.TEXT.written(at),
                "nothing of no length is admitted, so nothing of no length stands for the class");
    }

    /** And a rule with both ends, so it is the run the rule leaves that is read and not one end. */
    @Test
    void aBandOnTheLengthLeavesOnlyTheStringsInsideIt() {
        Place at = otherThan(Carrier.TEXT, List.of(Text.of("spring")), null, lengths(2, 3));

        assertNotNull(at);
        int held = Carrier.TEXT.written(at).codePointCount(0, Carrier.TEXT.written(at).length());
        assertTrue(held >= 2 && held <= 3,
                () -> "a value of a length the rule allows: " + Carrier.TEXT.written(at));
    }

    /**
     * The value singled out being the one the set itself has first to offer.
     *
     * <p>The case the two orders of the operation come apart at. Taken out of the set before the
     * value is looked for, the strings left are without end and one of them stands for the class;
     * looked for first and refused afterwards, there is nothing left to offer and a class the
     * declarations leave inhabited says nothing stands for it.
     *
     * <p>Which string that is is not written down here. What the set has first to offer is the
     * thing under test, so a test naming it would go green the day the choice moved and the
     * refusal came back.
     */
    @Test
    void theValueTheSetOffersFirstBeingSingledOutIsNotTheEndOfTheChoice() {
        ValueSet admits = lengths(1, PatternSyntax.Repeated.NO_CEILING);
        Place first = Carrier.TEXT.somewhereIn(admits);
        assertNotNull(first, "the set has a value to offer, which is what this case is about");

        Place at = otherThan(Carrier.TEXT, List.of(first), null, admits);

        assertNotNull(at, "every string of a length the rule allows but that one stands for this");
        assertNotEquals(Carrier.TEXT.written(first), Carrier.TEXT.written(at),
                "the value singled out is the one the class exists to exclude");
    }

    /**
     * A position nothing says anything about keeps the least string.
     *
     * <p>Every value of the carrier but the ones singled out, which is what the rules leave where
     * they say nothing — and the empty string is under every other, so it is what stands for the
     * class wherever it is not itself singled out.
     */
    @Test
    void aStringNothingBoundsIsOfferedTheLeastOne() {
        assertEquals("", Carrier.TEXT.written(
                        otherThan(Carrier.TEXT, List.of(Text.of("spring")), null, ValueSet.ANY)),
                "the least string there is, and not one this made up");
        assertNotEquals("", Carrier.TEXT.written(
                        otherThan(Carrier.TEXT, List.of(Text.of("")), null, ValueSet.ANY)),
                "and where that one is singled out, the strings above it are still the class");
    }

    /**
     * A set that names what it holds out is read on the order that counts them.
     *
     * <p>A rule the declarations write as {@code /=} leaves every value of the carrier but the one
     * it names, and which values those are is the order's answer. Read off the set instead, the
     * value offered for a position counting numbers was a string.
     */
    @Test
    void aNumberHeldOutByTheDeclarationsIsNotOfferedForTheClass() {
        Place at = otherThan(Carrier.WHOLE, List.of(Count.of(0)),
                null, new ValueSet.Cofinite(Set.of(Value.number(1), Value.number(-1))));

        assertNotNull(at, "the numbers away from zero that the rule leaves are without end");
        assertTrue(Carrier.WHOLE.written(at).equals("2") || Carrier.WHOLE.written(at).equals("-2"),
                () -> "one step past what the rule holds out: " + Carrier.WHOLE.written(at));
    }

    /** And both vocabularies together, so neither is answering for the other. */
    @Test
    void whatTheRangeLeavesAndWhatTheValuesLeaveAreBothAsked() {
        Place at = otherThan(Carrier.WHOLE, List.of(Count.of(1)),
                new NumericDomain.Bounds(Endpoint.inclusive(Count.of(0)),
                        Endpoint.inclusive(Count.of(3))),
                new ValueSet.Cofinite(Set.of(Value.number(0), Value.number(2))));

        assertEquals("3", Carrier.WHOLE.written(at),
                "zero and two are refused by the declarations, one is singled out, four is outside"
                        + " the range");
    }
}
