package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.UnreadReason;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which of a choice's alternatives its width rests on is read off what they leave.
 *
 * <p>A choice admits whatever either of its alternatives admits, so what it leaves without one of
 * them is contained in what it leaves with both: differing there is being narrower, and a position
 * the two of them leave alike is one neither alternative is answerable for the width of. That is
 * the whole rule, and it is the same rule whether one alternative was read whole, both were, or
 * neither — which is what lets the account ask a single question of it.
 *
 * <p>Asked of the descriptions and answered by their normal forms. Nothing is built to answer it,
 * so a choice costs no more for being asked; two descriptions of one set that were written
 * differently come out apart, and the position is kept as one the width may rest on.
 */
class WhatAChoicesWidthRestsOnIsReadOffItsAlternativesTest {

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject VALUE = FactSubject.of(NAMES.written("value"));
    private static final FactSubject OTHER = FactSubject.of(NAMES.written("other"));
    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    /** {@code value == A}. */
    private static PlannedValues<FactSubject> isA() {
        return PlannedValues.at(VALUE, AdmittedPlan.of(ValueSet.just(A)));
    }

    /** {@code value /= A}. */
    private static PlannedValues<FactSubject> notA() {
        return PlannedValues.at(VALUE, AdmittedPlan.of(ValueSet.allBut(A)));
    }

    /** {@code other == A}. */
    private static PlannedValues<FactSubject> otherIsA() {
        return PlannedValues.at(OTHER, AdmittedPlan.of(ValueSet.just(A)));
    }

    /** A rule this reading has no word for, naming the positions it is about. */
    private static PlannedValues<FactSubject> unread(Set<FactSubject> named) {
        return PlannedValues.unreadable(named, UnreadReason.FORM_NOT_READ);
    }

    /** A choice this reading showed it is as wide as it is without either alternative. */
    private static final Settlement.Width<ReadingLanguage.Values> NEITHER = Settlement.Width.none();

    /** What a choice between two branches somebody can be in is as wide as it is because of. */
    private static Settlement.Width<ReadingLanguage.Values> between(PlannedValues<FactSubject> one,
                                                                   PlannedValues<FactSubject> other) {
        return Settlement.Width.ofValues(one, other);
    }

    /** A position ordered on a decimal, whose order stops nowhere. */
    private static final Map<FactSubject, Carrier> ON_A_DECIMAL = Map.of(VALUE, Carrier.DENSE);

    /** And one ordered on a whole number, whose order stops at both ends. */
    private static final Map<FactSubject, Carrier> ON_A_WHOLE_NUMBER = Map.of(VALUE, Carrier.WHOLE);

    /** One branch, with nothing said about where its orders stop. */
    private static Confinement.Planned<FactSubject> branch(PlannedValues<FactSubject> values) {
        return new Confinement.Planned<>(values, OrderedIntervals.top(), Map.of());
    }

    /**
     * An alternative saying nothing about a position the other narrowed is why the choice says
     * nothing there.
     *
     * <p>{@code value == A || f(other)}: the choice admits every value at {@code value} and would
     * admit one without the right alternative, so the width there is the right's.
     */
    @Test
    void anAlternativeThatSaysNothingIsWhyTheChoiceSaysNothing() {
        Settlement.Width<ReadingLanguage.Values> width = between(isA(), unread(Set.of(OTHER)));

        assertEquals(Set.of(), width.mayRestOnLeft(),
                "without the left the choice still admits every value at value");
        assertEquals(Set.of(VALUE), width.mayRestOnRight(),
                "without the right it admits one");
    }

    /**
     * Two alternatives narrowing a position the same way leave the choice's width resting on
     * neither.
     *
     * <p>{@code (value == A && f(other)) || (value == A && f(other))} holds {@code value} exactly
     * where {@code value == A} does, with or without either branch. Read off what each branch took
     * in rather than what it leaves, both would come out answerable for it.
     */
    @Test
    void twoAlternativesNarrowingAPositionAlikeLeaveTheWidthOnNeither() {
        Settlement.Width<ReadingLanguage.Values> width = between(isA().meet(unread(Set.of(OTHER))),
                isA().meet(unread(Set.of(OTHER))));

        assertEquals(NEITHER, width,
                "either branch dropped leaves the other saying the same thing at every position");
    }

    /** And the same where only one of them holds a clause nothing read. */
    @Test
    void andTheSameWhereOnlyOneOfThemHoldsAClauseNothingRead() {
        assertEquals(NEITHER,
                between(isA().meet(unread(Set.of(OTHER))), isA()),
                "the redundant branch is not why the choice admits what it admits");
    }

    /**
     * Alternatives that cover a position between them leave the width on neither.
     *
     * <p>{@code value == A || value /= A} admits every value at {@code value}, and so does either
     * of them beside a third that says nothing — which is why a further unread alternative cannot
     * be why the choice is that wide.
     */
    @Test
    void alternativesCoveringAPositionBetweenThemLeaveTheWidthOnNeither() {
        Settlement.Width<ReadingLanguage.Values> width =
                between(isA().joinLive(notA()), unread(Set.of(OTHER)));

        assertEquals(NEITHER, width,
                "the covered position is every value without either alternative");
    }

    /**
     * Alternatives narrowing one position to sets neither contains leave the width on both.
     *
     * <p>Neither {@code value == A} nor {@code value == B} is what the choice between them leaves,
     * so dropping either narrows it and each is answerable for the width.
     */
    @Test
    void alternativesNeitherOfWhichCoversTheOtherLeaveTheWidthOnBoth() {
        Settlement.Width<ReadingLanguage.Values> width = between(isA(),
                PlannedValues.at(VALUE, AdmittedPlan.of(ValueSet.just(B))));

        assertEquals(Set.of(VALUE), width.mayRestOnLeft());
        assertEquals(Set.of(VALUE), width.mayRestOnRight());
    }

    /**
     * The width is asked at each position on its own, and a branch answerable for one is not
     * answerable for the next.
     *
     * <p>Two alternatives that narrow one position apart and another alike: dropping either leaves
     * the first wider than it was and the second exactly where it was. Answered for the branch
     * rather than for each of its positions, a branch that is why a choice is wide anywhere would
     * be why it is wide everywhere it spoke.
     */
    @Test
    void aBranchAnswerableForOnePositionIsNotAnswerableForTheNext() {
        Settlement.Width<ReadingLanguage.Values> width = between(isA().meet(otherIsA()),
                notA().meet(otherIsA()));

        assertEquals(Set.of(VALUE), width.mayRestOnLeft(),
                "other is A under either alternative, so neither is why the choice leaves it so");
        assertEquals(Set.of(VALUE), width.mayRestOnRight());
    }

    /**
     * An occurrence one branch of which admits nothing leaves the width resting on neither, and the
     * next occurrence of the same written choice still says what it says.
     *
     * <p>Distribution puts the same written choice wherever a conjunction beside it stands, and a
     * branch impossible under one neighbour can be the branch somebody is in under another. What
     * this occurrence leaves is the branch beside the dead one, so nothing here rests on an
     * alternative — and read as an answer about the written choice, it would take back what the
     * occurrence beside it found.
     *
     * <p>Which of the two is the dead one is the author's, so both ways round are asked. Written one
     * way only, this would hold of a reading that took a choice with a dead second alternative for a
     * choice: the branch beside the dead one is the same branch either way, and nothing else here
     * tells the two apart.
     */
    @Test
    void anOccurrenceOneBranchOfWhichAdmitsNothingRestsOnNeitherAndDoesNotSpeakForTheRest() {
        Settlement.WidthDependency dead = Settlement.WidthDependency.of(
                souther.compiler.values.Emptiness.EMPTY, branch(isA()),
                souther.compiler.values.Emptiness.UNDECIDED, branch(unread(Set.of(OTHER))));
        Settlement.WidthDependency deadOnTheRight = Settlement.WidthDependency.of(
                souther.compiler.values.Emptiness.UNDECIDED, branch(isA()),
                souther.compiler.values.Emptiness.EMPTY, branch(unread(Set.of(OTHER))));
        Settlement.WidthDependency live = Settlement.WidthDependency.of(
                souther.compiler.values.Emptiness.UNDECIDED, branch(isA()),
                souther.compiler.values.Emptiness.UNDECIDED, branch(unread(Set.of(OTHER))));

        assertEquals(Settlement.WidthDependency.none(), dead,
                "the choice is the branch beside the dead one, and no alternative widened it");
        assertEquals(Settlement.WidthDependency.none(), deadOnTheRight,
                "and the same where the dead one is the alternative written second");
        assertEquals(live, dead.alsoSeen(live),
                "and what the occurrence beside it found is what the written choice is left with");
    }

    /**
     * The reading of order is asked the same question about the same two branches, and answers it
     * about where the positions stop.
     *
     * <p>{@code value >= 5 || f(other)}: what the choice leaves {@code value} is every number, and
     * without the right alternative it is the numbers from five — so the right is why the order
     * stops nowhere, which is a fact about the order and one the values have no word for. Asked of
     * the values instead, the answer is about a position they were told nothing about.
     */
    @Test
    void theOrderIsAskedTheSameQuestionAboutWhereItsPositionsStop() {
        Settlement.Width<ReadingLanguage.Order> width = Settlement.Width.ofOrder(
                OrderedIntervals.at(VALUE, from(5)), OrderedIntervals.top(), ON_A_DECIMAL);

        assertEquals(Set.of(), width.mayRestOnLeft(),
                "without the left the choice stops the position nowhere, as it does with it");
        assertEquals(Set.of(VALUE), width.mayRestOnRight(),
                "without the right it stops at five");
    }

    /** And two branches stopping a position in the same place leave the width on neither. */
    @Test
    void twoAlternativesStoppingAPositionAlikeLeaveTheWidthOnNeither() {
        assertEquals(Settlement.Width.<ReadingLanguage.Order>none(),
                Settlement.Width.ofOrder(OrderedIntervals.at(VALUE, from(5)),
                        OrderedIntervals.at(VALUE, from(5)), ON_A_DECIMAL),
                "the two leave the position the same values, so dropping either changes nothing");
    }

    /**
     * And alike is the same values and not the same spelling.
     *
     * <p>{@code 5} and {@code 5.00} are one place on the order and two numbers as they are written
     * down, which is what {@link souther.compiler.numeric.Place#key} exists to say. Compared as
     * written, two alternatives stopping a position at one place are two answers and the choice is
     * as wide as it is because of each of them — a border said to rest on a branch that states
     * exactly what its neighbour states.
     */
    @Test
    void andAlikeIsTheSameValuesAndNotTheSameSpelling() {
        assertEquals(Settlement.Width.<ReadingLanguage.Order>none(),
                Settlement.Width.ofOrder(OrderedIntervals.at(VALUE, from(5)),
                        OrderedIntervals.at(VALUE, fromSpelled("5.00")), ON_A_DECIMAL),
                "one place written two ways is one place");
    }

    /**
     * And a choice whose alternatives cover the order leaves the position where nothing said
     * anything would.
     *
     * <p>{@code n >= 2 || n <= 0} on a whole number. What the branches leave between them is every
     * {@code Int}, written as that carrier's own two ends; what a branch saying nothing about the
     * position leaves is every {@code Int} with no end written at all. Told apart, the choice above
     * such a branch is as wide as it is because of the branch beside it, and the border comes back
     * as one this compiler could not measure.
     */
    @Test
    void andACoveredOrderIsWhatSayingNothingLeaves() {
        Settlement.Width<ReadingLanguage.Order> width = Settlement.Width.ofOrder(
                OrderedIntervals.top(),
                OrderedIntervals.at(VALUE, Carrier.WHOLE.extent()), ON_A_WHOLE_NUMBER);

        assertEquals(Settlement.Width.<ReadingLanguage.Order>none(), width,
                "each alternative holds every value of the order, so the choice does with or"
                        + " without either");
    }

    /** {@code value >= low}, written as {@code spelling}. */
    private static OrderedInterval fromSpelled(String spelling) {
        return new OrderedInterval(
                Endpoint.inclusive(Count.of(new java.math.BigDecimal(spelling))), null);
    }

    /** {@code value >= low}. */
    private static OrderedInterval from(int low) {
        return new OrderedInterval(Endpoint.inclusive(Count.of(low)), null);
    }

    /**
     * One more occurrence of the same written choice adds what its width rests on and nothing else.
     *
     * <p>The same choice stands wherever a conjunction beside it was distributed in, and a branch
     * dropped is dropped at every one of them — so the sides are joined by union, which cannot
     * depend on the order the copies were met or on a copy being met twice.
     */
    @Test
    void whatTheOccurrencesLeaveIsJoinedWithoutRegardToOrderOrRepetition() {
        Settlement.Width<ReadingLanguage.Values> here =
                new Settlement.Width<>(Set.of(VALUE), Set.of());
        Settlement.Width<ReadingLanguage.Values> there =
                new Settlement.Width<>(Set.of(), Set.of(OTHER));

        assertEquals(here.alsoSeen(there), there.alsoSeen(here),
                "the order the copies were met in is not in the answer");
        assertEquals(here.alsoSeen(there), here.alsoSeen(there).alsoSeen(here),
                "and neither is a copy met twice");
        assertEquals(new Settlement.Width<ReadingLanguage.Values>(Set.of(VALUE), Set.of(OTHER)),
                here.alsoSeen(there),
                "a position any occurrence's width rests on is one the whole answer's does");
    }
}
