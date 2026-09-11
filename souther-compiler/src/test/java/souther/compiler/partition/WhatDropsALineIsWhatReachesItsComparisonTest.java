package souther.compiler.partition;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A comparison's line stands exactly where the values that reach the comparison reach the line.
 *
 * <p>Not where both its outcomes are ones something takes, which is the question that used to be
 * asked and is a different one. The two cases the difference shows in are opposites and had the same
 * answer: a guard under a stricter guard draws its line through values that all stop short of it,
 * and a comparison the declarations settle one way is arrived at by every row there is and takes the
 * other way out. Each has exactly one outcome nothing takes.
 *
 * <p>Only a proof drops a line. Everything the walk publishes about what arrives over-approximates
 * it, so a line the approximation does not reach is a line no arriving row reaches; the other
 * direction is not claimed, and a comparison nothing could be projected for keeps its line and its
 * rows.
 */
class WhatDropsALineIsWhatReachesItsComparisonTest {

    /** The sentence a document writes for a line the rows that arrive stop short of. */
    private static final String NOTHING_ARRIVES =
            "no row that arrives at it holds a value at its line";

    private static String reportOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    /** How many lines this report dropped for nothing arriving at them. */
    private static long droppedForNothingArriving(String source) {
        return reportOf(source).lines().filter(each -> each.contains(NOTHING_ARRIVES)).count();
    }

    private static final String AMOUNT = """
            module d

            data Amount = Int invariant value >= 0 && value <= 1000000
            data Free
            data Charged = { yen: Int }
            """;

    /**
     * The first case: a guard the guard above it has already ruled out.
     *
     * <p>Everything that reaches the second comparison is under five thousand, so the line at six
     * thousand parts values none of which get there, and the two rows it would ask for are rows
     * nobody can write.
     */
    @Test
    void aLineTheGuardsAboveItRuleOutIsDropped() {
        assertEquals(1, droppedForNothingArriving(AMOUNT + """
                behavior charge : (a: Amount) -> Free | Charged
                    constructs Charged

                let charge (a) = {
                    guard a.value < 5000 else Free
                    guard a.value < 6000 else Free
                    Charged { yen = 500 }
                }
                """));
    }

    /**
     * The second case, which is the same shape and the opposite answer.
     *
     * <p>A {@code Level} is ten or more, so the comparison inside the body cannot hold — and every
     * row arrives at it and takes it the other way. There is a row to write at the line, so the
     * border stands: what the declarations rule out is one of the comparison's outcomes and not its
     * line.
     */
    @Test
    void aLineTheDeclarationsSettleOneWayStands() {
        String report = reportOf("""
                module example.empty

                data Level = Int invariant value >= 10
                data Answer = { n: Int }

                behavior classify : (level: Level) -> Answer
                    constructs Answer

                let classify (level) =
                    if level.value < 10 then Answer { n = 1 } else Answer { n = 2 }
                """);

        assertEquals(0, report.lines().filter(each -> each.contains(NOTHING_ARRIVES)).count(),
                () -> "every row arrives at this comparison and takes it the other way: " + report);
        assertTrue(report.contains("level = 10 (comparison"),
                () -> "so the line at ten is one the measure asks about, and it is the"
                        + " comparison's own and not only the clause's: " + report);
    }

    /**
     * A path nothing reaches, with the compared position's own values untouched.
     *
     * <p>Whether anything arrives is the whole state's answer and never a projection of it. The
     * guard above asks for an amount past where an {@code Amount} stops, so nothing stands below it
     * — and none of that reaches what is known of {@code b.value}, which still runs from nought to a
     * million with the line at five thousand well inside. Read as an interval of the position the
     * comparison turns on, this line is one every row reaches; read as what arrives, no row does.
     *
     * <p>The guard above is a line of its own and is dropped for its own reason: the declarations
     * never run that far, which is a fact about them and holds wherever the rule stands. So the one
     * sentence counted here is the second comparison's.
     */
    @Test
    void aPathNothingReachesDropsTheLinesBelowItWhateverTheirOwnValuesAre() {
        assertEquals(1, droppedForNothingArriving("""
                module d

                data Amount = Int invariant value >= 0 && value <= 1000000
                data Free
                data Charged = { yen: Int }

                behavior charge : (a: Amount, b: Amount) -> Free | Charged
                    constructs Charged

                let charge (a, b) = {
                    guard a.value > 2000000 else Free
                    guard b.value < 5000 else Free
                    Charged { yen = 500 }
                }
                """));
    }

    /**
     * A value singled out is held to the same law, and the law reaches it because the decision is
     * taken where the rule is read.
     *
     * <p>Nothing that arrives at the second equality is anything but nought, so the value it names
     * is in no class of anything: the model draws the distinction and no row can stand either side
     * of it. Dropped a stage later this was never asked, because that stage only ever looked at the
     * lines that order values around them.
     */
    @Test
    void aValueSingledOutWhereNothingArrivesAtItIsDroppedToo() {
        assertEquals(1, droppedForNothingArriving(AMOUNT + """
                behavior charge : (a: Amount) -> Free | Charged
                    constructs Charged

                let charge (a) = {
                    guard a.value == 0 else Free
                    guard a.value == 5000 else Free
                    Charged { yen = 500 }
                }
                """));
    }

    /**
     * A line on a multiple of the position keeps its line, arrival or none.
     *
     * <p>What arrives is an interval of the position's own values, and this rule's quantity is twice
     * them — a level of one order is not a level of the other, so there is nothing here to meet and
     * the line stands. Which is the fail-open direction and costs precision rather than truth: this
     * particular line is one nothing arriving reaches, and the measure asks for its rows anyway.
     */
    @Test
    void aLineOnAMultipleOfThePositionIsKeptBecauseItIsOnAnotherOrder() {
        assertEquals(0, droppedForNothingArriving(AMOUNT + """
                behavior charge : (a: Amount) -> Free | Charged
                    constructs Charged

                let charge (a) = {
                    guard a.value < 2500 else Free
                    guard 2 * a.value < 6000 else Free
                    Charged { yen = 500 }
                }
                """));
    }

    /**
     * A comparison over more than one position keeps its line, whatever arrives.
     *
     * <p>What is published is an interval of one position's values, and the quantity here is the sum
     * of two — so there is nothing to meet it into, and not being able to read a fact is no proof of
     * anything. The line stands, which is the direction that leaves an author with work rather than
     * with a report about a model of theirs that is fine.
     */
    @Test
    void aLineOverSeveralPositionsIsKeptBecauseNothingCanBeProjectedOntoIt() {
        assertEquals(0, droppedForNothingArriving("""
                module d

                data Amount = Int invariant value >= 0 && value <= 1000000
                data Free
                data Charged = { yen: Int }

                behavior charge : (a: Amount, b: Amount) -> Free | Charged
                    constructs Charged

                let charge (a, b) = {
                    guard a.value < 5000 else Free
                    guard a.value + b.value < 6000 else Free
                    Charged { yen = 500 }
                }
                """));
    }

    /**
     * The right side of a short circuit stands under the left, and the arrival says so.
     *
     * <p>{@code &&} reaches its right operand having held on the left, so the same pair of lines one
     * under the other is the same pair of answers whether they are written as two guards or as one.
     * Published from the state at the top of the condition instead, the second line would have been
     * read against everything the declarations leave.
     */
    @Test
    void theRightSideOfAShortCircuitStandsUnderTheLeft() {
        assertEquals(1, droppedForNothingArriving(AMOUNT + """
                behavior charge : (a: Amount) -> Free | Charged
                    constructs Charged

                let charge (a) = {
                    guard a.value < 5000 && a.value < 6000 else Free
                    Charged { yen = 500 }
                }
                """));
    }

    /**
     * One comparison written once and reached twice is two arrivals, and they do not merge.
     *
     * <p>A helper's comparison is a site per call, and what has been established on the way to each
     * call is the caller's own. Keyed on the comparison a person wrote, the guard above one call
     * would take the line away from the other — so the answer here is exactly one dropped line and
     * not none or two.
     */
    @Test
    void oneComparisonReachedTwiceIsTwoArrivals() {
        assertEquals(1, droppedForNothingArriving("""
                module d

                data Amount = Int invariant value >= 0 && value <= 1000000
                data Free
                data Charged = { yen: Int }

                let over (n: Int): Int = if n < 6000 then 0 else 1

                behavior charge : (a: Amount, b: Amount) -> Free | Charged
                    constructs Charged

                let charge (a, b) = {
                    guard a.value < 5000 else Free
                    guard over(a.value) == 0 else Free
                    guard over(b.value) == 0 else Free
                    Charged { yen = 500 }
                }
                """));
    }
}
