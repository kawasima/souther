package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.AnalysisBody;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.StatedContract;
import souther.compiler.core.Core;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.regex.PatternPlan;
import souther.compiler.values.Allowance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A fork is the rule only where nothing in what it tests is one.
 *
 * <p>A condition raises a question about the input, and which rule of the model that question is
 * filed under is whichever reader owns what the condition states. Three of them do today: a
 * comparison of the values, the position the condition is, and a predicate over the strings there.
 * The fork is what is left when none of them claims it — the answer of last resort, and not a fourth
 * kind of condition recognised on its own.
 *
 * <p><b>Which is why the negative cases are the point of this.</b> Read as "no comparison, so a
 * fork", the rule would file a second question at every condition an owner already answers for: a
 * fork on a {@code Bool} of the input tests the values standing there and its arms are their two
 * classes, and one on {@code String.isEmpty(s)} is a predicate this compiler reads perfectly well.
 * Both would come back as rules nothing interpreted, and a model stating them completely would be
 * reported as one this compiler could not read.
 *
 * <p>And it is asked of the whole condition. A predicate under a {@code !} or beside a {@code &&}
 * is what the fork tests as much as one written alone, so a reading that looked only at the
 * condition's outermost shape would call such a fork opaque and file the question twice.
 */
class AForkStatesARuleOnlyWhereNothingInItDoesTest {

    private static final String PRELUDE = """
            module probe.forks

            data Person = { age: Int, name: String }
            data Low
            data High
            """;

    /** What the readers of {@code pick}'s condition come to: how many comparisons were read, and
     *  how many forks were left stating a rule of their own. */
    private record Owned(int comparisons, int forks) {}

    private static Owned read(String declaration) {
        Compilation compilation = Compilation.ofSource(PRELUDE + "\n" + declaration + "\n", "Main");
        compilation.answerEverything();
        assertEquals(1, compilation.modules().size(), "the model under test compiles");
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        AnalysisBody states = checked.analysisBodies().get("pick");
        Core body = checked.behaviorBodies().get("pick");
        CoverageSites.Plan plan = checked.plan();
        InputDomain inputs =
                compilation.db().ask(new Adequacy.Inputs(module)).value().get("pick");
        StatedContract stated =
                compilation.db().ask(new Bodies.StatedContracts(module)).value().get("pick");
        assertNotNull(states, "and its body is read");

        GuardThresholds.Guards guards = GuardThresholds.of("pick", states, body, plan, inputs,
                rules);
        BehaviorSetStatements.Read sets = BehaviorSetStatements.of("pick", states, stated,
                inputs.reading(rules), inputs.parameterReads(),
                checked.elementBindings().get("pick"), Allowance.of(new PatternPlan.Budget(1000, 1000)), guards.forks());
        return new Owned(guards.thresholds().size(), sets.forks().size());
    }

    /** A comparison owns the question, and the fork around it states nothing of its own. */
    @Test
    void aForkOnAComparisonIsTheComparisonsRule() {
        assertEquals(new Owned(1, 0), read("""
                behavior pick : (n: Int) -> Low | High
                let pick (n) = if n > 0 then High else Low"""));
    }

    /**
     * A fork on a position of the input is that position's, and the arms are its classes.
     *
     * <p>The minimal counterexample to reading a fork as opaque because no comparison came out of
     * it. Nothing here went unread: a {@code Bool} holds two values, the fork tests which of them
     * stands there, and its two arms are the two classes. Filed as a rule nothing interpreted, a
     * model that says everything there is to say about its input would be reported as one this
     * compiler stopped on.
     */
    @Test
    void aForkOnAPositionIsThatPositionsRule() {
        assertEquals(new Owned(0, 0), read("""
                behavior pick : (b: Bool) -> Low | High
                let pick (b) = if b then High else Low"""));
    }

    /** And a fork on a predicate is the predicate's, which is a rule this compiler reads. */
    @Test
    void aForkOnAPredicateIsThePredicatesRule() {
        assertEquals(new Owned(0, 0), read("""
                behavior pick : (p: Person) -> Low | High
                let pick (p) = if String.contains("x", p.name) then High else Low"""));
    }

    /**
     * And the whole condition is asked, not its outermost shape.
     *
     * <p>The predicate is under a negation and beside a comparison, so a reading that looked at
     * what the condition is rather than at what is in it would find a conjunction, no rule of its
     * own, and file the fork — beside the two rules the condition already states.
     */
    @Test
    void aPredicateInsideAConditionIsStillWhatTheForkTests() {
        assertEquals(new Owned(1, 0), read("""
                behavior pick : (p: Person) -> Low | High
                let pick (p) =
                    if String.contains("x", p.name) && p.age > 18 then High else Low"""));
    }

    /**
     * And a fork on what one of the language's own operations answers is the fork's own rule.
     *
     * <p>No comparison, because the operation stands rather than having been expanded into the
     * arithmetic it does; no position, because what it answers is made from one and is not one; no
     * predicate, because this is not a rule about the strings anywhere. What the author wrote is a
     * fork, and the question it raises about {@code xs} is the fork's.
     */
    @Test
    void aForkOnWhatAnOperationAnswersIsTheForksOwn() {
        assertEquals(new Owned(0, 1), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = if List.isEmpty(xs) then High else Low"""));
    }

    /**
     * A fork owning one part of what it tests and not the other states a rule for the part left.
     *
     * <p>The control the whole shape rests on. The comparison is read and the operation is not, so
     * one part of this condition has an owner and one has none — and what the fork is owed a
     * question about is the part with none. Answered for the fork rather than for its parts, the
     * comparison having been read would say the condition was taken in, and a model half of whose
     * fork nobody could read would come back as one this compiler read from end to end.
     */
    @Test
    void aForkOwningOnePartOfItsConditionStillStatesTheOtherOne() {
        assertEquals(new Owned(1, 1), read("""
                behavior pick : (n: Int, xs: List<Int>) -> Low | High
                let pick (n, xs) = if n > 0 && List.isEmpty(xs) then High else Low"""));
    }

    /**
     * And a rule written inside a part is that part's, closure and all.
     *
     * <p>{@code x > 0} is a rule about the elements {@code List.filter} walks, and what the fork
     * tests is what the operations answer of those elements — so the line that rule draws is what
     * answers for this fork. Read as belonging only to the operation it is written in, the fork
     * would raise a question over a model whose rule about {@code xs} was read and drawn.
     *
     * <p>Which is a different thing from the conjunction below it. A rule in one part says nothing
     * about another part, and that is why the parts are asked one at a time; asked once of the
     * whole condition, a rule in either would answer for both.
     */
    @Test
    void aRuleWrittenInsideAPartIsThatPartsRule() {
        assertEquals(new Owned(1, 0), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = if List.isEmpty(List.filter(x -> x > 0, xs)) then High else Low"""));
    }

    /**
     * And a name standing for a condition is a part nothing owns, which is the honest answer.
     *
     * <p>What the name holds is not read: following it would make the parts of a condition depend
     * on how many names an author put between the fork and what it tests. So this compiler did not
     * work out what {@code ok} stands for, and the question stays open rather than being closed on
     * a guess about it.
     */
    @Test
    void aNameStandingForAConditionIsAPartNothingOwns() {
        assertEquals(new Owned(0, 1), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = {
                    let ok = List.isEmpty(xs)
                    if ok then High else Low
                }"""));
    }

    /**
     * And a fork about nothing of the input states no rule about the input.
     *
     * <p>Not a rule this compiler failed to read: {@code List.isEmpty([1, 2, 3])} is something the
     * input has no part in, so there is no position for a question about it to be about. The same
     * threshold a comparison is held to and decided by the same reading
     * ({@link ComparisonAssessment.NoInput}), so a fork and a comparison over one shape do not
     * disagree about whether the model states anything.
     *
     * <p>Which is not a question dropped for want of somewhere to put it. What decides it is the
     * subject — whether the rule is about the input at all — and a part that does name a position
     * is filed there however little else was worked out about it, which the fork above shows.
     */
    @Test
    void aForkAboutNothingOfTheInputStatesNoRuleAboutIt() {
        assertEquals(new Owned(0, 0), read("""
                behavior pick : (n: Int) -> Low | High
                let pick (n) = if List.isEmpty([1, 2, 3]) then High else Low"""));
    }

    /** Nothing this compiler composed is one of these: the forks are the ones an author wrote. */
    @Test
    void everyForkFiledIsOneAnAuthorWrote() {
        Compilation compilation = Compilation.ofSource(PRELUDE + """

                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = if List.isEmpty(xs) then High else Low
                """, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        InputDomain inputs =
                compilation.db().ask(new Adequacy.Inputs(module)).value().get("pick");
        StatedContract stated =
                compilation.db().ask(new Bodies.StatedContracts(module)).value().get("pick");
        GuardThresholds.Guards guards = GuardThresholds.of("pick",
                checked.analysisBodies().get("pick"), checked.behaviorBodies().get("pick"),
                checked.plan(), inputs, rules);
        List<BehaviorSetStatements.ForkOfItsOwn> forks = BehaviorSetStatements.of("pick",
                checked.analysisBodies().get("pick"), stated, inputs.reading(rules),
                inputs.parameterReads(), checked.elementBindings().get("pick"),
                Allowance.of(new PatternPlan.Budget(1000, 1000)), guards.forks()).forks();

        assertEquals(List.of("probe.forks/pick"), forks.stream()
                .map(each -> each.rule().writtenIn().module() + "/"
                        + each.rule().writtenIn().definition())
                .toList());
    }
}
