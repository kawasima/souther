package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.AnalysisBody;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.RuleRef;
import souther.compiler.check.StatedContract;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.RulesWithNoLine;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.regex.PatternPlan;
import souther.compiler.values.Allowance;

import java.util.ArrayList;
import java.util.List;

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

    /** Where each rule that came to no line was filed, and what stopped its reading, in the order
     *  the walk met them. */
    private static List<String> withoutALine(String declaration) {
        RulesWithNoLine noLine = guardsOf(declaration).noLine();
        List<String> found = new ArrayList<>();
        noLine.reported().forEach(each ->
                found.add(each.at() + " " + each.why().getClass().getSimpleName()));
        noLine.unclassified().forEach(each ->
                found.add(each.at() + " " + each.why().getClass().getSimpleName()));
        return found;
    }

    /** The forks left stating a rule of their own, as the rule and where it was filed. */
    private static List<String> forksOfTheirOwn(String declaration) {
        Compilation compilation = compiled(declaration);
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        InputDomain inputs =
                compilation.db().ask(new Adequacy.Inputs(module)).value().get("pick");
        BehaviorSetStatements.Read sets = BehaviorSetStatements.of("pick",
                checked.analysisBodies().get("pick"),
                compilation.db().ask(new Bodies.StatedContracts(module)).value().get("pick"),
                inputs.reading(rules), inputs.parameterReads(),
                checked.elementBindings().get("pick"),
                Allowance.of(new PatternPlan.Budget(1000, 1000)),
                guardsOf(declaration).forks(),
                new RuleReachNumbering(module, "pick"));
        List<String> found = new ArrayList<>();
        sets.forks().forEach(each -> each.filed().forEach((at, why) ->
                found.add(((RuleRef.Written) each.cited().rule()).whatItIs() + " at " + at + " "
                        + why.getClass().getSimpleName())));
        return found;
    }

    private static GuardThresholds.Guards guardsOf(String declaration) {
        Compilation compilation = compiled(declaration);
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        return GuardThresholds.of("pick", checked.analysisBodies().get("pick"),
                checked.behaviorBodies().get("pick"), checked.plan(),
                compilation.db().ask(new Adequacy.Inputs(module)).value().get("pick"),
                RuleReadings.of(compilation, module));
    }

    private static Compilation compiled(String declaration) {
        Compilation compilation = Compilation.ofSource("""
                module probe.spelling

                data Low
                data High
                """ + "\n" + declaration + "\n", "Main");
        compilation.answerEverything();
        assertEquals(1, compilation.modules().size(), "the model under test compiles");
        return compilation;
    }

    private static Read read(String declaration) {
        Compilation compilation = compiled(declaration);
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
                Allowance.of(new PatternPlan.Budget(1000, 1000)), guards.forks(),
                new RuleReachNumbering(module, "pick"));
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
     * And where nothing the language owns is between the closure and where it runs at all.
     *
     * <p>A helper of the model applying what it was handed, and a second helper handing it on to
     * the first. Both readings expand both, so the copies are the same copies in each — which is
     * what says the crossing is about who wrote the block and not about which of the copies is one
     * of the language's.
     */
    @Test
    void aClosureTheModelHandsAboutAndAppliesItself() {
        assertEquals(new Read(1, 0, 0), read("""
                let applies (p: (Int) -> Bool, n: Int): Bool = p(n)

                behavior pick : (n: Int) -> Low | High
                let pick (n) = if applies(x -> x > 0, n) then High else Low"""));
        assertEquals(new Read(1, 0, 0), read("""
                let applies (p: (Int) -> Bool, n: Int): Bool = p(n)
                let hands (p: (Int) -> Bool, n: Int): Bool = applies(p, n)

                behavior pick : (n: Int) -> Low | High
                let pick (n) = if hands(x -> x > 0, n) then High else Low"""));
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
     * And one closure two calls share names the elements of both, and draws a line at neither.
     *
     * <p>One block handed to two operations has one parameter and two containers, so what arrives
     * under that binding is an element of a different sequence on each run. Kept as whichever call
     * was met first, a rule inside the closure would be filed at a sequence it says nothing about,
     * which an author cannot tell from a line their model states.
     *
     * <p><b>So no line, and a question at each sequence it may be about.</b> The rule is one the
     * model states and the reading of it stopped, which is a finding filed where a reader can be
     * sent to look — at both, because both are where it may be. Taken back out instead, the name
     * would have read as one holding nothing of the input, the rule would have left the measurement
     * without a word, and a model an author wrote about their input could come out adequate on the
     * strength of a rule nobody read.
     */
    @Test
    void aClosureTwoCallsShareNamesTheElementsOfBoth() {
        assertEquals(List.of("xs[*] RuleAboutAnElementOfSeveralSequences",
                        "ys[*] RuleAboutAnElementOfSeveralSequences"),
                withoutALine("""
                        behavior pick : (xs: List<Int>, ys: List<Int>) -> Low | High
                        let pick (xs, ys) = {
                            let positive = (x) -> x > 0
                            if List.any(positive, xs) && List.any(positive, ys)
                                then High else Low
                        }"""));
    }

    /**
     * And a fork inside such a closure states a rule of its own, at each of those places.
     *
     * <p>Nothing owns what this fork tests: no comparison, no predicate, and no one position it is
     * the value at — the position is what could not be chosen. So the fork is a rule of the model
     * by having been written, and where a reader is sent for it is every place it may be about.
     * Read only by what names one term, the places come back empty and the fork leaves with them.
     *
     * <p>And it is the only rule here. The fork the body writes tests what the walks answered, and
     * what they answered is what this closure decided — so it states the rule inside the closure
     * rather than one of its own, which is what a fork over a comparison in a closure already does.
     */
    @Test
    void aForkInsideTheSharedClosureIsFiledWhereItMayBeAbout() {
        assertEquals(List.of("fork at xs[*].active RuleAboutAnElementOfSeveralSequences",
                        "fork at ys[*].active RuleAboutAnElementOfSeveralSequences"),
                forksOfTheirOwn("""
                        data Person = { active: Bool }

                        behavior pick : (xs: List<Person>, ys: List<Person>) -> Low | High
                        let pick (xs, ys) = {
                            let chooses = (p) -> if p.active then true else false
                            if List.any(chooses, xs) && List.any(chooses, ys)
                                then High else Low
                        }"""));
    }

    /**
     * And a closure handed to a walk over the input and to a walk over something else keeps what it
     * says about the input.
     *
     * <p>The run through the written list stands at no position, and the run through the parameter
     * stands at one. Taken as all of them or none, the second went with the first: the rule the
     * author wrote about their input came out as one about nothing, and the position it divides was
     * left with no word said about it.
     */
    @Test
    void aClosureHandedToTheInputAndToSomethingElseKeepsWhatItSaysAboutTheInput() {
        assertEquals(List.of("xs[*] RuleAboutAnElementOfSeveralSequences"),
                withoutALine("""
                        behavior pick : (xs: List<Int>) -> Low | High
                        let pick (xs) = {
                            let positive: (Int) -> Bool = (x) -> x > 0
                            if List.any(positive, xs) && List.any(positive, [1, 2])
                                then High else Low
                        }"""));
    }

    /**
     * And the places it may be about are the places the rule is written about.
     *
     * <p>The rule is about a field of the element, so where it may be is a field of each of the
     * sequences and not the sequences themselves. Told at the element instead, a reader would be
     * sent to a position the model says nothing about while the position the rule is written about
     * came out as one no rule reaches — which is the same measurement standing open at the wrong
     * place, and it reads as an answer.
     *
     * <p>Which is why the places are checked here and not how many there are. A count is the same
     * count whichever place each of them is.
     */
    @Test
    void whatTheSharedClosureNamesIsWhereItsRuleIsWritten() {
        assertEquals(List.of("xs[*].age RuleAboutAnElementOfSeveralSequences",
                        "ys[*].age RuleAboutAnElementOfSeveralSequences"),
                withoutALine("""
                        data Person = { age: Int }

                        behavior pick : (xs: List<Person>, ys: List<Person>) -> Low | High
                        let pick (xs, ys) = {
                            let adult = (p) -> p.age > 18
                            if List.any(adult, xs) && List.any(adult, ys) then High else Low
                        }"""));
    }
}
