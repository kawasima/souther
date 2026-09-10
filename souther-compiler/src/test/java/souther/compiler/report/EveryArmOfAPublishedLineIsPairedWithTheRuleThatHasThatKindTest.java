package souther.compiler.report;

import souther.compiler.partition.WhichLine;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Which line a document may say a line is, and which rule may stand in each of those.
 *
 * <p>The three kinds of rule are not decomposed alike, so the numbers under them are not
 * interchangeable: a part and which of its lines goes with a clause of a {@code data}, a part and
 * which of its statements with a clause of a behavior, and a body's comparison takes neither. This
 * is what the document says about that pairing, read off the schema rather than off the sentence in
 * the description beside it.
 *
 * <p><b>Against the seal and not against a list written here.</b> What arms there are is
 * {@link WhichLine}'s answer, so an arm added to it is an arm this stops at until the document says
 * what a line of that kind is. Written as a list of its own, the check would agree with itself
 * about a union the compiler had already grown past.
 *
 * <p>What this does not do is validate a document. Which branch a value takes is a question for a
 * reader of the schema, and the shape checker beside this reads a document for the keys the schema
 * names rather than for the branch it satisfies — so the pairing is held here, where it is written,
 * and a document carrying a part beside a body's comparison is refused by there being no arm that
 * admits one.
 */
class EveryArmOfAPublishedLineIsPairedWithTheRuleThatHasThatKindTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Which arm of the seal is published under which word, and with which kind of rule. */
    private static final Map<String, String> PAIRED_WITH = new TreeMap<>(Map.of(
            "part", "invariant",
            "statement_of_part", "ensures",
            "comparison", "comparison"));

    /**
     * And which numbers each of them carries beside the rule.
     *
     * <p>A declaration's clause carries which of its parts and which of that part's lines, because
     * a part draws more than one: a conjunct written as a denied choice states one comparison per
     * branch and places an end on each of the numbers they are about. Carrying the part alone, the
     * document said the same of both — and the facts beside them do not tell them apart, since two
     * lower bounds admitting their own value say the same thing about two numbers.
     */
    private static final Map<String, String> CARRIES = new TreeMap<>(Map.of(
            "part", "[kind, line, part, rule]",
            "statement_of_part", "[kind, part, rule, statement]",
            "comparison", "[kind, rule]"));

    @Test
    void everyArmPairsItsWordWithTheKindOfRuleThatIsDecomposedThatWay() throws IOException {
        Map<String, String> found = new TreeMap<>();
        for (JsonNode arm : whichLine().get("oneOf")) {
            found.put(constOf(arm, "kind"),
                    constOf(arm.get("properties").get("rule"), "kind"));
        }

        assertEquals(PAIRED_WITH, found,
                "a part is what a declaration's clause is written in, a statement is what a part of"
                        + " a behavior's clause states, and a body's comparison is decomposed by"
                        + " nothing — so no arm may put one kind's number beside another's rule");
    }

    @Test
    void everyArmCarriesTheNumbersThatNameALineOfThatKind() throws IOException {
        Map<String, String> found = new TreeMap<>();
        for (JsonNode arm : whichLine().get("oneOf")) {
            found.put(constOf(arm, "kind"),
                    new TreeSet<>(arm.get("properties").propertyNames()).toString());
        }

        assertEquals(CARRIES, found,
                "each arm names what a line of that kind is named by, and every arm refuses what it"
                        + " does not name — so a body's comparison cannot carry a part");
    }

    /**
     * And the words are the arms of the seal, so a kind of line added to the model stops here.
     *
     * <p>The seal is what the compiler can build. A document that admitted a word the seal has no
     * arm for would be describing a line nothing writes, and one that had no word for an arm would
     * be a line this compiler writes and the schema refuses.
     */
    @Test
    void theArmsPublishedAreTheArmsTheSealHas() {
        Map<String, String> arms = new LinkedHashMap<>();
        for (Class<?> each : WhichLine.class.getPermittedSubclasses()) {
            arms.put(each.getSimpleName(), "");
        }

        assertEquals(new TreeMap<>(Map.of("OfADeclarationsLine", "", "OfAComparisonOfAPart", "",
                        "OfAComparison", "")),
                new TreeMap<>(arms),
                "the arms a line of the model has, which is what the words above are the document's"
                        + " spelling of. An arm added here wants one, and this is where that is"
                        + " noticed");
    }

    private static JsonNode whichLine() throws IOException {
        try (InputStream open = AdequacyReport.class
                .getResourceAsStream(AdequacyReport.SCHEMA_RESOURCE)) {
            assertNotNull(open, "the schema shipped beside this compiler");
            JsonNode which = JSON.readTree(open).get("$defs").get("whichLine");
            assertNotNull(which, "the document says which of a rule's lines a line is");
            return which;
        }
    }

    /** The one value a schema allows under {@code key}, which is how a branch is told from the
     *  rest. */
    private static String constOf(JsonNode said, String key) {
        JsonNode under = said.get("properties").get(key);
        assertNotNull(under, "an arm says what it is under `" + key + "`");
        JsonNode only = under.get("const");
        assertNotNull(only, "and says it as the one word it allows, not as a description");
        return only.asString();
    }
}
