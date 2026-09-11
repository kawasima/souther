package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a reading leaves standing is a state the schema says, not one its prose describes.
 *
 * <p>`read` says how far one measure got with a position: whether the walk reached every rule, and
 * whether the questions that measure answers were answered. `extent` is the word over both. A
 * description saying "present exactly where" is a promise a consumer written against it will find
 * broken by the first document that breaks it, since nothing refuses one; the schema says what it
 * can in `oneOf`, and this holds the emitter to the rest.
 *
 * <p>The questions themselves are not in `read`. They are the model's and every measure is one
 * reader of them, so they are written once beside the measures — which is also what keeps a
 * question alive at a position no axis came back for.
 */
class WhatAReadingLeavesStandingIsAStateTheSchemaSaysTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Every rule taken in, and the walk reached all of them. */
    private static final String NOTHING_STANDING = """
            module example.rooms

            data Length = Int
                invariant min = value >= 1
                invariant max = value <= 100

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 50 then 1 else 2
            """;

    /** A rule that arrived and nothing took in. */
    private static final String A_RULE_UNACCOUNTED = """
            module example.rooms

            data Length = Int
                invariant min = value >= 1
                invariant square = value * value >= 4

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 50 then 1 else 2
            """;

    /** A line the model draws that this could not fold, beside a bound it could. */
    private static final String A_LINE_UNACCOUNTED = """
            module example.rooms

            data Length = Int
                invariant min = value >= 1
                invariant max = value <= 10 * 2

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 5 then 1 else 2
            """;

    /** The same line with nothing beside it, so no axis comes back at all. */
    private static final String A_LINE_AND_NO_AXIS = """
            module example.rooms

            data Length = Int
                invariant top = value <= 10 * 2

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 50 then 1 else 2
            """;

    /** A clause of the value this reading owns that reached no reading at all, beside one it read
     *  to the end. What is out of sight is a rule of the very position the classes are at: a rule
     *  handed to the reading one position down is that reading's and is read there (#1072). */
    private static final String RULES_OUT_OF_SIGHT = """
            module o

            data Assignee = String
                invariant named = value == "ada" || value == "bob"
                invariant unreadable = value == 1

            data Issue = { assignee: Assignee }
            data Accepted = { at: Int }

            behavior classify : (i: Issue) -> Accepted
            """;

    private static JsonNode schema() throws Exception {
        try (InputStream in =
                     AdequacyReport.class.getResourceAsStream(AdequacyReport.SCHEMA_RESOURCE)) {
            assertNotNull(in, AdequacyReport.SCHEMA_RESOURCE + " ships beside the compiler");
            return JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** The axis about {@code path}, which is what these are asked of. */
    private static JsonNode axisAt(JsonNode partition, String path) {
        for (JsonNode axis : partition.get("axes")) {
            if (path.equals(axis.get("path").asString())) {
                return axis;
            }
        }
        throw new AssertionError(path + " is not among " + partition.get("axes"));
    }

    private static JsonNode partitionOf(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        JsonNode document = JSON.readTree(
                AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts())));
        for (JsonNode each : document.get("modules").get(0).get("behaviors")) {
            if (each.get("name").asString().equals(behavior)) {
                return each.get("partition");
            }
        }
        throw new AssertionError("no behavior called " + behavior);
    }

    /**
     * The states are in the schema, and `complete` is the one where the key is not written.
     *
     * <p>Read off the schema so that a description rewritten without the constraint fails here.
     * What the schema can say is the reach; that a question about this position's values is
     * standing is written elsewhere in the document, which no cross-reference here can require, so
     * it is held by the emitter tests below instead.
     */
    @Test
    void theSchemaSaysWhichKeysEachExtentIsWrittenWith() throws Exception {
        JsonNode read = schema().get("$defs").get("partition").get("properties").get("axes")
                .get("items").get("properties").get("read");
        JsonNode states = read.get("oneOf");
        assertNotNull(states, "the states are in the schema and not only in its description");
        assertEquals(2, states.size());
        assertEquals("complete",
                states.get(0).get("properties").get("extent").get("const").asString());
        assertEquals("rulesNotReached",
                states.get(0).get("not").get("required").get(0).asString(),
                "complete is never written beside a reach that fell short");
        assertEquals("partial",
                states.get(1).get("properties").get("extent").get("const").asString());

        assertTrue(read.get("properties").get("rulesNotReached").get("const").asBoolean(),
                "written only where it is so, so `false` is not a document this accepts");
        assertFalse(read.get("properties").has("unanswered"),
                "the questions are the model's and are written beside the measures, not in one");
    }

    /** And where they are written, keyed by the position so that a reader can find them. */
    @Test
    void theQuestionsAreWrittenBesideTheMeasures() throws Exception {
        JsonNode standing = schema().get("$defs").get("partition").get("properties")
                .get("unanswered");
        assertNotNull(standing, "beside `axes` and `boundaries`, not inside either");
        assertEquals(1, standing.get("minItems").asInt(),
                "an empty list is a behavior with nothing standing, which is written by leaving it out");
        assertEquals("at", standing.get("items").get("required").get(0).asString(),
                "each says which position it is about, since no axis is carrying it");
    }

    /** Two positions of one value, one with a rule nothing took in and one with rules out of
     *  sight. */
    private static final String BOTH_FACTS = """
            module m

            data Inner = String
                invariant named = value == "ada" || value == "bob"
                invariant unreadable = value == 1

            data R = { n: Int, deep: Inner }
                invariant odd = n * n >= 4

            data Ok = { at: Int }

            behavior f : (r: R) -> Ok
                constructs Ok

            let f (r) = if r.n >= 5 then Ok { at = 1 } else Ok { at = 2 }
            """;

    /**
     * The two facts are independent, and a position carries the one that is true of it.
     *
     * <p>They were arms of one type, which said they could not both be so. Nothing grounds that:
     * what a reading could not read and what a reading never reached are different facts about
     * different rules, and here they fall at two positions of one value. Whether one position can
     * carry both is not shown by this — the model and the schema allow it, and no input is known
     * that produces it.
     *
     * <p><b>Both rules belong to the readings that report them.</b> {@code odd} is written on
     * {@code R} and is short at {@code R}'s own position; {@code unreadable} is written on
     * {@code Inner} and is short where {@code Inner} is read. A rule handed to a reading one
     * position down is that reading's and is reported there, so neither of these is a position
     * answering for somebody else's rule (#1072).
     */
    @Test
    void aRuleNothingTookInAndRulesOutOfSightAreIndependent() {
        JsonNode partition = partitionOf(BOTH_FACTS, "f");

        // The rule nothing took in, at the position it is about. Asked of the questions rather than
        // of an axis: the classes at `r.n` are ones a comparison in the body draws, and this model
        // is refused, so its bodies are not elaborated and there is no comparison to draw them. The
        // question the model raises about `r.n` is raised either way — it is read off the
        // declaration, which is what makes it the thing to assert here.
        assertEquals(List.of("r.n", "r.n"), partition.get("unanswered").valueStream()
                .map(each -> each.get("at").asString()).toList(), partition.toString());
        assertEquals(List.of("admitted_values", "boundaryNotDetermined"),
                partition.get("unanswered").valueStream()
                        .map(each -> each.get("question").asString()).sorted().toList(),
                "which values may stand there is raised, and whether it bounds is not worked out —"
                        + " which is its own word, since the classes are settled and only the"
                        + " border waits on it");

        // And the rules out of sight, at the other position, which raises no question at all.
        JsonNode held = axisAt(partition, "r.deep").get("read");
        assertTrue(held.get("rulesNotReached").asBoolean(), held.toString());
    }

    /** Nothing standing: `complete`, and nothing written either way. */
    @Test
    void aPositionWithNothingStandingIsCompleteAndCarriesNeitherKey() {
        JsonNode partition = partitionOf(NOTHING_STANDING, "price");

        assertEquals("complete", partition.get("axes").get(0).get("read").get("extent").asString());
        assertFalse(partition.get("axes").get(0).get("read").has("rulesNotReached"));
        assertFalse(partition.has("unanswered"), partition.toString());
    }

    /** A rule nothing took in: `partial`, and the rule named beside the measures. */
    @Test
    void aRuleNothingTookInIsPartialAndNamesIt() {
        JsonNode partition = partitionOf(A_RULE_UNACCOUNTED, "price");

        assertEquals("partial", partition.get("axes").get(0).get("read").get("extent").asString());
        assertFalse(partition.get("axes").get(0).get("read").has("rulesNotReached"));
        // Two entries and one rule: which values may stand there is raised and unanswered, and
        // whether the clause also bounds the position is what folding the product would settle.
        assertEquals(List.of("invariant Length (square)", "invariant Length (square)"),
                partition.get("unanswered").valueStream()
                        .map(each -> each.get("rule").asString()).toList());
    }

    /**
     * A line nothing answered leaves the position's own extent alone.
     *
     * <p>The two measures #869 told apart. Which values may stand at the position is what classes
     * are made of and is the partition measure's to be short of; where the line falls is the border
     * measure's. Counted in `extent`, the number they were separated into would be one again — and
     * an author reading `partial` would go looking for a class.
     */
    @Test
    void aLineNothingAnsweredIsNotWhatMakesTheDivisionPartial() {
        JsonNode partition = partitionOf(A_LINE_UNACCOUNTED, "price");

        assertEquals(1, partition.get("unanswered").size(), partition.toString());
        assertEquals("boundary", partition.get("unanswered").get(0).get("question").asString());
        assertEquals("invariant Length (max)",
                partition.get("unanswered").get(0).get("rule").asString());
        assertEquals("complete", partition.get("axes").get(0).get("read").get("extent").asString(),
                "every rule about which values may stand there was answered");
    }

    /**
     * And it is written where no axis came back at all.
     *
     * <p>The reason the questions are not inside an axis. This position is divided no way this
     * could read, so nothing here has an axis to carry a question — and what the model asked is
     * still what the model asked.
     */
    @Test
    void aLineNothingAnsweredSurvivesAPositionWithNoAxis() {
        JsonNode partition = partitionOf(A_LINE_AND_NO_AXIS, "price");

        assertEquals(0, partition.get("axes").size(), "nothing divided the position");
        assertEquals(1, partition.get("unanswered").size(), partition.toString());
        assertEquals("length", partition.get("unanswered").get(0).get("at").asString());
        assertEquals("boundary", partition.get("unanswered").get(0).get("question").asString());
    }

    /** Rules out of sight: `partial`, the reach, and no rule to name. */
    @Test
    void rulesOutOfSightArePartialAndNameNoRule() {
        JsonNode partition = partitionOf(RULES_OUT_OF_SIGHT, "classify");
        JsonNode read = partition.get("axes").get(0).get("read");

        assertEquals("partial", read.get("extent").asString(), read.toString());
        assertTrue(read.get("rulesNotReached").asBoolean(), read.toString());
        assertFalse(partition.has("unanswered"), partition.toString());
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
    void theModelsThatSaySoAreOnesThisCompilerRefuses() {
        assertTrue(isRefused(RULES_OUT_OF_SIGHT), RULES_OUT_OF_SIGHT);
        assertTrue(isRefused(BOTH_FACTS), BOTH_FACTS);
        assertFalse(isRefused(NOTHING_STANDING), "and the control is a model that compiles");
    }

    /** Whether this compiler refuses {@code source}, which is what the fixtures above turn on. */
    private static boolean isRefused(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        return !compilation.diagnostics().values().stream()
                .flatMap(java.util.List::stream).toList().isEmpty();
    }

}
