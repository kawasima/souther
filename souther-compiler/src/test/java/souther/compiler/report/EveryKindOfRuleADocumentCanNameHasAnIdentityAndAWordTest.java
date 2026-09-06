package souther.compiler.report;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.BehaviorContract;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.WrittenOwner;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.ValueName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every kind of rule a document can name is one it can write down and one it has a word for.
 *
 * <p>Two total answers over {@link RuleRef}, and neither is total by anything the compiler checks.
 * They are switches with a throwing arm, so a kind added to the seal keeps compiling and stops the
 * first document that names one — at whichever build first writes a model with that kind of rule in
 * it, which is nobody's test and everybody's build.
 *
 * <p>That has happened. A rule about the strings at a position was added to the seal with both arms
 * left refusing, on the reasoning that nothing published one yet; the day something did, generating
 * an adequacy report threw. The refusal was right when it was written and wrong the moment the
 * reader existed, and nothing here connected the two.
 *
 * <p>So the population comes from the seal and every member is asked both questions. A kind added
 * without an answer fails here rather than in a build somebody else is running.
 */
class EveryKindOfRuleADocumentCanNameHasAnIdentityAndAWordTest {

    /**
     * One of each kind, built by hand.
     *
     * <p>From the seal and not from a compilation: what is being asked is whether every kind has an
     * answer, and a model that happens to write four kinds of rule would make the population what
     * that model states. A kind nothing can yet produce is a kind a document may still be asked to
     * name the day something produces one, which is exactly the case this exists for.
     */
    private static List<RuleRef> everyKind() {
        WrittenOwner.Body body = new WrittenOwner.Body("m", "b");
        SourceConstructOrigin written =
                new SourceConstructOrigin(body, 1, 0, SourceConstruct.BINARY);
        Clause.Ref clause = new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Amount")), 0),
                Optional.of(new ClauseName("cap")));
        return List.of(
                new RuleRef.Invariant(clause),
                new RuleRef.Ensures(
                        new BehaviorContract.RuleId(new ValueName.Behavior("m", "b"), 0, 0, null),
                        "c"),
                new RuleRef.Comparison("b", written),
                new RuleRef.Predicate("b", new SourceConstructOrigin(body, 2, 0,
                        SourceConstruct.CALL)));
    }

    /** That the list above is the seal and not a list somebody kept up by hand. */
    @Test
    void thePopulationIsEveryKindTheSealHas() {
        assertEquals(
                kindsOf(RuleRef.class),
                everyKind().stream().map(each -> (Class<?>) each.getClass())
                        .collect(Collectors.toSet()),
                "one of each kind of rule the seal has, and no other");
    }

    /**
     * Every kind of rule the seal names, which are its leaves.
     *
     * <p>Walked to the leaves rather than read off what {@code RuleRef} permits directly. The seal
     * divides first by how a reader finds a rule and then by which kind of rule it is, so its own
     * permits are the two halves and none of them is a kind a document names. Read one level deep,
     * this population would be two things nobody can build and no kind at all.
     */
    private static Set<Class<?>> kindsOf(Class<?> sealed) {
        Class<?>[] permits = sealed.getPermittedSubclasses();
        if (permits == null || permits.length == 0) {
            return Set.of(sealed);
        }
        Set<Class<?>> out = new java.util.LinkedHashSet<>();
        for (Class<?> each : permits) {
            out.addAll(kindsOf(each));
        }
        return out;
    }

    @Test
    void everyKindCanBeWrittenDown() {
        for (RuleRef each : everyKind()) {
            ObjectNode into = JsonMapper.builder().build().createObjectNode();
            AdequacyReport.ruleId(into, each);
            assertTrue(into.has("kind"),
                    "a document says which kind of rule it is naming: " + each);
        }
    }

    @Test
    void everyKindHasAWordForItsKind() {
        for (RuleRef each : everyKind()) {
            assertTrue(!AdequacyReport.schemaRuleKind(each).isBlank(),
                    "a document has a word for this kind of rule: " + each);
        }
    }

    /**
     * Two rules that are not one rule are written down as two.
     *
     * <p>What an identity in a document is for. A consumer groups by it, so two rules under one of
     * them are one rule to everything downstream — the second is counted at the first's classes,
     * and a reader sent to it is sent to the wrong place. The compiler's own equality is over every
     * coordinate; what is checked here is that the projection into the document keeps them apart.
     *
     * <p>The population varies each coordinate of each kind in turn, so a coordinate left out of
     * the document shows up as two of these being written alike. It caught the one this was written
     * for: a predicate's ordinal is counted within what wrote it, and a behavior writes such a rule
     * in its body and in its own clauses, so the first of each was numbered zero and published as
     * one rule.
     *
     * <p>What a rule is <em>called</em> is not among the coordinates, and two of these differing
     * only there are written alike. That is not a collision: an author's name for a clause belongs
     * to the clause the identity already names, and the label an {@code ensures} carries is read off
     * the rule the identity already names — so no two rules of a model differ there and nowhere
     * else. Nothing in the types says so, which is a question about those two kinds rather than
     * about this projection.
     *
     * <p>Nor is the behavior a predicate's own clauses were written by. That is the same name as
     * the rule's behavior and is refused where they differ, so there is no pair to tell apart —
     * which is checked as a pair that cannot be built rather than as two identities.
     */
    @Test
    void twoRulesThatAreNotOneAreWrittenDownAsTwo() {
        List<RuleRef> distinct = eachCoordinateVaried();
        assertEquals(distinct.size(), Set.copyOf(distinct).size(),
                "the population is rules that are not one another");

        List<String> written = distinct.stream().map(each -> {
            ObjectNode into = JsonMapper.builder().build().createObjectNode();
            AdequacyReport.ruleId(into, each);
            return into.toString();
        }).toList();

        assertEquals(written.size(), Set.copyOf(written).size(),
                "each of them is written under an identity of its own. Two written alike are two"
                        + " rules a consumer counts as one: " + written);
    }

    /**
     * The population above: for every kind, one rule per coordinate that tells it from another.
     *
     * <p>Varied one at a time rather than all together, so that a coordinate the document leaves
     * out is what collides rather than being hidden by the ones beside it.
     */
    private static List<RuleRef> eachCoordinateVaried() {
        WrittenOwner.Body body = new WrittenOwner.Body("m", "b");
        Clause.Id on = new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Amount")), 0);
        ValueName.Behavior behavior = new ValueName.Behavior("m", "b");
        return List.of(
                new RuleRef.Invariant(new Clause.Ref(on, Optional.of(new ClauseName("cap")))),
                new RuleRef.Invariant(new Clause.Ref(
                        new Clause.Id(TypeSymbols.declared(new TypeKey("other", "Amount")), 0),
                        Optional.of(new ClauseName("cap")))),
                new RuleRef.Invariant(new Clause.Ref(
                        new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Other")), 0),
                        Optional.of(new ClauseName("cap")))),
                new RuleRef.Invariant(new Clause.Ref(
                        new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Amount")), 1),
                        Optional.of(new ClauseName("cap")))),

                new RuleRef.Ensures(new BehaviorContract.RuleId(behavior, 0, 0, null), "c"),
                new RuleRef.Ensures(
                        new BehaviorContract.RuleId(new ValueName.Behavior("other", "b"), 0, 0,
                                null), "c"),
                new RuleRef.Ensures(
                        new BehaviorContract.RuleId(new ValueName.Behavior("m", "other"), 0, 0,
                                null), "c"),
                new RuleRef.Ensures(new BehaviorContract.RuleId(behavior, 1, 0, null), "c"),
                new RuleRef.Ensures(new BehaviorContract.RuleId(behavior, 0, 1, null), "c"),

                new RuleRef.Comparison("b", new SourceConstructOrigin(body, 1, 0,
                        SourceConstruct.BINARY)),
                new RuleRef.Comparison("other", new SourceConstructOrigin(body, 1, 0,
                        SourceConstruct.BINARY)),
                new RuleRef.Comparison("b", new SourceConstructOrigin(
                        new WrittenOwner.Body("other", "b"), 1, 0, SourceConstruct.BINARY)),
                new RuleRef.Comparison("b", new SourceConstructOrigin(
                        new WrittenOwner.Body("m", "helper"), 1, 0, SourceConstruct.BINARY)),
                new RuleRef.Comparison("b", new SourceConstructOrigin(body, 2, 0,
                        SourceConstruct.BINARY)),
                new RuleRef.Comparison("b", new SourceConstructOrigin(body, 1, 1,
                        SourceConstruct.BINARY)),

                new RuleRef.Predicate("b", new SourceConstructOrigin(body, 1, 0,
                        SourceConstruct.CALL)),
                new RuleRef.Predicate("other", new SourceConstructOrigin(body, 1, 0,
                        SourceConstruct.CALL)),
                new RuleRef.Predicate("b", new SourceConstructOrigin(
                        new WrittenOwner.Body("other", "b"), 1, 0, SourceConstruct.CALL)),
                new RuleRef.Predicate("b", new SourceConstructOrigin(
                        new WrittenOwner.Body("m", "helper"), 1, 0, SourceConstruct.CALL)),
                new RuleRef.Predicate("b", new SourceConstructOrigin(body, 2, 0,
                        SourceConstruct.CALL)),
                new RuleRef.Predicate("b", new SourceConstructOrigin(body, 1, 1,
                        SourceConstruct.CALL)),
                // The one the body's first predicate shares a number with: a behavior states a rule
                // about its strings in its own clauses too, and that is another construct numbered
                // from zero.
                new RuleRef.Predicate("b", new SourceConstructOrigin(
                        new WrittenOwner.Stated("m", "b"), 1, 0, SourceConstruct.CALL)));
    }

    /**
     * The words for what wrote a predicate are the owners one can be written by.
     *
     * <p>The schema promises two, and what may reach the field is whatever a {@code Predicate} can
     * be built with — so the two lists are one fact and are checked against each other rather than
     * kept alike by hand. Every owner the seal has is offered to the constructor: the ones it takes
     * are what a document may have to name, and the ones it refuses are the negative control that
     * says the check is doing something.
     */
    @Test
    void theWordsForWhatWroteAPredicateAreTheOwnersOneCanBeWrittenBy() {
        List<String> written = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        for (WrittenOwner owner : everyOwner()) {
            SourceConstructOrigin at =
                    new SourceConstructOrigin(owner, 0, 0, SourceConstruct.CALL);
            RuleRef.Predicate rule;
            try {
                rule = new RuleRef.Predicate("b", at);
            } catch (IllegalArgumentException refusedThere) {
                refused.add(owner.getClass().getSimpleName());
                continue;
            }
            ObjectNode into = JsonMapper.builder().build().createObjectNode();
            AdequacyReport.ruleId(into, rule);
            written.add(into.get("writtenIn").asString());
        }

        assertEquals(Set.of("body", "stated"), Set.copyOf(written),
                "the words a document may carry are the owners a predicate may be written by");
        assertEquals(List.of("Declaration", "Examples", "Fake"), refused,
                "and the owners that write no such rule are refused where one is made, which is"
                        + " what keeps the list above from being every owner there is");
    }

    /**
     * The coordinates a kind may carry are the ones its rules are written with.
     *
     * <p>The schema and the writer are two accounts of one identity, and what holds them together
     * is that each kind names what it carries and takes nothing else. Read the other way — each kind
     * listing what it may <em>not</em> have — every coordinate added has to be forbidden again in
     * every other kind by hand, and the first one added after that shape was settled leaked into
     * two of them: an invariant could carry what wrote a predicate's application, which is a shape
     * this compiler never writes and the schema promised to refuse.
     *
     * <p>So the two lists are compared rather than kept alike. A key the writer starts writing that
     * the kind does not name fails here, and a key a kind names that nothing writes fails here too —
     * the second is the direction that was open.
     */
    @Test
    void theCoordinatesAKindMayCarryAreTheOnesItsRulesAreWrittenWith() {
        Map<String, Set<String>> written = new LinkedHashMap<>();
        for (RuleRef each : eachCoordinateVaried()) {
            ObjectNode into = JsonMapper.builder().build().createObjectNode();
            AdequacyReport.ruleId(into, each);
            written.computeIfAbsent(AdequacyReport.schemaRuleKind(each), _ -> new TreeSet<>())
                    .addAll(into.propertyNames());
        }

        Map<String, Set<String>> allowed = new LinkedHashMap<>();
        for (JsonNode arm : schema().get("$defs").get("ruleId").get("oneOf")) {
            assertEquals(false, arm.get("additionalProperties").asBoolean(),
                    "a kind takes only what it names, so there is no list of what it may not have"
                            + " to fall behind: " + arm);
            allowed.put(arm.get("properties").get("kind").get("const").asString(),
                    new TreeSet<>(arm.get("properties").propertyNames()));
        }

        assertEquals(allowed, written,
                "each kind allows the coordinates its rules are written with, and no others");
    }

    /** The schema shipped beside this compiler, which is what a reader validates against. */
    private static JsonNode schema() {
        try (java.io.InputStream in = AdequacyReport.class.getResourceAsStream(
                "/souther/adequacy-schema-" + AdequacyReport.SCHEMA_VERSION + ".json")) {
            return JsonMapper.builder().build().readTree(in);
        } catch (java.io.IOException cannotRead) {
            throw new IllegalStateException("the schema is shipped beside this", cannotRead);
        }
    }

    /**
     * A rule a behavior's own clauses state is that behavior's, and cannot be built otherwise.
     *
     * <p>The one coordinate the document does not write, because it is not a coordinate: the owner
     * of such a rule is the behavior stating it, and {@code behavior} beside it is whose rule it is.
     * They are one name written once.
     *
     * <p>Held where the value is made rather than by writing the name twice. Left to agree, the
     * two could differ and the document would put both under one identity — which is the collision
     * the owner was published to end, arriving by the other door.
     */
    @Test
    void aRuleStatedInABehaviorsOwnClausesCannotBeAnothersRule() {
        assertEquals("b", new RuleRef.Predicate("b", new SourceConstructOrigin(
                        new WrittenOwner.Stated("m", "b"), 0, 0, SourceConstruct.CALL)).behavior(),
                "the behavior stating the rule is whose rule it is");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new RuleRef.Predicate("b", new SourceConstructOrigin(
                        new WrittenOwner.Stated("m", "other"), 0, 0, SourceConstruct.CALL)),
                "and a rule of one behavior's clauses cannot be another behavior's");
        assertTrue(refused.getMessage().contains("written by other"), refused.getMessage());
    }

    /** Every owner the seal has, one of each. */
    private static List<WrittenOwner> everyOwner() {
        QuotedFrom text = new QuotedFrom.TextItCannotName();
        List<WrittenOwner> out = List.of(
                new WrittenOwner.Declaration(new TypeKey("m", "Amount")),
                new WrittenOwner.Stated("m", "b"),
                new WrittenOwner.Body("m", "b"),
                new WrittenOwner.Examples(text, "m", "b"),
                new WrittenOwner.Fake(text, "m", "b"));
        assertEquals(Set.copyOf(List.of(WrittenOwner.class.getPermittedSubclasses())),
                out.stream().map(each -> (Class<?>) each.getClass())
                        .collect(Collectors.toSet()),
                "one of each owner the seal has, and no other");
        return out;
    }

    /** And the words tell the kinds apart, which is what a reader groups by. */
    @Test
    void andTheWordsTellTheKindsApart() {
        List<String> words = everyKind().stream()
                .map(AdequacyReport::schemaRuleKind).toList();

        assertEquals(words.size(), Set.copyOf(words).size(),
                "each kind is written under a word of its own: " + words);
    }
}
