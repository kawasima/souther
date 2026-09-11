package souther.cli;

import souther.cli.Main;
import org.junit.jupiter.api.Test;

import souther.compiler.publish.RuleHandleProse;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A boundary the report asks for is a value some row could carry.
 *
 * <p>Both ends of {@code WorkInterval} are minutes of a day, so both stop at 1440 read on their own.
 * Read where they sit, one of them cannot: a {@code startsAt} of 1440 wants an {@code endsAt} past
 * the end of the day. Asking for it is asking for a row the decoder refuses, and nothing in the
 * report tells that gap from one worth closing — which is what issue #427 reports.
 */
class ABoundaryIsAValueTheRecordCanHoldTest {

    private static final String TIMESHEET = """
            module example.timesheet

            data MinuteOfDay = Int
                invariant withinDay = value >= 0 && value <= 1440

            data WorkInterval =
                { startsAt: MinuteOfDay
                , endsAt: MinuteOfDay
                }
                invariant endsAfterStart = startsAt < endsAt

            data ShortShift
            data LongShift

            behavior classifyInterval : (interval: WorkInterval) -> ShortShift | LongShift

            let classifyInterval (interval) =
                if interval.endsAt.value - interval.startsAt.value >= 480
                    then LongShift
                    else ShortShift

            example classifyInterval
                | "short" : (WorkInterval { startsAt = MinuteOfDay(540), endsAt = MinuteOfDay(600) })
                    -> ShortShift
            """;

    /** Two fields of one record, each bounded at both ends by its own type and related to the other
     * by a strict rule the decimals give no next value to step to. */
    private static final String BAND = """
            module example.band

            data Ratio = Decimal
                invariant withinOne = value >= 0.0m && value <= 1.0m

            data Band =
                { low: Ratio
                , high: Ratio
                }
                invariant ordered = low < high

            data Ok

            behavior classify : (band: Band) -> Ok

            let classify (band) = Ok

            example classify
                | "a" : (Band { low = Ratio(0.1m), high = Ratio(0.9m) }) -> Ok
            """;

    /**
     * Every point of every line that no row of the model stands at, said per position.
     *
     * <p>Read off the measure and not off the report. A line an {@code invariant} drew is owed once
     * over every position carrying the type, and the report says it once and names it in the terms
     * the declaration wrote (issue #1062) — so which positions reach a line is a question the report
     * stopped answering when the debt stopped being a position's. It is answered here, where the
     * lines are still one per position, which is what these tests are about: whether the value a
     * record's rules leave a field at is the value the line is drawn at.
     *
     * <p>Written the way the report writes it, so that what the tests say is unchanged.
     */
    private static List<String> boundariesOf(String model) throws Exception {
        souther.compiler.query.Compilation compilation =
                souther.compiler.query.Compilation.ofSource(model, "Main");
        compilation.measure(souther.compiler.query.Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        java.util.Map<String, List<souther.compiler.query.BorderAssessment>> borders =
                // The searched reading, which is what the report is written from: whether a value
                // at a point could be built is part of what tells a line no row stands at from one
                // no row could stand at, and the plain measure does not compose one.
                souther.compiler.query.Adequacy.searchedBoundariesOf(compilation.db(), module);
        assertNotNull(borders, "the model under test compiles");
        souther.compiler.diag.SourceRendering names = new souther.compiler.diag.SourceRendering(
                souther.compiler.diag.SourceNameResolver.identity(),
                souther.compiler.diag.SourceLayouts.NONE);
        // Where each rule a line names is shown, asked of the compile that read it — which is what
        // a sentence about a rule with no name is written from.
        souther.compiler.publish.PublishedRuleHandle.WhereARuleIs places =
                cited -> souther.compiler.query.Sites.placeOf(compilation.db(), cited);
        List<String> out = new java.util.ArrayList<>();
        borders.forEach((behavior, lines) -> lines.forEach(line -> {
            for (souther.compiler.query.BorderAssessment.Point point : line.points()) {
                if (!point.item().isUnmetGap()) {
                    continue;
                }
                out.add(point.role().againstTheLine()
                        ? "no row is at the " + point.role() + " point " + behavior + "/"
                                + line.axis() + " = " + point.against()
                                + " (" + RuleHandleProse.said(line.describe(places), names, null) + ")"
                        : "no row is at an " + point.role() + " point of " + behavior + "/"
                                + line.axis() + ", " + point.against()
                                + " (" + RuleHandleProse.said(line.describe(places), names, null) + ")");
            }
        }));
        return List.copyOf(out);
    }

    /**
     * The lines the declarations place, which is what these two tests are about.
     *
     * <p>The body of the behavior draws lines of its own — {@code endsAt <= startsAt + 480} holds the
     * two ends within a working day, which is a line on how far apart they stand — and those are a
     * different rule's. Counted together, a reading that learned to draw one more of them would fail
     * these tests without anything having changed about where the record stops.
     */
    private static List<String> placedByTheRecord(String model) throws Exception {
        return boundariesOf(model).stream().filter(line -> line.contains("(invariant")).toList();
    }

    private static String reportOn(String model, String... extra) throws Exception {
        Path file = Files.createTempDirectory("souther-boundary").resolve("model.sou");
        Files.writeString(file, model);
        List<String> args = new java.util.ArrayList<>(List.of("examples", file.toString()));
        args.addAll(List.of(extra));
        PrintStream was = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            Main.main(args.toArray(String[]::new));
        } finally {
            System.setOut(was);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void anEdgeIsWhereTheRecordStopsAndNotWhereTheFieldsTypeDoes() throws Exception {
        List<String> asked = placedByTheRecord(TIMESHEET);

        // Four ends, and the relation that moved two of them. That relation is a line of its own —
        // it says where the pair parts — and it divides neither field, which is why the ends below
        // are still each field's own.
        assertEquals(5, asked.size(), () -> "asked for " + asked);
        assertTrue(asked.stream().anyMatch(l -> l.contains("interval.startsAt = interval.endsAt - 1")
                        && l.contains("invariant WorkInterval (endsAfterStart)")),
                () -> "the pair parts one minute in from where they are level: " + asked);
        assertTrue(asked.stream().anyMatch(l -> l.contains("interval.startsAt = 1439")),
                () -> "the last minute an interval can start at is 1439: " + asked);
        assertTrue(asked.stream().anyMatch(l -> l.contains("interval.endsAt = 1")),
                () -> "the first it can end at is 1: " + asked);
        assertFalse(asked.stream().anyMatch(l -> l.contains("interval.startsAt = 1440")),
                () -> "1440 would need an endsAt past the end of the day: " + asked);
        assertFalse(asked.stream().anyMatch(l -> l.contains("interval.endsAt = 0")),
                () -> "0 would need a startsAt below zero: " + asked);
    }

    /** An end the record moved says so. `MinuteOfDay`'s maximum is why there is an upper edge here;
     * `WorkInterval`'s clause is why it is 1439, and an author reading 1439 beside a rule that says
     * 1440 has been told half of it. */
    @Test
    void anEdgeTheRecordMovedNamesTheRecordBesideTheRuleThatPutItThere() throws Exception {
        List<String> asked = boundariesOf(TIMESHEET);

        assertTrue(asked.stream().anyMatch(l -> l.contains("interval.startsAt = 1439")
                        && l.contains("invariant MinuteOfDay (withinDay) within WorkInterval")),
                () -> "asked for " + asked);
        assertTrue(asked.stream().anyMatch(l -> l.contains("interval.startsAt = 0")
                        && l.contains("(invariant MinuteOfDay (withinDay))")),
                () -> "an end the record left alone names only the rule that put it there: " + asked);
    }

    /**
     * A row offered at one of these boundaries is one the decoder accepts.
     *
     * <p>The other end of the interval is chosen beside the one being fixed and not from its own
     * type: a {@code startsAt} of 1439 leaves exactly 1440 for {@code endsAt}, and taking the bottom
     * of the type's range instead is how a boundary that can be written came back as one every value
     * tried was refused at.
     */
    @Test
    void aRowIsOfferedAtEachOfThem() throws Exception {
        String report = reportOn(TIMESHEET, "--generate", "--boundaries");

        assertTrue(report.contains(
                        "startsAt = MinuteOfDay(1439), endsAt = MinuteOfDay(1440)"),
                () -> "1439 is only writable beside 1440:\n" + report);
        assertTrue(report.contains("startsAt = MinuteOfDay(0), endsAt = MinuteOfDay(1)"),
                () -> "and 0 beside anything above it:\n" + report);
        assertFalse(report.contains("every value tried was refused"),
                () -> "nothing here is out of reach:\n" + report);
    }

    /** A guard drawn at a value the record cannot hold divides nothing and is no edge either. Held
     * because the two answers are computed from one list, and a line left in the cuts while the
     * intervals dropped it is this same defect one field over. */
    @Test
    void aLineTheRecordCannotHoldIsNotABoundaryEither() throws Exception {
        String gated = TIMESHEET
                .replace("interval.endsAt.value - interval.startsAt.value >= 480",
                        "interval.startsAt.value >= 1440")
                .replace("then LongShift", "then LongShift")
                .replace("else ShortShift", "else ShortShift");
        List<String> asked = boundariesOf(gated);

        // The four ends, and the record's own relation between the two fields. What the guard would
        // have drawn is what is absent, and that is what the assertion below is about.
        assertEquals(5, asked.size(), () -> "asked for " + asked);
        // The word and not the spelling. A guard says `guard@15:23` where it is written in a file
        // this compile holds and names its declaration where it is not, so matching the first of
        // those would stop seeing the second — which is a line drawn by a guard all the same.
        assertFalse(asked.stream().anyMatch(l -> l.contains("guard")),
                () -> "no interval starts at 1440, so the comparison has no line to be at: " + asked);
    }

    /**
     * An end only the record imposes rules out a line beyond it as much as one the type repeats.
     *
     * <p>Above, both fields carry an end of their own and the record moves one of them. Here
     * {@code Count} bounds its value below and nothing above, and the cap on {@code b} is written on
     * the record alone. What a position can hold is the whole of what its rules leave it, whichever
     * of them said so — read off the type's own ends, the cap is invisible, and a guard at 50 is left
     * dividing a range that stops at 10.
     */
    @Test
    void anEndOnlyTheRecordImposesRulesOutALineBeyondIt() throws Exception {
        String report = reportOn("""
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
                """, "--generate", "--boundaries");

        // Read off the values the rows are written with: a row composed only for a line carries no
        // name, so what is asked for is a row holding the value rather than a line named in the text.
        assertFalse(report.contains("b = Count(50)"),
                () -> "no pair holds a `b` of 50, so the guard draws no line there:\n" + report);
        assertFalse(report.contains("b = Count(49)"),
                () -> "and none holds the value below it either:\n" + report);
        assertFalse(report.contains("pair.b=50 <= x"),
                () -> "a class nothing can be in is not a class:\n" + report);
        assertTrue(report.contains("b = Count(1)"),
                () -> "the end the record moved is still asked for:\n" + report);
        assertFalse(report.contains("every value tried was refused"),
                () -> "and everything left is writable:\n" + report);
    }

    /**
     * A guard at the end of what a position holds is a line, and the step off it is not a row.
     *
     * <p>The neighbour is invented rather than read: a guard has values on both sides, so the class
     * beyond it wants its own edge. Where the guard sits at the end of the range there is no class
     * beyond it, and the neighbour is a value the type refuses. The line itself stays owed — a row
     * written at it reaches the comparison, which is what a guard's boundary is about.
     */
    @Test
    void aStepOffTheEndOfTheRangeIsNotABoundary() throws Exception {
        List<String> asked = boundariesOf("""
                module example.floor

                data Level = Int
                    invariant lower = value >= 10

                data Answer = { n: Int }

                behavior classify : (level: Level) -> Answer
                    constructs Answer

                let classify (level) =
                    if level.value < 10 then Answer { n = 1 } else Answer { n = 2 }

                example classify
                    | "twenty" : (Level(20)) -> Answer { n = 2 }
                """);

        assertTrue(asked.stream().anyMatch(l -> l.contains("level = 10")),
                () -> "the line is still owed a row: " + asked);
        assertFalse(asked.stream().anyMatch(l -> l.contains("level = 9")),
                () -> "and no level is 9, so no row can be written there: " + asked);
    }

    /**
     * A row filling a class picks the rest of the record beside the value it settled on.
     *
     * <p>The boundary filler and the class filler are two searches over one record, and only one of
     * them was told what the other had fixed. A class of `720 <= x <= 1439` stands for 720, and an
     * `endsAt` taken from its own type's bottom is refused beside it.
     */
    @Test
    void aClassIsFilledBesideWhatItSettledOn() throws Exception {
        String report = reportOn(TIMESHEET.replace(
                        "interval.endsAt.value - interval.startsAt.value >= 480",
                        "interval.startsAt.value >= 720"),
                "--generate", "--boundaries");

        assertFalse(report.contains("every value tried was refused"),
                () -> "the class of the afternoon is as writable as its edge:\n" + report);
    }

    /**
     * A rule on the outer record reaches a field of a field.
     *
     * <p>The reading is of the parameter and not of each record met on the way down. Rebuilt at the
     * inner record it has never seen the outer clause, and `interval.startsAt` goes back to the top
     * of its own type — the whole of #427, one level in.
     */
    @Test
    void aRuleOnTheOuterRecordReachesAFieldOfAField() throws Exception {
        String nested = """
                module example.nested

                data Minute = Int
                    invariant withinDay = value >= 0 && value <= 1440

                data Interval =
                    { startsAt: Minute
                    , endsAt: Minute
                    }

                data Input =
                    { interval: Interval
                    , cap: Minute
                    }
                    invariant capped = interval.startsAt < cap

                data Yes
                data No

                behavior classify : (input: Input) -> Yes | No

                let classify (input) = if input.interval.startsAt.value >= 100 then Yes else No

                example classify
                    | "a" : (Input { interval = Interval { startsAt = Minute(10),
                        endsAt = Minute(20) }, cap = Minute(30) }) -> No
                """;
        List<String> asked = boundariesOf(nested);

        assertTrue(asked.stream().anyMatch(l ->
                        l.contains("input.interval.startsAt = 1439")
                                && l.contains("within Input")),
                () -> "cap stops at 1440, so a start of 1440 has nothing to be under: " + asked);
        assertFalse(asked.stream().anyMatch(l -> l.contains("input.interval.startsAt = 1440")),
                () -> "asked for " + asked);
        assertFalse(reportOn(nested, "--generate", "--boundaries")
                        .contains("every value tried was refused"),
                "and each of them is a row that builds");
    }

    /**
     * An edge a rule refuses is not a row anybody is owed.
     *
     * <p>`low < high` under a shared `[0, 1]` leaves `low` in `[0, 1)` and `high` in `(0, 1]`. The
     * line at 1 is one `Ratio` draws and both fields carry, and only one of them reaches it: a `low`
     * of 1 would need a `high` above 1, which `Ratio` has no room for. The two edges of one rule come
     * out differently because the record says so, and asking for the other is asking for a row nobody
     * can write.
     */
    @Test
    void anEdgeThePositionCannotReachIsNotOwedARow() throws Exception {
        List<String> owed = boundariesOf(BAND);

        assertTrue(owed.stream().anyMatch(l -> l.contains("classify/band.low = 0")),
                () -> "the bottom of the lower field is its own: " + owed);
        assertTrue(owed.stream().anyMatch(l -> l.contains("classify/band.high = 1")),
                () -> "and the top of the upper field is: " + owed);
        assertFalse(owed.stream().anyMatch(l -> l.contains("classify/band.low = 1")),
                () -> "a low of 1 leaves no room for a high above it: " + owed);
        assertFalse(owed.stream().anyMatch(l -> l.contains("classify/band.high = 0")),
                () -> "and a high of 0 leaves none for a low below it: " + owed);
    }

    /**
     * One pair of values, reached from either field of the rule that relates them.
     *
     * <p>`low < high` is satisfied by `{ 0, 1 }` whichever end is fixed first. Fixing `high` at 1
     * leaves `low` a range whose bottom the rule takes, and fixing `low` at 0 leaves `high` a range
     * whose bottom it does not — so a position that proposes one value has nothing left to offer
     * the moment that value is refused, and the same pair comes back found one way and missing the
     * other.
     */
    @Test
    void thePairIsFoundFromEitherFieldOfTheRule() throws Exception {
        List<String> owed = boundariesOf(BAND);

        assertTrue(owed.stream().anyMatch(l -> l.contains("classify/band.low = 0")),
                () -> "a row at the bottom of the lower field builds: " + owed);
        assertTrue(owed.stream().anyMatch(l -> l.contains("classify/band.high = 1")),
                () -> "and so does one at the top of the upper field: " + owed);
    }

    /**
     * A row at the value settles what the projection could not.
     *
     * <p>A hole in a range is a rule the domain cannot keep, so neither edge of that position is
     * promised. One of them has a row sitting on it, which went through the decoder to get there —
     * reporting that value as not known to be writable is preferring an absence of argument to a
     * witness.
     */
    @Test
    void aRowAtTheValueIsTheProofTheProjectionCouldNotGive() throws Exception {
        String holed = """
                module example.holed

                data N = Int
                    invariant within = value >= 0 && value <= 10

                data R = { a: N }
                    invariant hole = a.value /= 5

                data Ok

                behavior f : (r: R) -> Ok

                let f (r) = Ok

                example f
                    | "zero" : (R { a = N(0) }) -> Ok
                """;
        String report = reportOn(holed);

        assertFalse(report.contains("not known to be writable: the ON point f/r.a = 0"),
                () -> "there is a row at it:\n" + report);
        // The row settles its own edge without anything being built for it, and the other edge is
        // settled by building one. Two kinds of witness, and the projection proves neither.
        // Under the declarations: both lines are the record's own clause, so the behavior carrying
        // the type is owed nothing at them and the module's account is where the two are counted.
        assertTrue(report.contains("declarations   obligations 2/4"),
                () -> "the row at 0 is met, and 10 was built and is owed:\n" + report);
        // Under the declaration that drew it, in the terms it wrote: the line is owed once over
        // every position carrying the type (issue #1062).
        assertTrue(report.contains("no row is at the ON point value = 10"), () -> report);
    }

    /**
     * A bound four records down narrows the edge at the top.
     *
     * <p>How far a report takes an input apart is a limit on what is worth measuring. What a
     * construction has to satisfy has no such limit, and the reading a boundary is derived from has
     * to reach as far as the second: `n < l1.l2.leaf.x` with `x` stopping at 10 puts `n` at 9, and a
     * reading that stopped two levels down would have left it at 10 with nothing able to hold it.
     */
    @Test
    void aBoundBelowWhatAReportLooksAtStillMovesTheEdgeAboveIt() throws Exception {
        List<String> asked = boundariesOf("""
                module example.deepbound

                data N = Int
                    invariant within = value >= 0 && value <= 10

                data Leaf = { x: N }
                data L2 = { leaf: Leaf }
                data L1 = { l2: L2 }

                data Root =
                    { n: N
                    , l1: L1
                    }
                    invariant ordered = n < l1.l2.leaf.x

                data Ok

                behavior f : (root: Root) -> Ok

                let f (root) = Ok

                example f
                    | "x" : (Root { n = N(1),
                        l1 = L1 { l2 = L2 { leaf = Leaf { x = N(2) } } } }) -> Ok
                """);

        assertTrue(asked.stream().anyMatch(l -> l.contains("root.n = 9")
                        && l.contains("within Root")),
                () -> "x stops at 10, so n stops at 9: " + asked);
        assertFalse(asked.stream().anyMatch(l -> l.contains("root.n = 10")), () -> "asked " + asked);
    }

    /**
     * A newtype taken straight as a parameter is held to its own rules like any other position.
     *
     * <p>It has no siblings, which is why it has no per-field bounds to hand out. Its own rules are
     * read all the same, and they leave it `[1, 10]`: `within` writes the bottom at 0 and `nonzero`
     * takes the 0 away, so the line `within` placed is at the first value the position has. A line
     * at the 0 would be a line at no value of `N`, and asking whether a row can be written there
     * asks about a value the model excludes.
     */
    @Test
    void aNewtypeTakenStraightIsHeldToItsOwnRules() throws Exception {
        String holed = """
                module example.holednewtype

                data N = Int
                    invariant within = value >= 0 && value <= 10
                    invariant nonzero = value /= 0

                data Ok

                behavior f : (n: N) -> Ok

                let f (n) = Ok

                example f
                    | "x" : (N(3)) -> Ok
                """;

        String report = reportOn(holed);

        List<String> owed = boundariesOf(holed);

        // Asked of the lines the positions have and not of the report, for the reason
        // `boundariesOf` is: the report names a declaration's line once and in the declaration's
        // own terms, so a position's absence of one is not a sentence it can be short of.
        assertFalse(owed.stream().anyMatch(l -> l.contains("f/n = 0")),
                () -> "0 is no value of `N`, so it is no line of it: " + owed);
        assertTrue(owed.stream().anyMatch(l -> l.contains("f/n = 1")),
                () -> "the line `within` placed is at the first value the rules leave: " + owed);
        assertTrue(owed.stream().anyMatch(l -> l.contains("f/n = 10")),
                () -> "and the top of the same range: " + owed);
        // Both are rows an author is owed rather than edges nothing promises. The bounds state
        // every rule of the value — the denial became an end rather than a hole — so what is
        // missing is the row and not the reading.
        assertFalse(report.contains("not known to be writable"), () -> report);
    }

    /** The same two fields with the rule removed keep the whole of their type's range, so the
     * narrowing above is read as that rule doing it. */
    @Test
    void withoutTheRuleBothEndsKeepTheirTypesRange() throws Exception {
        List<String> asked = placedByTheRecord(TIMESHEET.replace(
                "    invariant endsAfterStart = startsAt < endsAt\n", ""));

        assertEquals(4, asked.size(), () -> "asked for " + asked);
        assertTrue(asked.stream().anyMatch(l -> l.contains("interval.startsAt = 1440")),
                () -> "nothing stops an interval starting at the end of the day now: " + asked);
        assertTrue(asked.stream().anyMatch(l -> l.contains("interval.endsAt = 0")),
                () -> "nor ending at the start of it: " + asked);
    }
}
