package souther.compiler.publish;

import org.junit.jupiter.api.Test;

import souther.compiler.report.AdequacyReport;
import souther.compiler.source.SourceId;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every form of sentence a document sends a reader to a rule by is one the shipped schema describes.
 *
 * <p>The third contract surface. Which keys a document may carry is held elsewhere, and so is every
 * enumerated word it may carry; what a field of free text is written as was held nowhere, and a
 * description is where a consumer reads it. The schema promised a handle spelled {@code if@…} from
 * the first version that shipped, three days after the word stopped being {@code if} and before any
 * document of any version carried one — nine versions, each copied from the one before, and nothing
 * ever read the sentence against the writer.
 *
 * <p>Held as a correspondence and not by generating one side from the other, for the reason the
 * enumerated words are: what a document may carry is a decision about the contract, and moving a
 * word inside the compiler is not. A generated description would move with the compiler, and the
 * only thing left to notice would be a consumer.
 *
 * <p>The population is the published grammar rather than the seals underneath it. A citation and a
 * place are two internal sums whose product is not the set of sentences: a clause the author named
 * and one a reader counts to are one arm of {@code RuleRef} and two sentences, and two arms of the
 * citation write one. Walked for the internal division, the forms a contract has to describe come
 * out short by exactly the ones a value decides.
 */
class EveryFormOfARuleHandleIsOneTheContractDescribesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Where the handle's forms are described, once, for every field that carries one. */
    private static final String CANONICAL = "/$defs/ruleHandle";

    /** What the canonical definition is referred to as from the fields that carry a handle. */
    private static final String REFERENCE = "#/$defs/ruleHandle";

    /** The annotation a field that writes a sentence with a handle in it carries. Beside the
     *  reference rather than instead of it: such a field is not a handle, and a reader validating a
     *  document must not be told it is. */
    private static final String EMBEDS = "x-souther-contains";

    private static final SourceId IN = new SourceId("billing.sou");

    /**
     * One handle of every form the grammar has.
     *
     * <p>The product of what decides a sentence: which form it is, which of the published words goes
     * in front of a place where one does, and what kind of place there is. Written out rather than
     * gathered from a compilation — a model states the rules it states, and a form no fixture
     * happens to write is one a document may carry the day something does.
     */
    private static List<PublishedRuleHandle> everyForm() {
        List<PublishedRuleHandle> out = new ArrayList<>(List.of(
                new PublishedRuleHandle.NamedInvariant("Amount", "cap"),
                new PublishedRuleHandle.NumberedInvariant("Amount", 2),
                new PublishedRuleHandle.NamedEnsures("charge", "refunded"),
                new PublishedRuleHandle.WholeEnsures("charge")));
        for (PublishedRuleKind kind : PublishedRuleKind.values()) {
            out.add(new PublishedRuleHandle.Written(kind, inASourceThisCompileHolds()));
            out.add(new PublishedRuleHandle.Written(kind, inATextWithNoName()));
            out.add(new PublishedRuleHandle.Reached(kind, inASourceThisCompileHolds(), "Money"));
            out.add(new PublishedRuleHandle.Reached(kind, inATextWithNoName(), "Money"));
            out.add(new PublishedRuleHandle.Reached(kind, new PublishedRuleHandle.Place.Nowhere(),
                    "Money"));
        }
        return List.copyOf(out);
    }

    private static PublishedRuleHandle.Place inASourceThisCompileHolds() {
        return new PublishedRuleHandle.Place.InSource(
                new PublishedAt(IN, 14, 22, new PublishedAt.Where.Here()));
    }

    private static PublishedRuleHandle.Place inATextWithNoName() {
        return new PublishedRuleHandle.Place.Unplaced(7, 3);
    }

    /**
     * That the population above is the grammar and not a list somebody kept up by hand.
     *
     * <p>Each axis on its own, because a form is a point of the product: an arm nobody built and a
     * kind of place nobody paired with an arm are both forms the contract would go on describing
     * nothing about.
     */
    @Test
    void thePopulationIsEveryFormTheGrammarHas() {
        assertEquals(
                Set.of(PublishedRuleHandle.class.getPermittedSubclasses()),
                everyForm().stream().map(each -> (Class<?>) each.getClass())
                        .collect(Collectors.toSet()),
                "one of each form of sentence the grammar has, and no other");
        assertEquals(
                Set.of(PublishedRuleKind.values()),
                everyForm().stream().flatMap(each -> switch (each) {
                    case PublishedRuleHandle.Written it -> Stream.of(it.kind());
                    case PublishedRuleHandle.Reached it -> Stream.of(it.kind());
                    default -> Stream.<PublishedRuleKind>of();
                }).collect(Collectors.toSet()),
                "and every published word a rule with no name is called by");
        assertEquals(
                Set.of(PublishedRuleHandle.Place.class.getPermittedSubclasses()),
                everyForm().stream().flatMap(each -> switch (each) {
                    case PublishedRuleHandle.Written it -> Stream.of(it.at());
                    case PublishedRuleHandle.Reached it -> Stream.of(it.at());
                    default -> Stream.<PublishedRuleHandle.Place>of();
                }).map(each -> (Class<?>) each.getClass()).collect(Collectors.toSet()),
                "and every kind of place a report says such a rule is at");
    }

    /**
     * What the compiler writes and what the contract gives as examples are the same set.
     *
     * <p>Rendered through the surface a person's report writes, which is the same spelling every
     * surface writes: a form that reads one way in a document and another in a report is two forms,
     * and this asks about the grammar rather than about one field.
     *
     * <p>Not against a fixture, which would tie the contract to a line number some model happens to
     * be written at. Against the renderer, so the contract moves exactly when the sentence does.
     */
    @Test
    void theContractGivesAnExampleOfEveryFormAndOfNothingElse() {
        assertEquals(rendered(), examples(),
                "the sentences this compiler writes and the ones the schema gives as examples");
    }

    /**
     * And the prose beside them says each of those sentences.
     *
     * <p>The examples are what a check can read and the description is what a person reads, and a
     * contract whose machine-readable half is right while its sentence is nine versions old is the
     * failure this exists for, spelled a second way.
     */
    @Test
    void everyExampleTheContractGivesIsInTheProseBesideIt() {
        String said = at(schema(), CANONICAL).get("description").asString();
        for (String example : rendered()) {
            assertTrue(said.contains(example),
                    () -> "the prose a reader builds against says this form: " + example);
        }
    }

    /** The sentences this compiler writes, one per form. */
    private static Set<String> rendered() {
        return everyForm().stream()
                .map(each -> RuleHandleSurface.PROSE.render(each, SourceId::value, null))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The sentences the contract gives as examples of a handle. */
    private static Set<String> examples() {
        JsonNode said = at(schema(), CANONICAL).get("examples");
        assertNotNull(said, "the canonical definition gives examples of what a handle reads as");
        Set<String> out = new LinkedHashSet<>();
        said.forEach(each -> out.add(each.asString()));
        return out;
    }

    /**
     * Every field of the document that carries a handle, as the schema says so.
     *
     * <p>By where the schema declares the field and not by what it is called. {@code rule} is
     * written under three parents, so a set of names would say there were fewer fields than there
     * are and go on saying it as more were added.
     */
    @Test
    void everyFieldTheCompilerWritesAHandleIntoIsOneTheSchemaSaysCarriesOne() {
        Map<String, RuleHandleSurface.Carries> written = new LinkedHashMap<>();
        for (RuleHandleSurface.InADocument each : RuleHandleSurface.InADocument.values()) {
            written.put(each.schemaPath(), each.carries());
        }

        assertEquals(declaredInTheSchema(), written,
                "the fields the schema says carry a handle and the fields the compiler writes one"
                        + " into");
    }

    /** The same, read off the schema: a field that is a handle refers to the canonical definition,
     *  and one that writes a sentence around a handle says which definition it embeds. */
    private static Map<String, RuleHandleSurface.Carries> declaredInTheSchema() {
        Map<String, RuleHandleSurface.Carries> out = new LinkedHashMap<>();
        walk(schema(), "", out);
        return out;
    }

    private static void walk(JsonNode at, String path, Map<String, RuleHandleSurface.Carries> out) {
        if (at.isObject()) {
            if (at.has("$ref") && REFERENCE.equals(at.get("$ref").asString())) {
                out.put(path, RuleHandleSurface.Carries.THE_HANDLE_ALONE);
            }
            if (at.has(EMBEDS) && REFERENCE.equals(at.get(EMBEDS).asString())) {
                out.put(path, RuleHandleSurface.Carries.A_SENTENCE_AROUND_IT);
            }
            // Under the canonical definition itself there is nothing to find, and walking into it
            // would name the definition as a field that carries a handle.
            at.propertyNames().forEach(key -> {
                if (!(path + "/" + key).equals(CANONICAL)) {
                    walk(at.get(key), path + "/" + key, out);
                }
            });
        } else if (at.isArray()) {
            for (int i = 0; i < at.size(); i++) {
                walk(at.get(i), path + "/" + i, out);
            }
        }
    }

    private static JsonNode at(JsonNode schema, String pointer) {
        JsonNode found = schema;
        for (String step : pointer.substring(1).split("/")) {
            found = found.get(step);
            assertNotNull(found, () -> "the schema declares " + pointer);
        }
        return found;
    }

    private static JsonNode schema() {
        try (InputStream said = AdequacyReport.class
                .getResourceAsStream(AdequacyReport.SCHEMA_RESOURCE)) {
            assertNotNull(said, "the schema this compiler ships");
            return JSON.readTree(said);
        } catch (Exception e) {
            throw new AssertionError("the schema this compiler ships is readable", e);
        }
    }
}
