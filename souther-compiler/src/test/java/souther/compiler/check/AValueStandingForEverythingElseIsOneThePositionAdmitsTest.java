package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.Text;
import souther.compiler.regex.CodePoints;
import souther.compiler.regex.Meter;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternSyntax;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        Place first = Carrier.TEXT.somewhereIn(admits, new OrderedInterval(null, null),
                List.of(), meter());
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
        assertEquals("", written(otherThan(Carrier.TEXT, List.of(Text.of("spring")), null,
                        ValueSet.ANY)),
                "the least string there is, and not one this made up");

        Place above = otherThan(Carrier.TEXT, List.of(Text.of("")), null, ValueSet.ANY);

        assertNotNull(above,
                "where the least string is the one singled out, the strings above it are the class");
        assertNotEquals("", written(above));
    }

    /**
     * What a place is written as, and null where there is no place.
     *
     * <p>Never the whole of what a case asks. A class with nothing standing for it is the answer
     * under test here, so a comparison against a null would be a case that goes green by the value
     * having gone missing — every use of this is beside something that says a value was composed.
     */
    private static String written(Place at) {
        return at == null ? null : Carrier.TEXT.written(at);
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

    /**
     * The run is what the values are looked through, and not what the value found is put to.
     *
     * <p>The set names a value outside the run and a value inside it, and the one outside comes
     * first. Looked through, the run reaches the second; put to the first afterwards, the search
     * stops at a value the range refuses and the class is left with nothing standing for it —
     * while the position holds one the whole time.
     *
     * <p>The order composes no candidate here: every step from the value singled out and every end
     * of the run is a value the declarations refuse, so what answers is the question asked of the
     * values themselves.
     */
    @Test
    void theRunIsWhatTheValuesAreLookedThroughAndNotWhatIsPutToTheOneFound() {
        Set<Value> named = new LinkedHashSet<>();
        named.add(Value.number(100));   // the first the set names, and outside the run
        named.add(Value.number(14));    // the one the position holds
        named.add(Value.number(12));    // singled out by the body

        Place at = otherThan(Carrier.WHOLE, List.of(Count.of(12)),
                new NumericDomain.Bounds(Endpoint.inclusive(Count.of(10)),
                        Endpoint.inclusive(Count.of(20))),
                new ValueSet.Finite(named));

        assertNotNull(at, "the position holds a value away from the one singled out");
        assertEquals("14", Carrier.WHOLE.written(at),
                "the one value of the set inside the run that the body did not single out");
    }

    /**
     * An order with no step is not walked through by counting.
     *
     * <p>Where the values step, the two lists name finitely many and a walk one longer than that
     * reaches a value neither names. A decimal never runs out that way: the run between two numbers
     * holds numbers without end, and counting from an end of it by ones leaves it after the first
     * step. So the stretches the named values leave are what is looked at — {@code (0, 0.25)} here,
     * and three more — and each of them is a run the order can give a value up from.
     *
     * <p>Every value the candidates before it compose is named: the middle of the run is the one
     * singled out, the middles either side of it are the two the declarations refuse, and a step of
     * one from any of them is outside the run.
     */
    @Test
    void anOrderWithNoStepIsLookedThroughByTheStretchesTheNamedValuesLeave() {
        Place at = otherThan(Carrier.DENSE, List.of(Count.of(new BigDecimal("0.5"))),
                new NumericDomain.Bounds(Endpoint.exclusive(Count.of(0)),
                        Endpoint.exclusive(Count.of(1))),
                new ValueSet.Cofinite(Set.of(Value.number(new BigDecimal("0.25")),
                        Value.number(new BigDecimal("0.75")))));

        assertNotNull(at, "the numbers between nought and one that the rules leave are without end");
        assertTrue(at.compareTo(Count.of(0)) > 0 && at.compareTo(Count.of(1)) < 0,
                () -> "inside the run: " + Carrier.DENSE.written(at));
        assertFalse(List.of("0.25", "0.5", "0.75").contains(Carrier.DENSE.written(at)),
                () -> "and none of the three the rules and the body name: "
                        + Carrier.DENSE.written(at));
    }

    /**
     * The same order with nothing bounding it, where the stretches run to the ends of the order.
     *
     * <p>Every value ruled out is inside the run here, so the stretch below the least of them and
     * the stretch above the greatest are the ones with no end of their own. What the case is about
     * is that those are runs the order gives a value up from like any other.
     */
    @Test
    void theStretchesWithNoEndOfTheirOwnGiveUpAValueToo() {
        Place at = otherThan(Carrier.DENSE, List.of(Count.of(new BigDecimal("0.5"))), null,
                new ValueSet.Cofinite(Set.of(Value.number(new BigDecimal("1.5")),
                        Value.number(new BigDecimal("-0.5")))));

        assertNotNull(at, "the numbers away from those three are without end");
        assertFalse(List.of("-0.5", "0.5", "1.5").contains(Carrier.DENSE.written(at)),
                () -> "and none of the three: " + Carrier.DENSE.written(at));
    }

    /**
     * A language, a run of the order and a value singled out, met before the string is taken.
     *
     * <p>The three come from three readings and none of them has a word for what the others hold:
     * a rule about how many a value holds names no ends, a bound on the order names no set, and a
     * value a body singled out is neither. This is where they are put together, and the string that
     * comes back has to satisfy all three at once.
     */
    @Test
    void aLanguageAndARunOfTheOrderAndAValueSingledOutAreMetBeforeTheStringIsTaken() {
        Place at = otherThan(Carrier.TEXT, List.of(Text.of("m")),
                new NumericDomain.Bounds(Endpoint.inclusive(Text.of("m")),
                        Endpoint.exclusive(Text.of("n"))),
                lengths(1, PatternSyntax.Repeated.NO_CEILING));

        assertNotNull(at, "the strings from `m` up to `n` that are not `m` itself are without end");
        String some = Carrier.TEXT.written(at);
        assertTrue(some.compareTo("m") > 0 && some.compareTo("n") < 0,
                () -> "inside the run: " + some);
        assertFalse(some.isEmpty(), () -> "and of a length the rule allows: " + some);
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
