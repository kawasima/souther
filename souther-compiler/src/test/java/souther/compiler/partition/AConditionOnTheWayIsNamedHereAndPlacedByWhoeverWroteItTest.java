package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Citation;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Sites;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * One condition is one name, however many ways carry it.
     *
     * <p>The left operand of a short-circuit operator is on the way to its right operand, and on
     * the way into the arm the whole condition leads to. Those are two accounts of one condition,
     * and a reader joining them on the name joins them on nothing if the two say different names.
     *
     * <p>Its own model, because what this is about is one condition reached twice rather than the
     * shape of an account.
     */
    @Test
    void oneConditionIsOneNameHoweverManyWaysCarryIt() {
        String source = """
                module example.twice

                behavior oneCondition : (a: Bool, n: Int) -> Bool
                let oneCondition (a, n) =
                    if a && n > 3 then n > 0 else n < 0
                """;
        Compilation compilation = compiledFrom(source);
        List<OnTheWay.Declined> named = new ArrayList<>();
        for (List<OnTheWay> way : waysIn(compilation, "oneCondition")) {
            for (OnTheWay each : way) {
                if (each instanceof OnTheWay.Declined left
                        && left.why() instanceof OnTheWay.Why.NoWordsForTheShape) {
                    named.add(left);
                }
            }
        }
        assertTrue(named.size() > 1,
                () -> "the parameter this reading has no words for is on more than one way: "
                        + named);
        assertEquals(1, new LinkedHashSet<>(named).size(),
                () -> "one condition, and every way that carries it says the same one: " + named);
    }

    /**
     * And a reading names each condition once, however many times it is read.
     *
     * <p>What the reading writes down for the conditions it places itself is one entry per
     * condition. A name minted per reading of a subtree instead would grow with the shape of the
     * tree rather than with what is in it — a left-leaning run of operators reads its whole prefix
     * again at every step — and every one of those names would be an entry in an answer.
     */
    @Test
    void whatTheReadingWritesDownGrowsWithTheConditionsAndNotWithTheReading() {
        String source = """
                module example.run

                behavior aRun : (a: Bool, b: Bool, c: Bool, d: Bool, n: Int) -> Bool
                let aRun (a, b, c, d, n) =
                    if a && b && c && d then n > 0 else n < 0
                """;
        assertEquals(4, placedByTheReading(compiledFrom(source), "aRun").size(),
                "the four truths it has no words for, and nothing else");
    }

    /**
     * Two conditions a report sends a reader to one place for are still two conditions.
     *
     * <p>The one that says the name is doing work the anchor cannot. A helper is spliced into each
     * call of it, so one condition an author wrote stands twice in one body — and where it is
     * written is one place, because there is one of it in the source. Told apart by where a report
     * points, the two would be one value; told apart by the name, they are what they are.
     */
    @Test
    void twoConditionsReportedAtOnePlaceAreStillTwo() {
        String source = """
                module example.expanded

                let either (x: Int, y: Int): Bool = x > 0 || y > 0

                behavior calledTwice : (m: Int, n: Int) -> Bool
                let calledTwice (m, n) =
                    if either(m, n) then
                        if either(n, m) then n > 0 else n < 0
                    else
                        n > 5
                """;
        List<OnTheWay.Declined> declined = new ArrayList<>();
        for (List<OnTheWay> way : waysIn(compiledFrom(source), "calledTwice")) {
            for (OnTheWay each : way) {
                if (each instanceof OnTheWay.Declined left
                        && left.why() instanceof OnTheWay.Why.OneOfTwoThings) {
                    declined.add(left);
                }
            }
        }
        Set<ConditionReportAnchor> where = new LinkedHashSet<>();
        Set<ConditionOccurrence> named = new LinkedHashSet<>();
        declined.forEach(each -> {
            where.add(each.anchor());
            named.add(each.condition());
        });
        assertEquals(1, where.size(),
                () -> "one condition was written, so a report points at one place: " + declined);
        assertEquals(2, named.size(),
                () -> "and the reading met two of it, which is what the name says: " + declined);
        assertEquals(2, new LinkedHashSet<>(declined).size(),
                () -> "so the two are two values, which the place could not have said: " + declined);
    }

    /** What the reading of {@code behavior} wrote down for the conditions it places itself. */
    private static Map<ConditionOccurrence, Citation> placedByTheReading(Compilation compilation,
                                                                         String behavior) {
        return compilation.db()
                .ask(new Adequacy.ConditionsMet(compilation.modules().get(0), behavior)).value();
    }

    /** Every account the reading of {@code behavior} filed. */
    private static List<List<OnTheWay>> waysIn(Compilation compilation, String behavior) {
        Partitions.Partitioning divided = compilation.db()
                .ask(new Adequacy.Divided(compilation.modules().get(0), behavior)).value();
        assertNotNull(divided, "the model under test is measured");
        return List.copyOf(divided.reaching().byComparison().values());
    }

    private static Compilation compiledFrom(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        return compilation;
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
