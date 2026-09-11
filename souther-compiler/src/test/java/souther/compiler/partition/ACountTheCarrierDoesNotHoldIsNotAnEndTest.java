package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Carrier;
import souther.compiler.inputs.InputDomain;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.DateTimes;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Which counts a carrier holds is the carrier's to say, and every producer of an end asks it.
 *
 * <p>A strict comparison over a carrier that steps is sharpened onto the count beside the one it
 * names: {@code value > 5} is an end at 6. Where the carrier has no count there, there is no end to
 * record — and the reader that sharpens it had its own idea of where counts stop, the range of a
 * {@code long}, which is a fact about one carrier out of five.
 *
 * <p>What that cost: {@code value > Won} sharpened to the place after the last case, which is inside
 * a {@code long} and outside the enumeration. The end became a cut, the cut an obligation, and the
 * obligation was asked for the value it stands for — a case at a place that has none. A `Date` past
 * the last day the calendar has does the same and did so before any of this.
 *
 * <p>The other half of the same rule is that holding a count is a question and not a correction. A
 * date-time's counts sit on a grid at the nanosecond, and what writes one rounds onto that grid — so
 * a reader that asked whether a carrier holds a count and was handed the nearest one it does hold
 * would offer a value between two moments as one of them.
 */
class ACountTheCarrierDoesNotHoldIsNotAnEndTest {

    private static final String STAGE = """
            data Prospecting
            data Qualified
            data Won
            data Stage = Prospecting | Qualified | Won

            data Ok
            data No
            data Verdict = Ok | No
            """;

    @Test
    void aBoundPastTheLastCaseOfAnEnumerationLeavesNoEnd() {
        assertEquals(List.of(), obligations("module example.past\n" + STAGE + """
                data PastEnd = Stage
                    invariant value > Won

                behavior f : (s: PastEnd) -> Verdict
                let f (s) = Ok
                """));
    }

    @Test
    void aBoundBeforeTheFirstCaseOfAnEnumerationLeavesNoEnd() {
        assertEquals(List.of(), obligations("module example.before\n" + STAGE + """
                data BeforeStart = Stage
                    invariant value < Prospecting

                behavior f : (s: BeforeStart) -> Verdict
                let f (s) = Ok
                """));
    }

    /** The same at the end of the calendar, which behaved this way before an enumeration was a
     * carrier: the reader that sharpened the bound stopped where an {@code Int} stops. */
    @Test
    void aBoundPastTheLastDayTheCalendarHasLeavesNoEnd() {
        assertEquals(List.of(), obligations("""
                module example.pastdate

                data Ok
                data No
                data Verdict = Ok | No

                data PastDate = Date
                    invariant value > Date("+999999999-12-31")

                behavior f : (s: PastDate) -> Verdict
                let f (s) = Ok
                """));
    }

    /** And a bound the carrier does hold is still an end, so none of the above passes by drawing
     * nothing anywhere. */
    @Test
    void aBoundInsideTheCasesIsAnEnd() {
        assertEquals(List.of("ON Qualified"), obligations("module example.inside\n" + STAGE + """
                data FromQualified = Stage
                    invariant value >= Qualified

                behavior f : (s: FromQualified) -> Verdict
                let f (s) = Ok
                """));
    }

    /**
     * Halfway between two adjacent moments is a number and not a moment.
     *
     * <p>Null rather than the moment it would be written as. Answered with the nearest one, a class
     * open between two adjacent moments was offered a value at one of its own ends.
     *
     * <p>The grid is the second, which is what a `DateTime` is held to
     * (spec §a-local-temporal-is-held-to-the-second). Any fraction of one is off it, so what the
     * nanosecond counts here used to demonstrate one unit down is demonstrated by a half-second.
     */
    @Test
    void aCountBetweenTwoMomentsIsNotOneOfThem() {
        assertNull(Carrier.MOMENT.onTheGrid(Count.of(new BigDecimal("0.5"))));
        assertNull(Carrier.MOMENT.onTheGrid(Count.of(new BigDecimal("0.000000001"))));
        assertEquals(Count.of(new BigDecimal("1")),
                Carrier.MOMENT.onTheGrid(Count.of(new BigDecimal("1"))));
    }

    /**
     * And a count past where the calendar stops is not a moment either.
     *
     * <p>Answered rather than thrown. A date-time is the one carrier whose counts are both bounded
     * and spaced, and the bound was left to the writer — which answers a count it cannot write by
     * throwing, out of a question whose whole job is to answer no.
     */
    @Test
    void aCountPastWhereTheCalendarStopsIsNotAMoment() {
        Count nanosecond = Count.of(new BigDecimal("0.000000001"));

        assertEquals(DateTimes.MAX, Carrier.MOMENT.onTheGrid(DateTimes.MAX));
        assertNull(Carrier.MOMENT.onTheGrid(DateTimes.MAX.plus(1)));
        assertNull(Carrier.MOMENT.onTheGrid(Count.of(DateTimes.MAX.at().add(nanosecond.at()))));

        assertEquals(DateTimes.MIN, Carrier.MOMENT.onTheGrid(DateTimes.MIN));
        assertNull(Carrier.MOMENT.onTheGrid(DateTimes.MIN.minus(1)));
        assertNull(Carrier.MOMENT.onTheGrid(Count.of(DateTimes.MIN.at().subtract(nanosecond.at()))));
    }

    /**
     * A rule past the last moment leaves a position with no value to stand for it, and says so.
     *
     * <p>The reachable half. A strict bound over a dense carrier stays on the count it names, which
     * is a moment and legal — so nothing is wrong until something looks inside the range it leaves,
     * one nanosecond past the end of the calendar. Reached through the value that stands for the
     * position rather than through a boundary, and swallowed on the way out: the report came back
     * missing its partition and boundary lines altogether, with nothing said about why.
     */
    @Test
    void aRuleBeyondTheLastMomentOffersNothingFromInsideItRatherThanThrowing() {
        // What the type wraps still stands for it — a candidate is a proposal the decoder answers,
        // and this one is refused there. What is gone is the value from inside the range, which is
        // where a count past the calendar was being written from.
        assertEquals(List.of("PastMoment(DateTime(\"2000-01-01T00:00:00\"))"), representatives("""
                module example.pastmoment

                data PastMoment = DateTime
                    invariant value > DateTime("+999999999-12-31T23:59:59.999999999")
                """, "PastMoment"));
        assertEquals(List.of("BeforeMoment(DateTime(\"2000-01-01T00:00:00\"))"), representatives("""
                module example.beforemoment

                data BeforeMoment = DateTime
                    invariant value < DateTime("-999999999-01-01T00:00:00")
                """, "BeforeMoment"));
    }

    /** And a range inside the calendar still gives up a value from inside itself, so the two above
     * are not short of one for some other reason. */
    @Test
    void aRuleInsideTheCalendarStillOffersAValueFromInsideIt() {
        assertEquals(List.of("Ordinary(DateTime(\"2026-01-01T00:00:00\"))",
                        "Ordinary(DateTime(\"2000-01-01T00:00:00\"))"),
                representatives("""
                        module example.ordinary

                        data Ordinary = DateTime
                            invariant value > DateTime("2025-12-31T23:59:59")
                        """, "Ordinary"));
    }

    /** What stands for a position of {@code name}, written. */
    private static List<String> representatives(String source, String name) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return Partitions.representativesOf(
                        souther.compiler.types.Type.ref(
                                souther.compiler.types.TypeSymbols.declared(new souther.compiler.types.TypeKey(module, name))),
                        RuleReadingContext.unshared(rules,
                                souther.compiler.query.ReadAs.THE_COMPILATION_DOES))
                .stream().map(FixtureTemplate::text).toList();
    }

    /** Every obligation the one position of {@code source}'s behavior owes, written. */
    private static List<String> obligations(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        assertFalse(sigs.isEmpty(), "the model under test compiles");
        Map.Entry<String, DeclaredSig> declared = sigs.entrySet().iterator().next();
        InputDomain domain = InputDomain.of(declared.getValue(), rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        souther.compiler.inputs.Quantities reading = domain.quantities(rules);
        Partitions.Partitioning p = Partitions.of(declared.getKey(), domain, rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        return p.axes().stream()
                .flatMap(axis -> Partitions.bordersOf(axis, reading,
                        reading.runsBetween(axis.term()), new LinesRead()).stream())
                .flatMap(border -> java.util.stream.Stream.of(PointRole.ON, PointRole.OFF)
                        .filter(role -> border.demand(role).criterion() != null)
                        .map(role -> role + " "
                                + border.demand(role).criterion().asked(border.cut().of()).substring(2)))
                .toList();
    }
}
