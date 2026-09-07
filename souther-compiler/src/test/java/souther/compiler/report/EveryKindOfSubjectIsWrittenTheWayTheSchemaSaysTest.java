package souther.compiler.report;

import souther.compiler.DocumentShape;
import souther.compiler.publish.MeasureWord;
import souther.compiler.publish.PublishedSubject;
import souther.compiler.publish.SubjectWord;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every kind of subject is written the way the shipped schema says one is written.
 *
 * <p>The schema tells a consumer what it can read for each kind, and it does that with a branch per
 * kind. Only some of those branches are ever true over the models this repository carries: no model
 * here holds a verdict open on a row that did not come back, on one of a behavior's inputs, or on
 * this compiler's own proof. A schema whose branch nothing reaches is a contract nothing has been
 * held to, and this repository has shipped one of those before — nine versions of a condition that
 * was never once true.
 *
 * <p>So the kinds are built here and written. What is held is not that a model produces them, which
 * is the corpus's business and is asked where the corpus is; it is that what this compiler writes
 * for each kind is what it says it writes.
 */
class EveryKindOfSubjectIsWrittenTheWayTheSchemaSaysTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * One of each, made rather than found.
     *
     * <p>The nested identities are the shapes those writers make, put here as objects: what is
     * under test is the subject around them, and the identities are held to the schema wherever the
     * arrays that carry them are written.
     */
    private static List<PublishedSubject> oneOfEach() {
        ObjectNode ruleId = JSON.createObjectNode();
        ruleId.put("kind", "comparison");
        ruleId.put("declaredIn", "m");
        ruleId.put("definition", "b");
        ruleId.put("behavior", "b");
        ruleId.put("ordinal", 1);
        ruleId.put("lowered", 0);
        ObjectNode writtenBy = JSON.createObjectNode();
        writtenBy.put("kind", "body");
        writtenBy.put("definition", "b");
        ObjectNode armId = JSON.createObjectNode();
        armId.put("module", "m");
        armId.put("definition", "b");
        armId.put("construct", 0);
        armId.put("lowered", 0);
        armId.put("part", 0);
        armId.put("decidedBy", "the_body");
        ObjectNode line = JSON.createObjectNode();
        line.set("rule", ruleId.deepCopy());
        line.put("conjunct", 0);
        ObjectNode facts = line.putObject("facts");
        facts.put("valueBelongsBelow", false);
        facts.put("holdsAtTheValue", true);
        facts.put("singles", false);
        line.putArray("narrowedWithin");
        ObjectNode obligationId = JSON.createObjectNode();
        obligationId.set("line", line);
        ObjectNode level = obligationId.putObject("level");
        level.put("kind", "on_a_carrier");
        level.putObject("carrier").put("kind", "whole");
        level.put("at", "0");
        obligationId.putObject("location").put("kind", "at_the_line");
        return List.of(
                new PublishedSubject.OfAModule("m"),
                new PublishedSubject.OfABehavior("b"),
                new PublishedSubject.OfASource("0"),
                new PublishedSubject.OfARow("b", "0", "named", null),
                new PublishedSubject.OfARow("b", "0", null, 1),
                new PublishedSubject.AtASpelledPosition("b", "r.cost"),
                new PublishedSubject.AtAPosition("b", "r.cost", "r.cost@Some"),
                new PublishedSubject.AtAnInput("b", 0),
                new PublishedSubject.AtARule("r.cost", ruleId),
                new PublishedSubject.AtABorder("r.cost = 0", ruleId),
                new PublishedSubject.AtAPoint(obligationId),
                new PublishedSubject.AtAFork("m", writtenBy, 0, 0, "if"),
                new PublishedSubject.AtAnArm(armId),
                new PublishedSubject.OfAMeasure("m", "b", MeasureWord.BRANCH),
                new PublishedSubject.OfAnAxisMeasure("m", "b", "r.cost"));
    }

    /**
     * Every kind of place a document can name is one of these.
     *
     * <p>Held against the vocabulary rather than against a count, so a word added arrives here as
     * the word it is: a kind nothing above builds is one this test says nothing about, whatever the
     * schema declares for it.
     */
    @Test
    void oneOfEveryKindIsBuilt() {
        Set<SubjectWord> built = new LinkedHashSet<>();
        oneOfEach().forEach(each -> built.add(each.kind()));

        assertEquals(Set.of(SubjectWord.values()), built,
                "a kind of place this document can name that nothing here writes");
    }

    /**
     * And the schema shipped beside this accepts each of them.
     *
     * <p>Written into the array they are written into, because that is where the schema says what
     * they are: a subject held against its definition on its own would pass a document the array
     * refuses.
     */
    @Test
    void theSchemaAcceptsEveryKindThisCompilerWrites() {
        List<String> wrong = new ArrayList<>();
        for (PublishedSubject subject : oneOfEach()) {
            ObjectNode document = document();
            ObjectNode entry = ((tools.jackson.databind.node.ArrayNode)
                    document.get("keptOpenBy")).addObject();
            entry.put("kind", "not_measured");
            entry.put("reason", "no_rows");
            entry.put("runSensitivity", "unaffected");
            AdequacyReport.about(entry.putObject("about"), subject);

            DocumentShape.of(document).wrong().forEach(said ->
                    wrong.add(subject.kind() + ": " + said));
        }

        assertEquals(List.of(), wrong, "the schema shipped beside this refuses a subject it writes");
    }

    /**
     * And each kind writes the keys the schema says that kind has.
     *
     * <p>Read from the schema's own branches. What holds a document to its shape here walks the
     * definitions and says so of itself: it is not a validator, and it does not take a branch that
     * turns on the value of a field. So the branch a consumer reads to know what it can expect for
     * a kind is one nothing else checks, and a key renamed on one side of it would ship — which is
     * how `input` came to be written as `at` for as long as it took to write this.
     */
    @Test
    void everyKindWritesTheKeysItsBranchRequires() {
        JsonNode subject = schema().get("$defs").get("subject");
        List<String> wrong = new ArrayList<>();
        for (PublishedSubject each : oneOfEach()) {
            ObjectNode written = JSON.createObjectNode();
            AdequacyReport.about(written, each);
            for (JsonNode branch : subject.get("allOf")) {
                String of = branch.get("if").get("properties").get("kind").get("const").asString();
                if (!of.equals(written.get("kind").asString())) {
                    continue;
                }
                for (JsonNode key : branch.get("then").get("required")) {
                    if (!written.has(key.asString())) {
                        wrong.add(of + " is written without `" + key.asString() + "`");
                    }
                }
            }
            for (String key : written.propertyNames()) {
                if (!subject.get("properties").has(key)) {
                    wrong.add(written.get("kind").asString() + " writes `" + key
                            + "`, which the schema does not declare");
                }
            }
        }

        assertEquals(List.of(), wrong, () -> "what a kind writes and what its branch says: " + wrong);
    }

    /** The schema this compiler ships beside the documents it writes. */
    private static JsonNode schema() {
        try (java.io.InputStream in = AdequacyReport.class
                .getResourceAsStream(AdequacyReport.SCHEMA_RESOURCE)) {
            return JSON.readTree(in);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** The smallest document the schema accepts, for one entry to be put into. */
    private static ObjectNode document() {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", AdequacyReport.SCHEMA_VERSION);
        root.put("compilerVersion", "0");
        root.put("status", "complete");
        root.put("adequacy", "undetermined");
        root.putArray("keptOpenBy");
        root.putArray("modules");
        root.putObject("sources");
        return root;
    }
}
