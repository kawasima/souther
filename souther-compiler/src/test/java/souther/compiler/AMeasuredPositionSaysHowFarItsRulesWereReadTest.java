package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A position the axes measure says how far its rules were read.
 *
 * <p>The classes beside a position mean one thing on a reading that ran to the end and another on
 * one that did not: a class arrived at from part of the rules is a value the model singled out, and
 * a rule that went unread may yet refuse it. The axis has carried that since it was measured, with
 * nothing reading it — so two classes were offered and neither surface said what they rested on.
 *
 * <p>Said apart from a position with no axis at all. Both are readings that did not finish, and
 * they are not the same thing to act on: reading the same sentence about a position with three
 * classes as about one with none says the numbers beside it are not to be believed.
 */
class AMeasuredPositionSaysHowFarItsRulesWereReadTest {

    /**
     * A position the axes measure whose rules were not read in full.
     *
     * <p>Two clauses about one value, and this reading owns both. The first names the values, so the
     * position divides into them and is measured; the second states nothing that could be typed, so
     * it reached no reading here and which values it would have refused is unknown. The classes are
     * what the model was read to say and the rule that went unread may yet refuse one of them.
     *
     * <p><b>A rule this reading owns, and not one it handed on.</b> The fixture used to be an
     * {@code Assignee?}, whose rules the reading below the option reads and the position above was
     * told it had not reached — which is the defect this measure was reporting rather than a state a
     * model can be in (#1072). What inhabits this word is a reader that got as far as deriving the
     * classes and then lost a clause of its own.
     */
    private static final String MEASURED_IN_PART = """
            module o

            data Assignee = String
                invariant named = value == "ada" || value == "bob"
                invariant unreadable = value == 1

            data Issue = { assignee: Assignee }
            data Accepted = { at: Int }

            behavior classify : (i: Issue) -> Accepted
            """;

    /** The control: the same shape with nothing left unread. */
    private static final String READ_IN_FULL = """
            module u

            data Low
            data Accepted = { at: Int }

            behavior classify : (n: Int) -> Accepted | Low
                constructs Accepted

            let classify (n) = {
                guard n >= 60 else Low
                Accepted { at = n }
            }
            """;

    private static AdequacyReport reportOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }

    private static String humanOf(String source) {
        return reportOf(source).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    private static JsonNode partitionOf(String source) {
        JsonNode document = JsonMapper.builder().build()
                .readTree(reportOf(source).json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        return document.get("modules").get(0).get("behaviors").get(0).get("partition");
    }

    @Test
    void aMeasuredPositionIsToldApartFromOneWithNoAxis() {
        JsonNode axis = partitionOf(MEASURED_IN_PART).get("axes").get(0);
        assertEquals("i.assignee", axis.get("path").asString());
        assertEquals(2, axis.get("classes").size(), axis.toString());
        assertEquals("partial", axis.get("read").get("extent").asString(),
                "the axis says that something about its position's rules is left standing");
        // Which of the two it is, and not only that there is one. The clause that went unread
        // reached no reading at all, so there is no rule this read and could not use, and saying so
        // would publish a cause this was not observed to have.
        assertTrue(axis.get("read").get("rulesNotReached").asBoolean(), axis.toString());
        assertFalse(axis.get("read").has("unanswered"),
                "there is no rule to name, because nothing was seen: " + axis);

        String human = humanOf(MEASURED_IN_PART);
        assertTrue(human.contains("rules not reached: i.assignee"),
                "a measured position says its rules were never reached, which a position with no"
                        + " axis does not: " + human);
        assertFalse(notReadAbout(human, "i.assignee"),
                "and is not said as one nothing divided: " + human);
    }

    /** Nothing is invented: a position whose rules were read in full gains no line either way. */
    @Test
    void aPositionWhoseRulesWereReadInFullGainsNoLine() {
        JsonNode read = partitionOf(READ_IN_FULL).get("axes").get(0).get("read");
        assertEquals("complete", read.get("extent").asString());
        assertFalse(read.has("rulesNotReached"), "nothing was left standing: " + read);
        assertFalse(read.has("unanswered"), "nothing was left standing: " + read);
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
    /**
     * And the model that leaves a rule unread at a position it measured is one this compiler
     * refuses.
     *
     * <p>Said out loud, because it is what the word means now and not an accident of the fixture.
     * A rule written under a container, a case or an optional is read where it governs, one position
     * down (#1072); a rule this compiler cannot find a value for is a shortfall of its own. What is
     * left is a clause the front end could not type — and a model carrying one is refused, so a
     * document that says a measured position went short of a rule is a document about a model that
     * did not compile.
     *
     * <p>A tripwire and not a preference. The day a clause can go unread in a model that compiles,
     * this fails and whoever made it so is the one who should decide what the word means then.
     */
    @Test
    void theModelThatSaysSoIsOneThisCompilerRefuses() {
        assertTrue(isRefused(MEASURED_IN_PART),
                "a measured position short of a rule takes a clause nothing could type");
        assertFalse(isRefused(READ_IN_FULL), "and the control is a model that compiles");
    }

    /** Whether this compiler refuses {@code source}, which is what the fixtures above turn on. */
    private static boolean isRefused(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        return !compilation.diagnostics().values().stream()
                .flatMap(java.util.List::stream).toList().isEmpty();
    }

}
