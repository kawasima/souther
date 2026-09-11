package souther.compiler.partition;

import souther.compiler.coverage.ArmProbe;
import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Prepared;
import souther.compiler.check.Sig;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputDomain;
import souther.compiler.reading.CoverageRead;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One demand over one position, asked as a class and asked as the way into an arm.
 *
 * <p>These are two questions a reader has and one search. A class is the model dividing a position;
 * an arm is a place in the body, and what it takes to arrive there is what the reading says. What
 * they have in common is the shape of the answer: a row that sits in some classes, written as near
 * as it can be to what the model already states.
 *
 * <p>So a way into an arm that settles one position asks exactly what a class of that position asks,
 * and the row offered is the same row. Where the two searches were written apart, the class's was
 * composed against a value the module states and the arm's against nothing — the same question
 * answered two ways in one block (issue #1034).
 *
 * <p>Asked of every such way the model has rather than of one named here. Which arms a body has and
 * which of them one position settles is the reading's answer, and a test naming a probe would be
 * asserting this of whichever arm a reading happened to number that way.
 */
class OneDemandOverOnePositionIsOneRowHoweverItIsAskedTest {

    private static final String SHIPPING = """
            module example.shipping

            data Membership = Premium | Standard

            data Delivery = Express | Regular

            data Fee = Int
                invariant value >= 0

            behavior shippingFee : (member: Membership, delivery: Delivery, rush: Bool) -> Fee
                constructs Fee

            let baseFee (tier: Membership): Int =
                match tier with
                    | Premium -> 0
                    | Standard -> 500

            let expressFee (speed: Delivery): Int =
                match speed with
                    | Express -> 500
                    | Regular -> 0

            let shippingFee (member, delivery, rush) =
                Fee(baseFee(member) + expressFee(delivery))
            """;

    /**
     * Every arm one position settles is offered the row that position's class is offered.
     *
     * <p>The two are asked separately here — one run owing the class and nothing else, one owing the
     * arm and nothing else — so that neither answer can be the other one being handed on. What comes
     * back is compared by the values the row carries, which is the whole of what an author is given.
     */
    @Test
    void anArmOnePositionSettlesIsOfferedTheRowThatPositionsClassIs() {
        Model model = Model.of(SHIPPING, "shippingFee");
        List<Axis> axes = model.subject().axes().axes();
        int asked = 0;
        for (ArmProbe probe : model.read().arms().keySet()) {
            for (Map.Entry<Integer, Integer> pin : onePinWaysInto(probe, model, axes)) {
                Axis axis = axes.get(pin.getKey());
                assertEquals(
                        rowsOf(model, List.of(new ClassOfAPosition(axis.id(),
                                axis.classes().get(pin.getValue()).id())), List.of()),
                        rowsOf(model, List.of(), List.of(probe)),
                        "the class of " + axis.path() + " and the arm it is the way into");
                asked++;
            }
        }
        assertTrue(asked > 0, "this model has an arm one position is the way into");
    }

    /** The ways into {@code probe} that settle exactly one position, as that position and its
     *  class. */
    private static List<Map.Entry<Integer, Integer>> onePinWaysInto(ArmProbe probe, Model model,
                                                                    List<Axis> axes) {
        List<Map.Entry<Integer, Integer>> out = new ArrayList<>();
        if (!(model.read().armAt(probe) instanceof souther.compiler.reading.PathAccess.Ways ways)) {
            return out;
        }
        for (souther.compiler.reading.WayIn way : ways.ways()) {
            CellSelection at = InteractionCells.at(way, ways.arrivesAt(), axes);
            if (at == null) {
                continue;
            }
            List<Interpretation> readings = new ArrayList<>();
            at.interpretations(reading -> {
                readings.add(reading);
                return Taking.Taken.AND_MORE;
            });
            if (readings.size() == 1 && readings.get(0).pins().size() == 1) {
                out.addAll(readings.get(0).pins().entrySet());
            }
        }
        return out;
    }

    /** What one run of the search offered, by the values each row carries. */
    private static List<List<String>> rowsOf(Model model, List<ClassOfAPosition> classes,
                                             List<ArmProbe> arms) {
        return Generator.fill(model.subject(), List.of(), Generator.CandidateCheck.ANY,
                        model.read(), Generator.Trial.NOTHING_RUNS, List.of(), classes, arms,
                        Budgets.generation())
                .rows().stream()
                .map(row -> row.inputs().stream().map(FixtureTemplate::text).toList())
                .toList();
    }

    private record Model(MeasuredInput subject, CoverageRead.Read read) {

        /** The groups of the one reading, for a caller asking about the combinations alone. */
        static Model of(String source, String behavior) {
            Compilation compilation = Compilation.ofSource(source, "Main");
            compilation.answerEverything();
            String module = compilation.modules().get(0);
            Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
            Map<String, Sig> sigs = compilation.db().ask(new Bodies.Signatures(module)).value();
            RuleReadingSource rules = RuleReadings.of(compilation, module);
            Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
            assertNotNull(prepared);
            assertNotNull(sigs);
            assertNotNull(checked);
            Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().stream()
                    .filter(b -> b.name().equals(behavior)).findFirst().orElseThrow();
            InputDomain inputs =
                    compilation.db().ask(new Adequacy.Inputs(module)).value().get(behavior);
            assertNotNull(inputs, "the behavior's inputs were read");
            Core body = checked.behaviorBodies().get(behavior);
            assertNotNull(body, "the behavior under test has a body");
            return new Model(MeasuredInput.of(spec.name(), inputs.reading(rules),
                    Partitions.of(spec.name(), inputs, rules,
                            souther.compiler.query.ReadAs.THE_COMPILATION_DOES)),
                    CoverageRead.of(spec.name(), body,
                            checked.plan(), inputs,
                            rules));
        }
    }
}
