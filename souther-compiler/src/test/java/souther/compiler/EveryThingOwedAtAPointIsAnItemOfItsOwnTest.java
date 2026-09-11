package souther.compiler;

import org.junit.jupiter.api.Test;
import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderObligationPointAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.query.GenerationScope;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.query.Composition;
import souther.compiler.query.OfferingRequest;
import souther.compiler.query.RowKey;
import souther.compiler.query.Settlements;
import souther.compiler.partition.BorderObligationPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two things owed at one point are two items, however many rows they take.
 *
 * <p>A region is stopped by whatever reaches the position, and where two rules stop it in two places
 * the line is owed a row in that role twice — two obligations at one point of one line. One value
 * answers both, which is why the search composes one row for them; the account is what says there
 * are two.
 *
 * <p><b>Those are two questions and only one of them is about rows.</b> Read as the places a row is
 * composed at, the second obligation is in no item universe: nothing counts a row as settling it,
 * nothing can say it is answered, and a note saying no row is offered for it prints over the row
 * standing at it. That is the shape this whole change is against, arriving from the inside.
 */
class EveryThingOwedAtAPointIsAnItemOfItsOwnTest {

    /**
     * One position whose line is stopped in two places.
     *
     * <p>Two helpers compare the same position at the same value, so the region beside the
     * declaration's line runs up to a line each of them drew. The values coincide; the rules do not.
     */
    private static final String TWO_STOPS = """
            module example.stops

            data Hours = Int
                invariant value >= 0 && value <= 24

            let over (h: Hours): Int = if h.value > 8 then 1 else 0

            let also (h: Hours): Int = if h.value > 8 then 2 else 0

            behavior tally : (worked: Hours) -> Int

            let tally (worked) = over(worked) + also(worked)
            """;

    /**
     * A point of one line owed twice is two items, and what tells them apart is the far side.
     *
     * <p>The declaration is owed a run beside each of its ends, and the run stops at whichever
     * helper's line reaches the position — two of them, at one value. Same line, same role, two
     * things to be told about, which is what {@link souther.compiler.partition.RegionBasis}
     * carries.
     */
    @Test
    void aPointOwedTwiceIsTwoItemsOfTheOffering() {
        Compilation compilation = compiled();
        Settlements table = Settlements.of(compilation.db(), composed(compilation));

        Map<String, Set<BorderObligationPoint>> byLineAndRole = new LinkedHashMap<>();
        for (BorderObligationPoint point : pointsOf(table)) {
            byLineAndRole.computeIfAbsent(point.line() + " " + point.role(),
                    _ -> new LinkedHashSet<>()).add(point);
        }
        assertTrue(byLineAndRole.values().stream().anyMatch(at -> at.size() > 1),
                "a point of this model is owed more than once: " + byLineAndRole);
    }

    /**
     * Every open point is one item, and nothing else is.
     *
     * <p>The whole of what an offering is asked. A point is one piece of work however many readings
     * of the line there are and whoever owes it, so the items are the points this module answers
     * for that the measurement says are worth looking for — one apiece.
     *
     * <p>Written as the two sets rather than as a count, which holds while a point this dropped is
     * made up for by one it invented.
     */
    @Test
    void everyOpenPointIsOneItemAndNothingElseIs() {
        Compilation compilation = compiled();
        List<BorderObligationPointAssessment> points = compilation.db()
                .ask(new Adequacy.Obligations("example.stops",
                        new souther.compiler.query.GenerationScope.Module())).value();
        assertNotNull(points, "the model under test is measured");

        Set<BorderObligationPoint> open = new LinkedHashSet<>();
        for (BorderObligationPointAssessment each : points) {
            // What this module answers for. A line owed to declarations elsewhere is one its values
            // are held to and somebody else's to write a row at.
            boolean here = !each.ownersIn("example.stops").isEmpty() || each.owedToTheReading();
            if (here && each.owed().worthSearching()) {
                open.add(each.point());
            }
        }
        assertFalse(open.isEmpty(), "the model under test leaves points to look for");

        Settlements table = Settlements.of(compilation.db(), composed(compilation));
        assertEquals(open, new LinkedHashSet<>(pointsOf(table)),
                "every open point is asked about, once");
        assertEquals(pointsOf(table).size(), new LinkedHashSet<>(pointsOf(table)).size(),
                "and no point is asked about twice: " + pointsOf(table));
    }

    /** The points the offering was asked about, in the order it asks them. */
    private static List<BorderObligationPoint> pointsOf(Settlements table) {
        List<BorderObligationPoint> asked = new ArrayList<>();
        for (ObligationIdentity item : table.requested()) {
            if (item instanceof ObligationIdentity.OfALine(var point)) {
                asked.add(point);
            }
        }
        return asked;
    }

    @Test
    void theTwoOfThemAreAnsweredByTheOneRowComposedThere() {
        Compilation compilation = compiled();
        Settlements table = Settlements.of(compilation.db(), composed(compilation));

        // The obligations that share a point, which is what a row there answers at once.
        Map<String, Set<ObligationIdentity>> byPoint = new LinkedHashMap<>();
        for (ObligationIdentity item : table.requested()) {
            if (item instanceof ObligationIdentity.OfALine(var point)) {
                byPoint.computeIfAbsent(point.line() + " " + point.role(),
                        _ -> new LinkedHashSet<>()).add(item);
            }
        }
        List<Set<ObligationIdentity>> shared = byPoint.values().stream().filter(at -> at.size() > 1).toList();
        assertFalse(shared.isEmpty(), "a point of this model is owed more than once: " + byPoint);

        for (Set<ObligationIdentity> at : shared) {
            Set<RowKey> composedThere = new LinkedHashSet<>();
            at.forEach(item -> composedThere.add(table.composedFor().get(item)));
            assertEquals(1, composedThere.size(),
                    "one row is composed for everything owed at one point: " + at + " -> "
                            + composedThere);
            // And it answers all of them, so a reduction counts it once for each.
            RowKey row = composedThere.iterator().next();
            if (row != null) {
                at.forEach(item -> assertTrue(table.at(row, item).settles(),
                        "the row composed there settles " + item));
            }
        }
    }

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSource(TWO_STOPS, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    private static Composition composed(Compilation compilation) {
        Map<String, Adequacy.Filling> generated =
                Adequacy.generatedOf(compilation.db(), "example.stops");
        assertNotNull(generated, "the model under test compiles: " + compilation.errors());
        return Composition.composed(OfferingRequest.overTheModule("example.stops", true), generated,
                Adequacy.accountFor(compilation.db(), "example.stops",
                        new GenerationScope.Module()),
                Composition.WhatStandsIn.REQUIRING_NOTHING);
    }
}
