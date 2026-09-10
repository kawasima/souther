package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.core.Core;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.reach.Reachability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A comparison is read under what holds where it stands, wherever that is.
 *
 * <p>The reading walked a fork's condition and nothing else, so a comparison given a name a line
 * above the fork was numbered and then never asked about — and a question nothing answered reads,
 * everywhere below, exactly like a question answered "nothing is known". What the guards above rule
 * out is the same fact under either spelling.
 */
class AComparisonIsReadWhereverItStandsTest {

    /** The second comparison is written in the condition of the fork that tests it. */
    private static final String IN_THE_CONDITION = """
            module d

            data Amount = Int invariant value >= 0 && value <= 1000000
            data Free
            data Charged = { yen: Int }

            behavior charge : (a: Amount) -> Free | Charged
                constructs Charged

            let charge (a) = {
                guard a.value < 5000 else Free

                if a.value >= 6000 then Free else Charged { yen = 500 }
            }
            """;

    /** The same model, with the second comparison given a name before the fork tests it. */
    private static final String NAMED_BEFORE_THE_FORK = """
            module d

            data Amount = Int invariant value >= 0 && value <= 1000000
            data Free
            data Charged = { yen: Int }

            behavior charge : (a: Amount) -> Free | Charged
                constructs Charged

            let charge (a) = {
                guard a.value < 5000 else Free

                let over = a.value >= 6000

                if over then Free else Charged { yen = 500 }
            }
            """;

    /** Which ways out of a comparison this reading proved nothing arrives at. */
    private static List<Boolean> comparisonsProvenUnreachable(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        Map<String, PathReachability.Answers> answers = compilation.db()
                .ask(new Adequacy.PathReached("d")).value();
        assertNotNull(answers, "the model under test compiles");
        return answers.get("charge").found().entrySet().stream()
                .filter(each -> each.getKey() instanceof ControlPlace.Outcome)
                .filter(each -> each.getValue() instanceof Reachability.Unreachable)
                .map(each -> ((ControlPlace.Outcome) each.getKey()).held())
                .toList();
    }

    /** A chain whose first comparison the guard above rules out, with two more behind it. */
    private static final String NOTHING_REACHES_THE_REST_OF_THE_CHAIN = """
            module d

            data Amount = Int invariant value >= 0 && value <= 1000000
            data Free
            data Charged = { yen: Int }

            behavior charge : (a: Amount, b: Amount) -> Free | Charged
                constructs Charged

            let charge (a, b) = {
                guard a.value < 5000 else Free

                if a.value >= 6000 && (b.value > 1 || b.value < 3)
                    then Free else Charged { yen = 500 }
            }
            """;

    /** Which comparisons of {@code behavior} the plan numbered and this reading never answered. */
    private static List<String> numberedButUnanswered(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        CoverageSites.Plan plan = checked.plan();
        Map<ControlPlace, Reachability> found = compilation.db()
                .ask(new Adequacy.PathReached(module)).value().get(behavior).found();
        List<String> out = new ArrayList<>();
        unanswered(checked.behaviorBodies().get(behavior), plan, found, out);
        return out;
    }

    /** The traversal is this test's and which nodes are comparisons is the plan's. */
    private static void unanswered(Core e, CoverageSites.Plan plan,
                                   Map<ControlPlace, Reachability> found, List<String> out) {
        if (e instanceof Core.Binary node) {
            plan.comparisons().occurrenceAt(node).ifPresent(which -> {
                for (boolean result : new boolean[] {true, false}) {
                    plan.outcomeOf(which, result)
                            .filter(where -> !found.containsKey(where))
                            .ifPresent(where ->
                                    out.add(node.op() + "@" + node.pos() + " " + result));
                }
            });
        }
        Core.forEachChild(e, child -> unanswered(child, plan, found, out));
    }

    /**
     * A comparison the chain above it never gets to is still one this reading answers for.
     *
     * <p>What the reading owes is one answer per comparison the plan numbered, and a walk that
     * stops where nothing arrives discharges that for whatever it happened to reach. Under
     * {@code A && (B || C)} with {@code A} ruled out, the operator is where it stops — and the
     * operator is numbered nowhere, so two comparisons behind it go unanswered. Unanswered reads
     * the same as "nothing is known" everywhere below, which is the state a line drawn on one of
     * them can never be dropped from.
     */
    @Test
    void aComparisonBehindOneNothingReachesIsStillAnsweredFor() {
        assertEquals(List.of(), numberedButUnanswered(NOTHING_REACHES_THE_REST_OF_THE_CHAIN,
                        "charge"),
                "every comparison the plan numbered has an answer");
    }

    /**
     * And so does every comparison of every shape a body can hold one in.
     *
     * <p>What the reading owes is one answer per comparison the plan numbered, and it is discharged
     * by a walk — so the set the walk reaches and the set the plan numbered agree only as far as
     * the shapes anything has looked at. Held as a property over the shapes rather than as a check
     * the reading makes of itself: a check inside a reading that is fail-open by contract reports
     * into a list nothing reads, which is a guard that cannot fail.
     */
    @Test
    void everyComparisonOfEveryShapeIsAnsweredFor() {
        for (String shape : SHAPES) {
            assertEquals(List.of(), numberedButUnanswered(shape, "charge"),
                    () -> "every comparison the plan numbered has an answer:" + shape);
        }
    }

    /** Bodies holding a comparison where each of the walk's cases has to reach it. */
    private static final List<String> SHAPES = List.of(
            NOTHING_REACHES_THE_REST_OF_THE_CHAIN,
            IN_THE_CONDITION,
            NAMED_BEFORE_THE_FORK,
            // Under an arm of a fork, which is entered with what that arm proves.
            body("if a.value < 100 then (if a.value > 1 then Free else Charged { yen = 1 })"
                    + " else Free"),
            // Inside a function value handed to a combinator, passed once per element.
            body("if List.length(List.filter(x -> x > 0, [a.value])) > 0"
                    + " then Free else Charged { yen = 1 }"),
            // Behind a comparison the rules leave nothing on either side of.
            body("if a.value > 2000000 then Free else Charged { yen = 1 }"));

    private static String body(String answer) {
        return "TEMPLATE".replace("TEMPLATE", "module d\n\ndata Amount = Int invariant value >= 0 && value <= 1000000\ndata Free\ndata Charged = { yen: Int }\n\nbehavior charge : (a: Amount) -> Free | Charged\n    constructs Charged\n\nlet charge (a) = ") + answer + "\n";
    }

    /**
     * Nothing at or above 6000 got past a guard that kept everything under 5000, and the comparison
     * saying so is read whether or not a name stands between it and the fork.
     *
     * <p>Held as an equality against the inline spelling. A count of its own would pass for a
     * reading that answered about some other comparison, and what is being said here is that the two
     * models state the same rule.
     */
    @Test
    void aComparisonNamedBeforeTheForkIsProvenWhereTheInlineOneIs() {
        assertEquals(List.of(true), comparisonsProvenUnreachable(IN_THE_CONDITION),
                "the guard above rules out the way the second comparison holds");
        assertEquals(comparisonsProvenUnreachable(IN_THE_CONDITION),
                comparisonsProvenUnreachable(NAMED_BEFORE_THE_FORK),
                "and giving it a name is not a fact about what arrives at it");
    }

}
