package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.values.PlannedValues;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a choice's alternatives leave the positions, over every place distribution put the choice.
 *
 * <p>The same written choice stands wherever a conjunction beside it was distributed in, and the
 * copies do not agree: a conjunct met with one copy can hold a position down where the copy beside
 * it leaves all of it. Every reader here acts on a negative — that a branch leaves the position at
 * every value, or that the choice stops it — so what is published has to be what any copy found,
 * and a reader takes the absence of it.
 *
 * <p><b>Which the readers do not distinguish today, and that is why the rule is pinned here.</b>
 * The state is reached: compiling this repository's own corpus meets copies that differ over what
 * the choice leaves whole. What no model reaches yet is a reader whose answer turns on it, so
 * keeping either copy alone passes every other test there is — and the direction is not a
 * preference, since keeping the copies apart is what publishes a line nobody draws.
 */
class WhatOneCopyOfAChoiceLeavesIsNotWhatTheChoiceLeavesTest {

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject VALUE = FactSubject.of(NAMES.written("value"));
    private static final FactSubject OTHER = FactSubject.of(NAMES.written("other"));

    private static final Map<FactSubject, Carrier> ON_WHOLE_NUMBERS =
            Map.of(VALUE, Carrier.WHOLE, OTHER, Carrier.WHOLE);

    /**
     * A position one copy leaves whole is one the choice may leave whole.
     *
     * <p>Read as what every copy left whole, a position one copy stops would be published as one
     * the choice stops everywhere — and the finding about a rule that draws no line there would be
     * suppressed by a copy that is not the one being asked about.
     */
    @Test
    void aPositionOneCopyLeavesWholeIsOneTheChoiceMayLeaveWhole() {
        WhatTheAlternativesLeave stopping = new WhatTheAlternativesLeave(
                Set.of(VALUE), Set.of(VALUE), Set.of());
        WhatTheAlternativesLeave leavingItWhole = new WhatTheAlternativesLeave(
                Set.of(VALUE), Set.of(VALUE), Set.of(VALUE));

        assertTrue(stopping.stops(VALUE), "the copy on its own stops it");
        assertFalse(stopping.alsoSeen(leavingItWhole).stops(VALUE),
                "and the copy beside it leaves all of it, so the choice is not one that stops it");
        assertFalse(leavingItWhole.alsoSeen(stopping).stops(VALUE),
                "either way round");
    }

    /**
     * And a position one copy leaves alone is one the branch may leave alone.
     *
     * <p>The same rule for the other two sets, and the reader of them takes the same negative: an
     * end left open beside this branch is struck off only where the branch leaves the position at
     * every value wherever it stands.
     */
    @Test
    void aPositionOneCopyHoldsDownIsOneTheBranchMayHoldDown() {
        WhatTheAlternativesLeave holding = new WhatTheAlternativesLeave(
                Set.of(VALUE), Set.of(OTHER), Set.of());
        WhatTheAlternativesLeave leavingThemAlone = WhatTheAlternativesLeave.nothing();

        assertTrue(leavingThemAlone.leavesEveryValueOnLeft(VALUE), "this copy holds nothing down");
        assertFalse(holding.alsoSeen(leavingThemAlone).leavesEveryValueOnLeft(VALUE),
                "and the copy beside it holds the position down on the left");
        assertFalse(leavingThemAlone.alsoSeen(holding).leavesEveryValueOnRight(OTHER),
                "and on the right, either way round");
    }

    /** And a copy met twice says what it said once, so the answer cannot count the copies. */
    @Test
    void aCopyMetTwiceSaysWhatItSaidOnce() {
        WhatTheAlternativesLeave one = new WhatTheAlternativesLeave(
                Set.of(VALUE), Set.of(OTHER), Set.of(VALUE));

        assertEquals(one, one.alsoSeen(one));
    }

    /**
     * What a choice leaves whole is read off both alternatives and never off one.
     *
     * <p>{@code value >= 2 || value <= 0} on a whole number. Each alternative stops the position and
     * what the two leave between them is every value it had, so the answer about the pair is not any
     * answer about a side: read a side at a time, this comes back as a choice that stops the
     * position, which is true of each alternative and false of the choice they are alternatives of.
     */
    @Test
    void whatAChoiceLeavesWholeIsNotWhatEitherAlternativeLeaves() {
        WhatTheAlternativesLeave leaves = WhatTheAlternativesLeave.of(
                branch(OrderedIntervals.at(VALUE, atLeast(2))),
                branch(OrderedIntervals.at(VALUE, atMost(0))));

        assertFalse(leaves.leavesEveryValueOnLeft(VALUE), "the left stops it at two");
        assertFalse(leaves.leavesEveryValueOnRight(VALUE), "and the right at nothing");
        assertFalse(leaves.stops(VALUE),
                "and between them they leave every value the order has");
    }

    /** And two bounds reaching the same way leave the choice stopping the position. */
    @Test
    void andTwoBoundsReachingTheSameWayLeaveTheChoiceStoppingIt() {
        WhatTheAlternativesLeave leaves = WhatTheAlternativesLeave.of(
                branch(OrderedIntervals.at(VALUE, atLeast(2))),
                branch(OrderedIntervals.at(VALUE, atLeast(0))));

        assertTrue(leaves.stops(VALUE), "nothing below zero is left, so the choice stops it");
    }

    /**
     * A copy of the choice that is not one leaves nothing to be taken back.
     *
     * <p>An occurrence one alternative of which nobody can be in is not a choice there: what is
     * left of it is the branch beside the dead one, and a rule written inside a branch nobody can
     * be in constrains nobody. So what its ranges say is no part of what the written choice leaves.
     *
     * <p>Which only shows in the aggregate, and that is why it matters. A branch is dead for the
     * author only where nobody can be in it anywhere, so a branch dead at one copy and live at
     * another is live — and the copy that is not a choice is met with the copies that are. Read off
     * the ranges whatever the fate, the dead copy says the left alternative holds {@code value}
     * down, and the copy where it is a choice and leaves all of it is overruled by a branch nobody
     * is in.
     */
    @Test
    void aCopyThatIsNotAChoiceLeavesNothingForTheOthersToTakeBack() {
        Settlement.OfAChoice notAChoice = Settlement.OfAChoice.of(
                dead(), said(OrderedIntervals.at(VALUE, atLeast(2))),
                live(), said(OrderedIntervals.top()));
        Settlement.OfAChoice aChoice = Settlement.OfAChoice.of(
                live(), said(OrderedIntervals.top()),
                live(), said(OrderedIntervals.top()));

        assertTrue(notAChoice.narrowed().leavesEveryValueOnLeft(VALUE),
                "there is no choice at that copy, so its left branch holds nothing down here");
        assertTrue(notAChoice.alsoSeen(aChoice).narrowed().leavesEveryValueOnLeft(VALUE),
                "and the copy that is a choice keeps what it showed");
        assertTrue(aChoice.alsoSeen(notAChoice).narrowed().leavesEveryValueOnLeft(VALUE),
                "either way round");
    }

    /** And the same of what the choice was shown to stop. */
    @Test
    void andACopyThatIsNotAChoiceStopsNothingEither() {
        Settlement.OfAChoice notAChoice = Settlement.OfAChoice.of(
                dead(), said(OrderedIntervals.at(VALUE, atLeast(2))),
                live(), said(OrderedIntervals.at(VALUE, atLeast(2))));

        assertFalse(notAChoice.narrowed().stops(VALUE),
                "nothing at that copy is a choice, so nothing there stops the position");
    }

    /**
     * And a choice nobody read anything about stops nothing.
     *
     * <p>The positions this can be asked about are the positions an alternative held down, since a
     * choice stops nothing its alternatives did not. Answered off what the choice was shown to
     * leave whole alone, an absence stands for two things at once — a position shown stopped, and a
     * position nobody put the question about — and the second is every position of every
     * declaration the choice says nothing about.
     */
    @Test
    void aChoiceNothingWasReadAboutStopsNothing() {
        assertFalse(WhatTheAlternativesLeave.nothing().stops(VALUE),
                "no alternative held it down, so there is nothing here that stopped it");
    }

    /** One branch, with its positions ordered on whole numbers. */
    private static Confinement.Planned<FactSubject> branch(OrderedIntervals<FactSubject> ordered) {
        return new Confinement.Planned<>(PlannedValues.top(), ordered, ON_WHOLE_NUMBERS);
    }

    /** The same, as a reading of the clauses met together. */
    private static StatedTogether.Said said(OrderedIntervals<FactSubject> ordered) {
        return new StatedTogether.Said(branch(ordered));
    }

    /** A branch nobody can be in. */
    private static Settlement.Sided dead() {
        return Settlement.Sided.settledAs(Confinement.Admission.at(
                souther.compiler.values.Emptiness.EMPTY, Confinement.EmptyBy.ORDER,
                Set.of(VALUE), Confinement.Shown.BY_THE_READINGS));
    }

    /** And one nothing showed empty. */
    private static Settlement.Sided live() {
        return Settlement.Sided.settledAs(
                Confinement.Admission.left(souther.compiler.values.Emptiness.UNDECIDED));
    }

    /** {@code value >= low}, held inside what a whole number's order reaches. */
    private static OrderedInterval atLeast(long low) {
        return Carrier.WHOLE.extent()
                .meet(new OrderedInterval(Endpoint.inclusive(Count.of(low)), null));
    }

    /** {@code value <= high}, the same way. */
    private static OrderedInterval atMost(long high) {
        return Carrier.WHOLE.extent()
                .meet(new OrderedInterval(null, Endpoint.inclusive(Count.of(high))));
    }
}
