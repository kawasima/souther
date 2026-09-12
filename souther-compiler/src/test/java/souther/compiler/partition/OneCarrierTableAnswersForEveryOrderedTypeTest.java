package souther.compiler.partition;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each ordered type is measured at, written out rather than left to be inferred.
 *
 * <p>Which values a line can be drawn on used to be decided in several places, and they disagreed:
 * a {@code Date} was a carrier to the reader that drew a {@code guard}'s line and not to the one
 * that read an invariant's bound, so the same rule about the same position answered two ways
 * depending on where it was written. A table of the answers is what makes that visible — a
 * disagreement is two cells of one row differing, which nothing shorter than this shows.
 *
 * <p>Three numbers per cell and not one. How many positions the model divides, how many lines the
 * rules drew, and what the reading could not read are three separate answers, and a cell that got
 * the first right and the third wrong is what {@code not derivable} on a bounded position was.
 *
 * <p><b>Every carrier appears in both rows.</b> A rule written as an invariant and the same rule
 * written as a {@code guard} reach the measure through different readers, so a row missing from one
 * of them is a reader nothing holds to the other. The bare and the wrapped forms are both here for
 * the same reason: a newtype is the value it carries (spec §primitives), and a cell where the two
 * differ is a name changing what a rule means.
 */
class OneCarrierTableAnswersForEveryOrderedTypeTest {

    /**
     * One cell.
     *
     * @param axes        positions the model divides into classes
     * @param obligations lines some rule drew that a row is owed at
     * @param unread      what stopped a rule being read, or null where nothing did
     */
    private record Measured(int axes, int obligations, UndividedPosition.Reason unread) {}

    /** A carrier read all the way through: an axis, the line and the value beside it where the
     *  values step, and nothing left unread. */
    private static Measured read(int obligations) {
        return new Measured(1, obligations, null);
    }

    /**
     * A carrier read all the way through at a position whose type already states classes of its own.
     *
     * <p>The line is drawn and owes its rows; the classes stay the cases. An enumeration's cases are
     * a finer partition than any cut of it — {@code s < Qualified} leaves `{Prospecting}` and
     * `{Qualified, Won}`, and the meet of that with the three cases is the three cases — so the cut
     * adds no class, which is what tells this apart from the carriers whose type left the position
     * whole.
     */
    private static Measured readBesideItsClasses(int obligations) {
        return new Measured(1, obligations, null);
    }

    private static final String MODEL = """
            module example.matrix

            data Ok
            data No
            data Verdict = Ok | No

            data Prospecting
            data Qualified
            data Won
            data Stage = Prospecting | Qualified | Won

            data WholeN  = Int
            data DenseN  = Decimal
            data DayN    = Date
            data MomentN = DateTime
            data TextN   = String
            data TimeN   = Time
            data NanoN   = Instant
            data StageN  = Stage

            data WholeI  = Int      invariant value >= 100
            data DenseI  = Decimal  invariant value >= 0.5m
            data DayI    = Date     invariant value >= Date("2026-01-01")
            data MomentI = DateTime invariant value >= DateTime("2026-01-01T00:00:00")
            data TextI   = String   invariant value >= "2020-01"
            data TimeI   = Time     invariant value >= Time("09:00:00")
            data NanoI   = Instant  invariant value >= Instant("2026-01-01T00:00:00Z")
            data StageI  = Stage    invariant value >= Qualified

            behavior guardWholeBare : (x: Int) -> Verdict
            let guardWholeBare (x) = { guard x < 5000 else Ok
                No }

            behavior guardDenseBare : (x: Decimal) -> Verdict
            let guardDenseBare (x) = { guard x < 0.5m else Ok
                No }

            behavior guardDayBare : (x: Date) -> Verdict
            let guardDayBare (x) = { guard x < Date("2026-08-01") else Ok
                No }

            behavior guardMomentBare : (x: DateTime) -> Verdict
            let guardMomentBare (x) = { guard x < DateTime("2026-08-01T00:00:00") else Ok
                No }

            behavior guardTextBare : (x: String) -> Verdict
            let guardTextBare (x) = { guard x < "2026-08" else Ok
                No }

            behavior guardTimeBare : (x: Time) -> Verdict
            let guardTimeBare (x) = { guard x < Time("16:00:00") else Ok
                No }

            behavior guardNanoBare : (x: Instant) -> Verdict
            let guardNanoBare (x) = { guard x < Instant("2026-08-01T00:00:00Z") else Ok
                No }

            behavior guardStageBare : (x: Stage) -> Verdict
            let guardStageBare (x) = { guard x < Qualified else Ok
                No }

            behavior guardWholeWrapped : (x: WholeN) -> Verdict
            let guardWholeWrapped (x) = { guard x.value < 5000 else Ok
                No }

            behavior guardDenseWrapped : (x: DenseN) -> Verdict
            let guardDenseWrapped (x) = { guard x.value < 0.5m else Ok
                No }

            behavior guardDayWrapped : (x: DayN) -> Verdict
            let guardDayWrapped (x) = { guard x.value < Date("2026-08-01") else Ok
                No }

            behavior guardMomentWrapped : (x: MomentN) -> Verdict
            let guardMomentWrapped (x) = {
                guard x.value < DateTime("2026-08-01T00:00:00") else Ok
                No }

            behavior guardTextWrapped : (x: TextN) -> Verdict
            let guardTextWrapped (x) = { guard x.value < "2026-08" else Ok
                No }

            behavior guardTimeWrapped : (x: TimeN) -> Verdict
            let guardTimeWrapped (x) = { guard x.value < Time("16:00:00") else Ok
                No }

            behavior guardNanoWrapped : (x: NanoN) -> Verdict
            let guardNanoWrapped (x) = {
                guard x.value < Instant("2026-08-01T00:00:00Z") else Ok
                No }

            behavior guardStageWrapped : (x: StageN) -> Verdict
            let guardStageWrapped (x) = { guard x.value < Qualified else Ok
                No }

            // The same line, written as the position's own type rather than as what it wraps. A
            // newtype is the value it carries, so `x < WholeN(5000)` compares two of them and the
            // line is the one `x < 5000` draws at the value inside.
            behavior guardWholeBuilt : (x: WholeN) -> Verdict
                constructs WholeN
            let guardWholeBuilt (x) = { guard x < WholeN(5000) else Ok
                No }

            behavior guardDenseBuilt : (x: DenseN) -> Verdict
                constructs DenseN
            let guardDenseBuilt (x) = { guard x < DenseN(0.5m) else Ok
                No }

            behavior guardDayBuilt : (x: DayN) -> Verdict
                constructs DayN
            let guardDayBuilt (x) = { guard x < DayN(Date("2026-08-01")) else Ok
                No }

            behavior guardMomentBuilt : (x: MomentN) -> Verdict
                constructs MomentN
            let guardMomentBuilt (x) = {
                guard x < MomentN(DateTime("2026-08-01T00:00:00")) else Ok
                No }

            behavior guardTimeBuilt : (x: TimeN) -> Verdict
                constructs TimeN
            let guardTimeBuilt (x) = { guard x < TimeN(Time("16:00:00")) else Ok
                No }

            behavior guardNanoBuilt : (x: NanoN) -> Verdict
                constructs NanoN
            let guardNanoBuilt (x) = {
                guard x < NanoN(Instant("2026-08-01T00:00:00Z")) else Ok
                No }

            behavior guardTextBuilt : (x: TextN) -> Verdict
                constructs TextN
            let guardTextBuilt (x) = { guard x < TextN("2026-08") else Ok
                No }

            behavior guardStageBuilt : (x: StageN) -> Verdict
                constructs StageN
            let guardStageBuilt (x) = { guard x < StageN(Qualified) else Ok
                No }

            behavior boundWhole  : (x: WholeI)  -> Ok
            let boundWhole (x) = Ok

            behavior boundDense  : (x: DenseI)  -> Ok
            let boundDense (x) = Ok

            behavior boundDay    : (x: DayI)    -> Ok
            let boundDay (x) = Ok

            behavior boundMoment : (x: MomentI) -> Ok
            let boundMoment (x) = Ok

            behavior boundText   : (x: TextI)   -> Ok
            let boundText (x) = Ok

            behavior boundTime   : (x: TimeI)   -> Ok
            let boundTime (x) = Ok

            behavior boundNano   : (x: NanoI)   -> Ok
            let boundNano (x) = Ok

            behavior boundStage  : (x: StageI)  -> Ok
            let boundStage (x) = Ok

            // The wider line is guarded first, so both departures are ones values reach. Written
            // the other way round the second guard departs at two while everything past the first
            // is under one, and the line at two divides nothing — which is a model with a dead
            // branch in it and not a position with three classes.
            behavior twoLinesDense : (x: Decimal) -> Verdict
            let twoLinesDense (x) = { guard x < 2.0m else No
                guard x < 1.0m else Ok
                Ok }

            behavior twoLinesMoment : (x: DateTime) -> Verdict
            let twoLinesMoment (x) = {
                guard x < DateTime("2026-08-01T00:00:02") else No
                guard x < DateTime("2026-08-01T00:00:01") else Ok
                Ok }

            behavior openOnBothSidesDense : (x: Decimal) -> Verdict
            let openOnBothSidesDense (x) = { guard x < 2.0m else No
                guard x <= 1.0m else Ok
                Ok }

            data TwoDecimals = Decimal
                invariant value >= 0.0m && value <= 1.0m
            data TwoMoments = DateTime
                invariant value >= DateTime("2026-08-01T00:00:00")
                    && value <= DateTime("2026-08-01T00:00:01")

            behavior singledDense : (x: TwoDecimals, k: Int) -> Verdict
            let singledDense (x, k) = {
                if k > 0 then { guard x.value == 0.0m else No
                    Ok }
                else { guard x.value == 1.0m else No
                    Ok } }

            behavior singledMoment : (x: TwoMoments, k: Int) -> Verdict
            let singledMoment (x, k) = {
                if k > 0 then { guard x.value == DateTime("2026-08-01T00:00:00") else No
                    Ok }
                else { guard x.value == DateTime("2026-08-01T00:00:01") else No
                    Ok } }

            behavior openOnBothSidesMoment : (x: DateTime) -> Verdict
            let openOnBothSidesMoment (x) = {
                guard x <= DateTime("2026-08-01T00:00:01") else Ok
                guard x < DateTime("2026-08-01T00:00:02") else No
                Ok }
            """;

    /**
     * A {@code guard}'s line, at a bare position and at a newtype over the same values.
     *
     * <p>The line and the value beside it where the carrier steps; the line alone where it does not.
     * A {@code Decimal} has no smallest step this language names, so the row beside the line is not
     * asked for — which is a fact about the values and not about the reading, and is why it is an
     * obligation short of the whole numbers rather than unread. A {@code DateTime} is held to the
     * second (spec §a-local-temporal-is-held-to-the-second) and steps like a day count, so it is
     * asked for both.
     */
    @Test
    void aGuardsLineIsDrawnOnTheCarriersThatHaveACount() {
        Map<String, Measured> expected = new LinkedHashMap<>();
        expected.put("guardWholeBare", read(2));
        expected.put("guardDenseBare", read(1));
        expected.put("guardDayBare", read(2));
        expected.put("guardMomentBare", read(2));
        // The line and no value beside it. A string has no next string, which is the answer a
        // decimal gets and for the same reason — so it is one obligation short of the carriers whose
        // values step, rather than unread.
        expected.put("guardTextBare", read(1));
        // Both step, each at its own unit: a time of day at the second and a moment at the
        // nanosecond, so each is owed the line and the value beside it.
        expected.put("guardTimeBare", read(2));
        expected.put("guardNanoBare", read(2));
        // The line and the case beside it, written as case names. The classes are still the three
        // cases: `read(2)` would say the cut replaced them, and it does not.
        expected.put("guardStageBare", readBesideItsClasses(2));
        expected.put("guardWholeWrapped", read(2));
        expected.put("guardDenseWrapped", read(1));
        expected.put("guardDayWrapped", read(2));
        expected.put("guardMomentWrapped", read(2));
        expected.put("guardTextWrapped", read(1));
        expected.put("guardTimeWrapped", read(2));
        expected.put("guardNanoWrapped", read(2));
        // What the bare position answers. The classes reader reads through the name now, as the
        // carrier always did, so the cell that was a name changing what a rule means is a cell that
        // says the two forms are one.
        expected.put("guardStageWrapped", readBesideItsClasses(2));
        // And the same line written as the position's own type. A newtype's construction around a
        // value is that value here, for the reason the carrier reads through the name to begin
        // with — so a cell differing from the bare one is a value the model wrote that this could
        // not read back.
        expected.put("guardWholeBuilt", read(2));
        expected.put("guardDenseBuilt", read(1));
        expected.put("guardDayBuilt", read(2));
        expected.put("guardMomentBuilt", read(2));
        expected.put("guardTimeBuilt", read(2));
        expected.put("guardNanoBuilt", read(2));
        // The eighth built row. It could not be written while a newtype over an enumeration was
        // measured here and refused by `<`, and the gap was the last cell where a name changed what
        // a rule means (issue #856). It answers what the bare and the wrapped rows answer.
        expected.put("guardStageBuilt", readBesideItsClasses(2));
        expected.put("guardTextBuilt", read(1));

        assertEquals(expected, measured(expected.keySet()));
    }

    /**
     * The same rule written as an invariant, which draws a line and adds no class.
     *
     * <p>Everything outside a bound is refused at construction, so there is no class on the far side
     * to cover and the position has one edge worth a row (ADR-0090) — which is why the cells over a
     * range have no axis and the {@code guard} cells above have one.
     *
     * <p>The enumeration is the exception, and it is the bound adding nothing rather than the bound
     * being read differently: an enumeration's type states its cases whatever any rule says, so the
     * axis is there before the invariant is read. What the invariant does is take away the cases
     * outside it — {@code value >= Qualified} leaves two of the three — because a class is a set of
     * values the position holds, and a class for a value refused at construction is a row nobody
     * can write.
     */
    @Test
    void anInvariantsBoundIsDrawnOnTheSameCarriers() {
        Map<String, Measured> expected = new LinkedHashMap<>();
        expected.put("boundWhole", new Measured(0, 1, null));
        expected.put("boundDense", new Measured(0, 1, null));
        expected.put("boundDay", new Measured(0, 1, null));
        expected.put("boundMoment", new Measured(0, 1, null));
        expected.put("boundText", new Measured(0, 1, null));
        expected.put("boundTime", new Measured(0, 1, null));
        expected.put("boundNano", new Measured(0, 1, null));
        expected.put("boundStage", new Measured(1, 1, null));

        assertEquals(expected, measured(expected.keySet()));
    }


    /**
     * And the two rows say the same thing about the same carriers.
     *
     * <p>Asserted over the table rather than cell by cell, because what went wrong was not a wrong
     * number anywhere — it was one reader admitting a carrier the other refused, which only shows up
     * as two rows compared.
     */
    @Test
    void aCarrierIsReadOrNotWhicheverRuleWroteIt() {
        Map<String, Measured> all = measured(List.of(
                "guardWholeBare", "guardDenseBare", "guardDayBare", "guardMomentBare",
                "guardTextBare", "guardTimeBare", "guardNanoBare", "guardStageBare",
                "boundWhole", "boundDense", "boundDay", "boundMoment", "boundText", "boundTime",
                "boundNano", "boundStage"));

        for (String[] pair : new String[][] {
                {"guardWholeBare", "boundWhole"}, {"guardDenseBare", "boundDense"},
                {"guardDayBare", "boundDay"}, {"guardMomentBare", "boundMoment"},
                {"guardTextBare", "boundText"}, {"guardTimeBare", "boundTime"},
                {"guardNanoBare", "boundNano"}, {"guardStageBare", "boundStage"}}) {
            assertEquals(all.get(pair[0]).unread(), all.get(pair[1]).unread(),
                    "a carrier a guard's line is drawn on is one an invariant's bound is drawn on: "
                            + pair[0] + " against " + pair[1]);
        }
    }

    /**
     * And a name wrapped round the values does not change the answer.
     *
     * <p>A newtype is the value it carries, so a rule about it is the rule about what it wraps. The
     * enumeration is a row here like the rest: it used to be the one carrier where the two forms
     * differed, because the reader that drew the line read through the name and the reader that
     * asked what the position divides into stopped at it (issue #631). One reading answers both
     * now, so the row that recorded the disagreement is the row that holds it closed.
     */
    @Test
    void aNameWrappedRoundTheValuesDoesNotChangeWhatIsMeasured() {
        List<String> carriers = List.of("Whole", "Dense", "Day", "Moment", "Text", "Time", "Nano",
                "Stage");
        List<String> asked = new java.util.ArrayList<>();
        for (String carrier : carriers) {
            asked.add("guard" + carrier + "Bare");
            asked.add("guard" + carrier + "Wrapped");
            if (!carrier.equals("Stage")) {
                asked.add("guard" + carrier + "Built");
            }
        }
        Map<String, Measured> all = measured(asked);

        for (String carrier : carriers) {
            assertEquals(all.get("guard" + carrier + "Bare"), all.get("guard" + carrier + "Wrapped"),
                    carrier + ": a newtype is the value it carries");
            // And the value written as the newtype rather than as what it wraps. The reading that
            // sends a position to a carrier walks through the names; a reading of the values that
            // stops at one leaves a position whose own literals it cannot read.
            if (!carrier.equals("Stage")) {
                assertEquals(all.get("guard" + carrier + "Bare"),
                        all.get("guard" + carrier + "Built"),
                        carrier + ": and a value written as the newtype is that value");
            }
        }
    }

    /**
     * A class open at both ends offers a value of its own, and is not a class where there is none.
     *
     * <p>The pair of the test above, and the half of it that spacing alone gets wrong. Between two
     * decimals a whole apart there is a decimal, so the class is there and has a row. Between two
     * moments a second apart there is a number and no date-time — what a date-time can be written as
     * sits on a grid at the second, and half a second is a count and not a value the position holds.
     *
     * <p>Which makes the two rules one division of the moments. {@code x <= :01} and {@code x < :02}
     * part them in the same place, so there is no class between them to offer a row for or to say
     * there is none for; the position has the two classes either side of one line (issue #880).
     *
     * <p>Read at the row and not at the count, which is what the same defect looked like from the
     * other end: the number offered was between the two ends, and writing it back landed on one of
     * them, so a row was labelled for a class it is not in.
     */
    @Test
    void aClassOpenAtBothEndsOffersAValueOfItsOwnOrNone() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();

        String dense = souther.compiler.report.GeneratedRows.of(
                compilation, "example.matrix", "openOnBothSidesDense",
                SourceRendering.namedByIdentity(compilation.texts())).text();
        assertTrue(dense.contains("1 < x < 2"), dense);
        assertFalse(dense.contains("nothing here writes a value whose value is in this range"),
                "a decimal lies between two decimals a whole apart: " + dense);

        String moment = souther.compiler.report.GeneratedRows.of(
                compilation, "example.matrix", "openOnBothSidesMoment",
                SourceRendering.namedByIdentity(compilation.texts())).text();
        assertFalse(moment.contains("2026-08-01T00:00:01 < x < 2026-08-01T00:00:02"),
                "nothing lies strictly between two adjacent moments, so the two rules part them"
                        + " in one place and there is no class between: " + moment);
        assertTrue(moment.contains("\"x=x <= 2026-08-01T00:00:01\""), moment);
        assertTrue(moment.contains("\"x=2026-08-01T00:00:01 < x\""),
                "and the position has the two classes either side of that one line: " + moment);
    }

    /**
     * And the class of everything a body singled out is offered a value that is none of them.
     *
     * <p>The same question an interval asks, reached by the other reader. An equality names a value
     * rather than a place to cut, so what is left over is a class too, and the value that stands for
     * it has to be one the position holds and not one of the values it excludes. Over two decimals
     * there is one between them; over two adjacent moments the number between them is written back
     * as one of the two, so the class has nothing to offer and says so.
     *
     * <p>Reachable because this branch taught the reader to see a date-time constant at all: before
     * it, an equality against one was not read and there was no such class.
     *
     * <p><b>The two equalities are under a fork on another position, and that is what keeps both.</b>
     * Written one under the other, the second stands where the first has already narrowed the
     * position to the value it names — nothing arriving there holds the second's value, so the line
     * is one the model draws through nothing that gets to it and is dropped
     * ({@link ComparisonAssessment.NothingArrivesAtItsLine}). A position needs two values singled out
     * for this class to exist at all, so they are put where neither narrows the other.
     */
    @Test
    void theClassOfEverythingElseIsOfferedAValueThatIsNoneOfThem() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();

        String dense = souther.compiler.report.GeneratedRows.of(
                compilation, "example.matrix", "singledDense", SourceRendering.namedByIdentity(compilation.texts())).text();
        assertTrue(dense.contains("\"x=/= 0, 1\" : (TwoDecimals(0.5m)"),
                "a decimal lies between the two singled out: " + dense);

        String moment = souther.compiler.report.GeneratedRows.of(
                compilation, "example.matrix", "singledMoment", SourceRendering.namedByIdentity(compilation.texts())).text();
        assertTrue(moment.contains("no row for `x=/= 2026-08-01T00:00:00,"
                        + " 2026-08-01T00:00:01`"),
                "the position holds nothing but the two singled out: " + moment);
        assertFalse(moment.contains("=/= 2026-08-01T00:00:00,"
                        + " 2026-08-01T00:00:01 x x = "),
                "and neither of them is offered under that class's name: " + moment);
    }

    /** What the measures answered for each named behavior. */
    private static Map<String, Measured> measured(Iterable<String> behaviors) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, PartitionEvidence> coverage =
                compilation.db().ask(new Adequacy.Coverage("example.matrix")).value();
        assertNotNull(coverage, "the model under test compiles");

        Map<String, Measured> out = new LinkedHashMap<>();
        for (String behavior : behaviors) {
            PartitionEvidence evidence = coverage.get(behavior);
            assertNotNull(evidence, behavior + " was measured");
            // Through the projection a report goes through, which is what this table is of: what
            // this compiler could not do is recorded in its own words and said in the document's.
            UndividedPosition.Reason unread = evidence.rulesWithoutALine().isEmpty() ? null
                    : ReportedReason.of(evidence.rulesWithoutALine().get(0).why());
            // The points a row is owed at against a line, and not the borders. Which carriers name a
            // value one step over is what this table is about, and a border is one whether or not
            // its second point exists — counted as borders, every carrier answers alike.
            out.put(behavior, new Measured(evidence.axes().size(),
                    (int) souther.compiler.query.BorderAssessment.pointsOf(
                            Adequacy.readingsOf(compilation.db(), "example.matrix").get(behavior))
                            .stream().filter(point -> point.role().againstTheLine())
                            .filter(point -> point.owed() != null).count(),
                    unread));
        }
        return out;
    }

    /**
     * How the values step is the carrier's answer, and only the carrier's.
     *
     * <p>The class between two lines holds something, and which carrier it is on decides what. Two
     * decimals a whole apart leave a range dense in decimals; two moments a second apart leave the
     * lower of them, the way two days a day apart would. Asked as "is this the decimal" it was a
     * second spelling of the question, and a carrier dense without being that one answered no — the
     * range came back holding no value, which is what neither of these leaves.
     *
     * <p>The moment row was the dense case here while a {@code DateTime} carried nanoseconds. It is
     * a stepping carrier now, so what it holds the reader to is that the answer is read off the
     * carrier at all; {@code twoLinesDense} is what still holds it to the dense reading.
     */
    @Test
    void howTheValuesStepIsAskedOfTheCarrier() {
        for (String behavior : List.of("twoLinesDense", "twoLinesMoment")) {
            Compilation compilation = Compilation.ofSource(MODEL, "Main");
            compilation.measure(Adequacy.Asked.fullReport());
            compilation.answerEverything();
            String block = souther.compiler.report.GeneratedRows.of(
                    compilation, "example.matrix", behavior, SourceRendering.namedByIdentity(compilation.texts())).text();

            assertFalse(block.contains("no value of this range can be written"),
                    behavior + ": the range between the two lines holds a value: " + block);
            assertEquals(3, block.lines().filter(line -> line.contains(" : (")).count(),
                    behavior + ": a row for each of the three classes: " + block);
        }
    }
}
