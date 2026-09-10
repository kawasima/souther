package souther.compiler;

import souther.compiler.source.SourceId;

import souther.compiler.execute.jvm.JvmExampleDeadlines;
import org.junit.jupiter.api.Test;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.Incompleteness;
import souther.compiler.publish.PublishedIncompleteness;
import souther.compiler.observe.MeasurementStatus;
import souther.compiler.query.About;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Db;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;
import souther.compiler.report.GeneratedRows;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What is said when some of what a row wrote could not be read.
 *
 * <p>The whole feature rests on three answers where two would fit: met, unmet, and *not decided*. The
 * measures each knew that, and the aggregate in front of them did not — it handed on the rows and
 * dropped the reasons the rest were missing, so every measure downstream read "what was observed" as
 * "what there was". These hold each of them to the third answer.
 */
class CompilePartialAdequacyTest {

    /** A row that never comes back: the helper loops, so its evaluation is stopped rather than
     * finished, and what it went through on the way goes with it. */
    private static final String TIMES_OUT = """
            module example.loop

            data Draft = { n: Int }
            data Done = { n: Int }
            data Small = { n: Int }

            partial let spin (n: Int): Int = spin(n)

            behavior go : (request: Draft) -> Done | Small
                constructs Done, Small

            let go (request) = {
                guard request.n <= 0 else Done { n = spin(request.n) }
                Small { n = request.n }
            }

            example go
                | (Draft { n = 1 }) -> Done { n = 1 }
            """;

    /**
     * Two rows of one behavior, one of which does not come back.
     *
     * <p>The row that finishes goes through the guard and comes out of it, so the arm it went
     * through is settled by a run nothing stopped; the row that loops was on its way through the
     * other arm when it was dropped, so that arm is what nobody can say. A model where nothing
     * comes back leaves every arm alike and cannot tell an account that answers per arm from one
     * that answers once for the measure.
     */
    private static final String ONE_ROW_OF_TWO_STOPS = """
            module example.mix

            data Amount = Int
                invariant value >= 0

            data Domestic
            data Overseas
            data Kind = Domestic | Overseas

            data Draft = { kind: Kind, cost: Amount }
            data Ok = { n: Int }
            data Big = { n: Int }

            partial let spin (n: Int): Int = spin(n)

            behavior take : (request: Draft) -> Ok | Big
                constructs Ok, Big

            let take (request) = {
                guard request.cost.value <= 100 else Big { n = spin(request.cost.value) }
                Ok { n = request.cost.value }
            }

            example take
                | "at the line" : (Draft { kind = Overseas, cost = Amount(100) }) -> Ok { n = 100 }
                | "over it"     : (Draft { kind = Domestic, cost = Amount(500) }) -> Big { n = 0 }
            """;

    /** {@link #ONE_ROW_OF_TWO_STOPS}, measured, with the row named "over it" the one that does not
     *  come back. */
    private static Compilation oneRowOfTwoStops() {
        return measured("mix", ONE_ROW_OF_TWO_STOPS,
                DoesNotComeBack.overrunningOn(
                        DoesNotComeBack.everythingAboutTheRowNamed("over it")),
                Adequacy.Asked.fullReport());
    }

    /**
     * An input whose observation is cut short before the position a rule is about.
     *
     * <p>The collection ahead of it spends the node budget, so `cost` is truncated — while the row
     * writes exactly the number the invariant's lower bound names.
     */
    private static String budgetSpent(String tail) {
        String groups = "someGroups(64)";
        return """
                module example.budget

                data Amount = Int
                    invariant value >= 0 && value <= 1000

                data Yes
                data No
                data Flag = Yes | No

                data Item = { a: String, b: String, c: String }
                data Group = { items: List<Item> }

                data Draft = { groups: List<Group>, cost: Amount, flag: Flag }
                data Ok = { n: Int }

                behavior take : (request: Draft) -> Ok
                    constructs Ok

                let take (request) = Ok { n = request.cost.value }


                let someItems (n: Int): List<Item> =
                    List.map({ (i) -> Item { a = "x", b = "x", c = "x" } }, List.rangeInclusive(1, n))

                let someGroups (n: Int): List<Group> =
                    List.map({ (i) -> Group { items = someItems(64) } }, List.rangeInclusive(1, n))

                example take
                    | (Draft { groups = %s, cost = Amount(0), flag = Yes }) -> Ok { n = 0 }
                """.formatted(groups) + tail;
    }

    /** Compiles are shared between the cases that read the same model, keyed by the budget as well as
     * the source: the two are what a compile here is, and a model read under one of them says nothing
     * about what it would say under the other. */
    private static final Map<String, Compilation> COMPILED = new java.util.LinkedHashMap<>();

    /** A model that comes back, on the default budget — including {@link #budgetSpent}, which walks
     * four thousand nodes and is the reason a short budget cannot be set for the whole suite. */
    private static Compilation measured(String source) {
        return measured(source, (JvmExampleDeadlines) null);
    }

    private static Compilation warned(String source) {
        return warned(source, null);
    }

    private static Compilation warned(String source, Duration budget) {
        return compiled("warn:", source, budget, Adequacy.Asked.warningsAt(Adequacy.Level.ALL));
    }

    private static Compilation compiled(String what, String source, Duration budget,
                                        Adequacy.Asked asked) {
        return COMPILED.computeIfAbsent(what + budget + ":" + source, _ -> {
            Compilation compilation = Compilation.ofSource(source, "Main");
            if (budget != null) {
                compilation.withExampleBudget(budget);
            }
            compilation.measure(asked);
            compilation.answerEverything();
            return compilation;
        });
    }

    /** Measured, with {@code overrun} the work this model does not get back from — for a model
     *  written out here, which is its own key. */
    private static Compilation measured(String source, JvmExampleDeadlines overrun) {
        return measured("report:", source, overrun, Adequacy.Asked.fullReport());
    }

    /** The same, for a model shared between tests, which needs a key of its own. */
    private static Compilation measured(String key, String source, JvmExampleDeadlines overrun) {
        return measured("report:" + key, source, overrun, Adequacy.Asked.fullReport());
    }

    /** The same, warned about at every level. */
    private static Compilation warned(String key, String source, JvmExampleDeadlines overrun) {
        return measured("warn:" + key, source, overrun,
                Adequacy.Asked.warningsAt(Adequacy.Level.ALL));
    }

    /**
     * Measured, with {@code overrun} the work this model does not get back from.
     *
     * <p>Said rather than timed. What these tests are about is what a measure makes of a row that did
     * not come back, and a model that loops plus a clock short enough to catch it also reports the
     * rows that were supposed to finish as rows that did not, on any host loaded enough to make it
     * so.
     */
    private static Compilation measured(String key, String source, JvmExampleDeadlines overrun,
                                        Adequacy.Asked asked) {
        return COMPILED.computeIfAbsent(key + ":" + source, _ -> {
            Compilation compilation = Compilation.ofSource(source, "Main");
            if (overrun != null) {
                compilation.withJvmExampleDeadlines(overrun);
            }
            compilation.measure(asked);
            compilation.answerEverything();
            return compilation;
        });
    }

    private static List<String> warningCodes(Compilation compilation) {
        List<String> codes = new ArrayList<>();
        for (Db.Found found : compilation.db().allReports()) {
            Diagnostic d = found.report().diagnostic();
            if (!found.report().isError() && d.code() != null && d.code().startsWith("E19")) {
                codes.add(d.code());
            }
        }
        return codes;
    }

    /**
     * A row that did not finish went somewhere before it stopped.
     *
     * <p>What it went through was dropped with it, so the arms it did not light are undecided rather
     * than unreached — and every arm of the behavior would otherwise be reported as one no row goes
     * through, on the strength of a run nobody let finish.
     */
    @Test
    void aBranchMeasureOverAnUnfinishedRowIsUndecided() {
        Compilation compilation = measured("loop", TIMES_OUT, DoesNotComeBack.overrunningOn(DoesNotComeBack.everythingAboutRowsOf("go")));
        Adequacy.BranchEvidence branch = compilation.db()
                .ask(new Adequacy.BranchCoverage(compilation.modules().get(0))).value().get("go");

        assertEquals(2, branch.arms().counted(), "the arms are there");
        assertEquals(MeasurementStatus.PARTIAL, AdequacyReport.statusOf(branch.measured()));
        assertEquals(0, branch.arms().covered());
        assertEquals(2, branch.arms().undecided().size(),
                "and each of them is a question rather than a gap");
        assertEquals(List.of(), branch.arms().unmet(),
                "nothing is asserted over a run nobody let finish");
    }

    /** And nothing is warned about from it. */
    @Test
    void anUndecidedBranchMeasureWarnsAboutNoArm() {
        assertFalse(warningCodes(warned("loop", TIMES_OUT, DoesNotComeBack.overrunningOn(DoesNotComeBack.everythingAboutRowsOf("go")))).contains("E1918"),
                "an arm a row might have gone through is not an arm nothing reaches");
    }

    /**
     * A value the observation could not reach is not a value that missed.
     *
     * <p>The row here writes the very number the invariant's lower bound names. Its observation was
     * cut short by a limit reached elsewhere in the same input, and reporting that as "no row is at
     * this boundary" states something about the model that is not true.
     */
    @Test
    void aBoundaryWhosePositionWasNotReadIsUndecided() {
        List<BorderAssessment> lines = Adequacy.readingsOf(
                measured(budgetSpent("")).db(), "example.budget").get("take");

        List<BorderAssessment.Point> at = pointsAgainstTheLine(lines).stream()
                .filter(p -> "0".equals(p.against())).toList();
        assertEquals(1, at.size());
        assertEquals(MeasurementStatus.PARTIAL, AdequacyReport.statusOf(at.get(0).item().weakeningSource()));
        assertFalse(at.get(0).owed().hasRowWitness(),
                "nothing was read, so nothing was met either");
    }

    @Test
    void anUndecidedBoundaryWarnsAboutNothing() {
        assertFalse(warningCodes(warned(budgetSpent(""))).contains("E1916"),
                "a boundary whose position was not read is not a boundary nothing is at");
    }

    /**
     * A class nothing looked for is not a class a search stopped short of.
     *
     * <p>Two different pieces of news. A search that ran out of room leaves a class still owed and
     * a row still writable by this compiler; a position held back leaves a class nothing was ever
     * going to look for, and a row offered there may be one already sitting in the file. Said the
     * first way, the block printed `the search left something untried before reaching it` two lines under the
     * line saying the position had been withheld.
     *
     * <p>What made this possible: the finding's answer is read off there being no attempt recorded
     * for the class, and an absence says nothing about its cause. So the cause is read off what the
     * run wrote down, and where it wrote nothing the answer says that rather than naming the
     * likeliest — which is what the words this replaced were guarding.
     */
    @Test
    void aClassNothingLookedForIsNotAClassASearchStoppedShortOf() {
        Compilation compilation = measured(budgetSpent(""));
        Map<String, Adequacy.Filling> generated = Adequacy.generatedOf(compilation.db(), "example.budget");
        assertNotNull(generated);

        List<Adequacy.GenerationDisposition> classes =
                generated.get("take").generation().stream()
                        .filter(each -> each.finding().kind() == Adequacy.Kind.AXIS_CLASS_UNCOVERED)
                        .toList();
        assertFalse(classes.isEmpty(), "the flag's classes are reported as uncovered");
        for (Adequacy.GenerationDisposition each : classes) {
            assertEquals(souther.compiler.partition.Generator.UnresolvedCombination.Reason
                            .THE_POSITION_WAS_WITHHELD,
                    ((souther.compiler.partition.GenerationOutcome.CannotGenerate) each.outcome())
                            .why().get(0).reason(),
                    each.finding()::toString);
        }
    }

    /**
     * A class a row may already sit in is not a class to write a row for.
     *
     * <p>The worst of these to get wrong. A warning is a claim; a generated row is a specific piece of
     * work handed to a person, and handing them work that is already done is worse than saying nothing.
     */
    @Test
    void nothingIsGeneratedForAPositionThatCouldNotBeRead() {
        Compilation compilation = measured(budgetSpent(""));
        Map<String, Adequacy.Filling> generated = Adequacy.generatedOf(compilation.db(), "example.budget");
        assertNotNull(generated);

        assertEquals(List.of(), generated.get("take").composed().rows(),
                "the flag's classes are undecided, so nothing is written for them");
        assertFalse(generated.get("take").composed().reasons().isEmpty(),
                "and the position that could not be read is named");
        String written = GeneratedRows.of(Adequacy.offeredFor(compilation.db(),
                        souther.compiler.query.OfferingRequest.overTheModule(
                                "example.budget", true)),
                Map.of(), SourceNameResolver.identity(), compilation.db()).text();
        assertFalse(written.contains("example take"), "no row is offered: " + written);
        assertTrue(written.contains("no rows offered at"),
                "the position it could not read is what there is to say: " + written);
        assertFalse(written.contains("generation stopped"),
                "and it did not stop — every position it had was one it could not read: " + written);
    }

    /**
     * The rows the compile ran are what every measure reads, reasons included.
     *
     * <p>The evaluation keeps observations and the reasons for their absence apart on purpose. An
     * aggregate that passed on the first and dropped the second would answer as if the reason were
     * nothing, which is the one reading the whole design is against.
     */
    @Test
    void whatStoppedARowBeingSeenReachesTheMeasureThatReadsIt() {
        Compilation compilation = measured("loop", TIMES_OUT, DoesNotComeBack.overrunningOn(DoesNotComeBack.everythingAboutRowsOf("go")));
        Adequacy.SignatureEvidence signature = compilation.db()
                .ask(new Adequacy.Witnesses(compilation.modules().get(0))).value().get("go");

        assertEquals(MeasurementStatus.PARTIAL, AdequacyReport.statusOf(signature.counted()));
        assertFalse(warningCodes(warned("loop", TIMES_OUT, DoesNotComeBack.overrunningOn(DoesNotComeBack.everythingAboutRowsOf("go")))).contains("E1913"),
                "a case the unfinished row might have produced is not a case nothing claims");
    }

    /**
     * The third disposition, which is the one a two-valued field would lose.
     *
     * <p>Said here rather than beside the other two, because this is where a measure that came to no
     * answer is. A kind a build refuses over, from a measurement that did not finish, is neither a
     * gap nor a kind nobody gates on — and the line above is the same fact from the warning's side:
     * no code is printed for it. What a report calls it and what a build does about it come from
     * this one word, so a finding that is undecided and a finding nobody gates on may not read
     * alike.
     */
    @Test
    void aGapFromAMeasureThatCameToNoAnswerIsUndecided() {
        List<Adequacy.Finding> findings = AdequacyReport.of(
                        measured("loop", TIMES_OUT,
                                DoesNotComeBack.overrunningOn(
                                        DoesNotComeBack.everythingAboutRowsOf("go"))))
                .findings();

        List<Adequacy.Finding> undecided = findings.stream()
                .filter(f -> Adequacy.AdequacyBar.SIMPLIFIED_DOMAIN.refuses(f.kind())).toList();

        assertFalse(undecided.isEmpty(), () -> "the model has a kind a build gates on: " + findings);
        for (Adequacy.Finding f : undecided) {
            assertFalse(f.weakenedBy().isEmpty(), f::toString);
            assertEquals(Adequacy.Finding.Disposition.UNDECIDED,
                    f.disposition(Adequacy.AdequacyBar.SIMPLIFIED_DOMAIN), f::toString);
            assertFalse(f.isAdequacyGap(Adequacy.AdequacyBar.SIMPLIFIED_DOMAIN), f::toString);
        }
    }

    /**
     * A report about one behavior says nothing about another.
     *
     * <p>`--behavior submit` is a promise about what the output is about. A status of `partial` whose
     * reason names a behavior the report does not show is a status nothing in front of the reader
     * accounts for.
     */
    @Test
    void filteringToOneBehaviorLeavesAnotherBehaviorsReasonsBehind() {
        Compilation compilation = measured("""
                module example.two

                data Draft = { n: Int }
                data Ok = { n: Int }
                data Gone = { why: String }

                partial let spin (n: Int): Int = spin(n)

                behavior submit : (request: Draft) -> Ok
                    constructs Ok

                let submit (request) = Ok { n = request.n }

                behavior cancel : (request: Draft) -> Gone
                    constructs Gone

                let cancel (request) = {
                    guard request.n > 0 else Gone { why = "none" }
                    Gone { why = "x" }
                }

                example submit
                    | (Draft { n = 1 }) -> Ok { n = 1 }

                example cancel
                    | (Draft { n = spin(1) }) -> Gone { why = "x" }
                """, DoesNotComeBack.overrunningOn(DoesNotComeBack.everythingAboutRowsOf("cancel")));
        AdequacyReport whole = AdequacyReport.of(compilation);

        assertEquals(MeasurementStatus.PARTIAL, whole.status(), "`cancel` did not finish");
        // Both of them `cancel`'s: the row that did not finish — said as which row, since a
        // behavior may have more than one that did not — and the position of its guard whose value
        // that row was the only one to write.
        assertEquals(List.of("cancel/0/#1", "cancel/request.n"),
                whole.modules().get(0).incompleteness().written().stream()
                        .map(gap -> gap.fact().subject()).toList());

        AdequacyReport one = whole.only(null, "submit");
        assertEquals(MeasurementStatus.COMPLETE, one.status());
        assertEquals(List.of(), one.modules().get(0).incompleteness().written());
        assertTrue(one.modules().get(0).behaviors().stream()
                .allMatch(b -> b.name().equals("submit")));
    }

    // --- a source whose rows nothing read at all ------------------------------------------------

    /**
     * One source read, one not.
     *
     * <p>The attached file declares a name the module already declares, so its rows would read that
     * other declaration and it is not evaluated (E1906). It leaves no rows and one reason — and the
     * rows it holds are at both boundaries and in the class the module's own row is not.
     */
    private static final List<String> SPLIT = List.of("""
            module example.split

            data Amount = Int
                invariant value >= 0 && value <= 1000

            data Yes
            data No
            data Flag = Yes | No

            data Draft = { cost: Amount, flag: Flag }
            data Ok = { n: Int }
            data Refused = { why: String }

            let shared = Draft { cost = Amount(7), flag = Yes }

            behavior take : (request: Draft) -> Ok | Refused
                constructs Ok

            let take (request) = Ok { n = request.cost.value }

            example take
                | (Draft { cost = Amount(7), flag = Yes }) -> Ok { n = 7 }
            """, """
            examples for example.split

            let shared = Draft { cost = Amount(0), flag = No }

            example take
                | (Draft { cost = Amount(0), flag = No }) -> Ok { n = 0 }
                | (Draft { cost = Amount(1000), flag = No }) -> Ok { n = 1000 }
            """);

    private static Compilation split() {
        return COMPILED.computeIfAbsent("split", _ -> {
            Compilation compilation = Compilation.ofSources(SPLIT,
                    souther.compiler.meta.ModulePath.EMPTY);
            compilation.measure(Adequacy.Asked.fullReport());
            compilation.answerEverything();
            return compilation;
        });
    }

    /**
     * A boundary the unread rows are at is not a boundary nothing is at.
     *
     * <p>The axes already knew this; the boundaries were reading only the rows that remained. Being
     * found still settles it — one row at the value is one row at the value, whatever went unread —
     * but not being found among some of the rows settles nothing.
     */
    @Test
    void aBoundaryIsUndecidedWhereSomeRowsWereNeverRead() {
        List<BorderAssessment> lines =
                Adequacy.readingsOf(split().db(), "example.split").get("take");

        assertEquals(2, pointsAgainstTheLine(lines).size());
        for (BorderAssessment.Point boundary : pointsAgainstTheLine(lines)) {
            assertEquals(MeasurementStatus.PARTIAL, AdequacyReport.statusOf(boundary.item().weakeningSource()), boundary.against());
            assertFalse(boundary.owed().hasRowWitness());
        }
    }

    /**
     * And nothing is generated from it.
     *
     * <p>Both rows the generator would write are sitting in the file it could not read. A row that
     * could not be classified stops its own position; a row that was never seen stops the behavior,
     * because which positions it would have settled is exactly what is unknown.
     */
    @Test
    void nothingIsGeneratedWhereSomeRowsWereNeverRead() {
        Compilation compilation = split();
        Map<String, Adequacy.Filling> generated = Adequacy.generatedOf(compilation.db(), "example.split");

        assertEquals(List.of(), generated.get("take").composed().rows());
        assertEquals(List.of(), generated.get("take").boundaries().rows());
        String written = GeneratedRows.of(Adequacy.offeredFor(compilation.db(),
                        souther.compiler.query.OfferingRequest.overTheModule(
                                "example.split", true)),
                Map.of(), SourceNameResolver.identity(), compilation.db()).text();
        assertFalse(written.contains("example take"),
                "the row may be sitting in the file that could not be read: " + written);
        assertTrue(written.contains("generation stopped"),
                "and an author who asked what to write is told why there is nothing: " + written);
    }

    /**
     * A reason that names no behavior belongs to every behavior.
     *
     * <p>Filtering to one behavior drops the reasons about the others. A whole source that could not
     * be evaluated is not about another behavior — it is missing rows for whatever it held, this one
     * included — so it stays, and the status with it.
     */
    @Test
    void filteringKeepsAReasonThatIsAboutNoOneBehavior() {
        AdequacyReport one = AdequacyReport.of(split()).only(null, "take");

        assertEquals(MeasurementStatus.PARTIAL, one.status());
        assertEquals(1, one.modules().get(0).incompleteness().written().size());
        assertEquals(Incompleteness.Code.OBSERVATION_ABSENT,
                one.modules().get(0).incompleteness().written().iterator().next().fact().code());
    }

    /**
     * The lines say what the summary says.
     *
     * <p>A `(partial)` in the margin and a flat assertion under it read as a finding with a footnote.
     * Under partial the honest sentence is that nothing *seen* claims the case.
     */
    @Test
    void aSignatureLineUnderPartialDoesNotAssert() {
        String human = AdequacyReport.of(split()).human(SourceNameResolver.identity());

        assertTrue(human.contains("undecided whether a row expects `Refused`"), human);
        assertFalse(human.contains("· no row expects"), human);
    }

    // --- what the status above a measure says -----------------------------------------------------

    /**
     * A measure that could not be made shows in the status over it, and says why.
     *
     * <p>The truncation is inside the value a row wrote, and the measure that reads it is where it
     * becomes visible — as a count of rows it could not place. The count says how many and the
     * reason says what happened, and an author reading only the count cannot tell an observation a
     * limit stopped from a value that could not be read at all. The first goes away if the fixture
     * is written smaller and the second does not.
     *
     * <p>Here it is the collection ahead of it that spent the budget, so the value at the position
     * named is the number the invariant's lower bound names. Which is why the sentence says the
     * observation was stopped and not that the value was large.
     *
     * <p>The statuses are what they were before the reason was carried. Nothing here is a new
     * finding: the measure had already come back partial, and what is new is that it can be
     * explained.
     */
    @Test
    void aPartialMeasureMakesTheStatusAboveItPartialAndSaysWhy() {
        AdequacyReport report = AdequacyReport.of(measured(budgetSpent("")));

        assertEquals(MeasurementStatus.PARTIAL, report.status());
        assertEquals(MeasurementStatus.PARTIAL, report.modules().get(0).status());
        assertEquals(MeasurementStatus.PARTIAL, report.modules().get(0).behaviors().get(0).status());

        List<PublishedIncompleteness> why = report.modules().get(0).incompleteness().written();
        assertEquals(1, why.size(), why.toString());
        assertEquals(Incompleteness.Code.VALUE_TRUNCATED, why.get(0).fact().code());
        assertEquals(Optional.of("take"), why.get(0).fact().behavior(),
                "a position is inside one behavior");
        String human = report.human(SourceNameResolver.identity());
        assertTrue(human.contains("the observation at"), human);
    }

    /**
     * The JSON names every arm and says of each what became of it.
     *
     * <p>A reading that did not finish leaves each arm it might have lit undecided, and that is what
     * the entry says: the arm is there, it is counted, and no row was seen to go through it. What
     * used to be here was a field named `unreached` that went absent whenever anything at all
     * weakened the measurement, so a consumer was handed two counts and no way to tell which arms
     * the difference between them was.
     */
    @Test
    void theJsonSaysOfEachArmWhatBecameOfItUnderPartial() throws Exception {
        JsonNode branch = JsonMapper.builder().build().readTree(
                AdequacyReport.of(measured("loop", TIMES_OUT, DoesNotComeBack.overrunningOn(DoesNotComeBack.everythingAboutRowsOf("go"))))
                        .json(souther.compiler.diag.SourceNameResolver.identity()))
                .get("modules").get(0).get("behaviors").get(0).get("branch");

        assertEquals("partial", branch.get("status").asString());
        assertEquals(2, branch.get("obligations").size());
        for (JsonNode arm : branch.get("obligations")) {
            assertEquals("undecided", arm.get("disposition").asString(),
                    () -> "a row that did not come back may have gone through it: " + branch);
            // What left it open, and no second way of saying that it is open. An arm says where it
            // stands once; a status and a hit beside the word would be the same answer again.
            assertEquals(1, arm.get("weakening").size(),
                    () -> "and the reading that stopped is why: " + branch);
        }
    }

    /**
     * And what the JSON says of each arm, the human report says of each arm.
     *
     * <p>The pair of tests has said since they were written that these two surfaces answer alike,
     * and only one of them was ever rendered under a reading that did not finish. So the line a
     * person reads here was carried by nobody while the measure behind it was moved twice.
     *
     * <p>What is printed is the counts and, under them, the arms the difference is made of. A word
     * qualifying the whole measure is not enough for a person either: it says the number cannot be
     * trusted, which is true and is not the same as saying which arms it is about.
     */
    @Test
    void theHumanReportNamesTheArmsNobodyCouldDecideUnderPartial() {
        String human = AdequacyReport.of(measured("loop", TIMES_OUT,
                        DoesNotComeBack.overrunningOn(DoesNotComeBack.everythingAboutRowsOf("go"))))
                .human(SourceNameResolver.identity());

        assertTrue(human.contains("branch      0/2"), () -> "the counts are printed: " + human);
        assertEquals(2, human.lines()
                        .filter(line -> line.strip().startsWith("? undecided whether a row goes"))
                        .count(),
                () -> "and both arms are named as the ones nobody could decide: " + human);
        assertFalse(human.contains("no row goes through"),
                () -> "none of them is named as a gap: " + human);
    }

    /**
     * A combination an unread row may sit in is unknown over the rows that were read, and says so.
     *
     * <p>The other measures each say whether their numbers are over all the rows or some of them; the
     * pair space was the one that could not, so its count read as a count over all of them.
     */
    @Test
    void thePairSpaceSaysWhetherItSawEveryRow() {
        Compilation compilation = measured("""
                module example.pair

                data Yes
                data No
                data Flag = Yes | No

                data Ok = { n: Int }

                partial let spin (n: Int): Int = spin(n)

                behavior pick : (a: Flag, b: Flag) -> Ok
                    constructs Ok

                let pick (a, b) = Ok { n = spin(1) }

                example pick
                    | (Yes, Yes) -> Ok { n = 0 }
                """, DoesNotComeBack.overrunningOn(DoesNotComeBack.everyRowOf("pick")));
        PartitionEvidence partition = compilation.db()
                .ask(new Adequacy.Coverage("example.pair")).value().get("pick");

        assertEquals(4, partition.pairs().total());
        assertEquals(MeasurementStatus.PARTIAL, AdequacyReport.statusOf(partition.pairs().counted()),
                "the one row could not be placed at either position");
        String human = AdequacyReport.of(compilation).human(SourceNameResolver.identity());
        assertTrue(human.contains("unknown of the rows that were read"),
                () -> "the count is over the rows that came back, and the line says so: " + human);
    }

    /**
     * The positions and the combinations over them answer from one reading.
     *
     * <p>They were putting the same question to the rows and answering the follow-up differently: the
     * positions counted the row nothing could place and called their classes undecided, the
     * combinations left it out and called theirs untried. Two sentences from one row, printed one
     * after the other.
     */
    @Test
    void thePositionsAndTheCombinationsAgree() {
        Compilation compilation = measured("""
                module example.agree

                data Yes
                data No
                data Flag = Yes | No

                data Ok = { n: Int }

                partial let spin (n: Int): Int = spin(n)

                behavior pick : (a: Flag, b: Flag) -> Ok
                    constructs Ok

                let pick (a, b) = Ok { n = spin(1) }

                example pick
                    | (Yes, Yes) -> Ok { n = 0 }
                """, DoesNotComeBack.overrunningOn(DoesNotComeBack.everyRowOf("pick")));
        PartitionEvidence partition = compilation.db()
                .ask(new Adequacy.Coverage("example.agree")).value().get("pick");

        for (PartitionEvidence.AxisCoverage axis : partition.axes()) {
            assertEquals(MeasurementStatus.PARTIAL, AdequacyReport.statusOf(axis.reached()), axis.path());
        }
        assertEquals(MeasurementStatus.PARTIAL, AdequacyReport.statusOf(partition.pairs().counted()));
    }

    /**
     * A hit is a hit whatever else stopped.
     *
     * <p>Found is decidable over some of the rows; not-found is not. A row that wrote the boundary
     * value and went through the comparison did so, and hiding that because another row never
     * finished throws away the one thing that was settled.
     */
    @Test
    void aBoundaryARowDemonstrablyMetStaysMet() {
        Compilation compilation = oneRowOfTwoStops();
        List<BorderAssessment> lines =
                Adequacy.readingsOf(compilation.db(), "example.mix").get("take");

        BorderAssessment.Point line = pointsAgainstTheLine(lines).stream()
                .filter(p -> "100".equals(p.against())).findFirst().orElseThrow();
        assertTrue(line.owed().hasRowWitness(),
                "a row wrote 100 and went through the comparison");
        assertEquals(MeasurementStatus.COMPLETE, AdequacyReport.statusOf(line.item().weakeningSource()));

        BorderAssessment.Point beyond = pointsAgainstTheLine(lines).stream()
                .filter(p -> "101".equals(p.against())).findFirst().orElseThrow();
        assertEquals(MeasurementStatus.PARTIAL, AdequacyReport.statusOf(beyond.item().weakeningSource()),
                "and the one nothing was found at is undecided, not missed");
    }

    /**
     * An arm a row went through, beside an arm nobody could decide.
     *
     * <p>A row that ran through an arm ran through it, whatever became of the row beside it — so an
     * account that answers per arm has one of these two settled and one of them open, and the two
     * are told apart in the block that counts them. Answered once for the measure, the four arms a
     * larger body is short of are one word with nothing under it, and the arm that was settled is
     * qualified by something that has nothing to do with it.
     */
    @Test
    void anArmARowWentThroughIsToldFromOneNobodyCouldDecide() {
        String human = AdequacyReport.of(oneRowOfTwoStops()).human(SourceNameResolver.identity());

        assertTrue(human.contains("branch      1/2"),
                () -> "one of the two arms was gone through: " + human);
        assertTrue(human.contains("? undecided whether a row goes through `else`"),
                () -> "and the other is named as the one nobody could decide: " + human);
        assertFalse(human.contains("(undecided: a row was not read)"),
                () -> "no word for the whole measure stands in for naming them: " + human);
    }

    /**
     * A build refuses over the arm it can, whatever it could not decide beside it.
     *
     * <p>One behavior's rows all ran and leave an arm nothing goes through; the behavior next to it
     * has a row that never came back, so its arms are questions. The first is a gap and a build held
     * to the arms refuses over it — the second holds nothing open, because what a build refuses over
     * is what a measure established and not how far the measure beside it got.
     *
     * <p>What this holds is the gate staying where it belongs. Each behavior's arms are answered for
     * by its own reading, so a reading that stopped in one behavior settles nothing about the
     * behavior next to it — raised to the module, a run with one unfinished row anywhere would let
     * every gap in it through. What settles an arm within one behavior is held where an arm and the
     * measure over it can come apart, which no model reaches
     * ({@code AGapIsRefusedOverByWhatSettledItTest}).
     */
    @Test
    void aGapIsRefusedOverBesideAnArmNobodyCouldDecide() {
        AdequacyReport report = AdequacyReport.of(measured("both", """
                module example.both

                data Draft = { n: Int }
                data Done = { n: Int }
                data Small = { n: Int }

                partial let spin (n: Int): Int = spin(n)

                behavior go : (request: Draft) -> Done | Small
                    constructs Done, Small

                let go (request) = {
                    guard request.n <= 0 else Done { n = spin(request.n) }
                    Small { n = request.n }
                }

                behavior gate : (request: Draft) -> Done | Small
                    constructs Done, Small

                let gate (request) = {
                    guard request.n <= 0 else Done { n = 1 }
                    Small { n = request.n }
                }

                example go
                    | (Draft { n = 1 }) -> Done { n = 1 }

                example gate
                    | (Draft { n = 0 }) -> Small { n = 0 }
                """, DoesNotComeBack.overrunningOn(
                        DoesNotComeBack.everythingAboutRowsOf("go")),
                Adequacy.Asked.fullReport()));

        List<String> refused = report.adequacyGaps().stream()
                .filter(f -> f.about() instanceof About.AnArmNoRowGoesThrough)
                .map(f -> f.subject().toString()).toList();
        assertEquals(1, refused.size(),
                () -> "the arm of the behavior every row of was read: " + refused);
        assertEquals(AdequacyReport.AdequacyStatus.NOT_SATISFIED, report.adequacy(),
                () -> "and one gap settles the verdict, whatever is undecided beside it: "
                        + report.human(SourceNameResolver.identity()));
    }

    /** The points a row is owed at against a line, which is what a value names. */
    private static List<BorderAssessment.Point> pointsAgainstTheLine(
            List<BorderAssessment> lines) {
        return BorderAssessment.pointsOf(lines).stream()
                .filter(p -> p.role().againstTheLine()).filter(p -> p.owed() != null).toList();
    }

    /**
     * One row of two written under separate {@code example} blocks does not come back, and the other
     * is evaluated as it would have been.
     *
     * <p>Here because a behavior can be exampled more than once — a second block beside the first, or
     * an {@code examples for} file beside the model — and the first thing this was written with named
     * a row by its number within its block. That made both first rows one name, so picking out either
     * picked out both, and a model of this shape would have had its working rows reported as rows that
     * did not come back.
     */
    @Test
    void aRowIsPickedOutOfTheBlockItIsInAndNoOther() {
        Compilation compilation = measured("""
                module example.twice

                data Draft = { n: Int }
                data Ok = { n: Int }

                behavior take : (request: Draft) -> Ok
                    constructs Ok

                let take (request) = Ok { n = request.n }

                example take
                    | "does not come back" : (Draft { n = 1 }) -> Ok { n = 1 }

                example take
                    | "comes back" : (Draft { n = 2 }) -> Ok { n = 2 }
                """,
                DoesNotComeBack.overrunningOn(
                        DoesNotComeBack.everythingAboutTheRowNamed("does not come back")));

        List<String> codes = new ArrayList<>();
        for (Db.Found found : compilation.db().allReports()) {
            codes.add(String.valueOf(found.report().diagnostic().code()));
        }
        assertEquals(1, codes.stream().filter("E1923"::equals).count(),
                "one row was picked out, so one row did not come back: " + codes);
    }

    // --- who a reason counts against ---------------------------------------------------------------

    /**
     * A reason larger than a behavior counts against every behavior it holds.
     *
     * <p>`subject` carried a behavior name, a source id, the module or a position in one string, and
     * three readers worked out which from the shape of it — differently. The aggregate treated a
     * module-wide reason as a behavior nobody declared, the report matched it by name and found none,
     * and each was a defect found on its own. The scope is on the reason now, so nobody guesses.
     */
    @Test
    void aReasonAboutASourceCountsAgainstTheBehaviorsInIt() {
        souther.compiler.observe.Incompleteness aboutOne =
                souther.compiler.observe.Incompleteness.of(
                        souther.compiler.observe.Incompleteness.Code.ROW_UNDECIDED,
                        souther.compiler.observe.Incompleteness.Scope.BEHAVIOR, "submit");
        souther.compiler.observe.Incompleteness aboutTheSource =
                souther.compiler.observe.Incompleteness.ofSource(
                        souther.compiler.observe.Incompleteness.Code.OBSERVATION_ABSENT,
                        new souther.compiler.source.SourceId("3"));

        assertTrue(aboutOne.countsAgainst("submit"));
        assertFalse(aboutOne.countsAgainst("cancel"));
        assertTrue(aboutTheSource.countsAgainst("submit"));
        assertTrue(aboutTheSource.countsAgainst("cancel"),
                "nothing in the source was read, whichever behaviors it wrote rows for");
    }

    /** And the report says so: a behavior held by an unread source is not complete. */
    @Test
    void aBehaviorHeldByAnUnreadSourceIsNotComplete() {
        AdequacyReport report = AdequacyReport.of(split());

        assertEquals(MeasurementStatus.PARTIAL, report.status());
        for (AdequacyReport.BehaviorReport behavior : report.modules().get(0).behaviors()) {
            assertEquals(MeasurementStatus.PARTIAL, behavior.status(), behavior.name());
        }
    }

    /**
     * One reason is one entry, however many sources went looking.
     *
     * <p>These are structured rather than written out so that a build can count them, and a count
     * that grows with the number of attached files is counting the looking rather than the failure.
     */
    @Test
    void oneReasonIsReportedOnce() {
        List<PublishedIncompleteness> gaps =
                AdequacyReport.of(split()).modules().get(0).incompleteness().written();

        assertFalse(gaps.isEmpty(), "a split model leaves something unread");
        assertEquals(gaps.stream().map(PublishedIncompleteness::fact).distinct().count(),
                gaps.size(), gaps.toString());
    }

    /** The row that did not finish is still there to be counted, and still says it did not. */
    @Test
    void theUnfinishedRowIsStillReported() {
        Compilation compilation = measured("loop", TIMES_OUT, DoesNotComeBack.overrunningOn(DoesNotComeBack.everythingAboutRowsOf("go")));
        SourceId sourceId = compilation.exampleSourcesOf("example.loop").getFirst();

        List<souther.compiler.observe.RowOutcome> rows = compilation.db()
                .ask(souther.compiler.query.Output.Examples.asked(
                        compilation.db(), "example.loop", sourceId)).value().rows();

        assertEquals(1, rows.size());
        assertEquals(Disposition.INCOMPLETE, rows.get(0).disposition());
    }
}
