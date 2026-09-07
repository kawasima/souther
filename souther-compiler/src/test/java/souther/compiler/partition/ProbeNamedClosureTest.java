package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.AnalysisBody;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.StatedContract;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.regex.PatternPlan;
import souther.compiler.values.Allowance;

/** Scratch: a closure written as a name a `let` bound, beside the same one written inline. */
class ProbeNamedClosureTest {

    private static void probe(String name, String declaration) {
        String source = """
                module probe.named

                data Person = { age: Int }
                data Low
                data High
                """ + "\n" + declaration + "\n";
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        if (compilation.modules().isEmpty()) {
            System.out.println("PROBE " + name + " DID NOT COMPILE");
            return;
        }
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        AnalysisBody states = checked.analysisBodies().get("pick");
        InputDomain inputs =
                compilation.db().ask(new Adequacy.Inputs(module)).value().get("pick");
        StatedContract stated =
                compilation.db().ask(new Bodies.StatedContracts(module)).value().get("pick");
        GuardThresholds.Guards guards = GuardThresholds.of("pick", states,
                checked.behaviorBodies().get("pick"), checked.plan(), inputs, rules);
        var sets = BehaviorSetStatements.of("pick", states, stated, inputs.reading(rules),
                inputs.parameterReads(), checked.elementBindings().get("pick"),
                Allowance.of(new PatternPlan.Budget(1000, 1000)), guards.forks());
        System.out.println("PROBE " + name + " comparisons=" + guards.thresholds().size()
                + " forks=" + sets.forks().size());
    }

    @Test
    void probeIt() {
        probe("inline any", """
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = if List.any(x -> x > 0, xs) then High else Low""");
        probe("named any", """
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = {
                    let positive = (x) -> x > 0
                    if List.any(positive, xs) then High else Low
                }""");
        probe("inline filter", """
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) =
                    if List.isEmpty(List.filter(x -> x > 0, xs)) then High else Low""");
        probe("named filter", """
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = {
                    let positive = (x) -> x > 0
                    if List.isEmpty(List.filter(positive, xs)) then High else Low
                }""");
    }
}
