package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A page and a document are written from one reading, and disagree about how much to say and never
 * about what there was to say.
 *
 * <p>Which behaviors have anything said about their positions was worked out twice. The page left
 * the two lines out where the lists beside the measures were all empty; the document left the object
 * out where the boundary the positions come off was not derived. Neither read the measures. So one
 * compilation answered a reader two ways: a {@code >->} composition wrote {@code no_subject} into
 * the document and nothing onto the page, and a behavior bounded only on a {@code Decimal} wrote
 * that its rules draw no line into one and nothing into the other (issue #1079).
 *
 * <p>Measured over a module holding every shape that decides it, because the two rules agreed on
 * most of them: a model with one shape passes against either rule and says nothing about the pair.
 */
class TwoSurfacesAgreeWhichBehaviorsHaveASectionTest {

    /**
     * One module holding each way the answer can go.
     *
     * <p>A composition with no positions of its own; a behavior whose rules divide nothing and draw
     * no line; one bounded only on a carrier with no step, whose lines were the ones being lost; and
     * one the rules divide and bound in the ordinary way.
     */
    private static final String MODEL = """
            module example.surfaces

            data Wrap = { v: String }
            data Mid = { v: String }
            data Out = { v: String }

            behavior widen : (w: Wrap) -> Mid constructs Mid
            let widen (w) = Mid { v = w.v }
            behavior narrow : (m: Mid) -> Out constructs Out
            let narrow (m) = Out { v = m.v }

            behavior both = widen >-> narrow

            example both
                | (Wrap { v = "x" }) -> Out { v = "x" }

            data Rate = Decimal
                invariant open = value > 0.00m && value < 1.00m

            data Priced = { rate: Rate }
            data Ok

            behavior take : (p: Priced) -> Ok
            let take (p) = Ok

            data Amount = Int
                invariant capped = value >= 0 && value <= 1000

            data Req = { cost: Amount }
            data Yes
            data No
            data Verdict = Yes | No

            behavior judge : (r: Req) -> Verdict
            let judge (r) = if r.cost.value < 500 then Yes else No
            """;

    /**
     * The same behaviors, on both surfaces.
     *
     * <p>Written as one equality rather than as two lists to be read side by side: what is being
     * held is that the two answers are one answer, and a pair of expectations would be two places to
     * keep in step with whichever rule moved.
     */
    @Test
    void bothSurfacesSayWhichBehaviorsHaveASectionAboutTheirPositions() {
        assertEquals(inTheJson(), inTheText(),
                "a page and a document written from one reading");
    }

    /**
     * And it is not the empty answer on both.
     *
     * <p>An equality holds against two surfaces that both say nothing, which is what the page did
     * over the model this issue was about. Every behavior here has a section, the composition
     * included: it is measured at its stages, which is what its two measures say, and a measure that
     * says why it has no number is one a reader is owed rather than one to leave out.
     */
    @Test
    void andTheAnswerIsNotThatNobodyHasOne() {
        assertEquals(Set.of("widen", "narrow", "both", "take", "judge"), inTheText());
    }

    /**
     * The one that was lost is in it, and says what it measured.
     *
     * <p>{@code take}'s every rule is a bound on a {@code Decimal}. Its lines went unbuilt and its
     * section unwritten, so the page said nothing about the behavior at all while the module was
     * called adequate. Named here because an agreement between two surfaces is also what two
     * surfaces that both lost it have.
     */
    @Test
    void theBehaviorThisIssueWasAboutHasOneAndSaysWhatItFound() {
        String take = block(report().human(SourceRendering.namedByIdentity(SourceLayouts.NONE)), "take");

        assertTrue(take.contains("border      borders 2"), take);
        assertTrue(take.contains("this order names no value there"), take);
    }

    /** The lines a report writes under one behavior's name. */
    private static String block(String report, String behavior) {
        StringBuilder out = new StringBuilder();
        boolean under = false;
        for (String line : report.lines().toList()) {
            if (line.startsWith("  ") && !line.startsWith("    ") && !line.isBlank()) {
                under = line.trim().split("\\s+")[0].equals(behavior);
            }
            if (under) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static Set<String> inTheText() {
        Set<String> out = new LinkedHashSet<>();
        String name = null;
        for (String line : report().human(SourceRendering.namedByIdentity(SourceLayouts.NONE)).lines().toList()) {
            if (line.startsWith("  ") && !line.startsWith("    ") && !line.isBlank()) {
                name = line.trim().split("\\s+")[0];
            } else if (name != null && line.startsWith("    partition ")) {
                out.add(name);
            }
        }
        return out;
    }

    private static Set<String> inTheJson() {
        JsonNode root = JsonMapper.builder().build()
                .readTree(report().json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        Set<String> out = new LinkedHashSet<>();
        root.get("modules").forEach(module -> module.get("behaviors").forEach(behavior -> {
            if (behavior.has("partition")) {
                out.add(behavior.get("name").asString());
            }
        }));
        return out;
    }

    private static AdequacyReport report() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }
}
