package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.coverage.CoverageSites;
import souther.compiler.coverage.DecidedBy;
import souther.compiler.types.WrittenOwner;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.MeasurementStatus;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.ItemAssessment;
import souther.compiler.query.Measure;
import souther.compiler.query.Measurement;
import souther.compiler.query.Weakening;
import souther.compiler.query.WeakeningSet;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why a measure has no number, kept by the measure and not worked out again downstream.
 *
 * <p>Every one of these has the same shape underneath. A measure came back with nothing, the reason
 * was not carried with it, and whatever read it next rebuilt the reason out of what else was to
 * hand — the row count, whether the behavior was injected, whether the declaration had a body. Those
 * correlate with the reason and are not it, so each rebuild was right about the cases it was written
 * against and wrong about one nobody had in mind.
 */
class AMeasureWithNoNumberSaysWhyTest {

    /**
     * One model holding each way a measure can come back empty.
     *
     * <p>Together in one module on purpose: what the report says about any one of these is only
     * right if it is still right beside the others, and the run that measures them is the same run.
     */
    private static final String MODEL = """
            module example.repro

            data Wrap = { v: String }
            data Mid = { v: String }
            data Out = { v: String }

            behavior widen : (w: Wrap) -> Mid constructs Mid
            let widen (w) = Mid { v = w.v }
            behavior narrow : (m: Mid) -> Out constructs Out
            let narrow (m) = Out { v = m.v }

            behavior both = widen >-> narrow

            example both
                | (Wrap { v = "x" }) -> Out { v = "x" }

            data Amount = Int
                invariant value >= 0 && value <= 1000

            data Req = { cost: Amount }
            data Res = { n: Int }

            behavior baseRate : (r: Req) -> Res
                constructs Res

            behavior rated : (r: Req) -> Res
                constructs Res
            let rated (r) = Res { n = r.cost.value }

            data Yes
            data No
            data Flag = Yes | No
            data Ask = { flag: Flag }

            behavior classify : (q: Ask) -> Res
                constructs Res
            let classify (q) = Res { n = 1 }

            example classify
                | (Ask { flag = Yes }) -> Res { n = 1 }

            data Pair = { left: Flag, right: Flag }

            behavior sift : (p: Pair) -> Res
                constructs Res
            let sift (p) = match p.left with
                | Yes -> Res { n = 1 }
                | No -> Res { n = 0 }
            """;

    /**
     * A signature with cases to cover and no row to cover them with.
     *
     * <p>Its own model because the measure only reaches {@code NO_ROWS} where every row there is was
     * read: beside a row nobody evaluated, a behavior with no rows is undecided rather than
     * unmeasured, and the shared model above has rows.
     */
    private static final String NO_ROWS = """
            module example.norows

            data Yes
            data No
            data Flag = Yes | No
            data Ask = { flag: Flag }

            data Approved
            data Rejected
            data Verdict = Approved | Rejected

            behavior judge : (q: Ask) -> Verdict
            let judge (q) = Approved
            """;

    /**
     * A measure nobody made says so, whichever measure it is.
     *
     * <p>Silence was the older answer and it is the one this issue is about. A behavior with cases to
     * cover and no row to cover them with had its whole {@code signature} line left out, which reads
     * as a measurement that failed rather than one nobody asked for — and put two behaviors of one
     * report on a different number of lines with nothing saying why.
     */
    @Test
    void aSignatureWithCasesAndNoRowSaysNobodyMeasuredIt() {
        Compilation compilation = Compilation.ofSource(NO_ROWS, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String judge = behaviorBlock(
                AdequacyReport.of(compilation).human(SourceNameResolver.identity()), "judge");

        assertTrue(judge.contains("signature   not measured (no row names this behavior)"), judge);
    }

    /**
     * {@link #MODEL} measured, once for the class.
     *
     * <p>Six checks below read this one and nothing writes to it: a compilation's database answers
     * questions and keeps the answers, which is the same arrangement whether one check asks or six
     * do. Rendered once for the same reason — the sentences are a function of the report.
     */
    private static Compilation measured;

    private static String rendered;

    private static Compilation compiled() {
        if (measured == null) {
            Compilation compilation = Compilation.ofSource(MODEL, "Main");
            compilation.measure(Adequacy.Asked.fullReport());
            compilation.answerEverything();
            measured = compilation;
        }
        return measured;
    }

    private static String human() {
        if (rendered == null) {
            rendered = AdequacyReport.of(compiled()).human(SourceNameResolver.identity());
        }
        return rendered;
    }

    /**
     * A composition says why the two measures do not apply to it, rather than leaving them out.
     *
     * <p>It is measured at its stages and has no positions of its own, which is a fact about the
     * model and is what the other two measures say of it in the same breath. Left out, the same page
     * was what a behavior whose measures had something to say and whose evidence lists were empty
     * got — and a document written from the same compilation said {@code no_subject} where the page
     * said nothing at all (issue #1079).
     */
    @Test
    void aCompositionSaysWhyItsPositionsAreNotMeasuredHere() {
        String both = behaviorBlock(human(), "both");

        assertTrue(both.contains(
                "partition   not applicable (this behavior is measured at its stages)"), both);
        assertTrue(both.contains(
                "border      not applicable (this behavior is measured at its stages)"), both);
    }

    /**
     * The whole report, fixed.
     *
     * <p>Fixed whole rather than line by line. Every wrong line this issue was about could have been
     * removed by printing less, and a test that only forbids the wrong sentences passes on a report
     * that says nothing at all. What each behavior is told about it is here to be read.
     */
    @Test
    void theReportSaysWhatEachMeasureManagedAndWhyWhereItManagedNothing() {
        assertEquals("""
                example.repro                                            measurement: complete
                  widen                    implemented   rows 0    pending 0
                    signature   not applicable (this behavior's output is not a sum)
                    partition   not applicable (the rules of this behavior divide no position)
                      · divided no way: w.v
                    border      not applicable (the rules of this behavior draw no line)
                    branch      not applicable (this body owes no arm)
                  narrow                   implemented   rows 0    pending 0
                    signature   not applicable (this behavior's output is not a sum)
                    partition   not applicable (the rules of this behavior divide no position)
                      · divided no way: m.v
                    border      not applicable (the rules of this behavior draw no line)
                    branch      not applicable (this body owes no arm)
                  both                     implemented   rows 1    pending 0
                    signature   not applicable (this behavior's output is not a sum)
                    partition   not applicable (this behavior is measured at its stages)
                    border      not applicable (this behavior is measured at its stages)
                    branch      not applicable (this behavior has no body)
                  baseRate                 injected      rows 0    pending 0
                    signature   not applicable (this behavior's output is not a sum)
                    partition   not applicable (the rules of this behavior divide no position)
                    border      borders 2   obligations 0/0
                      · no OFF point is owed at r.cost = 0 (invariant Amount #1): excluded — the rules leave no value there
                      · no OUT point is owed at r.cost = 0 (invariant Amount #1): excluded — the rules leave no value there
                      · no OFF point is owed at r.cost = 1000 (invariant Amount #1): excluded — the rules leave no value there
                      · no OUT point is owed at r.cost = 1000 (invariant Amount #1): excluded — the rules leave no value there
                    branch      not applicable (this behavior has no body)
                  rated                    implemented   rows 0    pending 0
                    signature   not applicable (this behavior's output is not a sum)
                    partition   not applicable (the rules of this behavior divide no position)
                    border      borders 2   obligations 0/0
                      · no OFF point is owed at r.cost = 0 (invariant Amount #1): excluded — the rules leave no value there
                      · no OUT point is owed at r.cost = 0 (invariant Amount #1): excluded — the rules leave no value there
                      · no OFF point is owed at r.cost = 1000 (invariant Amount #1): excluded — the rules leave no value there
                      · no OUT point is owed at r.cost = 1000 (invariant Amount #1): excluded — the rules leave no value there
                    branch      not applicable (this body owes no arm)
                  classify                 implemented   rows 1    pending 0
                    signature   not applicable (this behavior's output is not a sum)
                    partition   axes 1   equivalence partitions 1/2
                      · no row is in `No` at q.flag
                      · q.flag holds 2 classes and this behavior's rules compose 0 of them
                    border      not applicable (the rules of this behavior draw no line)
                    branch      not applicable (this body owes no arm)
                  sift                     implemented   rows 0    pending 0
                    signature   not applicable (this behavior's output is not a sum)
                    partition   axes 2   equivalence partitions 0/0   (2 not measured: no row names this behavior)
                      · p.left holds 2 classes and this behavior's rules compose 0 of them
                      · p.right holds 2 classes and this behavior's rules compose 0 of them
                    border      not applicable (the rules of this behavior draw no line)
                    branch      not measured (no row names this behavior)
                  declarations   obligations 0/4
                      ? undecided whether a row is at the ON point value = 0 (invariant Amount #1) — no row names this behavior
                          · read as baseRate/r.cost: = 0
                          · read as rated/r.cost: = 0
                      ? undecided whether a row is at the IN point value in 0 < value <= 1000 (invariant Amount #1) — no row names this behavior
                          · read as baseRate/r.cost: in 0 < r.cost <= 1000
                          · read as rated/r.cost: in 0 < r.cost <= 1000
                      ? undecided whether a row is at the ON point value = 1000 (invariant Amount #1) — no row names this behavior
                          · read as baseRate/r.cost: = 1000
                          · read as rated/r.cost: = 1000
                      ? undecided whether a row is at the IN point value in 0 <= value < 1000 (invariant Amount #1) — no row names this behavior
                          · read as baseRate/r.cost: in 0 <= r.cost < 1000
                          · read as rated/r.cost: in 0 <= r.cost < 1000

                7 behaviors: 6 implemented, 0 unimplemented, 1 injected; 0 rows waiting for a `let`.
                adequacy: undetermined
                  what keeps it open
                    may change in a wider run     0
                    unaffected by a wider run     5
                      not_measured branch of sift — no measurement was made: no_rows
                      not_measured invariant Amount #1 = 0 (at the line) — no measurement was made: no_rows
                      not_measured invariant Amount #1 = 0 (in the region, above) — no measurement was made: no_rows
                      not_measured invariant Amount #1 = 1000 (at the line) — no measurement was made: no_rows
                      not_measured invariant Amount #1 = 1000 (in the region, below) — no measurement was made: no_rows
                """, human());
    }

    /** What the report may not say, checked over the whole of it: a line removed from one behavior
     * and left on another is the defect, not the fix. */
    @Test
    void nothingSaysAMeasurementIsStillComing() {
        String human = human();
        assertFalse(human.contains("until branches are"), human);
        assertFalse(human.contains("were not measured"), human);
    }

    /**
     * A behavior no row names has no gaps to be told about.
     *
     * <p>An absence of evidence is not a set of gaps, and naming the classes nothing sits in sends an
     * author after rows on the strength of a measurement nobody made. Asked of {@code sift}, which
     * has positions the model divides and no row at any of them — a behavior with no positions at all
     * would pass this whatever the code did.
     */
    @Test
    void aBehaviorNoRowNamesIsNotToldWhichClassesItMisses() {
        PartitionEvidence partition = partitions().get("sift");
        assertEquals(2, partition.axes().size(), "the model divides two positions here");
        for (PartitionEvidence.AxisCoverage axis : partition.axes()) {
            assertEquals(2, axis.classes().size(), axis.path());
            assertEquals(PartitionEvidence.AxisCoverage.NoRows.NO_ROWS, axis.reached().why(), axis.path());
        }
        assertEquals(List.of(), findings("sift", Adequacy.Kind.AXIS_CLASS_UNCOVERED),
                "nothing was established, so nothing was found");

        String sift = behaviorBlock(human(), "sift");
        assertFalse(sift.contains("no row is in"), sift);
        String classify = behaviorBlock(human(), "classify");
        assertTrue(classify.contains("no row is in `No` at q.flag"),
                "the same line is still said where a row was written: " + classify);
    }

    /** How large the space of combinations is says nothing about a behavior no row names, so the
     * numbers are not shown and the reason is. */
    @Test
    void thePairsOfABehaviorNoRowNamesAreNotCountedEither() {
        PartitionEvidence.PairSpace pairs = partitions().get("sift").pairs();
        assertEquals(4, pairs.total(), "two positions of two classes each");
        assertEquals(MeasurementStatus.NOT_MEASURED, AdequacyReport.statusOf(pairs.counted()));
        assertEquals(PartitionEvidence.PairSpace.NoRows.NO_ROWS, pairs.counted().why());
        assertFalse(behaviorBlock(human(), "sift").contains("pairs"),
                "untried is what nobody tried, and nobody was asked");
    }

    /** A composition has no arms of its own. Its stages have them, and it is not asked. */
    @Test
    void aCompositionSaysItsArmsDoNotApply() {
        Adequacy.BranchEvidence branch = branches().get("both");
        assertEquals(MeasurementStatus.NOT_APPLICABLE, AdequacyReport.statusOf(branch.measured()));
        assertEquals(Adequacy.BranchEvidence.NoArms.NO_BODY, branch.measured().why());
        assertFalse(branch.applicable(), "nothing here is owed a branch measure");
    }

    /** An injected behavior has none either, and it is the body and not the `let` that decides:
     * the composition above is implemented and has no body all the same. */
    @Test
    void anInjectedBehaviorSaysTheSame() {
        Adequacy.BranchEvidence branch = branches().get("baseRate");
        assertEquals(Adequacy.BranchEvidence.NoArms.NO_BODY, branch.measured().why());
    }

    /** A body with arms, that no row names, is a measure that was not made rather than one that does
     * not apply. The verdict reads these apart. */
    @Test
    void aBodyNoRowNamesIsUnmeasuredAndNotInapplicable() {
        Adequacy.BranchEvidence branch = branches().get("sift");
        assertEquals(Adequacy.BranchEvidence.NotAsked.NO_ROWS, branch.measured().why());
        assertTrue(branch.applicable(), "there are arms here; nothing asked about them");
    }

    /** A body that forks nowhere owes no arm, and a row would not give it one. Told apart from the
     * one above, which is the same absence of numbers and asks the author for a row. */
    @Test
    void aBodyThatForksNowhereOwesNoArm() {
        Adequacy.BranchEvidence branch = branches().get("rated");
        assertEquals(Adequacy.BranchEvidence.NoArms.NO_ARM_OBLIGATIONS, branch.measured().why());
        assertFalse(branch.applicable(), "nothing here is owed a branch measure");
    }

    /**
     * The two are different answers, and the status says which.
     *
     * <p>A measure with nothing to be about and a measure nobody asked ask opposite things of
     * whoever reads them: the first wants nothing, and the second wants a row written. Held as one
     * status apart by each measure's own reason, only a reader that knows that measure's reasons can
     * tell them apart — so every reader that has to rebuilt it, and a measure whose reasons nobody
     * had in mind was read as whichever the rebuild happened to favour.
     */
    @Test
    void aMeasureThatDoesNotApplyIsNotTheSameStatusAsOneNobodyMade() {
        assertNotEquals(branches().get("sift").measured().why(),
                branches().get("both").measured().why(),
                "a body no row names and a composition with no arms are not one answer");
    }

    /**
     * What a behavior owes is the same answer at every level.
     *
     * <p>The arms a body has are read off the checked bodies, and nothing in that reading waits on
     * the instrumented classes. So a build that did not ask for the arms gets the applicability
     * answer all the same, and only the behaviors that owe one come back as a measurement nobody
     * made — which is what keeps a verdict from being held open by a body that forks nowhere
     * (issue #955).
     */
    @Test
    void whatABehaviorOwesIsTheSameAnswerAtEveryLevel() {
        Map<String, Adequacy.BranchEvidence> asked = branchesAt(Adequacy.Level.ALL);
        Map<String, Adequacy.BranchEvidence> notAsked = branchesAt(Adequacy.Level.WITNESS);

        for (Map.Entry<String, Adequacy.BranchEvidence> each : asked.entrySet()) {
            String behavior = each.getKey();
            assertEquals(each.getValue().applicable(), notAsked.get(behavior).applicable(),
                    behavior + " owes what it owes whether or not the build asked for the arms");
            if (!each.getValue().applicable()) {
                assertEquals(each.getValue().measured().why(),
                        notAsked.get(behavior).measured().why(),
                        behavior + " says the same reason either way");
            }
        }
        // And the one that does owe arms is the one the level changes the answer for.
        assertEquals(Adequacy.BranchEvidence.NotAsked.NOT_ASKED,
                notAsked.get("sift").measured().why());
    }

    /**
     * And which of the two reasons it is decides whether the line is printed at all.
     *
     * <p>The two states of one reason, held apart on one behavior. What a build asked for is an
     * input to the whole run, so a line repeating it under every behavior says one fact as many
     * times as the module has behaviors; a behavior no row names is short of something of its own
     * and says so. Read for one of the two — the line is printed unless the reason is the other —
     * the second reason added to that enum is printed as neither.
     */
    @Test
    void theArmsNobodyAskedForSayNothingUnderTheBehaviorAndTheArmsNoRowNamesSayWhy() {
        String notAsked = behaviorBlock(humanAt(Adequacy.Level.WITNESS), "sift");
        assertFalse(notAsked.contains("branch"),
                () -> "what a build asked for is not said behavior by behavior:\n" + notAsked);

        String noRows = behaviorBlock(human(), "sift");
        assertTrue(noRows.contains("branch      not measured (no row names this behavior)"),
                () -> "and what this behavior is short of is:\n" + noRows);
    }

    private static String humanAt(Adequacy.Level level) {
        return AdequacyReport.of(compiledAt(level)).human(SourceNameResolver.identity());
    }

    private static Map<String, Adequacy.BranchEvidence> branchesAt(Adequacy.Level level) {
        return compiledAt(level).db().ask(new Adequacy.BranchCoverage("example.repro")).value();
    }

    private static Compilation compiledAt(Adequacy.Level level) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.reportOnly(level));
        compilation.answerEverything();
        return compilation;
    }

    /** A line an invariant drew is met by writing the value, so it is never waiting on the arms.
     * The two origins are measured along separate paths for exactly this reason. */
    @Test
    void anInvariantsLineIsNeverWaitingOnTheArms() {
        List<BorderAssessment.Point> lines =
                BorderAssessment.pointsOf(lines().get("rated")).stream()
                        .filter(p -> p.owed() != null).toList();
        assertFalse(lines.isEmpty(), "the invariant draws two");
        for (BorderAssessment.Point line : lines) {
            assertEquals(ItemAssessment.Coverage.NotAsked.NO_ROWS, line.item().weakeningSource().why(),
                    line.border().origin().saidWithoutAPlace() + " at " + line.asked());
        }
    }

    /**
     * A source that could not be evaluated, holding the only rows one behavior has.
     *
     * <p>The attached file redeclares a name the module already declares, so it does not evaluate.
     * `take` keeps the row written beside it; `elsewhere` has every row it owns in there, and what
     * comes back for it is an empty list of rows and a reason saying the list is not the model's.
     */
    private static final List<String> UNSEEN = List.of("""
            module example.unseen

            data Amount = Int
                invariant value >= 0 && value <= 1000

            data Yes
            data No
            data Flag = Yes | No

            data Draft = { cost: Amount, flag: Flag }
            data Ok = { n: Int }

            let shared = Draft { cost = Amount(7), flag = Yes }

            behavior take : (request: Draft) -> Ok
                constructs Ok
            let take (request) = match request.flag with
                | Yes -> Ok { n = request.cost.value }
                | No -> Ok { n = 0 }

            behavior elsewhere : (request: Draft) -> Ok
                constructs Ok
            let elsewhere (request) = {
                guard request.cost.value <= 100 else Ok { n = 0 }
                Ok { n = request.cost.value }
            }

            example take
                | (Draft { cost = Amount(7), flag = Yes }) -> Ok { n = 7 }
            """, """
            examples for example.unseen

            let shared = Draft { cost = Amount(0), flag = No }

            example elsewhere
                | (Draft { cost = Amount(0), flag = No }) -> Ok { n = 0 }
            """);

    private static Compilation unseen() {
        Compilation compilation = Compilation.ofSources(UNSEEN,
                souther.compiler.meta.ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    /**
     * No rows to read and no rows are two different things.
     *
     * <p>A behavior nobody wrote a row for is not being asked. A behavior whose rows are in a source
     * that would not evaluate is being asked and cannot be answered, and the rows it is waiting on may
     * already be written. Calling the second `no_rows` tells an author to write what is sitting in the
     * file the run could not read — which is the mistake this whole change is about, made once more
     * about a different pair of correlated states.
     */
    @Test
    void rowsNothingCouldReadAreNotRowsThatWereNeverWritten() {
        Compilation compilation = unseen();
        AdequacyReport.BehaviorReport reported = AdequacyReport.of(compilation)
                .modules().get(0).behaviors().stream()
                .filter(b -> b.name().equals("elsewhere")).findFirst().orElseThrow();
        // And the count says so rather than saying zero. `0` is a behavior whose rows were read and
        // numbered none of them, which is the other half of the pair this test is about: written
        // as a number, "nothing was read" and "nothing was written" were the same byte.
        assertEquals(java.util.OptionalInt.empty(), reported.rowCount(), "nothing was read");
        assertTrue(AdequacyReport.of(compilation).modules().get(0).incompleteness().written().stream()
                        .anyMatch(gap -> gap.fact().code()
                                == Incompleteness.Code.OBSERVATION_ABSENT),
                "and there may well have been something to read");

        Adequacy.BranchEvidence branch = compilation.db()
                .ask(new Adequacy.BranchCoverage("example.unseen")).value().get("elsewhere");
        assertNotEquals(Adequacy.BranchEvidence.NotAsked.NO_ROWS, branch.measured().why(),
                "the rows this is waiting on may be the ones that went unread");
        assertEquals(MeasurementStatus.PARTIAL, AdequacyReport.statusOf(branch.measured()),
                "there are arms, and which of them were reached is undecided");

        List<BorderAssessment> read =
                Adequacy.readingsOf(compilation.db(), "example.unseen").get("elsewhere");
        assertFalse(read.isEmpty(), "the invariant and the guard draw lines");
        for (BorderAssessment.Point line : BorderAssessment.pointsOf(read)) {
            if (line.owed() == null) {
                continue;   // nothing was measured there and nothing was waiting on a row
            }
            assertNotEquals(ItemAssessment.Coverage.NotAsked.NO_ROWS, line.item().weakeningSource().why(),
                    line.border().origin().saidWithoutAPlace() + " at " + line.asked());
            assertEquals(MeasurementStatus.PARTIAL, AdequacyReport.statusOf(line.item().weakeningSource()),
                    line.border().origin().saidWithoutAPlace() + " at " + line.asked());
        }
    }

    /** The behavior beside it, whose own row was read, still answers from what was read. */
    @Test
    void aBehaviorWhoseRowsWereReadIsUnaffectedByTheSourceThatWasNot() {
        Adequacy.BranchEvidence branch = unseen().db()
                .ask(new Adequacy.BranchCoverage("example.unseen")).value().get("take");
        assertNull(branch.measured().why(), "this one was measured");
    }

    /**
     * A measure answers with a number, or with why it has none — and either way with what it went
     * without.
     *
     * <p>Five arms across two types and no way to build a sixth: whether there is a question here
     * is {@code Measure}'s and how far asking it got is {@code Measurement}'s. It used to be a
     * status and a reason held beside each other, checked where the value was built because either
     * could be written without the other; the arms carry what they need and there is nothing left
     * to check. What this holds is that the arms mean what they say, over every measure the model
     * produces rather than over the ones a test remembered.
     */
    @Test
    void everyMeasureAnswersWithANumberOrWithWhyItHasNone() {
        List<Object[]> measures = allMeasures();
        assertTrue(measures.size() > 20, "the model produces every kind: " + measures.size());
        for (Object[] measure : measures) {
            Measure<?> made = (Measure<?>) measure[1];
            String what = (String) measure[0];
            switch (made) {
                case Measure.NotApplicable<?> it -> {
                    assertNotNull(it.why(), what + " has no number and does not say why");
                    assertTrue(it.weakening().isEmpty(),
                            what + " has nothing to be about and went without something");
                }
                case Measurement.Complete<?> it -> {
                    assertNull(it.why(), what + " has a number and says why it has none");
                    assertTrue(it.weakening().isEmpty(),
                            what + " was made in full and went without something");
                }
                case Measurement.Partial<?> it -> {
                    assertNull(it.why(), what + " has a number and says why it has none");
                    assertFalse(it.weakening().isEmpty(),
                            what + " was made in part and does not say what by");
                }
                case Measurement.NotMeasured<?> it -> {
                    assertNotNull(it.why(), what + " has no number and does not say why");
                    assertTrue(it.weakening().isEmpty(),
                            what + " was never started and went without something");
                }
                case Measurement.FailedToMeasure<?> it -> {
                    assertNotNull(it.why(), what + " has no number and does not say why");
                    assertFalse(it.weakening().isEmpty(),
                            what + " could not be finished and does not say what it went without");
                }
            }
        }
    }

    /**
     * What the verdict does with each kind.
     *
     * <p>The two are the whole reason for telling them apart. A measure nothing was ever going to be
     * measured at is not a doubt anybody can act on; a measure that could have found a gap and was
     * not made is exactly one. Asked of one model holding both, so that neither answer is the
     * accident of a fixture with only one kind in it.
     */
    @Test
    void aMeasureThatWasNotMadeHoldsTheVerdictOpenAndAnInapplicableOneDoesNot() {
        AdequacyReport report = AdequacyReport.of(compiled());
        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED, report.adequacy(),
                report.human(SourceNameResolver.identity()));

        List<Object[]> measures = allMeasures();
        assertTrue(measures.stream().anyMatch(m -> m[1] instanceof Measure.NotApplicable<?>),
                "the model holds an inapplicable measure");
        assertTrue(measures.stream().anyMatch(m -> m[1] instanceof Measurement.NotMeasured<?>),
                "and one nobody made");

        // Every behavior of the model whose signature does not apply still reads `satisfied` where
        // the measures that were asked came to an answer — the inapplicable ones are not what holds
        // this open. `classify` is the one whose positions were divided and whose rows were read.
        String classify = behaviorBlock(human(), "classify");
        assertTrue(classify.contains("not applicable"), classify);
        assertTrue(classify.contains("branch      not applicable (this body owes no arm)"),
                classify);
    }

    /** Every measure the model produces, as (what it is, what it came to). */
    private static List<Object[]> allMeasures() {
        List<Object[]> measures = new ArrayList<>();
        // The reading of each behavior's rows, which is a measure like the ones counted over them.
        // This is a list somebody has to remember to add to, and leaving the newest measure out of
        // it is the shape of the defect that made it a measure at all (issue #996).
        for (Map.Entry<String, Adequacy.RowReading> each : readings().entrySet()) {
            measures.add(new Object[] {"rows " + each.getKey(), each.getValue().measured()});
        }
        for (Map.Entry<String, Adequacy.BranchEvidence> each : branches().entrySet()) {
            measures.add(new Object[] {"branch " + each.getKey(),
                    each.getValue().measured()});
        }
        for (Map.Entry<String, Adequacy.SignatureEvidence> each : signatures().entrySet()) {
            measures.add(new Object[] {"signature " + each.getKey(), each.getValue().counted()});
            measures.add(new Object[] {"out " + each.getKey(), each.getValue().output().cases()});
            // The positions are a measure of their own: how many there are is read off the
            // boundary, so a behavior whose boundary did not work out has a count nobody could
            // arrive at.
            measures.add(new Object[] {"positions " + each.getKey(), each.getValue().inputs()});
            each.getValue().inputs().made().ifPresent(at -> at.forEach(in -> measures.add(
                    new Object[] {"in " + each.getKey(), in.cases()})));
        }
        for (Map.Entry<String, PartitionEvidence> each : partitions().entrySet()) {
            PartitionEvidence partition = each.getValue();
            measures.add(new Object[] {"partition " + each.getKey(),
                    partition.partitioned()});
            measures.add(new Object[] {"boundary " + each.getKey(),
                    boundaryReadings().get(each.getKey())});
            measures.add(new Object[] {"pairs " + each.getKey(),
                    partition.pairs().counted()});
            partition.axes().forEach(a -> measures.add(
                    new Object[] {"axis " + each.getKey(), a.reached()}));
            BorderAssessment.pointsOf(lines().get(each.getKey())).stream()
                    .filter(p -> p.owed() != null)
                    .forEach(p -> measures.add(new Object[] {"line " + each.getKey(),
                            p.item().weakeningSource()}));
        }
        return measures;
    }

    /**
     * What is left to check, now that the type says the rest.
     *
     * <p>Most of what this used to assert is gone because it cannot be written. A status paired with
     * the wrong reason, a measure with a number and a reason beside it, a measure with no number and
     * none — each was a value somebody could build and a constructor had to refuse. The five arms
     * carry what they need and nothing else, so there is no such value to refuse.
     *
     * <p>Two things are still a caller's to get wrong, and both are about a measurement that says it
     * is weaker than complete. Saying so and carrying nothing is the whole of issue #953 in one
     * value, and it stays refused where it is built.
     */
    @Test
    void aMeasurementWeakerThanCompleteSaysWhatMadeItSo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Measurement.Partial<>("something", WeakeningSet.none()));
        assertThrows(IllegalArgumentException.class,
                () -> new Measurement.Partial<>("something", null));
        assertThrows(IllegalArgumentException.class,
                () -> new Measurement.FailedToMeasure<>(
                        Adequacy.BranchEvidence.Unreadable.UNREADABLE, WeakeningSet.none()));
        assertThrows(IllegalArgumentException.class,
                () -> new Measurement.FailedToMeasure<>(
                        Adequacy.BranchEvidence.Unreadable.UNREADABLE, null));

        // And an absence that nothing proved. The proof is the argument, so this is the whole of
        // what "the model divides nothing anywhere" costs to say — moved onto the reason when the
        // arms became a measurement's, and not dropped.
        assertThrows(NullPointerException.class,
                () -> new souther.compiler.query.PartitionDerivation.NothingIsDivided(null));
        assertThrows(NullPointerException.class,
                () -> new souther.compiler.query.BoundaryDerivation.NoRuleDrawsALine(null));

        // A measure with no number holds no number, rather than holding zeroes that read as one.
        assertThrows(NullPointerException.class, () -> new Measurement.NotMeasured<>(null));
        assertThrows(NullPointerException.class, () -> new Measure.NotApplicable<>(null));
        assertThrows(NullPointerException.class, () -> new Measurement.Complete<>(null));
    }

    /**
     * A weakening set is a set: what a parent went without does not depend on how many paths a fact
     * reached it by, nor on the order the readers found things in.
     *
     * <p>Held because this is the whole of the arithmetic. Every level above a measure is the union
     * of what its parts went without, and a union that counted paths would report one rule this
     * compiler could not read once per position it bears on.
     */
    @Test
    void whatAMeasurementWentWithoutIsASetAndUnionsLikeOne() {
        Weakening a = new Weakening.ProofContradicted(new CoverageSites.Obligation("take",
                new souther.compiler.types.SourceConstructOrigin(
                        new WrittenOwner.Body("m", "take"), 0, 0,
                        souther.compiler.types.SourceConstruct.IF),
                1, new DecidedBy.NotSaid()));
        Weakening b = new Weakening.ArmsUnsettled(
                new souther.compiler.types.SourceConstructOrigin(new WrittenOwner.Body("m", "b"), 0, 0,
                        souther.compiler.types.SourceConstruct.IF));
        Weakening c = new Weakening.OutputCasesUnreadable("take");

        assertEquals(WeakeningSet.of(a), WeakeningSet.of(a).union(WeakeningSet.none()));
        assertEquals(WeakeningSet.of(a), WeakeningSet.none().union(WeakeningSet.of(a)));
        assertEquals(WeakeningSet.of(a), WeakeningSet.of(a).union(WeakeningSet.of(a)));
        assertEquals(WeakeningSet.of(a, b), WeakeningSet.of(b, a),
                "two sets holding the same facts are one value whatever order they were found in");
        assertEquals(WeakeningSet.of(a).union(WeakeningSet.of(b)),
                WeakeningSet.of(b).union(WeakeningSet.of(a)));
        assertEquals(WeakeningSet.of(a).union(WeakeningSet.of(b)).union(WeakeningSet.of(c)),
                WeakeningSet.of(a).union(WeakeningSet.of(b).union(WeakeningSet.of(c))));
        assertEquals(WeakeningSet.of(a, b).hashCode(), WeakeningSet.of(b, a).hashCode(),
                "equal sets hash alike, or a Db answer never equals its own recomputation");
    }

    private static String behaviorBlock(String human, String behavior) {
        List<String> kept = new ArrayList<>();
        boolean inside = false;
        for (String line : human.lines().toList()) {
            if (line.startsWith("  ") && !line.startsWith("    ") && !line.startsWith("      ")) {
                inside = line.trim().startsWith(behavior + " ");
            }
            if (inside) {
                kept.add(line);
            }
        }
        assertFalse(kept.isEmpty(), behavior + " is in the report");
        return String.join("\n", kept);
    }

    private static Map<String, Adequacy.RowReading> readings() {
        Compilation compilation = compiled();
        return compilation.db()
                .ask(new Adequacy.RowReadings(compilation.modules().get(0))).value();
    }

    private static Map<String, Adequacy.BranchEvidence> branches() {
        Compilation compilation = compiled();
        return compilation.db()
                .ask(new Adequacy.BranchCoverage(compilation.modules().get(0))).value();
    }

    private static Map<String, PartitionEvidence> partitions() {
        Compilation compilation = compiled();
        return compilation.db().ask(new Adequacy.Coverage(compilation.modules().get(0))).value();
    }

    /** The lines each behavior's positions met, whosever the row at each point is. */
    private static Map<String, List<BorderAssessment>> lines() {
        Compilation compilation = compiled();
        return Adequacy.readingsOf(compilation.db(), compilation.modules().get(0));
    }

    /** How far the reading that found each behavior's lines got. */
    private static Map<String, souther.compiler.query.Measure<List<BorderAssessment>>>
            boundaryReadings() {
        Compilation compilation = compiled();
        return compilation.db()
                .ask(new Adequacy.BoundaryReadings(compilation.modules().get(0))).value();
    }

    private static List<Adequacy.Finding> findings(String behavior, Adequacy.Kind kind) {
        Compilation compilation = compiled();
        List<Adequacy.Finding> all = compilation.db()
                .ask(new Adequacy.Findings(compilation.modules().get(0))).value();
        return all.stream().filter(f -> f.subject().isBehavior(behavior))
                .filter(f -> f.kind() == kind).toList();
    }

    private static Map<String, Adequacy.SignatureEvidence> signatures() {
        Compilation compilation = compiled();
        return compilation.db().ask(new Adequacy.Witnesses(compilation.modules().get(0))).value();
    }
}
