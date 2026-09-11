package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import org.junit.jupiter.api.Test;

import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.MeasurementStatus;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Measure;
import souther.compiler.query.Measurement;
import souther.compiler.report.AdequacyReport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model issue #996 is written about.
 *
 * <p>Every measure counted over a behavior's rows may have nothing to be about. This one's output is
 * not a sum, its rules divide no position and draw no line, and it has no body — so all four answer
 * inapplicable, correctly, and none of them is weaker for anything. Its one row does not come back.
 *
 * <p>The reading of the rows is the measure that is left, and it is the one measure that can never
 * be inapplicable: a behavior has rows to read, even where there are none. Before it was one, what
 * the run went without reached the document only through the measures counted over the rows — so a
 * module where every one of those had nothing to be about reported {@code measurement: complete}
 * under a line saying a row of it did not come back.
 */
class AModuleEveryMeasureOfWhichDoesNotApplyStillSaysWhatItWentWithoutTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The model as the issue writes it, with the row said not to come back rather than looped. */
    private static Compilation measured() {
        Compilation compilation = Compilation.ofSource("""
                module example.gap

                data Draft = { n: Int }
                data Done = { n: Int }

                behavior go : (request: Draft) -> Done

                example go
                    | (Draft { n = 1 }) -> Done { n = 1 }
                """, "Main");
        compilation.withJvmExampleDeadlines(DoesNotComeBack.overrunningOn(
                DoesNotComeBack.everythingAboutRowsOf("go")));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    /** Every measure counted over the rows has nothing to be about, which is the case this is for. */
    @Test
    void everyMeasureCountedOverTheRowsIsInapplicable() {
        AdequacyReport.BehaviorReport behavior =
                AdequacyReport.of(measured()).modules().get(0).behaviors().get(0);

        assertInstanceOf(Measure.NotApplicable.class, behavior.signature().counted(),
                "the output is not a sum");
        assertInstanceOf(Measure.NotApplicable.class, behavior.partition().partitioned(),
                "the rules divide no position");
        assertInstanceOf(Measure.NotApplicable.class, behavior.boundaryReadings(),
                "and draw no line");
        assertInstanceOf(Measure.NotApplicable.class, behavior.branch().measured(),
                "and there is no body");
        assertTrue(behavior.signature().weakening().isEmpty()
                        && behavior.partition().weakening().isEmpty()
                        && behavior.branch().measured().weakening().isEmpty(),
                "none of them is weaker for anything, which is what they are entitled to say");
    }

    /** And the reading of the rows carries what the run went without. */
    @Test
    void theReadingOfTheRowsCarriesIt() {
        AdequacyReport.BehaviorReport behavior =
                AdequacyReport.of(measured()).modules().get(0).behaviors().get(0);

        assertInstanceOf(Measurement.Partial.class, behavior.reading().measured(),
                "the row was read and ran past the deadline it was evaluated under");
        assertEquals(List.of(Incompleteness.Code.ROW_EVALUATION_LIMIT_REACHED),
                behavior.reading().gaps().stream()
                        .map(gap -> gap.fact().code()).toList());
        assertFalse(behavior.weakenedBy().isEmpty(),
                () -> "so the behavior went without something: " + behavior.weakenedBy());
    }

    /** So the two halves of the report say one thing. */
    @Test
    void theHeaderAndTheReasonUnderItAgree() {
        AdequacyReport report = AdequacyReport.of(measured());
        AdequacyReport.ModuleReport module = report.modules().get(0);

        assertEquals(MeasurementStatus.PARTIAL, module.status(),
                () -> "a row of this module did not come back: " + module.incompleteness().written());
        assertEquals(MeasurementStatus.PARTIAL, report.status());
        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED, report.adequacy(),
                "which the verdict already said, from a list the status could not see");
        assertEquals(List.of(Incompleteness.Code.ROW_EVALUATION_LIMIT_REACHED),
                module.incompleteness().written().stream().map(gap -> gap.fact().code()).toList());
    }

    /** And the document a build reads says it too. */
    @Test
    void theDocumentSaysBoth() {
        JsonNode root = JSON.readTree(AdequacyReport.of(measured())
                .json(souther.compiler.diag.SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        JsonNode module = root.get("modules").get(0);

        assertEquals("partial", module.get("status").asString());
        assertEquals(List.of("row_evaluation_limit_reached"),
                module.get("weakening").valueStream().map(JsonNode::asString).toList());
        assertEquals("row", module.get("incompleteness").get(0).get("scope").asString(),
                "and says which row, since a behavior may have more than one");
    }
}
