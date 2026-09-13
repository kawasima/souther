package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Carrier;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.RunSource;
import souther.compiler.inputs.TermOrders;
import souther.compiler.inputs.TermOrdersFixtures;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.LinearForm;
import souther.compiler.observe.ObservedValue;
import souther.compiler.types.ValueName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A number an operation takes over a run of values is measured without any position answering it.
 *
 * <p>What the machinery around a border wants of a number is that it can be read off a row and that
 * its values are ordered. Neither wants a place, and this holds that: the run is written by hand
 * here, so what is being tested is the reading and the quantity rather than the walk that finds one
 * in a body.
 *
 * <p>What it must not gain is a position. Two lines of sixty and forty stand on the boundary of a
 * hundred as surely as one of a hundred does, so a class at the place the values are read from
 * would be a class about a rule no model states — which is why this term has no position at all
 * and every reader that would draw one cannot be handed it.
 */
class ANumberOverARunIsMeasuredWithoutAPositionTest {

    private static final TermPath UNDER = TermPath.of("lines").element().then("amount");

    private static final NumericTerm.TakenOver TOTAL = NumericTerm.TakenOver.of(
            ValueName.Stdlib.operation("List", "sum"),
            new RunSource.ProjectedOccurrences(UNDER),
            souther.compiler.types.Type.Prim.INT,
            souther.compiler.check.NewtypeInners.NONE,
            souther.compiler.check.Symbols.none(souther.compiler.DefaultStdlib.get()));

    private static final TermOrders WHOLE =
            TermOrdersFixtures.itself(TOTAL, new Carrier.Whole());

    /** The capability question, which is the one every reader that would act on a place asks. */
    @Test
    void noSinglePositionAnswersIt() {
        assertNull(TOTAL.atOnePosition(),
                "a run is answered by no one place, and that is what this term is");
        assertEquals(UNDER, TOTAL.subjectPath(),
                "where its values are read from is still said, since a reader has to be sent"
                        + " somewhere");
    }

    /** A run stands inside a sequence. One position holding one value is not a run of anything. */
    @Test
    void aRunIsNotOnePositionHoldingOneValue() {
        assertThrows(IllegalArgumentException.class,
                () -> new RunSource.ProjectedOccurrences(TermPath.of("total")));
    }

    /** The number is what the values come to, not what any one of them is. */
    @Test
    void theNumberIsWhatTheValuesAddUpTo() {
        assertEquals(Count.of(100000),
                number(WHOLE.readOver(List.of(whole(60000), whole(40000)))),
                "sixty thousand and forty thousand come to the hundred thousand a rule compares");
    }

    /** A container holding nothing comes to what the walk starts from. */
    @Test
    void anEmptyRunComesToWhatTheWalkStartsFrom() {
        assertEquals(Count.of(0), number(WHOLE.readOver(List.of())),
                "a row writing no element is a row an author can write, and its total is nought");
    }

    /** One value nobody could read leaves the total unread, and says why. */
    @Test
    void aValueThatCouldNotBeReadLeavesTheTotalUnread() {
        assertInstanceOf(NumericTerm.Reading.Missing.class,
                WHOLE.readOver(List.of(whole(1), new ObservedValue.Unknown("not read"))),
                "a total is over every value, so one that could not be read is not a total short"
                        + " of a part");
    }

    /** A form over the run reads a row and answers where the row stands, with no position asked
     *  for. */
    @Test
    void aFormOverTheRunReadsARowAndSaysWhereItStands() {
        BorderQuantity.OverAForm over = new BorderQuantity.OverAForm("decide",
                LinearForm.atom((NumericTerm) TOTAL),
                Map.of(TOTAL, WHOLE));

        assertEquals("List.sum(lines[*].amount)", over.left(),
                "a report names the number, which is what the rule is about");
        assertEquals(BorderQuantity.Stands.YES,
                over.standsAt(new Criterion.AtTheLevel(new Level.ACount(Count.of(100000))),
                        rowHolding(60000, 40000)),
                "a row whose lines come to the total stands on the line");
        assertEquals(BorderQuantity.Stands.NO,
                over.standsAt(new Criterion.AtTheLevel(new Level.ACount(Count.of(100000))),
                        rowHolding(60000, 39999)),
                "and one that comes to anything else does not");
    }

    /**
     * The search reaches the level and demands the sequence, which is where such a number is
     * realized.
     *
     * <p>What it hands back is a demand and not a value: the sequence the run is read from is what a
     * row rebuilds to move the total, and no total is written at it
     * ({@link RealizationTarget.OverARun}). Whether anything writes such a value is
     * {@link TermRealizations}' question, asked once, of every number — held here as well, this
     * would be a second reading of what can be built, free to say no on a day that one says yes.
     */
    @Test
    void theSearchDemandsTheSequenceTheRunIsReadFrom() {
        BorderQuantity.OverAForm over = new BorderQuantity.OverAForm("decide",
                LinearForm.atom((NumericTerm) TOTAL),
                Map.of(TOTAL, WHOLE));
        Standing standing = over.standingAt(
                new Criterion.AtTheLevel(new Level.ACount(Count.of(100000))));

        Realization made = new LevelRealizer().realize(standing, NothingTheRulesSay.REGION,
                NothingTheDeclarationsRefuse.at());

        assertEquals(new Realization.Found(
                        Map.of(new RealizationTarget.OverARun(TOTAL), Count.of(100000))),
                made,
                "the level is reached, and what the row has to do to be at it is come to it");
        assertEquals(TermPath.of("lines"),
                new RealizationTarget.OverARun(TOTAL).writeRoot(),
                "and the value it rebuilds is the sequence, which the run holds to one of them");
    }

    private static ObservedValue whole(long at) {
        return new ObservedValue.Integer(at);
    }

    private static Count number(NumericTerm.Reading read) {
        return (Count) assertInstanceOf(NumericTerm.Reading.Number.class, read).value();
    }

    /** A row whose only readable place is the run, holding these amounts. */
    private static BorderQuantity.Observation rowHolding(long... amounts) {
        List<ObservedValue> values = new java.util.ArrayList<>();
        for (long each : amounts) {
            values.add(whole(each));
        }
        return new BorderQuantity.Observation() {

            @Override
            public WalkResult<ObservationAtPoint> at(TermPath path) {
                throw new AssertionError("a number over a run is not read from one value, and"
                        + " asking for one is the defect this term exists to stop");
            }

            @Override
            public WalkResult<List<ObservedValue>> everyValueAt(TermPath path) {
                assertEquals(UNDER, path, "read from where the run says its values are");
                return WalkResult.reached(values);
            }
        };
    }
}
