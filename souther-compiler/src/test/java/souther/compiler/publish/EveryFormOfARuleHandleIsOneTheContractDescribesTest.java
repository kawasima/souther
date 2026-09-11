package souther.compiler.publish;

import souther.compiler.diag.QuotedFrom;
import souther.compiler.diag.SourcePos;
import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleRef;
import souther.compiler.report.AdequacyReport;
import souther.compiler.source.SourceId;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.WrittenOwner;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
     * A source the handles below are written in, so that the prose has a line and a column to say.
     *
     * <p>A handle carries a place, and what line that is at is what the file is laid out as — so a
     * test about what a handle reads as says which file it means and hands the text over, as the
     * document writer does.
     */
    private static final String BILLING = "module billing\n" + "\n".repeat(12)
            + "data Amount = Int    invariant cap = value <= 100\n";

    private static final SourceLayout LAID_OUT = SourceLayout.of(BILLING, IN);

    /** And a text nobody named, which is the other half of what the contract gives examples of. */
    private static final String A_BUFFER = "module billing\n" + "\n".repeat(5) + "  invariant x\n";

    private static final SourceLayout UNNAMED = SourceLayout.of(A_BUFFER);

    private static final SourceRendering NAMED = new SourceRendering(SourceId::value,
            place -> place.quotedFrom() instanceof QuotedFrom.ASourceThisCompileHolds
                    ? LAID_OUT : UNNAMED);

    /** The clause of the source above, which is written at the line and column the contract's own
     *  examples are spelled with. */
    private static final SourcePos CLAUSE = LAID_OUT.placeAt(BILLING.indexOf("invariant"));

    /** The same clause, in the text nobody named. */
    private static final SourcePos IN_A_BUFFER = UNNAMED.placeAt(A_BUFFER.indexOf("invariant"));

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
            out.add(new PublishedRuleHandle.ReachedOutOfSight(kind, "Money"));
        }
        return List.copyOf(out);
    }

    private static PublishedRuleHandle.Place inASourceThisCompileHolds() {
        return new PublishedRuleHandle.Place.InSource(
                new PublishedAt(CLAUSE, new PublishedAt.Where.Here()));
    }

    private static PublishedRuleHandle.Place inATextWithNoName() {
        return new PublishedRuleHandle.Place.Unplaced(IN_A_BUFFER);
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
     * Every published word for a rule with no name is one some rule of the model is called by.
     *
     * <p>The projection read the other way round. Every kind of written rule has a word because the
     * switch that chooses one is total, and what is not total by anything the compiler checks is the
     * far side: a word added here that no rule projects to is a word the contract would go on
     * promising and no document could ever carry — which is this issue's own defect, in the one
     * place the population above is taken from a declaration rather than from what is produced.
     */
    @Test
    void everyPublishedWordIsOneSomeRuleIsCalledBy() {
        assertEquals(
                Set.of(PublishedRuleKind.values()),
                everyKindOfWrittenRule().stream().map(PublishedRuleKind::of)
                        .collect(Collectors.toSet()),
                "the words a rule of the model is called by, and the words the contract has");
    }

    /**
     * One rule of every kind the author writes rather than names.
     *
     * <p>From the seal, so the day a third kind of written rule exists this is asked about it. Walked
     * to the leaves: what {@code RuleRef.Written} permits is what a document names, and a half of the
     * seal above it is nothing an author writes.
     */
    private static List<RuleRef.Written> everyKindOfWrittenRule() {
        WrittenOwner.Body body = new WrittenOwner.Body("m", "b");
        List<RuleRef.Written> out = List.of(
                new RuleRef.Comparison("b",
                        new SourceConstructOrigin(body, 0, 0, SourceConstruct.BINARY)),
                new RuleRef.Predicate("b",
                        new SourceConstructOrigin(body, 1, 0, SourceConstruct.CALL)),
                new RuleRef.Fork("b",
                        new SourceConstructOrigin(body, 2, 0, SourceConstruct.IF)));

        assertEquals(
                Set.of(RuleRef.Written.class.getPermittedSubclasses()),
                out.stream().map(each -> (Class<?>) each.getClass()).collect(Collectors.toSet()),
                "one of each kind of rule an author writes rather than names, and no other");
        return out;
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
                .map(each -> RuleHandleProse.said(each, NAMED, null))
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
        for (RuleHandleSurface each : RuleHandleSurface.values()) {
            written.put(each.schemaPath(), each.carries());
        }

        assertEquals(declaredInTheSchema(), written,
                "the fields the schema says carry a handle and the fields the compiler writes one"
                        + " into");
    }

    /**
     * A field that is a handle writes one, and a field that puts words around one writes those.
     *
     * <p>The two are not interchangeable and the surface is what says which: handed a handle, a
     * field whose sentence has more in it would carry a document's shortest true answer and lose the
     * rest, and told a sentence, a field a consumer reads as a handle would carry words it cannot
     * take apart. Which each is, is already the pair the check above compares, so this asks that the
     * writing side act on it rather than carry it.
     */
    @Test
    void aFieldIsWrittenTheWayItSaysItCarriesAHandle() {
        PublishedRuleHandle handle = new PublishedRuleHandle.NamedInvariant("Amount", "cap");
        PublishedSentence sentence = PublishedSentence.AroundAHandle.alone(handle);
        for (RuleHandleSurface each : RuleHandleSurface.values()) {
            DocumentItem into = whereItBelongs(each);
            switch (each.carries()) {
                case THE_HANDLE_ALONE -> {
                    each.put(into, handle, NAMED, null);
                    assertThrows(IllegalStateException.class,
                            () -> each.put(whereItBelongs(each), sentence, NAMED, null),
                            () -> "a field that is the handle is not told a sentence: " + each);
                }
                case A_SENTENCE_AROUND_IT -> {
                    each.put(into, sentence, NAMED, null);
                    assertThrows(IllegalStateException.class,
                            () -> each.put(whereItBelongs(each), handle, NAMED, null),
                            () -> "a field with words of its own is not handed a handle: " + each);
                }
            }
            assertTrue(into.node().has(each.key()),
                    () -> "and either way the field is written under the key this names: " + each);
        }
    }

    /**
     * Every part a handle is written into is a part the schema declares under that name.
     *
     * <p>What ties a name to a place. The two are one value so that no writer can put an array under
     * one name and claim the place of another — but a value is still a claim until something reads
     * it, and what reads it is the schema: the place has to be an array of objects, and the name has
     * to be the name of a property that leads there. Left unread, a part could name a field the
     * contract has never heard of and every check between the writer and the surface would still
     * agree with every other.
     *
     * <p>Reached from the property, because that is the direction a document is written in. A part
     * two sections publish is reached from each of them and is one definition, so what is asked is
     * that every property leading to it is called what this part is called, and that there is one.
     */
    @Test
    void everyPartAHandleIsWrittenIntoIsOneTheSchemaDeclaresUnderThatName() {
        for (DocumentPart part : DocumentPart.values()) {
            JsonNode declared = at(schema(), part.schemaPath());
            assertEquals("array", declared.get("type").asString(),
                    () -> "the schema declares a repeated part at " + part.schemaPath());
            assertNotNull(declared.get("items"),
                    () -> "and says what a row of it looks like: " + part.schemaPath());

            assertEquals(Set.of(part.key()), namesLeadingTo(part.schemaPath()),
                    () -> "and every property that leads there is what this part is called: "
                            + part);
        }
    }

    /**
     * The names of the properties that lead to {@code path}, whether by sitting there or by
     * referring to it.
     *
     * <p>Both, because a definition is reached through {@code $ref} and a part written in place is
     * not, and a document writer cannot tell which of the two it is filling in.
     */
    private static Set<String> namesLeadingTo(String path) {
        Set<String> found = new LinkedHashSet<>();
        leadingTo(schema(), "", "#" + path, path, found);
        return found;
    }

    private static void leadingTo(JsonNode at, String here, String reference, String path,
                                  Set<String> found) {
        if (at.isObject()) {
            at.propertyNames().forEach(key -> {
                String below = here + "/" + key;
                JsonNode under = at.get(key);
                if (here.endsWith("/properties")
                        && (below.equals(path) || refersTo(under, reference))) {
                    found.add(key);
                }
                leadingTo(under, below, reference, path, found);
            });
        } else if (at.isArray()) {
            for (int i = 0; i < at.size(); i++) {
                leadingTo(at.get(i), here + "/" + i, reference, path, found);
            }
        }
    }

    /** Whether {@code said} is that reference, or a composition one branch of which is. */
    private static boolean refersTo(JsonNode said, String reference) {
        if (!said.isObject()) {
            return false;
        }
        if (said.has("$ref") && reference.equals(said.get("$ref").asString())) {
            return true;
        }
        JsonNode all = said.get("allOf");
        if (all == null) {
            return false;
        }
        for (JsonNode each : all) {
            if (refersTo(each, reference)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A field is written into the object the schema declares it on, and refused anywhere else.
     *
     * <p>What a field is includes where it lives, and that was the half nothing compared: the
     * contract said where each of these sits and the writer said which part of the document it was
     * building, and the two answers never met. A handle written into some other object is a field
     * the schema declares nothing about, however carefully the surfaces themselves are counted —
     * which is this issue's own defect, arrived at from the writing side.
     */
    @Test
    void aFieldIsRefusedWhereTheSchemaDoesNotDeclareIt() {
        PublishedRuleHandle handle = new PublishedRuleHandle.NamedInvariant("Amount", "cap");
        PublishedSentence sentence = PublishedSentence.AroundAHandle.alone(handle);
        for (RuleHandleSurface each : RuleHandleSurface.values()) {
            DocumentItem elsewhere =
                    DocumentItem.at(JSON.createObjectNode(), "/$defs/somewhereElse/items");

            assertThrows(IllegalStateException.class,
                    () -> {
                        switch (each.carries()) {
                            case THE_HANDLE_ALONE ->
                                    each.put(elsewhere, handle, NAMED, null);
                            case A_SENTENCE_AROUND_IT ->
                                    each.put(elsewhere, sentence, NAMED, null);
                        }
                    },
                    () -> "a handle written into an object the schema does not declare this field"
                            + " on: " + each);
            assertTrue(elsewhere.node().isEmpty(),
                    () -> "and nothing was written there: " + each);
        }
    }

    /**
     * An object at the place the schema declares {@code surface} on.
     *
     * <p>Read off the surface, which is what makes the pair above say something: what is asked is
     * whether writing anywhere else is refused, and an object built from some other answer would be
     * refused for being that other answer rather than for being elsewhere.
     */
    private static DocumentItem whereItBelongs(RuleHandleSurface surface) {
        String path = surface.schemaPath();
        return DocumentItem.at(JSON.createObjectNode(),
                path.substring(0, path.length() - "/properties/".length() - surface.key().length()));
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
            at.propertyNames().forEach(key -> walk(at.get(key), path + "/" + key, out));
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
