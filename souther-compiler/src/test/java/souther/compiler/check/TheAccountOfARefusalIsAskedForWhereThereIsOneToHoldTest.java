package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.numeric.Text;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.StringMachineAnswers;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a reading was refused by is asked for where a refusal has been found, and nowhere else.
 *
 * <p>{@link Confinement#admission} is asked three things. Two of them are questions about the
 * reading met with what places its positions, and the walk asks each where it wants an answer. The
 * third is about the alternatives alone — what every one of them describes itself as refused by —
 * and exactly one way out of the question reads it: the reading holds nothing, and what emptied it
 * is the values rather than the ends.
 *
 * <p>So it is a question too, and this is what says so. A reading that stands, one the ends leave
 * nothing, and one whose values and ranges share no value are three answers that owe an author
 * nothing about the alternatives, and the third question is not asked on any of them. Handed an
 * answer instead, what it costs to ask whether a reading stands would follow how many rules the
 * reading holds, on every one of these paths.
 *
 * <p>Asked of {@link Confinement#admission} directly, because what is under test is which arm
 * reaches for the question. The two questions beside it are the reading's own, so what walks here
 * is what walks in production and only the answer being counted is this test's.
 *
 * <p>The name of the verdict is written out because this package declares one of its own.
 */
class TheAccountOfARefusalIsAskedForWhereThereIsOneToHoldTest {

    /** A reading with something in it, which is the answer most askings reach. */
    @Test
    void aReadingThatStandsIsNeverAskedWhatRefusedIt() {
        Confinement.Admission<FactSubject> said = askedNever(
                PlannedValues.at(X, AdmittedPlan.of(ValueSet.just(Value.text("A")))),
                OrderedIntervals.top());

        assertFalse(said.emptiness().isEmpty(), "the reading holds a value");
    }

    /** And a reading the ends leave nothing, which the ends answer for on their own. */
    @Test
    void aReadingTheEndsEmptyIsNeverAskedWhatRefusedIt() {
        Confinement.Admission<FactSubject> said = askedNever(
                PlannedValues.at(X, AdmittedPlan.of(ValueSet.just(Value.text("A")))),
                atLeast("B").meet(atMost("A")));

        assertEquals(Confinement.EmptyBy.ORDER, said.by());
    }

    /**
     * And a reading whose values and whose ends each hold something and share nothing.
     *
     * <p>The one that would be paid for on every declaration an author gets wrong this way. What
     * refused it is where the set and the range were asked together, which the walk has in hand,
     * and what the alternatives say about themselves is a sentence nobody here is owed.
     */
    @Test
    void aReadingEmptiedByItsValuesAndItsEndsTogetherIsNeverAskedWhatRefusedIt() {
        Confinement.Admission<FactSubject> said = askedNever(
                PlannedValues.at(X, AdmittedPlan.of(ValueSet.just(Value.text("A")))),
                atLeast("B"));

        assertEquals(Confinement.EmptyBy.SET_AND_RANGE, said.by());
        assertTrue(said.emptiness().isEmpty());
    }

    /**
     * And the one arm that is owed it, which asks once and reports what it was told.
     *
     * <p>Once, because the answer is worked out over every position every alternative describes:
     * asked a second time it would be worked out a second time, and the arm has no more to learn
     * from it than which of the two nearer sentences this is.
     */
    @Test
    void aReadingItsValuesEmptyIsAskedWhatRefusedItExactlyOnce() {
        PlannedValues<FactSubject> values =
                PlannedValues.<FactSubject>at(X, AdmittedPlan.of(ValueSet.just(Value.text("A"))))
                        .meet(PlannedValues.at(X, AdmittedPlan.of(ValueSet.just(Value.text("B")))));
        int[] asked = {0};

        Confinement.Admission<FactSubject> said = admission(values, OrderedIntervals.top(), () -> {
            asked[0]++;
            return values.refusedBy();
        });

        assertEquals(1, asked[0], "the arm that is owed the account asks for it once");
        assertEquals(values.refusedBy(), said.site(), "and reports what it was told");
        assertEquals(Confinement.EmptyBy.VALUES, said.by());
    }

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject X = FactSubject.of(NAMES.written("x"));

    /** The strings from {@code least} up. */
    private static OrderedIntervals<FactSubject> atLeast(String least) {
        return OrderedIntervals.at(X,
                new OrderedInterval(Endpoint.inclusive(Text.of(least)), null));
    }

    /** And the strings up to {@code most}, so that the two of them together name none. */
    private static OrderedIntervals<FactSubject> atMost(String most) {
        return OrderedIntervals.at(X,
                new OrderedInterval(null, Endpoint.inclusive(Text.of(most))));
    }

    /** The same question, asked where being asked what refused the reading is the failure. */
    private static Confinement.Admission<FactSubject> askedNever(PlannedValues<FactSubject> values,
                                                                 OrderedIntervals<FactSubject> ends) {
        return admission(values, ends, () -> {
            throw new AssertionError("this answer was reached without a refusal to write down");
        });
    }

    private static Confinement.Admission<FactSubject> admission(PlannedValues<FactSubject> values,
                                                                OrderedIntervals<FactSubject> ends,
                                                                Confinement.AskedOfTheReadingAlone<FactSubject> refusedBy) {
        return Confinement.admission(ends, Map.of(X, Carrier.TEXT),
                PositionEnvelope.Restrictions.nothingSpokenOf(),
                (asked, _) -> values.anyAlternativeAdmits(asked),
                (asked, _) -> values.refusedInEveryAlternativeAt(asked),
                refusedBy, StringMachineAnswers.NONE);
    }
}
