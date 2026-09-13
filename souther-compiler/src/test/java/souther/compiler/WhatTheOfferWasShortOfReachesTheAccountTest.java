package souther.compiler;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.diag.SourceRendering;
import souther.compiler.partition.ReportedShortfall;
import souther.compiler.partition.StringOfferShortfall;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The account says why a search had less than the rules leave to try, and not only that it had.
 *
 * <p>What the search came to and why the offer was short of the rules are two axes. A rule this
 * compiler could not read and a rule it read from end to end whose machine it would not build are
 * one category — every value tried was refused, and the values tried were not everything — and they
 * are different work: one is rewritten and one is allowed more. Published as the category alone,
 * the page and the document say the same thing of both.
 *
 * <p>The point rows of the account are not the other half. They say what reading a position met,
 * which is a different question asked by a different walk: the limit a machine ran into is said
 * there of the rules of the position met with each other, where this says which rule was being
 * built. So what is held here is that the decision-level sentence is complete on its own.
 */
class WhatTheOfferWasShortOfReachesTheAccountTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A rule about the strings written in a construct this compiler's reader does not enter. */
    private static final String OUTSIDE_THE_SUBSET = "String.matches(\"(a+)\\\\1\", value)";

    /** One it reads and will not build a machine for, which is the other way to be short. */
    private static final String MORE_THAN_A_WITNESS_MAY_SPEND =
            "String.matches(\"a{60000}\", value)";

    private static String model(String rule) {
        return """
                module example.offer

                data Code = String
                    invariant shape = RULE

                data Flag = Yes | No

                data T = { flag: Flag, code: Code }

                data Ok

                behavior look : (t: T) -> Ok

                let look (t) = Ok
                """.replace("RULE", rule);
    }

    /**
     * A body whose every rule about the position was read, and every value they leave was refused.
     *
     * <p>The other half of the pair, and the control for every claim below: nothing here was short
     * of anything, so an entry saying what the offer was short of would be one written for a search
     * that had everything.
     */
    private static final String NOTHING_WAS_SHORT = """
            module example.refused

            data Amount = Int
                invariant range = value >= 0 && value <= 3

            data R = { a1: Amount, a2: Amount }
                invariant rule = a1.value * a2.value >= 100

            data Ok

            behavior f : (r: R) -> Ok

            let f (r) = if r.a1.value > 1 then Ok else Ok
            """;

    /**
     * The rule is named where the page counts the rules nothing settled, so a reader of the account
     * is sent to the rule rather than to the position.
     */
    @Test
    void theDecisionSentenceNamesTheRuleThatGaveTheOfferNoValue() {
        String line = whereNothingCouldShowARow(human(model(OUTSIDE_THE_SUBSET)));

        assertTrue(line.contains("every value tried at a rule of the decision was refused, and what"
                        + " was tried was not everything the rules leave"),
                () -> "what the search came to: " + line);
        assertTrue(line.contains("invariant Code (shape) at `t.code` gave none of them: written in"
                        + " a form this compiler does not read"),
                () -> "and which rule gave it no value: " + line);
    }

    /**
     * And a rule read from end to end is a different sentence, which is the whole of what the
     * attribution is for: one of them is rewritten and the other is allowed more.
     */
    @Test
    void aRuleReadToTheEndIsNotSaidAsOneThisCompilerCannotRead() {
        String unread = whereNothingCouldShowARow(human(model(OUTSIDE_THE_SUBSET)));
        String costly = whereNothingCouldShowARow(human(model(MORE_THAN_A_WITNESS_MAY_SPEND)));

        assertNotEquals(unread, costly,
                "an author rewriting a rule and an author raising a figure are doing different"
                        + " work, and the account sent both to the same place");
        assertTrue(costly.contains("invariant Code (shape) at `t.code` gave none of them: working a"
                        + " value out of it asks for a larger machine than one may be"),
                () -> costly);
        assertFalse(costly.contains("does not read"),
                () -> "this rule was read from end to end: " + costly);
    }

    /**
     * The document carries the same distinction as a shape, and not as the sentence the page shows.
     *
     * <p>The category stays what it was. What the search came to is the same on both ways in — the
     * values tried were refused and they were not everything — and splitting the word would say the
     * searches came to different answers when what differs is why they were given less.
     */
    @Test
    void theDocumentCarriesTheAttributionBesideTheCategory() {
        JsonNode unread = onlyCause(json(model(OUTSIDE_THE_SUBSET)));
        JsonNode costly = onlyCause(json(model(MORE_THAN_A_WITNESS_MAY_SPEND)));

        assertEquals("not_all_candidates_could_be_offered",
                shortfallWord(json(model(OUTSIDE_THE_SUBSET))));
        assertEquals("not_all_candidates_could_be_offered",
                shortfallWord(json(model(MORE_THAN_A_WITNESS_MAY_SPEND))));

        // Both are attributed to the rule, and what stopped it is written under the key whose
        // vocabulary says what kind of thing it was: a reading that stopped is said in the words
        // `notRead` already has, and a limit is no reading at all.
        assertEquals("a_rule", unread.get("attribution").stringValue());
        assertEquals("a_rule", costly.get("attribution").stringValue());
        assertEquals("unsupported_syntax", unread.get("unread").stringValue());
        assertNull(unread.get("limit"), () -> "no limit refused this one: " + unread);
        assertEquals("a_machine_larger_than_one_may_be", costly.get("limit").stringValue());
        assertNull(costly.get("unread"), () -> "this rule was read to the end: " + costly);

        // And which rule, by the pair the rest of this document names rules with.
        assertNotNull(unread.get("rule"), () -> "the handle an author is sent to: " + unread);
        assertEquals("Code", unread.get("ruleId").get("declaredOn").stringValue());
        assertEquals("t.code", unread.get("position").stringValue());
    }

    /**
     * And a search that had everything the rules leave carries none of this, which is what the
     * absence of the array means.
     */
    @Test
    void aSearchThatWasShortOfNothingCarriesNoCause() {
        List<JsonNode> owed = withAShortfall(json(NOTHING_WAS_SHORT));

        assertFalse(owed.isEmpty(), "both ways of this body had nothing composed to try");
        for (JsonNode each : owed) {
            assertEquals("all_candidates_rejected", each.get("synthesisShortfall").stringValue(),
                    () -> "every value the rules leave was tried: " + each);
            assertNull(each.get("synthesisShortfallCauses"),
                    () -> "and nothing was short of anything: " + each);
        }
        assertFalse(whereNothingCouldShowARow(human(NOTHING_WAS_SHORT)).contains("gave none of"),
                "the page says the same by saying nothing");
    }

    /**
     * And every entry is shaped the way the document says entries of this are shaped.
     *
     * <p>The schema states it, and what evaluates a schema is a consumer's validator rather than
     * anything here — so what holds the writer to it is this. Over every model below and not over
     * the one that shows the news, because what is being held is a shape and the shape is the same
     * wherever an entry is written.
     *
     * <p>The count first, so that a walk that found no entries is this failing rather than this
     * passing with nothing to say.
     */
    @Test
    void everyCauseIsShapedTheWayTheDocumentSaysTheyAre() {
        List<JsonNode> seen = new ArrayList<>();
        for (String document : List.of(json(model(OUTSIDE_THE_SUBSET)),
                json(model(MORE_THAN_A_WITNESS_MAY_SPEND)), json(NOTHING_WAS_SHORT))) {
            for (JsonNode owed : withAShortfall(document)) {
                JsonNode causes = owed.get("synthesisShortfallCauses");
                if (causes != null) {
                    causes.forEach(seen::add);
                }
            }
        }

        assertFalse(seen.isEmpty(), "the models below write entries for this to be about");
        for (JsonNode each : seen) {
            boolean named = each.get("rule") != null;
            assertEquals("a_rule".equals(each.get("attribution").stringValue()), named,
                    () -> "a rule is named exactly where the shortfall is attributed to one: "
                            + each);
            assertEquals(named, each.get("ruleId") != null,
                    () -> "and what tells one rule from another travels with it: " + each);
            assertNotEquals(each.get("unread") == null, each.get("limit") == null,
                    () -> "what stopped it is one of the two, never both and never neither: "
                            + each);
        }
    }

    /**
     * The questions under the position say what they said before, which is another axis and not the
     * half this was missing.
     *
     * <p>Held as a non-regression. The limit is said there of the rules of the position met with
     * each other, and the decision sentence names the rule that was being built — so a reader who
     * meets both meets one limit attributed at two grains, and neither line is the other's.
     */
    @Test
    void thePointRowStillSaysWhatTheReadingOfThePositionMet() {
        String page = human(model(MORE_THAN_A_WITNESS_MAY_SPEND));

        assertTrue(page.contains("not accounted for: invariant Code (shape) — which values may"
                        + " stand at t.code: read to the end, and the values the rules about this"
                        + " position leave between them are more than this compiler will work out"),
                () -> page);
    }

    /**
     * And a cause with no rule behind it publishes none, which is the way the allowance for
     * composing a value arrives.
     *
     * <p>Said of the subject and not through a compile: reaching the end of that allowance means
     * having built most of what it allows, which is the one thing about this that cannot be made
     * cheap. That the allowance is attributed to composing a value at all is held where the
     * attribution is decided ({@code AnAllowanceSpentIsNotOneRulesDoing}).
     */
    @Test
    void aCauseWithNoRuleBehindItNamesNone() {
        assertEquals(ReportedShortfall.Attribution.COMPOSING_A_VALUE,
                ReportedShortfall.attribution(
                        new StringOfferShortfall.Subject.ComposingAValue()));
        assertEquals(ReportedShortfall.Attribution.THE_RULES_TOGETHER,
                ReportedShortfall.attribution(
                        new StringOfferShortfall.Subject.WhatTheyLeaveTogether()),
                "what two rules leave between them is nobody's rule, and an author sent to either"
                        + " would be sent to one that reads perfectly");
    }

    /** The line the page counts the rules nothing settled under. */
    private static String whereNothingCouldShowARow(String page) {
        List<String> found = new ArrayList<>();
        for (String line : page.split("\n")) {
            // The line the rules of the decision are counted under, and not a point of a line: the
            // page opens both the same way and what this is about is the decision's.
            if (line.contains("nothing could show a row can be written at")
                    && line.contains("decision rule")) {
                found.add(line);
            }
        }
        assertEquals(1, found.size(), () -> "one body here has a rule nothing settled: " + found);
        return found.get(0);
    }

    /** Every rule of the document whose search had nothing composed to try it with. */
    private static List<JsonNode> withAShortfall(String document) {
        JsonNode behaviors = JSON.readTree(document).get("modules").get(0).get("behaviors");
        List<JsonNode> found = new ArrayList<>();
        behaviors.forEach(behavior -> behavior.get("decision").get("obligations").forEach(rule -> {
            if (rule.get("synthesisShortfall") != null) {
                found.add(rule);
            }
        }));
        return found;
    }

    /** The one of them these models have, where the model is one that has one. */
    private static JsonNode unsettled(String document) {
        List<JsonNode> found = withAShortfall(document);
        assertEquals(1, found.size(), () -> "one rule here had nothing composed to try: " + found);
        return found.get(0);
    }

    private static String shortfallWord(String document) {
        return unsettled(document).get("synthesisShortfall").stringValue();
    }

    /** The one thing that gave that search's offer no value. */
    private static JsonNode onlyCause(String document) {
        JsonNode causes = unsettled(document).get("synthesisShortfallCauses");
        assertNotNull(causes, "the offer was short of something");
        assertEquals(1, causes.size(), () -> "one rule gave it no value: " + causes);
        return causes.get(0);
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
