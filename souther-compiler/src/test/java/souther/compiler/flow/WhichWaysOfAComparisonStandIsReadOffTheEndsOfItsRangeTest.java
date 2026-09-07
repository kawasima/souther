package souther.compiler.flow;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.types.BinOp;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;
import souther.compiler.diag.SourcePos;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which ways a comparison against a written whole number stands, one row per way.
 *
 * <p>Every answer here is a fact about a range: the number written is a value of it, and whether
 * anything lies beyond that number on the side a way needs is what the ends of {@code Int} decide.
 * So a comparison against an interior number stands both ways whichever way round it is written,
 * and one against the last number on the order loses the way that needs a value past it.
 *
 * <p><b>Written out rather than worked out.</b> The answers are the ones this reading gives, put
 * here as data, so that a reading which comes to them by another route is held to the same table.
 * A test that derived them would agree with whatever rule it derived them from, including a rule
 * that had the side of the comparison the wrong way round — which is the one slip a reading that
 * turns a comparison round can make, and the reason both ways of writing each comparison are here.
 *
 * <p>The two sides are positions of the input that nothing settled, so nothing about the values is
 * known beyond what {@code Int} is. A comparison of two such positions is a different question and
 * is not asked here.
 */
class WhichWaysOfAComparisonStandIsReadOffTheEndsOfItsRangeTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "go");
    private static final Core.Read POSITION = new Core.Read("n", new BindingId(OWNER, 0), Type.INT, POS);

    /** What a way is asked of, and what this reading answers for it. */
    private record Row(BinOp op, long written, boolean want, boolean stands) {

        String asked(boolean numberOnTheLeft) {
            return (numberOnTheLeft ? written + " " + op + " n" : "n " + op + " " + written)
                    + " comes out " + want;
        }
    }

    private static Row row(BinOp op, long written, boolean want, boolean stands) {
        return new Row(op, written, want, stands);
    }

    private static final long LOW = Long.MIN_VALUE;
    private static final long HIGH = Long.MAX_VALUE;

    /**
     * Every way of every comparison against a number in the middle of the order.
     *
     * <p>The positive control for the two tables below: there is a value at the number written, one
     * below it and one above it, so no way of any comparison is closed and nothing here can be
     * answered false for want of the reading working at all.
     */
    private static final List<Row> INTERIOR = List.of(
            row(BinOp.LT, 0, true, true), row(BinOp.LT, 0, false, true),
            row(BinOp.LE, 0, true, true), row(BinOp.LE, 0, false, true),
            row(BinOp.GT, 0, true, true), row(BinOp.GT, 0, false, true),
            row(BinOp.GE, 0, true, true), row(BinOp.GE, 0, false, true),
            row(BinOp.EQ, 0, true, true), row(BinOp.EQ, 0, false, true),
            row(BinOp.NE, 0, true, true), row(BinOp.NE, 0, false, true));

    /**
     * The same against the last number the order holds, at either end.
     *
     * <p>Nothing lies past either of them, so a way that needs a value there is the one that goes.
     * {@code n > HIGH} is met by nothing and {@code n <= HIGH} is failed by nothing; at the low end
     * the pair is the other way round. An equality keeps both ways at either end, because the
     * values that are not the one written are still there.
     */
    private static final List<Row> AT_THE_ENDS = List.of(
            row(BinOp.LT, HIGH, true, true), row(BinOp.LT, HIGH, false, true),
            row(BinOp.LT, LOW, true, false), row(BinOp.LT, LOW, false, true),
            row(BinOp.LE, HIGH, true, true), row(BinOp.LE, HIGH, false, false),
            row(BinOp.LE, LOW, true, true), row(BinOp.LE, LOW, false, true),
            row(BinOp.GT, HIGH, true, false), row(BinOp.GT, HIGH, false, true),
            row(BinOp.GT, LOW, true, true), row(BinOp.GT, LOW, false, true),
            row(BinOp.GE, HIGH, true, true), row(BinOp.GE, HIGH, false, true),
            row(BinOp.GE, LOW, true, true), row(BinOp.GE, LOW, false, false),
            row(BinOp.EQ, HIGH, true, true), row(BinOp.EQ, HIGH, false, true),
            row(BinOp.EQ, LOW, true, true), row(BinOp.EQ, LOW, false, true),
            row(BinOp.NE, HIGH, true, true), row(BinOp.NE, HIGH, false, true),
            row(BinOp.NE, LOW, true, true), row(BinOp.NE, LOW, false, true));

    /**
     * Every comparison, both ways it can come out, at each number that is asked about.
     *
     * <p>The tables below are what both halves of every test here are read out of, so a row taken
     * out of one goes from the answers as well and nothing else would say so.
     */
    @Test
    void everyComparisonIsAskedAboutAtEachNumber() {
        assertEquals(6 * 2, INTERIOR.size(),
                "six comparisons, each asked which ways it comes out");
        assertEquals(6 * 2 * 2, AT_THE_ENDS.size(),
                "the same at each of the two numbers the order stops at");
    }

    @Test
    void aWayStandsWhereTheRangeHoldsAValueThatTakesIt() {
        assertEquals(expected(INTERIOR, false), answered(INTERIOR, false));
        assertEquals(expected(AT_THE_ENDS, false), answered(AT_THE_ENDS, false));
    }

    /**
     * The same rows with the number written on the left, which is the same statement.
     *
     * <p>{@code 3 > n} says of {@code n} what {@code n < 3} says of it, so the way that closes at
     * an end is the one the statement closes at and not the one the operator is spelled with. Read
     * without turning the comparison round, a comparison written this way answers about the number
     * where it should answer about the position, and the two disagree exactly at the ends.
     */
    @Test
    void writingTheNumberOnTheLeftStatesTheSameThing() {
        assertEquals(turned(INTERIOR, true), answered(INTERIOR, true));
        assertEquals(turned(AT_THE_ENDS, true), answered(AT_THE_ENDS, true));
    }

    /** What the rows say, as lines. */
    private static List<String> expected(List<Row> rows, boolean numberOnTheLeft) {
        List<String> out = new ArrayList<>();
        rows.forEach(each -> out.add(each.asked(numberOnTheLeft) + ": " + each.stands()));
        return out;
    }

    /**
     * The same rows for the comparison written the other way round, whose answers are the rows'
     * own: which way a statement is written is not part of what it says.
     */
    private static List<String> turned(List<Row> rows, boolean numberOnTheLeft) {
        List<String> out = new ArrayList<>();
        rows.forEach(each -> out.add(new Row(exchanged(each.op()), each.written(), each.want(),
                each.stands()).asked(numberOnTheLeft) + ": " + each.stands()));
        return out;
    }

    /** The operator the same statement is written with when its sides are exchanged, declared here
     *  so that what this test holds the reading to does not come from the reading. */
    private static BinOp exchanged(BinOp op) {
        return switch (op) {
            case LT -> BinOp.GT;
            case GT -> BinOp.LT;
            case LE -> BinOp.GE;
            case GE -> BinOp.LE;
            case EQ -> BinOp.EQ;
            case NE -> BinOp.NE;
            default -> throw new IllegalArgumentException("not a comparison: " + op);
        };
    }

    /** What the reading answers for each row. */
    private static List<String> answered(List<Row> rows, boolean numberOnTheLeft) {
        List<String> out = new ArrayList<>();
        for (Row each : rows) {
            BinOp op = numberOnTheLeft ? exchanged(each.op()) : each.op();
            Core number = new Core.Int(each.written(), Type.INT, POS);
            Core.Binary comparison = numberOnTheLeft
                    ? new Core.Binary(op, number, POSITION, ConstructOccurrence.unwritten(), Type.BOOL, POS)
                    : new Core.Binary(op, POSITION, number, ConstructOccurrence.unwritten(), Type.BOOL, POS);
            boolean stands = Witnessed.comesOut(comparison, each.want(), read -> null);
            out.add(new Row(op, each.written(), each.want(), stands).asked(numberOnTheLeft)
                    + ": " + stands);
        }
        return out;
    }
}
