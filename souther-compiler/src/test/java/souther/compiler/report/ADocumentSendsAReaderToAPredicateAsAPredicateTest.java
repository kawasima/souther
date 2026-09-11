package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A document that names a rule about the strings at a position calls it a predicate on both
 * surfaces.
 *
 * <p>The end of what {@link OneRuleIsCalledOneThingOnBothSurfacesTest} holds of the two words, over
 * a model rather than over a rule built by hand. What is asked there is that the words agree; what
 * is asked here is that a document written from a real compilation is one where they do — which
 * takes the handle a reader is given and the identity beside it having come from the same rule all
 * the way through.
 *
 * <p>Written because nothing in the fixtures produced such an entry. A word that is wrong for a kind
 * of rule no checked-in document holds is wrong where nobody is looking, and the first reader to
 * meet it is the one this exists instead of.
 */
class ADocumentSendsAReaderToAPredicateAsAPredicateTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * A behavior stating a rule about the strings at one of its positions.
     *
     * <p>The rule is a call and not a comparison, which is the whole point: a reader following the
     * handle is sent to the application, and what they find there is not a line on an order.
     */
    private static final String A_PREDICATE_IN_A_BODY = """
            module m

            data Yes
            data No

            behavior f : (prefix: String, code: String) -> Yes | No
            let f (prefix, code) = if String.startsWith(prefix, code) then Yes else No
            """;

    @Test
    void aPredicateIsCalledAPredicateWhereverTheDocumentNamesIt() {
        List<JsonNode> named = entriesNamingARule(A_PREDICATE_IN_A_BODY);

        assertFalse(named.isEmpty(),
                () -> "no entry of this document names a rule, so what it calls one is a question"
                        + " this cannot answer: " + json(A_PREDICATE_IN_A_BODY));
        List<String> predicates = new ArrayList<>();
        for (JsonNode each : named) {
            if ("predicate".equals(each.get("ruleId").get("kind").asString())) {
                predicates.add(each.get("rule").asString());
            }
        }

        assertFalse(predicates.isEmpty(),
                () -> "this model states a rule about the strings at a position and no entry is of"
                        + " one, so the word for such a rule is not being written anywhere: "
                        + json(A_PREDICATE_IN_A_BODY));
        for (String said : predicates) {
            assertTrue(said.startsWith("predicate@") || said.startsWith("predicate in "),
                    () -> "a reader following the handle is sent to a predicate and told what it"
                            + " is: " + said);
        }
    }

    /**
     * And the same rule is not called a comparison anywhere in the document.
     *
     * <p>The negative half, over the words rather than over the entries. An entry keyed
     * {@code predicate} whose sentence said {@code comparison} is the pair this is about, and a
     * check that only read the entries of one kind would pass while the other surface still said
     * the wrong word.
     */
    @Test
    void andNoEntryOfThisModelIsSentToAComparison() {
        for (JsonNode each : entriesNamingARule(A_PREDICATE_IN_A_BODY)) {
            String kind = each.get("ruleId").get("kind").asString();
            String said = each.get("rule").asString();

            assertEquals(kind, said.replaceAll("[@ ].*$", ""),
                    () -> "the word a reader is shown and the word a consumer groups by are of one"
                            + " rule: " + each);
        }
    }

    /** Every entry of the document that names a rule both ways, wherever a measure writes one. */
    private static List<JsonNode> entriesNamingARule(String model) {
        List<JsonNode> out = new ArrayList<>();
        collect(JSON.readTree(json(model)), out);
        return out;
    }

    private static void collect(JsonNode node, List<JsonNode> into) {
        if (node.isObject() && node.has("rule") && node.get("rule").isString()
                && node.has("ruleId")) {
            into.add(node);
        }
        node.values().forEach(each -> collect(each, into));
    }

    private static String json(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
