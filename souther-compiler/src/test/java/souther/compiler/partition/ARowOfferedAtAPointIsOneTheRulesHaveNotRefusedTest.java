package souther.compiler.partition;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A row offered at a point of a form is one the rules have not been shown to refuse.
 *
 * <p>The search narrows what it walks by reading the rules again as it fixes positions, and how much
 * of that it is willing to pay for is its own affair — past a budget it walks the wider box the
 * ranges alone describe, which holds every assignment the rules leave and some they do not. Giving
 * that up is giving up precision.
 *
 * <p>What it may not give up is the answer it hands back. An assignment picked out of the wider box
 * and never held against the rules is one the record can refuse, and a row offered there is refused
 * where it is built — which a report says as every value having been tried, of a point some other
 * pair stands at perfectly well. That is the defect this whole reading exists to remove, arriving by
 * way of a budget rather than by way of a missing question.
 *
 * <p>So the narrowing is budgeted and the last step is not: an assignment is handed back only where
 * the rules, with every position of it fixed, were not shown to leave nothing. Where that cannot be
 * said the search has not settled the point, which is a different sentence from having tried.
 *
 * <p>The box here is wide enough that the narrowing runs out well before the walk does, which is
 * what the smaller models in this package do not reach.
 */
class ARowOfferedAtAPointIsOneTheRulesHaveNotRefusedTest {

    /**
     * Two fields the record holds equal, each running to ten thousand.
     *
     * <p>The only pair whose sum is ten thousand is five thousand each. Every other pair of that sum
     * is inside the box the two ranges describe and outside what the record leaves, and the walk
     * meets thousands of them on the way.
     */
    private static final String EQUAL_HALVES = """
            module example.halves

            data N = Int
                invariant atLeastNone = value >= 0
                invariant atMostTenThousand = value <= 10000

            data P = { x: N, y: N }
                invariant sameAsEachOther = x.value == y.value

            data No = { why: Int }
            data Yes = { v: Int }
            data Result = No | Yes

            behavior f : (p: P) -> Result
                constructs Yes, No
            let f (p) = {
                guard Int.add(p.x.value, p.y.value) <= 10000 else No { why = 0 }
                Yes { v = 1 }
            }

            example f
                | "under" : (P { x = N(1), y = N(1) }) -> Yes { v = 1 }
            """;

    /**
     * The ON point of the guard's line is at a sum of ten thousand, which one pair stands at.
     *
     * <p>What may not be said of it is that every value tried there was refused. Either a row is
     * offered — and then it is a pair the rules leave — or the search says it did not settle the
     * point.
     */
    @Test
    void aPointIsNotReportedAsRefusedOnTheStrengthOfPairsTheRulesRefuse() {
        String report = report(EQUAL_HALVES);

        assertFalse(report.contains("every value tried at p.x + p.y = 10000 was refused"), report);
    }

    /**
     * Two positions held a distance apart, whose ranges overlap where the pair cannot stand.
     *
     * <p>A line between two positions is met by fixing both at once, and each is fixed inside its
     * own ends — which is every range the pair could have and not the rule that relates them. Their
     * sum being seven leaves each of them between two and five, so a place both admit is easy to
     * find and the pair standing at it adds to four.
     */
    private static final String SUMMING_TO_SEVEN = """
            module example.seven

            data N = Int
                invariant atLeastNone = value >= 0
                invariant atMostFive  = value <= 5

            data P = { x: N, y: N }
                invariant seven = x.value + y.value == 7

            data No = { why: Int }
            data Yes = { v: Int }
            data Result = No | Yes

            behavior f : (p: P) -> Result
                constructs Yes, No
            let f (p) = {
                guard p.x.value > p.y.value else No { why = 0 }
                Yes { v = 1 }
            }

            example f
                | "over" : (P { x = N(5), y = N(2) }) -> Yes { v = 1 }
            """;

    /**
     * And a pair the rules refuse is not offered as a row either.
     *
     * <p>The line here is where the two are equal, and no pair adding to seven stands on it. What
     * may not happen is a pair inside both ranges being offered all the same and coming back refused
     * where it is built, which reads as the point having been tried.
     */
    @Test
    void aPairTheRulesRefuseIsNotOfferedAsARowEither() {
        String report = report(SUMMING_TO_SEVEN);

        assertFalse(report.contains("every value tried at f/p.x = p.y was refused"), report);
        assertFalse(report.contains("every value tried at p.x = p.y was refused"), report);
    }

    /**
     * One position, whose range holds a value the rules leave nothing at.
     *
     * <p>A range says where a position's values stop and not which of them it holds. Two fields the
     * record holds equal, one of them refused the value one, leave the other running from none to
     * two with nothing at one — and a line drawn there is met by a value the range admits and no
     * value of the record has.
     */
    private static final String A_HOLE_IN_THE_RANGE = """
            module example.hole

            data N = Int
                invariant atLeastNone = value >= 0
                invariant atMostTwo   = value <= 2

            data P = { x: N, y: N }
                invariant sameAsEachOther = x.value == y.value
                invariant notOne = y.value /= 1

            data No = { why: Int }
            data Yes = { v: Int }
            data Result = No | Yes

            behavior f : (p: P) -> Result
                constructs Yes, No
            let f (p) = {
                guard p.x.value <= 1 else No { why = 0 }
                Yes { v = 1 }
            }

            example f
                | "at none" : (P { x = N(0), y = N(0) }) -> Yes { v = 1 }
            """;

    /**
     * And a value at one position that the rules leave nothing at is not offered as a row.
     *
     * <p>The same last step the pair and the form already get. What a range admits is where the
     * values stop, and a rule can take one out of the middle without moving either end.
     */
    @Test
    void aValueTheRangeAdmitsAndTheRulesDoNotIsNotOfferedAsARow() {
        String report = report(A_HOLE_IN_THE_RANGE);

        assertFalse(report.contains("every value tried at f/p.x = 1 was refused"), report);
        assertFalse(report.contains("every value tried at p.x = 1 was refused"), report);
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
