package souther.compiler.check;

import souther.compiler.Compiler;
import souther.compiler.DefaultStdlib;
import souther.compiler.core.Core;
import souther.compiler.diag.Severity;
import souther.compiler.diag.SourcePos;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.BinOp;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Which readers of a condition cross a binding, which stop at one, and what makes up for it.
 *
 * <p>A rule an author names is expanded where it stands, so a condition written as a call is a
 * binding holding the argument with the rule written against it. Handed such a node, the readers
 * below stop: they recognise a restatement and a connective and then ask for the comparison, and a
 * binding is none of the three. What is under it is the rule, and each of them states it once the
 * environment has been entered — so what stops is the walk to the comparison and not the language
 * that reads one.
 *
 * <p>They are not handed one on the way to a construction. The walk that threads knowledge enters a
 * binding standing inside a value and goes on over the rebuilt tree ({@link InvariantChecker}), so
 * by the time an arm is opened the condition is the comparison the source would have written. What
 * an author sees of a construction is therefore the same either way, and the difference between the
 * two spellings is held here rather than there.
 *
 * <p>One report does differ, and it is not the construction: a branch no value reaches is named
 * where the condition was written out and not where it was named.
 */
class WhoCrossesABindingInAConditionAndWhoDoesNotTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "f");
    private static final BindingId VALUE = new BindingId(OWNER, 0);

    private static Terms terms() {
        return RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()),
                ReadAs.THE_COMPILATION_DOES);
    }

    private static Denotations rootAt() {
        return Denotations.none().location(VALUE, AsPlaces.of(VALUE), AsPlaces.term(VALUE));
    }

    /** `n`, the value both spellings of the rule are about. */
    private static Core.Read subject() {
        return new Core.Read("n", VALUE, Type.INT, POS);
    }

    /** `<subject> >= 0`, the rule itself. */
    private static Core.Binary rule(Core subject) {
        return new Core.Binary(BinOp.GE, subject, new Core.Int(0, Type.INT, POS),
                SourceConstructOrigin.unwritten(), Type.BOOL, POS);
    }

    /** `let $n = n in $n >= 0`, which is what naming the rule expands to. */
    private static Core.LetIn named() {
        BindingId bound = new BindingId(OWNER, 1);
        Core.Binary body = rule(new Core.Read("$n", bound, Type.INT, POS));
        return new Core.LetIn(new Core.Binder("$n", bound), subject(), body, body.type(), POS);
    }

    private static List<NumericConstraint> stated(Terms terms, Core cond, Denotations at) {
        List<NumericConstraint> out = new ArrayList<>();
        Conditions.stating(terms, cond, at, true, out);
        return out;
    }

    /** What a condition states on its own: the rule written out, and nothing where it was named. */
    @Test
    void theReadingOfWhatAConditionStatesStopsAtABinding() {
        Terms terms = terms();

        assertEquals(List.of(1, 0), List.of(
                        stated(terms, rule(subject()), rootAt()).size(),
                        stated(terms, named(), rootAt()).size()),
                "how many relations each spelling states");
    }

    /** And so does the reader of comparisons under it, which is what that walk asks. */
    @Test
    void theReaderOfComparisonsStopsAtOneToo() {
        Terms terms = terms();

        assertEquals(List.of(1, 0), List.of(
                        Conditions.comparisonsStatedBy(terms, rule(subject()), rootAt())
                                .inReadingOrder().size(),
                        Conditions.comparisonsStatedBy(terms, named(), rootAt())
                                .inReadingOrder().size()),
                "how many comparisons each spelling states");
    }

    /** And so does the walk that threads knowledge along a path. */
    @Test
    void theWalkThatThreadsKnowledgeStopsAtOneAsWell() {
        Terms terms = terms();
        Predicates predicates = new Predicates(terms);
        Denotations at = rootAt();
        LinearForm<FactSubject> about = terms.affineOf(subject(), at);
        assertNotNull(about, "the value the rule is about is a form this reads");

        assertEquals(List.of(true, false), List.of(
                        predicates.assumeCond(rule(subject()), Known.top(), at, true)
                                .known().numbers().entails(about, Rel.GE),
                        predicates.assumeCond(named(), Known.top(), at, true)
                                .known().numbers().entails(about, Rel.GE)),
                "whether what is known entails the rule, written out and named");
    }

    /**
     * What stops is the walk to the comparison and not the language that reads one.
     *
     * <p>The body states the rule where the environment has been entered, and the binding itself
     * states nothing even there: an entered environment is not what such a reader is missing.
     */
    @Test
    void whatIsUnderTheBindingStatesTheRuleAndTheBindingItselfStatesNothing() {
        Terms terms = terms();
        Core.LetIn named = named();
        Denotations inside = terms.inside(named, rootAt());

        assertEquals(List.of(1, 0), List.of(
                        stated(terms, named.body(), inside).size(),
                        stated(terms, named, inside).size()),
                "what is under the binding, and the binding handed whole to the same reader");
    }

    private static final String YEN = """
            module demo
            data Yen = Int
                invariant nonNegative = value >= 0
            """;

    /** Which warnings, and not how many: a count answers about two reports alike where the two say
     *  different things about different values. */
    private static List<String> reported(String source) {
        return Compiler.compileWithWarnings(source).warnings().stream()
                .filter(d -> d.severity() == Severity.WARNING)
                .<String>map(d -> d.code() + " " + d.titleKey()).toList();
    }

    /** The condition the analysis is given, where the rule was named: the binding the expansion
     *  wrote, which is the shape the readers above stop at. */
    @Test
    void theConditionTheAnalysisReadsIsTheBindingTheExpansionWrote() {
        Compilation compilation = Compilation.ofSource(YEN + """
                let nonNeg (n: Int) = n >= 0
                behavior f : (n: Int) -> Yen constructs Yen
                let f (n) = Yen(if nonNeg(n) then n else 0)
                """, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        AnalysisBody body = checked.analysisBodies().get("f");
        assertNotNull(body, "the behavior under test has a body the analysis reads");

        List<String> conditions = new ArrayList<>();
        forks(body.core(), conditions);
        assertEquals(List.of("LetIn"), conditions,
                "the condition of the fork, as the class of the node the reading is handed");
    }

    private static void forks(Core e, List<String> out) {
        if (e instanceof Core.If iff) {
            out.add(iff.cond().getClass().getSimpleName());
        }
        Core.forEachChild(e, child -> forks(child, out));
    }

    /** And what a construction the arms are given comes to, which is the same either way: the walk
     *  enters the binding and goes on over the rebuilt tree before any arm is opened. */
    @Test
    void aConstructionGivenTheArmsIsAnsweredWhicheverWayTheConditionWasWritten() {
        assertEquals(List.of(List.of(), List.of()), List.of(
                        reported(YEN + """
                                behavior f : (n: Int) -> Yen constructs Yen
                                let f (n) = Yen(if n >= 0 then n else 0)
                                """),
                        reported(YEN + """
                                let nonNeg (n: Int) = n >= 0
                                behavior f : (n: Int) -> Yen constructs Yen
                                let f (n) = Yen(if nonNeg(n) then n else 0)
                                """)),
                "the rule written out, and the same rule named");
    }

    /**
     * And a value named from a conditional, which is the other reader of what choosing an arm
     * settles: the arms of a recipe ({@link Derivation.Chosen}) rather than a branch of the walk.
     *
     * <p>The third reader of the same answer takes its arms from how a call's arguments stand
     * ({@link Choice.Decides.ByArgumentRelations}), where there is no condition node to hold a
     * binding — held as what a library definition's cases become in
     * {@link EveryCaseALibraryDefinitionIsWrittenInBecomesAnArmTest}.
     */
    @Test
    void aValueNamedFromAConditionalIsBoundedTheSameWayEitherSpelling() {
        assertEquals(List.of(List.of(), List.of()), List.of(
                        reported(YEN + """
                                behavior f : (n: Int) -> Yen constructs Yen
                                let f (n) = {
                                    let v = if n >= 0 then n else 0
                                    Yen(v)
                                }
                                """),
                        reported(YEN + """
                                let nonNeg (n: Int) = n >= 0
                                behavior f : (n: Int) -> Yen constructs Yen
                                let f (n) = {
                                    let v = if nonNeg(n) then n else 0
                                    Yen(v)
                                }
                                """)),
                "the rule written out, and the same rule named, in a value the body names");
    }

    /** And what answers for the arm is the rule about the value the arm is, which is what says the
     *  condition was read rather than the construction let through. */
    @Test
    void whatAnswersForTheArmIsTheRuleAboutTheValueTheArmIs() {
        assertEquals(List.of(List.of(), List.of("E2011 check.invariant.title")), List.of(
                        reported(YEN + """
                                let nonNeg (n: Int) = n >= 0
                                behavior f : (n: Int, k: Int) -> Yen constructs Yen
                                let f (n, k) = Yen(if nonNeg(n) then n else 0)
                                """),
                        reported(YEN + """
                                let nonNeg (n: Int) = n >= 0
                                behavior f : (n: Int, k: Int) -> Yen constructs Yen
                                let f (n, k) = Yen(if nonNeg(k) then n else 0)
                                """)),
                "the rule about the arm, and the same rule about another value");
    }

    /**
     * The one report that does differ, and it is not the construction.
     *
     * <p>A condition stating nothing about what the arm is leaves the construction owed both ways.
     * The branch no value reaches is named where the rule was written out and not where it was
     * named, which is a reader the rebuilt tree does not reach.
     */
    @Test
    void aBranchNoValueReachesIsNamedOnlyWhereTheConditionWasWrittenOut() {
        assertEquals(List.of(
                        List.of("E2011 check.invariant.title", "E1327 check.dead.branch.title"),
                        List.of("E2011 check.invariant.title")), List.of(
                        reported(YEN + """
                                behavior f : (n: Int) -> Yen constructs Yen
                                let f (n) = Yen(if n == n then n else 0)
                                """),
                        reported(YEN + """
                                let anything (n: Int) = n == n
                                behavior f : (n: Int) -> Yen constructs Yen
                                let f (n) = Yen(if anything(n) then n else 0)
                                """)),
                "a vacuous condition written out, and the same one named");
    }
}
