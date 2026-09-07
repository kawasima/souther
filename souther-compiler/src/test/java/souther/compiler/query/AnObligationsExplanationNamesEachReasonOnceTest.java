package souther.compiler.query;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import souther.compiler.check.BehaviorContract;
import souther.compiler.check.Carrier;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.RuleRef;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermOrdersFixtures;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Towards;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.Target;
import souther.compiler.partition.Border;
import souther.compiler.partition.BorderQuantity;
import souther.compiler.partition.BoundaryTarget;
import souther.compiler.partition.Level;
import souther.compiler.partition.LineFacts;
import souther.compiler.partition.LineOrigin;
import souther.compiler.partition.ReadingGap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What an obligation nobody can decide says about itself, and what it leaves to the readings.
 *
 * <p>One line is read at every position that carries the type, each reading is measured on its own,
 * and what weakened the measurement is keyed on the border the reading was made at — one fact per
 * line a module could not read, which is what a module's account of itself counts. The explanation
 * of one point is the other question about the same readings, and the border is no part of its
 * answer: the readings are named under the point, one to a line, so a clause said once per reading
 * counts the paths a fact arrived by (spec §an-obligations-explanation-names-each-distinct-reason-once).
 *
 * <p>So the crossing from one to the other is what these hold to. The same reason met at any number
 * of readings is one reason; two readings stopped in two ways are two; and neither which border a
 * reading was made at nor the order the readings were walked in reaches the answer.
 */
class AnObligationsExplanationNamesEachReasonOnceTest {

    private static final Incompleteness.Code A_LIMIT = Incompleteness.Code.VALUE_TRUNCATED;
    private static final Incompleteness.Code NOTHING_COULD_READ_IT =
            Incompleteness.Code.VALUE_UNREADABLE;

    /**
     * One reason met at any number of readings is said once.
     *
     * <p>Adding a reading that meets a reason already met leaves the explanation where it was.
     * Measured at four widths, because an answer that repeats a clause per reading agrees with this
     * one at a width of one.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 7})
    void oneReasonMetAtAnyNumberOfReadingsIsSaidOnce(int readings) {
        List<Weakening> met = new ArrayList<>();
        for (int i = 0; i < readings; i++) {
            met.add(new Weakening.BorderValueUnreadable(border("t" + i), ReadingGap.of(A_LIMIT)));
        }

        assertEquals(ReadingReasons.of(List.of(ReadingGap.of(A_LIMIT))), explanationOf(met),
                () -> readings + " readings met one reason, which is one thing to tell an author");
    }

    /**
     * Which borders the readings were made at does not reach the explanation.
     *
     * <p>The design boundary, said as an equality. A border is where a reading was made, it is what
     * tells one fact of a module's account from another, and what an author is told about this
     * point is the same whichever lines carried it.
     */
    @Test
    void theBordersTheReadingsWereMadeAtDoNotReachTheExplanation() {
        ReadingReasons here = explanationOf(List.of(
                new Weakening.BorderValueUnreadable(border("first"), ReadingGap.of(A_LIMIT)),
                new Weakening.BorderValueUnreadable(border("second"), ReadingGap.of(A_LIMIT))));
        ReadingReasons elsewhere = explanationOf(List.of(
                new Weakening.BorderValueUnreadable(border("third"), ReadingGap.of(A_LIMIT)),
                new Weakening.BorderValueUnreadable(border("fourth"), ReadingGap.of(A_LIMIT))));

        assertEquals(here, elsewhere,
                "a border is where a reading was made, not what an author is told about the point");
    }

    /**
     * The order the readings were walked in does not reach the explanation.
     *
     * <p>Equal as values and not merely as sets: what a reader compares against the last run is a
     * line of text, so two runs that met the same reasons write the same sentence whichever order
     * the readings arrived in.
     */
    @Test
    void theOrderTheReadingsWereWalkedInDoesNotReachTheExplanation() {
        ReadingReasons oneWay = explanationOf(List.of(
                new Weakening.BorderValueUnreadable(border("a"), ReadingGap.of(A_LIMIT)),
                new Weakening.BorderValueUnreadable(border("b"), ReadingGap.NO_VALUE),
                new Weakening.BorderValueUnreadable(border("c"),
                        ReadingGap.of(NOTHING_COULD_READ_IT))));
        ReadingReasons theOther = explanationOf(List.of(
                new Weakening.BorderValueUnreadable(border("c"),
                        ReadingGap.of(NOTHING_COULD_READ_IT)),
                new Weakening.BorderValueUnreadable(border("b"), ReadingGap.NO_VALUE),
                new Weakening.BorderValueUnreadable(border("a"), ReadingGap.of(A_LIMIT))));

        assertEquals(oneWay, theOther,
                "the order the readings were found in is the walk's and not the point's");
        assertEquals(List.of(ReadingGap.of(NOTHING_COULD_READ_IT), ReadingGap.of(A_LIMIT),
                        ReadingGap.NO_VALUE),
                oneWay.eachKindOnce().written(),
                "and the order they are said in is written down rather than met");
    }

    /**
     * Two reasons that are not one reason stay two.
     *
     * <p>The other side of saying a reason once. A fold that answered one reason for every reading
     * that could not be read would pass everything above and tell an author that a value nothing
     * could decode and a value a limit shortened are the same news.
     */
    @Test
    void reasonsThatDifferAreKeptApart() {
        assertEquals(2, explanationOf(List.of(
                        new Weakening.BorderValueUnreadable(border("a"), ReadingGap.of(A_LIMIT)),
                        new Weakening.BorderValueUnreadable(border("b"), ReadingGap.NO_VALUE)))
                .eachKindOnce().size(),
                "a limit that shortened a value and a walk that reached none are two reasons");
        assertEquals(2, explanationOf(List.of(
                        new Weakening.BorderValueUnreadable(border("a"), ReadingGap.of(A_LIMIT)),
                        new Weakening.BorderValueUnreadable(border("b"),
                                ReadingGap.of(NOTHING_COULD_READ_IT))))
                .eachKindOnce().size(),
                "and two observations that stopped for different reasons are two");
    }

    /**
     * Reasons of both kinds at several readings each come to one of each.
     *
     * <p>The row that separates saying each reason once from counting the readings: five readings,
     * two reasons, and neither number is the other.
     */
    @Test
    void severalReadingsOfEachOfTwoReasonsComeToTwo() {
        List<Weakening> met = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            met.add(new Weakening.BorderValueUnreadable(border("a" + i), ReadingGap.of(A_LIMIT)));
        }
        for (int i = 0; i < 2; i++) {
            met.add(new Weakening.BorderValueUnreadable(border("b" + i),
                    ReadingGap.of(NOTHING_COULD_READ_IT)));
        }

        assertEquals(ReadingReasons.of(List.of(ReadingGap.of(NOTHING_COULD_READ_IT),
                        ReadingGap.of(A_LIMIT))),
                explanationOf(met),
                "five readings met two reasons, and what the point says is the two");
    }

    /**
     * A reason said where it happened is not said here.
     *
     * <p>A row nothing read at all leaves every line of the module short and is reported where the
     * row stopped. So the point is undecided and has no reason of its own to give, which is an
     * answer the projection reaches by classifying every way a measurement is weakened rather than
     * by matching the one kind it knows how to say.
     */
    @Test
    void aReasonSaidWhereItHappenedIsNotSaidAgainHere() {
        ReadingReasons met = explanationOf(List.of(Weakening.ObservationIncomplete.of(
                new Incompleteness(Incompleteness.Code.OBSERVATION_ABSENT,
                        new Target.OfModule("example"), Optional.empty()))));

        assertEquals(List.of(), met.eachKindOnce().written(),
                "a row that never ran bears on every line, and is said where the row stopped");
    }

    /**
     * The open questions are said in the same one order, whichever order a fold found them in.
     *
     * <p>The other plurality one obligation publishes. Held on the reasons and not on these, the
     * rule would be half a rule and the next order written would be somebody's own again.
     */
    @Test
    void theOpenQuestionsAreSaidInTheOrderTheyArePublishedIn() {
        ObligationDisposition.Uncertainty there =
                new ObligationDisposition.Uncertainty.WhetherARowIsThere.ReadingsStopped(
                        ReadingReasons.of(List.of(ReadingGap.NO_VALUE)));
        ObligationDisposition.Uncertainty written =
                new ObligationDisposition.Uncertainty.WhetherARowCanBeWritten.Stopped(prevented());

        assertEquals(List.of(there, written),
                ObligationDisposition.Undecided.about(List.of(written, there)).because().written(),
                "the question an author can act on is said first, whichever arrived first");
        assertEquals(List.of(there),
                ObligationDisposition.Undecided.about(List.of(there, there)).because().written(),
                "and one question is one entry, however many times it arrived");
        ObligationDisposition.Uncertainty alsoThere =
                new ObligationDisposition.Uncertainty.WhetherARowIsThere.ReadingsStopped(
                        ReadingReasons.of(List.of(ReadingGap.of(A_LIMIT))));
        assertThrows(IllegalArgumentException.class,
                () -> ObligationDisposition.Undecided.about(List.of(there, alsoThere)),
                "and one question has one answer: two of them are not something to put in order");
    }

    /** A showing this compiler was stopped from making, for the second of the two questions. */
    private static WritabilityKnowledge.Prevented prevented() {
        return WritabilityKnowledge.Prevented.by(
                EstablishmentGap.Observation.of(Set.of(A_LIMIT)));
    }

    /** What the point says about whether a row is at it, out of what its readings went without. */
    private static ReadingReasons explanationOf(List<Weakening> met) {
        ObligationDisposition disposition = ObligationDisposition.of(
                new ObligationCoverage.Undecided(WeakeningSet.ofAll(met)),
                new WritabilityKnowledge.NoEvidence());
        ObligationDisposition.Undecided undecided =
                assertInstanceOf(ObligationDisposition.Undecided.class, disposition,
                        "a point whose readings did not run out is one nobody can decide");
        return assertInstanceOf(
                ObligationDisposition.Uncertainty.WhetherARowIsThere.ReadingsStopped.class,
                undecided.because().written().getFirst(),
                "and the question the readings left open is whether a row is there").met();
    }

    /** A line on one term of one behavior, which differs from the next only in the term. */
    private static Border border(String term) {
        Carrier carrier = new Carrier.Whole();
        NumericTerm.ValueOf value = new NumericTerm.ValueOf(TermPath.of(term));
        BoundaryTarget target = BoundaryTarget.at(
                new BorderQuantity.OfACoordinate("cap", value,
                        TermOrdersFixtures.itself(value, carrier)),
                new Level.OnACarrier(carrier, Count.of(100)));
        LineOrigin origin = new LineOrigin.EnsuresOrigin(
                new souther.compiler.partition.WhichLine.OfAComparisonOfAPart(
                        new souther.compiler.partition.ClauseStatementId(
                                new souther.compiler.check.PartId<>(new RuleRef.Ensures(
                                        new BehaviorContract.RuleId(null, 0, 0, null), "cap"), 0),
                                0)),
                new LineFacts(new ComparisonClaim.Cut(Towards.BELOW, true)));
        return Border.at(target, origin, new NumericDomain.Bounds(null, null));
    }
}
