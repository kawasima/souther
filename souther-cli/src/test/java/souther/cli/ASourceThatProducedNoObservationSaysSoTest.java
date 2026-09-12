package souther.cli;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.observe.Incompleteness;
import souther.compiler.publish.PublishedIncompleteness;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reason is named for what happened, and a source that produced no observation is not a missing
 * runtime.
 *
 * <p>Where a source is not evaluated, two places turn that into a reason, and both used to write
 * the code a caught {@code LinkageError} writes. At that point the runtime cannot be what happened:
 * the evaluation is asked only of a module that checked, so a source with no observation failed
 * something earlier, and a {@code LinkageError} is caught per example and recorded against the
 * behavior it was thrown for. The code was named after one of its causes and then reused for the
 * state, and every producer since widened it.
 */
class ASourceThatProducedNoObservationSaysSoTest {

    /** A module and an attached file that declares a value the module already declares. The rows in
     * the attached file are never evaluated, and the runtime is present throughout. */
    private static final String MODULE = """
            module example.split

            data Amount = Int
                invariant value >= 0 && value <= 1000

            data Draft = { cost: Amount }
            data Ok = { n: Int }

            let shared = Draft { cost = Amount(7) }

            behavior take : (request: Draft) -> Ok
                constructs Ok

            let take (request) = Ok { n = request.cost.value }

            example take
                | (Draft { cost = Amount(7) }) -> Ok { n = 7 }
            """;

    private static final String ATTACHED = """
            examples for example.split

            let shared = Draft { cost = Amount(0) }

            example take
                | (Draft { cost = Amount(0) }) -> Ok { n = 0 }
            """;

    /** One behavior that checks and one that does not: something to report on, and a source with no
     * observation to report about it. */
    private static final String ONE_BEHAVIOR_DOES_NOT_CHECK = """
            module example.partly

            data Amount = Int
                invariant value >= 0 && value <= 1000

            data Draft = { cost: Amount }
            data Ok = { n: Int }

            behavior take : (request: Draft) -> Ok
                constructs Ok

            let take (request) = Ok { n = request.cost.value }

            behavior other : (request: Draft) -> Ok
                constructs Ok

            let other (request) = Ok { n = request.cost.nope }

            example take
                | (Draft { cost = Amount(7) }) -> Ok { n = 7 }
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void theReasonIsNamedForTheObservationAndNotForTheRuntime() {
        Compilation compilation = Compilation.ofSources(List.of(MODULE, ATTACHED),
                souther.compiler.meta.ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();

        List<PublishedIncompleteness> gaps =
                AdequacyReport.of(compilation).modules().get(0).incompleteness().written();

        assertEquals(1, gaps.size(), gaps.toString());
        Incompleteness.Fact only = gaps.get(0).fact();
        assertEquals(Incompleteness.Code.OBSERVATION_ABSENT, only.code(),
                "the runtime is on this classpath; what happened is that a source was not read");
        assertEquals(Incompleteness.Scope.SOURCE, only.scope());
    }

    /** And the word the schema allows is the word that is written. */
    @Test
    void theJsonWritesTheNewWord() throws Exception {
        Streams ran = run(List.of(MODULE, ATTACHED), "--format", "json");

        JsonNode gaps = JSON.readTree(ran.out()).get("modules").get(0).get("incompleteness");
        assertEquals("observation_absent", gaps.get(0).get("code").asString(), ran.out());
    }

    /**
     * The line a person reads says what happened, in words.
     *
     * <p>It printed the code's own name beside a source index, which between them told a reader
     * with a type error that their runtime was missing. Every other line of this report is a
     * sentence.
     */
    @Test
    void theHumanLineIsASentenceAndNotACodeName() throws Exception {
        Streams ran = run(List.of(ONE_BEHAVIOR_DOES_NOT_CHECK));

        assertFalse(ran.out().contains("observation_absent"),
                "no code name reaches a person: " + ran.out());
        assertFalse(ran.out().contains("linkage_failed"), ran.out());
        assertTrue(ran.out().contains("no rows were read from"),
                "what happened, said as what happened: " + ran.out());
    }

    /**
     * And {@code --generate} says why it wrote nothing.
     *
     * <p>It wrote nothing at all. The filling carried the reason, and
     * {@code GenerationResult.isEmpty} counted only rows and unresolved combinations, so a result
     * that was nothing but a reason was empty and the whole block was dropped — leaving an author
     * who asked what to write with silence, and no way to tell it from having nothing to write.
     */
    @Test
    void generateSaysWhyItWroteNothing() throws Exception {
        Streams ran = run(List.of(ONE_BEHAVIOR_DOES_NOT_CHECK), "--generate");

        assertTrue(ran.out().contains("generation stopped"),
                "the reason is what there is to say: " + ran.out());
    }

    /** And says it the way the report says it. The note is a comment in the source an author is
     * about to write in, which is no place for an enum's name either. */
    @Test
    void theGeneratedNoteReadsTheSameWayTheReportDoes() throws Exception {
        Streams ran = run(List.of(ONE_BEHAVIOR_DOES_NOT_CHECK), "--generate");

        assertFalse(ran.out().contains("observation_absent"),
                "one wording, written once: " + ran.out());
        assertTrue(ran.out().contains("// generation stopped for `take`: no rows were read from"),
                ran.out());
    }

    private record Streams(int code, String out, String err) {}

    private static Streams run(List<String> texts, String... extraArgs) throws Exception {
        Path dir = Files.createTempDirectory("souther-examples");
        List<String> args = new ArrayList<>(List.of("examples"));
        args.addAll(List.of(extraArgs));
        for (int i = 0; i < texts.size(); i++) {
            Path file = dir.resolve(i == 0 ? "model.sou" : "attached" + i + ".sou");
            Files.writeString(file, texts.get(i));
            args.add(file.toString());
        }
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        int code;
        try {
            code = Main.dispatch(args.toArray(String[]::new));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return new Streams(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }
}
