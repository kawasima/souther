package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.partition.GenerationOutcome;
import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderObligationPointAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.query.PointResolution;
import souther.compiler.query.BorderAccount;
import souther.compiler.query.GenerationScope;
import souther.compiler.query.SearchCoverage;
import souther.compiler.report.GeneratedRows;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every finding gets one generation disposition, and which bar a build asked for is no part of it.
 *
 * <p>A generator that offers rows for the findings it has a strategy for and says nothing about the
 * rest reads as though the block filled everything. An author who takes every row it offers is left
 * with a model that still fails, and nothing told them which part of the shortfall was never
 * attempted.
 *
 * <p>So the answer is total over the findings — <em>all</em> of them. It used to be total over the
 * ones a build refuses over, which is the coupling this holds against: what a bar refuses and what
 * a search can compose are two projections of one set of findings, and the first was standing in
 * the doorway of the second. A finding no bar had been asked for went unanswered, and no strategy
 * could be written for one until some bar gated on it first.
 *
 * <p>Which of the four it is depends on whether a strategy applies, not on what a search happened
 * to do: a strategy that recognised the finding and composed nothing is not the same as no strategy
 * for it at all, and neither is the same as a finding no row would answer.
 *
 * <p><b>Beside the exhaustive switch and not instead of it.</b> The compiler makes somebody answer
 * for a new shape of finding; this makes sure nothing filters one out before the switch is reached.
 * That is exactly what went wrong — a class no row is in was listed in the switch and dropped by
 * the line above it.
 */
class EveryFindingHasAGenerationDispositionTest {

    /** Two boundary gaps a strategy composes a row for, and an arm no strategy reaches. */
    private static final String POLICY = """
            module example.policy

            data Rate = Int
                invariant nonNegative = value >= 0

            data Cap = Int
                invariant nonNegative = value >= 0

            data Policy =
                { rate: Rate
                , cap: Cap
                }

            behavior fee : (days: Int, policy: Policy) -> Int

            let fee (days, policy) = {
                let accrued = days * policy.rate.value

                if accrued > policy.cap.value then policy.cap.value else accrued
            }

            example fee
                | "under the cap" : (5, Policy { rate = Rate(10), cap = Cap(500) }) -> 50
            """;

    private static Compilation compiled(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    /**
     * Every finding of one behavior, filtered by nothing.
     *
     * <p>No filter, and that is the point rather than a simplification. Narrowing to what some bar
     * refuses over is what this exists to refuse: the two lists compared below would then be
     * answers to one question asked twice, and would agree however much was being dropped between
     * the findings and the dispositions.
     */
    private static List<Adequacy.Finding> findings(Compilation compilation, String module,
                                                   String behavior) {
        List<Adequacy.Finding> found =
                compilation.db().ask(new Adequacy.Findings(module)).value();
        assertNotNull(found, "the model under test compiles");
        return found.stream().filter(each -> each.subject().isBehavior(behavior)).toList();
    }

    private static Adequacy.Filling filling(Compilation compilation, String module,
                                            String behavior) {
        Map<String, Adequacy.Filling> generated =
                Adequacy.generatedOf(compilation.db(), module);
        assertNotNull(generated, "the model under test compiles");
        return generated.get(behavior);
    }

    @Test
    void everyFindingHasOneAnswer() {
        Compilation compilation = compiled(POLICY);
        List<Adequacy.Finding> findings = findings(compilation, "example.policy", "fee");

        assertFalse(findings.isEmpty(), "the model under test has findings to answer for");
        assertEquals(findings,
                filling(compilation, "example.policy", "fee").generation().stream()
                        .map(Adequacy.GenerationDisposition::finding).toList(),
                "one answer per finding, in the order the findings were established");
    }

    /**
     * And the same list whichever bar the run was held to.
     *
     * <p>The other half of the separation. The list above could be total over the findings and
     * still be decided by the bar — a stricter one having more of them — and what says it is not is
     * that two runs of one model under two bars answer for the same findings.
     */
    @Test
    void whatIsAnsweredForDoesNotMoveWithTheBar() {
        for (Adequacy.AdequacyBar bar : Adequacy.AdequacyBar.values()) {
            Compilation compilation = Compilation.ofSource(POLICY, "Main");
            compilation.measure(Adequacy.Asked.fullReport(bar));
            compilation.answerEverything();

            // Held within one compilation, because that is where the two lists are the same
            // findings. Compared across two, a border's finding carries the region its row was
            // composed over, which is one object per run and equal to nothing else — so the
            // comparison would be about object identity rather than about what was answered for.
            List<Adequacy.Finding> found = findings(compilation, "example.policy", "fee");
            assertFalse(found.isEmpty(), "the model under test has findings to answer for");
            assertEquals(found,
                    filling(compilation, "example.policy", "fee").generation().stream()
                            .map(Adequacy.GenerationDisposition::finding).toList(),
                    bar::name);
        }
    }

    @Test
    void aBoundaryARowWasComposedForIsAnsweredWithThatRow() {
        Compilation compilation = compiled(GUARDED);
        List<Adequacy.GenerationDisposition> answered =
                filling(compilation, "example.guarded", "band").generation().stream()
                        .filter(d -> d.finding().kind() == Adequacy.Kind.BOUNDARY_UNMET).toList();

        assertEquals(2, answered.size(), "both edges");
        assertTrue(answered.stream().allMatch(
                        d -> d.outcome() instanceof GenerationOutcome.Generated),
                "a strategy applies to a boundary and it composed a row: " + answered);
    }

    /**
     * A line the declaration is owed is answered too, with the row a reading of it already composed.
     *
     * <p>The subject the walk over the behaviors does not reach. A line an {@code invariant} drew is
     * not any behavior's, so the fillings above answer for every finding but those — and one printed
     * in the report and left out of this answer is one an author is told nothing about, while the
     * rows beside it read as though they filled everything.
     *
     * <p>And answered with what is known rather than with what has not been arranged. A reading of
     * the line composed a row standing at the point, and the readings of a debt ask the same of a
     * row at the points against the line — so that row is the one the debt is offered. Answered
     * "nothing offers a row" regardless, a block printed the row and then said no row was on offer.
     */
    @Test
    void aLineOwedToADeclarationIsAnsweredForToo() {
        Compilation compilation = compiled(POLICY);
        List<Adequacy.Finding> declared =
                compilation.db().ask(new Adequacy.Findings("example.policy")).value().stream()
                        .filter(each -> each.subject()
                                instanceof souther.compiler.query.FindingSubject.OfADeclaration)
                        .toList();
        assertFalse(declared.isEmpty(), "the model under test has lines its declarations are owed");

        List<Adequacy.GenerationDisposition> answered =
                Adequacy.accountFor(compilation.db(), "example.policy",
                        new GenerationScope.Module()).dispositions(
                                compilation.db().ask(new Adequacy.Findings("example.policy"))
                                        .value());
        assertEquals(declared, answered.stream()
                        .map(Adequacy.GenerationDisposition::finding).toList(),
                "every one of them, and each with its own answer");
        assertTrue(answered.stream().allMatch(each ->
                        each.outcome() instanceof GenerationOutcome.Generated),
                "a reading of each of these composed a row at the point: " + answered);
    }

    /**
     * A generation narrowed to one behavior answers from what that behavior's search composed.
     *
     * <p>A line is owed once over every behavior carrying the type, and a row for it may be
     * composable at one of them and not at another — {@code held} holds the field to a single
     * value the line is not at, and {@code anywhere} does not. Answered from the module's readings, a
     * request that searched only {@code held} would say a row is on offer because {@code anywhere}
     * had one, and print no row beside it (issue #1062).
     *
     * <p>Which is what makes the scope a question rather than a saving. A row composed at {@code
     * anywhere} is written in that behavior's terms and belongs in that behavior's block, so it is
     * not an answer to a request about {@code held} however well it settles the line.
     */
    @Test
    void aGenerationNarrowedToOneBehaviorAnswersFromWhatThatBehaviorSearched() {
        Compilation compilation = compiled(NARROWED);
        List<PointResolution> atHeld = drawnBy(resolved(compilation, "example.narrowed",
                new GenerationScope.Behavior("held")), "Code");
        List<PointResolution> atAnywhere = drawnBy(resolved(compilation, "example.narrowed",
                new GenerationScope.Behavior("anywhere")), "Code");

        assertFalse(atHeld.isEmpty(), "the line is owed at both, so both are asked about");
        assertFalse(atAnywhere.isEmpty(), "the line is owed at both, so both are asked about");
        assertTrue(atAnywhere.stream()
                        .anyMatch(each -> each instanceof PointResolution.Generated),
                "the search of `anywhere` composed a row at the line: " + atAnywhere);
        assertTrue(atHeld.stream()
                        .noneMatch(each -> each instanceof PointResolution.Generated),
                "and the search of `held` composed none, whatever `anywhere` did: " + atHeld);
    }

    /**
     * The answers about the lines one declaration drew.
     *
     * <p>Asked by the declaration the line is owed to, because a model has more than one: what
     * {@code Narrow} says about its own field is a line too, and a reading that can compose nothing
     * at {@code Code}'s line composes one at that.
     */
    private static List<PointResolution> drawnBy(BorderAccount rows, String declaredOn) {
        return rows.resolved().entrySet().stream()
                .filter(each -> each.getKey().line().owedToTheDeclaration()
                        .map(on -> on.name().equals(declaredOn)).orElse(false))
                .map(each -> each.getValue().resolution()).toList();
    }

    /**
     * A line the first reading composes nothing at is searched at the next.
     *
     * <p>The whole of what a search over the readings is for. {@code Narrow} holds its field to four
     * characters or more, so the reading of the line at {@code held} cannot stand a row at length 1
     * — and a line one reading composes nothing at is not a line nothing composes a row for
     * (issue #1076). Stopping at the first reading, the module's own declaration was reported as
     * work nobody could do while the row was two behaviors further down.
     */
    @Test
    void aLineTheFirstReadingComposesNothingAtIsSearchedAtTheNext() {
        List<PointResolution> atCode = drawnBy(
                resolved(compiled(HELD_FIRST), "example.held", new GenerationScope.Module()), "Code");

        assertFalse(atCode.isEmpty(), "the model under test has a line owed at both");
        assertEquals(List.of("anywhere"), atCode.stream()
                        .filter(each -> each instanceof PointResolution.Generated)
                        .map(each -> ((PointResolution.Generated) each).composedBy())
                        .distinct().toList(),
                "the walk went past the reading that composed nothing, and the row is written in"
                        + " the terms of the one that did: " + atCode);
    }

    /**
     * One row for one line, however many readings could compose one.
     *
     * <p>What a person has to write is one row, and a block offering it once per position of every
     * behavior carrying the type is the same work put in front of them four times (issue #1076).
     * How many there are is settled where the search is resolved and not where the block is laid
     * out, so it is asked of the resolution.
     */
    @Test
    void oneLineIsOfferedOneRow() {
        BorderAccount rows = resolved(compiled(NARROWED), "example.narrowed",
                new GenerationScope.Module());

        assertFalse(rows.resolved().isEmpty(), "the model under test has a line owed at both");
        assertEquals(rows.resolved().size(),
                rows.resolved().keySet().stream().distinct().count(),
                "one answer per point of a line");
        assertEquals(rows.resolved().values().stream()
                        .filter(each -> each.resolution()
                                instanceof PointResolution.Generated).count(),
                rows.rowsByCarrier().values().stream().mapToLong(List::size).sum(),
                "and one row per answer that composed one");
    }

    /**
     * A walk that could not see every reading does not settle the line.
     *
     * <p>What the readings prove about the line takes all of them: where a region stops is settled
     * by every other rule reaching a position, so one reading proving there is nothing at the point
     * proves it of that position. A request about {@code held} leaves {@code anywhere} unwalked —
     * and {@code anywhere} is where the row is — so nothing it comes back with may be read as the
     * line refusing one.
     *
     * <p>Asked of what was walked and never of which scope this was. A line one behavior carries is
     * walked entirely by a request about that behavior, and a rule reading the shape of the request
     * would refuse it the answer its own evidence supports.
     */
    @Test
    void aWalkThatCouldNotSeeEveryReadingDoesNotSettleTheLine() {
        List<SearchCoverage> narrowed = drawnBy(resolved(compiled(NARROWED), "example.narrowed",
                new GenerationScope.Behavior("held")), "Code").stream()
                .filter(each -> each instanceof PointResolution.Unresolved)
                .map(each -> ((PointResolution.Unresolved) each).coverage()).toList();

        assertFalse(narrowed.isEmpty(), "the reading this asked about composed nothing");
        assertTrue(narrowed.stream().noneMatch(SearchCoverage::walkedEveryReading),
                "a reading of the line was left out of the walk: " + narrowed);
        assertTrue(narrowed.stream().noneMatch(SearchCoverage::provesTheLineCannotBeWritten),
                "so nothing here says a row cannot be written at the line: " + narrowed);
    }

    /**
     * Where more than one reading can compose the row, the module's declaration order settles which.
     *
     * <p>Not the order the requests happened to arrive in, and not what some earlier caller had
     * already paid to search. Both are readings of one line and either row settles it, so what is
     * left to decide is which one a person is handed — and that has to be the same on every run of
     * one model.
     *
     * <p>And it is settled relative to what was asked. A request about {@code second} is a different
     * question from a request about the module, so "the first that composed one" is the first of the
     * readings the request admits — read off the module regardless, the request would be handed a
     * row written in another behavior's terms.
     */
    @Test
    void theFirstReadingTheRequestAdmitsComposesTheRow() {
        Compilation compilation = compiled(EITHER);

        assertEquals(List.of("first"), composedBy(drawnBy(
                        resolved(compilation, "example.either", new GenerationScope.Module()),
                        "Code")),
                "the module declares `first` before `second`");
        assertEquals(List.of("second"), composedBy(drawnBy(
                        resolved(compilation, "example.either",
                                new GenerationScope.Behavior("second")), "Code")),
                "and a request about `second` is answered from `second`");
    }

    /**
     * A behavior carrying the type twice is two readings of the line, and both are accounted for.
     *
     * <p>What a search of one position came to is a fact about that position — the rules reaching
     * it, the values its decoder took — so a coverage keyed by the behavior holds one of the two
     * answers and which one is whichever the search walked first. Which is the shape a debt-level
     * answer exists to refuse, one level down: the terminal claim about the line is released on
     * every reading having been walked, and a walk that recorded one of two positions has not
     * walked them.
     */
    @Test
    void oneBehaviorCarryingTheTypeTwiceIsTwoReadings() {
        List<SearchCoverage> coverage = drawnBy(
                resolved(compiled(TWICE), "example.twice", new GenerationScope.Module()), "Code")
                .stream()
                .filter(each -> each instanceof PointResolution.Unresolved)
                .map(each -> ((PointResolution.Unresolved) each).coverage()).toList();

        assertFalse(coverage.isEmpty(), "the model under test composes nothing at the line");
        for (SearchCoverage each : coverage) {
            assertEquals(List.of("one", "one"), each.readings().stream()
                            .map(BorderObligationPointAssessment.Reading::behavior).toList(),
                    "one behavior, at every point of the line");
            assertEquals(List.of("String.length(x.a)", "String.length(x.b)"),
                    each.readings().stream().map(r -> r.target().left()).toList(),
                    "both positions it meets the line at");
            assertEquals(2, java.util.Set.copyOf(each.readings()).size(),
                    "and they are two readings, which is what a walk of them has to see");
        }
        // Both points of the line, each searched at both positions. The line and the run beside it
        // are two rows to write and neither is composable here, so each of them is answered from
        // both readings rather than from whichever the walk reached first.
        assertEquals(List.of(
                        List.of("String.length(x.a) = 1", "String.length(x.b) = 1"),
                        List.of("1 < String.length(x.a)", "1 < String.length(x.b)")),
                coverage.stream()
                        .map(each -> each.attempted().stream()
                                .map(souther.compiler.partition.Generator
                                        .UnresolvedCombination::subject)
                                .toList())
                        .toList(),
                "and what each of them said, in its own position's words");
    }

    /** One line, met twice by one behavior, and composable at neither position. */
    private static final String TWICE = """
            module example.twice

            data Code = String
                invariant nonempty = String.length(value) >= 1

            data Both = { a: Code, b: Code }
                invariant fixedA = String.matches("[a-z][a-z][a-z]+", a.value)
                invariant fixedB = String.matches("[a-z][a-z][a-z]+", b.value)

            data Ok

            behavior one : (x: Both) -> Ok
            let one (x) = Ok
            """;

    /**
     * Two lines one clause drew are searched apart.
     *
     * <p>A clause with both ends draws two lines — {@code >= 1 && <= 3} is one at each — and they
     * are two debts, not one thing with two sides (issue #1062). So which reading composes the row
     * for one of them says nothing about the other: {@code Narrow} takes three letters or more,
     * which refuses the value at the bottom of {@code Code}'s range and takes the one at the top.
     * Resolved per clause, a line would be handed to an author as a single piece of work whose two
     * ends are owed at different positions.
     *
     * <p>The point against a line and the run beside it are kept apart the same way, and each of the
     * four is resolved on its own. Asserted over the two against the lines, because those are the
     * two the model was written to put at different readings.
     */
    @Test
    void twoLinesOneClauseDrewAreSearchedApart() {
        Map<souther.compiler.partition.BorderObligationPoint, String> composers =
                new java.util.LinkedHashMap<>();
        resolved(compiled(EITHER_END), "example.ends", new GenerationScope.Module()).resolved()
                .forEach((at, answer) -> {
                    if (answer.resolution() instanceof PointResolution.Generated(var composedAt,
                            var _)
                            && at.line().owedToTheDeclaration()
                                    .map(on -> on.name().equals("Code")).orElse(false)) {
                        composers.put(at, composedAt.behavior());
                    }
                });
        Map<souther.compiler.partition.BorderObligationPoint, String> atTheLines = composers
                .entrySet().stream().filter(each -> each.getKey().role().againstTheLine())
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (one, _) -> one, java.util.LinkedHashMap::new));

        assertEquals(2, atTheLines.size(),
                "one clause drew two lines and a row stands at each: " + composers);
        assertEquals(2, java.util.Set.copyOf(atTheLines.values()).size(),
                "and the two were composed at different readings: " + atTheLines);
    }

    /** Which reading composed each row, in the order the points were resolved. */
    private static List<String> composedBy(List<PointResolution> resolutions) {
        return resolutions.stream()
                .filter(each -> each instanceof PointResolution.Generated)
                .map(each -> ((PointResolution.Generated) each).composedBy())
                .distinct().toList();
    }

    /** One line, at two positions either of which can hold a row at it. */
    private static final String EITHER = """
            module example.either

            data Code = String
                invariant nonempty = String.length(value) >= 1

            data A = { c: Code }
            data B = { c: Code }

            data Ok

            behavior first : (a: A) -> Ok
            let first (a) = Ok

            behavior second : (b: B) -> Ok
            let second (b) = Ok
            """;

    /** A line with a value at each end, and a reading that can hold one of them and not the other. */
    private static final String EITHER_END = """
            module example.ends

            data Code = String
                invariant sized = String.length(value) >= 1 && String.length(value) <= 3

            data Narrow = { c: Code }
                invariant fixed = String.matches("[a-z][a-z][a-z]+", c.value)

            data Wide = { c: Code }

            data Ok

            behavior held : (n: Narrow) -> Ok
            let held (n) = Ok

            behavior anywhere : (w: Wide) -> Ok
            let anywhere (w) = Ok
            """;

    /**
     * A block says what a reading came to as that reading's, and says nothing about the line.
     *
     * <p>The sentence a reader may act on is that no row can be written at the line, and it takes
     * every reading of it having been searched. A request about {@code held} leaves {@code
     * anywhere} unwalked — and {@code anywhere} is where the row is — so whatever {@code held}
     * found is a fact about {@code held}. Written under the declaration's own name, an author is
     * told a row cannot be written where the next behavior writes one.
     */
    @Test
    void whatOneReadingCameToIsSaidAsThatReadings() {
        Compilation compilation = compiled(NARROWED);

        String block = GeneratedRows.of(compilation, "example.narrowed", "held", true,
                SourceNameResolver.identity()).text();

        assertTrue(block.contains("in `held`"),
                "what the reading this asked about came to, named as its own: " + block);
        assertFalse(block.contains("no row can be written at"),
                "and nothing said about the line, which this walk did not see the whole of: "
                        + block);
    }

    private static BorderAccount resolved(Compilation compilation, String module,
                                         GenerationScope scope) {
        return Adequacy.accountFor(compilation.db(), module, scope);
    }

    /** The same line, with the reading that can compose nothing at it declared first. */
    private static final String HELD_FIRST = """
            module example.held

            data Code = String
                invariant nonempty = String.length(value) >= 1

            data Narrow = { c: Code }
                invariant fixed = String.matches("[a-z][a-z][a-z]+", c.value)

            data Wide = { c: Code }

            data Ok

            behavior held : (n: Narrow) -> Ok
            let held (n) = Ok

            behavior anywhere : (w: Wide) -> Ok
            let anywhere (w) = Ok
            """;


    /**
     * One line, at a position one behavior can hold a row at and the other cannot.
     *
     * <p>Both readings owe a row at {@code String.length(value) = 1} — the line cuts inside what
     * either position admits — and only one of them can hold one: {@code Narrow} takes three
     * letters or more, so every one-character value composed at {@code held} is refused where it is
     * constructed, and the plain field at {@code anywhere} takes it.
     */
    private static final String NARROWED = """
            module example.narrowed

            data Code = String
                invariant nonempty = String.length(value) >= 1

            data Wide = { c: Code }

            data Narrow = { c: Code }
                invariant fixed = String.matches("[a-z][a-z][a-z]+", c.value)

            data Ok

            behavior anywhere : (w: Wide) -> Ok
            let anywhere (w) = Ok

            behavior held : (n: Narrow) -> Ok
            let held (n) = Ok
            """;


    /** A guard on an input, whose line is the behavior's own and is owed a row either side. */
    private static final String GUARDED = """
            module example.guarded

            data Ok
            data No
            data Verdict = Ok | No

            behavior band : (days: Int) -> Verdict
            let band (days) = {
                guard days <= 30 else No
                Ok
            }

            example band
                | "well inside" : (5) -> Ok
            """;

    /** A body whose decisions read two positions together, so the combinations reach its arms. */
    private static final String SHIPPING = """
            module example.shipping

            data Membership = Premium | Standard
            data Delivery = Express | Regular

            data Fee = Int
                invariant value >= 0

            behavior shippingFee : (member: Membership, delivery: Delivery) -> Fee
                constructs Fee

            let baseFee (tier: Membership): Int =
                match tier with
                    | Premium -> 0
                    | Standard -> 500

            let expressFee (speed: Delivery): Int =
                match speed with
                    | Express -> 500
                    | Regular -> 0

            let shippingFee (member, delivery) =
                Fee(baseFee(member) + expressFee(delivery))

            example shippingFee
                | "a premium express parcel" : (Premium, Express) -> Fee(500)
            """;

    /**
     * An arm a combination of the body's own decisions takes is answered with the row that takes it.
     *
     * <p>Two searches used to be two worlds: the combinations composed rows before any finding was
     * consulted, and the finding about an arm was told nothing composes an input for one. Both were
     * true and they were about the same rows — a combination is a way through each of the forks it
     * reads, so a row composed for one takes an arm of each.
     */
    @Test
    void anArmACombinationTakesIsAnsweredWithTheRowThatTakesIt() {
        Compilation compilation = compiled(SHIPPING);
        List<Adequacy.GenerationDisposition> arms =
                filling(compilation, "example.shipping", "shippingFee").generation().stream()
                        .filter(d -> d.finding().kind() == Adequacy.Kind.ARM_UNREACHED).toList();

        assertFalse(arms.isEmpty(), "no row is written, so every arm is unreached");
        assertTrue(arms.stream().allMatch(
                        d -> d.outcome() instanceof GenerationOutcome.Generated),
                "each is answered with the row composed for a combination that takes it: " + arms);
    }

    /**
     * An arm nothing was composed for is answered from the reading of the body, whatever the
     * positions came to.
     *
     * <p>The way into this arm is a comparison between a value the body works out and a position
     * beside it, and the ways to such a value could not all be written down — so the reading has an
     * answer about the arm before any position is looked at. No position of this model is divided
     * either, and the search over the classes has nothing to walk; read as though that were what
     * happened to the arm, the answer was that a strategy took the arm and recorded neither a row
     * nor a reason, which is this compiler failing to say rather than anything about the model.
     */
    @Test
    void anArmNothingWasComposedForIsAnsweredFromTheReadingOfTheBody() {
        Compilation compilation = compiled(POLICY);
        List<Adequacy.GenerationDisposition> arms =
                filling(compilation, "example.policy", "fee").generation().stream()
                        .filter(d -> d.finding().kind() == Adequacy.Kind.ARM_UNREACHED).toList();

        assertEquals(1, arms.size());
        assertEquals(new GenerationOutcome.NotSupported(
                        GenerationOutcome.NotSupported.Reason.THE_WAYS_INTO_THIS_ARM_ARE_NOT_ENUMERABLE),
                arms.get(0).outcome(),
                "the reading says the ways into this arm are not enumerable: " + arms);
    }

    /** A fork inside a block, whose body runs where something applies it. */
    private static final String IN_A_BLOCK = """
            module example.block

            data Flag = On | Off

            behavior mark : (flags: List<Flag>) -> List<Int>

            let mark (flags) = List.map(f ->
                match f with
                    | On -> 1
                    | Off -> 0, flags)

            example mark
                | "one on" : ([On]) -> [1]
            """;

    /**
     * An arm no strategy reaches is {@link GenerationOutcome.NotSupported} rather than unanswered.
     *
     * <p>What steers a row into this one is what the thing applying the block applies it to, which
     * is not a class of this behavior's inputs — so there is nothing for a search to work from, and
     * that is a fact about what this compiler can state rather than about the body.
     */
    @Test
    void anArmNoStrategyReachesIsNotSupportedRatherThanUnanswered() {
        Compilation compilation = compiled(IN_A_BLOCK);
        List<Adequacy.GenerationDisposition> arms =
                filling(compilation, "example.block", "mark").generation().stream()
                        .filter(d -> d.finding().kind() == Adequacy.Kind.ARM_UNREACHED).toList();

        assertFalse(arms.isEmpty(), "the arm the row does not take is found");
        assertTrue(arms.stream().allMatch(
                        d -> d.outcome() instanceof GenerationOutcome.NotSupported),
                "no strategy composes an input to reach an arm inside a block: " + arms);
    }

    // --- a case of an input, which is the one gap the three answers all reach ---------------------

    /** A sum of unit cases, one of which no row applies the behavior to. */
    private static final String KIND = """
            module example.kind

            data Domestic
            data Overseas
            data Kind = Domestic | Overseas

            data Fine
            data Broke
            data Verdict = Fine | Broke

            behavior pick : (k: Kind) -> Verdict

            let pick (k) = match k with
                | Domestic -> Fine
                | Overseas -> Fine

            example pick
                | "home" : (Domestic) -> Fine
            """;

    /**
     * The same, with a case an axis divides and no value gets past its rules.
     *
     * <p>The case is composed field by field like any other and every candidate is refused at
     * construction, so what comes back is a search that ran and a reason for what it found.
     */
    private static final String CARRIED = """
            module example.pay

            data Amount = Decimal
                invariant value >= 0.0m

            data Card = { holder: Amount }
                invariant notFree = holder.value /= 0.0m
            data Cash = { amount: Amount }
            data Payment = Card | Cash

            data Fine
            data Broke
            data Verdict = Fine | Broke

            behavior feeFor : (p: Payment) -> Verdict

            let feeFor (p) = match p with
                | Card -> Fine
                | Cash -> Fine

            example feeFor
                | "cash" : (Cash { amount = Amount(0.0m) }) -> Fine
            """;

    /** More divided positions than the partition keeps axes for, so the last ones have none. */
    private static final String WIDE = wide(13);

    private static String wide(int positions) {
        StringBuilder names = new StringBuilder();
        StringBuilder declared = new StringBuilder();
        StringBuilder written = new StringBuilder();
        for (int at = 1; at <= positions; at++) {
            names.append(at == 1 ? "" : ", ").append("k").append(at);
            declared.append(at == 1 ? "" : ", ").append("k").append(at).append(": Kind");
            written.append(at == 1 ? "" : ", ").append("Domestic");
        }
        return """
                module example.wide

                data Domestic
                data Overseas
                data Kind = Domestic | Overseas

                data Fine
                data Broke
                data Verdict = Fine | Broke

                behavior pick : (%s) -> Verdict

                let pick (%s) = Fine

                example pick
                    | "all home" : (%s) -> Fine
                """.formatted(declared, names, written);
    }

    /** The answer for the case named {@code missing} of the input at {@code at}, one-based. */
    private static GenerationOutcome forCase(Compilation compilation, String module,
                                             String behavior, String missing, int at) {
        return filling(compilation, module, behavior).generation().stream()
                .filter(each -> each.finding().about()
                        instanceof souther.compiler.query.About.ACaseNoRowAppliesItTo(
                                var input, var case_)
                        && case_.name().equals(missing) && input.at() + 1 == at)
                .map(Adequacy.GenerationDisposition::outcome)
                .findFirst().orElseThrow(() -> new AssertionError("no gap for " + missing));
    }

    @Test
    void aCaseAnAxisDividesAndAValueComposesForIsAnsweredWithARow() {
        GenerationOutcome answer = forCase(compiled(KIND), "example.kind", "pick", "Overseas", 1);

        assertInstanceOf(GenerationOutcome.Generated.class, answer);
        assertEquals(List.of("Overseas"),
                ((GenerationOutcome.Generated) answer).candidates().stream()
                        .map(row -> row.inputs().get(0).text()).toList());
    }

    /**
     * A case an axis divides and nothing composed a value for is answered by the attempt.
     *
     * <p>Not {@code NotSupported}: the strategy took this class and came back with a reason, which is
     * news of a different kind. Reading it as "no strategy for this" would say the generator will
     * never offer a row at a class it took and searched.
     *
     * <p>A card has values — any amount above none is one — and the search proposes the end of the
     * range, which is the one amount the rule refuses. So this is what the search could not reach and
     * not what has no value: a type with none is refused before any of this, and a fixture that had
     * none would be asking the generator about a model that does not compile. Should the search learn
     * to step off an end it was refused at, this becomes a row, which is the answer changing for the
     * reason it should.
     */
    @Test
    void aCaseNothingComposedAValueForIsAnsweredByTheAttempt() {
        GenerationOutcome answer = forCase(compiled(CARRIED), "example.pay", "feeFor", "Card", 1);

        assertInstanceOf(GenerationOutcome.CannotGenerate.class, answer);
    }

    /**
     * Every case of a sum parameter is offered a row, however many positions the behavior takes.
     *
     * <p>What used to be here asserted the other half of this: a limit on how many positions one
     * behavior is measured at left the thirteenth without an axis, and its cases were answered
     * {@code NO_AXIS_AT_THIS_POSITION}. That limit is gone (#969) and with it the only reading that
     * produced a case at a position nothing divided, so what is left to hold is that the thirteenth
     * is offered a row like the first.
     */
    @Test
    void everyCaseOfASumParameterIsOfferedARow() {
        Compilation compilation = compiled(WIDE);

        assertInstanceOf(GenerationOutcome.Generated.class,
                forCase(compilation, "example.wide", "pick", "Overseas", 1),
                "the first position is divided and offered");
        assertInstanceOf(GenerationOutcome.Generated.class,
                forCase(compilation, "example.wide", "pick", "Overseas", 13),
                "and so is the thirteenth");
    }

    /**
     * And what each of them says is what is missing here.
     *
     * <p>The words, because the words are the contract at this point. This type's own javadoc says
     * these are strategies that could exist and do not, and that none of them says the gap cannot
     * be met — while the sentence for an arm said nothing composes an input for it, of a row an
     * author writes in two lines (issue #643). A reader who writes that row must not be
     * contradicting the report.
     *
     * <p>Held one by one rather than by a rule over the strings, because there is no property of a
     * sentence that says it is about a compiler. What there is, is a person reading each of them
     * next to what it is written for.
     */
    @Test
    void eachReasonSaysWhatIsMissingHereRatherThanWhatCannotExist() {
        assertEquals("a row is steered into an arm by the decisions that hold on the way there, and"
                        + " the fork this arm is of is one nothing could name a position for",
                GenerationOutcome.NotSupported.Reason.NO_WAY_INTO_THIS_ARM_CAN_BE_NAMED.said());
        assertEquals("the ways into this arm run past what one reading of the body holds at once,"
                        + " so the reading stopped short of it rather than saying what steers a row"
                        + " there",
                GenerationOutcome.NotSupported.Reason.MORE_WAYS_IN_THAN_THE_READING_HOLDS.said());
        assertEquals("rows here are composed from what the input positions divide into, and nothing"
                        + " searches for one by the case it would answer with",
                GenerationOutcome.NotSupported.Reason.NO_STRATEGY_FOR_AN_OUTPUT_CASE.said());
        // A case whose parameter no axis was derived at has its cases in the declaration and in no
        // partition, so what is missing is a derivation and not the classes. Said as there being
        // none, the sentence is about the model again — the thing the other two stopped doing.
        assertEquals("no axis was derived at the position this case belongs to, so no classes were"
                        + " derived there to compose a row from",
                GenerationOutcome.NotSupported.Reason.NO_AXIS_AT_THIS_POSITION.said());
    }

    @Test
    void aCaseOfTheOutputIsNotSupported() {
        List<GenerationOutcome> outputs = filling(compiled(KIND), "example.kind", "pick").generation()
                .stream()
                .filter(each -> each.finding().kind() == Adequacy.Kind.OUTPUT_CASE_UNSPECIFIED)
                .map(Adequacy.GenerationDisposition::outcome).toList();

        assertEquals(List.of(new GenerationOutcome.NotSupported(
                        GenerationOutcome.NotSupported.Reason.NO_STRATEGY_FOR_AN_OUTPUT_CASE)),
                outputs, "nothing searches for an input by the case it would answer with");
    }

    // --- classes that were not there, and classes that would not link -----------------------------

    private static String saidAbout(souther.compiler.partition.GenerationReason why,
                                    souther.compiler.partition.GenerationReason alsoAtTheEdges) {
        // The composition and not what is offered: these fillings are built here rather than
        // searched for, so there is no store to ask what their rows would settle.
        return GeneratedRows.of(souther.compiler.query.EveryRowOfIt.offered(
                        souther.compiler.query.Composition.composed(
                        souther.compiler.query.OfferingRequest.overTheModule("example.kind", true),
                        Map.of("pick", new Adequacy.Filling(stopped(why),
                                atTheEdges(alsoAtTheEdges),
                                Adequacy.Generated.RowsForRules.NOTHING, List.of())), null)),
                Map.of(), SourceNameResolver.identity(), compiled(KIND).db()).text();
    }

    /** A run asked for nothing that came to a reason about itself, which is what a stopped
     *  generation is when nothing was owed. */
    private static souther.compiler.partition.FillResult stopped(
            souther.compiler.partition.GenerationReason why) {
        souther.compiler.partition.GenerationPlan plan =
                new souther.compiler.partition.GenerationPlan(nothingIsDivided(), List.of(),
                        List.of());
        return why == null ? souther.compiler.partition.FillResult.nothingAskedOf(plan)
                : new souther.compiler.partition.FillResult(plan, new java.util.LinkedHashMap<>(),
                        List.of(), List.of(why),
                        souther.compiler.partition.Discharge.NOTHING);
    }

    /** The same at the boundaries, which nothing is owed at and which has no plan. */
    private static souther.compiler.partition.Generator.GenerationResult atTheEdges(
            souther.compiler.partition.GenerationReason why) {
        return why == null ? souther.compiler.partition.Generator.GenerationResult.NONE
                : new souther.compiler.partition.Generator.GenerationResult(List.of(), List.of(),
                        List.of(why));
    }

    private static souther.compiler.partition.MeasuredInput nothingIsDivided() {
        RuleReadingSource rules = RuleReadings.ofNoClauseFiled(
                souther.compiler.check.Symbols.none(DefaultStdlib.get()));
        return souther.compiler.partition.MeasuredInput.of("pick",
                souther.compiler.inputs.InputDomain.of(List.of(), rules,
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES).reading(rules),
                souther.compiler.partition.AxesATestWrote.asAMeasurement("pick", List.of()));
    }

    /**
     * Classes that would not link are not classes that were not there.
     *
     * <p>The assessment records which of the two it met, and both leave no row to offer. That is all
     * they share: a sentence saying the classes were not there, printed where they were built and
     * could not be reached, is this compiler choosing which of the things it saw to report.
     */
    @Test
    void classesThatWouldNotLinkAreNotClassesThatWereNotThere() {
        String linked = saidAbout(
                new souther.compiler.partition.GenerationReason.LinkageFailed("pick"), null);
        String absent = saidAbout(
                new souther.compiler.partition.GenerationReason.NothingToBuildAgainst("pick"), null);

        assertFalse(linked.isBlank(), "the one it met is said");
        assertFalse(absent.isBlank(), "and so is the other");
        assertNotEquals(absent, linked,
                "two reasons reported in one sentence are one reason:\n" + linked);
    }

    /**
     * One reason both searches stopped for is said once.
     *
     * <p>Nothing to put a candidate through stops the pair search and the edges alike, and the two
     * results carry it separately. Printed from each, a reader is told twice that generation stopped
     * and reads two things having gone wrong.
     */
    @Test
    void oneReasonBothSearchesStoppedForIsSaidOnce() {
        souther.compiler.partition.GenerationReason why =
                new souther.compiler.partition.GenerationReason.LinkageFailed("pick");

        assertEquals(saidAbout(why, null), saidAbout(why, why),
                "the second search stopping for the reason the first did adds nothing to say");
    }

    @Test
    void anArmNoStrategyReachesIsNamedInTheBlock() {
        Compilation compilation = compiled(POLICY);
        String block = GeneratedRows.of(Adequacy.offeredFor(compilation.db(),
                        souther.compiler.query.OfferingRequest.overTheModule(
                                "example.policy", true)),
                Map.of(), SourceNameResolver.identity(), compilation.db()).text();

        assertTrue(block.contains("`then`"),
                "the arm nothing offers a row for is named: " + block);
    }
}
