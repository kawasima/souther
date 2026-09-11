package souther.compiler.partition;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code guard} comparing one position against another draws a line, and the rows are owed it.
 *
 * <p>The line is where the two are equal. A guard's arms are above the line and below-or-on it, so a
 * row on the line takes the same arm as one well below it, and the arms cannot stand in for it — a
 * row on the line is the one thing that tells a rule written {@code >} from one written {@code >=}
 * (spec §every-obligation-against-its-line-has-a-row).
 *
 * <p>Nothing asked for it. The reader that turns a comparison into a line wants a constant on one
 * side, and where there is none it produced nothing at all: no line, no obligation, and a note saying
 * the partition could not read the comparison — which is not a demand and is counted nowhere. So a
 * model whose every rule is of this shape reported every measure full and {@code --strict} exited
 * {@code 0} while nothing had ever been evaluated at the line each rule turns on.
 *
 * <p>Which positions those are is asked of the carrier and not of the type. Two operands compare only
 * when they are of one type, and a type is not what makes a line measurable: two newtypes of one base
 * are two types ordered alike and have a line, while two positions each declared as one case of an
 * enumeration are comparable on their sum's order, range over less than it, and have none.
 */
class ALineBetweenTwoPositionsIsStillALineTest {

    /** The shape the defect was found in: a charge against a ceiling, each a newtype of its own with
     *  a minimum of its own, so no invariant row lands on the diagonal by coincidence. */
    private static final String TWO_NEWTYPES = """
            module example.relational

            data Charge = Int
                invariant value >= 0
            data Ceiling = Int
                invariant value >= 1000

            data NoBenefit
            data Benefit = { amount: Charge }
            data Result = NoBenefit | Benefit

            behavior benefitOf : (charge: Charge, ceiling: Ceiling) -> Result
                constructs Benefit, Charge
            let benefitOf (charge, ceiling) = {
                guard charge.value > ceiling.value else NoBenefit
                Benefit { amount = Charge(charge.value - ceiling.value) }
            }

            example benefitOf
                | "over the ceiling" : (Charge(100000), Ceiling(80000))
                    -> Benefit { amount = Charge(20000) }
                | "under the ceiling" : (Charge(50000), Ceiling(80000)) -> NoBenefit
                | "charge = 0 x ceiling = 1000" : (Charge(0), Ceiling(1000)) -> NoBenefit
            """;

    /** The same with a row on the line. Under `>` a charge equal to the ceiling is no benefit; under
     *  `>=` it is a benefit of nothing, so this row is what tells the two apart. */
    private static final String ON_THE_LINE = TWO_NEWTYPES
            + "    | \"at the ceiling\" : (Charge(1000), Ceiling(1000)) -> NoBenefit\n";

    /** Two plain {@code Int} parameters. Nothing bounds either of them, so the behavior has no axis
     *  at all — and the line its body draws is still a line. */
    private static final String NO_AXIS = """
            module example.bare

            data NoBenefit
            data Benefit = { amount: Int }
            data Result = NoBenefit | Benefit

            behavior benefitOf : (charge: Int, ceiling: Int) -> Result
                constructs Benefit
            let benefitOf (charge, ceiling) = {
                guard charge > ceiling else NoBenefit
                Benefit { amount = charge - ceiling }
            }

            example benefitOf
                | "over" : (100000, 80000) -> Benefit { amount = 20000 }
                | "under" : (50000, 80000) -> NoBenefit
            """;

    /** Two positions of one enumeration, which counts on the place its cases are declared at. */
    private static final String ENUMERATION = """
            module example.rank

            data Bronze
            data Silver
            data Gold
            data Rank = Bronze | Silver | Gold

            data No
            data Yes = { r: Rank }
            data Result = No | Yes

            behavior cmp : (a: Rank, b: Rank) -> Result
                constructs Yes
            let cmp (a, b) = {
                guard a > b else No
                Yes { r = a }
            }

            example cmp
                | "over" : (Gold, Bronze) -> Yes { r = Gold }
                | "under" : (Bronze, Gold) -> No
            """;

    /** Two {@code String} positions, which order on their own values. */
    private static final String TEXT = """
            module example.text

            data No
            data Yes = { s: String }
            data Result = No | Yes

            behavior cmp : (a: String, b: String) -> Result
                constructs Yes
            let cmp (a, b) = {
                guard a > b else No
                Yes { s = a }
            }

            example cmp
                | "over" : ("m", "b") -> Yes { s = "m" }
                | "under" : ("b", "m") -> No
            """;

    /** Two positions declared as cases of one sum. They compare, because the sum lists them both,
     *  and a case is not an enumeration: neither carries places of its own. */
    private static final String NO_CARRIER = """
            module example.caserel

            data Bronze
            data Silver
            data Gold
            data Rank = Bronze | Silver | Gold

            data No
            data Yes = { r: Rank }
            data Result = No | Yes

            behavior cmp : (a: Bronze, b: Gold) -> Result
                constructs Yes
            let cmp (a, b) = {
                guard a > b else No
                Yes { r = a }
            }

            example cmp
                | "under" : (Bronze, Gold) -> No
            """;

    /** A measure of each position rather than each position. Both sides are lengths, which are whole
     *  numbers, so the line is on the same order a number's is. */
    private static final String MEASURED = """
            module example.sized

            data No
            data Yes = { s: String }
            data Result = No | Yes

            behavior cmp : (a: String, b: String) -> Result
                constructs Yes
            let cmp (a, b) = {
                guard String.length(a) > String.length(b) else No
                Yes { s = a }
            }

            example cmp
                | "over" : ("mmm", "b") -> Yes { s = "mmm" }
                | "under" : ("b", "mmm") -> No
            """;

    /** Two fields of one record whose rule leaves the line between them no value at all. Each field's
     *  own range runs everywhere the other's does; no pair on the diagonal is in either. */
    private static final String RULED_OUT_BY_THE_RECORD = """
            module example.jointneg

            data Pair = { a: Int, b: Int }
                invariant a < b

            data No
            data Yes
            data Result = No | Yes

            behavior cmp : (p: Pair) -> Result
            let cmp (p) = {
                guard p.a > p.b else No
                Yes
            }

            example cmp
                | "under" : (Pair { a = 1, b = 5 }) -> No
            """;

    /** The same record one character apart, whose rule does admit the diagonal. */
    private static final String ALLOWED_BY_THE_RECORD =
            RULED_OUT_BY_THE_RECORD.replace("invariant a < b", "invariant a <= b");

    /** Two positions under different parameters whose rules the ranges cannot hold: a hole against a
     *  single value. Their ranges overlap at zero and one of them refuses zero. */
    private static final String A_HOLE_AND_A_POINT = """
            module example.holeneg

            data NonZero = Int
                invariant value /= 0

            data Zero = Int
                invariant value == 0

            data No
            data Yes
            data Result = No | Yes

            behavior cmp : (a: NonZero, b: Zero) -> Result
            let cmp (a, b) = {
                guard a.value > b.value else No
                Yes
            }

            example cmp
                | "over" : (NonZero(1), Zero(0)) -> Yes
                | "under" : (NonZero(-1), Zero(0)) -> No
            """;

    /** An expression on one side, which names no position a row can be written at. */
    private static final String NOT_A_TERM = """
            module example.offset

            data NoBenefit
            data Benefit = { amount: Int }
            data Result = NoBenefit | Benefit

            behavior benefitOf : (charge: Int, ceiling: Int) -> Result
                constructs Benefit
            let benefitOf (charge, ceiling) = {
                guard charge > ceiling + 1000 else NoBenefit
                Benefit { amount = charge - ceiling }
            }

            example benefitOf
                | "over" : (100000, 80000) -> Benefit { amount = 20000 }
                | "under" : (50000, 80000) -> NoBenefit
            """;

    /** Two positions whose minima leave them no count in common. */
    private static final String NO_COMMON_COUNT = """
            module example.apart

            data Low = Int
                invariant value <= -1
            data High = Int
                invariant value >= 1

            data No
            data Yes = { v: Low }
            data Result = No | Yes

            behavior cmp : (a: Low, b: High) -> Result
                constructs Yes
            let cmp (a, b) = {
                guard a.value > b.value else No
                Yes { v = a }
            }

            example cmp
                | "under" : (Low(-5), High(5)) -> No
            """;

    @Test
    void aComparisonOfTwoPositionsAsksForARowWhereTheyAreEqual() {
        String report = report(TWO_NEWTYPES);

        assertTrue(report.contains("read as benefitOf/charge: = ceiling"), report);
        assertTrue(report.contains("border      borders 3   obligations 2/4"), report);
    }

    /**
     * The row on the line meets the point where the two terms meet, and is met by a row that reached
     * the comparison — the same rule a line against a constant is met by.
     *
     * <p>And meets that point alone. The border owes a row one step from the line as well, which is
     * a different pair: `charge > ceiling` is open where they meet, so the row on the line is the
     * point outside and the point inside is where the charge is one over. A reading that had them as
     * one set would call this border covered on the strength of a row that is at the other side of
     * it.
     */
    @Test
    void aRowOnTheLineMeetsIt() {
        String report = report(ON_THE_LINE);

        assertTrue(report.contains("border      borders 3   obligations 3/4"), report);
        assertFalse(report.contains("read as benefitOf/charge: = ceiling\n"),
                report);
        assertTrue(report.contains("read as benefitOf/charge: = ceiling + 1"),
                report);
    }

    /**
     * The line is not the axis's, so a behavior with no axis has one all the same.
     *
     * <p>This is the case the old reading cannot be patched into. There is no position here for a line
     * to be a count of: two plain {@code Int} parameters have no invariant, no threshold and no axis,
     * and the body still draws a line between them.
     */
    @Test
    void aBehaviorWithNoAxisStillDrawsALineBetweenItsPositions() {
        String report = report(NO_AXIS);

        assertTrue(report.contains("read as benefitOf/charge: = ceiling"), report);
        assertTrue(report.contains("border      borders 1   obligations 2/4"), report);
    }

    /** An enumeration counts on the place its cases are declared at, so it reaches this by the same
     *  route a number does. */
    @Test
    void anEnumerationDrawsOneToo() {
        String report = report(ENUMERATION);

        assertTrue(report.contains("read as cmp/a: = b"), report);
    }

    /** A carrier whose values are strings reaches this the way one whose values count does. Nothing
     *  here is about numbers: the line is where the two positions hold the same place on one order. */
    @Test
    void aCarrierOfStringsDrawsOneToo() {
        String report = report(TEXT);

        assertTrue(report.contains("read as cmp/a: = b"), report);
    }

    /**
     * Recognised by the carrier and not by the type the comparison type-checked under.
     *
     * <p>Two cases of one sum order against each other — the sum lists them both — while neither is
     * an enumeration and neither carries places of its own. So the comparison is legal and the line
     * is not one anything can say where to write, and no line is drawn.
     */
    @Test
    void aComparableTypeWithNoCarrierDrawsNone() {
        String report = report(NO_CARRIER);

        assertFalse(report.contains("point cmp/a = b"), report);
        assertTrue(report.contains("border      not measured (no line was derived at any position)"),
                report);
    }

    /**
     * And what each position is left with is the carrier, which is what was found.
     *
     * <p>The word beside the one above, because that one holds over any way of drawing no line and
     * this one says which. Both positions stand on an order this counts nothing on, so a sum over
     * them has nothing to be spaced by — and that is established where the orders are asked for
     * rather than left over when three readings declined.
     *
     * <p>Paired with {@link #aCarrierOfStringsDrawsOneToo}, which is the same question answered the
     * other way: two strings also count nothing and the place they meet is a line, so a reader
     * asking for counting orders before the narrower readings would refuse a line the model draws.
     * Together they hold the question to "is there an order this quantity can be realized on" and
     * away from "is this domain numeric".
     */
    @Test
    void aCarrierNoOrderCountsOnSaysThatAtEachPositionOfTheQuantity() {
        assertEquals(List.of("a: UNSUPPORTED_DOMAIN", "b: UNSUPPORTED_DOMAIN"),
                notRead(NO_CARRIER));
    }

    /**
     * An offset on one side moves the line rather than taking it away.
     *
     * <p>{@code charge > ceiling + 1000} is {@code charge - ceiling > 1000}: a line on the same
     * distance, one thousand along it. Read as a position against a position, the offset made the
     * second side something no line could be drawn against, and the rule went unread — while the
     * check enforced it and refused every row past it.
     *
     * <p>It still divides neither position, so the note under the classes measure is the one a
     * relation gets.
     */
    @Test
    void anOffsetOnOneSideMovesTheLineRatherThanTakingItAway() {
        String report = report(NOT_A_TERM);

        assertTrue(report.contains("read as benefitOf/charge: = ceiling + 1000"),
                report);
        assertTrue(report.contains("read as benefitOf/charge: = ceiling + 1001"),
                report);
        assertTrue(notReadAbout(report, "charge"), report);
    }

    /**
     * A line the quantity never reaches is said, and it is not a border.
     *
     * <p>The rules leave the two positions nothing in common: {@code a} runs to minus one and
     * {@code b} from one, so the distance between them never comes near the place they would be
     * equal. That is not a border whose row nobody could find — it is not a border, and the
     * comparison says so.
     *
     * <p>Apart from {@link #aRuleTheRangesCouldNotTakeInIsNotAProofEither}, where the ranges do
     * meet and the pair on the line is what the rules refuse. There the line is real and the row for
     * it is unproven; here the quantity stops short of the line. Held alike, a rule stating
     * something no row satisfies was reported as one this compiler could not find a witness for.
     */
    @Test
    void aLineNoCountSatisfiesIsSaidAndNotCounted() {
        assertEquals(List.of("a: RULE_CUTS_OUTSIDE_WHAT_THE_QUANTITY_HOLDS",
                        "b: RULE_CUTS_OUTSIDE_WHAT_THE_QUANTITY_HOLDS"),
                notRead(NO_COMMON_COUNT),
                "both positions are named, and the rule was read to the end");
        assertEquals(2, borders(NO_COMMON_COUNT),
                "the two the newtypes' own bounds draw, and none between the positions");
    }

    /**
     * The partition is unchanged and says so where it is.
     *
     * <p>A rule relating two positions divides neither of them, which is what the classes measure has
     * always answered here and still answers. The two are separate answers about one comparison, and
     * the note about the first of them belongs under the first of them — printed under the boundary
     * count it sat two rows beneath the line that same comparison had drawn.
     */
    @Test
    void thePositionIsStillOneNothingDividesAndTheNoteIsUnderThePartition() {
        String report = report(TWO_NEWTYPES);

        int partition = report.indexOf("    partition ");
        int note = report.indexOf("dividing one, about `charge`");
        int boundary = report.indexOf("    border ");

        assertTrue(note > partition && note < boundary,
                "the note about the classes sits under the classes measure:\n" + report);
        assertTrue(report.contains(
                "it relates two positions rather than dividing one, about `charge`"), report);
    }

    /**
     * The row offered for such a line puts one value at both positions.
     *
     * <p>Which is the whole of what makes it a row on the line. A search that settled one position
     * and left the other to its own range would offer a row beside the line as readily as one on it,
     * and the row a person is handed is the one they answer.
     */
    @Test
    void theRowOfferedForTheLinePutsOneValueAtBothPositions() {
        String rows = generated(TWO_NEWTYPES);

        assertTrue(rows.contains("(Charge(1000), Ceiling(1000))"), rows);
    }

    /**
     * A line on a measure of two positions is drawn and read, and nothing here promises it.
     *
     * <p>Three answers and not one. The line is where the two lengths are equal and the rows can be
     * read against it; nothing here writes a value from a length — four is not what goes at the
     * position, it is four characters somebody has to choose — so no witness is found; and with no
     * witness nothing is counted.
     *
     * <p>What is said about that matters more than the absence. Two strings of one length are the
     * easiest row in the file to write by hand, so a sentence saying no value can be written there
     * would be false, and the one written says what this could not do.
     */
    @Test
    void aLineOnAMeasureIsReadAndPromisedByNothing() {
        String report = report(MEASURED);
        String rows = generated(MEASURED);

        assertFalse(report.contains("no row is at the OFF point ("), report);
        // The point is at a distance of nothing, which is what a level of a distance is — how far
        // the row stands from the other position, and which position that is is each reading's.
        assertTrue(report.contains(
                "nothing could show a row can be written at the OFF point ("), report);
        assertTrue(report.contains("read as cmp/String.length(a): = String.length(b)"), report);
        assertTrue(rows.contains("nothing here could build a representative for it"), rows);
        assertTrue(rows.contains("does not make one unwritable"), rows);
    }

    /**
     * A rule the ranges could not take in is not a range that says nothing is missing.
     *
     * <p>{@code /= 0} leaves a hole and {@code == 0} leaves one value, and the two positions are
     * under different parameters, so nothing relates them and their ranges overlap at zero. Read as a
     * proof, that asks for a diagonal row at a value one of the two refuses outright.
     *
     * <p>Nor does "every rule was read" answer it. The checker understands a disequality perfectly
     * well and it reaches no range, so a position with a hole in it looks unbounded from there.
     */
    @Test
    void aRuleTheRangesCouldNotTakeInIsNotAProofEither() {
        String report = report(A_HOLE_AND_A_POINT);

        assertFalse(report.contains("no row is at the OFF point ("),
                "zero is the only place both ranges hold and one position refuses it:\n" + report);
        assertTrue(report.contains(
                "nothing could show a row can be written at the OFF point ("), report);
        assertTrue(report.contains("read as cmp/a: = b"), report);
    }

    /**
     * Two ranges overlapping is not two positions holding one value.
     *
     * <p>A place in both ranges is one each position admits on its own, and a rule relating them can
     * refuse the pair each half would have taken. Under {@code invariant a < b} the two ranges run
     * over each other everywhere and the diagonal holds nothing — so what the rules leave the
     * distance between them never reaches the place they would be equal, and there is no border
     * there at all. Read off the two ranges instead, a row that cannot exist was asked for and
     * {@code --strict} refused a model for not writing it.
     */
    @Test
    void aRuleRelatingTheTwoPositionsIsNotAnsweredByTheirRangesOverlapping() {
        assertEquals(List.of("p.a: UNSUPPORTED_PARTITION_SHAPE", "p.b: UNSUPPORTED_PARTITION_SHAPE",
                        "p.a: RULE_CUTS_OUTSIDE_WHAT_THE_QUANTITY_HOLDS",
                        "p.b: RULE_CUTS_OUTSIDE_WHAT_THE_QUANTITY_HOLDS"),
                notRead(RULED_OUT_BY_THE_RECORD),
                "the record's own rule relates them, and the guard's line is outside the distance"
                        + " that rule leaves between them");
        assertEquals(1, borders(RULED_OUT_BY_THE_RECORD),
                "the diagonal holds nothing, so the guard has no border to owe a row at — and the"
                        + " record's own rule, which is what leaves the distance where it is, has"
                        + " the one line here");
    }

    /**
     * And the same rule one character apart does owe the row.
     *
     * <p>The pair to the case above. Without it, refusing every line under a record that relates its
     * fields would pass just as well, and that would drop the rows the model does owe wherever an
     * author wrote a rule of any kind.
     *
     * <p>Two lines, because the model states two rules about the pair: the record's clause and the
     * body's guard. They fall on one quantity and each is its own line — a row answering one says
     * nothing about the other, and what each owes is read off the rule that drew it.
     */
    @Test
    void aRuleThatAdmitsTheDiagonalStillOwesTheRow() {
        String report = report(ALLOWED_BY_THE_RECORD);

        assertTrue(report.contains("read as cmp/p.a: = p.b"), report);
        assertTrue(report.contains("border      borders 2   obligations 1/2"), report);
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    /**
     * What the first behavior of {@code model} left unread, as the position it is about and the
     * reason it was left with.
     *
     * <p>Off the evidence rather than out of the rendered report. What a document prints for a
     * reason is a projection made elsewhere, and a test reading the sentence is held to how it is
     * worded as much as to what it says.
     */
    private static List<String> notRead(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).modules().get(0).behaviors().get(0)
                .partition().notRead().stream()
                .map(each -> each.at() + ": " + each.reason()).toList();
    }

    /** How many borders the first behavior of {@code model} draws, which is what a line the
     *  quantity never reaches does not add to. */
    private static int borders(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).modules().get(0).behaviors().get(0)
                .lines().size();
    }

    private static String generated(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return souther.compiler.report.GeneratedRows.of(compilation, null, null, true,
                SourceRendering.namedByIdentity(compilation.texts())).text();
    }

    /** Held here so a rename of the report's own words does not quietly turn every assertion above
     * into one that passes on a report saying nothing. */
    @Test
    void theReportStillNamesTheMeasuresTheseAssertionsRead() {
        String report = report(TWO_NEWTYPES);

        assertEquals(1, report.split("    border ", -1).length - 1, report);
        assertEquals(1, report.split("    partition ", -1).length - 1, report);
    }

    /**
     * Whether any line of {@code block} saying a rule left the position with no line is about
     * {@code position}.
     *
     * <p>Asked as a line rather than as a prefix. A finding about a rule names the rule first and
     * the position after it, and one about a position names the position — so a test matching
     * `+not read: <position>+` stopped meaning anything for the first kind rather than failing,
     * which is a negative assertion that passes because the words moved.
     *
     * <p>Either word, because the report writes two: a reading that stopped is `+not read+` and a
     * rule read to the end that divided no position is `+no line+`. Which of them a rule gets is
     * its reason's business and not this one's — what is asked here is whether the position was
     * named at all.
     */
    private static boolean notReadAbout(String block, String position) {
        return block.lines().anyMatch(line ->
                (line.contains("not read:") || line.contains("no line:"))
                && (line.contains("not read: " + position + " ")
                        || line.contains("no line: " + position + " ")
                        || line.contains("about `" + position + "`")));
    }
}
