package souther.compiler.report;

import org.junit.jupiter.api.Test;

import souther.compiler.observe.Incompleteness;
import souther.compiler.partition.CompositionBudget;
import souther.compiler.query.EstablishmentGap;
import souther.compiler.query.ItemAssessment;
import souther.compiler.query.ObligationCoverage;
import souther.compiler.query.ObligationDisposition;
import souther.compiler.query.ReadingReasons;
import souther.compiler.query.UnaskedReasons;
import souther.compiler.query.WeakeningSet;
import souther.compiler.query.WritabilityKnowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every way an obligation stays undecided names something the verdict is open on.
 *
 * <p>The verdict rests on three things and the account of what holds it open walked all three —
 * but it asked the obligations a different question than {@code adequacy()} asks them. That one
 * reads the disposition; this read the coverage under it. Three of the four ways an obligation is
 * undecided leave the coverage with nothing to have gone without, so a point nothing could show a
 * row for held a verdict open and named nothing.
 *
 * <p>The same defect the conformance corpus found on the measurement side, one list along. It is
 * held here as a law over the seal rather than as a model: which ways there are is
 * {@link ObligationDisposition.Uncertainty}'s own answer, and a fourth added later arrives here
 * whether or not anybody remembered to write a model for it.
 */
class EveryWayTheVerdictStaysOpenNamesSomethingTest {

    /**
     * What the walk would have handed in, standing for whichever point it reached.
     *
     * <p>Any subject does here. What is held below is that whatever was handed in comes back on
     * every opening the way yields, and no way is entitled to answer with one of its own: a
     * disposition holds what is undecided about a point and never which point, so an opening that
     * named a subject of its own would have made it up.
     */
    private static final Subject HANDED_IN = new Subject.OfABehavior("submit");

    /**
     * Every way, and what it opens the verdict on.
     *
     * <p>Written as the openers each yields, as {@code kind/sensitivity}. A way that yields none
     * says so and owes a reason, which is what the row for a reading that stopped is: what it met
     * is the coverage's own set of facts, and those are counted where the facts are.
     */
    private static Map<String, List<String>> theWaysAnObligationStaysOpen() {
        Map<String, List<String>> table = new LinkedHashMap<>();
        // Nothing here, and not nothing missing. What a reading that stopped met is the coverage's
        // `WeakeningSet`, which is unioned with every other measure's before any of this — so an
        // opener made here would be one fact said twice. That the set is never empty is what makes
        // the row below an answer rather than a hole, and `ObligationCoverage.Undecided` refuses an
        // empty one at construction.
        table.put("ReadingsStopped", List.of());
        // A point nothing was read against. The same fact as a measure nobody made, so the same
        // word: what was never started is not something that fell short.
        table.put("NothingWasRead", List.of("NotMeasured/UNAFFECTED"));
        // A showing that was made and stopped, one opener per thing that stopped it. The gap
        // answers for the sensitivity, because an observation holds the codes it met and those do
        // not agree with each other.
        table.put("Stopped[observation of a value a limit shortened]",
                List.of("ShowingStopped/MAY_CHANGE"));
        table.put("Stopped[observation of a value nothing could read]",
                List.of("ShowingStopped/UNAFFECTED"));
        table.put("Stopped[observation of both]", List.of("ShowingStopped/UNAFFECTED"));
        table.put("Stopped[nothing composed]", List.of("ShowingStopped/MAY_CHANGE"));
        // And a point where nothing was stopped and nothing arrived.
        table.put("NothingShowedIt", List.of("NothingShowedARowCanBeWritten/UNAFFECTED"));
        return table;
    }

    /** The table, held against what the ways themselves come to. */
    @Test
    void everyWayAnObligationStaysOpenSaysWhatItOpensTheVerdictOn() {
        Map<String, List<String>> said = new LinkedHashMap<>();
        everyWay().forEach((name, why) -> {
            List<AdequacyOpening> out = new ArrayList<>();
            AdequacyReport.openedBy(out, HANDED_IN, ObligationDisposition.Undecided.about(List.of(why)));
            said.put(name, out.stream()
                    .map(each -> each.getClass().getSimpleName() + "/" + each.runSensitivity())
                    .toList());
        });

        assertEquals(theWaysAnObligationStaysOpen(), said);
    }

    /**
     * And every way there is has a row, read off the seal rather than off the list.
     *
     * <p>The list is written out because a way with an argument cannot be made from its class
     * alone. That it holds every one of them is this check's word, and a fifth way added and left
     * out of it comes back here with nothing to say about it.
     */
    @Test
    void everyWayThereIsHasARowAbove() {
        Set<String> sealed = new LinkedHashSet<>();
        for (Class<?> arm : armsOf(ObligationDisposition.Uncertainty.class)) {
            sealed.add(arm.getSimpleName());
        }
        Set<String> listed = new LinkedHashSet<>();
        for (ObligationDisposition.Uncertainty each : everyWay().values()) {
            listed.add(each.getClass().getSimpleName());
        }

        assertEquals(sealed, listed, "a way an obligation stays undecided that no row answers for");
    }

    /**
     * An undecided obligation opens the verdict on something, whichever way it is undecided.
     *
     * <p>The law the rows are for, and the one a row of {@code ReadingsStopped} alone would break
     * if the coverage could go without nothing. It cannot: the two places an emptiness could hide
     * both refuse it where the value is made, so "undecided and nothing to say" is a state nobody
     * can write rather than one nobody has written yet.
     */
    @Test
    void anUndecidedObligationIsAlwaysOpenOnSomething() {
        for (ObligationDisposition.Uncertainty each : everyWay().values()) {
            List<AdequacyOpening> out = new ArrayList<>();
            AdequacyReport.openedBy(out, HANDED_IN, ObligationDisposition.Undecided.about(List.of(each)));
            boolean readings =
                    each instanceof ObligationDisposition.Uncertainty.WhetherARowIsThere
                            .ReadingsStopped;

            assertTrue(readings || !out.isEmpty(),
                    () -> each + " holds the verdict open and names nothing");
            // And names the point the walk reached it at, which is the half a disposition does not
            // hold. Handed over and dropped, every one of these came out as the same entry however
            // many points the module owed a row at (issue #1437).
            for (AdequacyOpening opened : out) {
                assertEquals(HANDED_IN, opened.subject(),
                        () -> each + " opened the verdict on something other than the point it is"
                                + " about");
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> new ObligationCoverage.Undecided(WeakeningSet.none()),
                "a reading that stopped met something, so the facts it is counted by are there");
        assertThrows(IllegalArgumentException.class,
                () -> ObligationDisposition.Undecided.about(List.of()),
                "and an obligation is undecided about something");
    }

    /** One of each way, named as the rows name them. */
    private static Map<String, ObligationDisposition.Uncertainty> everyWay() {
        Map<String, ObligationDisposition.Uncertainty> out = new LinkedHashMap<>();
        out.put("ReadingsStopped",
                new ObligationDisposition.Uncertainty.WhetherARowIsThere.ReadingsStopped(
                        ReadingReasons.of(List.of())));
        out.put("NothingWasRead",
                new ObligationDisposition.Uncertainty.WhetherARowIsThere.NothingWasRead(
                        UnaskedReasons.of(ItemAssessment.Coverage.NotAsked.NOT_ASKED)));
        out.put("Stopped[observation of a value a limit shortened]",
                stopped(EstablishmentGap.Observation.of(
                        Set.of(Incompleteness.Code.VALUE_TRUNCATED))));
        out.put("Stopped[observation of a value nothing could read]",
                stopped(EstablishmentGap.Observation.of(
                        Set.of(Incompleteness.Code.VALUE_UNREADABLE))));
        // Both, which is the case a fold over the codes gets wrong: a showing stopped by a limit
        // and by something nothing could read is stopped again after the limit is raised.
        out.put("Stopped[observation of both]",
                stopped(EstablishmentGap.Observation.of(Set.of(
                        Incompleteness.Code.VALUE_TRUNCATED,
                        Incompleteness.Code.VALUE_UNREADABLE))));
        out.put("Stopped[nothing composed]",
                stopped(EstablishmentGap.Composition.of(
                        Set.of(CompositionBudget.ELEMENTS_A_PROPOSAL_HOLDS))));
        out.put("NothingShowedIt",
                new ObligationDisposition.Uncertainty.WhetherARowCanBeWritten.NothingShowedIt());
        return out;
    }

    private static ObligationDisposition.Uncertainty stopped(EstablishmentGap gap) {
        return new ObligationDisposition.Uncertainty.WhetherARowCanBeWritten.Stopped(
                WritabilityKnowledge.Prevented.by(gap));
    }

    /** The leaves of a seal, which is what its ways are. */
    private static List<Class<?>> armsOf(Class<?> seal) {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> each : seal.getPermittedSubclasses()) {
            if (each.isSealed()) {
                out.addAll(armsOf(each));
            } else {
                out.add(each);
            }
        }
        return out;
    }
}
