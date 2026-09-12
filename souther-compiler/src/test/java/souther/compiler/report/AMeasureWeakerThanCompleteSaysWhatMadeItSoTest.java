package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Measure;
import souther.compiler.query.Measurement;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a document says about a measurement, held to what the specification promises.
 *
 * <p>Four words stand for five states, and the document is only readable because a
 * {@code weakening} stands beside the word: {@code unavailable} with a reason of not-measured and no
 * weakening is a measurement nobody asked for, and the same with one is a measurement that was
 * asked for and could not be finished. Read over what the writer actually emits rather than over
 * the model, because it is the document a build reads and the projection is where the two could
 * come apart (spec §example-report-vocabulary).
 */
class AMeasureWeakerThanCompleteSaysWhatMadeItSoTest {

    /**
     * A row that never comes back, beside behaviors that were measured to the end.
     *
     * <p>The helper loops, so the row's evaluation is stopped rather than finished and what it went
     * through goes with it — which is what leaves a measure with a number it cannot be trusted for.
     * A model every measure of which came to an answer would let every rule below pass by never
     * being asked, which is why the run is held to producing each state.
     */
    private static final String MODEL = """
            module example.loop

            data Draft = { n: Int }
            data Done = { n: Int }
            data Small = { n: Int }

            partial let spin (n: Int): Int = spin(n)

            behavior go : (request: Draft) -> Done | Small
                constructs Done, Small

            let go (request) = {
                guard request.n <= 0 else Done { n = spin(request.n) }
                Small { n = request.n }
            }

            behavior unwritten : (request: Draft) -> Done | Small
                constructs Done, Small

            example go
                | (Draft { n = 1 }) -> Done { n = 1 }
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * Steps enough for every row of {@link #MODEL} that comes to an answer, and few enough that the
     * one that does not is done spending them at once.
     *
     * <p>What the model is here for is a row whose evaluation is stopped rather than finished, and
     * the default limit is a hundred million steps — so at the default this class spends most of a
     * minute interpreting {@code spin} to reach a conclusion it reaches just as well at a
     * thousandth of it. Which number the default is set to is not what is being checked here; that
     * it is reached and reported is, and {@code ARowIsHeldToStepsAndNotToTheClock} is where the
     * limit itself is held to anything.
     */
    private static final EvaluationPolicy BOUNDED =
            EvaluationPolicy.DEFAULT.withStepLimit(100_000L);

    /**
     * The report of {@link #MODEL}, built once for the class.
     *
     * <p>Read and never written by the tests below — it is a record over copied lists, and every
     * accessor they reach for derives its answer rather than keeping one — so building it per test
     * only pays for the same compilation again.
     */
    private static AdequacyReport report;

    @BeforeAll
    static void theModelIsMeasured() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.withEvaluationPolicy(BOUNDED);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        report = AdequacyReport.of(compilation);
    }

    /**
     * Every measurement in the JSON, held to the two rules the specification writes as MUST.
     *
     * <p>Walked over the document rather than listed, so a measure added later is in it. What makes
     * an object a measurement is that it carries {@code status} — which is exactly what the writer
     * puts there.
     */
    @Test
    void everyMeasurementInTheDocumentSaysWhatItIsAndWhatItWentWithout() throws Exception {
        List<JsonNode> measurements = new ArrayList<>();
        collect(JSON.readTree(report.json(SourceRendering.namedByIdentity(SourceLayouts.NONE))), measurements);
        assertTrue(measurements.size() > 10,
                "the model produces measurements of every kind: " + measurements.size());
        // What this walk is worth is what it met. A run in which no measure came back weaker than
        // complete would pass every rule below by never reaching it, which is the shape of a check
        // that says nothing.
        List<String> statuses = measurements.stream()
                .map(each -> each.get("status").asString()).toList();
        assertTrue(statuses.contains("partial"), () -> "no measure was made in part: " + statuses);
        assertTrue(statuses.contains("complete"), () -> "none was made in full: " + statuses);
        assertTrue(statuses.contains("unavailable"), () -> "none came back with no number: "
                + statuses);

        for (JsonNode each : measurements) {
            String status = each.get("status").asString();
            JsonNode weakening = each.get("weakening");
            switch (status) {
                case "complete" -> {
                    assertNull(each.get("reason"), () -> "a measure with a number says why: " + each);
                    assertNull(weakening, () -> "and went without nothing: " + each);
                }
                case "partial" -> {
                    assertNull(each.get("reason"), () -> "a measure with a number says why: " + each);
                    assertNotNull(weakening,
                            () -> "a measure made in part does not say what by: " + each);
                    assertFalse(weakening.isEmpty(), () -> "and says it with nothing: " + each);
                }
                case "unavailable" -> assertNotNull(each.get("reason"),
                        () -> "a measure with no number does not say why: " + each);
                default -> throw new AssertionError("a status the schema does not name: " + status);
            }
            if (weakening != null) {
                List<String> words = new ArrayList<>();
                weakening.forEach(word -> words.add(word.asString()));
                assertEquals(words.stream().distinct().toList(), words,
                        () -> "a kind said twice, which counts paths rather than facts: " + each);
            }
        }
    }

    /**
     * The one thing a document cannot say: a measure with no number, and nothing to say whether it
     * was never started or could not be finished.
     *
     * <p>Not a rule about the writer so much as about the reader. Both are {@code unavailable} and
     * both give a reason, and only the weakening beside them separates a measurement nobody asked
     * for from one that went without what it needed — which is what a reader deciding how far to
     * trust the numbers around it came for.
     */
    @Test
    void aMeasurementThatCouldNotBeFinishedIsTheOneUnavailableWithAWeakening() {
        List<Measure<?>> failed = new ArrayList<>();
        for (AdequacyReport.ModuleReport module : report.modules()) {
            for (AdequacyReport.BehaviorReport behavior : module.behaviors()) {
                if (behavior.branch() != null) {
                    failed.add(behavior.branch().measured());
                }
                if (behavior.partition() != null) {
                    failed.add(behavior.partition().partitioned());
                }
                if (behavior.evidence().account() != null) {
                    failed.add(behavior.evidence().account());
                }
                if (behavior.boundaryReadings() != null) {
                    failed.add(behavior.boundaryReadings());
                }
            }
        }
        for (Measure<?> each : failed) {
            boolean unfinished = each instanceof Measurement.FailedToMeasure<?>;
            assertEquals(unfinished, !each.weakening().isEmpty() && each.made().isEmpty(),
                    () -> "only a measurement that could not be finished is a no-number that went"
                            + " without something: " + each);
            if (unfinished) {
                assertEquals(souther.compiler.observe.MeasurementStatus.NOT_MEASURED,
                        AdequacyReport.statusOf(each),
                        "and it is written under the word for a measurement nobody made");
            }
        }
    }

    /**
     * What a build refuses over is what a measure established, and nothing else.
     *
     * <p>A finding carries what weakened the measurement that produced it, and the three-way answer
     * is read off that: a kind the bar refuses, from a measurement nothing weakened, is a gap;
     * the same kind from a measurement that went without something is undecided. Held over every
     * finding of the model, since this used to be worked out from a status word and is now the
     * finding's own.
     */
    @Test
    void whatABuildRefusesOverIsWhatNothingWeakened() {
        List<Adequacy.Finding> findings = report.findings();
        assertFalse(findings.isEmpty(), "the model produces findings");
        for (Adequacy.Finding each : findings) {
            Adequacy.Finding.Disposition said = each.disposition();
            if (!Adequacy.refuses(each.kind())) {
                assertEquals(Adequacy.Finding.Disposition.REPORTED, said, each::toString);
                continue;
            }
            assertEquals(each.weakenedBy().isEmpty()
                            ? Adequacy.Finding.Disposition.REFUSED
                            : Adequacy.Finding.Disposition.UNDECIDED,
                    said, each::toString);
        }
    }

    /** Every object of the document that is a measurement, which is every one carrying a status. */
    private static void collect(JsonNode node, List<JsonNode> into) {
        if (node.isObject()) {
            if (node.get("status") != null && node.get("status").isString()) {
                into.add(node);
            }
            node.properties().forEach(entry -> collect(entry.getValue(), into));
        } else if (node.isArray()) {
            node.forEach(each -> collect(each, into));
        }
    }
}
