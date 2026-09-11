package souther.compiler;

import org.junit.jupiter.api.Test;
import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.query.PointResolution;
import souther.compiler.query.GenerationScope;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.query.Composition;
import souther.compiler.query.OfferingRequest;
import souther.compiler.query.OwedBoundaryPoint;
import souther.compiler.query.Settlements;
import souther.compiler.partition.BorderObligationPoint;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a run is asked for a row at is not everything a line is owed one at.
 *
 * <p>A point a written row already stands at is owed and is nobody's work: the measurement says so,
 * and the search is gated on the same answer. Held in the offering's items all the same, a candidate
 * standing there would be the only thing offering for it — so nothing could drop that candidate, and
 * it would go out beside the row that made it unnecessary.
 *
 * <p>The declarations say it in their own words. A point resolved as {@code NoSearch} is one nothing
 * was looked for at, and its two causes are a row already standing there and nothing having measured
 * it. Neither is a piece of work this run hands anybody.
 */
class APointNothingIsAskedForARowAtIsNotOfferedOneTest {

    /**
     * A line with a row written at one of its points and nothing at the others.
     *
     * <p>The guard draws a line at 50 and the example row sits on it, so the point against the line
     * is answered and the ones beside it are not.
     */
    private static final String ONE_POINT_WRITTEN = """
            module example.written

            data Hours = Int
                invariant value >= 0 && value <= 24

            let over (h: Hours): Int = if h.value > 8 then 1 else 0

            behavior tally : (worked: Hours) -> Int

            let tally (worked) = over(worked)

            example tally
                | "on the line" : (Hours(8)) -> 0
            """;

    @Test
    void aPointAWrittenRowStandsAtIsNotAnItemOfTheOffering() {
        Compilation compilation = compiled();
        List<BorderAssessment> edges = compilation.db()
                .ask(new Adequacy.BoundarySearch("example.written", "tally")).value();
        assertNotNull(edges, "the model under test is measured");

        List<OwedBoundaryPoint> account = OwedBoundaryPoint.across(edges);
        List<OwedBoundaryPoint> settledAlready = account.stream()
                .filter(point -> !point.item().worthSearching()).toList();
        assertFalse(settledAlready.isEmpty(),
                "the written row leaves some point owed and not worth a candidate: " + account);

        Set<BorderObligationPoint> asked = Settlements.of(compilation.db(), composed(compilation))
                .requested().stream()
                .filter(ObligationIdentity.OfALine.class::isInstance)
                .map(item -> ((ObligationIdentity.OfALine) item).point())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        for (OwedBoundaryPoint point : settledAlready) {
            assertFalse(asked.contains(point.owed()),
                    "nothing is asked for a row at " + point + ", so it is not an item: " + asked);
        }
        // And what is asked for is still there, so this is not the items going missing.
        for (OwedBoundaryPoint point : account) {
            if (point.item().worthSearching()) {
                assertTrue(asked.contains(point.owed()),
                        "a point a row is asked for at is an item: " + point);
            }
        }
    }

    @Test
    void nothingOfferedStandsAloneAtAPointNobodyIsAskedAbout() {
        Compilation compilation = compiled();
        Settlements table = Settlements.of(compilation.db(), composed(compilation));

        // Every kept row answers something this run was asked for. A row kept because it was the
        // only thing standing at a point already answered is the defect this is against.
        for (var row : table.keeping()) {
            assertTrue(table.requested().stream().anyMatch(item -> table.offers(Set.of(row), item)),
                    row + " is kept for something this run was asked for");
        }
    }

    /**
     * A declaration's line with a row written at one of its points.
     *
     * <p>So one point of it resolves to nothing having been looked for, and another to a search.
     */
    private static final String DECLARED = """
            module example.declaredwritten

            data Code = String
                invariant longEnough = String.length(value) >= 4

            data Ok

            behavior take : (code: Code) -> Ok
                constructs Ok

            let take (code) = Ok

            example take
                | "on the line" : (Code("abcd")) -> Ok
            """;

    @Test
    void aDeclaredPointNothingWasLookedForAtIsNotAnItem() {
        Compilation compilation = Compilation.ofSource(DECLARED, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, Adequacy.Filling> generated =
                Adequacy.generatedOf(compilation.db(), "example.declaredwritten");
        assertNotNull(generated, "the model under test compiles: " + compilation.errors());
        var declared = Adequacy.accountFor(compilation.db(),
                "example.declaredwritten", new GenerationScope.Module());
        Composition composed = Composition.composed(
                OfferingRequest.overTheModule("example.declaredwritten", true), generated, declared,
                Composition.WhatStandsIn.REQUIRING_NOTHING);
        Settlements table = Settlements.of(compilation.db(), composed);

        Set<BorderObligationPoint> asked = table.requested().stream()
                .filter(ObligationIdentity.OfALine.class::isInstance)
                .map(item -> ((ObligationIdentity.OfALine) item).point())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        boolean sawOne = false;
        for (var each : declared.resolved().entrySet()) {
            if (each.getValue().resolution() instanceof PointResolution.NoSearch _) {
                sawOne = true;
                assertFalse(asked.contains(each.getKey()),
                        "nothing was looked for at " + each.getKey() + ", so it is not an item");
            } else {
                assertTrue(asked.contains(each.getKey()),
                        "a point this run asked about is an item: " + each.getKey());
            }
        }
        assertTrue(sawOne, "the written row leaves a declared point nothing is looked for at: "
                + declared.resolved());
    }

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSource(ONE_POINT_WRITTEN, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    private static Composition composed(Compilation compilation) {
        Map<String, Adequacy.Filling> generated =
                Adequacy.generatedOf(compilation.db(), "example.written");
        assertNotNull(generated, "the model under test compiles: " + compilation.errors());
        return Composition.composed(OfferingRequest.overTheModule("example.written", true), generated,
                Adequacy.accountFor(compilation.db(), "example.written",
                        new GenerationScope.Module()),
                Composition.WhatStandsIn.REQUIRING_NOTHING);
    }
}
