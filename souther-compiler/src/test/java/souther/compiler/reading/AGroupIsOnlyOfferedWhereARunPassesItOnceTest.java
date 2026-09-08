package souther.compiler.reading;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.core.Core;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.coverage.Plans;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What may be claimed of a run, given what a run's record can hold.
 *
 * <p>A record says which places a run passed. It does not say how many times it passed each, so
 * nothing read off one can tell two facts about a place a run came back to from two facts about a
 * place it passed once. A group says more than that: that these decisions were settled these ways
 * and were consumed into one value together — which is about one passing, and about their outcomes
 * being that passing's.
 *
 * <p>So a group is offered only where a run passes the meeting once. Today nothing forms one
 * anywhere else, because the reading does not go inside a function value at all; that is how the
 * property is met and is not the property. Stated the other way round, a reading that started going
 * in there would quietly begin offering rows for combinations nothing could ever be shown to sit in.
 */
class AGroupIsOnlyOfferedWhereARunPassesItOnceTest {

    /** Two forks, one inside a function value handed to a combinator and one outside it. */
    private static final String DOUBLING = """
            module example.tally

            data Ns = { values: List<Int> }

            behavior doubled : (ns: Ns, loud: Bool) -> Ns
                constructs Ns

            let doubled (ns, loud) =
                if loud then
                    Ns { values = List.map(x -> if x > 10 then x * 2 else x, ns.values) }
                else
                    Ns { values = ns.values }
            """;

    /** One meeting of two decisions, standing where a run passes it once. */
    private static final String SHIPPING = """
            module example.shipping

            data Membership = Premium | Standard

            data Delivery = Express | Regular

            data Fee = Int
                invariant value >= 0

            behavior shippingFee : (member: Membership, delivery: Delivery) -> Fee
                constructs Fee

            let baseFee (tier: Membership): Int =
                match tier with
                    | Premium -> 0
                    | Standard -> 500

            let expressFee (speed: Delivery): Int =
                match speed with
                    | Express -> 500
                    | Regular -> 0

            let shippingFee (member, delivery) =
                Fee(baseFee(member) + expressFee(delivery))
            """;

    /**
     * A run may come back to what is inside a function value, and may not to what is outside one.
     *
     * <p>How many times a combinator applies what it was handed is that combinator's business, so
     * everything written in there is somewhere a run may come back to. The fork above it stands in
     * the body and is passed once.
     */
    @Test
    void whatStandsInsideAFunctionValueIsSomewhereARunMayComeBackTo() {
        Model model = Model.of(DOUBLING, "doubled");

        List<Core> forks = Plans.nodesWithArms(model.plan());
        assertEquals(2, forks.size(), "the body under test has two forks");
        assertEquals(1, forks.stream().filter(model.plan()::mayRepeat).count(),
                "and one of them stands where a run may come back to");
    }

    /** A meeting a run passes once is offered, which is what the next test takes away. */
    @Test
    void aMeetingPassedOnceIsOffered() {
        Model model = Model.of(SHIPPING, "shippingFee");

        assertFalse(model.groups().isEmpty(),
                "two decisions are consumed into one value here, and a run passes the meeting once");
    }

    /**
     * The same meeting, where a run may come back to it, is not offered.
     *
     * <p>The same body and the same decisions, so what changed is only what may be established about
     * a run that reaches them. Written this way round because the reading does not go inside a
     * function value today: a model whose meeting stands in one produces no group whether or not
     * anything asks this question, and a test over one would pass with the question deleted.
     */
    @Test
    void aMeetingARunMayComeBackToIsNotOffered() {
        Model model = Model.of(SHIPPING, "shippingFee");

        List<Interaction> asIfRepeated = CoverageRead.of(model.behavior(), model.body(),
                model.planWhereEverythingRepeats(), model.inputs(), model.rules())
                .interactions();

        assertTrue(asIfRepeated.isEmpty(),
                "nothing could show a row to sit in one of these, so none is offered");
    }

    /** One model, read the way the generator reads it. */
    private record Model(String behavior, Core body, CoverageSites.Plan plan, InputDomain inputs,
                         RuleReadingSource rules) {

        static Model of(String source, String behavior) {
            Compilation compilation = Compilation.ofSource(source, "Main");
            compilation.answerEverything();
            String module = compilation.modules().get(0);
            Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
            assertNotNull(checked, "the model under test compiles");
            Core body = checked.behaviorBodies().get(behavior);
            assertNotNull(body, "the behavior under test has a body");
            return new Model(behavior, body,
                    checked.plan(),
                    compilation.db().ask(new Adequacy.Inputs(module)).value().get(behavior),
                    RuleReadings.of(compilation, module));
        }

        List<Interaction> groups() {
            return CoverageRead.of(behavior, body, plan, inputs, rules).interactions();
        }

        /** The same plan, answering that a run may come back to anywhere. */
        CoverageSites.Plan planWhereEverythingRepeats() {
            return Plans.whereEverythingRepeats(plan);
        }
    }
}
