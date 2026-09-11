package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A declaration whose clauses could not be worked out is a rule this reading did not reach, and not
 * a declaration that states nothing.
 *
 * <p>The two are the same empty list and opposite facts. One says a position holds every value its
 * type has and the model agrees; the other says the model was never asked. A reading handed the
 * second as the first reports a position as unconstrained, and nothing anywhere says it did not
 * look — which is the defect this whole change is about, one rung down from where it was found.
 *
 * <p>Reached by a module whose own values are not well founded, which is the one way to have a
 * declaration that resolves and an expansion that does not: its names are answered, so an importer
 * has the type and can write a behavior over it, and nothing expands its bodies, so there is no
 * representation of its clauses to be had. The cycle is that module's own error and is reported
 * there; what is held here is what the module beside it is told.
 *
 * <p>Held against the same model with the cycle removed. A run where both come out alike is a run
 * where the absence was read as an answer.
 */
class AnExpansionThatDidNotHappenIsARuleNotReachedTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The declaring module, whose {@code floor} is defined in terms of itself. */
    private static final String CYCLES = """
            module owner exposing ( Code, Plain, Yes, No )

            let floor = floor

            data Code = String
                invariant String.length(value) >= floor

            data Plain = String
                invariant String.length(value) >= 1

            data Yes
            data No
            """;

    /** The importer, which writes a behavior over each. */
    private static final String IMPORTER = """
            module importer exposing ( onCode, onPlain )

            import owner ( Code, Plain, Yes, No )

            behavior onCode : (i: Code) -> Yes | No
            let onCode (i) = Yes

            example onCode
                | "one" : (Code("ab")) -> Yes

            behavior onPlain : (i: Plain) -> Yes | No
            let onPlain (i) = Yes

            example onPlain
                | "one" : (Plain("ab")) -> Yes
            """;

    /** How far the rules about each behavior's position were read, in the importer. */
    private static Map<String, JsonNode> read(String owner) {
        Compilation compilation =
                Compilation.ofSources(List.of(owner, IMPORTER), ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        JsonNode document = JSON.readTree(
                AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts())));
        Map<String, JsonNode> out = new LinkedHashMap<>();
        for (JsonNode module : document.get("modules")) {
            for (JsonNode behavior : module.path("behaviors")) {
                out.put(behavior.path("name").asString(),
                        behavior.path("partition").path("axesMeasure"));
            }
        }
        return out;
    }

    /**
     * The declaration whose module could not be expanded is not read as one with no rules.
     *
     * <p>Its neighbour in the same module is the control: {@code Plain} writes a clause of literals
     * and is affected by the cycle only in that its module holds one. Both go unexpanded, so what
     * this says is that the position is short of its rules rather than free of them — and the run
     * where the two are told apart is the run below.
     */
    @Test
    void aPositionWhoseRulesWereNeverExpandedIsNotReadAsHavingNone() {
        Map<String, JsonNode> withACycle = read(CYCLES);
        Map<String, JsonNode> without = read(CYCLES.replace("let floor = floor", "let floor = 1"));

        assertNotNull(withACycle.get("onPlain"), "the importer measures the position");
        assertNotNull(without.get("onPlain"), "in both runs");

        assertNotEquals(without.get("onPlain"), withACycle.get("onPlain"),
                "a clause nobody expanded leaves the position short of its rules, and a clause read"
                        + " to the end leaves it holding what the rule admits — read alike, the"
                        + " first is reported as a position the model says nothing about");
    }

    /** And with the cycle gone, the same two declarations are read to the end. */
    @Test
    void withTheCycleGoneBothAreRead() {
        Map<String, JsonNode> without = read(CYCLES.replace("let floor = floor", "let floor = 1"));

        assertEquals(without.get("onPlain"), without.get("onCode"),
                "a clause composed from a value and one written out are read alike once there is"
                        + " an expansion to read");
    }
}
