package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.core.Core;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.Requirements;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standing inside an arm of a fork is something a row had to be, and it is on the account.
 *
 * <p>A comparison written inside an arm is reached by the values that took the arm and by no others.
 * Left off the way to it, the account of such a comparison says nothing stood on the way — which is
 * the sentence a reader is entitled to make of an empty account, and it is false of every model that
 * forks on a case.
 *
 * <p>What goes on is the narrowing and not the arm. Which case the scrutinee turned out to be is a
 * thing a row can be composed to be; which arm of which {@code match} was taken is a fact about the
 * text, and nothing composing a row could act on it. Where this reading arrives at no narrowing the
 * arm is declined, so what a search does not represent stays counted.
 */
class AnArmOfAForkIsOnTheWayLikeAnyOtherConditionTest {

    private static final String MODEL = """
            module example.arm

            data Kind = Plain | Special
            data Pair = { kind: Kind, n: Int }

            behavior onAParameter : (kind: Kind, n: Int) -> Bool
            let onAParameter (kind, n) =
                match kind with
                    | Plain   -> n > 0
                    | Special -> n > 10

            behavior onAField : (p: Pair) -> Bool
            let onAField (p) =
                match p.kind with
                    | Plain   -> p.n > 0
                    | Special -> p.n > 10

            behavior onSomethingComposed : (p: Pair) -> Bool
            let onSomethingComposed (p) =
                match (if p.n > 5 then Plain else Special) with
                    | Plain   -> p.n > 0
                    | Special -> p.n > 10
            """;

    /** A narrowing is what a fork on a case states, named by the position it narrows. */
    @Test
    void aForkOnAParameterPutsTheCaseTheArmSelectsOnTheWay() {
        assertEquals(List.of("kind@Plain", "kind@Special"), narrowingsIn("onAParameter"));
    }

    /** And it names the position the scrutinee is at, wherever that is. */
    @Test
    void aForkOnAFieldNarrowsThatField() {
        assertEquals(List.of("p.kind@Plain", "p.kind@Special"), narrowingsIn("onAField"));
    }

    /**
     * A fork on something no position holds is declined, and declined is not silence.
     *
     * <p>The comparisons inside the arms are reached under something, and this reading cannot say
     * what. Left off, a search would compose rows for their lines believing nothing stood in the way
     * — which is the same answer it gets for a comparison at the top of a body.
     */
    @Test
    void aForkOnSomethingComposedIsDeclinedRatherThanLeftOff() {
        List<OnTheWay> way = wayOf("onSomethingComposed");
        assertTrue(narrowingsIn("onSomethingComposed").isEmpty(),
                "no position was narrowed: " + way);
        assertEquals(List.of(new OnTheWay.Why.ForkArmNotReadAsANarrowing(),
                        new OnTheWay.Why.ForkArmNotReadAsANarrowing()),
                way.stream().filter(OnTheWay.Declined.class::isInstance)
                        .map(each -> ((OnTheWay.Declined) each).why()).toList(),
                "each arm said so, at the arm");
    }

    /**
     * And what a composer reads off it is a requirement on the parameter.
     *
     * <p>One entry per narrowing, keyed by the position that had to be narrowed. Read as anything
     * else, a composer would be holding the arm rather than what a row has to be.
     */
    @Test
    void whatAComposerReadsOffItIsARequirementOnThePosition() {
        for (List<OnTheWay> assumed : ways("onAField")) {
            Requirements.Merge read = new WayToTheBorder(assumed).requirements();
            Requirements required = assertInstanceOf(Requirements.Merge.Merged.class, read,
                    "one arm's narrowing holds by itself").requirements();
            assertEquals(1, required.refinements().size(),
                    "the fork states one thing about one position: " + required);
            assertEquals("p.kind", required.refinements().keySet().iterator().next().toString());
        }
    }

    /**
     * The narrowed positions on the way to every comparison of {@code behavior}, sorted.
     *
     * <p>Named rather than in the order they were filed. Which comparison a walk reaches first is
     * the walk's, and holding this against it would be holding it against the order a map came back
     * in.
     */
    private static List<String> narrowingsIn(String behavior) {
        List<String> out = new ArrayList<>();
        for (OnTheWay each : wayOf(behavior)) {
            if (each instanceof OnTheWay.Narrowed narrowed) {
                out.add(narrowed.position().toString());
            }
        }
        return out.stream().sorted().toList();
    }

    /** Everything on the way to any comparison of {@code behavior}, the comparisons in the order the
     *  walk filed them. */
    private static List<OnTheWay> wayOf(String behavior) {
        List<OnTheWay> out = new ArrayList<>();
        ways(behavior).forEach(out::addAll);
        return out;
    }

    private static List<List<OnTheWay>> ways(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        Core body = checked.behaviorBodies().get(behavior);
        assertNotNull(body, () -> "the model under test writes " + behavior);
        CoverageSites.Plan plan = checked.plan();
        Map<String, souther.compiler.inputs.InputDomain> inputs =
                compilation.db().ask(new Adequacy.Inputs(module)).value();
        GuardThresholds.Guards guards =
                GuardThresholds.of(behavior, body, plan, inputs.get(behavior), rules);
        return List.copyOf(guards.reaching().byComparison().values());
    }
}
