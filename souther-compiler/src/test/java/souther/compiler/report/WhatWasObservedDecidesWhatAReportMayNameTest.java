package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import souther.compiler.query.WeakeningSet;
import souther.compiler.types.WrittenOwner;
import souther.compiler.report.AdequacyReport;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import souther.compiler.check.BehaviorImplementation;
import souther.compiler.coverage.ArmProbe;
import souther.compiler.coverage.ArmReportAnchor;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.coverage.DecidedBy;
import souther.compiler.coverage.Numberings;
import souther.compiler.coverage.SourceOutcome;
import souther.compiler.query.Adequacy;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a report may name about the arms turns on what was observed of each arm, not on what the
 * measure over all of them came to.
 *
 * <p>The measure here is short of something and every arm of it is answered for: the rows were all
 * read, so the arm no row goes through is a gap and is named, and the fork whose rule could not be
 * worked out takes its own arms out of the count and is said under it. Read as one word, a build
 * whose rows all ran was told a row was not read, and every arm it certainly does not reach went
 * unsaid.
 */
class WhatWasObservedDecidesWhatAReportMayNameTest {

    private static final SourceConstructOrigin SETTLED =
            SourceConstructOrigin.written(new WrittenOwner.Body("m", "b"), 0,
                    SourceConstruct.IF);
    private static final SourceConstructOrigin UNSETTLED =
            SourceConstructOrigin.written(new WrittenOwner.Body("m", "b"), 1,
                    SourceConstruct.IF);

    /** Four places of one numbering, so that arms put in one list are addresses of one. */
    private static final java.util.Map<Integer, ArmProbe> PLACES = Numberings.arms(4);

    private static CoverageSites.ArmSite arm(SourceConstructOrigin fork, int index, DecidedBy decided) {
        return new CoverageSites.ArmSite("b",
                new SourceOutcome.Held(new SourceOutcome.HeldBy.Condition()),
                Numberings.armPlace(Numberings.arm(fork, index), PLACES.get(index),
                        new ArmReportAnchor.WhereItIsWritten(fork)),
                index,
                new CoverageSites.Obligation("b", fork, index, decided));
    }

    /** The behavior as the report holds it, with where each of its forks is shown. */
    private static AdequacyReport.BehaviorReport reported() {
        return new AdequacyReport.BehaviorReport("b", BehaviorImplementation.IMPLEMENTED,
                new souther.compiler.query.BehaviorEvidence(
                        souther.compiler.query.Adequacy.RowReading.NONE,
                        null, null, null, null, read(), null),
                null, List.of(), shown(), Map.of(), Map.of(), Map.of());
    }

    /** Where this report shows each of the two forks. A place per fork and not per arm: the arms of
     *  one fork are written at it. */
    private static java.util.Map<ArmReportAnchor, souther.compiler.diag.Citation> shown() {
        return java.util.Map.of(
                new ArmReportAnchor.WhereItIsWritten(SETTLED), at(1),
                new ArmReportAnchor.WhereItIsWritten(UNSETTLED), at(2));
    }

    private static souther.compiler.diag.Citation at(int line) {
        return souther.compiler.diag.Citation.of(souther.compiler.diag.Placement
                .aFileOfThisCompile(new souther.compiler.source.SourceId("m.sou")).at(line, 1));
    }

    /** Every row read, and one fork whose rule could not be worked out. */
    private static Adequacy.BranchEvidence read() {
        return Adequacy.BranchEvidence.measured("b",
                List.of(arm(SETTLED, 0, DecidedBy.THE_DECLARATION),
                        arm(SETTLED, 1, DecidedBy.THE_DECLARATION),
                        arm(UNSETTLED, 2, DecidedBy.NOT_SAID),
                        arm(UNSETTLED, 3, DecidedBy.NOT_SAID)),
                Set.of(PLACES.get(0)), Set.of(), souther.compiler.query.Adequacy.NOTHING_PROVEN,
                WeakeningSet.none());
    }

    /** The arm no row goes through is named, though the numbers are not a whole measure. */
    @Test
    void theArmNoRowGoesThroughIsStillNamed() {
        ObjectNode behavior = JsonMapper.builder().build().createObjectNode();
        AdequacyReport.branch(behavior, reported(),
                new DocumentSources(SourceRendering.namedByIdentity(SourceLayouts.NONE)));

        List<String> dispositions = new java.util.ArrayList<>();
        behavior.get("branch").get("obligations")
                .forEach(arm -> dispositions.add(arm.get("disposition").asString()));
        assertEquals(List.of("met", "unmet", "not_counted", "not_counted"), dispositions,
                () -> "the settled fork's other arm, and the fork nothing tells apart: "
                        + behavior.get("branch"));
        assertEquals("partial", behavior.get("branch").get("status").asString(),
                "and the numbers still say they are not a whole measure");
    }

    /** And nothing says a row was not read, because every one was. */
    @Test
    void andNothingSaysARowWasNotRead() {
        StringBuilder out = new StringBuilder();
        new AdequacyReport(AdequacyReport.SCHEMA_VERSION, "x",
                souther.compiler.query.Adequacy.AdequacyBar.RELIABLE_DOMAIN, WeakeningSet.none(),
                List.of()).branch(out, reported(), null, SourceRendering.namedByIdentity(SourceLayouts.NONE));

        // Two arms and not four. What the count holds is what a row can be owed for, and a fork
        // standing for however many rules nobody could work out is not that — it is said under the
        // number instead. Held in the denominator, the difference between the numbers was two arms
        // a reader had no way to walk to.
        assertTrue(out.toString().contains("branch      1/2"),
                () -> "what the arms came to: " + out);
        assertTrue(out.toString().contains("could not be worked out"),
                () -> "and the arms it does not hold are said under it: " + out);
        assertFalse(out.toString().contains("a row was not read"),
                () -> "every row was read: " + out);
    }
}
