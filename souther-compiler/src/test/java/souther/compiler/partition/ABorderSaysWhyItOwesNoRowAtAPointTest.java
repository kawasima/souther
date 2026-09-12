package souther.compiler.partition;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Towards;
import souther.compiler.query.Adequacy;
import souther.compiler.partition.FixtureTemplate;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.query.ItemAssessment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A border owes a row at four points, and where it owes none it says which of three things settled
 * that.
 *
 * <p>Every one of these used to be a coverage item that was never built. The obligation was left out
 * of a list, and all of them reached a reader as the same thing — one fewer entry — so a reader
 * counting the four items the technique asks for found this compiler short and was never told which
 * of them had happened. Two of the three are not shortfalls at all: the rules refusing the point is
 * the model having discharged it, and a carrier naming no value one step over is an item that cannot
 * exist.
 *
 * <p>Each reason is measured against a model that differs from it in one thing and owes the row. An
 * expectation on one model alone holds against a reader that answers "not owed" everywhere, which is
 * exactly what leaving the entry out amounted to.
 */
class ABorderSaysWhyItOwesNoRowAtAPointTest {

    /** A newtype bounded by {@code rule}, held in a record, with a guard at {@code guard} on it. */
    private static String model(String carrier, String rule, String guard, String row) {
        return """
                module example.owed

                data A = %s
                    invariant bound = value %s

                data H = { a: A }

                data Ok
                data No
                data Verdict = Ok | No

                behavior take : (h: H) -> Verdict
                let take (h) = { guard h.a.value %s else Ok
                    No }

                example take
                    | "one" : (H { a = A(%s) }) -> No
                """.formatted(carrier, rule, guard, row);
    }

    /**
     * A bound owes nothing outside itself, and a guard at the same value owes the step over.
     *
     * <p>The pair is the measurement. Both borders are on one position and one is at the very value
     * the other is, so what differs between the two answers is which rule drew the line — which is
     * the whole of what decides whether there is a far side at all.
     */
    @Test
    void theRulesRefuseThePointsOutsideABound() {
        Map<String, BorderAssessment> borders = bordersOf(model("Int", ">= 0", "<= 100", "50"));

        BorderAssessment bound = borders.get("h.a = 0");
        assertNotNull(bound, borders.keySet().toString());
        assertEquals(new souther.compiler.query.ItemAssessment.NotOwed(
                        NotOwedReason.THE_RULES_REFUSE_IT), bound.at(PointRole.OFF));
        assertEquals(new souther.compiler.query.ItemAssessment.NotOwed(
                        NotOwedReason.THE_RULES_REFUSE_IT), bound.at(PointRole.OUT));

        BorderAssessment guard = borders.get("h.a = 100");
        assertNotNull(guard, borders.keySet().toString());
        assertEquals("101", guard.against(PointRole.OFF),
                "a guard leaves values either side, so the step over is a row somebody is owed");
        assertEquals("in 101 < h.a",
                guard.operator(PointRole.OUT) + " " + guard.against(PointRole.OUT));
    }

    /**
     * A carrier with no next value names no point one step over, and one that has a next value does.
     *
     * <p>Told apart from the reason above rather than folded into it. This one is a limit of what
     * the language can write down; that one is the model refusing the value. A reader handed the
     * second about a point the rules refuse would go looking for a missing conversion.
     */
    @Test
    void aCarrierWithNoNextValueNamesNoPointBesideTheLine() {
        assertEquals("101",
                bordersOf(model("Int", ">= 0", "<= 100", "50")).get("h.a = 100")
                        .against(PointRole.OFF),
                "a whole number has a next one");
        assertEquals(new souther.compiler.query.ItemAssessment.NotOwed(
                        NotOwedReason.THE_CARRIER_NAMES_NO_NEIGHBOUR),
                bordersOf(model("Decimal", ">= 0m", "<= 100m", "50m")).get("h.a = 100")
                        .at(PointRole.OFF),
                "and a decimal has none, so the point one step over is not named at all");
    }

    /**
     * A rule that names a value owes a row at it and at the nearest value on each side of it.
     *
     * <p>Both neighbours are in the class such a rule keeps out. What it distinguishes is the one
     * value from every other one, so each of the two crosses the line and each is a point of its
     * own — and a row at one of them says nothing about the other, which is what makes them two.
     *
     * <p>Against an ordering at the same value, where one neighbour crosses and the other does not.
     * That is the whole of the difference between the two shapes of line: the value below
     * {@code <= 100} is in the partition the border bounds, and a row there answers for the point
     * away from the line rather than for a point against it.
     *
     * <p>Both spellings of the naming rule, because the roles reverse and the values do not.
     * {@code == 100} is met at the value and not beside it and {@code /= 100} is the other way
     * about, and the three places a row is asked for are the same three.
     */
    @Test
    void aRuleThatNamesAValueOwesAPointOnEachSideOfIt() {
        assertEquals(java.util.Set.of("h.a = 99", "h.a = 100", "h.a = 101"),
                atALevel(model("Int", ">= 0", "== 100", "50")),
                "the value the rule names, and the nearest value on each side of it");

        assertEquals(java.util.Set.of("h.a = 99", "h.a = 100", "h.a = 101"),
                atALevel(model("Int", ">= 0", "/= 100", "50")),
                "the same three places, whichever of the two classes the rule selects");

        assertEquals(java.util.Set.of("h.a = 100", "h.a = 101"),
                atALevel(model("Int", ">= 0", "<= 100", "50")),
                "an ordering leaves the value below its line in the partition it bounds, so that"
                        + " one is no point against the line");
    }

    /**
     * A rule that names a value draws a line exactly where the rules leave that value.
     *
     * <p>Such a rule parts the value it names from every other one, so what says whether it divides
     * anything is whether the position holds the value — and not whether it holds anything one step
     * either side of it. A rule naming a value the rules refuse divides nothing: every value left
     * is in one class, and there is no line for a row to be beside.
     *
     * <p>Every spelling at every place, because the question is about the value and the two
     * operators and the three places must not be able to answer it differently. A rule satisfied at
     * the value and one refusing it name the same value, and a value at the end of the range is as
     * much a value the rules leave as one in the middle.
     */
    @Test
    void aRuleThatNamesAValueDrawsWhereverTheRulesLeaveThatValue() {
        assertEquals(List.of("/= 0 -> [h.a = 0]", "== 0 -> [h.a = 0]",
                        "/= 50 -> [h.a = 50]", "== 50 -> [h.a = 50]",
                        "/= 100 -> [h.a = 100]", "== 100 -> [h.a = 100]",
                        "/= 200 -> []", "== 200 -> []"),
                linesDrawnBy("/= 0", "== 0", "/= 50", "== 50", "/= 100", "== 100",
                        "/= 200", "== 200"),
                "a value the rules leave is a line wherever in the range it falls, and a value they"
                        + " refuse is no line at all");
    }

    /**
     * At an end of the range one of the two values beside the line is a value the rules leave and
     * the other is not, and the two are answered apart.
     *
     * <p>The edge case the whole of this identity is for. A rule naming the value the rules stop at
     * has a point on each side of it, and only one of them is a value a row can hold — so a border
     * that had one place for the two would have to answer for both at once, and whichever answer it
     * gave would be wrong about the other.
     *
     * <p>Both operators against one table, because the place and what the rules leave there are the
     * same for the two and only the role turns over. That is the whole of what putting the identity
     * in the place and the classification in the rule buys, said as one measurement.
     */
    @Test
    void atAnEndOfTheRangeTheTwoValuesBesideTheLineAreAnsweredApart() {
        assertEquals(List.of(
                        "== 100: beside below = 99 (OFF), beside above = THE_RULES_REFUSE_IT",
                        "/= 100: beside below = 99 (ON), beside above = THE_RULES_REFUSE_IT",
                        "== 0: beside below = THE_RULES_REFUSE_IT, beside above = 1 (OFF)",
                        "/= 0: beside below = THE_RULES_REFUSE_IT, beside above = 1 (ON)",
                        "== 50: beside below = 49 (OFF), beside above = 51 (OFF)",
                        "/= 50: beside below = 49 (ON), beside above = 51 (ON)"),
                besideTheLineOf("== 100", "/= 100", "== 0", "/= 0", "== 50", "/= 50"),
                "the value beside the line on each side is asked for on its own, and the rules"
                        + " refusing one of them says nothing about the other");
    }

    /**
     * A rule that names a value has no point in one of the four roles, and says which and why.
     *
     * <p>The class its own value is in holds that value and nothing else, so there is nowhere in it
     * away from the line: {@code == 50} has no {@code IN} point and {@code /= 50} no {@code OUT}
     * one. That is not a point the rules refuse — it is a word the technique has for something such
     * a line does not have — and a reader told nothing about it cannot tell the two apart from the
     * four words being four.
     *
     * <p>An order beside them, where every word is played and none of this arises.
     */
    @Test
    void aRuleThatNamesAValueHasNoPointInOneOfTheFourRoles() {
        assertEquals(List.of("ON x1", "OFF x2", "IN none", "OUT x2"),
                rolesOf(onlyLineOf(model("Int", ">= 0 && value <= 100", "== 50", "50"))),
                "what a rule naming a value keeps out is two runs and two values, and what it keeps"
                        + " is the value alone");
        assertEquals(List.of("ON x2", "OFF x1", "IN x2", "OUT none"),
                rolesOf(onlyLineOf(model("Int", ">= 0 && value <= 100", "/= 50", "50"))),
                "and the same places one class over");
        assertEquals(List.of("ON x1", "OFF x1", "IN x1", "OUT x1"),
                rolesOf(onlyLineOf(model("Int", ">= 0 && value <= 100", "<= 50", "50"))),
                "an order plays every one of the four once");

        assertEquals(RoleAnswer.Reason.THE_CLASS_AT_THE_LINE_HOLDS_ONE_VALUE,
                ((RoleAnswer.NoPoint) onlyLineOf(model("Int", ">= 0 && value <= 100", "== 50", "50"))
                        .inEachRole().get(PointRole.IN)).why(),
                "and what settles the absence is the class at the line holding one value");
    }

    /** How many points of one line play each of the four roles, every role answered. */
    private static List<String> rolesOf(Border line) {
        List<String> said = new ArrayList<>();
        line.inEachRole().forEach((role, answer) -> said.add(role + " " + switch (answer) {
            case RoleAnswer.Played played -> "x" + played.at().size();
            case RoleAnswer.NoPoint _ -> "none";
        }));
        return said;
    }

    /** What each guard's line asks at the value beside it on each side, over a position the rules
     *  leave {@code 0..100}. */
    private static List<String> besideTheLineOf(String... guards) {
        List<String> said = new ArrayList<>();
        for (String guard : guards) {
            Border line = onlyLineOf(model("Int", ">= 0 && value <= 100", guard, "50"));
            said.add(guard + ": " + beside(line, Towards.BELOW)
                    + ", " + beside(line, Towards.ABOVE));
        }
        return said;
    }

    /** What one line asks at the value beside it on one side, with which of the four that point is
     *  where a row is owed there. */
    private static String beside(Border line, Towards side) {
        DomainPoint at = new DomainPoint.BesideTheLine(side);
        String where = "beside " + (side == Towards.BELOW ? "below" : "above") + " = ";
        return switch (line.answer(at)) {
            case PointAnswer.NotOwed not -> where + not.reason();
            case PointAnswer.AtLine _ -> where + line.against(at) + " (" + line.roleOf(at) + ")";
            case PointAnswer.InRegion in -> where + in;
        };
    }

    /** The one line a guard of this model draws, without the two the bounds of the range draw. */
    private static Border onlyLineOf(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        List<Border> drawn = Adequacy.boundariesOf(compilation.db(), "example.owed").values()
                .stream().flatMap(List::stream).map(BorderAssessment::border)
                .filter(each -> !(each.origin() instanceof LineOrigin.InvariantOrigin)).toList();
        assertEquals(1, drawn.size(), () -> "this model draws one line of its own: " + drawn);
        return drawn.get(0);
    }

    /** The lines each guard draws over a position the rules leave {@code 0..100}, without the two
     *  the bounds of that range draw themselves. */
    private static List<String> linesDrawnBy(String... guards) {
        List<String> drawn = new java.util.ArrayList<>();
        for (String guard : guards) {
            Compilation compilation = Compilation.ofSource(
                    model("Int", ">= 0 && value <= 100", guard, "50"), "Main");
            compilation.measure(Adequacy.Asked.fullReport());
            compilation.answerEverything();
            drawn.add(guard + " -> " + Adequacy.boundariesOf(compilation.db(), "example.owed")
                    .values().stream().flatMap(List::stream)
                    .filter(each -> !(each.origin() instanceof LineOrigin.InvariantOrigin))
                    .map(BorderAssessment::label).toList());
        }
        return drawn;
    }

    /** Every value of the quantity this model's line asks a row at, as a report writes it. Asked of
     *  the answers themselves rather than of the four roles, because what is being counted is how
     *  many places the line asks for and a role is what one of them is. */
    private static java.util.Set<String> atALevel(String model) {
        BorderAssessment line = bordersOf(model).get("h.a = 100");
        assertNotNull(line, bordersOf(model).keySet().toString());
        return line.border().answers().values().stream()
                .map(PointAnswer::criterion)
                .filter(each -> each instanceof Criterion.AtTheLevel)
                .map(line.border()::label)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * A row on the line is not a row at a point past it, whatever the search had to start from.
     *
     * <p>An order with no numbers has one level — where the two are equal — and a point past it is a
     * run the level is not in. Read as a level the item accepts, a pair standing equal came back for
     * a point that lies strictly past them.
     *
     * <p><b>Of every row offered at such a point, and not of one pair.</b> Which pair a search
     * happens to compose is its own answer and moves when it learns to reach further; that a row
     * offered for a strict side stands on the line is wrong whichever pair it is. Written as one
     * literal, the check went on passing the day the search composed a different equal pair.
     *
     * <p>And nothing here says a row has to be found. Whether the search reaches one is a question
     * about how far it can look, and a point it reaches none at is reported as such — so what is
     * counted below is the points examined, which is what keeps this from passing because there was
     * nothing to look at.
     */
    @Test
    void aRowOnTheLineIsNotOfferedForAPointPastIt() {
        Compilation compiled = compiled("""
                module example.strings

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior cmp : (a: String, b: String) -> Result
                    constructs Yes, No

                let cmp (a, b) = {
                    guard a > b else No { why = 0 }
                    Yes { v = 1 }
                }

                example cmp
                    | "same" : ("b", "b") -> No { why = 0 }
                """);
        Map<String, List<BorderAssessment>> lines =
                Adequacy.searchedBoundariesOf(compiled.db(), "example.strings");
        assertNotNull(lines, "the model under test compiles");

        List<String> sides = new ArrayList<>();
        lines.values().forEach(each -> each.forEach(line -> {
            for (BorderAssessment.Point point : line.points()) {
                if (point.role().againstTheLine() || point.owed() == null) {
                    continue;   // the line itself, and the points nothing is owed at
                }
                sides.add(point.role() + " " + point.against());
                for (ItemAssessment.Attempt attempt : point.owed().searches().each()) {
                    if (!(attempt instanceof ItemAssessment.Attempt.Built built)) {
                        continue;
                    }
                    List<String> wrote = built.row().inputs().stream()
                            .map(FixtureTemplate::text).toList();
                    assertNotEquals(wrote.get(0), wrote.get(1),
                            () -> "a pair standing equal is the line itself and is at neither side"
                                    + " of it, and this one is offered for " + point.against()
                                    + ": " + wrote);
                }
            }
        }));
        assertFalse(sides.isEmpty(),
                "the sides of the line this reads, which are what the rows above are offered for");
    }

    /**
     * A side the rules leave one value wide has no point away from the border.
     *
     * <p>The same reason as a bound's far side, one arity up: every value an {@code IN} point could
     * be written at is one the rules refuse. Against a guard three values further out, where the
     * side holds values and the point is owed.
     */
    @Test
    void aSideTheRulesLeaveOneValueWideOwesNoPointAwayFromTheBorder() {
        assertEquals(new souther.compiler.query.ItemAssessment.NotOwed(
                        NotOwedReason.THE_RULES_REFUSE_IT),
                bordersOf(model("Int", ">= 0", "<= 0", "0")).get("h.a = 0").at(PointRole.IN),
                "nothing is inside `0 <= x <= 0` and away from its border");
        assertEquals("in 0 <= h.a < 3",
                inside(bordersOf(model("Int", ">= 0", "<= 3", "1")).get("h.a = 3")),
                "and three values further out the side holds values");
    }

    /**
     * A line between two positions owes all four points, and no two of them hold one pair.
     *
     * <p>Such a line is a border on the difference the two terms fall apart by, so its points are
     * ordinary: under {@code a < b} the pair where {@code a} is {@code b} less one is inside the
     * border and against it, which is its {@code ON} point, and the pairs further under that are its
     * {@code IN} point. Read as {@code a < b} against {@code a = b} with no step, the two came out
     * as one set — a row at the {@code ON} point was counted as the point away from the border, and
     * the point against it was reported as one nothing could name.
     *
     * <p>Both spellings, because the four move together. An expectation on the strict one alone
     * holds against a reading that puts every point one step the wrong way.
     */
    @Test
    void aLineBetweenTwoPositionsOwesFourPointsThatNoOnePairSatisfiesTwice() {
        assertEquals(List.of("ON: = p.b - 1", "OFF: = p.b", "IN: in p.a < p.b - 1",
                        "OUT: in p.b < p.a"),
                pointsOf(comparing("<")),
                "an open border is at its own OFF point, with the ON point one step inside it");
        assertEquals(List.of("ON: = p.b", "OFF: = p.b + 1", "IN: in p.a < p.b",
                        "OUT: in p.b + 1 < p.a"),
                pointsOf(comparing("<=")),
                "a closed border is at its own ON point, with the OFF point one step outside it");
    }

    /**
     * A row offered for a point puts that point's own coverage where it was missing.
     *
     * <p>The invariant the four criteria above do not state. What a point asks of a row and what a
     * search composes to stand for it are two answers, and holding only the first let a side be
     * offered the pair against the line: the row was labelled for the side, carried the values of
     * the point beside it, and an author who pasted it and measured again found the item still
     * uncovered — and was offered the same row next time.
     *
     * <p>Written as pasting the row back, because that is what an author does with it. Read as the
     * values the search settled on, this passes on any arithmetic that agrees with itself.
     *
     * <p>All four spellings, since the four points move together and each spelling puts a different
     * one of them on the line.
     */
    @Test
    void aRowOfferedForAPointCoversThatPointWhenItIsPastedBack() {
        for (String op : List.of("<", "<=", ">", ">=")) {
            String model = comparing(op);
            // Every point that is owed and has no row is one a row is offered for. Held first,
            // because what a wrong candidate becomes once it is checked is no candidate at all: a
            // side offered the pair against the line composes nothing rather than composing that
            // pair, and a check that walked only the rows it was given would see a point go quiet.
            assertEquals(uncovered(model), offeredFor(model).keySet(),
                    () -> "every uncovered point of `guard a " + op + " b` is offered a row");
            for (Map.Entry<String, String> offered : offeredFor(model).entrySet()) {
                String point = offered.getKey();
                Map<String, BorderAssessment> after = bordersOf(withRow(model, offered.getValue()));
                BorderAssessment line = after.get("p.a = p.b");
                assertNotNull(line, after.keySet().toString());
                PointRole role = java.util.stream.Stream.of(PointRole.values())
                        .filter(each -> point.equals(line.label(each)))
                        .findFirst().orElseThrow(() -> new AssertionError(
                                "the row was offered for " + point + ", which this line has no"
                                        + " point of: " + after.keySet()));
                assertTrue(line.owedAt(role).hasRowWitness(),
                        () -> "a row offered for `" + point + "` of `guard a " + op + " b` does not"
                                + " cover it: " + offered.getValue());
            }
        }
    }

    /**
     * A side of such a line is offered a row over a carrier that names no next value.
     *
     * <p>The point against the line is not, and that is the difference. A `Decimal` names no value
     * one step from anything, so the two points against the line are not named at all; the two sides
     * are ordinary — every pair where one is under the other is in one of them — and asking the
     * carrier for the nearest pair is asking it the wrong question. Answered that way, `a < b` over
     * decimals had both of its sides owed and neither of them offered a row, and the block said
     * nothing composes one about a pair anybody could write by hand.
     */
    @Test
    void aSideOfALineOverACarrierWithNoNextValueIsStillOfferedARow() {
        String model = comparing("<").replace("a: Int, b: Int", "a: Decimal, b: Decimal")
                .replace("a = 3, b = 3", "a = 3m, b = 3m");
        BorderAssessment line = bordersOf(model).get("p.a = p.b");
        assertNotNull(line, bordersOf(model).keySet().toString());

        assertEquals(new souther.compiler.query.ItemAssessment.NotOwed(
                        NotOwedReason.THE_CARRIER_NAMES_NO_NEIGHBOUR), line.at(PointRole.ON),
                "a decimal names no pair one step inside the line");
        assertEquals(java.util.Set.of("p.a < p.b", "p.b < p.a"), offeredFor(model).keySet(),
                "and both sides of it are pairs this composes, being sets rather than nearest"
                        + " anything");
    }

    /** Each point of this model's line that is owed a row and has none, as the report labels it. */
    private static java.util.Set<String> uncovered(String model) {
        BorderAssessment line = bordersOf(model).get("p.a = p.b");
        assertNotNull(line, bordersOf(model).keySet().toString());
        return java.util.stream.Stream.of(PointRole.values())
                .filter(role -> line.owedAt(role) != null)
                .filter(role -> !line.owedAt(role).hasRowWitness())
                .map(line::label)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** Every row this model is offered for its line between two positions, by the point it is
     *  offered for. */
    private static Map<String, String> offeredFor(String model) {
        Adequacy.Filling filling = compiled(model).db()
                .ask(new Adequacy.Generated("example.owed", "cmp")).value();
        assertNotNull(filling, "the model under test compiles");
        Map<String, String> out = new java.util.LinkedHashMap<>();
        filling.boundaries().rows().forEach(row -> out.put(String.join(" x ", row.labels()),
                row.inputs().stream().map(FixtureTemplate::text)
                        .collect(java.util.stream.Collectors.joining(", "))));
        assertFalse(out.isEmpty(), "the model under test is offered rows at its line");
        return out;
    }

    /** The same model with one more row written at {@code inputs}. */
    private static String withRow(String model, String inputs) {
        return model + "    | \"offered\" : (" + inputs + ") -> Ok\n";
    }

    /** Each point of the model's one line, as {@code role: relation against}. */
    private static List<String> pointsOf(String model) {
        BorderAssessment line = bordersOf(model).get("p.a = p.b");
        assertNotNull(line, bordersOf(model).keySet().toString());
        return java.util.stream.Stream.of(PointRole.ON, PointRole.OFF, PointRole.IN, PointRole.OUT)
                .map(role -> role + ": " + line.operator(role) + " " + line.against(role)).toList();
    }

    /** Two whole numbers of one record, compared. */
    private static String comparing(String op) {
        return """
                module example.owed

                data P = { a: Int, b: Int }

                data Ok
                data No
                data Verdict = Ok | No

                behavior cmp : (p: P) -> Verdict
                let cmp (p) = { guard p.a %s p.b else Ok
                    No }

                example cmp
                    | "on the line" : (P { a = 3, b = 3 }) -> Ok
                """.formatted(op);
    }

    /**
     * A point a row already sits at is not a point a search came back empty from.
     *
     * <p>The two answers are about different things and both reach an author: what the rows showed
     * is a verdict, and what was tried is what {@code --generate} prints. A side of a line between
     * two positions is the one point this compiler composes nothing for, and answered as a search
     * that ran and failed it put a reason under every such side on every run — including the sides a
     * row is in and the sides nothing measured, which is the specific work a person is handed that
     * they may already have done.
     */
    @Test
    void aPointARowIsAlreadyAtIsNotOneASearchCameBackEmptyFrom() {
        Map<String, BorderAssessment> borders = bordersOf(BOTH_SIDES);
        BorderAssessment line = borders.get("p.a = p.b");
        assertNotNull(line, borders.keySet().toString());

        // Well under and well over, which is what the two points away from the border ask for. A
        // row one step under the line is the ON point rather than the IN point, and a test written
        // with one would be asserting about the point beside the one it names.
        assertTrue(line.owedAt(PointRole.IN).hasRowWitness(), "a row is well under the line");
        assertTrue(line.owedAt(PointRole.OUT).hasRowWitness(), "and one is well over it");
        for (PointRole role : List.of(PointRole.IN, PointRole.OUT)) {
            assertFalse(line.owedAt(role).worthSearching(), role.toString());
            assertFalse(line.owedAt(role).searches().ran(), role.toString());
        }

        // And the block an author reads says nothing about them, because nothing is owed there.
        String block = souther.compiler.report.GeneratedRows.of(
                compiled(BOTH_SIDES), "example.owed", "cmp", SourceRendering.namedByIdentity(SourceLayouts.NONE)).text();
        assertFalse(block.contains("p.a < p.b"), block);
        assertFalse(block.contains("p.a > p.b"), block);
    }

    /** A line between two positions with a row either side of it and none on it. */
    private static final String BOTH_SIDES = """
            module example.owed

            data P = { a: Int, b: Int }

            data Ok
            data No
            data Verdict = Ok | No

            behavior cmp : (p: P) -> Verdict
            let cmp (p) = { guard p.a < p.b else Ok
                No }

            example cmp
                | "well under" : (P { a = 1, b = 5 }) -> No
                | "well over" : (P { a = 5, b = 1 }) -> Ok
            """;

    /** What a row inside this border has to do, as the report writes it. */
    private static String inside(BorderAssessment border) {
        return border.operator(PointRole.IN) + " " + border.against(PointRole.IN);
    }

    /** Every border of the one behavior {@code model} declares, by the line it is at. */
    private static Map<String, BorderAssessment> bordersOf(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.boundariesOf(compilation.db(), "example.owed");
        assertNotNull(boundaries, "the model under test compiles");
        Map<String, BorderAssessment> out = new java.util.LinkedHashMap<>();
        boundaries.values().forEach(each -> each.forEach(b -> out.put(b.label(), b)));
        return out;
    }

    private static Compilation compiled(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }
}
