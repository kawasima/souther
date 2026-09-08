package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.values.AdmissibleValues;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Allowance;
import souther.compiler.values.AsACompilationAllows;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A state holding nothing, whichever of its domains is the one that holds nothing.
 *
 * <p>A clause reaches whatever domain has a word for it. Some clauses reach more than one — a bound
 * on an {@code Int} is an affine relation and a bound on an order, and each domain abstracts it in a
 * way that is safe on its own — and some reach exactly one: a predicate about a string reaches the
 * facts and says nothing to the numbers, and a date bounded against a written date reaches the
 * ordering and says nothing to either. So a contradiction can be held entirely by one of them while
 * every other is untouched, and a reader asking a single domain whether a value exists answers yes
 * whenever it asked the domain that had never heard of the rules.
 *
 * <p>Each domain is put at its own bottom on its own here, because that is the shape of the mistake:
 * not that the answer was wrong about a state where everything contradicts, but that it was wrong
 * about a state where one thing does.
 *
 * <p>And a state holds nothing for one reason more than a domain finding a contradiction: a caller
 * may have shown it by an argument none of the domains makes
 * ({@link ConstraintState#shownToHoldNothing}). That is put at its own bottom on its own here too,
 * for the same reason as the rest.
 */
class WhetherAValueExistsIsAskedOfEveryDomainTest {

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject A_PREDICATE = FactSubject.of(NAMES.written("some predicate"));
    private static final FactSubject A_POSITION = FactSubject.of(NAMES.written("some position"));

    /** Nothing taken in leaves every value there was. */
    @Test
    void aStateNothingWasTakenIntoHoldsWhateverItHeld() {
        assertFalse(ConstraintState.<FactSubject>top().isBottom());
    }

    /** Numbers that cannot both hold, which is the one the reading always asked. */
    @Test
    void numbersThatCannotBothHoldLeaveNothing() {
        assertTrue(numbersAtBottom().isBottom());
        assertFalse(numbersAtBottom().facts().isBottom(), "and the other domain is untouched");
    }

    /** A predicate settled both ways, which reaches no number at all. */
    @Test
    void aPredicateSettledBothWaysLeavesNothing() {
        assertTrue(factsAtBottom().isBottom());
        assertFalse(factsAtBottom().numbers().isBottom(), "and the other domain is untouched");
    }

    /** A position left no value it may hold, which no number and no predicate has a word for. */
    @Test
    void aPositionWithNoValueLeftLeavesNothing() {
        assertTrue(valuesAtBottom().isBottom());
        assertFalse(valuesAtBottom().numbers().isBottom(), "and the other domains are untouched");
        assertFalse(valuesAtBottom().facts().isBottom());
    }

    /**
     * A position whose ends cross, which is a contradiction over an order the numbers do not hold.
     *
     * <p>The interval algebra carries one number per position and only for the positions a model
     * adds and subtracts, so a date bounded above a date it is bounded below of reaches nothing
     * there; the values are a finite set and an ordering names no finite set. Neither had heard of
     * the rule.
     */
    @Test
    void aPositionWhoseEndsCrossLeavesNothing() {
        assertTrue(orderedAtBottom().isBottom());
        assertFalse(orderedAtBottom().numbers().isBottom(), "and the other domains are untouched");
        assertFalse(orderedAtBottom().facts().isBottom());
    }

    /**
     * A set of values and a range on the same position with no value in both.
     *
     * <p>The case neither of the two above is. The set admits something and the range holds
     * something, so each of them, asked alone, finds nothing wrong — and what is left is nothing.
     * That is why they are one component here and not two arms: an arm apiece is what read this
     * state as one somebody can be in.
     */
    @Test
    void aSetAndARangeThatShareNoValueLeaveNothing() {
        assertTrue(setBesideARangeItIsOutside().isBottom());
        assertFalse(setBesideARangeItIsOutside().numbers().isBottom(),
                "and neither half of it holds nothing on its own");
        assertFalse(setBesideARangeItIsOutside().facts().isBottom());
        assertFalse(setBesideARangeItIsOutside().confinement().holdingNothing()
                .contains(A_POSITION));
        assertEquals(ValueSet.just(Value.text("A")),
                setBesideARangeItIsOutside().values().at(A_POSITION));
    }

    /** A caller having shown it, by an argument no domain holds: the cases an operation is defined
     * in leave the condition unsatisfiable, and nothing an interval or a predicate carries says so. */
    @Test
    void aStateShownToHoldNothingHoldsNothing() {
        assertTrue(shownAtBottom().isBottom());
        assertFalse(shownAtBottom().numbers().isBottom(), "and every domain is untouched");
        assertFalse(shownAtBottom().facts().isBottom());
        assertFalse(shownAtBottom().confinement().holdsNothing());
    }

    private static ConstraintState<FactSubject> numbersAtBottom() {
        // `1 <= 0`, which is how a reading already says that what it stands in is never reached.
        return ConstraintState.<FactSubject>top()
                .taking(LinearForm.constant(BigDecimal.ONE), Rel.LE, Map.of());
    }

    private static ConstraintState<FactSubject> factsAtBottom() {
        return ConstraintState.<FactSubject>top().taking(A_PREDICATE, true).taking(A_PREDICATE, false);
    }

    private static ConstraintState<FactSubject> valuesAtBottom() {
        // Met as one reading and handed over as one. Two readings are combined where the
        // clauses of a declaration are read, and never at the state's boundary.
        Allowance<FactSubject> sets = AsACompilationAllows.forAdmittedValues();
        return ConstraintState.<FactSubject>top().takingRead(Confinement.Worked.of(
                says("A", sets).meet(says("B", sets), sets),
                OrderedIntervals.top(), Map.of()), sets);
    }

    /** One rule about the position, worked out, which is how a reading is come by. */
    private static AdmissibleValues<FactSubject> says(String text, Allowance<FactSubject> sets) {
        return PlannedValues.at(A_POSITION, AdmittedPlan.of(ValueSet.just(Value.text(text))))
                .resolve(sets).values();
    }

    private static ConstraintState<FactSubject> orderedAtBottom() {
        return ConstraintState.<FactSubject>top()
                .taking(OrderedIntervals.at(A_POSITION,
                        new OrderedInterval(Endpoint.inclusive(Count.of(6)), null)), Map.of())
                .taking(OrderedIntervals.at(A_POSITION,
                        new OrderedInterval(null, Endpoint.inclusive(Count.of(2)))), Map.of());
    }

    /** The values pinned at one string, and the order left the strings above another. */
    private static ConstraintState<FactSubject> setBesideARangeItIsOutside() {
        Allowance<FactSubject> sets = AsACompilationAllows.forAdmittedValues();
        return ConstraintState.<FactSubject>top().takingRead(Confinement.Worked.of(
                says("A", sets),
                OrderedIntervals.at(A_POSITION, new OrderedInterval(
                        Endpoint.inclusive(souther.compiler.numeric.Text.of("B")), null)),
                Map.of(A_POSITION, Carrier.TEXT)), sets);
    }

    private static ConstraintState<FactSubject> shownAtBottom() {
        return ConstraintState.<FactSubject>top().shownToHoldNothing();
    }

    /**
     * A tripwire and not the proof above it.
     *
     * <p>What the tests above establish is that {@link ConstraintState#isBottom} reads each part of
     * this state that can make it hold today. They cannot establish it of a part added tomorrow, and
     * the failure they would leave is the silent one: the new part reaches its own bottom, nothing
     * asks it, and every type whose rules only that part can read is built — and every path only it
     * rules out is walked. So the count is pinned here. A component added to the state fails this
     * until it is added to the table above as well.
     *
     * <p>Every component and not every domain. What makes the state hold nothing is not only a
     * domain finding a contradiction in what it was given: a caller may have shown it by an argument
     * none of them holds ({@link ConstraintState#shownToHoldNothing}), and that is a component here
     * on the same footing. Counting domains would have let it in unread.
     */
    @Test
    void everyComponentThatCanMakeTheStateHoldNothingIsOneThisTestPutAtItsOwnBottom() {
        RecordComponent[] components = ConstraintState.class.getRecordComponents();
        assertEquals(4, components.length,
                "each component of the state needs a case above putting that one, and only that "
                        + "one, at its bottom");
    }

    /** And the interval algebra's own answer is left as it was: a state is not bottom because some
     * domain in it has nothing to say. */
    @Test
    void aDomainWithNothingToSayIsNotADomainThatHoldsNothing() {
        assertFalse(NumericDomain.<FactSubject>top().isBottom());
    }
}
