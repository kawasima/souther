package souther.compiler;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule of a decision this compiler could not compose a row for is said, in its own words.
 *
 * <p>Three axes and the states of one may not be written into another. Whether a row is owed at a
 * rule is what a search settles; whether one was composed for it is what this compiler managed. A
 * rule the search left unsettled is neither covered nor a gap — it is owed no row and no author is
 * asked for one — and that is exactly why it has to be said: a count of the body's rules with
 * nothing under some of them is a difference a reader can do nothing with, and a document answering
 * {@code taken: false} and nothing beside it reads as a gap.
 *
 * <p>The model is a body that reads one dependency at two calls and decides on both answers. One of
 * its ways needs the dependency to answer differently at each, which is a table written once for a
 * module rather than a line on a row, and nothing here writes one. So that way's rule goes
 * unsettled while its neighbours are settled, which is what makes the sentence about this rule
 * rather than about the behavior.
 */
class ARuleNothingCouldComposeARowForIsSaidAndNotDroppedTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * A body deciding on one dependency's answer at two calls.
     *
     * <p>The outer {@code match} reads what the dependency answers for the input, and the inner one
     * reads what it answers for a number of the body's own. A row states one answer for the whole
     * of its run, so the way through {@code Cleared} and then {@code Blocked} is the one no row
     * this composes takes.
     */
    private static final String TWO_CALLS = """
            module example.stood

            data Yes
            data No
            data Answer = Yes | No

            data Blocked = Int
            data Cleared

            behavior lookup : (id: Int) -> Blocked | Cleared

            behavior decides : (id: Int) -> Answer
                depends on lookup
            let decides (id, lookup) =
                if id > 5 then
                    match lookup(id) with
                        | Blocked -> No
                        | Cleared ->
                            match lookup(0) with
                                | Blocked -> Yes
                                | Cleared -> No
                else No
            """;

    @Test
    void thePageSaysWhatTheSearchCameToAtTheRuleItSettledNothingAbout() {
        List<String> decision = decisionSection(human(TWO_CALLS));

        assertTrue(decision.stream().anyMatch(line -> line.contains(
                        "nothing could show a row can be written at a decision rule")
                        && line.contains("answer by what it was applied to")),
                () -> "the way needing two answers is said in the words the search came back"
                        + " with: " + decision);
    }

    @Test
    void everyRuleTheCountHoldsHasALineUnderIt() {
        List<String> decision = decisionSection(human(TWO_CALLS));

        assertEquals(4, decision.stream()
                        .filter(line -> line.contains("decision rule")).count(),
                () -> "the body states four rules and no row takes one, so the page has a line"
                        + " apiece: " + decision);
    }

    @Test
    void theDocumentSaysWhichOfThemARowIsOwedAt() {
        JsonNode rules = JSON.readTree(json(TWO_CALLS))
                .get("modules").get(0).get("behaviors").get(1)
                .get("decision").get("obligations");

        List<String> said = new ArrayList<>();
        // What the entry says, and a word of this test's own where it says nothing. Read as a
        // value that must be there, an entry carrying no word would come back as this test
        // failing to read a document rather than as the document not saying what a rule is owed.
        rules.forEach(rule -> said.add(rule.get("requirement") == null ? "nothing said"
                : rule.get("requirement").stringValue()));
        // Counted rather than read off the positions. Which rule of the four the search could not
        // compose for is what this is about; where the document writes it is the order the body's
        // ways were read in, and an expectation spelled that way would be about that order too.
        assertEquals(List.of(1L, 3L, 0L),
                List.of(said.stream().filter("unsettled"::equals).count(),
                        said.stream().filter("required"::equals).count(),
                        said.stream().filter("excluded"::equals).count()),
                () -> "one of the four ways is one nothing could compose a row for and the three"
                        + " beside it are owed one: " + said);
    }

    /**
     * A body whose {@code match} has an arm for a case the position's own rules refuse.
     *
     * <p>Nothing of {@code Active} is ever {@code Off}, so the way down that arm is one no row
     * anybody writes takes. The arms are already counted without it, and the rules are the same
     * fact read again.
     */
    private static final String REFUSED = """
            module example.narrowed

            data On
            data Off
            data Pending
            data Flag = On | Off | Pending
            data Active = Flag invariant value /= Off
            data Answer = Int

            behavior pick : (f: Active) -> Answer
                constructs Answer

            let pick (f) = match f.value with
                | On      -> Answer(1)
                | Pending -> Answer(0)
                | Off     -> Answer(9)

            example pick
                | "on" : (Active(On)) -> Answer(1)
            """;

    @Test
    void aRuleTheModelsOwnRulesRefuseIsSaidToBeOwedNoRow() {
        JsonNode rules = JSON.readTree(json(REFUSED))
                .get("modules").get(0).get("behaviors").get(0)
                .get("decision").get("obligations");

        List<String> said = new ArrayList<>();
        rules.forEach(rule -> said.add(rule.get("requirement") == null ? "nothing said"
                : rule.get("requirement").stringValue()));
        assertTrue(said.contains("excluded"),
                () -> "the way down the arm nothing reaches is owed no row, and saying nothing"
                        + " settled it would put the model's own answer in this compiler's"
                        + " mouth: " + said);
        assertFalse(said.contains("unsettled"),
                () -> "which is the model answering rather than a search coming up short: " + said);
    }

    /** The lines of the one implemented behavior's decision measure. */
    private static List<String> decisionSection(String page) {
        List<String> out = new ArrayList<>();
        boolean inside = false;
        for (String line : page.split("\n")) {
            if (line.startsWith("    decision ")) {
                inside = true;
            } else if (inside && !line.startsWith("      ") && !line.startsWith("          ")) {
                inside = false;
            }
            if (inside) {
                out.add(line);
            }
        }
        return out;
    }

    private static String human(String model) {
        Compilation compilation = measured(model);
        return AdequacyReport.of(compilation)
                .human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    private static String json(String model) {
        Compilation compilation = measured(model);
        return AdequacyReport.of(compilation)
                .json(SourceRendering.namedByIdentity(compilation.texts()));
    }

    private static Compilation measured(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(List.of(), compilation.errors().stream()
                        .map(e -> e.diagnostic().code().toString()).toList(),
                "the model under test compiles");
        return compilation;
    }
}
