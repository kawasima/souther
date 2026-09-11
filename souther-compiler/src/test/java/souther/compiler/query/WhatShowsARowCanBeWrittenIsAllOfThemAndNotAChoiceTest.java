package souther.compiler.query;

import souther.compiler.diag.SourceLayouts;
import org.junit.jupiter.api.Test;

import souther.compiler.partition.PointRole;
import souther.compiler.query.ItemAssessment.WritabilityEvidence.Ground;
import souther.compiler.report.AdequacyReport;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What has shown a row can be written at a point is every ground that holds, and never one of them.
 *
 * <p>The three grounds are not alternatives. The rules can prove a point inhabited and a row can be
 * at it and a value can have been built there, all at once, and a verdict that named one of them
 * named whichever an order put first — so a point the rules prove came back saying a row was found
 * there, and the claim that stands whatever a search afterwards makes of the point was the one left
 * out. Holding all of them there is nothing to leave out, and the order they are said in is one
 * written down rather than one this has to invent.
 *
 * <p>Nothing here is stored. Each ground is read off the answer that establishes it — the coverage
 * measurement, the projection, the attempt — so a point cannot carry a ground the facts beside it do
 * not support, and the correspondence is not something a test has to watch.
 */
class WhatShowsARowCanBeWrittenIsAllOfThemAndNotAChoiceTest {

    /** Both ends read in full, so both are proven on their own, and the body draws a line between
     *  them that nothing has read. */
    private static final String BETWEEN_TWO_PROVEN_POSITIONS = """
            module example.relational

            data Charge = Int
                invariant value >= 0
            data Ceiling = Int
                invariant value >= 1000

            data NoBenefit
            data Benefit = { amount: Charge }
            data Result = NoBenefit | Benefit

            behavior benefitOf : (charge: Charge, ceiling: Ceiling) -> Result
                constructs Benefit, Charge
            let benefitOf (charge, ceiling) = {
                guard charge.value > ceiling.value else NoBenefit
                Benefit { amount = Charge(charge.value - ceiling.value) }
            }

            example benefitOf
                | "over the ceiling" : (Charge(100000), Ceiling(80000))
                    -> Benefit { amount = Charge(20000) }
                | "under the ceiling" : (Charge(50000), Ceiling(80000)) -> NoBenefit
            """;

    /** A bounded position with a row at one end of it and none at the other. */
    private static final String ONE_END_WRITTEN = """
            module example.bounded

            data N = Int
                invariant within = value >= 0 && value <= 10

            data Ok

            behavior f : (n: N) -> Ok

            let f (n) = Ok

            example f
                | "bottom" : (N(0)) -> Ok
            """;

    /**
     * Every ground is the answer beside it, and no ground is anything else.
     *
     * <p>Over every point of a corpus rather than of one model, because what this holds is the
     * correspondence and not a value: a ground appearing where its fact does not is the state the
     * derivation exists to make unspellable, and one model reaches too few of the combinations to
     * say so.
     */
    @Test
    void everyGroundIsReadOffTheAnswerThatEstablishesIt() {
        List<ItemAssessment.Owed> points = owedPoints();
        assertFalse(points.isEmpty(), "the models under test owe rows, or this asserts nothing");
        for (ItemAssessment.Owed owed : points) {
            ItemAssessment.WritabilityEvidence evidence = owed.writabilityEvidence();
            assertEquals(owed.projection() == ItemAssessment.WritabilityProjection.PROVEN,
                    evidence.has(Ground.THE_RULES_PROVE_IT),
                    "the rules ground is the projection having proven it, and both the reading that"
                            + " came to nothing and the reading nobody made leave it out");
            assertEquals(owed.hasRowWitness(), evidence.has(Ground.A_ROW_IS_AT_IT),
                    "the row ground is a row this compilation read standing at the point");
            assertEquals(owed.searches().each().stream()
                            .anyMatch(each -> each instanceof ItemAssessment.Attempt.Certified),
                    evidence.has(Ground.A_VALUE_WAS_BUILT),
                    "and the construction ground is a search having built one and read it back"
                            + " — any of them, since the searches are of the one point");
            assertEquals(!evidence.grounds().isEmpty(), evidence.known(),
                    "known is having a ground, and empty is the whole of what unknown was");
        }
    }

    /**
     * A point more than one thing shows keeps all of them.
     *
     * <p>The case a verdict could not hold. A row at a point the rules already prove is two things
     * established about it, and the two license different sentences — one stands whatever any search
     * afterwards makes of the point, and the other is what this run happened to read.
     */
    @Test
    void aPointTwoThingsShowCarriesBoth() {
        List<List<Ground>> found = owedPoints().stream()
                .map(owed -> owed.writabilityEvidence().grounds().written())
                .filter(grounds -> grounds.size() > 1).toList();
        assertFalse(found.isEmpty(),
                "the corpus reaches a point more than one thing shows, or this asserts nothing");
        assertTrue(found.stream().anyMatch(grounds -> grounds.contains(Ground.THE_RULES_PROVE_IT)
                        && grounds.contains(Ground.A_ROW_IS_AT_IT)),
                "including a point the rules prove that a row also stands at, which is the pair a"
                        + " verdict reported as the row alone");
    }

    /**
     * Nobody has put the question to a line between two positions.
     *
     * <p>What this holds is where the implementation stands, and it is meant to be deleted. A line
     * between two positions is met by a place both positions admit at once; reading each position on
     * its own does not answer that — two ranges overlapping is not two positions holding a pair, and
     * what refuses the pair need not be in either range — so no reading of it is made and the state
     * says so. When one is made, these lines come back {@code PROVEN} or {@code UNPROVEN} and this
     * test goes, because what it is about will have happened.
     *
     * <p>The property that outlives it is not here. That the grounds of a point come from that
     * point's own projection and from nothing beside it is what forbids the endpoints being composed
     * into an answer for the line, and it is held over every point by {@link
     * #everyGroundIsReadOffTheAnswerThatEstablishesIt} — which goes on holding after a relational
     * reading arrives, since it says where a ground comes from rather than what the reading found.
     */
    @Test
    void theProjectionOfALineBetweenTwoPositionsIsNotComputedYet() {
        List<ItemAssessment.Owed> between = new ArrayList<>();
        for (BorderAssessment border : bordersOf(BETWEEN_TWO_PROVEN_POSITIONS,
                "example.relational", "benefitOf")) {
            if (border.border().cut().of()
                    instanceof souther.compiler.partition.BorderQuantity.Apart) {
                for (PointRole role : PointRole.values()) {
                    if (border.at(role) instanceof ItemAssessment.Owed owed) {
                        between.add(owed);
                    }
                }
            }
        }
        assertFalse(between.isEmpty(), "the body draws a line between its two positions");

        List<ItemAssessment.Owed> ends = owedPointsOf(BETWEEN_TWO_PROVEN_POSITIONS,
                "example.relational", "benefitOf");
        assertTrue(ends.stream().anyMatch(
                        owed -> owed.projection() == ItemAssessment.WritabilityProjection.PROVEN),
                "and each position is read in full, so a line at one of them is proven on its own");

        for (ItemAssessment.Owed owed : between) {
            assertEquals(ItemAssessment.WritabilityProjection.NOT_COMPUTED, owed.projection(),
                    "the question was not put to the line, which is not the same as having been put"
                            + " and come to nothing");
            assertFalse(owed.writabilityEvidence().has(Ground.THE_RULES_PROVE_IT),
                    "so nothing about the line is standing on the proofs of its two ends");
        }
    }

    /**
     * The document names the grounds at every point a row is owed at, and agrees with itself.
     *
     * <p>Three things at once, because they are one contract: the field is at every owed point and
     * nowhere else, the verdict beside it is the field being non-empty, and the row ground is the
     * {@code hit} already in the document. What is not in the document is the other two, which is
     * why the field is there.
     */
    @Test
    void aDocumentNamesTheGroundsWhereverARowIsOwed() {
        int owed = 0;
        for (JsonNode item : itemsOf(reportOf(ONE_END_WRITTEN))) {
            if (item.has("notOwed")) {
                assertFalse(item.has("writableBecause"),
                        "a point nobody is owed a row at has nothing to be shown about it");
                continue;
            }
            owed++;
            assertTrue(item.has("writableBecause"), "every point a row is owed at names its grounds");
            Set<String> grounds = new LinkedHashSet<>();
            item.get("writableBecause").forEach(each -> grounds.add(each.asString()));
            assertEquals(!grounds.isEmpty(), item.get("knownWritable").asBoolean(),
                    "the verdict is the field being non-empty and is never the two disagreeing");
            assertEquals(item.has("hit") && item.get("hit").asBoolean(),
                    grounds.contains("a_row_is_at_it"),
                    "and the row ground is the `hit` this document already carries");
            // As words and not as constants. What a consumer meets is the array, and an order read
            // back off the type would agree with the writer however either of them moved.
            assertEquals(List.of("the_rules_prove_it", "a_row_is_at_it", "a_value_was_built")
                            .stream().filter(grounds::contains).toList(),
                    List.copyOf(grounds), "the array is in the order the document promises");
        }
        assertTrue(owed > 0, "the model owes a row somewhere, or this asserts nothing");
    }

    /** Every point a row is owed at, over models that reach the grounds in different combinations. */
    private static List<ItemAssessment.Owed> owedPoints() {
        List<ItemAssessment.Owed> out = new ArrayList<>();
        out.addAll(owedPointsOf(BETWEEN_TWO_PROVEN_POSITIONS, "example.relational", "benefitOf"));
        out.addAll(owedPointsOf(ONE_END_WRITTEN, "example.bounded", "f"));
        return out;
    }

    private static List<ItemAssessment.Owed> owedPointsOf(String model, String module,
                                                          String behavior) {
        List<ItemAssessment.Owed> out = new ArrayList<>();
        for (BorderAssessment border : bordersOf(model, module, behavior)) {
            for (PointRole role : PointRole.values()) {
                if (border.at(role) instanceof ItemAssessment.Owed owed) {
                    out.add(owed);
                }
            }
        }
        return out;
    }

    /** The lines of one behavior, searched: a build that composes values is the one that reaches
     *  every ground, since nothing else puts a construction in front of this. */
    private static List<BorderAssessment> bordersOf(String model, String module, String behavior) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.reportOnly(Adequacy.Level.ALL));
        compilation.answerEverything();
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.searchedBoundariesOf(compilation.db(), module);
        assertNotNull(boundaries, "the model under test compiles");
        List<BorderAssessment> borders = boundaries.get(behavior);
        assertNotNull(borders, behavior + " has lines to be about");
        return borders;
    }

    /** Every coverage item of every border of the one behavior the document is about. */
    private static List<JsonNode> itemsOf(JsonNode report) {
        List<JsonNode> out = new ArrayList<>();
        report.get("modules").forEach(module -> module.get("behaviors").forEach(behavior -> {
            JsonNode partition = behavior.get("partition");
            if (partition != null && partition.has("boundaries")) {
                partition.get("boundaries").forEach(border ->
                        border.get("items").forEach(out::add));
            }
        }));
        assertFalse(out.isEmpty(), "the document is about a border, or this asserts nothing");
        return out;
    }

    private static JsonNode reportOf(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.reportOnly(Adequacy.Level.ALL));
        compilation.answerEverything();
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(
                AdequacyReport.of(compilation).json(
                        souther.compiler.diag.SourceRendering.namedByIdentity(SourceLayouts.NONE)));
    }
}
