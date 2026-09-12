package souther.cli;


import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.BorderAssessment;
import souther.compiler.report.AdequacyReport;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code souther examples --strict} refuses.
 *
 * <p>It used to refuse a row waiting for a {@code let}, which is the one thing the specification says
 * is not a defect: an injected behavior's recorded row is the record of what that behavior owes, and a
 * model being migrated onto starts with nothing else. So the flag failed on the practice the
 * specification recommends and passed on the gap the same report had just printed.
 *
 * <p>These hold the flag to the other question. What it refuses is a gap an asked measure came to an
 * answer about, and how many rows are waiting has no bearing on it in either direction.
 */
class WhatStrictRefusesIsWhatTheRowsDoNotCoverTest {

    /** An injected behavior with a row recorded against it, and nothing a measure can fault. The
     *  boundary its invariant draws is the one thing to reach, and a row is on it. */
    private static final String ONLY_WAITING = """
            module example.rate

            data RiskScore = Int
                invariant value >= 0

            data Amount = Int
                invariant value >= 0

            behavior baseRate : (score: RiskScore) -> Amount

            example baseRate
                | "mid"      : (RiskScore(50)) -> Amount(10)
                | "score = 0" : (RiskScore(0)) -> Amount(0)
            """;

    /** The same model with the boundary row taken away: one gap, and the rows that remain are still
     *  waiting for the same {@code let}. */
    private static final String WAITING_AND_UNCOVERED = """
            module example.rate

            data RiskScore = Int
                invariant value >= 0

            data Amount = Int
                invariant value >= 0

            behavior baseRate : (score: RiskScore) -> Amount

            example baseRate
                | "mid" : (RiskScore(50)) -> Amount(10)
            """;

    /** A behavior with a body, an arm no row goes through, and no row waiting for anything. */
    private static final String UNCOVERED_ONLY = """
            module example.rate

            data Amount = Int
                invariant value >= 0

            data Charged = { cost: Amount }
            data Refused = { reason: String }

            behavior submit : (cost: Amount) -> Charged | Refused
                constructs Charged, Refused

            let submit (cost) = {
                guard cost.value <= 100 else Refused { reason = "over" }
                Charged { cost = cost }
            }

            example submit
                | "within" : (Amount(50)) -> Charged
            """;

    /**
     * One class covered of a position's two, and nothing else the report can find.
     *
     * <p>A {@code Bool} rather than a sum, because a sum's case and its class are two findings the
     * same row closes and this model is here to move one of them on its own. Nothing draws a line,
     * so no border owes a point; the output is one data, so no case of it goes unexpected; the body
     * forks nowhere, so no arm goes unreached. What is left is the class.
     */
    private static final String ONE_CLASS_OF_TWO = """
            module example.rate

            data Charged = { cost: Int }

            behavior submit : (flag: Bool, cost: Int) -> Charged
                constructs Charged

            let submit (flag, cost) = Charged { cost = cost }

            example submit
                | "set" : (true, 1) -> Charged { cost = 1 }
            """;

    @Test
    void aRowWaitingForALetIsNotWhatStrictRefuses() throws Exception {
        Run run = examples(ONLY_WAITING, "--strict");

        assertEquals(0, run.code(), run.out() + run.err());
        assertTrue(run.out().contains("2 rows waiting for a `let`."), run.out());
        assertTrue(run.out().contains("adequacy: satisfied"), run.out());
    }

    @Test
    void aGapTheReportPrintsIsWhatStrictRefuses() throws Exception {
        Run run = examples(UNCOVERED_ONLY, "--strict");

        assertEquals(1, run.code(), run.out() + run.err());
        assertTrue(run.out().contains("0 rows waiting for a `let`."), run.out());
        assertTrue(run.out().contains("adequacy: not satisfied"), run.out());
        assertTrue(run.err().contains("the rows do not cover the model"), run.err());
    }

    /**
     * The refusal points at the entries in the terms of the report the reader was given.
     *
     * <p>A count is worth something beside a way of finding what it counts, and the two surfaces name
     * the same findings differently: a person reads a mark and a consumer reads a field. Naming the
     * mark whatever was printed sent a reader of a JSON document looking for a character that is
     * nowhere in it.
     */
    @Test
    void theRefusalNamesTheFindingsTheWayTheReportItPrintedDoes() throws Exception {
        Run human = examples(UNCOVERED_ONLY, "--strict");
        Run json = examples(UNCOVERED_ONLY, "--strict", "--format", "json");

        assertEquals(1, json.code(), json.out() + json.err());
        assertTrue(human.err().contains("marked `!` above"), human.err());
        assertTrue(json.err().contains("`disposition: refused`"), json.err());
        assertFalse(json.err().contains("`!`"), json.err());
        // The word the document it just printed says it under.
        assertTrue(json.out().contains("\"disposition\" : \"refused\""), json.out());
        assertFalse(json.out().contains("!"), "a JSON document carries no mark: " + json.out());
    }

    /**
     * The round trip the report used to lose.
     *
     * <p>{@code --generate} proposes a row for the boundary nothing sits on. Answering it and
     * pasting it back is what the generated block says to do, and it raises the number of rows
     * waiting for a {@code let} by one — which is the number this used to fail on. What it does to
     * the question being asked is the opposite: the gap is gone.
     */
    @Test
    void generatedBoundaryRowCanImproveStrictAdequacyEvenWhilePendingRowsIncrease() throws Exception {
        Run before = examples(WAITING_AND_UNCOVERED, "--generate", "--strict");

        assertEquals(1, before.code(), before.out() + before.err());
        assertTrue(before.out().contains("no row is at the ON point value = 0"), before.out());
        assertTrue(before.out().contains("1 row waiting for a `let`."), before.out());
        // The row the block proposes, which is what the pasted model below answers.
        assertTrue(before.out().contains("| (RiskScore(0)) -> <?>"), before.out());

        Run after = examples(ONLY_WAITING, "--strict");

        assertEquals(0, after.code(), after.out() + after.err());
        assertTrue(after.out().contains("2 rows waiting for a `let`."), after.out());
        assertFalse(after.out().contains("no row is at"), after.out());
    }

    /**
     * The two gates answer the same question about the same model.
     *
     * <p>Held on a model whose only warnings are the adequacy ones, because {@code --warnings error}
     * refuses a build that warns at all. Where they disagree, one of them is reading the evidence a
     * second time.
     *
     * <p>Against the build that measured as much. {@code souther examples} measures everything, so
     * the compile this is put beside is {@code --adequacy all}; what each is held to is the same
     * account, so the two agree by being two readings of one answer rather than by accident.
     */
    @Test
    void strictAndAWarningsErrorBuildAgreeWhereNothingElseWarns() throws Exception {
        for (String model : List.of(ONLY_WAITING, WAITING_AND_UNCOVERED, UNCOVERED_ONLY,
                ON_THE_LINE_ONLY)) {
            Path file = sourceOf(model);
            Path out = Files.createTempDirectory("souther-strict-out");

            Run examples = cli("examples", file.toString(), "--strict");
            Run compile = cli("compile", file.toString(), "-d", out.toString(),
                    "--adequacy", "all", "--warnings", "error");

            assertEquals(examples.code() != 0, compile.code() != 0,
                    model + "\n--- examples ---\n" + examples.err()
                            + "\n--- compile ---\n" + compile.err());
        }
    }

    /**
     * A model covered against the lines and at neither point away from them.
     *
     * <p>The model the two surfaces used to disagree about: the rows on the line and one step over
     * are there, and the two points away from it have none.
     */
    private static final String ON_THE_LINE_ONLY = """
            module example.limit

            data Ok
            data TooHigh

            behavior grade : (score: Int) -> Ok | TooHigh

            let grade (score) = {
                guard score <= 100 else TooHigh
                Ok
            }

            example grade
                | "on the line" : (100) -> Ok
                | "a step over" : (101) -> TooHigh
            """;

    /**
     * The command that reports and the build that refuses say the same thing about one model.
     *
     * <p>What this was. {@code souther examples} was measuring every point of every border and
     * being held to a build's default criterion, so it printed the two points away from the line and
     * then called the model satisfied — while a compile that named the other bar refused the same
     * model over the same two points. A CI running both was told the model was covered and that the
     * build was refused.
     */
    @Test
    void strictRefusesThePointsAwayFromALineTheWayABuildDoes() throws Exception {
        Path file = sourceOf(ON_THE_LINE_ONLY);

        Run examples = cli("examples", file.toString(), "--strict");
        Run compiled = cli("compile", file.toString(),
                "-d", Files.createTempDirectory("souther-strict-points").toString(),
                "--adequacy", "all", "--warnings", "error");

        assertEquals(1, examples.code(), examples.out() + examples.err());
        assertTrue(examples.out().contains("adequacy: not satisfied"), examples.out());
        assertTrue(examples.out().contains("! no row is at an IN point"), examples.out());
        assertTrue(examples.out().contains("! no row is at an OUT point"), examples.out());
        assertEquals(1, compiled.code(), compiled.out() + compiled.err());
        assertTrue(compiled.err().contains("E1917"), compiled.err());
    }

    /**
     * {@code --strict} decides an exit status and says nothing about the model.
     *
     * <p>What a reader is shown is one report, whether or not the run was asked to fail on it. A flag
     * that moved the bar would answer one model two ways from one command, and a reader comparing a
     * strict run's output against an earlier one would read the difference as the rows having
     * changed.
     */
    @Test
    void theReportIsWhatItIsWhetherOrNotTheRunWasAskedToBeStrict() throws Exception {
        Path file = sourceOf(ON_THE_LINE_ONLY);

        Run lenient = cli("examples", file.toString());
        Run strict = cli("examples", file.toString(), "--strict");

        assertEquals(lenient.out(), strict.out());
        assertEquals(0, lenient.code(), lenient.err());
        assertEquals(1, strict.code(), strict.err());
    }

    /**
     * Every kind about an obligation has something to be told under.
     *
     * <p>What the account owes and what a kind carries are written out separately so that neither
     * is read off the other. A kind a build can be refused over and nobody gave a code to would be
     * a gap a report prints and a build is never told about.
     */
    @Test
    void everyKindAboutAnObligationHasADiagnosticCode() {
        for (Adequacy.Kind kind : Adequacy.Kind.values()) {
            if (kind.isAboutAnObligation()) {
                assertTrue(kind.code().isPresent(), kind + " is about an obligation");
            }
        }
    }

    /**
     * And it is told as a warning, which is the other half of being able to refuse over it.
     *
     * <p>Whether a code is reported as an error or a warning is a set written by hand a package
     * away, and nothing tied it to this. A gap a build refuses over that is not among them is raised
     * as an error out of the measure that found it, which is not a compile error and is not the
     * warning `--warnings` decides about — the state E1917 was in until it was noticed by running
     * the command.
     */
    @Test
    void everyKindAboutAnObligationIsToldAsAWarning() {
        for (Adequacy.Kind kind : Adequacy.Kind.values()) {
            if (!kind.isAboutAnObligation()) {
                continue;
            }
            assertEquals(souther.compiler.diag.Severity.WARNING,
                    kind.code().orElseThrow().severity(),
                    kind + " is about an obligation, so its code is one a build is"
                            + " warned about rather than one a compile fails on");
        }
    }

    /**
     * A build that measured nothing reaches no verdict about what the account asks for.
     *
     * <p>A build that reads no rows is not held open on account of the rows themselves — nobody
     * asked for them, and holding it open there would make every such build undetermined about
     * every model, which is the answer #955 took out. So what holds it open has to be the measures
     * the account asks for, each of which says on its own account that it was not made.
     *
     * <p>Written because the exclusion is easy to state and easy to get wrong. Without this, "the
     * reading does not hold the verdict open where nobody asked for it" rests on the measures over
     * those rows all saying so — which is true and which nothing checked (issue #996).
     */
    @Test
    void aBuildThatMeasuredNothingIsNotSatisfied() {
        Compilation compilation = Compilation.ofSource(ONE_CLASS_OF_TWO, "Main");
        compilation.measure(new Adequacy.Asked(Adequacy.Level.OFF, false));
        compilation.answerEverything();
        AdequacyReport report = AdequacyReport.of(compilation);

        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED, report.adequacy(),
                () -> "the account asks what the rows reach of this position and nothing read"
                        + " them: " + report.human(SourceRendering.namedByIdentity(compilation.texts())));
    }

    /**
     * A body that forks nowhere owes no arm, and the arm measure does not hold its verdict open.
     *
     * <p>The measure is inapplicable rather than unmeasured, and the difference is the whole of what
     * a verdict does with it: counted as unmeasured, every implemented behavior without a fork in it
     * would hold its model open for a measurement that would find nothing however it was made.
     *
     * <p>Settled at {@code all} and open at {@code witness}, and what holds it open there is the
     * decision rather than the arms: which rules the rows took is an instrumented run's answer, so
     * a level that does not ask for one has not asked where the rows went.
     */
    @Test
    void aBodyThatForksNowhereOwesNoArmAndIsSettledWhereItsRulesWereRead() {
        AdequacyReport measured = reportOf(A_BODY_THAT_FORKS_NOWHERE, Adequacy.Level.ALL);
        AdequacyReport witness = reportOf(A_BODY_THAT_FORKS_NOWHERE, Adequacy.Level.WITNESS);

        String human = measured.human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
        assertEquals(AdequacyReport.AdequacyStatus.SATISFIED, measured.adequacy(), human);
        assertTrue(human.contains("branch      not applicable (this body owes no arm)"), human);
        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED, witness.adequacy(),
                witness.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        assertTrue(witness.whatKeepsTheVerdictOpen().stream()
                        .allMatch(each -> each.toString().contains("measure=DECISION")),
                () -> "and the arms are not what holds it open: "
                        + witness.whatKeepsTheVerdictOpen());
    }

    /**
     * A model with an arm nobody measured is not adequate; it is undetermined.
     *
     * <p>Every case of the signature is covered and every line the rules draw without the arms is
     * met, so a verdict reading the signature alone called this satisfied — on the same page as a
     * border line saying eight of its points were not measured. What holds it open is the arm
     * measure and the guard's line saying they were not made, which is what they say (issue #955).
     */
    @Test
    void aModelWhoseArmsNobodyMeasuredIsNotCalledAdequate() {
        AdequacyReport witness = reportOf(AN_ARM_AND_A_COVERED_SIGNATURE, Adequacy.Level.WITNESS);

        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED, witness.adequacy(),
                witness.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        assertEquals(List.of(), witness.adequacyGaps().stream()
                        .filter(f -> f.kind() == Adequacy.Kind.ARM_UNREACHED).toList(),
                "and not by naming a gap in a measure nobody made");
        assertEquals(AdequacyReport.AdequacyStatus.NOT_SATISFIED,
                reportOf(AN_ARM_AND_A_COVERED_SIGNATURE, Adequacy.Level.ALL).adequacy());
    }

    /**
     * And a model that owes no arm is adequate at that level, not held open for the level's sake.
     *
     * <p>The other half of the one above, and the reason the answer is the measure's rather than the
     * level's. Nothing here is implemented, so no body owes an arm and no fork draws a line; the one
     * line the invariant draws has a row on it. Read off the level, every model would be
     * undetermined at {@code witness} whatever it said — which is what a rule written against the
     * kinds a model could owe came to.
     */
    @Test
    void aModelThatOwesNoArmIsAdequateAtWitness() {
        AdequacyReport witness = reportOf(ONLY_WAITING, Adequacy.Level.WITNESS);

        assertEquals(AdequacyReport.AdequacyStatus.SATISFIED, witness.adequacy(),
                witness.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
    }

    /** A line an invariant drew is measured wherever the rows ran, so a row missing at it is a gap
     *  at {@code witness} and says the same thing there as at {@code all}. */
    @Test
    void aLineThatNeedsNoArmIsAGapAtEveryLevelThatRanTheRows() {
        for (Adequacy.Level level : List.of(Adequacy.Level.WITNESS, Adequacy.Level.ALL)) {
            AdequacyReport report = reportOf(WAITING_AND_UNCOVERED, level);
            assertEquals(AdequacyReport.AdequacyStatus.NOT_SATISFIED, report.adequacy(),
                    report.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
            assertEquals(List.of(Adequacy.Kind.BOUNDARY_UNMET),
                    report.adequacyGaps().stream().map(Adequacy.Finding::kind).toList(),
                    "at " + level);
        }
    }

    /**
     * An arm nothing reaches is a gap only where the arms were asked about.
     *
     * <p>The same model and the same rows; what differs is the level. At {@code witness} the arms are
     * not measured and the unreached one is not among the gaps, at {@code all} it is. What was asked
     * for is not readable from the evidence — a measure nobody wanted leaves the same absence as one
     * that could not be made — so the report carries it.
     */
    @Test
    void anArmIsAGapOnlyWhereTheArmsWereAskedAbout() {
        AdequacyReport armsNotAsked = reportOf(UNCOVERED_ONLY, Adequacy.Level.WITNESS);
        AdequacyReport armsAsked = reportOf(UNCOVERED_ONLY, Adequacy.Level.ALL);

        assertTrue(armsNotAsked.adequacyGaps().stream()
                        .noneMatch(f -> f.kind() == Adequacy.Kind.ARM_UNREACHED),
                armsNotAsked.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        assertTrue(armsAsked.adequacyGaps().stream()
                        .anyMatch(f -> f.kind() == Adequacy.Kind.ARM_UNREACHED),
                armsAsked.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
    }

    /**
     * A measure that does not apply is not a measure that failed.
     *
     * <p>A {@code >->} composition is implemented and has no arms of its own — its stages have them.
     * Its branch evidence is unavailable for that reason and not because anything went wrong, and a
     * verdict that read the evidence back would put every model holding a composition permanently out
     * of reach of {@code satisfied}.
     */
    @Test
    void aCompositionHasNoArmsOfItsOwnAndDoesNotHoldTheVerdictOpen() {
        // At both levels, because what it owes is what it owes: read off the level, the answer at
        // `witness` would be that nobody measured arms it does not have. What the levels do differ
        // over is where the rows went, which the stages' own rules are measured by.
        for (Adequacy.Level level : List.of(Adequacy.Level.WITNESS, Adequacy.Level.ALL)) {
            AdequacyReport report = reportOf(COMPOSED, level);

            String human = report.human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
            assertFalse(human.contains("the arms were not measured"), human);
            assertTrue(report.whatKeepsTheVerdictOpen().stream()
                            .allMatch(each -> each.toString().contains("measure=DECISION")),
                    () -> "at " + level + ", nothing of the composition's own holds it open: "
                            + report.whatKeepsTheVerdictOpen());
        }
        assertEquals(AdequacyReport.AdequacyStatus.SATISFIED,
                reportOf(COMPOSED, Adequacy.Level.ALL).adequacy(),
                reportOf(COMPOSED, Adequacy.Level.ALL)
                        .human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
    }

    /**
     * A border measure short of something holds the verdict open, and one made in full does not.
     *
     * <p>The rows either side of a line nothing could ask about are unmeasured rather than adequate,
     * and a measure that answered everything it answers for has nothing left to hold a verdict for.
     * Both borders here are met, so what separates the two answers is the measure's own status and
     * nothing about the entries beside it.
     *
     * <p>Which is the whole of what a verdict reads. Written the other way round — a verdict working
     * out for itself what some entry beside the measure cost — it would be a second reading of a
     * question the measure has already answered, free to disagree with it.
     */
    @Test
    void aBorderMeasureShortOfSomethingHoldsTheVerdictOpen() {
        BorderAssessment met = AReportOfOneBorder.assessed(
                AReportOfOneBorder.aBorderAtTheEdgeOfItsDomain(), AReportOfOneBorder::hit);

        assertEquals(AdequacyReport.AdequacyStatus.SATISFIED,
                verdictOf(AReportOfOneBorder.measured(met)),
                "a border measure made in full");
        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED,
                verdictOf(AReportOfOneBorder.shortOfSomething(met)),
                "a border measure that was not made in full");
    }

    /**
     * A reading that did not run out leaves the classes it never found, and the verdict says so.
     *
     * <p>Two answers make a class gap: which positions there are to cover, and what the rows reached
     * of each. Read off the positions alone, a reading that produced none looks exactly like a
     * behavior with nothing to cover — so a build was satisfied by the classes nobody had derived
     * yet. The border measure beside it was made in full and says nothing about this one
     * (issue #968).
     */
    @Test
    void aPartitionReadingThatDidNotRunOutHoldsTheVerdictOpen() {
        BorderAssessment met = AReportOfOneBorder.assessed(
                AReportOfOneBorder.aBorderAtTheEdgeOfItsDomain(), AReportOfOneBorder::hit);
        souther.compiler.query.Measurement<List<BorderAssessment>> lines =
                AReportOfOneBorder.measured(met);

        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED,
                AReportOfOneBorder.verdictOf(lines,
                        AReportOfOneBorder.partitionThatDidNotRunOut()),
                "the classes this could not derive are classes nobody has covered");
        assertEquals(AdequacyReport.AdequacyStatus.SATISFIED,
                AReportOfOneBorder.verdictOf(lines),
                "and a reading that did derive them settles it");
    }

    private static AdequacyReport.AdequacyStatus verdictOf(
            souther.compiler.query.Measurement<List<BorderAssessment>> lines) {
        return AReportOfOneBorder.verdictOf(lines);
    }

    /** Two covered stages and the composition of them, which carries rows of its own. */
    private static final String COMPOSED = """
            module example.pipe

            data Amount = Int
                invariant value >= 0

            data Doubled = { of: Amount }
            data Tripled = { of: Amount }

            behavior twice : (cost: Amount) -> Doubled
                constructs Doubled

            let twice (cost) = Doubled { of = cost }

            behavior thrice : (d: Doubled) -> Tripled
                constructs Tripled

            let thrice (d) = Tripled { of = d.of }

            behavior both = twice >-> thrice

            example twice
                | "zero"     : (Amount(0)) -> Doubled { of = Amount(0) }
                | "positive" : (Amount(1)) -> Doubled { of = Amount(1) }

            example thrice
                | "zero"     : (Doubled { of = Amount(0) }) -> Tripled { of = Amount(0) }
                | "positive" : (Doubled { of = Amount(1) }) -> Tripled { of = Amount(1) }

            example both
                | "zero" : (Amount(0)) -> Tripled { of = Amount(0) }
            """;

    /**
     * A behavior with no rows is not a behavior with gaps.
     *
     * <p>Nothing was measured, so nothing was found, and calling that satisfied would let a model pass
     * by writing no rows at all. The verdict says undetermined and the flag lets it through, which is
     * what {@code --warnings error} does with the same model.
     */
    @Test
    void aModelNothingWasMeasuredOnIsUndetermined() throws Exception {
        String noRows = """
                module example.rate

                data RiskScore = Int
                    invariant value >= 0

                data Amount = Int
                    invariant value >= 0

                behavior baseRate : (score: RiskScore) -> Amount
                """;
        Run run = examples(noRows, "--strict");

        assertEquals(0, run.code(), run.out() + run.err());
        assertTrue(run.out().contains("adequacy: undetermined"), run.out());
        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED,
                reportOf(noRows, Adequacy.Level.ALL).adequacy());
    }

    /**
     * A report of one behavior answers about that behavior.
     *
     * <p>The verdict is worked out from what the report holds every time it is asked, so filtering
     * cannot leave a verdict about behaviors the reader cannot see. What was asked for is carried
     * through untouched: filtering changes what is shown, not what was measured.
     */
    @Test
    void aFilteredReportAnswersAboutWhatItShows() {
        AdequacyReport whole = reportOf(BOTH, Adequacy.Level.ALL);
        AdequacyReport covered = whole.only(null, "baseRate");
        AdequacyReport uncovered = whole.only(null, "submit");

        assertEquals(AdequacyReport.AdequacyStatus.NOT_SATISFIED, whole.adequacy(),
                whole.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        assertEquals(AdequacyReport.AdequacyStatus.SATISFIED, covered.adequacy(),
                covered.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        assertEquals(AdequacyReport.AdequacyStatus.NOT_SATISFIED, uncovered.adequacy(),
                uncovered.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
    }

    /** One module holding both of the models above, so that filtering has something to filter. */
    private static final String BOTH = """
            module example.rate

            data RiskScore = Int
                invariant value >= 0

            data Amount = Int
                invariant value >= 0

            data Charged = { cost: Amount }
            data Refused = { reason: String }

            behavior baseRate : (score: RiskScore) -> Amount

            behavior submit : (cost: Amount) -> Charged | Refused
                constructs Charged, Refused

            let submit (cost) = {
                guard cost.value <= 100 else Refused { reason = "over" }
                Charged { cost = cost }
            }

            example baseRate
                | "mid"       : (RiskScore(50)) -> Amount(10)
                | "score = 0" : (RiskScore(0)) -> Amount(0)

            example submit
                | "within" : (Amount(50)) -> Charged
            """;

    /** Every case of the signature covered, every arm not. The two guards leave an arm no row goes
     *  through, and nothing about the signature is short. */
    private static final String AN_ARM_AND_A_COVERED_SIGNATURE = """
            module example.rate

            data Amount = Int
                invariant value >= 0

            data Charged = { cost: Amount }
            data Refused = { reason: String }

            behavior submit : (cost: Amount) -> Charged | Refused
                constructs Charged, Refused

            let submit (cost) = {
                guard cost.value <= 100 else Refused { reason = "over" }
                guard cost.value >= 10 else Refused { reason = "under" }
                Charged { cost = cost }
            }

            example submit
                | "within" : (Amount(50)) -> Charged
                | "over"   : (Amount(101)) -> Refused
                | "zero"   : (Amount(0)) -> Refused
                | "under"  : (Amount(5)) -> Refused
            """;

    /** An implemented body that forks nowhere, with every measure it does owe covered: one row on
     *  the invariant's line and one inside it. */
    private static final String A_BODY_THAT_FORKS_NOWHERE = """
            module example.flat

            data Amount = Int
                invariant value >= 0 && value <= 10

            data Ok = { n: Amount }

            behavior keep : (a: Amount) -> Ok
                constructs Ok

            let keep (a) = Ok { n = a }

            example keep
                | "on"  : (Amount(0)) -> Ok { n = Amount(0) }
                | "in"  : (Amount(5)) -> Ok { n = Amount(5) }
                | "top" : (Amount(10)) -> Ok { n = Amount(10) }
            """;

    private static AdequacyReport reportOf(String source, Adequacy.Level level) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.reportOnly(level));
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }

    private record Run(int code, String out, String err) {}

    /**
     * A class no row is in is a row the model asks for, so the report marks it and the verdict is
     * not satisfied.
     *
     * <p>Which used to be the word a caller wrote. The measurement does not move either way — the
     * same class is named at the same position in the same words — and what the caller decided was
     * whether the model was held to it, so one model was covered and uncovered at once depending on
     * which run a reader looked at.
     */
    @Test
    void aClassNoRowIsInIsMarkedAndLeavesTheVerdictNotSatisfied() throws Exception {
        Run reported = examples(ONE_CLASS_OF_TWO);

        assertTrue(reported.out().contains("! no row is in `false` at flag"), reported.out());
        assertTrue(reported.out().contains("adequacy: not satisfied"), reported.out());
        assertEquals(0, reported.code(),
                "the account decides the verdict and `--strict` decides the exit");
    }

    /** The same finding on the other surface, refused under its own code. */
    @Test
    void aCompileRefusesTheSameFinding() throws Exception {
        Path file = sourceOf(ONE_CLASS_OF_TWO);

        Run compiled = cli("compile", file.toString(),
                "-d", Files.createTempDirectory("souther-classes").toString(),
                "--adequacy", "all", "--warnings", "error");

        assertEquals(1, compiled.code(), compiled.out() + compiled.err());
        assertTrue(compiled.err().contains("E1931"), compiled.err());
    }

    /**
     * The report command takes no {@code --adequacy}, because it has neither half of it to choose.
     *
     * <p>Its output is the report, so everything is measured; and what it marks is the account's.
     * Refused rather than ignored: a run told to measure nothing and handed a full report has been
     * answered a question it did not ask.
     */
    @Test
    void theReportCommandTakesNoAdequacyWord() throws Exception {
        Run measured = examples(ONE_CLASS_OF_TWO, "--adequacy", "off");

        assertEquals(2, measured.code(), measured.out() + measured.err());
        assertTrue(measured.err().contains("--adequacy"), measured.err());
    }

    private static Path sourceOf(String model) throws Exception {
        Path file = Files.createTempDirectory("souther-strict").resolve("rate.sou");
        Files.writeString(file, model);
        return file;
    }

    private static Run examples(String model, String... extraArgs) throws Exception {
        List<String> args = new ArrayList<>(List.of("examples", sourceOf(model).toString()));
        args.addAll(List.of(extraArgs));
        return cli(args.toArray(String[]::new));
    }

    private static Run cli(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        int code;
        try {
            code = Main.dispatch(args);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return new Run(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }
}
