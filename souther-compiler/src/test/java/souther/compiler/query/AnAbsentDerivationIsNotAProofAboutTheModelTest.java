package souther.compiler.query;

import souther.compiler.diag.SourceLayouts;
import org.junit.jupiter.api.Test;
import souther.compiler.check.PathReachability;
import souther.compiler.report.AdequacyReport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a measure could not read is not a fact about what the model says.
 *
 * <p>{@code NotApplicable} is the strongest thing a measure says: the model was read to the end and
 * holds no subject for this measure, so no row anybody writes would give it one. A measure that
 * introduces one from an empty collection is making that claim without the reading behind it — and
 * an empty collection is what a derivation that did not come back leaves.
 *
 * <p>The arm measure did it twice over. Which behaviors have a body was read off the elaborated
 * bodies and what each owes was read off the plan, which is itself read off them; a module the
 * compile stopped in has neither, so every behavior in it was answered "this behavior has no body"
 * — on a report whose line above said {@code implemented} (issue #996).
 *
 * <p>What holds the fix is that both directions are here. A derivation that is absent must not
 * produce an inapplicable answer, and a model that genuinely has no subject must still get one:
 * the correction is worthless if it turns every settled nothing into a doubt.
 */
class AnAbsentDerivationIsNotAProofAboutTheModelTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Compilation measured(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    private static Adequacy.BranchEvidence branchOf(Compilation compilation, String module,
                                                    String behavior) {
        Map<String, Adequacy.BranchEvidence> branches =
                compilation.db().ask(new Adequacy.BranchCoverage(module)).value();
        assertNotNull(branches, "the arm measure answered for " + module);
        Adequacy.BranchEvidence branch = branches.get(behavior);
        assertNotNull(branch, "the arm measure answered for " + behavior);
        return branch;
    }

    /**
     * A model whose compile stops before the bodies are elaborated, with a {@code let} written.
     *
     * <p>The error is the point and not the subject: what this model has to be is one whose
     * declarations say a body is written and whose {@code Bodies.Checked} does not come back, and
     * the test asserts both rather than trusting the model to still arrange it.
     */
    private static final String STOPPED = """
            module example.rooms

            data Item = { sku: String }
            data Line = { sku: String }

            behavior pick : (item: Item) -> Line
                constructs Line
                ensures Line -> value.sku == item.sku
            let pick (item) = Line { sku = item.sku }

            example pick
                | "one" : (Item { sku = "a" }) -> Line { sku = "a" }
            """;

    /** A body that was not elaborated is a measurement that could not be finished. */
    @Test
    void aBodyThatWasNotElaboratedIsNotABehaviorWithNoBody() {
        Compilation compilation = measured(STOPPED);

        assertFalse(compilation.errors().isEmpty(), "this model is one the compile stops in");
        assertNull(compilation.db().ask(new Bodies.Checked("example.rooms")).value(),
                "and stops before the bodies are elaborated, which is what this is about");

        Measure<ArmSummary> measured =
                branchOf(compilation, "example.rooms", "pick").measured();

        Measurement.FailedToMeasure<?> failed = assertInstanceOf(
                Measurement.FailedToMeasure.class, measured,
                () -> "the model says a body is written, so what it owes is unknown rather than"
                        + " nothing: " + measured);
        assertEquals(Adequacy.BranchEvidence.Unelaborated.BODIES_NOT_ELABORATED, failed.why());
        assertEquals(WeakeningSet.of(new Weakening.BodiesNotElaborated("example.rooms")),
                failed.by(), "and says what it went without");
    }

    /**
     * And the document does not say both things about it.
     *
     * <p>The two halves are a line apart. Read from the declarations the behavior is
     * {@code implemented}; read from the elaborated bodies it had none — and the report printed
     * each of them under the other.
     *
     * <p>Asked of the document a build reads rather than of the one a person does. What is held
     * here is which words are written under which behavior, and a substring of the whole rendering
     * answers neither: {@code implemented} is inside {@code unimplemented}, a line about one
     * behavior reads the same as a line about the next, and the exact spacing of a column is not
     * what this is about.
     */
    @Test
    void theDocumentDoesNotCallAnImplementedBehaviorOneWithNoBody() {
        JsonNode root = JSON.readTree(AdequacyReport.of(measured(STOPPED))
                .json(souther.compiler.diag.SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        JsonNode behavior = root.get("modules").get(0).get("behaviors").get(0);

        assertEquals("pick", behavior.get("name").asString());
        assertEquals("implemented", behavior.get("implementation").asString(),
                "the declarations say a body is written here");
        assertEquals("bodies_not_elaborated", behavior.get("branch").get("reason").asString(),
                () -> "and the arm measure says what stopped it rather than saying the model has"
                        + " no body: " + behavior.get("branch"));
        assertEquals("unavailable", behavior.get("branch").get("status").asString(),
                "it has no number");
        assertEquals(List.of("bodies_not_elaborated"),
                behavior.get("branch").get("weakening").valueStream()
                        .map(JsonNode::asString).toList(),
                "and says what it went without, which is what tells it from a measure nobody asked"
                        + " for");
    }

    /** And the count of rows is left out rather than written as zero, for the same reason. */
    @Test
    void theDocumentLeavesOutACountItCouldNotMake() {
        JsonNode root = JSON.readTree(AdequacyReport.of(measured(STOPPED))
                .json(souther.compiler.diag.SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        JsonNode behavior = root.get("modules").get(0).get("behaviors").get(0);

        assertFalse(behavior.has("rows"),
                () -> "no source of this module was evaluated, so nothing counted its rows: "
                        + behavior);
        assertFalse(behavior.has("pending"), () -> "and none of them either: " + behavior);
    }

    /** A {@code >->} composition still has no body of its own, which is the model's own answer. */
    @Test
    void aCompositionStillHasNoBodyOfItsOwn() {
        Compilation compilation = measured("""
                module example.comp

                data A = { n: Int }
                data B = { n: Int }
                data C = { n: Int }

                behavior one : (a: A) -> B
                    constructs B
                let one (a) = B { n = a.n }

                behavior two : (b: B) -> C
                    constructs C
                let two (b) = C { n = b.n }

                behavior both = one >-> two

                example both
                    | "one" : (A { n = 1 }) -> C { n = 1 }
                """);

        assertTrue(compilation.errors().isEmpty(), () -> "this one compiles: "
                + compilation.errors());
        Measure<ArmSummary> measured =
                branchOf(compilation, "example.comp", "both").measured();

        Measure.NotApplicable<?> none = assertInstanceOf(Measure.NotApplicable.class, measured,
                () -> "its arms are its stages' and are measured there: " + measured);
        assertEquals(Adequacy.BranchEvidence.NoArms.NO_BODY, none.why());
    }

    /**
     * The coverage answers for every behavior of the module it was asked about, compositions
     * included.
     *
     * <p>A key carried part of the answer, and the two readers of this map read it two ways: one
     * as a composition with no subject, one as this compiler disagreeing with itself. Neither is
     * right about a behavior whose boundary did not work out, which is a third thing a key cannot
     * say and which the front end does produce — so the report stopped on it.
     *
     * <p>So a composition's answer is {@code NONE} and it is the producer that says so, from the
     * declarations it is holding anyway. What a reader does with this map is look a name up.
     */
    @Test
    void theCoverageAnswersForEveryBehavior() {
        Compilation compilation = measured("""
                module example.mixed

                data A = { n: Int }
                data B = { n: Int }
                data C = { n: Int }

                behavior one : (a: A) -> B
                    constructs B
                let one (a) = B { n = a.n }

                behavior two : (b: B) -> C
                    constructs C
                let two (b) = C { n = b.n }

                behavior both = one >-> two

                behavior injected : (a: A) -> B

                example both
                    | "one" : (A { n = 1 }) -> C { n = 1 }
                """);

        Map<String, PartitionEvidence> answered =
                compilation.db().ask(new Adequacy.Coverage("example.mixed")).value();
        assertNotNull(answered, "the coverage answered");

        souther.compiler.check.Prepared module =
                compilation.db().ask(new Shapes.Prepared("example.mixed")).value();
        assertEquals(module.behaviors().stream()
                        .map(souther.compiler.ast.Hir.BehaviorDef::name).toList(),
                List.copyOf(answered.keySet()),
                "the coverage answers for every behavior the module declares");
        for (souther.compiler.ast.Hir.BehaviorDef behavior : module.behaviors()) {
            PartitionEvidence found = answered.get(behavior.name());
            assertNotNull(found, () -> "an answer for " + behavior.name());
            // And a composition's answer is the one that says the model holds no subject here,
            // which is what a reader used to work out for itself from the key not being there.
            if (module.isComposition(behavior)) {
                assertEquals(PartitionEvidence.NONE, found,
                        () -> behavior.name() + " is measured at its stages");
            } else {
                assertNotEquals(PartitionEvidence.NONE, found,
                        () -> behavior.name() + " is measured here");
            }
        }
    }

    /** And a body that forks nowhere still owes no arm, which is the other settled nothing. */
    @Test
    void aBodyThatForksNowhereStillOwesNoArm() {
        Compilation compilation = measured("""
                module example.ok

                data Item = { sku: String }
                data Line = { sku: String }

                behavior pick : (item: Item) -> Line
                    constructs Line
                let pick (item) = Line { sku = item.sku }

                example pick
                    | "one" : (Item { sku = "a" }) -> Line { sku = "a" }
                """);

        assertTrue(compilation.errors().isEmpty(), () -> "this one compiles: "
                + compilation.errors());
        Measure<ArmSummary> measured =
                branchOf(compilation, "example.ok", "pick").measured();

        Measure.NotApplicable<?> none = assertInstanceOf(Measure.NotApplicable.class, measured,
                () -> "the body is here and decides nothing: " + measured);
        assertEquals(Adequacy.BranchEvidence.NoArms.NO_ARM_OBLIGATIONS, none.why());
    }

    /**
     * The reading of what the guards above each place leave is not made where the bodies were not.
     *
     * <p>The same rule the arm measure is held to, one derivation over. The reading is read off the
     * elaborated bodies; a module the compile stopped in has none, and the answer that came back was
     * a map with nothing in it — which is what a module whose bodies hold no place answers too.
     * Nothing between the two, so a reader that walked the reading and found nowhere was told the
     * model divides nowhere, on a compile that never looked.
     */
    @Test
    void thePlacesOfABodyThatWasNotElaboratedAreNotReadAsNoPlaces() {
        Compilation compilation = measured(STOPPED);

        assertFalse(compilation.errors().isEmpty(), "this model is one the compile stops in");
        assertFalse(compilation.db().ask(new Bodies.Checked("example.rooms")).present(),
                "and stops before the bodies are elaborated, which is what this is about");

        assertFalse(compilation.db().ask(new Adequacy.PathReached("example.rooms")).present(),
                "so there is no reading of its places, rather than a reading that found none");
    }

    /**
     * And a module whose bodies were elaborated and hold no place is read as holding none.
     *
     * <p>The other direction, and the reason the answer above is absent rather than the reading
     * being made absent whenever it is empty: a module of injected behaviors has been read to the
     * end, and what it holds is nothing.
     */
    @Test
    void thePlacesOfAModuleThatDeclaresNoBodyAreNone() {
        Compilation compilation = measured("""
                module example.injected

                data A = { n: Int }
                data B = { n: Int }

                behavior asked : (a: A) -> B
                """);

        assertTrue(compilation.errors().isEmpty(), () -> "this one compiles: "
                + compilation.errors());
        assertTrue(compilation.db().ask(new Bodies.Checked("example.injected")).present(),
                "its bodies were elaborated");

        Answer<Map<String, PathReachability.Answers>> read =
                compilation.db().ask(new Adequacy.PathReached("example.injected"));
        assertTrue(read.present(), "so the reading was made");
        assertEquals(Map.of(), read.value(), "and what it found is nothing");
    }
}
