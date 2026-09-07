package souther.compiler.values;

import org.junit.jupiter.api.Test;

import souther.compiler.regex.PatternParser;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternRead;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two readings that hold the same sets at the same positions are not the same reading.
 *
 * <p>What each position holds is a projection of the alternatives, and two different sets of
 * alternatives project to it: {@code x} with {@code y} beside {@code p} with {@code q} projects to
 * the same two positions as {@code x} with {@code q} beside {@code p} with {@code y}. The pairs are
 * what a meet of two readings builds — one for every alternative against every alternative — so
 * which pairs there are is most of what the meet costs.
 *
 * <p>Left out of the order the work is done in, those two readings were one thing: a sort keeps
 * equal things where it found them, so the tie fell back to the order they arrived in, which is the
 * one thing the order exists to be independent of.
 */
class HowAlternativesRelateTwoPositionsIsPartOfWhatAReadingCostsTest {

    private static ValueSet matching(String regex) {
        PatternRead said = PatternParser.read(regex);
        return ValueSet.matching(PatternPlan.of(
                assertInstanceOf(PatternRead.Read.class, said, regex).syntax())
                .compile(PatternPlan.Budget.OF_ADMITTED_VALUES.meter()));
    }

    /** One alternative: {@code left} holds one language and {@code right} holds the other, while
     *  it is still a description. */
    private static PlannedValues<String> pair(ValueSet left, ValueSet right) {
        return PlannedValues.at("left", AdmittedPlan.of(left))
                .meet(PlannedValues.at("right", AdmittedPlan.of(right)));
    }

    /** Two alternatives held apart, which is a reading whose positions are related by its boxes. */
    private static AdmissibleValues<String> related(boolean crossed, Allowance<String> by) {
        ValueSet a = matching("x|a{300}");
        ValueSet b = matching("x|b{300}");
        ValueSet c = matching("x|c{300}");
        ValueSet d = matching("x|d{300}");
        return (crossed
                // Every alternative here was read whole, so the choice left nothing open.
                ? pair(a, d).joinLiveApart(pair(c, b))
                : pair(a, b).joinLiveApart(pair(c, d)))
                .resolve(by).values();
    }

    /** The same positions, the same sets, and the pairs the other way round. */
    @Test
    void twoWaysOfRelatingTheSameSetsAreTwoReadings() {
        Allowance<String> by = AsACompilationAllows.forAdmittedValues();
        AdmissibleValues<String> straight = related(false, by);
        AdmissibleValues<String> crossed = related(true, by);

        assertEquals(straight.at("left"), crossed.at("left"), "the same values at one position");
        assertEquals(straight.at("right"), crossed.at("right"), "and at the other");
        // Asked as a boolean, since what a key says is only whether two of them are the same one.
        assertTrue(!PlanOrder.of(straight).equals(PlanOrder.of(crossed)),
                "and they are still two readings, so the work order tells them apart");
    }

    /**
     * And what several of them cost does not turn on which arrived first.
     *
     * <p>The pair above beside a third reading, in every order. What is asserted is the whole
     * contract: the same readings leave the same values, say the same thing about themselves, and
     * spend the same.
     */
    @Test
    void everyArrivalOrderOfTheSameThreeCostsTheSame() {
        ValueSet first = null;
        int spent = -1;
        for (List<Integer> order : List.of(List.of(0, 1, 2), List.of(0, 2, 1), List.of(1, 0, 2),
                List.of(1, 2, 0), List.of(2, 0, 1), List.of(2, 1, 0))) {
            Allowance<String> by = AsACompilationAllows.forAdmittedValues();
            List<AdmissibleValues<String>> read = List.of(related(false, by), related(true, by),
                    PlannedValues.at("left", AdmittedPlan.of(ValueSet.just(Value.text("x"))))
                            .resolve(by).values());
            AdmissibleValues<String> made =
                    AdmissibleValues.metAll(order.stream().map(read::get).toList(), by);

            if (first == null) {
                first = made.at("left");
                spent = spentOn(by);
                continue;
            }
            assertEquals(first, made.at("left"), "the values, arriving as " + order);
            assertEquals(spent, spentOn(by), "and the states, arriving as " + order);
        }
        assertTrue(spent > 0, "something was built, or this is measuring nothing");
    }

    /** How much of one position's allowance has gone. */
    private static int spentOn(Allowance<String> by) {
        return PatternPlan.Budget.OF_ADMITTED_VALUES.mostBuilt()
                - by.left(Sameness.Block.of("left"));
    }
}
