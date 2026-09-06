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
 * Which readers of a condition cross a binding, which stop at one, and why the two are not the same
 * kind of reader.
 *
 * <p>A rule an author names is expanded where it stands, so a condition written as a call is a
 * binding holding the argument with the rule written against it. There are two kinds of reader of
 * such a condition, and what separates them is what they are handed.
 *
 * <p><b>A reader of a flat condition does not read a binder node.</b> {@link
 * Conditions#comparisonsStatedBy} is handed one expression and asks which comparisons it states; a
 * binding is not a comparison, and it is not that reader's business to say what one means. Handed
 * what is under the binding it states the rule, and handed the binding itself in that same
 * environment it states nothing — so what stops it is the node and never the environment.
 *
 * <p><b>A reader of a whole clause crosses one.</b> A binding is a shape the clause has
 * ({@link ClauseExpr.Scoped}), the fold finds it and {@link ClauseScope} answers for it, and every
 * reading over that shape meets the leaves under it holding what their names mean. A reading is
 * never handed the binding itself: {@link ClauseExpr.Part} is what it is handed, and a binding is
 * not one.
 *
 * <p><b>What a condition taken in makes known is the second kind.</b> It reads three answers off one
 * shape beside what a clause states and which quantifiers it names, so a rule stated through a
 * helper makes known what the same rule written out makes known — in what is entailed, in whether
 * anything was taken in, and in whether the shape was read. It was the first kind until it was
 * written as a reading: it recognised a connective and a denial for itself, had no word for a
 * binding, and a rule an author named made nothing known.
 *
 * <p><b>And the hoist inside the invariant checker is a different job.</b> That one enters a binding
 * standing inside a value — under a field read, under one side of a comparison — so that the value
 * built after it is read under the names the call handed over. It is about what a walk sees next and
 * not about what environment a clause is read in, and it stays where it is.
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

    private static Core.Binary binary(BinOp op, Core left, Core right) {
        return new Core.Binary(op, left, right, SourceConstructOrigin.unwritten(), Type.BOOL, POS);
    }

    /** `<subject> >= 0`, the rule itself. */
    private static Core.Binary rule(Core subject) {
        return binary(BinOp.GE, subject, new Core.Int(0, Type.INT, POS));
    }

    /** `let $n = n in <written against $n>`, which is what naming a rule expands to. */
    private static Core.LetIn naming(java.util.function.Function<Core, Core> body) {
        BindingId bound = new BindingId(OWNER, 1);
        Core written = body.apply(new Core.Read("$n", bound, Type.INT, POS));
        return new Core.LetIn(new Core.Binder("$n", bound), subject(), written, written.type(),
                POS);
    }

    /** `let $n = n in $n >= 0`, which is what naming the rule expands to. */
    private static Core.LetIn named() {
        return naming(WhoCrossesABindingInAConditionAndWhoDoesNotTest::rule);
    }

    private static List<NumericConstraint> stated(Terms terms, Core cond, Denotations at) {
        List<NumericConstraint> out = new ArrayList<>();
        Conditions.stating(terms, cond, at, true, out);
        return out;
    }

    /** What a condition states on its own, which is read over the clause's shape and crosses a
     *  binding. */
    @Test
    void theReadingOfWhatAConditionStatesCrossesABinding() {
        Terms terms = terms();

        assertEquals(List.of(1, 1), List.of(
                        stated(terms, rule(subject()), rootAt()).size(),
                        stated(terms, named(), rootAt()).size()),
                "how many relations each spelling states");
    }

    /**
     * And the reader of a flat condition does not, which is the boundary and not a defect.
     *
     * <p>What it is asked is which comparisons one expression states. A binding is not a comparison
     * and what a binder means is not this reader's answer, so it says none — and the reading above
     * gets the rule by having crossed the binding before it asks this at all. A reader of a flat
     * condition that learned to enter a binding would be the second account of a binder this whole
     * arrangement exists to stop.
     */
    @Test
    void theFlatReaderOfComparisonsDoesNotReadABinderNode() {
        Terms terms = terms();

        assertEquals(List.of(1, 0), List.of(
                        Conditions.comparisonsStatedBy(terms, rule(subject()), rootAt())
                                .inReadingOrder().size(),
                        Conditions.comparisonsStatedBy(terms, named(), rootAt())
                                .inReadingOrder().size()),
                "how many comparisons each spelling states");
    }

    /**
     * What stops such a reader is the node and not the environment it would read one in.
     *
     * <p>Handed what is under the binding, the reader of comparisons states the rule; handed the
     * binding itself in that same environment, it states nothing. So an entered environment is not
     * what it is missing — the binding is a form its own reading has no word for, and a reading that
     * goes over the clause's shape crosses it without ever being handed one (ADR-0106).
     */
    @Test
    void whatStopsAReaderOfComparisonsIsTheNodeAndNotTheEnvironment() {
        Terms terms = terms();
        Core.LetIn named = named();
        Denotations inside = terms.inside(named, rootAt());

        assertEquals(List.of(1, 0), List.of(
                        Conditions.comparisonsStatedBy(terms, named.body(), inside)
                                .inReadingOrder().size(),
                        Conditions.comparisonsStatedBy(terms, named, inside)
                                .inReadingOrder().size()),
                "what is under the binding, and the binding handed whole to the same reader");
    }

    /** And so does the walk that threads knowledge along a path, which is a reading of the shape. */
    @Test
    void theWalkThatThreadsKnowledgeCrossesABindingAsWell() {
        Terms terms = terms();
        Predicates predicates = new Predicates(terms);
        Denotations at = rootAt();
        LinearForm<FactSubject> about = terms.affineOf(subject(), at);
        assertNotNull(about, "the value the rule is about is a form this reads");

        assertEquals(List.of(true, true), List.of(
                        predicates.assumeCond(rule(subject()), Known.top(), at, true)
                                .known().numbers().entails(about, Rel.GE),
                        predicates.assumeCond(named(), Known.top(), at, true)
                                .known().numbers().entails(about, Rel.GE)),
                "whether what is known entails the rule, written out and named");
    }

    /**
     * And the whole of what it answers agrees, not the state alone.
     *
     * <p>Three answers and not one. What is entailed says what a proof may rest on; whether anything
     * was taken in and whether the shape was read say what an unsettled arm may be explained by, and
     * a spelling that agreed on the first and not on the other two would report this compiler's
     * limit at a rule it had in fact read to the end.
     */
    @Test
    void takingARuleInAnswersTheSameWhicheverWayItWasWritten() {
        Terms terms = terms();
        Predicates predicates = new Predicates(terms);
        Denotations at = rootAt();
        LinearForm<FactSubject> about = terms.affineOf(subject(), at);

        assertEquals(answering(predicates.assumeCond(rule(subject()), Known.top(), at, true), about),
                answering(predicates.assumeCond(named(), Known.top(), at, true), about),
                "what taking the rule in came to, written out and named");
    }

    /** What one of these came to, as the three answers it is. */
    private static List<Object> answering(Predicates.Assumed assumed,
                                          LinearForm<FactSubject> about) {
        return List.of(assumed.known().numbers().entails(about, Rel.GE), assumed.taken(),
                assumed.shapeRead());
    }

    /**
     * A conjunction is taken a half at a time, the right under what the left left.
     *
     * <p>Which is this reading's own algebra and not the shape's: the shape says these two are
     * composed, and taking them in order is what taking a conjunction in means here. Read with each
     * half given the state the conjunction began in, the left half's rule would be gone from what
     * comes out, and a pair of halves nothing can satisfy at once would come out satisfiable — which
     * is exactly the answer a branch nothing reaches is decided by.
     */
    @Test
    void aConjunctionTakesItsRightHalfUnderWhatItsLeftHalfLeft() {
        Terms terms = terms();
        Predicates predicates = new Predicates(terms);
        Denotations at = rootAt();
        Core.LetIn both = naming(bound -> binary(BinOp.AND,
                binary(BinOp.GE, bound, new Core.Int(1, Type.INT, POS)),
                binary(BinOp.LE, bound, new Core.Int(0, Type.INT, POS))));

        assertEquals(true,
                predicates.assumeCond(both, Known.top(), at, true).known().reachesNothing(),
                "no value is at once above nought and below it, which only the half that was read"
                        + " second can find out");
    }

    /**
     * A choice names the two halves it composes, and nothing under them.
     *
     * <p>One of a choice's parts holds and this cannot say which, so neither is taken in. What is
     * left of it is that the author named the two, which is read off the shape that composed them —
     * the halves as they were written, denials and all. Walked instead, every comparison under the
     * choice would be named, which widens what a clause may be owed for and what a report may point
     * at.
     */
    @Test
    void aChoiceNamesItsTwoHalvesAndNoFurther() {
        Terms terms = terms();
        Predicates predicates = new Predicates(terms);
        Denotations at = rootAt();
        Core deeper = binary(BinOp.GE, subject(), new Core.Int(1, Type.INT, POS));
        Core half = binary(BinOp.OR, deeper,
                binary(BinOp.LE, subject(), new Core.Int(9, Type.INT, POS)));
        Core beside = binary(BinOp.EQ, subject(), new Core.Int(5, Type.INT, POS));
        Known known = predicates.assumeCond(binary(BinOp.OR, half, beside), Known.top(), at, true)
                .known();

        assertEquals(List.of(true, true, false), List.of(
                        known.speaksOf(terms.subjectOf(half, at)),
                        known.speaksOf(terms.subjectOf(beside, at)),
                        known.speaksOf(terms.subjectOf(deeper, at))),
                "the two halves of the choice, and a comparison one of them is written out of");
    }

    /**
     * And a denial above a binding reaches the connective under it.
     *
     * <p>The three shapes at once. A denial is carried to the leaves as the shape is read, so
     * denying a named rule turns over the connective inside the helper's body — a conjunction denied
     * is the choice between the denials, and denying that again is the conjunction back. Read as
     * three recognitions in three readers, the one that had no word for the binding stopped before
     * the other two ever ran.
     */
    @Test
    void aDenialAboveANamedRuleReachesTheConnectiveUnderIt() {
        Terms terms = terms();
        Predicates predicates = new Predicates(terms);
        Denotations at = rootAt();
        LinearForm<FactSubject> about = terms.affineOf(subject(), at);
        Core.LetIn both = naming(bound -> binary(BinOp.AND,
                binary(BinOp.GE, bound, new Core.Int(0, Type.INT, POS)),
                binary(BinOp.LE, bound, new Core.Int(0, Type.INT, POS))));
        // `named(n) == false`, taken as failing, which is the conjunction stated.
        Known known = predicates.assumeCond(
                binary(BinOp.EQ, both, new Core.Bool(false, Type.BOOL, POS)),
                Known.top(), at, false).known();

        assertEquals(List.of(true, true), List.of(
                        known.numbers().entails(about, Rel.GE),
                        known.numbers().entails(about, Rel.LE)),
                "each way nought is stood against, from the two halves of the denied conjunction"
                        + " under the binding");
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
     *  wrote, which is the shape a reading crosses and a flat reader stops at. */
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

    /** And what a construction the arms are given comes to, which is the same either way. */
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
     * And the report an author sees of a branch no value reaches, which is the same either way.
     *
     * <p>A condition stating nothing about what the arm is leaves the construction owed both ways,
     * and the branch nothing reaches is named both ways. This is the reader with no tree-rebuilding
     * above it: what it asks is which branches a value can be in, over the tree the analysis holds,
     * and the binding it meets there is crossed because what it asks the condition is a reading of
     * the clause's shape.
     */
    @Test
    void aBranchNoValueReachesIsNamedWhicheverWayTheConditionWasWritten() {
        List<String> both = List.of("E2011 check.invariant.title", "E1327 check.dead.branch.title");

        assertEquals(List.of(both, both), List.of(
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
