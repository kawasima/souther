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
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A key this writes is a key the schema declares, and the schema declares it where it is written.
 *
 * <p>Every object of this document is closed — `additionalProperties: false` — so a key the writer
 * emits and the schema does not declare is a document the schema it ships beside refuses. That is a
 * defect nothing else here can see: the words agree ({@code EverySchemaWordIsAccountedForTest}), the
 * document parses, and the tests that read it read the writer's own output rather than the contract.
 * `ruleId` was written on a finding and declared inside that finding's `subject`, which is a keyword
 * in no position at all.
 *
 * <p>Not a validator. What is checked is the one thing a shape can be wrong about here and a reader
 * cannot see: which object a key belongs to.
 */
class EveryKeyThisWritesIsOneTheSchemaDeclaresTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A model whose report reaches every object this checks. */
    private static final String MODEL = """
            module m

            data R = { a: Int }
            data Found
            data Missing

            behavior f : (r: R) -> Found | Missing
                ensures Found -> r.a <= Int.min(20, 30)
                ensures Found -> r.a >= Int.min(40, 50)
            let f (r) = if r.a >= 30 * 2 then Found else Missing

            example f
                | "one" : (R { a = 1 }) -> Missing
            """;

    private static JsonNode schema() throws Exception {
        try (InputStream in =
                     AdequacyReport.class.getResourceAsStream(AdequacyReport.SCHEMA_RESOURCE)) {
            assertNotNull(in, AdequacyReport.SCHEMA_RESOURCE + " ships beside the compiler");
            return JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * A model whose clause leaves a question standing, which is a different object.
     *
     * <p>Its own model and not a second thing asked of the one above. A comparison raises a question
     * exactly where the reading of it reached a line, and that line answers it — so a question that
     * stands comes from a clause about a position, and that is what this writes. Asked of one model
     * for both, the object under test moved whenever the reading of comparisons did.
     */
    private static final String MODEL_WITH_A_QUESTION = """
            module m

            data Length = Int
                invariant square = value * value >= 4

            behavior price : (length: Length) -> Int
            let price (length) = if length.value >= 5 then 1 else 2

            example price
                | "one" : (Length(5)) -> 1
            """;

    private static JsonNode document() {
        return document(MODEL);
    }

    private static JsonNode document(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return JSON.readTree(AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts())));
    }

    /** The object at a `$defs` name, following the one `$ref` that stands in the way. */
    private static JsonNode defined(JsonNode schema, String name) {
        JsonNode at = schema.get("$defs").get(name);
        assertNotNull(at, () -> "the schema no longer defines " + name);
        return at;
    }

    /** Every key the writer wrote in the objects of an array. */
    private static Set<String> keysWritten(JsonNode array) {
        Set<String> out = new LinkedHashSet<>();
        array.forEach(each -> each.propertyNames().forEach(out::add));
        return out;
    }

    /** And every key the schema declares for them. */
    private static Set<String> keysDeclared(JsonNode items) {
        Set<String> out = new LinkedHashSet<>();
        items.get("properties").propertyNames().forEach(out::add);
        assertTrue(items.has("additionalProperties")
                        && !items.get("additionalProperties").asBoolean(),
                "the object is closed, which is what makes an undeclared key a refusal");
        return out;
    }

    /**
     * A finding writes `ruleId`, and the schema declares it on a finding.
     *
     * <p>It was declared inside `subject`, whose schema is a string — so the key had no position at
     * all and the closed object refused the very document this ships to describe.
     */
    @Test
    void everyKeyOfAFindingIsDeclaredOnAFinding() throws Exception {
        JsonNode behavior = document().get("modules").get(0).get("behaviors").get(0);
        Set<String> written = keysWritten(behavior.get("findings"));
        assertTrue(written.contains("ruleId"),
                () -> "the model reaches a finding about a rule: " + behavior.get("findings"));

        Set<String> undeclared = new LinkedHashSet<>(written);
        undeclared.removeAll(keysDeclared(defined(schema(), "findings").get("items")));

        assertEquals(Set.of(), undeclared,
                "a key this writes that the schema declares nowhere on the object it is written on");
    }

    /** And the same of a question, which is the other object this change writes. */
    @Test
    void everyKeyOfAQuestionIsDeclaredOnAQuestion() throws Exception {
        JsonNode standing = document(MODEL_WITH_A_QUESTION)
                .get("modules").get(0).get("behaviors").get(0)
                .get("partition").get("unanswered");
        assertNotNull(standing, "the model leaves a question standing");

        Set<String> undeclared = new LinkedHashSet<>(keysWritten(standing));
        undeclared.removeAll(keysDeclared(defined(schema(), "partition")
                .get("properties").get("unanswered").get("items")));

        assertEquals(Set.of(), undeclared, "and none of a question's either");
    }

    /**
     * And the same of a rule this could not turn into a line, where the entry names a place inside
     * the rule.
     *
     * <p>Its own model, because the key is written for one reason only. A clause the reading of
     * ends gave up on writes an entry with no place in it, and a check reading whatever the models
     * above happened to produce would be green over an array that never carried the key — which is
     * how the field could be added, shipped, and refused by the schema beside it.
     */
    @Test
    void everyKeyOfARuleWithNoLineIsDeclaredOnOne() throws Exception {
        JsonNode unread = document("""
                module m

                data Yes
                data N = { n: Int }
                    invariant r = n >= 2 || Int.abs(n) >= 5

                behavior f : (v: N) -> Yes
                    constructs Yes
                let f (v) = Yes
                """).get("modules").get(0).get("behaviors").get(0)
                .get("partition").get("notRead");
        assertNotNull(unread, "the model leaves a rule this could not turn into a line");
        Set<String> written = keysWritten(unread);
        assertTrue(written.contains("sentTo"),
                () -> "the choice is what an author is sent to: " + unread);

        Set<String> undeclared = new LinkedHashSet<>(written);
        undeclared.removeAll(keysDeclared(defined(schema(), "partition")
                .get("properties").get("notRead").get("items")));

        assertEquals(Set.of(), undeclared, "and none of one of these either");
    }
}
