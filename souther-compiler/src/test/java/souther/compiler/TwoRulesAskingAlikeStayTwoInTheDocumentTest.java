package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.meta.ModulePath;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two rules asking the same question of the same position stay two in the document.
 *
 * <p>Asked of the document and not of the value behind it, because that is where every round of this
 * has gone wrong: the type was right and the seam it crossed was narrower. The rule reached
 * {@code PartitionEvidence} and the writer published the words for finding it; then it reached the
 * finding's arguments and that writer did not ask for it either.
 *
 * <p>Two arms of one {@code ensures} clause may name the same case, which {@code RuleRef.Ensures}
 * says outright — the author's words for them are the same words. So the handle cannot tell them
 * apart, and the identity beside it is what does. Both surfaces are asked here, and so is the part
 * of an identity that is easiest to leave out: two modules may each declare an {@code Amount}, and a
 * projection written without the module maps two rules onto one.
 */
class TwoRulesAskingAlikeStayTwoInTheDocumentTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Two arms of one clause, named alike by the author because the case is the name. */
    private static final String TWO_ARMS = """
            module m

            data R = { a: Int }
            data Found
            data Missing

            behavior f : (r: R) -> Found | Missing
                ensures Found -> r.a <= Int.min(20, 30)
                ensures Found -> r.a >= Int.min(40, 50)
            let f (r) = Missing

            example f
                | "one" : (R { a = 1 }) -> Missing
            """;

    /** Two comparisons of one condition, which are two rules and collide on nothing. */
    private static final String TWO_COMPARISONS = """
            module m

            data R = { a: Int }
            data X
            data Y

            behavior f : (r: R) -> X | Y
            let f (r) = if r.a <= Int.min(20, 30) && r.a >= Int.min(40, 50) then X else Y

            example f
                | "one" : (R { a = 1 }) -> Y
            """;

    private static JsonNode documentOf(Compilation compilation) {
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return JSON.readTree(AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
    }

    private static JsonNode behaviorOf(String source) {
        return documentOf(Compilation.ofSource(source, "Main"))
                .get("modules").get(0).get("behaviors").get(0);
    }

    /** What the reading left standing, as a consumer of the document reads it. */
    private static JsonNode standing(String source) {
        return behaviorOf(source).get("partition").get("notRead");
    }

    /** And the findings they came out as, which is the other surface a consumer reads. */
    private static List<JsonNode> reported(String source) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode each : behaviorOf(source).get("findings")) {
            if (each.get("kind").asString().equals("partition_not_read")) {
                out.add(each);
            }
        }
        return out;
    }

    /** What each entry says about anything but which rule it is, which is what makes the two ask
     *  alike. */
    private static Set<String> asked(Iterable<JsonNode> entries) {
        Set<String> out = new LinkedHashSet<>();
        entries.forEach(each -> {
            JsonNode copy = each.deepCopy();
            ((tools.jackson.databind.node.ObjectNode) copy).remove("ruleId");
            out.add(copy.toString());
        });
        return out;
    }

    /** Whole entries, so that anything a consumer could join on counts. */
    private static Set<String> whole(Iterable<JsonNode> entries) {
        Set<String> out = new LinkedHashSet<>();
        entries.forEach(each -> out.add(each.toString()));
        return out;
    }

    /** And what each says its rule is, which is what tells two of them apart. */
    private static Set<String> rules(Iterable<JsonNode> entries) {
        Set<String> out = new LinkedHashSet<>();
        entries.forEach(each -> out.add(each.get("ruleId").toString()));
        return out;
    }

    /**
     * Two arms of one clause, named alike by the author and told apart by the document.
     *
     * <p>The handle is `ensures f (Found)` for both: an unnamed clause is called by the case its arm
     * names, and both arms name `Found`. Published on the handle alone, these were two pairs of
     * identical objects — and a consumer could not tell two rules asking alike from one question
     * written twice.
     */
    @Test
    void twoArmsNamedAlikeAreTwoRules() {
        JsonNode standing = standing(TWO_ARMS);

        assertEquals(2, standing.size(), () -> "one entry per rule: " + standing);
        assertEquals(1, asked(standing).size(),
                () -> "and the fixture really asks alike — same position, same handle, same"
                        + " reason: " + standing);
        assertEquals(2, whole(standing).size(),
                () -> "so two entries a consumer can tell apart: " + standing);
        assertEquals(2, rules(standing).size(), () -> "which two rules they are: " + standing);
    }

    /**
     * And the findings they came out as say it too.
     *
     * <p>Both surfaces or neither. The identity reached the finding's arguments and the writer did
     * not ask for it, so the questions told the two apart and the findings did not — which is the
     * same seam one surface over.
     */
    @Test
    void andSoDoTheFindingsTheyCameOutAs() {
        List<JsonNode> reported = reported(TWO_ARMS);

        assertEquals(2, reported.size(), () -> "one per rule: " + reported);
        assertEquals(1, asked(reported).size(),
                () -> "asking alike here too: " + reported);
        assertEquals(2, whole(reported).size(),
                () -> "two a consumer can tell apart: " + reported);
        assertEquals(2, rules(reported).size(), () -> "and two rules: " + reported);
    }

    /** Two comparisons of one condition, which must stay two on both surfaces as well. */
    @Test
    void twoComparisonsOfOneConditionAreTwoRules() {
        JsonNode standing = standing(TWO_COMPARISONS);

        assertEquals(2, standing.size(), () -> "one entry per rule: " + standing);
        assertEquals(2, whole(standing).size(), () -> "both distinguishable: " + standing);
        assertEquals(2, rules(standing).size(), () -> "and two rules: " + standing);
        assertEquals(2, rules(reported(TWO_COMPARISONS)).size(),
                () -> "on both surfaces: " + reported(TWO_COMPARISONS));
    }

    /**
     * An identity written without the module is not one.
     *
     * <p>A declaration is its module and its name — two modules may each declare an {@code Amount}
     * and they are two types — and a behavior's constructs are numbered from zero in each source. A
     * projection that writes the name and leaves the module out maps two rules onto one identity,
     * which is the one thing this field may not do. Asked across two modules of one compile, since
     * that is where the collision is.
     */
    @Test
    void twoModulesDeclaringAlikeAreTwoIdentities() {
        JsonNode document = documentOf(Compilation.ofSources(List.of("""
                module a

                data Amount = Int
                    invariant cap = value <= Int.min(20, 30)

                behavior f : (x: Amount) -> Int
                let f (x) = x.value

                example f
                    | "one" : (Amount(1)) -> 1
                """, """
                module b

                data Amount = Int
                    invariant cap = value <= Int.min(20, 30)

                behavior g : (x: Amount) -> Int
                let g (x) = x.value

                example g
                    | "one" : (Amount(1)) -> 1
                """), ModulePath.EMPTY));

        Set<String> rules = new LinkedHashSet<>();
        for (JsonNode module : document.get("modules")) {
            for (JsonNode behavior : module.get("behaviors")) {
                JsonNode standing = behavior.get("partition").get("unanswered");
                if (standing != null) {
                    rules.addAll(rules(standing));
                }
            }
        }

        assertEquals(2, rules.size(), () -> "one `cap` per module, and two identities: " + rules);
    }
}
