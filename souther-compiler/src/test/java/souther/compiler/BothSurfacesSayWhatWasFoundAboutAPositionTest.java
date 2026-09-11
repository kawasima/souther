package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a report found it could not read about a position is one reading, and both surfaces write
 * from it.
 *
 * <p>They used to be assembled twice. A person reading the report was shown two lists — every rule
 * this compiler could not turn into a line, and the positions it divided no way — and the document
 * was written from the second alone. So a rule left unread at a position the axes went on to
 * measure reached one reader and stopped there, and `souther examples --strict` and a person
 * reading the same run were told different things about the same position.
 */
class BothSurfacesSayWhatWasFoundAboutAPositionTest {

    /**
     * A position the axes measure, with a rule about it the reading could not take in.
     *
     * <p>The guard divides `n`, so the position has classes. The second guard compares a form the
     * reading does not take apart, which is a rule about `n` that no line came from — and which no
     * list of positions divided no way has an entry for, because this one was divided.
     */
    private static final String MEASURED_AND_UNREAD = """
            module t

            data Low
            data Accepted = { at: Int }

            behavior classify : (n: Int) -> Accepted | Low
                constructs Accepted

            let classify (n) = {
                guard n >= 60 else Low
                guard Int.clamp(0, 100, n) > 70 else Low
                Accepted { at = n }
            }
            """;

    /** A report and the texts the compile it is about was holding, which is what places it. */
    private record Made(AdequacyReport report, SourceRendering sources) {
    }

    private static Made reportOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return new Made(AdequacyReport.of(compilation),
                SourceRendering.namedByIdentity(compilation.texts()));
    }

    private static String humanOf(String source) {
        Made made = reportOf(source);
        return made.report().human(made.sources());
    }

    private static JsonNode partitionOf(String source) {
        Made made = reportOf(source);
        JsonNode document = JsonMapper.builder().build()
                .readTree(made.report().json(made.sources()));
        return document.get("modules").get(0).get("behaviors").get(0).get("partition");
    }

    /** Every position the document says something was not read about, as `position:reason`. */
    private static List<String> documentSaysNotRead(String source) {
        List<String> said = new ArrayList<>();
        partitionOf(source).get("notRead").forEach(each ->
                said.add(each.get("position").asString() + ":" + each.get("reason").asString()
                        + ":" + (each.has("rule") ? each.get("rule").asString() : "-")));
        return said;
    }

    @Test
    void aPositionOnlyTheHumanLineNamedIsInTheDocumentToo() {
        assertTrue(notReadAbout(humanOf(MEASURED_AND_UNREAD), "n"),
                humanOf(MEASURED_AND_UNREAD));

        // The handle too, because that is the half a person is shown. Keyed on the position and the
        // reason alone, two rules stopped alike here were one entry and the document could not say
        // which of them a reader was being told about.
        //
        // One entry, about the guard the author wrote. `Int.clamp` answers a number nothing states
        // anything about, so the comparison against it is a rule about a value an operation made.
        //
        // The reading this is made over keeps that operation standing, so the comparisons inside
        // `Int.clamp` are not here. They were: the call was spliced in, its own first comparison
        // asked whether `n` is below zero, and the guard above left nothing arriving at the line it
        // draws — so the document carried an entry about a rule the author never wrote, at a number
        // their model never mentions. Those comparisons are that operation's implementation, and a
        // caller owes rows for what a caller wrote.
        assertEquals(List.of("n:rule_about_a_derived_value:comparison@0:11:32"),
                documentSaysNotRead(MEASURED_AND_UNREAD),
                "the document says what the report said");
    }

    /**
     * Whether any {@code not read} line of {@code block} is about {@code position}.
     *
     * <p>Asked as a line rather than as a prefix. A finding about a rule names the rule first and
     * the position after it, and one about a position names the position — so a test matching
     * `+not read: <position>+` stopped meaning anything for the first kind rather than failing,
     * which is a negative assertion that passes because the words moved.
     */
    private static boolean notReadAbout(String block, String position) {
        return block.lines().anyMatch(line -> line.contains("not read:")
                && (line.contains("not read: " + position + " ")
                        || line.contains("about `" + position + "`")));
    }
}
