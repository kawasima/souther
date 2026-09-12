package souther.compiler;

import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Measurement;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.ItemAssessment;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What says a row can be written at a boundary, and what a refusal is not.
 *
 * <p>Three things can say it and one thing cannot. A projection that read every rule of the value
 * proves it; a row already at the value witnesses it; a value built through the module's own decoder
 * witnesses it. The decoder refusing every candidate it tried says nothing — another value of the
 * same edge may build — so a refusal leaves the edge unknown and never closes it.
 *
 * <p>Held here rather than through the report, because the report prints only the two ends of this:
 * a line it counts and a line it says it cannot promise. Which of the three settled a line, and that
 * a refusal settled nothing, are the facts the counting is derived from, and a test that read them
 * off the printed line could not tell a witness from a proof.
 */
class AnEdgeIsWritableBecauseSomethingSaidSoTest {

    /**
     * A clause placing an end at a value the clause beside it takes away.
     *
     * <p>{@code within} writes the bottom of the range at 0 and {@code nonzero} refuses the 0, and
     * the two are read by different readings: an end is placed by whichever clause states one, and a
     * denial states none. So the end as written is at 0 and the position starts at 1, and the line
     * this position has is the one at 1 — a line at 0 is a line at no value of {@code N}, and asking
     * whether a row can be written there asks about a value the model excludes.
     *
     * <p>And both of them are owed a row there. Taking {@code nonzero} away leaves the values from
     * 0 and taking the floor away leaves them stopping nowhere, so each holds the line at 1 and
     * each is a clause an author could rewrite to move it. Owed to the clause that wrote a bound
     * alone, the rule that put the line where it is would owe nothing for it.
     */
    private static final String HOLED = """
            module example.holed

            data N = Int
                invariant within = value >= 0 && value <= 10
                invariant nonzero = value /= 0

            data Ok

            behavior f : (n: N) -> Ok

            let f (n) = Ok

            example f
                | "x" : (N(3)) -> Ok
            """;

    @Test
    void anEndTakenAwayByAClauseBesideItIsALineAtTheValueTheRulesLeave() {
        assertEquals(List.of("1", "1", "10"), valuesAt(HOLED, "example.holed", "f"),
                "the position starts at 1, so that is where the line is and 0 is no line of it —"
                        + " and the two clauses holding it there are each owed a row");

        ItemAssessment.Owed at = assessmentAt(HOLED, "example.holed", "f", "1");
        ItemAssessment.WritabilityEvidence evidence = at.writabilityEvidence();
        assertTrue(evidence.has(ItemAssessment.WritabilityEvidence.Ground.A_VALUE_WAS_BUILT),
                "and a value at it went through the decoder");
        assertTrue(evidence.has(ItemAssessment.WritabilityEvidence.Ground.THE_RULES_PROVE_IT),
                "which the rules already proved, and the two stand together rather than one of them"
                        + " standing for both");
    }

    @Test
    void anEdgeTheSameRuleDoesNotReachIsWitnessedByTheValueThatWasBuilt() {
        ItemAssessment.Owed at = assessmentAt(HOLED, "example.holed", "f", "10");

        assertTrue(at.writabilityEvidence().has(ItemAssessment.WritabilityEvidence.Ground.A_VALUE_WAS_BUILT),
                "a value at 10 went through the decoder");
        assertInstanceOf(ItemAssessment.Attempt.Built.class, at.searches().only(),
                "and the value it built is kept, because it is also the row an author is offered");
    }

    /**
     * A projection's proof and a search that came back with nothing are both true at once.
     *
     * <p>Two answers about two different things: whether a value at the edge exists, and whether this
     * run managed to produce one. Reading the second off the first is what left `--generate` silent
     * about a boundary it could say something useful about — the report named the row as owed and the
     * block printed neither a row nor a reason.
     *
     * <p>Every rule of {@code Amount} was read, so 0 is proven to be a value it holds. The row needs a
     * second input as well, and nothing can be written for that one — which is a fact about this
     * search and not about the edge.
     */
    private static final String PROVEN_BUT_UNREACHED = """
            module example.proven

            data Amount = Int
                invariant value >= 0

            data Code = String
                invariant String.matches("(?=.*a).*b", value)

            data Ok

            behavior place : (amount: Amount, code: Code) -> Ok

            let place (amount, code) = Ok

            example place
                | "some" : (Amount(7), Code("ab")) -> Ok
            """;

    @Test
    void anEdgeTheProjectionProvesCanStillBeOneNoSearchReached() {
        ItemAssessment.Owed at =
                assessmentAt(PROVEN_BUT_UNREACHED, "example.proven", "place", "0");

        assertInstanceOf(ItemAssessment.Coverage.NoHit.class,
at.coverage().made().orElseThrow());
        assertEquals(List.of(ItemAssessment.WritabilityEvidence.Ground.THE_RULES_PROVE_IT),
                at.writabilityEvidence().grounds().written(),
                "every rule of `Amount` was read, so 0 is a value it holds, and that is the whole"
                        + " of what showed it");
        assertInstanceOf(ItemAssessment.Attempt.Unresolved.class, at.searches().only(),
                "and the search still came back with nothing, which takes nothing away from that");
    }

    /**
     * The same boundary, as the block an author reads.
     *
     * <p>This is what recovering the attempt from the verdict cost: the report named the row as owed
     * and the block said nothing at all, because the verdict was "provable" and the attempt that had
     * failed was no longer anywhere to be read.
     */
    @Test
    void anEdgeNoSearchReachedIsStillSaidInTheBlock() {
        Compilation compilation = Compilation.ofSource(PROVEN_BUT_UNREACHED, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();

        String block = souther.compiler.report.GeneratedRows.of(
                compilation, "example.proven", "place", SourceRendering.namedByIdentity(compilation.texts())).text();

        // In the behavior, because that is what this says. Every value tried at the point was
        // refused where a `Yen` is constructed, which is a fact about that reading and not about
        // the line — another reading of it may compose a row. The declaration's own name is
        // reserved for the sentence a walk over every reading licenses (issue #1076).
        assertTrue(block.contains("no row for `amount = 0` in `place`"), block);
        assertTrue(block.contains("every value tried was refused"), block);
    }

    /**
     * A rule about another position does not disqualify this one.
     *
     * <p>{@code String.matches} on the identifier is outside the fragment the projection reads, so
     * nothing about this value is proven — and it cannot refuse an amount whatever it does to an id.
     * Which is the shape most of a real model has: one clause somewhere in a record used to leave
     * every numeric edge in it unpromised.
     */
    @Test
    void anUnreadRuleOnAnotherFieldDoesNotLeaveThisEdgeUnknown() {
        String model = """
                module example.order

                data OrderId = String
                    invariant String.matches("[0-9]{4}", value)

                data Amount = Int
                    invariant value >= 0

                data Order = { id: OrderId, amount: Amount }

                data Ok

                behavior place : (order: Order) -> Ok

                let place (order) = Ok

                example place
                    | "some" : (Order { id = OrderId("0001"), amount = Amount(7) }) -> Ok
                """;
        assertTrue(writabilityAt(model, "example.order", "place", "0")
                        .has(ItemAssessment.WritabilityEvidence.Ground.A_VALUE_WAS_BUILT),
                "the id's rule cannot refuse an amount of 0");
    }

    /** A row at the value settles it, and nothing is built to find out what the row already shows. */
    @Test
    void aRowAtTheValueIsTheWitnessAndNothingIsBuilt() {
        String model = """
                module example.at

                data N = Int
                    invariant within = value >= 0 && value <= 10
                    invariant nonzero = value /= 3

                data Ok

                behavior f : (n: N) -> Ok

                let f (n) = Ok

                example f
                    | "bottom" : (N(0)) -> Ok
                """;
        ItemAssessment.Owed at = assessmentAt(model, "example.at", "f", "0");
        assertInstanceOf(ItemAssessment.Coverage.Hit.class,
at.coverage().made().orElseThrow());
        assertTrue(at.writabilityEvidence().has(ItemAssessment.WritabilityEvidence.Ground.A_ROW_IS_AT_IT),
                "the row is the witness");
        assertFalse(at.worthSearching(),
                "and a value that is already there is not worth building one for");
        assertFalse(at.searches().ran(),
                "so nothing was searched for, which is said by there being no search");
    }

    /**
     * A behavior no row names has no coverage answer and may still have a writability one.
     *
     * <p>Two observations on two axes, and the report used to print the second as though it were the
     * first — a behavior whose only problem was that nobody had written a row yet was reported under
     * "not known to be writable", beside a note saying the line was never measured.
     */
    @Test
    void aBehaviorNoRowNamesIsUnmeasuredAndNotUnwritable() {
        String model = """
                module example.unnamed

                data N = Int
                    invariant within = value >= 0 && value <= 10

                data Ok

                behavior f : (n: N) -> Ok

                let f (n) = Ok

                data Other

                behavior g : (n: N) -> Other

                let g (n) = Other

                example g
                    | "some" : (N(4)) -> Other
                """;
        ItemAssessment.Owed at = assessmentAt(model, "example.unnamed", "f", "0");
        assertEquals(ItemAssessment.Coverage.NotAsked.NO_ROWS,
                assertInstanceOf(Measurement.NotMeasured.class, at.coverage()).why());
        assertTrue(at.writabilityEvidence().known(),
                "nobody wrote a row, which says nothing about whether one could be written");
        assertFalse(at.writabilityEvidence().has(ItemAssessment.WritabilityEvidence.Ground.A_ROW_IS_AT_IT),
                "and a measurement nobody made puts no row at the point");
        assertInstanceOf(ItemAssessment.Attempt.Built.class, at.searches().only(),
                "a value was built here, and what it settles is the writability and not the rows");
    }

    /**
     * A line waiting on the arms is a line nothing is built for.
     *
     * <p>Meeting a guard's line takes the comparison having run, which nothing measures until the
     * build asks for the arms. Until then no row there is owed to anybody, and building a candidate
     * would hand somebody a specific piece of work on the strength of a question nobody put.
     */
    @Test
    void aLineWaitingOnTheArmsIsALineNothingIsBuiltFor() {
        String model = """
                module example.waiting

                data N = Int
                    invariant within = value >= 0 && value <= 10

                data Ok
                data Big

                behavior f : (n: N) -> Ok | Big

                let f (n) = if n.value <= 4 then Ok else Big

                example f
                    | "some" : (N(2)) -> Ok
                """;
        Compilation compilation = Compilation.ofSource(model, "Main");
        // What a guard's line waits on, and this build does not ask for it.
        compilation.measure(Adequacy.Asked.reportOnly(Adequacy.Level.WITNESS));
        compilation.answerEverything();
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.boundariesOf(compilation.db(), "example.waiting");

        List<BorderAssessment.Point> guards =
                BorderAssessment.pointsOf(boundaries.get("f")).stream()
                        .filter(p -> p.border().origin().isWrittenRatherThanNamed())
                        .filter(p -> p.owed() != null).toList();
        assertFalse(guards.isEmpty(), "the comparison draws lines: " + boundaries.get("f"));
        for (BorderAssessment.Point at : guards) {
            assertEquals(ItemAssessment.Coverage.NotAsked.ARMS_NOT_ASKED,
                    assertInstanceOf(Measurement.NotMeasured.class,
                            at.owed().coverage()).why(), at.label());
            assertFalse(at.owed().worthSearching(), at.label());
            assertFalse(at.owed().searches().ran(), at.label());
        }
    }

    /** Every value a point against a line names, as the report names them. */
    private static List<String> valuesAt(String model, String module, String behavior) {
        return pointsAt(model, module, behavior).stream()
                .filter(p -> p.role().againstTheLine()).filter(p -> p.owed() != null)
                .map(BorderAssessment.Point::against).sorted().toList();
    }

    private static ItemAssessment.WritabilityEvidence writabilityAt(String model, String module,
                                                                    String behavior, String value) {
        return assessmentAt(model, module, behavior, value).writabilityEvidence();
    }

    /** The point against a line at {@code value}, which is what a row there is owed for. */
    private static ItemAssessment.Owed assessmentAt(String model, String module, String behavior,
                                                    String value) {
        return assessmentAt(model, module, behavior, value, Adequacy.Level.ALL);
    }

    private static ItemAssessment.Owed assessmentAt(String model, String module, String behavior,
                                                    String value, Adequacy.Level level) {
        return pointsAt(model, module, behavior, level).stream()
                .filter(p -> p.role().againstTheLine()).filter(p -> p.owed() != null)
                .filter(p -> value.equals(p.against()))
                .findFirst().map(BorderAssessment.Point::owed).orElseThrow(
                        () -> new AssertionError("no boundary at " + value + " of " + behavior));
    }

    /**
     * A level that composes no value falls back on what the rules prove, and says why it built none.
     *
     * <p>Composing a candidate costs a decoder run for each point it settles — sixteen seconds over
     * the corpus this was measured on, against a second for everything else a build at
     * {@code witness} does. That is not reading what the rows already established, so it is not what
     * the level promises, and it composes none (issue #955).
     *
     * <p>What it does not cost is the answer. Nothing a search does can take a proof away, so an
     * edge inside what every rule reaching it leaves is writable because the rules say so, whichever
     * level asked — and the row that is owed there is owed at both. What a composed value adds is a
     * witness for the edges the rules cannot reach, and what a person is offered to paste.
     *
     * <p>And the two nothings are told apart. Read off the same field, an edge nobody tried to build
     * at and an edge every value tried at was refused are one answer, and only the first is this
     * build's doing.
     */
    @Test
    void aLevelThatComposesNoValueStillReadsWhatTheRulesProve() {
        ItemAssessment.Owed built = assessmentAt(HOLED, "example.holed", "f", "1");
        assertTrue(built.writabilityEvidence().has(ItemAssessment.WritabilityEvidence.Ground.A_VALUE_WAS_BUILT),
                "at `all` a value at it went through the decoder");

        ItemAssessment.Owed unbuilt = assessmentAt(HOLED, "example.holed", "f", "1",
                Adequacy.Level.WITNESS);
        assertEquals(List.of(ItemAssessment.WritabilityEvidence.Ground.THE_RULES_PROVE_IT),
                unbuilt.writabilityEvidence().grounds().written(),
                "and the rules prove it whether or not anything was built, which is the one ground"
                        + " left when nothing was");
        assertTrue(unbuilt.worthSearching(),
                "a value here would have settled something, so the point is worth searching");
        assertFalse(unbuilt.searches().ran(),
                "and nobody asked for one: said by there being no search, not by a search that"
                        + " reports not having been asked for");
        assertInstanceOf(Measurement.Complete.class, unbuilt.coverage(),
                "the rows were read all the same: what is missing is the value, not the reading");
        assertTrue(unbuilt.coverage().made().orElseThrow() instanceof ItemAssessment.Coverage.NoHit
                        && unbuilt.writabilityEvidence().known(),
                "so the row is owed at both levels, and this one offers no value to write there");
    }

    private static List<BorderAssessment.Point> pointsAt(String model, String module,
                                                         String behavior) {
        return pointsAt(model, module, behavior, Adequacy.Level.ALL);
    }

    private static List<BorderAssessment.Point> pointsAt(String model, String module,
                                                         String behavior, Adequacy.Level level) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.reportOnly(level));
        compilation.answerEverything();
        // Which question the build puts, which is what the level says. A build that composes values
        // asks for the search; one that does not asks what the rows established and stops there, and
        // the difference shows as there being no attempt rather than as an attempt saying so.
        Map<String, List<BorderAssessment>> boundaries = level.composesValues()
                ? Adequacy.searchedBoundariesOf(compilation.db(), module)
                : Adequacy.boundariesOf(compilation.db(), module);
        assertNotNull(boundaries, "the model under test compiles");
        return BorderAssessment.pointsOf(boundaries.get(behavior));
    }

    /**
     * The same rule shape on the temporal carriers, which count as the whole numbers do.
     *
     * <p>What {@code HOLED} states of an {@code Int}, stated of a date and of a moment: a clause
     * bounds the value and a clause beside it refuses the value at that bound, so the line is at the
     * value the rules leave. A day and a second are counts with a next one, so each line steps by
     * one of its own unit.
     *
     * <p>Read on both, because they reach the generator by different writers and a count written
     * where a temporal belongs is refused by the decoder with nothing said about why. So the row at
     * the line is asked for here as well as the line's place.
     */
    private static final String TEMPORAL_EDGE_TAKEN_AWAY = """
            module example.temporal

            data Cutoff = Date
                invariant value >= Date("2026-01-01") && value /= Date("2026-01-01")
            data Moment = DateTime
                invariant value >= DateTime("2026-01-01T00:00:00")
                    && value /= DateTime("2026-01-01T00:00:00")

            data Ok

            behavior onADate : (c: Cutoff) -> Ok
            let onADate (c) = Ok

            behavior onAMoment : (m: Moment) -> Ok
            let onAMoment (m) = Ok

            example onADate
                | "some" : (Cutoff(Date("2026-06-01"))) -> Ok

            example onAMoment
                | "some" : (Moment(DateTime("2026-06-01T00:00:00"))) -> Ok
            """;

    /**
     * The line stands where the rules leave the values, one count along from the value refused.
     *
     * <p>Once per conjunct holding it there, which is both of them: the bound alone leaves the
     * value it names and the denial alone leaves the values stopping nowhere.
     */
    @Test
    void aTemporalEdgeTakenAwayIsALineAtTheValueTheRulesLeave() {
        assertEquals(List.of("2026-01-02", "2026-01-02"),
                valuesAt(TEMPORAL_EDGE_TAKEN_AWAY, "example.temporal", "onADate"),
                "a day is a count with a next one, so the line steps to it");
        assertEquals(List.of("2026-01-01T00:00:01", "2026-01-01T00:00:01"),
                valuesAt(TEMPORAL_EDGE_TAKEN_AWAY, "example.temporal", "onAMoment"),
                "and a moment steps by its second");
    }

    /** And a row is asked for there, which is what says the value is one a row can be written at. */
    @Test
    void aRowIsAskedForAtTheTemporalEdge() {
        for (String[] each : new String[][] {
                {"onADate", "Cutoff(Date(\"2026-01-02\"))"},
                {"onAMoment", "Moment(DateTime(\"2026-01-01T00:00:01\"))"}}) {
            Compilation compilation = Compilation.ofSource(TEMPORAL_EDGE_TAKEN_AWAY, "Main");
            compilation.measure(Adequacy.Asked.fullReport());
            compilation.answerEverything();
            String block = souther.compiler.report.GeneratedRows.of(
                    compilation, "example.temporal", each[0],
                    SourceRendering.namedByIdentity(compilation.texts())).text();

            assertTrue(block.contains(each[1]),
                    each[0] + ": a row is composed at the line: " + block);
            assertFalse(block.contains("no row for"),
                    each[0] + ": nothing here is a line no row could be written at: " + block);
        }
    }
}
