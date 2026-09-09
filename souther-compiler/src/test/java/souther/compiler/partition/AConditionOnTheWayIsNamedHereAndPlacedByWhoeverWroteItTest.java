package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Citation;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Sites;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What tells one condition on the way to a border from another, and what sends a reader to it, are
 * two things.
 *
 * <p>They were one. A condition carried where it is written, and for a condition the walk could not
 * turn into a cut that place was the only thing telling it from its neighbours — what else it
 * carries is why it was declined, and two conditions declined for one reason are one reason. So the
 * place was doing identity's work because nothing else was doing it, and an edit that moved a
 * helper's condition without changing anything it says changed every answer about it.
 *
 * <p>Both halves are checked here, and the second is what says the first bought anything: the
 * account comes out the same over source that says the same thing in a different place, and the
 * report still points at the condition.
 */
class AConditionOnTheWayIsNamedHereAndPlacedByWhoeverWroteItTest {

    /**
     * Two forks on a truth this reading has no words for, one inside the other.
     *
     * <p>A {@code Bool} that is not a comparison is where {@link Condition} stops, so each of these
     * is declined for the one reason — which is what makes the pair the thing under test. Innermost
     * is a fork on a comparison, so the account of what stands on the way to the arm under it holds
     * one of each: two conditions this reading has no words for, and one it took in.
     */
    private static String model(String beforeTheBody) {
        return """
                module example.way

                behavior twoUnread : (a: Bool, b: Bool, n: Int) -> Bool
                """
                + beforeTheBody
                + """
                let twoUnread (a, b, n) =
                    if a then
                        if b then
                            if n > 3 then n > 0 else n < 0
                        else
                            n < 0
                    else
                        n > 5
                """;
    }

    /**
     * Two conditions declined for one reason are two values.
     *
     * <p>One account holds both, so a reading that told them apart by nothing would be holding one
     * value twice — and a report about the way to that comparison would say one thing where the
     * model says two.
     */
    @Test
    void twoConditionsDeclinedForOneReasonAreTwoValues() {
        List<OnTheWay.Declined> declined = new ArrayList<>();
        for (OnTheWay each : longestWay(compiled(""))) {
            if (each instanceof OnTheWay.Declined left) {
                declined.add(left);
            }
        }
        assertEquals(2, declined.size(), () -> "both forks are on the way: " + declined);
        assertEquals(List.of(new OnTheWay.Why.NoWordsForTheShape(),
                        new OnTheWay.Why.NoWordsForTheShape()),
                declined.stream().map(OnTheWay.Declined::why).toList(),
                "and for the one reason, which is what leaves the name doing the telling apart");
        assertNotEquals(declined.get(0), declined.get(1),
                () -> "two conditions this compiler tells apart: " + declined);
    }

    /**
     * And nothing on the way is told from its neighbours by where a report would point.
     *
     * <p>Written out because the fault it guards against is invisible otherwise. A carrier that
     * carried the anchor and nothing else would pass the test above while the anchor was doing what
     * the place used to do, one name further along.
     */
    @Test
    void whatTellsThemApartIsTheirNameAndNotWhereAReportWouldPoint() {
        List<OnTheWay> way = longestWay(compiled(""));
        Set<ConditionReportAnchor> anchors = new LinkedHashSet<>();
        way.forEach(each -> anchors.add(each.anchor()));
        assertEquals(way.size(), anchors.size(),
                () -> "each of these is somewhere of its own here, so this says nothing yet: " + way);
        assertEquals(way.size(), new LinkedHashSet<>(way).size(),
                () -> "and each is its own value: " + way);
    }

    /**
     * A condition the source wrote a construct of is placed by the module that wrote it.
     *
     * <p>The comparisons are, and the forks on a truth are not: what the source wrote at one of
     * those is any expression at all, and nothing files a place under a name this could ask by. A
     * fallback rather than a switch would report the first at wherever a reading met a copy of it.
     */
    @Test
    void whichQuestionPlacesItIsSettledByWhetherTheSourceWroteAConstruct() {
        List<OnTheWay> way = longestWay(compiled(""));
        List<String> asked = new ArrayList<>();
        for (OnTheWay each : way) {
            asked.add(switch (each.anchor()) {
                case ConditionReportAnchor.WhereItIsWritten _ -> "whoever wrote it";
                case ConditionReportAnchor.WhereTheReadingMetIt _ -> "the reading that met it";
            });
        }
        assertEquals(List.of("the reading that met it", "the reading that met it",
                        "whoever wrote it"),
                asked.stream().sorted().toList(),
                () -> "the two forks on a truth are the reading's, and the comparison is the"
                        + " writing module's: " + way);
    }

    /**
     * Source that says the same thing in another place says the same thing.
     *
     * <p>The whole of what the change bought. What an answer holds is which condition it is, so
     * moving the body leaves every value on the account equal — and a reader is still sent to the
     * condition, because where it is is asked rather than remembered.
     */
    @Test
    void movingTheBodyLeavesTheAccountAndMovesWhereAReportPoints() {
        Compilation where = compiled("");
        Compilation moved = compiled("\n\n");

        assertEquals(longestWay(where), longestWay(moved),
                "an edit that moves a condition and changes nothing it says changes no answer");

        List<Citation> before = placesOf(where);
        List<Citation> after = placesOf(moved);
        assertEquals(before.size(), after.size(), "the same conditions are on the way");
        for (int i = 0; i < before.size(); i++) {
            assertNotEquals(before.get(i), after.get(i),
                    "a reader is sent to where the condition is now, and it has moved");
        }
    }

    /** Where a report about each condition on the way points, in the order the way carries them. */
    private static List<Citation> placesOf(Compilation compilation) {
        List<Citation> out = new ArrayList<>();
        for (OnTheWay each : longestWay(compilation)) {
            out.add(Sites.placeOf(compilation.db(), each.anchor()));
        }
        return out;
    }

    /**
     * The longest account this reading filed, which is the way to the comparison under both forks.
     *
     * <p>Named by what is on it rather than by which comparison it belongs to. Which comparison a
     * walk files first is the walk's business, and asking for one of them by name would be this
     * check holding the model to the order a map came back in.
     */
    private static List<OnTheWay> longestWay(Compilation compilation) {
        ReachingCuts reaching = reachingIn(compilation);
        List<OnTheWay> longest = List.of();
        for (List<OnTheWay> each : reaching.byComparison().values()) {
            if (each.size() > longest.size()) {
                longest = each;
            }
        }
        List<OnTheWay> found = longest;
        assertEquals(3, found.size(),
                () -> "two forks and the comparison the inner arm stands under: " + found);
        return found;
    }

    private static ReachingCuts reachingIn(Compilation compilation) {
        String module = compilation.modules().get(0);
        Partitions.Partitioning divided =
                compilation.db().ask(new Adequacy.Divided(module, "twoUnread")).value();
        assertNotNull(divided, "the model under test is measured");
        return divided.reaching();
    }

    private static Compilation compiled(String beforeTheBody) {
        Compilation compilation = Compilation.ofSource(model(beforeTheBody), "Main");
        compilation.answerEverything();
        assertInstanceOf(String.class, compilation.modules().get(0),
                "the model under test compiles to a module");
        return compilation;
    }
}
