package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.NothingWasAsked;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a build that asked for no measurement is told.
 *
 * <p>{@code off} used to be a word the callers acted on and the measures did not: nobody built a
 * report at that setting, so every measure went ahead and counted, and a caller that did build one
 * got numbers from a run that had asked for none. Which of those two a reader met was decided by
 * which entry point they came through, and that is not something a level should decide (issue #955).
 *
 * <p>So it is a state of each measure, and of each measure's parts. The model's own answers stay —
 * which cases a signature has, which classes a position divides into, which lines its rules draw —
 * because no measurement made them true and none is needed to read them back.
 */
class AskingForNothingIsAnAnswerTest {

    private static final String MODEL = """
            module example.off

            data Amount = Int
                invariant value >= 0

            data Yes
            data No
            data Flag = Yes | No

            data Charged = { cost: Amount }
            data Refused = { reason: String }

            behavior submit : (flag: Flag, cost: Amount) -> Charged | Refused
                constructs Charged, Refused

            let submit (flag, cost) = {
                guard cost.value <= 100 else Refused { reason = "over" }
                Charged { cost = cost }
            }

            example submit
                | "within" : (Yes, Amount(50)) -> Charged
            """;

    /** Every measure that reads the rows says nobody asked, and says it in the same word. */
    @Test
    void everyMeasureThatReadsTheRowsSaysNobodyAsked() {
        Compilation compilation = compiled(Adequacy.Level.OFF);
        Adequacy.Of measured = compilation.adequacy("example.off");

        Adequacy.SignatureEvidence signature = measured.signatures().get("submit");
        assertEquals(NothingWasAsked.NOT_ASKED, signature.counted().why());
        assertEquals(NothingWasAsked.NOT_ASKED, signature.output().cases().why(),
                "and its parts say it themselves, since a document writes them on their own");
        // The position that is a sum. The one beside it is not, which is the measure's own answer
        // and comes before this one: no build asking for anything would have counted it.
        assertEquals(NothingWasAsked.NOT_ASKED, signature.positions().get(0).cases().why());
        assertEquals(souther.compiler.query.InputCaseEvidence.NotASum.NOT_A_SUM,
                signature.positions().get(1).cases().why());

        PartitionEvidence partition = measured.partitions().get("submit");
        for (PartitionEvidence.AxisCoverage axis : partition.axes()) {
            assertEquals(NothingWasAsked.NOT_ASKED, axis.reached().why(), axis.path());
        }
        assertEquals(NothingWasAsked.NOT_ASKED, partition.pairs().counted().why());
        for (souther.compiler.query.BorderAssessment.Point point
                : souther.compiler.query.BorderAssessment.pointsOf(
                        Adequacy.readingsOf(compilation.db(), "example.off").get("submit"))) {
            if (point.owed() != null) {
                assertEquals(souther.compiler.query.ItemAssessment.Coverage.NotAsked.NOT_ASKED,
                        point.owed().coverage().why(), point.label());
            }
        }
        assertEquals(Adequacy.BranchEvidence.NotAsked.NOT_ASKED,
                measured.branches().get("submit").measured().why());
    }

    /** What the model says is there all the same. A measurement made none of it true. */
    @Test
    void whatTheModelDeclaresIsStillThere() {
        Compilation compilation = compiled(Adequacy.Level.OFF);
        Adequacy.Of measured = compilation.adequacy("example.off");

        assertEquals(2, measured.signatures().get("submit").output().declared().size(),
                "the output is a sum of two whether or not anybody counted");
        PartitionEvidence partition = measured.partitions().get("submit");
        assertEquals(List.of("Yes", "No"), partition.axes().get(0).classes());
        assertFalse(Adequacy.readingsOf(compilation.db(), "example.off").get("submit").isEmpty(),
                "the invariant and the guard draw lines");
    }

    /** A verdict over measures none of which was made is undetermined, which is what the
     *  specification says the word is for. */
    @Test
    void theVerdictIsUndetermined() {
        AdequacyReport report = AdequacyReport.of(compiled(Adequacy.Level.OFF));

        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED, report.adequacy(),
                report.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        assertEquals(List.of(), report.findings(), "nothing was measured, so nothing was found");
    }

    /**
     * And the document holds what the model declares, with no set of what the rows reached.
     *
     * <p>What this is against is a value that survives because one writer wrote it without asking
     * its measurement — and the writer that does that is the one nobody thought to check. Read over
     * the signature, the positions and the space of combinations, which are the measures whose
     * numbers a reader could take for a count. What {@code branch} and a border's points write
     * beside an unavailable status is not what this level decides: they write the same fields at
     * every level, so a report measured in full is where that question is asked.
     */
    @Test
    void theDocumentHoldsWhatTheModelSaysAndNoCount() {
        JsonNode behavior = JsonMapper.builder().build()
                .readTree(AdequacyReport.of(compiled(Adequacy.Level.OFF))
                        .json(SourceRendering.namedByIdentity(SourceLayouts.NONE)))
                .get("modules").get(0).get("behaviors").get(0);

        JsonNode signature = behavior.get("signature");
        assertEquals("not_asked", signature.get("reason").asString());
        assertEquals(List.of(), namesUnder(signature, "specified", "observed", "verified",
                "executed", "unclassifiedRows"));
        assertEquals(2, signature.get("output").get("declared").size(),
                "and what the model declares is written all the same");

        JsonNode partition = behavior.get("partition");
        for (JsonNode axis : partition.get("axes")) {
            assertEquals("not_asked", axis.get("reason").asString());
            assertEquals(List.of(), namesUnder(axis, "covered", "unclassifiedRows"));
            assertFalse(axis.get("classes").isEmpty(), "the classes are the model's");
        }
        assertEquals("not_asked", partition.get("pairs").get("reason").asString());
        assertEquals(List.of(), namesUnder(partition.get("pairs"), "covered", "witnessedFeasible",
                "provenInfeasible", "unknown"));
        assertTrue(partition.get("pairs").get("total").asInt() > 0, "the size is the model's");
    }

    /** Which of {@code counts} the document writes anywhere under {@code node}. */
    private static List<String> namesUnder(JsonNode node, String... counts) {
        List<String> found = new java.util.ArrayList<>();
        node.propertyStream().forEach(each -> {
            if (List.of(counts).contains(each.getKey())) {
                found.add(each.getKey());
            }
            found.addAll(namesUnder(each.getValue(), counts));
        });
        node.valueStream().forEach(each -> found.addAll(namesUnder(each, counts)));
        return found;
    }

    private static Compilation compiled(Adequacy.Level level) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.reportOnly(level));
        compilation.answerEverything();
        return compilation;
    }

    /** The same model measured, so the check above is one something can fail. */
    @Test
    void theSameModelMeasuredDoesHoldCounts() {
        JsonNode document = JsonMapper.builder().build()
                .readTree(AdequacyReport.of(compiled(Adequacy.Level.ALL))
                        .json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));

        assertFalse(document.findValues("specified").isEmpty(),
                () -> "nothing was counted at `all` either: " + document);
    }
}
