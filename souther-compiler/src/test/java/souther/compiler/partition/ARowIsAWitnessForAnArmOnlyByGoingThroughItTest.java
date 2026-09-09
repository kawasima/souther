package souther.compiler.partition;

import souther.compiler.coverage.ArmProbe;
import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Prepared;
import souther.compiler.core.Core;
import souther.compiler.coverage.AlignedObservation;
import souther.compiler.coverage.ControlClaim;
import souther.compiler.coverage.ControlPointId;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.coverage.SiteNumbering;
import souther.compiler.coverage.Runs;
import souther.compiler.coverage.SeenComparison;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Shapes;
import souther.compiler.reading.CoverageRead;
import souther.compiler.reading.PathAccess;
import souther.compiler.reading.WayIn;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row is a witness for an arm by having been seen taking it, and by nothing beside.
 *
 * <p>What holds on the way to an arm is not the same thing as being there, and for a fork on a
 * comparison the two are said in different vocabularies: the way in is the comparison coming out a
 * way, which an observation answers with {@code saw}, and the arm is a place, which it answers with
 * {@code lit}. A match reads the same both ways round — the case a run matched at <em>is</em> the
 * arm — so a search held to the way in alone passes there and says nothing about a fork on a
 * comparison, where it certifies a row that was never seen taking the arm.
 */
class ARowIsAWitnessForAnArmOnlyByGoingThroughItTest {

    /** One fork on one comparison, so a way into an arm is a comparison coming out a way and the
     *  arm is somewhere else. */
    private static final String GATE = """
            module example.gate

            data Fee = Int
                invariant value >= 0

            behavior fee : (n: Int) -> Fee
                constructs Fee

            let fee (n) = if n > 0 then Fee(1) else Fee(0)
            """;

    /** The way in to a comparison's arm is the comparison, which is what makes the rest of this a
     *  question rather than a spelling. */
    @Test
    void theWayIntoAComparisonsArmNamesTheComparisonAndNotTheArm() {
        Model model = Model.of(GATE);

        for (Map.Entry<ArmProbe, PathAccess> each : model.read().arms().entrySet()) {
            assertInstanceOf(PathAccess.Ways.class, each.getValue(),
                    "both arms are reached: " + each);
            for (WayIn way : ((PathAccess.Ways) each.getValue()).ways()) {
                assertTrue(way.claims().stream()
                                .noneMatch(claim -> claim.at() instanceof
                                        ControlPointId.ArmPoint),
                        "and nothing on the way names the arm it leads to: " + way.claims());
            }
        }
    }

    /**
     * A run that did what the way in names and never reached the arm is no witness for it.
     *
     * <p>Which is a run this compiler produces: the comparison is one place and the arm is another,
     * and a reading that put a row at the classes the comparison leaves has said where the row
     * should go rather than where it went. Certified on the way in alone, the arm is answered with
     * a row nothing was ever seen taking — which is the reading certifying itself, at the one point
     * a run was there to ask.
     */
    @Test
    void aRunThatTookTheWayButNotTheArmIsNoWitness() {
        Model model = Model.of(GATE);
        Set<ArmProbe> everyArm = model.read().arms().keySet();

        FillResult filled = Generator.fill(model.subject(), List.of(),
                Generator.CandidateCheck.ANY, model.read(),
                // Seen doing everything the ways in name, and seen at no arm at all.
                _ -> new Generator.Watched.Ran(waysWithoutTheArms(model)),
                List.of(), List.of(), List.copyOf(everyArm), Budgets.generation());

        for (ArmProbe probe : everyArm) {
            assertFalse(filled.discharge().at(new Generator.ArmOwed(probe)) instanceof ArmDisposition.Built,
                    () -> "no row goes through an arm nothing was seen at: " + filled.discharge().arms().values());
        }
        assertEquals(List.of(), filled.rows(),
                () -> "so nothing is offered for one: " + filled.rows());
    }

    /** A run seen at the arms as well is the witness the same search was looking for, which is what
     *  says the test above is about the arm and not about the rows being unbuildable. */
    @Test
    void aRunSeenAtTheArmIsAWitness() {
        Model model = Model.of(GATE);
        Set<ArmProbe> everyArm = model.read().arms().keySet();

        FillResult filled = Generator.fill(model.subject(), List.of(),
                Generator.CandidateCheck.ANY, model.read(),
                _ -> new Generator.Watched.Ran(everywhere(model, everyArm)),
                List.of(), List.of(), List.copyOf(everyArm), Budgets.generation());

        assertTrue(filled.discharge().arms().values().stream().allMatch(ArmDisposition.Built.class::isInstance),
                () -> "each arm has a row through it: " + filled.discharge().arms().values());
    }

    /** Everything the ways in name, and nothing at any arm. */
    private static AlignedObservation waysWithoutTheArms(Model model) {
        Set<ArmProbe> taken = new LinkedHashSet<>();
        Set<SeenComparison> ways = new LinkedHashSet<>();
        collect(model.read(), taken, ways);
        taken.removeAll(model.read().arms().keySet());
        return Runs.of(model.numbering(), taken, ways);
    }

    /** The same, and the arms as well. */
    private static AlignedObservation everywhere(Model model, Set<ArmProbe> arms) {
        Set<ArmProbe> taken = new LinkedHashSet<>(arms);
        Set<SeenComparison> ways = new LinkedHashSet<>();
        collect(model.read(), taken, ways);
        return Runs.of(model.numbering(), taken, ways);
    }

    private static void collect(CoverageRead.Read read, Set<ArmProbe> taken,
                                Set<SeenComparison> ways) {
        for (PathAccess access : read.arms().values()) {
            if (!(access instanceof PathAccess.Ways found)) {
                continue;
            }
            for (WayIn way : found.ways()) {
                for (ControlClaim claim : way.claims()) {
                    switch (claim.at()) {
                        case ControlPointId.ArmPoint arm -> taken.add(arm.probe().get());
                        case ControlPointId.ComparisonPoint point ->
                                ways.add(new SeenComparison(point.at(), point.held()));
                    }
                }
            }
        }
    }

    private record Model(MeasuredInput subject, CoverageRead.Read read,
                         SiteNumbering numbering) {

        static Model of(String source) {
            Compilation compilation = Compilation.ofSource(source, "Main");
            compilation.answerEverything();
            String module = compilation.modules().get(0);
            Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
            RuleReadingSource rules = RuleReadings.of(compilation, module);
            Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
            assertNotNull(checked, "the model under test compiles");
            Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().stream()
                    .filter(b -> b.name().equals("fee")).findFirst().orElseThrow();
            InputDomain inputs = compilation.db()
                    .ask(new Adequacy.Inputs(module)).value().get("fee");
            Core body = checked.behaviorBodies().get("fee");
            assertNotNull(body, "the behavior under test has a body");
            CoverageSites.Plan plan = checked.plan();
            // What the compilation divides this behavior into, and not what the classes alone come
            // to. The line this fork is on is drawn off the comparison in the body, so a reading
            // that only asked the declarations would leave the position with no classes — and a
            // search with nothing to compose says nothing about what certifies what.
            Partitions.Partitioning partitioning =
                    compilation.db().ask(new Adequacy.Divided(module, "fee")).value();
            assertNotNull(partitioning, "the model divides the position the fork reads");
            assertFalse(partitioning.axes().isEmpty() || partitioning.axes().stream()
                            .allMatch(axis -> axis.classes().isEmpty()),
                    "and divides it into classes a row can be composed at");
            return new Model(MeasuredInput.of(spec.name(), inputs.reading(rules),
                    partitioning),
                    CoverageRead.of("fee", body, plan, inputs, rules), plan.numbering());
        }
    }
}
