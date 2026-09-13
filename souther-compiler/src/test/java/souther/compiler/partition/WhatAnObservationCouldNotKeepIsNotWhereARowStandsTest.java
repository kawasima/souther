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
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.ObservedValue;
import souther.compiler.types.ValueName;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A value an observation could not keep is not a value standing somewhere else.
 *
 * <p>Every limit an observation is walked under produces one word — the value is not there and the
 * walk stopped — and what the word is <em>of</em> travels with it. From there the reading has one
 * job: to arrive at the border still saying that nothing could be read, rather than that the row
 * does not stand at the point. The two are a number that did not come back and a number that came
 * back wrong, and only the second is about what somebody wrote.
 *
 * <p>Written by hand, so what is held is the reading and not a model's answer. Which budget stopped
 * the walk is the observation's own question and is settled where the limits are; every one of them
 * hands over the same {@link ObservedValue.Truncated}, so a reading that keeps the word for one
 * keeps it for all four, and holding this against four models would be holding the same law four
 * times.
 */
class WhatAnObservationCouldNotKeepIsNotWhereARowStandsTest {

    private static final TermPath UNDER = TermPath.of("lines").element().then("amount");

    private static final NumericTerm.TakenOver TOTAL = NumericTerm.TakenOver.of(
            ValueName.Stdlib.operation("List", "sum"),
            new RunSource.ProjectedOccurrences(UNDER),
            souther.compiler.types.Type.Prim.INT,
            souther.compiler.check.NewtypeInners.NONE,
            souther.compiler.check.Symbols.none(souther.compiler.DefaultStdlib.get()));

    private static final TermPath BESIDE = TermPath.of("fees").element().then("amount");

    private static final NumericTerm.TakenOver OTHER_TOTAL = NumericTerm.TakenOver.of(
            ValueName.Stdlib.operation("List", "sum"),
            new RunSource.ProjectedOccurrences(BESIDE),
            souther.compiler.types.Type.Prim.INT,
            souther.compiler.check.NewtypeInners.NONE,
            souther.compiler.check.Symbols.none(souther.compiler.DefaultStdlib.get()));

    private static final TermOrders ON_THE_TOTAL =
            TermOrdersFixtures.itself(TOTAL, new Carrier.Whole());

    private static final TermOrders ON_THE_OTHER =
            TermOrdersFixtures.itself(OTHER_TOTAL, new Carrier.Whole());

    private static final Criterion AT_A_HUNDRED =
            new Criterion.AtTheLevel(new Level.ACount(Count.of(100)));

    /**
     * A run holding a value the limits stopped is unreadable, and says which stopped it.
     *
     * <p>The value the run would come to is not this compiler's to guess: the elements it can see
     * add up to less than the total, which is exactly what a row below the line looks like.
     */
    @Test
    void aRunHoldingAValueALimitStoppedIsUnreadableAndSaysSo() {
        assertEquals(BorderQuantity.Stands.couldNotTell(
                        ReadingGap.of(Incompleteness.Code.VALUE_TRUNCATED)),
                form().standsAt(AT_A_HUNDRED,
                        run(new ObservedValue.Integer(40), new ObservedValue.Truncated())),
                "a total over a value the limits stopped is not a total that missed the line");
    }

    /** And one holding a value nothing could decode says that instead, which is the other word. */
    @Test
    void aRunHoldingAValueNothingCouldDecodeSaysThatInstead() {
        assertEquals(BorderQuantity.Stands.couldNotTell(
                        ReadingGap.of(Incompleteness.Code.VALUE_UNREADABLE)),
                form().standsAt(AT_A_HUNDRED,
                        run(new ObservedValue.Integer(40), new ObservedValue.Unknown("no"))),
                "what a limit shortened and what nothing could read are two things to tell a"
                        + " person");
    }

    /**
     * A run whose values are all there answers the question, which is the control.
     *
     * <p>Without it every reading of this passes for a quantity that never answers anything.
     */
    @Test
    void aRunWhoseValuesAreAllThereStillAnswers() {
        assertEquals(BorderQuantity.Stands.YES,
                form().standsAt(AT_A_HUNDRED,
                        run(new ObservedValue.Integer(60), new ObservedValue.Integer(40))),
                "a row whose values come to the level stands on the line");
        assertEquals(BorderQuantity.Stands.NO,
                form().standsAt(AT_A_HUNDRED,
                        run(new ObservedValue.Integer(60), new ObservedValue.Integer(39))),
                "and one that comes to anything else does not");
    }

    /**
     * A form stopped at two of its terms says both.
     *
     * <p>The form is unreadable for whatever stopped any of it. Answered from the first term the
     * walk reaches, what a reader is told is which term the map handed over first — and a map's
     * order is not something a report may turn on.
     */
    @Test
    void aFormStoppedAtTwoOfItsTermsSaysBoth() {
        BorderQuantity.OverAForm both = new BorderQuantity.OverAForm("decide",
                LinearForm.atom((NumericTerm) TOTAL)
                        .plus(LinearForm.atom((NumericTerm) OTHER_TOTAL)),
                Map.of(TOTAL, ON_THE_TOTAL, OTHER_TOTAL, ON_THE_OTHER));

        BorderQuantity.Stands stands = both.standsAt(AT_A_HUNDRED,
                new BorderQuantity.Observation() {

                    @Override
                    public WalkResult<ObservationAtPoint> at(TermPath path) {
                        throw new AssertionError("a number over a run is not read from one value");
                    }

                    @Override
                    public WalkResult<List<ObservedValue>> everyValueAt(TermPath path) {
                        return WalkResult.reached(UNDER.equals(path)
                                ? List.of(new ObservedValue.Truncated())
                                : List.of(new ObservedValue.Unknown("no")));
                    }
                });

        assertEquals(Set.of(ReadingGap.of(Incompleteness.Code.VALUE_TRUNCATED),
                        ReadingGap.of(Incompleteness.Code.VALUE_UNREADABLE)),
                assertInstanceOf(BorderQuantity.Stands.CouldNotTell.class, stands).why(),
                "a reading stopped in two ways is stopped in both of them");
    }

    /**
     * A walk that arrived and found no value says that, and not that an observation stopped.
     *
     * <p>The two leave different work and only one of them names a budget. A reader that words a
     * code as what an observation did — and something downstream does, since that is what the code
     * is for — then says a limit did something where no limit fired: the value it would have
     * stopped was never met.
     */
    @Test
    void aWalkThatReachedNoValueIsNotAnObservationThatStopped() {
        BorderQuantity.Stands stands = atAPlace().standsAt(AT_A_HUNDRED,
                new BorderQuantity.Observation() {

                    @Override
                    public WalkResult<ObservationAtPoint> at(TermPath path) {
                        return WalkResult.reached(ObservationAtPoint.ANOTHER_READING);
                    }

                    @Override
                    public WalkResult<List<ObservedValue>> everyValueAt(TermPath path) {
                        throw new AssertionError("a number of one place is not read over a run");
                    }
                });

        assertEquals(BorderQuantity.Stands.couldNotTell(ReadingGap.NO_VALUE), stands,
                "the walk arrived and no value of the row stands there, which is not a value an"
                        + " observation could not keep");
    }

    /**
     * And a run the walk never reached says something else again, not that the row missed the line.
     *
     * <p>A caller that could not walk to the run hands over no run at all. Answering "this is no
     * number of that" would make a place this compiler could not reach into the model putting the
     * row somewhere else — {@code Stands.No}, and a point missed; answering that no value stands
     * there says the row wrote nothing at a position nothing ever looked at.
     */
    @Test
    void aRunTheWalkNeverReachedIsNotARunThatMissedTheLine() {
        BorderQuantity.Stands stands = form().standsAt(AT_A_HUNDRED,
                new BorderQuantity.Observation() {

                    @Override
                    public WalkResult<ObservationAtPoint> at(TermPath path) {
                        throw new AssertionError("a number over a run is not read from one value");
                    }

                    @Override
                    public WalkResult<List<ObservedValue>> everyValueAt(TermPath path) {
                        return WalkResult.couldNotWalk();
                    }
                });

        assertEquals(BorderQuantity.Stands.couldNotTell(ReadingGap.COULD_NOT_WALK), stands,
                "the walk did not reach the run, which says nothing about where the row stands");
    }

    /** The same quantity over one position's own value, for what is asked of a place. */
    private static BorderQuantity.OverAForm atAPlace() {
        NumericTerm.ValueOf here = new NumericTerm.ValueOf(UNDER);
        return new BorderQuantity.OverAForm("decide", LinearForm.atom((NumericTerm) here),
                Map.of(here, TermOrdersFixtures.itself(here, new Carrier.Whole())));
    }

    private static BorderQuantity.OverAForm form() {
        return new BorderQuantity.OverAForm("decide",
                LinearForm.atom((NumericTerm) TOTAL), Map.of(TOTAL, ON_THE_TOTAL));
    }

    /** A row whose only readable place is the run, holding these values. */
    private static BorderQuantity.Observation run(ObservedValue... values) {
        return new BorderQuantity.Observation() {

            @Override
            public WalkResult<ObservationAtPoint> at(TermPath path) {
                throw new AssertionError("a number over a run is not read from one value");
            }

            @Override
            public WalkResult<List<ObservedValue>> everyValueAt(TermPath path) {
                assertEquals(UNDER, path, "read from where the run says its values are");
                return WalkResult.reached(List.of(values));
            }
        };
    }
}
