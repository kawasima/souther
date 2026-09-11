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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What a reading was refused by is asked for where a refusal has been found, and nowhere else.
 *
 * <p>{@link Confinement#admission} is asked three things. Two of them are questions about the
 * reading met with what places its positions, and the walk asks each where it wants an answer. The
 * third is about the alternatives alone — what every one of them describes itself as refused by —
 * and one way out of the question reads it: the reading holds nothing, and what emptied it is the
 * values rather than the ends.
 *
 * <p><b>The population is the ways out and not the ones that came to mind.</b> A question asked
 * before any of them is a question asked on all of them, so what holds the asking to the one arm
 * that is owed an answer is every other arm refusing to be asked. Each way out below is pinned to
 * the verdict and the proof it carries, so that a case reaching some other way out is a case that
 * stopped testing what it names.
 *
 * <p>Asked of {@link Confinement#admission} directly, because what is under test is which arm
 * reaches for the question. The two questions beside it are the reading's own, so what walks here
 * is what walks in production and only the answer being counted is this test's.
 *
 * <p>A way out is a walk and not a pair of readings held side by side: where the positions stop is
 * made where it is asked for, since a value holding that beside what the positions admit is one its
 * reader could ask either of alone.
 *
 * <p>The name of the verdict is written out because this package declares one of its own.
 */
class TheAccountOfARefusalIsAskedForWhereThereIsOneToHoldTest {

    /**
     * Every way out of the question but the one that is owed an account.
     *
     * <p>What each of them must be is the verdict written beside it, so that a way out reached by
     * some other road is not read as the one it is named for.
     */
    @Test
    void noWayOutButOneAsksTheReadingWhatRefusedIt() {
        // Every one of them, and not the first that breaks. A question moved out of the arm that
        // is owed it is asked on several of these at once, and which ones is what says how far it
        // moved — so the asking is written down and read at the end rather than thrown.
        List<String> asked = new ArrayList<>();
        List<String> reached = new ArrayList<>();
        List<String> owed = new ArrayList<>();
        for (Exit exit : everyWayOutButOne()) {
            Confinement.Admission<FactSubject> said =
                    exit.way().asked(() -> asked.add(exit.name()));

            reached.add(exit.name() + ": " + said.by() + " " + said.how());
            owed.add(exit.name() + ": " + exit.by() + " " + exit.how());
        }

        assertEquals(List.of(), asked,
                "no way out but one is owed what the alternatives say refused them");
        assertEquals(owed, reached, "and each of them is the way out it is written down as");
    }

    /** And that a reading holding something is one of them, which the verdicts above do not say. */
    @Test
    void aReadingThatStandsIsOneOfThoseWaysOut() {
        Confinement.Admission<FactSubject> said = stands(() -> {
            throw new AssertionError("asked what refused a reading that stands");
        });

        assertFalse(said.emptiness().isEmpty(), "the reading holds a value");
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
        PlannedValues<FactSubject> values = holding(X, "A").meet(holding(X, "B"));
        int[] asked = {0};

        Confinement.Admission<FactSubject> said = admission(values, OrderedIntervals.top(),
                PositionEnvelope.Restrictions.nothingSpokenOf(), () -> asked[0]++);

        assertEquals(1, asked[0], "the arm that is owed the account asks for it once");
        assertEquals(values.refusedBy(), said.site(), "and reports what it was told");
        assertEquals(Confinement.EmptyBy.VALUES, said.by());
    }

    /** One asking of the question, told what to do where the reading is asked what refused it. */
    @FunctionalInterface
    private interface Way {
        Confinement.Admission<FactSubject> asked(Runnable whenAsked);
    }

    /** One way out, and what it must answer to be that way out. */
    private record Exit(String name, Way way, Confinement.EmptyBy by, Confinement.Shown how) {}

    private static List<Exit> everyWayOutButOne() {
        return List.of(
                new Exit("a reading that stands",
                        TheAccountOfARefusalIsAskedForWhereThereIsOneToHoldTest::stands,
                        Confinement.EmptyBy.NOTHING_SHOWN, Confinement.Shown.BY_THE_READINGS),
                new Exit("ends naming no value at all", whenAsked -> admission(holding(X, "A"),
                        atLeast(X, "B").meet(atMost(X, "A")),
                        PositionEnvelope.Restrictions.nothingSpokenOf(), whenAsked),
                        Confinement.EmptyBy.ORDER, Confinement.Shown.BY_THE_READINGS),
                // A position the alternatives never name, left nowhere once what is required of it
                // outside is met with its own ends. The reading stands, so this is reached past the
                // walk and not before it.
                new Exit("a position placed nowhere by what is outside",
                        whenAsked -> admission(holding(X, "A"), atMost(Y, "A"),
                                placing(Y, atLeastInterval("B")), whenAsked),
                        Confinement.EmptyBy.ORDER,
                        Confinement.Shown.ONCE_THE_POSITIONS_ARE_PLACED),
                // A block stated to differ from itself, which is a lack about several blocks
                // together and is nearer than what the alternatives say about themselves.
                new Exit("blocks refused together",
                        whenAsked -> admission(PlannedValues.<FactSubject>holdingAsOne(X, Y)
                                .meet(PlannedValues.heldApart(X, Y)), OrderedIntervals.top(),
                                PositionEnvelope.Restrictions.nothingSpokenOf(), whenAsked),
                        Confinement.EmptyBy.POSITIONS_HELD_APART,
                        Confinement.Shown.BY_THE_READINGS),
                new Exit("values and ends sharing no value",
                        whenAsked -> admission(holding(X, "A"), atLeast(X, "B"),
                                PositionEnvelope.Restrictions.nothingSpokenOf(), whenAsked),
                        Confinement.EmptyBy.SET_AND_RANGE, Confinement.Shown.BY_THE_READINGS));
    }

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject X = FactSubject.of(NAMES.written("x"));
    private static final FactSubject Y = FactSubject.of(NAMES.written("y"));

    /** A reading with something in it, wherever its positions are put. */
    private static Confinement.Admission<FactSubject> stands(Runnable whenAsked) {
        return admission(holding(X, "A"), OrderedIntervals.top(),
                PositionEnvelope.Restrictions.nothingSpokenOf(), whenAsked);
    }

    /** A reading leaving {@code position} the one value {@code only}. */
    private static PlannedValues<FactSubject> holding(FactSubject position, String only) {
        return PlannedValues.at(position, AdmittedPlan.of(ValueSet.just(Value.text(only))));
    }

    /** The strings from {@code least} up. */
    private static OrderedInterval atLeastInterval(String least) {
        return new OrderedInterval(Endpoint.inclusive(Text.of(least)), null);
    }

    private static OrderedIntervals<FactSubject> atLeast(FactSubject position, String least) {
        return OrderedIntervals.at(position, atLeastInterval(least));
    }

    /** And the strings up to {@code most}, so that the two of them together name none. */
    private static OrderedIntervals<FactSubject> atMost(FactSubject position, String most) {
        return OrderedIntervals.at(position,
                new OrderedInterval(null, Endpoint.inclusive(Text.of(most))));
    }

    /** What the readings outside this one prove about {@code position}. */
    private static PositionEnvelope.Restrictions<FactSubject> placing(FactSubject position,
                                                                      OrderedInterval within) {
        return new PositionEnvelope.Restrictions<>(
                Map.of(position, new PositionRestriction.Within(within)));
    }

    private static Confinement.Admission<FactSubject> admission(PlannedValues<FactSubject> values,
                                                                OrderedIntervals<FactSubject> ends,
                                                                PositionEnvelope.Restrictions<FactSubject> outside,
                                                                Runnable whenAsked) {
        return Confinement.admission(ends, Map.of(X, Carrier.TEXT, Y, Carrier.TEXT), outside,
                (asked, _) -> values.anyAlternativeAdmits(asked),
                (asked, _) -> values.refusedInEveryAlternativeAt(asked),
                () -> {
                    whenAsked.run();
                    return values.refusedBy();
                }, StringMachineAnswers.NONE);
    }
}
