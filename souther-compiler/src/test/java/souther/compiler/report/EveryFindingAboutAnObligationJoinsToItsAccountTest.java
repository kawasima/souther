package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A finding about something a row is owed for names it the way its account names it.
 *
 * <p>What a consumer does with a finding is act on the thing it is about, which means finding that
 * thing in the account the numbers are counted in. The words a reader is shown do not do it: two
 * points of one line are shown the same words where what differs is the run beside the line, and two
 * arms are shown the same word at the same place where what differs is the rule a caller handed the
 * fork. So the identity is written, and this holds that it is written wherever the document names
 * such a thing and that it lands on exactly one entry.
 *
 * <p>Asked of the document rather than of the writer. The rule the writer keeps is that a subject
 * carrying an identity says so by its type, which closes the question locally; what that cannot say
 * is that a shape which should carry one does. Here the two ends are compared: every finding of a
 * kind that is about an obligation, against the array that account publishes.
 */
@Tag("population")
class EveryFindingAboutAnObligationJoinsToItsAccountTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * One helper of this module's own, called twice with two rules the call sites wrote.
     *
     * <p>A fork the caller decides is one arm per rule handed in, so this body owes two arms at the
     * one {@code if} the helper holds. The helper is written here, so both copies keep the positions
     * they were written at: the two arms are the same word at the same place, and the row that goes
     * through both {@code then}s leaves both {@code else}s owed. They are the case the identity was
     * introduced for — what a reader is shown of the two says nothing about which is which.
     *
     * <p>A library helper is not this case. Its copies are stamped with the call that spliced them,
     * so a reader is shown two places, and the model that reaches for one of those tells the two
     * apart without an identity.
     */
    private static final String TWO_RULES_AT_ONE_FORK = """
            module example.decide

            data Person =
                { active: Bool
                , retired: Bool
                }
            data Yes
            data No
            data Verdict = Yes | No

            let decide (p: (Person) -> Bool, x: Person): Verdict =
                if p(x) then Yes else No

            behavior both : (x: Person) -> Verdict
            let both (x) =
                if decide(a -> a.active, x) == Yes then decide(b -> b.retired, x) else No

            example both
                | "both hold" : (Person { active = true, retired = true }) -> Yes
            """;

    /** The kinds that are about something a row is owed for, and the account each is counted in. */
    private static final List<String> ABOUT_AN_OBLIGATION =
            List.of("boundary_unmet", "domain_point_uncovered", "arm_unreached");

    @Test
    void everyFindingAboutAnObligationNamesOneEntryOfItsAccount() {
        List<String> wrong = new ArrayList<>();
        int joined = 0;
        for (JsonNode document : documents()) {
            for (JsonNode module : document.get("modules")) {
                for (JsonNode behavior : module.get("behaviors")) {
                    for (JsonNode finding : behavior.get("findings")) {
                        String kind = finding.get("kind").asString();
                        if (!ABOUT_AN_OBLIGATION.contains(kind)) {
                            // The other half of the same rule. A finding about something no account
                            // counts has no such thing to name, and a key written there would be a
                            // consumer's join landing on nothing.
                            if (finding.has("obligationId")) {
                                wrong.add(behavior.get("name").asString() + ": a " + kind
                                        + " finding names an obligation");
                            }
                            continue;
                        }
                        joined++;
                        JsonNode id = finding.get("obligationId");
                        if (id == null) {
                            wrong.add(behavior.get("name").asString() + ": a " + kind
                                    + " finding names no obligation");
                            continue;
                        }
                        long found = entriesOf(behavior, module, kind).stream()
                                .filter(entry -> id.equals(entry.get("obligationId"))).count();
                        if (found != 1) {
                            wrong.add(behavior.get("name").asString() + ": a " + kind
                                    + " finding joins " + found + " entries");
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), wrong, "a finding about an obligation joins to it");
        assertTrue(joined > 0, "and the corpus reaches such findings");
    }

    /**
     * Two arms of one fork, told apart by nothing a reader is shown.
     *
     * <p>The case the identity is for. Both are the {@code else} of one {@code if}, both are written
     * where that {@code if} is, and they are two things to cover because two callers handed the fork
     * two rules. Joined on what a reader is shown, a consumer lands on both or on whichever came
     * first.
     */
    @Test
    void twoArmsATableCannotTellApartAreToldApartByTheirIdentity() {
        JsonNode behavior = onlyBehaviorOf(reportOf(TWO_RULES_AT_ONE_FORK));
        java.util.Map<String, List<JsonNode>> shownAlike = new java.util.LinkedHashMap<>();
        for (JsonNode finding : behavior.get("findings")) {
            if ("arm_unreached".equals(finding.get("kind").asString())) {
                shownAlike.computeIfAbsent(
                        finding.get("subject").asString() + " " + finding.get("at"),
                        _ -> new ArrayList<>()).add(finding);
            }
        }

        List<JsonNode> together = shownAlike.values().stream()
                .filter(each -> each.size() > 1).findFirst()
                .orElseThrow(() -> new AssertionError(
                        "two arms shown alike: " + behavior.get("findings")));
        assertEquals(2, together.size(),
                () -> "the helper's else, once per rule handed to it: " + together);
        assertEquals(2, together.stream()
                        .map(f -> String.valueOf(f.get("obligationId"))).distinct().count(),
                () -> "told apart by which rule the caller handed the fork: " + together);
        // And each of them is the identity of one entry of the account, which is what the join
        // above holds of every finding — said here of the pair a reader cannot tell apart, since
        // that is the pair a consumer would land on twice.
        for (JsonNode arm : together) {
            assertEquals(1, entriesOf(behavior, onlyModuleOf(reportOf(TWO_RULES_AT_ONE_FORK)),
                            "arm_unreached").stream()
                            .filter(entry -> arm.get("obligationId").equals(
                                    entry.get("obligationId"))).count(),
                    () -> "and lands on one arm of the account: " + arm);
        }
    }

    private static List<JsonNode> entriesOf(JsonNode behavior, JsonNode module, String kind) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode from = switch (kind) {
            case "arm_unreached" -> behavior.get("branch").get("obligations");
            case "boundary_unmet", "domain_point_uncovered" ->
                    behavior.get("partition").get("obligations");
            default -> throw new IllegalStateException(kind);
        };
        from.forEach(out::add);
        // A line a declaration drew is owed once for the module and is kept under the declaration,
        // so a finding about one joins there rather than in the behavior it was read at.
        for (JsonNode declared : module.get("declarations")) {
            declared.get("obligations").forEach(out::add);
        }
        return out;
    }

    private static JsonNode onlyBehaviorOf(JsonNode document) {
        return onlyModuleOf(document).get("behaviors").get(0);
    }

    private static JsonNode onlyModuleOf(JsonNode document) {
        return document.get("modules").get(0);
    }

    private static JsonNode reportOf(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return JSON.readTree(
                AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
    }

    private static List<JsonNode> documents() {
        List<JsonNode> out = new ArrayList<>();
        for (ConformanceCorpus corpus : ConformanceCorpus.all()) {
            ConformanceCorpus.Analysed analysed = corpus.analyse();
            out.add(JSON.readTree(analysed.report().json(
                    new SourceRendering(corpus.names(), analysed.compilation().texts()))));
        }
        out.add(reportOf(TWO_RULES_AT_ONE_FORK));
        return out;
    }
}
