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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A closure handed to one of the language's operations is the same rule however it is written down.
 *
 * <p>An author may write the closure at the call, bind it to a name first, or bind a second name to
 * the first. The model states one rule in every case: a comparison inside the closure is about the
 * elements the operation walks, and which of the three spellings the author reached for is not
 * something a model says.
 *
 * <p><b>Two readings had to agree about it and read it two ways.</b> The reading of which construct
 * of the model a construct is asked what bound the applied callable, which for a lambda written at
 * the call is the expansion taking it and for one the author bound is their own body — so the
 * second looked like the operation's own implementation, the operation's envelope never closed, and
 * the tree that runs held no such construct. The reading of what a walk hands its elements asked
 * for a block and found a name. Between them, a model that bound its closure to a name stopped the
 * compile.
 *
 * <p>So the pair is held here, spelling against spelling, at the two things a reader is owed: the
 * line the rule draws, and whether the fork testing the call is owed a rule of its own.
 */
class AClosureIsTheSameRuleHoweverItIsWrittenDownTest {

    /** What the readers of {@code pick} come to: the lines its rules draw, and the forks left
     *  stating a rule nothing else answers for. */
    private record Read(int lines, int forks, int noLine) {}

    private static Read read(String declaration) {
        Compilation compilation = Compilation.ofSource("""
                module probe.spelling

                data Low
                data High
                """ + "\n" + declaration + "\n", "Main");
        compilation.answerEverything();
        assertEquals(1, compilation.modules().size(), "the model under test compiles");
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        AnalysisBody states = checked.analysisBodies().get("pick");
        InputDomain inputs =
                compilation.db().ask(new Adequacy.Inputs(module)).value().get("pick");
        StatedContract stated =
                compilation.db().ask(new Bodies.StatedContracts(module)).value().get("pick");

        GuardThresholds.Guards guards = GuardThresholds.of("pick", states,
                checked.behaviorBodies().get("pick"), checked.plan(), inputs, rules);
        BehaviorSetStatements.Read sets = BehaviorSetStatements.of("pick", states, stated,
                inputs.reading(rules), inputs.parameterReads(),
                checked.elementBindings().get("pick"),
                Allowance.of(new PatternPlan.Budget(1000, 1000)), guards.forks());
        return new Read(guards.thresholds().size(), sets.forks().size(),
                guards.noLine().reported().size() + guards.noLine().unclassified().size());
    }

    /** The closure written where it is handed over. */
    @Test
    void aClosureWrittenAtTheCall() {
        assertEquals(new Read(1, 0, 0), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = if List.any(x -> x > 0, xs) then High else Low"""));
    }

    /** And the same closure bound to a name first, which is the same rule. */
    @Test
    void theSameClosureBoundToANameFirst() {
        assertEquals(new Read(1, 0, 0), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = {
                    let positive = (x) -> x > 0
                    if List.any(positive, xs) then High else Low
                }"""));
    }

    /** And a second name for the first, which is still the same closure. */
    @Test
    void andASecondNameForIt() {
        assertEquals(new Read(1, 0, 0), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = {
                    let positive = (x) -> x > 0
                    let same = positive
                    if List.any(same, xs) then High else Low
                }"""));
    }

    /**
     * And where the rule is read through what an operation answers rather than out of it.
     *
     * <p>The other half of what a closure being one rule buys: the line here is drawn by reading
     * what {@code filter} answers of what its closure said, so a spelling that lost the closure
     * would lose the line rather than the fork.
     */
    @Test
    void aClosureReadThroughWhatTheOperationAnswers() {
        assertEquals(new Read(1, 0, 0), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) =
                    if List.isEmpty(List.filter(x -> x > 0, xs)) then High else Low"""));
        assertEquals(new Read(1, 0, 0), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = {
                    let positive = (x) -> x > 0
                    if List.isEmpty(List.filter(positive, xs)) then High else Low
                }"""));
    }

    /**
     * And where the operation handed the closure hands it on to another.
     *
     * <p>{@code Set.filter} is written as {@code List.filter} over the set's elements, so the
     * closure the author supplied crosses two operations before anything applies it. The rule
     * inside it is the author's at both, and what says so is which copy was handed the closure —
     * the outer one — rather than which copy stands where it is finally applied.
     */
    @Test
    void aClosureOneOperationHandsToAnother() {
        assertEquals(new Read(1, 0, 0), read("""
                behavior pick : (xs: Set<Int>) -> Low | High
                let pick (xs) =
                    if Set.isEmpty(Set.filter(x -> x > 0, xs)) then High else Low"""));
        assertEquals(new Read(1, 0, 0), read("""
                behavior pick : (xs: Set<Int>) -> Low | High
                let pick (xs) = {
                    let positive = (x) -> x > 0
                    if Set.isEmpty(Set.filter(positive, xs)) then High else Low
                }"""));
    }

    /**
     * And where the model's own helper is what hands the closure to the operation.
     *
     * <p>The closure is written in {@code pick} and run inside {@code List.any} inside
     * {@code through}, so two copies stand between where it is written and where it runs and
     * neither is a copy of it. Left only as far as the operation, the rule would come out standing
     * in a copy of {@code through} while the reading that keeps operations standing has it where
     * the author wrote it, and the two would state two rules for one closure.
     *
     * <p>The second spelling writes the closure's type out because a name bound to a bare block has
     * none a declared parameter can be checked against — which is the language and not this.
     */
    @Test
    void aClosureTheModelsOwnHelperHandsToTheOperation() {
        assertEquals(new Read(1, 0, 0), read("""
                let through (p: (Int) -> Bool, xs: List<Int>): Bool = List.any(p, xs)

                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) =
                    if through(x -> x > 0, xs) then High else Low"""));
        assertEquals(new Read(1, 0, 0), read("""
                let through (p: (Int) -> Bool, xs: List<Int>): Bool = List.any(p, xs)

                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = {
                    let positive: (Int) -> Bool = (x) -> x > 0
                    if through(positive, xs) then High else Low
                }"""));
    }

    /**
     * And where what took the closure binds a name of its own to it before handing it on.
     *
     * <p>A second name for a callable is the same callable, so where it came from is where the
     * first came from. Lost at the rebinding, the code being written would be handing over
     * something of its own for the first time — and the copies between the closure and where it
     * runs would be counted as copies of the closure.
     */
    @Test
    void aClosureRenamedByWhatTookIt() {
        assertEquals(new Read(1, 0, 0), read("""
                let through (p: (Int) -> Bool, xs: List<Int>): Bool = {
                    let same = p
                    List.any(same, xs)
                }

                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) =
                    if through(x -> x > 0, xs) then High else Low"""));
    }

    /**
     * And where one closure is written inside another.
     *
     * <p>The inner call is expanded before the block holding it is handed anywhere, and the copies
     * it made are built again where that block is applied. So a copy said as the chain it stood in
     * would name the chain it was first built under, and the inner closure's rule would come out as
     * a construct the model does not state — which stops the compile rather than losing a line.
     */
    @Test
    void aClosureWrittenInsideAnother() {
        assertEquals(new Read(1, 0, 0), read("""
                behavior pick : (rows: List<List<Int>>) -> Low | High
                let pick (rows) =
                    if List.any(r -> List.any(n -> n >= 5, r), rows) then High else Low"""));
    }

    /**
     * And one closure two calls share names the elements of neither.
     *
     * <p>One block handed to two operations has one parameter and two containers, so what arrives
     * under that binding is not one sequence's elements. Kept as whichever call was met first, a
     * rule inside the closure would be filed at a sequence it says nothing about, which an author
     * cannot tell from a line their model states — so it is filed at neither.
     *
     * <p><b>And nothing is said about it, which this pins and does not defend.</b> The rule is read
     * and comes to no line, and what a report is owed about a rule that came to none is a finding —
     * which needs a position to be filed at, and the position is exactly what could not be worked
     * out. So the rule leaves the measurement silently. That is what a reading with no element
     * binding has always done here; this makes the single-call spelling stop reaching it, and
     * leaves the shared one where it was.
     */
    @Test
    void aClosureTwoCallsShareNamesTheElementsOfNeither() {
        assertEquals(new Read(0, 0, 0), read("""
                behavior pick : (xs: List<Int>, ys: List<Int>) -> Low | High
                let pick (xs, ys) = {
                    let positive = (x) -> x > 0
                    if List.any(positive, xs) && List.any(positive, ys) then High else Low
                }"""));
    }
}
