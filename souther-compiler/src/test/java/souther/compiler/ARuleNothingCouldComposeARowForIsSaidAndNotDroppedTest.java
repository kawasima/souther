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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                        "nothing could show a row can be written at 1 decision rule")),
                () -> "the way needing two answers is counted as one nothing settled: "
                        + decision);
        assertTrue(decision.stream().anyMatch(line ->
                        line.contains("answer by what it was applied to")),
                () -> "under the words the search came back with: " + decision);
    }

    @Test
    void theCountCloses() {
        // Over the shapes a rule can be in and not over one model. The page enumerates what an
        // author can act on and counts the rest under the reason each came to, so what is held is
        // that the numbers add up to the ways the body has — not that there is a line apiece.
        for (String model : List.of(TWO_CALLS, REFUSED, bothWays("On"), bothWays("Off"))) {
            List<String> decision = decisionSection(human(model));
            String headline = decision.get(0);
            int rules = Integer.parseInt(headline.replaceAll(".*rules +(\\d+).*", "$1"));
            int taken = Integer.parseInt(headline.replaceAll(".*taken +(\\d+).*", "$1"));
            int findings = (int) decision.stream()
                    .filter(line -> line.contains("no row takes a decision rule")).count();
            int gathered = gatheredIn(decision);

            assertEquals(rules - taken, findings + gathered,
                    () -> "the ways no row took are the findings and what is counted beside"
                            + " them: " + decision);
        }
    }

    /** How many ways the block counts under an answer rather than writing out. */
    private static int gatheredIn(List<String> decision) {
        return decision.stream()
                .filter(line -> line.startsWith("      ")
                        && !line.startsWith("          ")
                        && line.matches(".*\\b\\d+ decision rules?\\b.*"))
                .mapToInt(line -> Integer.parseInt(
                        line.replaceAll(".*?(\\d+) decision rules?\\b.*", "$1")))
                .sum();
    }

    @Test
    void whatTheComposingFellShortOnIsNotWhatTheRequirementAnswers() {
        JsonNode rules = JSON.readTree(json(TWO_CALLS))
                .get("modules").get(0).get("behaviors").get(1)
                .get("decision").get("obligations");

        JsonNode unsettled = null;
        for (JsonNode rule : rules) {
            if ("unsettled".equals(rule.get("requirement").stringValue())) {
                unsettled = rule;
            }
        }
        assertNotNull(unsettled, () -> "the way needing two answers is the one nothing settled: "
                + rules);
        // The requirement says the inquiry had no candidate, and that is the whole of what it
        // says. What this compiler fell short on is the other axis: a way it writes no table for
        // may be the easiest row in the file to write by hand, and a requirement carrying that
        // reason would be the generator's failure answering whether a row is owed.
        assertEquals("nothing_was_composed_to_try", unsettled.get("because").stringValue());
        assertEquals("a_table_is_what_this_needs",
                unsettled.get("synthesisShortfall").stringValue());
    }

    @Test
    void aRuleOwedARowCarriesNoShortfall() {
        JsonNode rules = JSON.readTree(json(TWO_CALLS))
                .get("modules").get(0).get("behaviors").get(1)
                .get("decision").get("obligations");

        rules.forEach(rule -> {
            if ("required".equals(rule.get("requirement").stringValue())) {
                assertNull(rule.get("synthesisShortfall"),
                        () -> "a row was composed for it and run, so nothing fell short: " + rule);
                assertNull(rule.get("because"),
                        () -> "and what settles it is the row that was seen standing in: " + rule);
            }
        });
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

    /**
     * The same fork, carried by a helper two call sites reach it differently at.
     *
     * <p>{@code look} is applied to a position whose rules refuse {@code Off} and to one that does
     * not, so the arm for {@code Off} stands at two places and nothing arrives at one of them. The
     * arm is one the author wrote once, and a row through the second call site goes through it.
     *
     * @param first which of the two is written first, which nothing about the model turns on
     */
    private static String bothWays(String first) {
        String open = "        | On  -> look(open.value)\n";
        String shut = "        | Off -> look(shut)\n";
        return """
                module example.copies

                data On
                data Off
                data Flag = On | Off
                data Open = Flag invariant value /= Off
                data Answer = Int

                behavior pick : (open: Open, shut: Flag) -> Answer
                    constructs Answer

                let look (f: Flag): Answer = match f with
                    | On  -> Answer(1)
                    | Off -> Answer(9)

                let pick (open, shut) = match shut with
                """
                + ("On".equals(first) ? open + shut : shut + open)
                + """

                example pick
                  | "on" : (Open(On), On) -> Answer(1)
                """;
    }

    @Test
    void anArmOneCallSiteCannotReachIsNotAnArmNothingReaches() {
        for (String first : List.of("On", "Off")) {
            JsonNode rules = JSON.readTree(json(bothWays(first)))
                    .get("modules").get(0).get("behaviors").get(0)
                    .get("decision").get("obligations");

            List<String> said = new ArrayList<>();
            rules.forEach(rule -> said.add(rule.get("requirement") == null ? "nothing said"
                    : rule.get("requirement").stringValue()));
            assertFalse(said.contains("excluded"),
                    () -> "a row through the other call site goes through the arm, so nothing"
                            + " shows the ways down it out of reach (" + first + " first): "
                            + said);
        }
    }

    @Test
    void andTheAnswerIsTheSameWhicheverCallSiteIsWrittenFirst() {
        // As a tally and not in the document's order. Writing the two arms the other way round is
        // the same set of ways in another order, so the entries move with the source and what is
        // being held here is that no answer changes with which copy of the arm a walk meets first.
        assertEquals(requirementsOf(bothWays("On")).stream().sorted().toList(),
                requirementsOf(bothWays("Off")).stream().sorted().toList(),
                "which copy of an arm a walk meets first is not something the account is a"
                        + " function of");
    }

    /** What the document says a row is owed at, rule by rule. */
    private static List<String> requirementsOf(String model) {
        JsonNode rules = JSON.readTree(json(model))
                .get("modules").get(0).get("behaviors").get(0)
                .get("decision").get("obligations");
        List<String> said = new ArrayList<>();
        rules.forEach(rule -> said.add(rule.get("requirement") == null ? "nothing said"
                : rule.get("requirement").stringValue()));
        return said;
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
