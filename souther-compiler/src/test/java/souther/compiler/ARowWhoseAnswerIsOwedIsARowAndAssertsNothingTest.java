package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.Expectation;
import souther.compiler.observe.ExpectationState;
import souther.compiler.observe.RowOutcome;
import souther.compiler.observe.RowStatement;
import souther.compiler.observe.Stage;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.compiler.query.Db;
import souther.compiler.query.Output;
import souther.compiler.source.SourceId;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row written {@code <?>}: what it is, and what it is not.
 *
 * <p>Two claims, and neither holds without the other. It is a row — the module compiles with it in,
 * so a block of them can be pasted whole and answered one at a time, and everything that keeps
 * source keeps these. And it asserts nothing — nothing it did is evidence that the model answers as
 * it should, so the work it stands for goes on being reported until somebody writes the answer.
 *
 * <p>The second is the one that is easy to lose. A row that ran and was counted as covering the arm
 * it went through would take a finding off the report and leave no assertion behind it, which is the
 * shape of a build that is adequate because nobody looked.
 */
class ARowWhoseAnswerIsOwedIsARowAndAssertsNothingTest {

    private static final String MODEL = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Submitted = { cost: Amount }
            data Waiting = { cost: Amount }

            behavior submit : (cost: Amount) -> Submitted | Waiting
                constructs Submitted, Waiting

            let submit (cost) = {
                guard cost.value <= 100 else Waiting { cost = cost }
                Submitted { cost = cost }
            }

            example submit
                | "under the ceiling" : (Amount(50)) -> Submitted { cost = Amount(50) }
            """;

    /** The same model with a row for the other arm, written with its answer owed. */
    private static final String OWED = MODEL + "    | \"over it\" : (Amount(101)) -> <?>\n";

    /** And with that row answered, which is the control the two claims are read against. */
    private static final String ANSWERED = MODEL
            + "    | \"over it\" : (Amount(101)) -> Waiting { cost = Amount(101) }\n";

    @Test
    void theModuleCompilesWithTheRowInIt() {
        assertEquals(List.of(), errorsIn(OWED),
                "a module whose rows are still owed answers goes on compiling");
    }

    /**
     * It ran, it answered, and there was nothing to hold the answer to.
     *
     * <p>Every part of that is asserted. A row recorded as {@code PENDING} would be one nothing
     * evaluated, and one recorded as {@code HELD} would be a claim about the model that nobody made.
     */
    @Test
    void itRanAndHeldNothing() {
        RowOutcome owed = rowNamed(OWED, "over it");

        assertEquals(Disposition.NOTHING_TO_HOLD, owed.disposition(),
                "the row ran and states nothing for the answer to keep");
        assertEquals(Stage.ANSWERED, owed.stage(),
                "which is as far as a row gets when there is nothing to compare");
        assertTrue(owed.answered(), "the behavior answered it");
        assertTrue(owed.statement() instanceof RowStatement.Stated stated
                        && stated.expects() instanceof Expectation.Owed,
                "and what it states of the answer is that the answer is owed: " + owed.statement());
    }

    /**
     * The arm it goes through is still reported, and in the words that say what is left.
     *
     * <p>The row reaches the arm, so telling an author no row goes through it would send them to
     * write one they have already written. What is left there is the answer.
     */
    @Test
    void theArmItReachesIsReportedAsAwaitingItsAnswer() {
        List<ExampleMessage> said = armMessagesIn(OWED);

        assertEquals(1, said.size(), "one arm, one sentence: " + said);
        assertTrue(said.getFirst() instanceof ExampleMessage.ARowAtThatArmAwaitsItsAnswer,
                "the arm has a row and is short of its answer: " + said);
    }

    /** And answering the row is what takes the arm off the report, which is what says the sentence
     *  above is about this row rather than about the model having two arms. */
    @Test
    void answeringItCoversTheArm() {
        assertEquals(List.of(), armMessagesIn(ANSWERED),
                "the arm is covered by a row that says what the behavior answers there");
    }

    /**
     * A second row covering the arm does not answer the first.
     *
     * <p>The arm and the row are two facts, and this is where holding them as one loses the second:
     * the arm is covered, so nothing is owed about the arm — and the row written {@code <?>} is
     * still written, and still owed an answer. Reported through the arm alone, the row went missing
     * exactly when somebody else's row happened to cover it.
     */
    @Test
    void anAnsweredAndAnUnansweredRowThroughTheSameArmStillLeaveTheRowUnanswered() {
        String both = ANSWERED + "    | \"also over it\" : (Amount(102)) -> <?>\n";

        assertEquals(List.of(), armMessagesIn(both),
                "the arm is covered, so nothing is owed about the arm");
        assertEquals(List.of("also over it"), rowsOwedIn(both),
                "and the row whose answer is owed is still owed one");
        assertTrue(gapCodesIn(both).contains("E1934"),
                () -> "which a build refuses over: " + gapCodesIn(both));
    }

    /**
     * A behavior with no arms owes it just the same.
     *
     * <p>The plainest case, and the one an arm-shaped finding cannot hold at all: there is no arm
     * for the fact to be attached to, so a row written {@code <?>} had nowhere to be reported from.
     */
    @Test
    void anUnansweredRowNeedsNoArmToRemainUnanswered() {
        String straight = """
                module example.plain

                data Ok = { n: Int }

                behavior identity : (x: Int) -> Ok
                    constructs Ok
                let identity (x) = Ok { n = x }

                example identity
                    | "nothing branches here" : (1) -> <?>
                """;

        assertEquals(List.of("nothing branches here"), rowsOwedIn(straight),
                "a row with no arm under it is owed an answer like any other");
        // And it is the row alone that a build stands on here. There is no arm in this model and
        // nothing else this bar refuses over, so a verdict that came out satisfied would say the
        // new finding reaches the report and not the build.
        assertEquals(AdequacyReport.AdequacyStatus.NOT_SATISFIED, verdictOn(straight),
                "the row is what a build held to the rows refuses over");
    }

    /**
     * And a row of the same behavior that could not be read does not take it away.
     *
     * <p>What weakens an arm's measurement is what became of the rows, and that is about the arms.
     * A row written {@code <?>} is a fact about the text; reported through the arm, it was
     * suppressed whenever some other row left the arm's measurement short of an answer.
     */
    @Test
    void anotherRowThatCouldNotBeReadDoesNotSettleThisOne() {
        String alongside = OWED
                + "    | \"runs away\" : (Amount(1)) -> Waiting { cost = Amount(1) }\n";

        assertTrue(rowsOwedIn(alongside).contains("over it"),
                () -> "the owed row is owed whatever became of the rows beside it: "
                        + rowsOwedIn(alongside));
    }

    /**
     * A row too large to hand on still knows its answer is owed.
     *
     * <p>What a row states is dropped when its values are larger than a reader is given, and the
     * answer being owed used to be read out of that. So a valid row with a long input stopped being
     * owed — and the invariant that says a row ending with nothing to hold is one whose answer is
     * owed then refused to build the outcome at all.
     */
    @Test
    void anUnansweredRowWhoseInputObservationIsTruncatedStillKnowsItsAnswerIsOwed() {
        String wide = """
                module example.wide

                data Note = String

                data Ok = { n: Note }

                behavior take : (t: Note) -> Ok
                    constructs Ok
                let take (t) = Ok { n = t }

                example take
                    | "a long one" : (Note("%s")) -> <?>
                """.formatted("x".repeat(2000));

        assertEquals(List.of(), errorsIn(wide), "the module compiles");
        assertEquals(ExpectationState.OWED, rowNamed(wide, "a long one").expectation(),
                "what the source put there is not what the statement could carry");
        assertEquals(List.of("a long one"), rowsOwedIn(wide),
                "so the row is reported as owing its answer");
    }

    /**
     * A build held to a bar that refuses over arms refuses over this one.
     *
     * <p>The row is written and the arm is uncovered, and the second is what a build stands on. A
     * report that named the arm while calling the build adequate would be telling an author about
     * work and letting the build past it at the same time.
     */
    @Test
    void aBuildStillRefusesOverIt() {
        // Each side compiled once and read twice. Written as the message of an assertion, the
        // second read runs whether or not the assertion needs it — a compile apiece, on a model
        // measured at the level that runs every row a second time.
        List<String> owed = gapCodesIn(OWED);
        List<String> answered = gapCodesIn(ANSWERED);

        assertTrue(owed.contains("E1934"),
                () -> "a row owing its answer is a gap: " + owed);
        assertFalse(owed.contains("E1918"),
                () -> "and not one about an arm nothing reaches, since a row reaches it: " + owed);
        assertFalse(answered.contains("E1934"),
                () -> "answering it settles the gap: " + answered);
        assertFalse(answered.contains("E1918"),
                () -> "and the arm it covers is not reported either: " + answered);

        // The verdict itself, and not only the code the warning carries. What `--strict` exits over
        // is this word; a gap that was printed and left the verdict satisfied would let a build
        // past the work it had just told an author about.
        //
        // One-sided on purpose. This model has gaps of other kinds — a bound to be written at, a
        // case no row applies the behavior to — so answering the row does not make the verdict
        // satisfied and asserting that it does would be asserting something about those. What
        // answering settles is the arm, which is the pair above.
        assertEquals(AdequacyReport.AdequacyStatus.NOT_SATISFIED, verdictOn(OWED),
                "a build held to the arms refuses over a row that answers nothing");
    }

    /** What a build held to the arms makes of this model, which is what {@code --strict} reads. */
    private static AdequacyReport.AdequacyStatus verdictOn(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(new Adequacy.Asked(Adequacy.Level.ALL, false,
                Adequacy.AdequacyBar.CLASSES));
        compilation.answerEverything();
        return AdequacyReport.of(compilation).adequacy();
    }

    /** {@code <?>} is read where a row's answer goes and nowhere else: written as an input, it is
     *  not a term and the parse says so. */
    @Test
    void itIsNotATermAnywhereElse() {
        assertFalse(errorsIn(MODEL + "    | \"as an input\" : (<?>) -> Waiting { cost = Amount(1) }\n")
                        .isEmpty(),
                "a mark standing where a value stands is refused");
    }

    // --- reading what a compile came to ---------------------------------------------------------

    private static Compilation compiled(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Level.ALL);
        compilation.answerEverything();
        return compilation;
    }

    private static List<String> errorsIn(String source) {
        List<String> said = new ArrayList<>();
        for (Db.Found each : compiled(source).db().allReports()) {
            if (each.report().isError()) {
                said.add(each.report().diagnostic().code() + " " + each.report().diagnostic().said());
            }
        }
        return said;
    }

    /** The row this behavior wrote under {@code name}, which every claim above is about one of. */
    private static RowOutcome rowNamed(String source, String name) {
        Compilation compilation = compiled(source);
        for (String module : compilation.modules()) {
            for (SourceId sourceId : compilation.exampleSourcesOf(module)) {
                Output.Examples.Of observed = compilation.db()
                        .ask(Output.Examples.asked(compilation.db(), module, sourceId)).value();
                if (observed == null) {
                    continue;
                }
                for (RowOutcome row : observed.rows()) {
                    if (row.identity().shown().contains(name)) {
                        return row;
                    }
                }
            }
        }
        throw new AssertionError("no row named " + name + " was read");
    }

    /** What each arm warning of this model says, which is the sentence and not the code: the two
     *  arms of one rule are told apart by what an author is asked to do about them. */
    private static List<ExampleMessage> armMessagesIn(String source) {
        List<ExampleMessage> said = new ArrayList<>();
        for (Db.Found each : compiled(source).db().allReports()) {
            Diagnostic d = each.report().diagnostic();
            switch (d.said()) {
                case ExampleMessage.NoRowGoesThroughThatArm it -> said.add(it);
                case ExampleMessage.ARowAtThatArmAwaitsItsAnswer it -> said.add(it);
                default -> { }
            }
        }
        return said;
    }

    /** Which rows this model is told owe an answer, by the name each wrote. Read off the report
     *  rather than off the source, so what is asserted is what a person is told. */
    private static List<String> rowsOwedIn(String source) {
        List<String> said = new ArrayList<>();
        for (Db.Found each : compiled(source).db().allReports()) {
            if (each.report().diagnostic().said()
                    instanceof ExampleMessage.TheNamedRowsAnswerIsOwed named) {
                said.add(named.row());
            }
        }
        return said;
    }

    /** The codes this model's gaps are reported under. */
    private static List<String> gapCodesIn(String source) {
        List<String> codes = new ArrayList<>();
        for (Db.Found each : compiled(source).db().allReports()) {
            Diagnostic d = each.report().diagnostic();
            if (!each.report().isError() && d.code() != null) {
                codes.add(d.code());
            }
        }
        return codes;
    }
}
