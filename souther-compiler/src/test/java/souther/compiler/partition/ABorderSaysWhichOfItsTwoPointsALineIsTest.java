package souther.compiler.partition;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.check.BehaviorContract;
import souther.compiler.check.Carrier;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Towards;
import souther.compiler.check.Clause;
import souther.compiler.check.DeclaredLine;
import souther.compiler.check.InvariantStatementId;
import souther.compiler.check.PartId;
import souther.compiler.check.ClauseName;
import souther.compiler.check.RuleRef;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A line the report names says which of a border's two points it is.
 *
 * <p>Both are values a row is owed at one border and the numbers differ by one step, so a report
 * naming neither prints two lines a reader cannot tell apart. Which one a value is turns on whether
 * the border is closed or open — the same {@code AT} is the {@code ON} point of {@code <= 100} and
 * the {@code OFF} point of {@code < 100} — so it cannot be read off the value or off which side of
 * the cut it sits (spec §example-report-vocabulary, §a-border-says-which-point-it-owes).
 *
 * <p>Measured with a closed border against an open one, differing in the operator alone. An absolute
 * expectation on one of them would pass against a report that answered {@code ON} everywhere, which
 * is what the answer was before it was derived at all.
 */
class ABorderSaysWhichOfItsTwoPointsALineIsTest {

    /** The clause here states one thing, so the end it places comes out of its first conjunct. */
    private static final int THE_ONLY_CONJUNCT = 0;


    /** {@code <=}: the line's own values satisfy the rule, so 100 is inside and 101 is the step out. */
    private static final String CLOSED = model("<=");

    /** {@code <}: they do not, so 100 is outside and the point inside is the step back to 99. */
    private static final String OPENED = model("<");

    private static String model(String op) {
        return """
                module example.border

                data Ok
                data No
                data Result = Ok | No

                behavior sized : (n: Int) -> Result

                let sized (n) =
                    if n %s 100 then Ok else No

                example sized
                    | "small" : (1) -> Ok
                """.formatted(op);
    }

    /**
     * The two points of a closed border, named for what they are.
     *
     * <p>100 satisfies {@code n <= 100} and so lies inside the partition the border bounds; 101 is
     * the first value outside it.
     */
    @Test
    void aClosedBorderIsAtItsOwnOnPoint() {
        String report = report(CLOSED);

        // The role is the point's and the value is the reading's, so the two are two lines and
        // the pairing is what this test is about: read apart, `= 100` would answer for whichever
        // role the report happened to print it under.
        assertTrue(report.contains("""
                the ON point (comparison@10:10)
                          · read as sized/n: = 100"""), report);
        assertTrue(report.contains("""
                the OFF point (comparison@10:10)
                          · read as sized/n: = 101"""), report);
    }

    /**
     * The same border written {@code <}, where both answers move.
     *
     * <p>Not merely a different pair of numbers. 100 is still the value the rule names and is now the
     * point outside, and the point inside is 99 — so a reader told only that a row is missing at 100
     * is told the same thing about two rules that ask for opposite things.
     */
    @Test
    void anOpenBorderIsAtItsOwnOffPoint() {
        String report = report(OPENED);

        assertTrue(report.contains("""
                the OFF point (comparison@10:10)
                          · read as sized/n: = 100"""), report);
        assertTrue(report.contains("""
                the ON point (comparison@10:10)
                          · read as sized/n: = 99"""), report);
    }

    /**
     * The value alone does not say it, which is what the pair is here to show.
     *
     * <p>Both models name 100. The role differs, so a report that printed the value and not the role
     * would answer these two rules identically — and an assertion written against one of them alone
     * would hold against a report that never derived the role at all.
     */
    @Test
    void theSameValueIsTheOtherPointUnderTheOtherOperator() {
        assertTrue(report(CLOSED).contains("""
                the ON point (comparison@10:10)
                          · read as sized/n: = 100"""), report(CLOSED));
        assertTrue(report(OPENED).contains("""
                the OFF point (comparison@10:10)
                          · read as sized/n: = 100"""), report(OPENED));
    }

    /**
     * The JSON writes the four items under the border that owes them, each with what it asks for.
     *
     * <p>The role and the relation are two questions. {@code point} says what a row written there is
     * for; {@code relation} and {@code against} say what the row has to do, and the same {@code = 100}
     * is the {@code on} point of one of these models and the {@code off} point of the other. Neither
     * produces the other, and a document carrying one of them would be asking its reader to work out
     * the closed-border rule for themselves.
     */
    @Test
    void theJsonWritesEveryPointUnderItsBorder() {
        assertEquals(List.of("on:= 100", "off:= 101", "in:in n < 100", "out:in 101 < n"),
                pointsAsked(CLOSED));
        assertEquals(List.of("on:= 99", "off:= 100", "in:in n < 99", "out:in 100 < n"),
                pointsAsked(OPENED));
    }

    /**
     * A bound's point comes from the end it was placed at.
     *
     * <p>A discrete carrier steps a strict bound onto the value it leaves, so {@code value > 5}
     * bounds an {@code Int} at 6 — a value the type admits, and the {@code ON} point. The rule is
     * written {@code >} and the answer is {@code ON}, which is the operator and the answer
     * disagreeing: what settles it is where the end came to rest, and a bound read off its operator
     * would call this the {@code OFF} point and send an author after a 5 the type refuses.
     */
    @Test
    void aBoundsPointComesFromItsEndAndNotFromItsOperator() {
        String report = report(bounded("Int", "> 5"));

        assertTrue(report.contains("the ON point value = 6"), report);
        assertFalse(report.contains("value = 5"), report);
    }

    /**
     * A bound owes one point, and its spelling moves where that point is rather than what it is.
     *
     * <p>The invariant path answered from an operator would put these two at one value and tell them
     * apart by the role — which is what a guard's border does and what a bound's does not. A bound
     * leaves nothing outside itself, so there is no value over the line for a row to be at: the
     * spelling is spent moving the end, and both ends that arrive are inside.
     *
     * <p>Written as a pair for the same reason the guard tests are. An expectation on {@code >= 5}
     * alone holds against a reader that ignores the end and answers 5 for both, and one on {@code >
     * 5} alone holds against a reader that ignores the operator.
     */
    @Test
    void aBoundsSpellingMovesItsPointRatherThanChangingWhichPointItIs() {
        String closed = report(bounded("Int", ">= 5"));
        String strict = report(bounded("Int", "> 5"));

        assertTrue(closed.contains("the ON point value = 5"), closed);
        assertTrue(strict.contains("the ON point value = 6"), strict);
        // And neither owes a row over the line. A bound leaves nothing outside itself, so the two
        // points out there are excluded — said in those words rather than left out, because a border
        // showing two of four items and nothing beside them reads as this compiler being short.
        for (String report : List.of(closed, strict)) {
            assertTrue(report.contains(
                    "no OFF point is owed"), report);
            assertTrue(report.contains(
                    "no OUT point is owed"), report);
            assertTrue(report.contains("excluded — the rules leave no value there"), report);
            assertFalse(report.contains("no row is at the OFF point"), report);
        }
        // Asked of the value the line is named by, which is how a bound is written now: a line a
        // declaration is owed is named in the terms it wrote and never by the position a behavior
        // met it at (issue #1062). Left as `take/h.a = 5`, this refused a string the report has no
        // way of printing and said nothing about where the line is.
        assertFalse(strict.contains("point value = 5"), strict);
    }

    /**
     * The end is read rather than assumed closed, at the one place both kinds are built, and a bound
     * that stops short of its own line on a carrier that steps is refused rather than stepped here.
     *
     * <p>A continuous carrier has no step to take, so {@code value > 5.0m} on a {@code Decimal}
     * reaches {@link LineOrigin.InvariantOrigin} as an exclusive 5 — measured at the construction
     * site, where both kinds arrive. Where the rule stops is the line either way, and which value
     * the point against it stands at is the order's answer.
     *
     * <p><b>Which is not a licence to repair one here.</b> Two readings say where a rule leaves off
     * and they are independent: the rules of the value say what range they leave, and the order the
     * quantity is on says where the rule's own threshold parts its values. A border holds the two
     * against each other and normalizes neither — so a bound whose range stops at 5 while its rule
     * parts the values at 6 is a model this refuses rather than one it quietly agrees with.
     *
     * <p>What a {@code Decimal} does with the same shape is {@code
     * ABoundOnACarrierWithNoStepIsStillALineTest}, over a model rather than a hand-made origin: the
     * order names no value beside the line, so the point cannot be written down and says so.
     */
    @Test
    void aBoundThatStopsShortOfItsLineWhereTheOrderStepsIsRefused() {
        Border kept = borderOf(
                new LineOrigin.InvariantOrigin(
                        theOnlyStatement(),
                        souther.compiler.numeric.EndSide.LOWER, true));
        assertEquals("= 5", kept.demand(PointRole.ON).criterion().asked(kept.cut().of()),
                "a bound that admits its own end is at that end's ON point");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> borderOf(new LineOrigin.InvariantOrigin(
                        theOnlyStatement(),
                        souther.compiler.numeric.EndSide.LOWER, false)),
                "a rule parting the values at 6 over a range that stops at 5 is two readings of one"
                        + " model that disagree");
        assertTrue(refused.getMessage().startsWith(
                        "a bound whose line is not where what it leaves stops"),
                refused.getMessage());
    }

    /** The border a bound draws at 5 on an `Int` whose rules leave 5 and up. */
    private static Border borderOf(LineOrigin origin) {
        Carrier carrier = new Carrier.Whole();
        boolean admits = origin instanceof LineOrigin.InvariantOrigin bound && bound.holdsAtTheValue();
        return Border.at(lineAt(new AxisId("take", "h.a"), carrier, Count.of(5)), origin,
                new NumericDomain.Bounds(new Endpoint(Count.of(5), admits), null));
    }

    /**
     * The two roles are defined at the cut and at the value beside it, and refused anywhere else.
     *
     * <p>Under {@code <= 100} the value below the cut is 99, which is inside the partition and not
     * against its border — an {@code IN} point, and neither of the two words here. Answered by the
     * arithmetic alone it comes back {@code OFF}, which would say a value well inside the range is
     * outside it. What keeps the derivation honest is the domain rather than the formula, so a side
     * no rule places a row on is refused.
     *
     * <p>An invariant places a row on no side at all, so every side but the cut is refused there.
     */
    @Test
    void aSideNoRowIsOwedOnIsNamedRatherThanGivenTheNearerWord() {
        // `<= 100`: the cut belongs to the passing side, so the row beside it is the one above, and
        // the value below the cut is 99 — inside the partition and away from its border, which is
        // the IN point and neither of the two words against the line.
        Carrier carrier = new Carrier.Whole();
        LineOrigin closed = new LineOrigin.EnsuresOrigin(
                new WhichLine.OfAComparisonOfAPart(new ClauseStatementId(
                        new souther.compiler.check.PartId<>(new RuleRef.Ensures(
                                new BehaviorContract.RuleId(null, 0, 0, null), "cap"),
                                THE_ONLY_CONJUNCT), 0)),
                new LineFacts(new souther.compiler.check.ComparisonClaim.Cut(Towards.BELOW, true)));
        Border border = Border.at(lineAt(new AxisId("cap", "n"), carrier, Count.of(100)), closed,
                new NumericDomain.Bounds(null, null));

        assertEquals("= 100", border.demand(PointRole.ON).criterion().asked(border.cut().of()));
        assertEquals("= 101", border.demand(PointRole.OFF).criterion().asked(border.cut().of()));
        assertEquals("in n < 100",
                border.demand(PointRole.IN).criterion().asked(border.cut().of()),
                "99 is inside the partition and away from the border, which is the IN point");
        assertEquals("in 101 < n",
                border.demand(PointRole.OUT).criterion().asked(border.cut().of()));

        // A bound owes nothing outside itself, and says which of the three answers settled it.
        Border bound = borderOf(new LineOrigin.InvariantOrigin(
                        theOnlyStatement(),
                        souther.compiler.numeric.EndSide.LOWER, true));
        assertEquals(new Demand.NotOwed(NotOwedReason.THE_RULES_REFUSE_IT),
                bound.demand(PointRole.OFF));
        assertEquals(new Demand.NotOwed(NotOwedReason.THE_RULES_REFUSE_IT),
                bound.demand(PointRole.OUT));
        assertEquals("in 5 < h.a", bound.demand(PointRole.IN).criterion().asked(bound.cut().of()),
                "and the run the bound bounds, without the edge itself, is inside it");
    }

    /** A line on one position's own values, at a place of its carrier. */
    private static BoundaryTarget lineAt(AxisId axis, Carrier carrier,
                                         souther.compiler.numeric.Place at) {
        souther.compiler.inputs.NumericTerm.ValueOf term =
                new souther.compiler.inputs.NumericTerm.ValueOf(
                        souther.compiler.inputs.TermPath.of(axis.term()));
        return BoundaryTarget.at(new BorderQuantity.OfACoordinate(axis.behavior(), term,
                        souther.compiler.inputs.TermOrdersFixtures.itself(term, carrier)),
                new Level.OnACarrier(carrier, at));
    }

    /** The one statement of the one conjunct that clause was written in. */
    private static DeclaredLine theOnlyStatement() {
        return new DeclaredLine.OfAStatement(new InvariantStatementId(
                new PartId<>(invariant(), THE_ONLY_CONJUNCT), 0));
    }

    /** The clause the bound tests name, which is only an identity here. */
    private static RuleRef.Invariant invariant() {
        return new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("example.bound", "Above")), 0),
                Optional.of(new ClauseName("above"))));
    }

    /** A newtype bounded by {@code rule}, held in a record, with one row well away from the bound. */
    private static String bounded(String carrier, String rule) {
        return """
                module example.bound

                data Above = %s
                    invariant above = value %s

                data Holder = { a: Above }

                data Ok

                behavior take : (h: Holder) -> Ok

                let take (h) = Ok

                example take
                    | "away from the bound" : (Holder { a = Above(%s) }) -> Ok
                """.formatted(carrier, rule, "Int".equals(carrier) ? "30" : "30.0m");
    }

    /**
     * The combination counts are not on the border's line, nor on the partition's.
     *
     * <p>Counting combinations across two positions is the neighbouring technique. Printed at the end
     * of the partition line it sat beside the border counts, where the two read as parts of one
     * total.
     */
    @Test
    void combinationsAreCountedOnALineOfTheirOwn() {
        String report = report(CLOSED);

        for (String line : report.lines().toList()) {
            if (line.contains("pairs ")) {
                assertTrue(line.trim().startsWith("combination "), report);
            }
            assertFalse(line.contains("partition ") && line.contains("pairs "), report);
        }
    }

    /** Held so a rename of the report's own words does not turn the assertions above into ones that
     *  pass on a report saying nothing. */
    @Test
    void theReportStillNamesTheMeasuresTheseAssertionsRead() {
        String report = report(CLOSED);

        assertEquals(1, report.split("    border ", -1).length - 1, report);
        assertEquals(1, report.split("    partition ", -1).length - 1, report);
    }

    /** Each item of the model's borders as {@code point:relation against}, in document order. */
    private static List<String> pointsAsked(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        JsonNode root = JsonMapper.builder().build()
                .readTree(AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts())));
        List<String> out = new ArrayList<>();
        root.findValues("boundaries").forEach(each -> each.forEach(
                b -> b.get("items").forEach(i -> out.add(i.get("point").asString() + ":"
                        + i.get("relation").asString() + " " + i.get("against").asString()))));
        return out;
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }
}
