package souther.compiler.check;

import souther.compiler.numeric.OrderedInterval;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What each alternative of a choice leaves the positions, and what the two of them leave between
 * them, read as values of the positions' own orders.
 *
 * <p>Three questions of one walk, and none of them is {@link Settlement.Width}'s. The width is a
 * relation over both alternatives and asks whether dropping one would narrow the choice; these ask
 * what a branch leaves, and what the pair leaves. A reader wanting one of these and given the width
 * would be asking about the branch beside the one it is holding.
 *
 * <p><b>Off what the ends leave and never off what some rule mentioned.</b> A position some rule of
 * a branch bounded is not thereby a position the branch holds down: {@code n >= 2 || n <= 0} bounds
 * an {@code Int} twice and leaves it every value it had. Read off which positions were bounded, a
 * choice beside such a branch was told that the branch holds the position down, and a rule with two
 * bounds covering the order was credited with a line nobody draws.
 *
 * <p><b>A may in every set, and the fact a reader spends is that it is not a member.</b> The same
 * written choice stands wherever a conjunction beside it was distributed in, and one copy can hold
 * a position down where another leaves it alone. Each reader here acts on a negative — that the
 * branch leaves the position at every value, or that the choice stops it — and a negative has to
 * hold of every copy, so the copies are taken in by union ({@link #alsoSeen}). Kept the other way
 * round, as what every copy did, a position one copy left alone would be published as one the
 * branch leaves alone everywhere.
 *
 * <p>Union is associative, commutative and idempotent, which is what keeps the order the copies
 * were met in out of the answer.
 *
 * <p><b>No reader's answer turns on that yet.</b> Copies that disagree are reached — compiling this
 * repository's own corpus meets them — and keeping either copy alone rather than both leaves every
 * other test there is passing. So the rule is pinned on this type
 * ({@code WhatOneCopyOfAChoiceLeavesIsNotWhatTheChoiceLeavesTest}) rather than through a model, and
 * what settles the direction is which way a reader can be wrong: publishing a copy's own answer for
 * the choice says a position is stopped where a copy beside it draws no line.
 *
 * <p><b>Of where the orders stop and of nothing else.</b> What a set of values leaves a position is
 * a different question with a different word for "every value", and the reading that asks it
 * answers for its own rules at its own altitude ({@link ReadByClauses.OfARule#narrows}). Held here
 * as well, a branch would be asked whether it holds a position down and answer out of two languages
 * neither of which had read the other's rules.
 *
 * @param mayHoldDownOnLeft  the positions some occurrence of the left alternative holds down
 * @param mayHoldDownOnRight the same for the right
 * @param mayLeaveWhole      the positions some occurrence of the choice leaves at every value of
 *                           their order, which is not what either alternative alone leaves them:
 *                           two bounds reaching opposite ends of a carrier hold the position down
 *                           on each side and leave all of it between them
 */
record WhatTheAlternativesLeave(Set<FactSubject> mayHoldDownOnLeft,
                                Set<FactSubject> mayHoldDownOnRight,
                                Set<FactSubject> mayLeaveWhole) {

    WhatTheAlternativesLeave {
        mayHoldDownOnLeft = Set.copyOf(mayHoldDownOnLeft);
        mayHoldDownOnRight = Set.copyOf(mayHoldDownOnRight);
        mayLeaveWhole = Set.copyOf(mayLeaveWhole);
    }

    /** A choice whose alternatives hold nothing down and leave nothing whole, for a caller with no
     *  branches to read. */
    static WhatTheAlternativesLeave nothing() {
        return new WhatTheAlternativesLeave(Set.of(), Set.of(), Set.of());
    }

    /**
     * What one occurrence of a choice between these two branches leaves, read off the values each
     * of them leaves and building nothing.
     *
     * <p>Asked of the branches whatever their fate. An occurrence one alternative of which nobody
     * can be in is no choice there and its width rests on neither side
     * ({@link Settlement.WidthDependency#of}); what its ends leave is still what they leave, and an
     * occurrence answering that it holds nothing down would let a copy that is not a choice say the
     * branch leaves a position alone everywhere.
     */
    static WhatTheAlternativesLeave of(Confinement.Planned<FactSubject> one,
                                       Confinement.Planned<FactSubject> other) {
        // The carriers of one side, which are the declaration's and so are both sides'.
        return new WhatTheAlternativesLeave(
                one.ordered().stoppedShortOfTheirOrders(one.carriers()),
                other.ordered().stoppedShortOfTheirOrders(other.carriers()),
                leftWholeBy(one, other));
    }

    /**
     * The positions the choice between these two leaves at every value of their order.
     *
     * <p>The hull of what the alternatives leave, which is what a choice of two ranges comes to,
     * held against the order. Asked of the sides one at a time, the pair of bounds reaching
     * opposite ends of a carrier answers that both sides hold the position down — which is true of
     * each of them and false of the choice they are alternatives of.
     *
     * <p>Over the positions either of them bounded. A position neither bounded is left whole by
     * both and so by the choice, and it is left out because a reader here is striking positions off
     * what the branches brought and has nothing to strike one off with.
     */
    private static Set<FactSubject> leftWholeBy(Confinement.Planned<FactSubject> one,
                                                Confinement.Planned<FactSubject> other) {
        Set<FactSubject> bounded = one.ordered().boundedAt();
        if (!other.ordered().boundedAt().isEmpty()) {
            bounded = new LinkedHashSet<>(bounded);
            bounded.addAll(other.ordered().boundedAt());
        }
        Set<FactSubject> out = null;
        for (FactSubject position : bounded) {
            Carrier on = one.carriers().get(position);
            if (on == null) {
                continue;
            }
            OrderedInterval extent = on.extent();
            if (!one.ordered().valuesAt(position, on).join(other.ordered().valuesAt(position, on))
                    .sameValuesAs(extent)) {
                continue;
            }
            if (out == null) {
                out = new LinkedHashSet<>();
            }
            out.add(position);
        }
        return out == null ? Set.of() : out;
    }

    /** Whether the left alternative leaves {@code position} at every value of its order, wherever
     *  the choice stands. */
    boolean leavesEveryValueOnLeft(FactSubject position) {
        return !mayHoldDownOnLeft.contains(position);
    }

    /** The same asked of the right. */
    boolean leavesEveryValueOnRight(FactSubject position) {
        return !mayHoldDownOnRight.contains(position);
    }

    /** Whether the choice itself stops {@code position} short of its order, wherever it stands. */
    boolean stops(FactSubject position) {
        return !mayLeaveWhole.contains(position);
    }

    /** Whether there is any position at all a copy of this choice left whole, which is what a
     *  caller with nothing to strike off asks. */
    boolean leavesNothingWhole() {
        return mayLeaveWhole.isEmpty();
    }

    /** What one more occurrence of the same choice leaves, taken in beside this. */
    WhatTheAlternativesLeave alsoSeen(WhatTheAlternativesLeave occurrence) {
        return new WhatTheAlternativesLeave(
                union(mayHoldDownOnLeft, occurrence.mayHoldDownOnLeft()),
                union(mayHoldDownOnRight, occurrence.mayHoldDownOnRight()),
                union(mayLeaveWhole, occurrence.mayLeaveWhole()));
    }

    private static Set<FactSubject> union(Set<FactSubject> these, Set<FactSubject> those) {
        if (those.isEmpty()) {
            return these;
        }
        Set<FactSubject> out = new LinkedHashSet<>(these);
        out.addAll(those);
        return out;
    }
}
