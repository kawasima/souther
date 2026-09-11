package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import souther.compiler.report.AdequacyReport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;

import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No {@code status} in a document stands over a weaker one beneath it.
 *
 * <p>Read off the document and not off the code that writes it. What each measure answered and what
 * the line above it says are two statements a reader compares, so the thing worth holding is that
 * they can be — a check written from the same method that assembles them would agree with whatever
 * that method did, including agreeing with it being wrong.
 *
 * <p>It was wrong. The measures gained an answer of their own about how much of their reading was
 * made, the verdict was moved onto it, and the line above them went on being assembled from the
 * entries — so a behavior whose two measures both said {@code partial} was published under
 * {@code complete}, in a corpus document checked in as the answer.
 *
 * <p>Only {@code complete} against {@code partial}. What {@code unavailable} means takes the reason
 * beside it — a measure nobody asked for and one that could not read what it needed write the same
 * word — and this document is what a consumer holds, so a check here reads what a consumer can.
 */
class AMeasurementIsNeverStrongerThanWhatItIsAssembledFromTest {

    private static final JsonMapper JSON = new JsonMapper();

    /** Where the corpus keeps the answers a build is held to. */
    private static final Path CORPORA =
            Path.of("src/test/resources/souther/compiler/conformance");

    /**
     * A behavior with a comparison this compiler cannot read, so a measure of it is made in part.
     *
     * <p>The shape the corpus has and the one the starter model was changed out of: a rule about a
     * value the inputs were put through, which the border measure is short of while showing the one
     * line it did draw.
     */
    private static final String READ_IN_PART = """
            module example.inpart

            data Days = Int
                invariant value >= 1 && value <= 28
            data Res = { n: Int }

            let over (a: Int, b: Days): Int = if a > b.value then a - b.value else 0

            behavior late : (lent: Days, from: Int, to: Int) -> Res
                constructs Res
            let late (lent, from, to) = Res { n = over(to - from, lent) }

            example late
                | "one" : (14, 1, 2) -> Res { n = 0 }
                | "lo" : (1, 1, 2) -> Res { n = 0 }
                | "hi" : (28, 1, 2) -> Res { n = 0 }
            """;

    /**
     * And one whose every rule this reads, with a line of its own.
     *
     * <p>The control. A marker on every border line says nothing about which behaviors it is
     * about, and the corpus has none to spare: each of its behaviors that draws a line also has a
     * rule this compiler could not read.
     */
    private static final String READ_IN_FULL = """
            module example.infull

            data Res = { n: Int }

            behavior plain : (x: Int) -> Res
                constructs Res
            let plain (x) = if x >= 10 then Res { n = 1 } else Res { n = 0 }

            example plain
                | "on" : (10) -> Res { n = 1 }
                | "off" : (9) -> Res { n = 0 }
                | "in" : (11) -> Res { n = 1 }
                | "out" : (0) -> Res { n = 0 }
            """;

    @Test
    void noCheckedInAnswerStandsOverAWeakerOne() throws IOException {
        List<Path> documents;
        try (Stream<Path> found = Files.walk(CORPORA)) {
            documents = found.filter(p -> p.getFileName().toString().equals("expected.report.json"))
                    .sorted().toList();
        }

        assertFalse(documents.isEmpty(), () -> "no corpus document under " + CORPORA.toAbsolutePath());
        for (Path document : documents) {
            assertEquals(List.of(), contradictionsIn(JSON.readTree(Files.readString(document)), ""),
                    document + ": a status standing over a weaker one");
        }
    }

    /** And the same of a report as it is written, so a corpus that stops holding such a behavior
     *  does not quietly stop asking. */
    @Test
    void aBehaviorOneOfWhoseMeasuresWasReadInPartSaysSoAboveThem() {
        assertEquals("partial", statusOfTheOneBehaviorIn(READ_IN_PART));
        assertEquals(List.of(), contradictionsIn(documentOf(READ_IN_PART), ""));
    }

    /** The control. A model this reads to the end says so, which is what makes the answer above an
     *  answer about the model and not about every report. */
    @Test
    void andOneThisReadsToTheEndSaysThat() {
        assertEquals("complete", statusOfTheOneBehaviorIn(READ_IN_FULL),
                () -> humanOf(READ_IN_FULL));
    }

    /**
     * The human report says it too, of the measure that came to numbers and was not made in full.
     *
     * <p>Over the corpus and not a model of this test's own. What produces this is a rule the
     * compiler could not read beside lines it did draw, which is a shape that takes a real model to
     * reach — written here, the fixture would be chosen to produce the sentence it then asserts.
     *
     * <p>The numbers alone read the same either way, which is what a measure-level answer exists to
     * stop: {@code borders 5   coverage items 5/10} is what a model read to the end gets.
     */
    @Test
    void andTheHumanReportSaysWhichMeasureWasNotMadeInFull() {
        String human = humanOfTheCorpus();

        assertTrue(human.contains("(not all of it was measured)"), human);
        // And a model read to the end says nothing, which is what makes the line above an answer
        // about a behavior rather than a decoration on every border.
        String full = humanOf(READ_IN_FULL);
        assertTrue(full.contains("border      borders"), full);
        assertFalse(full.contains("(not all of it was measured)"), full);
    }

    /**
     * Every place a {@code status} of {@code complete} has a {@code partial} anywhere beneath it.
     *
     * <p>Beneath and not beside: what a status is assembled from is what its own object holds, so a
     * measure of one behavior says nothing about the behavior next to it.
     */
    private static List<String> contradictionsIn(JsonNode at, String path) {
        List<String> out = new ArrayList<>();
        if (at.isObject() && "complete".equals(at.path("status").asString(null))
                && hasBeneath(at, "partial")) {
            out.add(path.isEmpty() ? "<root>" : path);
        }
        at.propertyStream().forEach(field ->
                out.addAll(contradictionsIn(field.getValue(), path + "/" + field.getKey())));
        int index = 0;
        for (JsonNode item : at.values()) {
            if (at.isArray()) {
                out.addAll(contradictionsIn(item, path + "[" + index++ + "]"));
            }
        }
        return out;
    }

    private static boolean hasBeneath(JsonNode at, String word) {
        for (JsonNode child : children(at)) {
            if (word.equals(child.path("status").asString(null)) || hasBeneath(child, word)) {
                return true;
            }
        }
        return false;
    }

    private static List<JsonNode> children(JsonNode at) {
        return new ArrayList<>(at.values());
    }

    private static JsonNode documentOf(String source) {
        return JSON.readTree(reportOf(source).json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
    }

    private static String statusOfTheOneBehaviorIn(String source) {
        return documentOf(source).path("modules").get(0).path("behaviors").get(0)
                .path("status").asString();
    }

    /**
     * One of the corpora the checked-in answers are about, read as a person reads it.
     *
     * <p>Taken by name from where the models are declared, so what it is made of and in what order
     * is the corpus's own answer. Listed here, the files would be a second account of that, and a
     * file added to the corpus would leave this reading a model nobody writes any more.
     */
    private static String humanOfTheCorpus() {
        List<String> sources = ConformanceCorpus.sourcesOf("catalog");
        Compilation compilation = Compilation.ofSources(sources, ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    private static String humanOf(String source) {
        return reportOf(source).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    private static AdequacyReport reportOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }
}
