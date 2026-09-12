package souther.cli;

import souther.compiler.report.AdequacyReport;
import souther.compiler.query.WeakeningSet;
import souther.cli.Main;
import org.junit.jupiter.api.Test;
import souther.compiler.coverage.ArmProbe;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.observe.MeasurementStatus;
import souther.compiler.check.PathReachability;
import souther.compiler.query.Adequacy;
import souther.compiler.query.ArmCensus;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Weakening;
import souther.compiler.reach.Reachability;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An arm the model's own rules prove nothing reaches is not a row anybody is owed.
 *
 * <p>The cap on {@code b} is written on the record, so {@code Count} alone says nothing about it and a
 * guard at 50 sits outside what any pair can hold. What a position is divided into and which lines are
 * owed a row already knew that; the arms did not, and a report asking for a row through an arm nothing
 * can reach is asking for work nobody can do.
 *
 * <p>Instrumented is not the same as owed. The probe on that arm stays, because a probe is the only
 * thing that could show the reachability wrong.
 */
class AnArmNothingReachesIsNotOwedARowTest {

    private static final String CAPPED = """
            module example.capped

            data Count = Int
                invariant lower = value >= 0

            data Pair =
                { a: Count
                , b: Count
                }
                invariant cap = b <= 10
                invariant ordered = a < b

            data Small
            data Big

            behavior classify : (pair: Pair) -> Small | Big

            let classify (pair) =
                if pair.b.value >= 50
                    then Big
                    else Small

            example classify
                | "small" : (Pair { a = Count(0), b = Count(1) }) -> Small
            """;

    private static String reportOn(String model) throws Exception {
        Path file = Files.createTempDirectory("souther-arms").resolve("model.sou");
        Files.writeString(file, model);
        PrintStream was = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            Main.main(new String[] {"examples", file.toString()});
        } finally {
            System.setOut(was);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * An arm for a case the position's own rules refuse is not an arm either.
     *
     * <p>The other half of the same fact, and the one that was missing: a guard's arm went when its
     * comparison could not come out that way, and a {@code match} arm stayed however the rules
     * narrowed what it matched on. The arm answers a value here, so nothing about the body says it
     * is not an arm — what says so is that no {@code Active} is ever {@code Off}.
     */
    private static final String NARROWED = """
            module example.narrowed

            data On
            data Off
            data Pending
            data Flag = On | Off | Pending
            data Active = Flag invariant value /= Off
            data Answer = Int

            behavior pick : (f: Active) -> Answer
                constructs Answer

            let pick (f) = match f.value with
                | On      -> Answer(1)
                | Pending -> Answer(0)
                | Off     -> Answer(9)

            example pick
                | "on" : (Active(On)) -> Answer(1)
            """;

    @Test
    void anArmForACaseTheRulesRefuseIsNotCounted() throws Exception {
        String report = reportOn(NARROWED);

        assertTrue(report.contains("branch      1/2"),
                () -> "the `Off` arm is one no value reaches:\n" + report);
        assertTrue(report.contains("no row goes through `case Pending`"),
                () -> "and the arm that is owed a row is still named:\n" + report);
        // Asked of what the lines say and not of whether the arm is spelled anywhere. The account
        // names it where it says no row is owed through it, which is the opposite of asking for
        // one; held as the arm going unmentioned, this would refuse the report for explaining
        // itself.
        assertEquals(List.of(), asksForARowAt(report, "case Off"),
                () -> "nothing asks for a row through the arm nothing reaches:\n" + report);
    }

    /**
     * The lines saying a row is missing somewhere that names {@code arm}.
     *
     * <p>The line and whatever it stands over. A block's entry says what is missing and the lines
     * under it say which construct, so a check reading either alone answers about half of what the
     * page says — an entry asking for a row at a rule names the rule under itself.
     */
    private static List<String> asksForARowAt(String report, String arm) {
        List<String> asking = new ArrayList<>();
        String entry = "";
        for (String line : report.split("\n")) {
            if (!line.startsWith("          ")) {
                entry = line;
            }
            if (!line.contains(arm) && !entry.contains(arm)) {
                continue;
            }
            if (entry.contains("no row goes through") || entry.contains("no row takes")
                    || entry.contains("undecided whether a row")
                    || entry.contains("nothing could show a row")) {
                asking.add(entry);
            }
        }
        return asking.stream().distinct().toList();
    }

    /** The control: the same arms with nothing refusing the case. Without this the assertion above
     *  would pass on a measure that had stopped counting `match` arms at all. */
    @Test
    void andIsCountedWhereNothingRefusesTheCase() throws Exception {
        String report = reportOn(NARROWED
                .replace("data Active = Flag invariant value /= Off", "data Active = Flag")
                .replace("(f: Active)", "(f: Active)"));

        assertTrue(report.contains("branch      1/3"),
                () -> "every arm is owed a row where the rules refuse nothing:\n" + report);
        assertTrue(report.contains("no row goes through `case Off`"),
                () -> "including the one for `Off`:\n" + report);
    }

    @Test
    void anArmBeyondTheRecordsCapIsNotCounted() throws Exception {
        String report = reportOn(CAPPED);

        assertTrue(report.contains("branch      1/1"),
                () -> "the `else` arm is the only one a pair can take:\n" + report);
        assertFalse(report.contains("no row goes through"),
                () -> "and it was taken:\n" + report);
    }

    /** The same guard one value lower, which a pair can hold. Without this the assertion above would
     * pass on a measure that had stopped counting arms at all. */
    @Test
    void anArmWithinItStillIs() throws Exception {
        String report = reportOn(CAPPED.replace("pair.b.value >= 50", "pair.b.value >= 5"));

        assertTrue(report.contains("branch      1/2"),
                () -> "a `b` of 5 is a value some pair holds, so both arms are owed:\n" + report);
        assertTrue(report.contains("no row goes through `then`"),
                () -> "and no row went through this one:\n" + report);
    }

    // --- the cases behind such an arm -------------------------------------------------------------

    /**
     * The rows at the cap and away from the lines, which the record's own clauses draw.
     *
     * <p>Written here and not into {@link #CAPPED}, because the arm tests below move the guard down
     * to 5 and a `b` of 10 would then reach the arm they are about. What is checked here is that
     * nothing is left over once the arms have been accounted for, and `b <= 10` is a bound on one
     * coordinate against a constant, so it is owed a row like any other (ADR-0090). The second row
     * sits well inside both bounds, which is the other pair of points a border owes: the report a
     * report command writes is held to reliable domain coverage, so a model with nothing left over
     * has a row away from each line as well as against it.
     */
    private static final String AT_THE_CAP = CAPPED
            + "    | \"at the cap\" : (Pair { a = Count(0), b = Count(10) }) -> Small\n"
            + "    | \"well within\" : (Pair { a = Count(5), b = Count(9) }) -> Small\n";

    @Test
    void aCaseOnlyThatArmProducesIsNotOwedEither() throws Exception {
        String report = reportOn(AT_THE_CAP);

        assertTrue(report.contains("out specified 1/1"),
                () -> "`Big` is answered only where nothing reaches:\n" + report);
        assertFalse(report.contains("no row expects `Big`"), () -> report);
        assertTrue(report.contains("adequacy: satisfied"),
                () -> "and everything left is covered:\n" + report);
    }

    /** The same case built somewhere a row can get to. One producer proving nothing about the others
     * is the whole reason this is a union over producers rather than a fact about the case. */
    @Test
    void aCaseAReachableArmAlsoProducesStaysOwed() throws Exception {
        String report = reportOn(CAPPED.replace("        else Small",
                "        else if pair.a.value >= 1 then Big else Small"));

        assertTrue(report.contains("no row expects `Big`"),
                () -> "an `a` of 1 is a value some pair holds:\n" + report);
    }

    /**
     * A producer this cannot read keeps every case owed.
     *
     * <p>The body answers with a name rather than with a value built in a tail position, and what that
     * name holds is not followed. That is the top of the analysis, and it is where anything unreadable
     * goes: taking a case away on a guess is how an author stops being asked for a row they could have
     * written.
     */
    @Test
    void aCaseWhoseProducerCannotBeReadStaysOwed() throws Exception {
        String report = reportOn(CAPPED.replace("""
                let classify (pair) =
                    if pair.b.value >= 50
                        then Big
                        else Small
                """, """
                let classify (pair) = {
                    let answer = if pair.b.value >= 50 then Big else Small
                    answer
                }
                """));

        assertTrue(report.contains("no row expects `Big`"),
                () -> "nothing here says what `answer` holds:\n" + report);
    }

    /**
     * A case nothing answers with is not the same as a case nothing can answer with.
     *
     * <p>What is taken away here is a case something does answer with, at a place the rules prove
     * nothing reaches. A case the body has no producer for stays: that a signature says {@code A | B}
     * and the implementation never answers `B` is a gap between the two, and it is the kind of gap
     * this measure is for.
     */
    @Test
    void onlyACaseSomethingAnswersWithBehindAProvenArmIsTakenAway() throws Exception {
        String model = """
                module example.threeways

                data N = Int
                    invariant cap = value <= 10

                data A
                data B

                behavior f : (n: N) -> A | B

                let f (n) = BODY

                example f
                    | "one" : (N(1)) -> A
                """;
        String dead = reportOn(model.replace("BODY", "if n.value > 100 then B else A")
                .replace("BUILDS", "A, B"));
        String none = reportOn(model.replace("BODY", "A").replace("BUILDS", "A"));
        String live = reportOn(model.replace("BODY", "if n.value > 5 then B else A")
                .replace("BUILDS", "A, B"));

        assertTrue(dead.contains("out specified 1/1"),
                () -> "`B` is answered only where nothing reaches:\n" + dead);
        assertTrue(none.contains("out specified 1/2"),
                () -> "nothing answers `B` here, which is a gap and not an impossibility:\n" + none);
        assertTrue(live.contains("out specified 1/2"),
                () -> "and here a row can take the arm that answers it:\n" + live);
    }

    /**
     * The cases left are named in the order they were declared in.
     *
     * <p>A report is read against the declaration it came from, so the list has to run the way that
     * declaration does. Taking a case out of the middle is where the order is easiest to lose: what
     * is left is a new set, and a set that keeps the values is not one that keeps their order.
     */
    @Test
    void whatIsLeftKeepsTheOrderItWasDeclaredIn() throws Exception {
        String report = reportOn("""
                module example.order

                data N = Int
                    invariant cap = value <= 10

                data Alpha
                data Beta
                data Gamma
                data Delta
                data Epsilon
                data Zeta
                data Kind = Alpha | Beta | Gamma | Delta | Epsilon | Zeta

                behavior f : (n: N) -> Kind

                let f (n) =
                    if n.value > 100 then Beta else Alpha

                example f
                    | "one" : (N(1)) -> Alpha
                """);

        assertTrue(report.contains("out specified 1/5"),
                () -> "`Beta` is answered only where nothing reaches:\n" + report);
        List<String> named = report.lines().map(String::trim)
                .filter(line -> line.contains("no row expects"))
                .map(line -> line.substring(line.indexOf('`') + 1, line.lastIndexOf('`')))
                .toList();
        assertEquals(List.of("Gamma", "Delta", "Epsilon", "Zeta"), named,
                () -> "the order `Kind` lists them in:\n" + report);
    }

    // --- what happens if the proof is wrong -------------------------------------------------------

    /**
     * The fork's two arms, read off the plan.
     *
     * <p>The plan's own sites and not sites built here to stand for them. A site says where a run
     * through the arm is recorded and which place that is, and the two are one answer the numbering
     * gave; assembled in a test, the pair would be whatever this test put together and the measures
     * below would agree with it by construction.
     */
    private static List<CoverageSites.ArmSite> armSites() {
        Compilation compilation = Compilation.ofSource(CAPPED, "Main");
        compilation.answerEverything();
        Bodies.Elaborated checked = compilation.db()
                .ask(new Bodies.Checked(compilation.modules().get(0))).value();
        return checked.plan().arms("classify");
    }

    private static final List<CoverageSites.ArmSite> ARMS = armSites();

    /** The arm nothing reaches — the {@code then} of a guard at 50 no pair can be above. */
    private static final CoverageSites.ArmSite UNREACHED_ARM = ARMS.get(0);

    /** The arm every run takes. */
    private static final CoverageSites.ArmSite TAKEN_ARM = ARMS.get(1);

    private static final ArmProbe UNREACHED = UNREACHED_ARM.index();

    private static final ArmProbe TAKEN = TAKEN_ARM.index();

    /**
     * The model's own reachability, which proves arm 0 unreachable: nothing at or above 50 is a
     * value any pair holds.
     *
     * <p>Read off the model rather than assembled here. A proof written by hand is one this test
     * agrees with by construction, and what the rows below are about is what happens when the proof
     * the compiler made turns out to be wrong.
     */
    private static PathReachability.Answers proving() {
        Compilation compilation = Compilation.ofSource(CAPPED, "Main");
        compilation.answerEverything();
        return compilation.db()
                .ask(new Adequacy.PathReached(compilation.modules().get(0))).value()
                .get("classify");
    }

    @Test
    void aProvenArmLeavesTheDenominator() {
        Adequacy.BranchEvidence measured = Adequacy.BranchEvidence.measured("b",
                List.of(UNREACHED_ARM, TAKEN_ARM), Set.of(TAKEN), Set.of(),
                proving().asRunWith(Set.of(TAKEN)), WeakeningSet.none());

        assertEquals(List.of(TAKEN), probesOf(measured));
        assertEquals(1, measured.arms().covered());
        assertTrue(measured.arms().census().settled(),
                "nothing has shown the arms it is counted out of to be short of one");
        assertEquals(List.of(), measured.arms().unmet());
    }

    /** The probes of the arms this account holds, in the order the body holds them. */
    private static List<ArmProbe> probesOf(Adequacy.BranchEvidence measured) {
        return measured.arms().all().stream()
                .flatMap(arm -> arm.occurrences().stream())
                .map(CoverageSites.ArmSite::index).toList();
    }

    /**
     * A row through an arm nothing was supposed to reach.
     *
     * <p>Then the model is fine and the proof is not. The arm is back in the denominator before this
     * measure sees it, and what is left to do here is to refuse to report a complete measurement over
     * a proof already known to be wrong.
     */
    @Test
    void anArmObservedAgainstTheProofIsKeptAndSaidSo() {
        // The rows lit both arms, one of which nothing was supposed to reach. Handed to the same
        // fold the measures read, so what a run does to a proof is decided in one place.
        PathReachability.Answers.AsRun asRun = proving().asRunWith(Set.of(UNREACHED, TAKEN));
        Adequacy.BranchEvidence measured = Adequacy.BranchEvidence.measured("b",
                List.of(UNREACHED_ARM, TAKEN_ARM), Set.of(UNREACHED, TAKEN), Set.of(), asRun,
                WeakeningSet.none());

        // What a disproved proof bears on is the set of arms and not any one of them. Which arms
        // there are is what the proofs decided, so a proof shown wrong leaves that set in doubt;
        // where each arm stands is what the rows said, and a row through an arm went through it
        // however wrong a proof about the arm beside it turned out to be.
        assertEquals(new ArmCensus.Undecided(
                        WeakeningSet.of(new Weakening.ProofContradicted(UNREACHED_ARM.obligation()))),
                measured.arms().census(),
                "the arm nothing reaches was proven unreachable and a row went through it");
        assertEquals(List.of(UNREACHED, TAKEN), probesOf(measured),
                "so it is still an arm this behavior has");
        assertEquals(2, measured.arms().covered());
        assertEquals(MeasurementStatus.PARTIAL,
                AdequacyReport.statusOf(measured.measured()),
                "and no number here is given as though nothing had happened");
        assertEquals(WeakeningSet.of(new Weakening.ProofContradicted(UNREACHED_ARM.obligation())),
                measured.measured().weakening(),
                "and the measurement says which proof a row went against");
    }

    /**
     * And the arms themselves are answered for as usual.
     *
     * <p>What the third of these says is the point. The rows here all ran, so an arm nothing lit is
     * an arm nothing reaches — and while the claim over the arms was the measurement's to make, this
     * one was withheld: the measurement was weakened by the contradicted proof, and the arms went
     * unnamed under a line that said a row had not been read. No row had gone unread.
     */
    @Test
    void andAnArmIsStillAnsweredForBesideADisprovedProof() {
        PathReachability.Answers.AsRun asRun = proving().asRunWith(Set.of(UNREACHED));
        Adequacy.BranchEvidence measured = Adequacy.BranchEvidence.measured("b",
                List.of(UNREACHED_ARM, TAKEN_ARM), Set.of(UNREACHED), Set.of(), asRun,
                WeakeningSet.none());

        assertEquals(1, measured.arms().covered(), "the row went through the arm it went through");
        assertEquals(List.of(TAKEN), measured.arms().unmet().stream()
                        .map(arm -> arm.display().index()).toList(),
                "and nothing goes through the other, which every row was read against");
        assertEquals(List.of(), measured.arms().undecided(),
                "a proof shown wrong is not a row nobody read");
    }

    /** The same fact both measures read. Taking the arm back for one of them and not the other is how
     * the case behind it would stay unowed over a proof already disproved. */
    @Test
    void theSameArmIsBackForEveryMeasure() {
        PathReachability.Answers.AsRun asRun = proving().asRunWith(Set.of(UNREACHED));

        assertEquals(Set.of(UNREACHED), asRun.provedWrong(),
                "a row went through an arm this reading had proven nothing reaches");
        assertFalse(asRun.answers().at(UNREACHED_ARM.place())
                        instanceof Reachability.Unreachable,
                "so nothing about it is proven any more");
        // Both measures read this one object, so what is back for one is back for the other. Said
        // of the arms: what a comparison's outcome was proven to be is not something a row through
        // an arm settles — a lit comparison says it ran, not which way it came out.
        assertTrue(asRun.answers().found().entrySet().stream()
                        .filter(each -> each.getKey()
                                instanceof souther.compiler.coverage.ControlPlace.Arm)
                        .noneMatch(each -> each.getValue()
                                instanceof souther.compiler.reach.Reachability.Unreachable),
                "and what the signature reads is the same answer the arms are counted by");
    }
}
