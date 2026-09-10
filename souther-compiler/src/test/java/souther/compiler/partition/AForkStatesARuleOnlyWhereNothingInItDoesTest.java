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
 * classes, and one on {@code String.contains("x", s)} is a predicate this compiler reads perfectly
 * well.
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
        Both both = both(declaration);
        return new Owned(both.guards().thresholds().size(), both.forks().size());
    }

    /** Where each fork left stating a rule of its own is filed, in the order the walk met them. */
    private static List<String> filedAt(String declaration) {
        return both(declaration).forks().stream()
                .flatMap(each -> each.filed().keySet().stream())
                .map(String::valueOf)
                .toList();
    }

    /** What one compile of the model says, read once: the two answers are about the same nodes, and
     *  a second compile would hold nodes of its own for the first one's forks to be matched
     *  against. */
    private record Both(GuardThresholds.Guards guards,
                        List<BehaviorSetStatements.ForkOfItsOwn> forks) {}

    private static Both both(String declaration) {
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
                checked.elementBindings().get("pick"),
                Allowance.of(new PatternPlan.Budget(1000, 1000)), guards.forks(),
                new RuleReachNumbering(module, "pick"));
        return new Both(guards, sets.forks());
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

    /**
     * And a position reached along what an operation's answer turns on owns the fork as well.
     *
     * <p>The same edge a comparison inside a closure is found along. The library says
     * {@code List.any} answers what its closure said, and what this closure says is the value at a
     * position — so the fork tests that position and states no rule of its own. Asked at the part
     * itself while a comparison is asked along the edge, {@code List.any(x -> x > 0, xs)} would
     * state nothing and this would state a rule nobody wrote, over one fact about the operation.
     */
    @Test
    void aPositionTheAnswerTurnsOnOwnsTheFork() {
        assertEquals(new Owned(0, 0), read("""
                data Row = { active: Bool }

                behavior pick : (xs: List<Row>) -> Low | High
                let pick (xs) = if List.any(p -> p.active, xs) then High else Low"""));
    }

    /**
     * And a fork the answer turns on owns it too.
     *
     * <p>The inner condition is a rule of the model — nothing in it is a comparison, a predicate or
     * a position, so it is owed a rule of its own — and the outer fork's answer is what that rule
     * decides. Counted apart, one rule the author wrote would be two: the place it is written, and
     * every fork whose answer it settles.
     */
    @Test
    void aForkTheAnswerTurnsOnOwnsIt() {
        assertEquals(new Owned(0, 1), read("""
                data Bag = { tags: List<Int> }

                behavior pick : (xs: List<Bag>) -> Low | High
                let pick (xs) =
                    if List.any(p -> if List.isEmpty(p.tags) then true else false, xs)
                        then High else Low"""));
    }

    /**
     * And a fork that states no rule owns nothing.
     *
     * <p>The negative control for the one above, and the reason the owner is asked of the rules
     * rather than of the source. The inner condition is about nothing of the input, so it states no
     * rule — that is what a fork over {@code List.isEmpty([1, 2, 3])} already comes to. The outer
     * fork turns on it all the same, and what it turns on being a fork the author wrote is not what
     * makes it owned: read that way, the outer fork would be answered for by a rule that does not
     * exist and the question it leaves would go with it.
     */
    @Test
    void aForkThatStatesNoRuleOwnsNothing() {
        assertEquals(new Owned(0, 1), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) =
                    if List.any(n -> if List.isEmpty([1, 2, 3]) then false else true, xs)
                        then High else Low"""));
    }

    /**
     * And a fork one rule owns a part of still states the part it does not.
     *
     * <p>The same thing already held of a comparison owning one part, said of the owner a fork rule
     * is. The first part is what the closure decided, which is the inner fork's rule; the second is
     * something nobody read. Subtracted fork by fork rather than part by part, the second part's
     * question goes with the first part's owner, and a model half of which nobody took in is
     * reported as read.
     */
    @Test
    void aForkOwnedByAnotherForkForOnePartStillStatesTheOther() {
        assertEquals(List.of("xs[*].tags", "ys"), filedAt("""
                data Bag = { tags: List<Int> }

                behavior pick : (xs: List<Bag>, ys: List<Int>) -> Low | High
                let pick (xs, ys) =
                    if List.any(p -> if List.isEmpty(p.tags) then true else false, xs)
                            && List.isEmpty(ys)
                        then High else Low"""));
    }

    /**
     * And an owner of one part of what a closure states leaves the other part.
     *
     * <p>The same partial ownership a condition's own parts are cut along, one step past the
     * operation. The library says {@code List.any} answers what its closure said, and the closure
     * says two things: a comparison, and something nothing here reads. Asked as "is anything in
     * there owned", the second went with the first and the fork came out fully read — a model half
     * of which nobody took in reported as one this compiler followed to the end.
     */
    @Test
    void anOwnerOfOnePartPastTheOperationLeavesTheOther() {
        assertEquals(List.of("xs[*].tags"), filedAt("""
                data Row = { age: Int, tags: List<Int> }

                behavior pick : (xs: List<Row>) -> Low | High
                let pick (xs) =
                    if List.any(p -> p.age > 18 && List.isEmpty(p.tags), xs)
                        then High else Low"""));
    }

    /**
     * And a fork rule owning one part past the operation leaves the other part too.
     *
     * <p>The last of the four owners asked at the grain the other three are. The closure states two
     * things: an inner fork, which is a rule of its own, and something nothing here reads. Asked as
     * "is any part of what the outer fork tests owned by another fork's rule", the second went with
     * the first — the same partial ownership, lost at the one authority that was still answered
     * whole.
     */
    @Test
    void aForkRuleOwningOnePartPastTheOperationLeavesTheOther() {
        assertEquals(List.of("xs[*].tags", "xs[*].other"), filedAt("""
                data Row = { tags: List<Int>, other: List<Int> }

                behavior pick : (xs: List<Row>) -> Low | High
                let pick (xs) =
                    if List.any(
                            p -> (if List.isEmpty(p.tags) then true else false)
                                    && List.isEmpty(p.other),
                            xs)
                        then High else Low"""));
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
     * And a name is resolved by the owner, not by the cut.
     *
     * <p>Cutting a condition stops at a name — following it there would make the parts depend on
     * how many names an author put between the fork and what it tests — and the owner holding the
     * part asks the reading what the name stands for. So this fork is answered about
     * {@code List.isEmpty(xs)} rather than about {@code ok}, and states a rule of its own for the
     * same reason the one above it does.
     */
    @Test
    void aNameStandingForAConditionIsResolvedByWhoeverOwnsThePart() {
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

    /**
     * A rule reaches a fork along what the library says the answer turns on, and no further.
     *
     * <p><b>The pair this rests on.</b> {@code filter} and {@code map} are the same shape, and a
     * reading that looked through the tree for a rule would credit both alike. Filtering answers
     * fewer for exactly the reason the closure says; a mapping answers one per element whatever
     * the closure said, so what a rule inside it decides is what the answers are and never how many
     * — and a fork on whether the mapping is empty turns on neither.
     *
     * <p>Which is why the size the two declare is not what is asked. Both answer at most as many as
     * they walked, and so do a take and a distinct whose closures decide nothing; the fact read
     * here is the one that says the closure is the reason
     * ({@link souther.compiler.semantics.OperationFact.TurnsOnWhetherAnArgumentHolds}).
     */
    @Test
    void aRuleReachesAForkAlongWhatTheAnswerTurnsOn() {
        assertEquals(new Owned(1, 0), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = if List.isEmpty(List.filter(x -> x > 0, xs)) then High else Low"""),
                "filtering answers fewer for the reason the closure gives");
        assertEquals(new Owned(1, 1), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = if List.isEmpty(List.map(x -> x > 0, xs)) then High else Low"""),
                "and a mapping answers as many either way, so the fork is owed a rule of its own");
    }

    /** And an operation whose whole answer is what the closure said of the elements. */
    @Test
    void aForkOnWhatAQuantifierAnsweredIsTheRuleInsideIt() {
        assertEquals(new Owned(1, 0), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = if List.any(x -> x > 0, xs) then High else Low"""));
        assertEquals(new Owned(1, 0), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = if List.all(x -> x > 0, xs) then High else Low"""));
    }

    /**
     * And a closure that changes how many are answered but never whether any are.
     *
     * <p>The third corner of the triangle the pair above makes. {@code List.distinctBy} answers
     * fewer where its key sends two elements to one, so the key does decide the count — and it
     * never decides emptiness, because the first element of what it walked is always kept. A fork
     * on whether the answer is empty turns on neither the key nor what it said.
     *
     * <p>Which is why the aspect a fork on {@code List.isEmpty} reads is whether the answer holds
     * anything and not how many it holds. Read as the count, the key would answer for this fork on
     * a rule that says nothing about it.
     */
    @Test
    void aClosureThatChangesTheCountAndNotTheEmptinessAnswersForNoFork() {
        assertEquals(new Owned(1, 1), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) =
                    if List.isEmpty(List.distinctBy(x -> x > 0, xs)) then High else Low"""));
    }

    /**
     * And a fork nothing reads states no rule a row could be held to.
     *
     * <p>What is computed where no run reads it divides nothing: a comparison there draws no line
     * for that reason ({@link NotABoundary#NOTHING_READS_IT}), and a fork is a rule for having been
     * written rather than for what came of it — so left in, it would hold a measure open over a
     * question no row can answer, which is the case that word exists to exclude.
     */
    @Test
    void aForkNothingReadsStatesNoRule() {
        assertEquals(new Owned(0, 0), read("""
                behavior pick : (xs: List<Int>) -> Low | High
                let pick (xs) = {
                    let unread = if List.isEmpty(xs) then 1 else 2
                    High
                }"""));
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
                Allowance.of(new PatternPlan.Budget(1000, 1000)), guards.forks(),
                new RuleReachNumbering(module, "pick")).forks();

        assertEquals(List.of("probe.forks/pick"), forks.stream()
                .map(each -> each.rule().writtenIn().module() + "/"
                        + each.rule().writtenIn().definition())
                .toList());
    }
}
