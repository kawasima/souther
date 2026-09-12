package souther.compiler.partition;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.compiler.report.GeneratedRows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule draws a line wherever it cuts something the rows can be measured against, and what it cuts
 * is not always a position.
 *
 * <p>Issue #871. An equivalence partition is defined by conditions that may combine several
 * interacting variables, and each atomic condition defines a border of it (ISTQB CTAL-TA v4.0
 * §3.1.1). Souther drew a line where a rule compared one position against a written value, and where
 * it compared one position against another, and nothing anywhere else — so
 * {@code 300 * straw + 600 * choco <= 4800} drew nothing, and what the report showed instead were the
 * four sides of the box the two positions sit in. That is the case the technique exists for.
 *
 * <p>And the defect was wider than the issue's own title. {@code a + 1 <= 10} is a line at one
 * position, and {@code 2 * a <= 10} is a line on twice one position, and neither was read either:
 * what the readers wanted was a spelling, and what a rule says is arithmetic.
 *
 * <p>Two of these are the design tests rather than examples. {@code 2 * a <= 9} names a threshold the
 * quantity never takes — the even numbers have no nine in them — so a reading that assumed a
 * threshold is one of its quantity's own values puts a row at four and a half. And {@code a > b} over
 * strings has an order and no numbers, so a reading that turned every distance into a count loses it.
 * A border machinery that answers both without a branch for either is one a fourth kind of quantity
 * can be added to.
 */
class ABorderIsALineOnWhateverTheRuleCutsTest {

    /** Two whole-number positions, each bounded, so the box is finite and the search has ends. */
    private static String guarded(String condition) {
        return """
                module example.shape

                data Bound = Int
                    invariant value >= 0
                    invariant value <= 10

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (a: Bound, b: Bound) -> Result
                    constructs No, Yes
                let f (a, b) = {
                    guard %s else No { why = 0 }
                    Yes { v = 1 }
                }

                example f
                    | "one" : (Bound(1), Bound(1)) -> Yes { v = 1 }
                """.formatted(condition);
    }

    /** The issue's own model: a rule over what an order costs, and two positions it is priced on. */
    private static final String BASKET = """
            module example.basket

            data Strawberry = Int
                invariant value >= 0
                invariant value <= 10

            data Choco = Int
                invariant value >= 0
                invariant value <= 6

            data Order = { straw: Strawberry, choco: Choco }

            data TooExpensive = { total: Int }
            data Ok = { total: Int }
            data Result = TooExpensive | Ok

            let total (order: Order): Int =
                Int.add(Int.multiply(300, order.straw.value), Int.multiply(600, order.choco.value))

            behavior price : (order: Order) -> Result
                constructs TooExpensive, Ok
            let price (order) = {
                guard total(order) <= 4800 else TooExpensive { total = total(order) }
                Ok { total = total(order) }
            }

            example price
                | "cheap" : (Order { straw = Strawberry(1), choco = Choco(1) }) -> Ok { total = 900 }
            """;

    /** A bare position against a written value, which is the reading that always worked. */
    @Test
    void aPositionAgainstAValueIsALineAtThatPosition() {
        String report = report(guarded("a.value <= 10"));

        assertTrue(report.contains("point value = 10"), report);
    }

    /**
     * A constant added to the position moves the line rather than taking it away.
     *
     * <p>{@code a + 1 <= 10} is {@code a <= 9}, which is a line at one position and divides it into
     * classes — so this one reaches the partition measure as well as the border measure.
     */
    @Test
    void aConstantAddedToThePositionMovesTheLine() {
        String report = report(guarded("a.value + 1 <= 10"));

        assertTrue(report.contains("read as f/a: = 9"), report);
        assertTrue(report.contains("read as f/a: = 10"),
                report);
        assertTrue(report.contains("equivalence partitions"),
                "a line at one position divides it:\n" + report);
    }

    /**
     * A position scaled is that position, and the border is on it.
     *
     * <p>A form and any positive multiple of it order the rows the same way, so {@code 2 * a} is not
     * a quantity beside {@code a} — it is {@code a}, said in twos. Held apart, this rule divided
     * nothing and the position it cuts was reported as having one equivalence partition where the
     * model states two (issue #880).
     *
     * <p>The rows are the same either way: what the form comes to at ten and at twelve is
     * {@code a = 5} and {@code a = 6}. What changes is which of the two an author is asked for and
     * what the class between them is called.
     */
    @Test
    void aScaledPositionIsThePositionItScales() {
        String report = report(guarded("Int.multiply(2, a.value) <= 10"));

        assertTrue(report.contains("read as f/a: = 5"), report);
        assertTrue(report.contains("read as f/a: = 6"), report);
        assertTrue(report.contains("`a/5 < x <= 10`"),
                "and the position is divided at five, not left undivided:\n" + report);
        assertFalse(report.contains("2 * a"),
                "twice a position is not a quantity beside it:\n" + report);
    }

    /**
     * A threshold the written form never takes parts the position's own values all the same.
     *
     * <p>The design test. Nine is not a value {@code 2 * a} takes, and the rule cuts there: the row
     * inside is where the form comes to eight and the row outside where it comes to ten, which are
     * {@code a = 4} and {@code a = 5}. Neither is nine halved. A reading that took the threshold for
     * one of the quantity's own values would ask for {@code a = 4.5}, a value the position refuses;
     * one that stepped a whole step from the threshold would ask for the two in the wrong roles; and
     * one that divided the threshold out would have to write four and a half into a class name.
     */
    @Test
    void aThresholdTheWrittenFormNeverTakesPartsThePositionsOwnValues() {
        String report = report(guarded("Int.multiply(2, a.value) <= 9"));

        assertTrue(report.contains("read as f/a: = 4"), report);
        assertTrue(report.contains("read as f/a: = 5"), report);
        assertFalse(report.contains("4.5"),
                "nine halved is no value of the position, so nothing names one:\n" + report);
    }

    /** Both sides moving with the row, with a constant on one of them: a line on the distance, one
     *  along it. */
    @Test
    void aConstantOnOneSideOfARelationMovesTheLineAlongTheDistance() {
        String report = report(guarded("a.value + 1 > b.value"));

        assertTrue(report.contains("read as f/a: = b"), report);
        assertTrue(report.contains("read as f/a: = b - 1"), report);
    }

    /**
     * A form over two positions draws the line the model is about.
     *
     * <p>And the value past it is the next one the form takes rather than the next one any position
     * takes: {@code 3a + 6b} moves in threes, so the row outside {@code <= 48} is where it comes to
     * 51.
     */
    @Test
    void aFormOverTwoPositionsDrawsTheLineTheModelIsAbout() {
        String report = report(guarded(
                "Int.add(Int.multiply(3, a.value), Int.multiply(6, b.value)) <= 48"));

        assertTrue(report.contains("read as f/3 * a + 6 * b: = 48"), report);
        assertTrue(report.contains("read as f/3 * a + 6 * b: = 51"), report);
        // And what it leaves the positions is said in the same words as well. A rule over a quantity
        // that is not one position's own values divides none of them, whoever wrote it.
        assertTrue(report.contains(
                "it relates two positions rather than dividing one, about `a`"), report);
        assertTrue(report(guarded(
                        "Int.add(Int.multiply(3, a.value), Int.multiply(6, b.value)) <= 48"))
                        .contains("it relates two positions rather than dividing one, about `a`"),
                "a body's conditions say it the same way");
    }

    /** A form on both sides is one form. What the rule states is the difference, so the position
     *  written on both sides is counted once. */
    @Test
    void aFormOnBothSidesIsOneForm() {
        String report = report(guarded(
                "Int.add(Int.multiply(3, a.value), Int.multiply(6, b.value))"
                        + " <= Int.add(b.value, 48)"));

        assertTrue(report.contains("read as f/3 * a + 5 * b: = 48"), report);
    }

    /**
     * An order over strings is a border and carries no numbers.
     *
     * <p>The other design test. Two strings stand one above the other and no measurable distance
     * apart, so the only level the quantity takes is the one where they meet — and both sides of that
     * level are inhabited, because one string is still above another. A space that answered "no next
     * value" and "no value that way" with one word takes the {@code IN} point off this border; a
     * space that turned the distance into a count asks a carrier with no numbers to write one.
     */
    @Test
    void anOrderOverStringsIsABorderWithNoNumbersInIt() {
        String report = report("""
                module example.text

                data No = { why: Int }
                data Yes = { s: String }
                data Result = No | Yes

                behavior cmp : (a: String, b: String) -> Result
                    constructs No, Yes
                let cmp (a, b) = {
                    guard a > b else No { why = 0 }
                    Yes { s = a }
                }

                example cmp
                    | "over" : ("m", "b") -> Yes { s = "m" }
                    | "under" : ("b", "m") -> No { why = 0 }
                """);

        assertTrue(report.contains("read as cmp/a: = b"), report);
        assertTrue(report.contains("no ON point is owed at a = b")
                        && report.contains("names no value there"),
                "a string has no next string, so the point one along is not owed:\n" + report);
        assertFalse(report.contains("no IN point is owed") || report.contains("no OUT point is owed"),
                "and both sides of the line are still there, which is a different answer:\n"
                        + report);
    }

    /**
     * The issue's own model draws the border it is about, and the rows offered stand at it.
     *
     * <p>{@code (10, 3)} comes to 4800 and {@code (9, 4)} to 5100, which are the two points against
     * the line. Neither is a value of either position, and which position moved to reach them is the
     * search's answer rather than anything the report asks an author for.
     */
    @Test
    void theModelTheIssueWasWrittenAboutDrawsItsOwnBorder() {
        String report = report(BASKET);
        String rows = generated(BASKET);

        assertTrue(report.contains(
                "read as price/600 * order.choco + 300 * order.straw: = 4800"),
                report);
        assertTrue(report.contains(
                "read as price/600 * order.choco + 300 * order.straw: = 5100"),
                report);
        assertTrue(rows.contains("Strawberry(10), choco = Choco(3)"), rows);
        assertTrue(rows.contains("Strawberry(9), choco = Choco(4)"), rows);
    }

    /**
     * A level the rules leave no row at is said to be out of reach, and not merely unfound.
     *
     * <p>The two are different facts and only one of them a reader may act on (ADR-0091). Here every
     * position is bounded and every combination of those bounds was walked, so a level nothing
     * reaches is a level the model refuses; a search that had stopped, or one that composed no
     * candidate at all, would say so in its own words.
     */
    @Test
    void aLevelTheRulesLeaveNoRowAtIsSaidToBeOutOfReach() {
        String report = report(guarded(
                "Int.add(Int.multiply(3, a.value), Int.multiply(5, b.value)) <= 7"));

        assertTrue(report.contains("the ON point (comparison@14:71)"), report);
        assertTrue(report.contains("read as f/3 * a + 5 * b: = 7"
                        + " — the rules leave no value at 3 * a + 5 * b = 7"), report);
        assertFalse(report.contains("nothing composed one: the rules leave no value"),
                "a proof does not arrive under the opening a failed search gets:\n" + report);
    }

    /** Two positions whose values fill: every distance is a distance, and no distance has a next
     *  one. */
    private static String dense(String condition) {
        return """
                module example.dense

                data D = Decimal
                    invariant value >= 0m
                    invariant value <= 100m

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (a: D, b: D) -> Result
                    constructs No, Yes
                let f (a, b) = {
                    guard %s else No { why = 0 }
                    Yes { v = 1 }
                }

                example f
                    | "under" : (D(1m), D(50m)) -> Yes { v = 1 }
                    | "over" : (D(50m), D(1m)) -> No { why = 0 }
                """.formatted(condition);
    }

    /**
     * A distance is a number, and it is one on an order with no smallest step too.
     *
     * <p>Two decimals a rule holds one apart are one apart. Read as a count of the carrier's steps
     * and reached by walking them, a carrier with no step answered "nowhere" for every pair — so the
     * border was met by no row, including the rows that meet it, and nothing could compose one.
     */
    @Test
    void aDistanceOverAnOrderWithNoStepIsStillADistance() {
        String report = report(dense("a.value <= b.value - 1m"));

        assertTrue(report.contains("read as f/a: = b - 1"), report);
        assertFalse(report.contains("nothing composed one"),
                "a pair one apart is composable over decimals:\n" + report);
    }

    /**
     * And it need not be a whole number of anything.
     *
     * <p>Held as a count of steps, a threshold of half a step was an {@code ArithmeticException}
     * thrown out of the measure rather than a rule read or refused.
     */
    @Test
    void aDistanceNeedNotBeAWholeNumberOfSteps() {
        String report = report(dense("a.value <= b.value - 0.5m"));

        assertTrue(report.contains("read as f/a: = b - 0.5"), report);
    }

    /**
     * The place a pair stands at answers to both positions at the distance the rule names.
     *
     * <p>Two positions each left {@code [0, 100]} have every place in common where the rule cuts
     * where they meet, and only {@code [1, 100]} where it holds them one apart — the pair at zero
     * puts the first at minus one, which its own rules refuse. Intersected without the distance, the
     * search offered exactly that pair and the report said every value tried had been refused.
     */
    @Test
    void thePlaceAPairStandsAtAnswersToBothPositionsAtThatDistance() {
        String report = report(dense("a.value <= b.value - 1m"));

        assertFalse(report.contains("was refused"),
                "the pair offered is one both positions admit:\n" + report);
    }

    /**
     * A form over positions whose values fill is not walked, and nothing is proved by not walking it.
     *
     * <p>{@code 2a + 4b = 9} holds at {@code a = 0.5, b = 2}. A search that enumerated the box one
     * whole number at a time missed it and reported that the rules leave no value there — which is
     * the one answer a reader may act on, so an unsound proof here is worse than an unsettled
     * search (ADR-0091).
     */
    @Test
    void aFormOverAnOrderThatFillsIsNotProvedEmptyByAWalkOverWholeNumbers() {
        String report = report(dense("a.value * 2m + b.value * 4m <= 9m"));

        assertTrue(report.contains("read as f/2 * a + 4 * b: = 9"), report);
        assertFalse(report.contains("the rules leave no value at 2 * a + 4 * b"),
                "nothing walked the whole of this box, so nothing was proved about it:\n" + report);
    }

    /**
     * A rule written the other way round is the same rule.
     *
     * <p>{@code 48 >= 3a + 6b} and {@code 3a + 6b <= 48} draw one line. Turned round in two of the
     * three readings and not the third, the first drew its border on {@code -3a - 6b} at
     * {@code -48} — the same four points under a name no author wrote, and a different line from the
     * rule written the other way.
     */
    @Test
    void aRuleWrittenTheOtherWayRoundIsTheSameRule() {
        String report = report(guarded(
                "48 >= Int.add(Int.multiply(3, a.value), Int.multiply(6, b.value))"));

        assertTrue(report.contains("read as f/3 * a + 6 * b: = 48"), report);
        assertFalse(report.contains("-3 * a"), report);
    }

    /**
     * And a rule is read the same way wherever it is written.
     *
     * <p>Which quantity a comparison cuts was settled in three places, and the reading of a
     * behavior's clauses called two of them — so a form in an {@code ensures} drew no border while
     * the same form in a {@code guard} drew one.
     */
    @Test
    void aFormInAnEnsuresDrawsTheLineAFormInAGuardDraws() {
        String report = report("""
                module example.ens

                data Bound = Int
                    invariant value >= 0
                    invariant value <= 10

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (a: Bound, b: Bound) -> Result
                    ensures asked = Yes -> Int.add(Int.multiply(3, a.value),
                        Int.multiply(6, b.value)) <= 48

                example f
                    | "one" : (Bound(1), Bound(1)) -> Yes { v = 1 }
                """);

        assertTrue(report.contains("read as f/3 * a + 6 * b: = 48"), report);
        assertTrue(report.contains("read as f/3 * a + 6 * b: = 51"), report);
    }

    /**
     * A form written the other way round is the same form, whichever way its coefficients sign.
     *
     * <p>Turned round only where every coefficient was negative, {@code 48 >= 3a - 6b} faced neither
     * way and kept the quantity {@code -3a + 6b} at {@code -48} — the same line as
     * {@code 3a - 6b <= 48}, under a name no author wrote and with an identity of its own.
     */
    @Test
    void aFormWithMixedSignsIsTurnedRoundToo() {
        String written = report(guarded(
                "Int.subtract(Int.multiply(3, a.value), Int.multiply(6, b.value)) <= 12"));
        String turned = report(guarded(
                "12 >= Int.subtract(Int.multiply(3, a.value), Int.multiply(6, b.value))"));

        assertTrue(written.contains("read as f/3 * a - 6 * b: = 12"), written);
        assertTrue(turned.contains("read as f/3 * a - 6 * b: = 12"), turned);
        assertFalse(turned.contains("-3 * a"), turned);
    }

    /**
     * And the subject stays the one the author wrote where the source states it.
     *
     * <p>Canonical is not the same as derived. {@code charge > ceiling} is a line about the charge,
     * and orienting every form by its positions' names would rename half the borders in a report to
     * say the same thing backwards.
     */
    @Test
    void theSubjectIsTheOneTheSourceNames() {
        String report = report(guarded("b.value > a.value"));

        assertTrue(report.contains("read as f/b: = a"), report);
        assertFalse(report.contains("read as f/a: = b"), report);
    }

    /**
     * A side of a border over an order that fills is looked at.
     *
     * <p>A side is met anywhere past its end, and any level that way witnesses it. Asked for the
     * <em>neighbour</em> instead — which only an order that steps has — no level was looked at, and
     * every side of every form over decimals was offered no row.
     */
    @Test
    void aSideOfAFormOverAnOrderThatFillsIsOfferedARow() {
        String rows = generated(dense("a.value * 2m + b.value * 4m <= 9m"));

        assertTrue(rows.contains("D(0m), D(2.25m)"),
                "the point where the form comes to nine:\n" + rows);
        assertTrue(rows.contains("D(0m), D(2m)"),
                "and a pair past it, which is what stands for the side the rows leave open:\n"
                        + rows);
    }

    /**
     * A search offers a value the rules admit, or it offers none and says so.
     *
     * <p>An end the rules exclude is not one they leave. Worked out from the numbers rather than
     * asked of the carrier, {@code value > 0m} gave a lower end of zero, the row offered was the one
     * the record refuses, and the report said every candidate had been rejected — which reads as the
     * decoder having decided something about the edge.
     *
     * <p>What is not promised is that it finds one. This box holds a pair at every level of the
     * form and the walk takes one value for a position it cannot enumerate and solves the last
     * against it, so a value that leaves nothing for the last position to be ends the walk. The
     * report says the search stopped, which is what happened.
     */
    @Test
    void aSearchOffersAValueTheRulesAdmitOrOffersNone() {
        String excluded = """
                module example.excluded

                data D = Decimal
                    invariant value > 0m
                    invariant value <= 100m

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (a: D, b: D) -> Result
                    constructs No, Yes
                let f (a, b) = {
                    guard a.value * 2m + b.value * 4m <= 9m else No { why = 0 }
                    Yes { v = 1 }
                }

                example f
                    | "under" : (D(1m), D(1m)) -> Yes { v = 1 }
                """;
        String rows = generated(excluded);

        assertFalse(rows.contains("D(0m)"),
                "zero is no value of `D`, so no row is offered at it:\n" + rows);
        assertFalse(report(excluded).contains("was refused"),
                "and nothing was handed to the decoder to refuse:\n" + report(excluded));
    }

    /**
     * A level out of reach is proved out of reach, however wide the box is.
     *
     * <p>A wide box is not walked a step at a time: what the positions still to be chosen can add up
     * to bounds what this one may be, so an equation with one answer is one value tried. Used only
     * to reject a choice after making it, the walk runs the width of the box and the budget runs out
     * — here a million steps short of {@code a = b = 1000000}, which is the only pair that reaches
     * the line.
     */
    @Test
    void aBoxAMillionWideIsNotWalkedAStepAtATime() {
        String report = report("""
                module example.wide

                data Bound = Int
                    invariant value >= 0
                    invariant value <= 1000000

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (a: Bound, b: Bound) -> Result
                    constructs No, Yes
                let f (a, b) = {
                    guard Int.add(a.value, b.value) <= 2000000 else No { why = 0 }
                    Yes { v = 1 }
                }

                example f
                    | "one" : (Bound(1), Bound(1)) -> Yes { v = 1 }
                """);

        assertTrue(report.contains("read as f/a + b: = 2000000"), report);
        assertFalse(report.contains("the search left something untried before reaching a + b = 2000000"),
                "one equation with one answer, in a box a million wide:\n" + report);
    }

    /**
     * A position this cannot enumerate takes a value the rest can still absorb.
     *
     * <p>Where the values fill there is nothing to step through, so one position takes a value and
     * the ones after it have to make up what is left. Taken off the end of its own range without
     * asking, the residue lands outside what they can reach and a level the box plainly holds comes
     * back unsolved: here {@code b} runs to one, so {@code a} at zero leaves 9 for {@code 4 * b} to
     * make and it can make at most 4.
     */
    @Test
    void aPositionThatCannotBeEnumeratedTakesAValueTheRestCanAbsorb() {
        String rows = generated("""
                module example.lopsided

                data Wide = Decimal
                    invariant value >= 0m
                    invariant value <= 100m

                data Narrow = Decimal
                    invariant value >= 0m
                    invariant value <= 1m

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (a: Wide, b: Narrow) -> Result
                    constructs No, Yes
                let f (a, b) = {
                    guard a.value * 2m + b.value * 4m <= 9m else No { why = 0 }
                    Yes { v = 1 }
                }

                example f
                    | "under" : (Wide(0m), Narrow(0m)) -> Yes { v = 1 }
                """);

        assertFalse(rows.contains("no row for `2 * a + 4 * b = 9`"),
                "the box holds a pair where the form comes to nine:\n" + rows);
    }

    /**
     * A library call and the operator it stands for are one rule.
     *
     * <p>{@code Int.multiply(w, h)} and {@code w * h} are the same arithmetic, and the reading is of
     * the operation the call resolved to rather than of the representation it is in. Read in one and
     * not the other, a rule the check enforced was one the measure reported as unread — and the two
     * spellings would answer differently about the same model.
     */
    @Test
    void aLibraryCallAndItsOperatorAreOneRule() {
        String written = """
                module example.spelled

                data Bound = Int
                    invariant value >= 0
                    invariant value <= 10

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (a: Bound, b: Bound) -> Result
                    constructs No, Yes
                let f (a, b) = {
                    guard %s else No { why = 0 }
                    Yes { v = 1 }
                }

                example f
                    | "one" : (Bound(1), Bound(1)) -> Yes { v = 1 }
                """;

        // Apart from where the rule is written, which is a place and not the rule. The two
        // spellings are two lengths of text and the handle a report prints points at each of them,
        // so a comparison that kept the column would be asserting the two were written alike.
        assertEquals(exceptWhereRulesAreWritten(report(written.formatted(
                        "Int.add(Int.multiply(3, a.value), Int.multiply(6, b.value)) <= 48"))),
                exceptWhereRulesAreWritten(
                        report(written.formatted("a.value * 3 + b.value * 6 <= 48"))),
                "one rule, whichever way the arithmetic is spelled");
    }

    /**
     * A dense order is not an order whose every value the quantity takes.
     *
     * <p>A decimal is a finite decimal, so {@code 3 * a} takes every {@code 3 * d} with {@code d}
     * finite — dense, and not including one: no decimal a model writes is a third. Answered by the
     * topology, the border of {@code 3 * a <= 1m} owed a row where the quantity comes arbitrarily
     * close and never arrives, and no search could ever compose it. A coverage item nothing can meet
     * is worse than one nobody has got to: it never goes away.
     *
     * <p>Two and five are not the same case. Ten is a unit among the finite decimals, so
     * {@code 2 * a} does take nine — at {@code a = 4.5} — which is why a form whose coefficients are
     * twos and fours cannot tell this apart.
     */
    @Test
    void aDenseOrderIsNotAnOrderWhoseEveryValueTheQuantityTakes() {
        String report = report("""
                module example.third

                data D = Decimal
                    invariant value >= 0m
                    invariant value <= 1m

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (a: D) -> Result
                    constructs No, Yes
                let f (a) = {
                    guard a.value * 3m <= 1m else No { why = 0 }
                    Yes { v = 1 }
                }

                example f
                    | "one" : (D(0m)) -> Yes { v = 1 }
                """);

        assertTrue(report.contains("no ON point is owed at 3 * a = 1"), report);
        assertTrue(report.contains("no OFF point is owed at 3 * a = 1"), report);
        assertFalse(report.contains("read as f/3 * a: = 1"),
                "a third is no decimal, so no row is owed where the quantity never arrives:\n"
                        + report);
    }

    /**
     * A quantity is bounded by what it is made of, so a threshold outside it draws no line.
     *
     * <p>Three times a length is never negative, whatever a rule compares it against. Read as the
     * lattice alone — every multiple of the coefficients' divisor, negative ones among them — such a
     * threshold was a border whose points a search was sent looking for and never found. It is the
     * same answer a bound outside what a position's own type leaves already gets: there is no border
     * there, and there never was one for a point to be owed at.
     */
    @Test
    void aThresholdOutsideWhatTheFormTakesDrawsNoLine() {
        String report = report(guarded(
                "Int.add(Int.multiply(3, a.value), Int.multiply(6, b.value)) <= -3"));

        assertFalse(report.contains("3 * a + 6 * b"),
                "the form runs from nought upward, so nothing is cut at minus three:\n" + report);
        // And the question the comparison raises is answered by what came of reading it. Answered by
        // which reading was tried, a rule that drew nothing reported a line as read, and the
        // positions it names went unaccounted for.
        assertTrue(notReadAbout(report, "a") && notReadAbout(report, "b"),
                "the positions are still named, and as positions nothing divided:\n" + report);
    }

    /**
     * A bound a proof is drawn from is rounded outward, never inward.
     *
     * <p>What the walk is bounded by is a proof: a value outside it is one no assignment of the rest
     * completes, and a walk that covers what is left of the box may end in {@code Impossible}. So a
     * bound rounded the wrong way takes a value the box holds out of the walk and the level it
     * reaches is proved not to exist. Written to sixteen digits at the nearest,
     * {@code a = 10000000000000001} rounded to {@code 10000000000000000} and the one pair that meets
     * this line was refused.
     */
    @Test
    void aBoundAProofIsDrawnFromIsRoundedOutward() {
        String report = report("""
                module example.exact

                data Big = Int
                    invariant value >= 0
                    invariant value <= 10000000000000001

                data Nought = Int
                    invariant value >= 0
                    invariant value <= 0

                data No = { why: Int }
                data Yes = { v: Int }
                data Result = No | Yes

                behavior f : (a: Big, b: Nought) -> Result
                    constructs No, Yes
                let f (a, b) = {
                    guard Int.add(a.value, b.value) <= 10000000000000001 else No { why = 0 }
                    Yes { v = 1 }
                }

                example f
                    | "one" : (Big(1), Nought(0)) -> Yes { v = 1 }
                """);

        assertTrue(report.contains("read as f/a + b: = 10000000000000001"), report);
        assertFalse(report.contains("the rules leave no value at a + b = 10000000000000001"),
                "the box holds exactly one pair at this line, so nothing proves it holds none:\n"
                        + report);
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    private static String generated(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return GeneratedRows.of(compilation, null, null, SourceRendering.namedByIdentity(compilation.texts())).text();
    }

    /**
     * Whether any line of {@code block} saying a rule left the position with no line is about
     * {@code position}, under either word the report writes for that.
     *
     * <p>Asked as a line rather than as a prefix. A finding about a rule names the rule first and
     * the position after it, and one about a position names the position — so a test matching
     * `+not read: <position>+` stopped meaning anything for the first kind rather than failing,
     * which is a negative assertion that passes because the words moved.
     */
    private static boolean notReadAbout(String block, String position) {
        return block.lines().anyMatch(line ->
                (line.contains("not read:") || line.contains("no line:"))
                && (line.contains("not read: " + position + " ")
                        || line.contains("no line: " + position + " ")
                        || line.contains("about `" + position + "`")));
    }

    /**
     * The same report with every citation of a rule with no name blanked, since where a rule is
     * written is a place and two spellings of one rule are at two of them.
     *
     * <p>Both spellings of a place. A border names the rule and prints the place inside the handle;
     * a condition of a decision rule is marked at the construct instead and the report names the
     * place on its own. The two are one fact about this fixture — a comparison written two ways is
     * two lengths of text — and blanking one of them would have this comparing the columns through
     * the other.
     */
    private static String exceptWhereRulesAreWritten(String report) {
        return report.replaceAll("comparison@\\d+:\\d+", "comparison@<written>")
                .replaceAll("the comparison at \\d+:\\d+", "the comparison at <written>");
    }
}
