package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.DocumentShape;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.report.AdequacyReport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A case of an input and the class of its position are one obligation where the behavior has that
 * position, and a case of a behavior that has none is owed at itself.
 *
 * <p>Two derivations meet at one entry while both are about one behavior's own position: the
 * signature counts the cases a row applies the behavior to, and the partition counts the classes a
 * row sits in. A {@code >->} composition takes what its first stage takes and is read there, so it
 * has cases and no positions — and the classes that stage divides are that stage's, covered by that
 * stage's rows.
 *
 * <p>Which is why the account may not key one there, and why the account has to hold an entry of
 * its own for it: a finding names what a row is owed at, and a consumer joins the two by that name.
 * Keyed at the stage, the finding would name another behavior's entry; keyed at nothing published,
 * it would leave the consumer to rebuild the identity by searching the signature — the same
 * rebuilding somewhere other than where the obligation is.
 */
class ACaseOfAnInputIsAClassOnlyWhereTheBehaviorHasThePositionTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Two stages and the composition of them, with whichever rows are handed in. */
    private static String model(String rows) {
        return """
                module example.composed

                data On
                data Off
                data Flag = On | Off
                data Mid = { flag: Flag }
                data Yes
                data No
                data Verdict = Yes | No

                behavior first : (f: Flag) -> Mid
                    constructs Mid
                let first (f) = Mid { flag = f }

                behavior second : (m: Mid) -> Verdict
                let second (m) =
                    match m.flag with
                        | On  -> Yes
                        | Off -> No

                behavior both = first >-> second
                """ + rows;
    }

    /** One row each, so both are short of the same case of the same type. */
    private static final String ONE_ROW_EACH = model("""

            example first
                | "on" : (On) -> Mid { flag = On }

            example both
                | "on" : (On) -> Yes
            """);

    /** Every case of the stage covered, and the composition asked about only one of them. */
    private static final String THE_STAGE_COVERS_BOTH = model("""

            example first
                | "on"  : (On)  -> Mid { flag = On }
                | "off" : (Off) -> Mid { flag = Off }

            example both
                | "on" : (On) -> Yes
            """);

    /** And the same with the composition asked about both. */
    private static final String THE_COMPOSITION_COVERS_BOTH = model("""

            example first
                | "on"  : (On)  -> Mid { flag = On }
                | "off" : (Off) -> Mid { flag = Off }

            example both
                | "on"  : (On)  -> Yes
                | "off" : (Off) -> No
            """);

    /** The stage writes its cases at its own position, so the two derivations are one entry. */
    @Test
    void aBehaviorWithItsOwnPositionOwesTheCaseAtTheClassOfThatPosition() {
        About.ACaseNoRowAppliesItTo missing =
                caseFindingOf(findingsOf(ONE_ROW_EACH), "first").orElseThrow();
        ObligationIdentity.OfAClass owed = assertInstanceOf(ObligationIdentity.OfAClass.class,
                missing.obligationIdentity(),
                () -> "a case of a declared input is the class of its position: " + missing);
        assertEquals("first/f", owed.classOfAPosition().at().toString(),
                "which is the position the declaration names");
    }

    /** The composition writes its cases at no position of its own, so the case is the entry. */
    @Test
    void aCompositionOwesTheCaseAtTheCaseItself() {
        About.ACaseNoRowAppliesItTo missing =
                caseFindingOf(findingsOf(ONE_ROW_EACH), "both").orElseThrow();
        ObligationIdentity.OfAnInputCase owed = assertInstanceOf(
                ObligationIdentity.OfAnInputCase.class, missing.obligationIdentity(),
                () -> "a composition divides no position of its own: " + missing);
        assertEquals("both", owed.behavior(), "and the case is this behavior's");
        assertEquals(0, owed.at(), "at the input the signature takes first");
    }

    /**
     * A row of the stage does not discharge the composition's case, and a row of the composition
     * does.
     *
     * <p>What makes them two obligations rather than one written twice. The values are the same
     * case of the same type — the composition takes what the stage takes — so anything reading the
     * values alone would call the first covered by the second.
     */
    @Test
    void aRowOfTheStageDoesNotDischargeTheCompositionsCase() {
        List<Adequacy.Finding> covered = findingsOf(THE_STAGE_COVERS_BOTH);
        assertTrue(caseFindingOf(covered, "first").isEmpty(),
                () -> "the stage's own rows cover its cases: " + covered);
        assertEquals(new ObligationIdentity.OfAnInputCase("both", 0, off(covered)),
                caseFindingOf(covered, "both").orElseThrow().obligationIdentity(),
                () -> "and the composition is still owed a row at its own: " + covered);

        List<Adequacy.Finding> all = findingsOf(THE_COMPOSITION_COVERS_BOTH);
        assertTrue(caseFindingOf(all, "both").isEmpty(),
                () -> "which a row of the composition discharges: " + all);
    }

    /**
     * And the finding joins the entry the account publishes for it.
     *
     * <p>The half a shape of its own leaves open. An identity nothing publishes sends a consumer
     * back to the signature to search for the case by its words, which is the identity rebuilt away
     * from the obligation — the thing not borrowing the stage's class was for.
     */
    @Test
    void theFindingJoinsTheEntryTheAccountPublishes() {
        JsonNode document = reportOf(THE_STAGE_COVERS_BOTH);
        DocumentShape.assertShapedLikeTheSchema(document);
        JsonNode behavior = behaviorOf(document, "both");
        List<JsonNode> owed = new ArrayList<>();
        for (JsonNode input : behavior.get("signature").get("inputs")) {
            input.get("obligations").forEach(entry -> owed.add(entry.get("obligationId")));
        }

        JsonNode finding = findingOf(behavior, "input_case_unspecified");
        assertEquals(1, owed.stream().filter(entry -> entry.equals(finding.get("obligationId")))
                        .count(),
                () -> "the finding lands on one entry of the account: " + owed + " / " + finding);

        // And the stage's account holds no such entry: what it is owed at is a class, which the
        // axes publish.
        for (JsonNode input : behaviorOf(document, "first").get("signature").get("inputs")) {
            assertEquals(0, input.get("obligations").size(),
                    () -> "a behavior with a position of its own publishes its cases as classes: "
                            + input);
        }
    }

    /** And the two identities are not equal, however alike the cases are. */
    @Test
    void theCompositionsCaseIsNotTheStagesClass() {
        List<Adequacy.Finding> found = findingsOf(ONE_ROW_EACH);
        assertNotEquals(caseFindingOf(found, "first").orElseThrow().obligationIdentity(),
                caseFindingOf(found, "both").orElseThrow().obligationIdentity(),
                "a composition's case is not its first stage's class");
    }

    /** Which case of the input the composition is short of, as the finding names it. */
    private static souther.compiler.types.TypeSymbol off(List<Adequacy.Finding> found) {
        return caseFindingOf(found, "both").orElseThrow().missing();
    }

    private static java.util.Optional<About.ACaseNoRowAppliesItTo> caseFindingOf(
            List<Adequacy.Finding> found, String behavior) {
        for (Adequacy.Finding finding : found) {
            if (finding.about() instanceof About.ACaseNoRowAppliesItTo about
                    && finding.subject() instanceof FindingSubject.OfABehavior(String named)
                    && named.equals(behavior)) {
                return java.util.Optional.of(about);
            }
        }
        return java.util.Optional.empty();
    }

    private static JsonNode behaviorOf(JsonNode document, String named) {
        for (JsonNode behavior : document.get("modules").get(0).get("behaviors")) {
            if (named.equals(behavior.get("name").asString())) {
                return behavior;
            }
        }
        throw new AssertionError("no behavior `" + named + "` in the document");
    }

    private static JsonNode findingOf(JsonNode behavior, String kind) {
        for (JsonNode finding : behavior.get("findings")) {
            if (kind.equals(finding.get("kind").asString())) {
                return finding;
            }
        }
        throw new AssertionError("no " + kind + " finding: " + behavior.get("findings"));
    }

    private static List<Adequacy.Finding> findingsOf(String model) {
        return measured(model).db().ask(new Adequacy.Findings("example.composed")).value();
    }

    private static JsonNode reportOf(String model) {
        return JSON.readTree(AdequacyReport.of(measured(model))
                .json(SourceNameResolver.identity()));
    }

    private static Compilation measured(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertTrue(compilation.errors().isEmpty(),
                () -> "a model that did not compile answers every question with nothing: "
                        + compilation.errors());
        return compilation;
    }
}
