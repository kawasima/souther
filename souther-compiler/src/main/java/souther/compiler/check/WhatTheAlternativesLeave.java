package souther.compiler.check;

import souther.compiler.numeric.OrderedInterval;

import java.util.LinkedHashSet;
import java.util.Map;
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
     * <p><b>Of an occurrence that is a choice, and of no other.</b> Whether both alternatives are
     * ones somebody can be in is decided before this is asked ({@link Settlement.OfAChoice#of}),
     * and where they are not there is no choice at that copy — what is written inside a branch
     * nobody can be in constrains nobody, so its ranges are no part of what the written choice
     * leaves. Read off the branches whatever their fate, a copy that is not a choice puts a
     * position into what the branch holds down; and since a branch is dead for the author only
     * where nobody can be in it anywhere, that copy is met with the copies that are choices and
     * takes back what they showed.
     *
     * <p>One walk over the positions either branch bounded, which is where all three answers are:
     * what each side leaves says whether that side holds the position down, and the two of them
     * met by a choice says whether the choice leaves it whole. Asked a side at a time and then
     * again for the pair, the same ends are read twice over.
     */
    static WhatTheAlternativesLeave of(Confinement.Planned<FactSubject> one,
                                       Confinement.Planned<FactSubject> other) {
        // The carriers of one side, which are the declaration's and so are both sides'.
        Map<FactSubject, Carrier> carriers = one.carriers();
        Set<FactSubject> bounded = one.ordered().boundedAt();
        if (!other.ordered().boundedAt().isEmpty()) {
            bounded = new LinkedHashSet<>(bounded);
            bounded.addAll(other.ordered().boundedAt());
        }
        Set<FactSubject> onLeft = null;
        Set<FactSubject> onRight = null;
        Set<FactSubject> whole = null;
        for (FactSubject position : bounded) {
            // Asked of each side once, and of its own order once. A position outside a side's own
            // ranges is one that side leaves every value of, which is the answer its order gives
            // here without a case of its own.
            OrderedInterval extent = carriers.get(position).extent();
            OrderedInterval here = one.ordered().valuesAt(position, carriers);
            OrderedInterval there = other.ordered().valuesAt(position, carriers);
            if (!here.sameValuesAs(extent)) {
                onLeft = alsoAt(onLeft, position);
            }
            if (!there.sameValuesAs(extent)) {
                onRight = alsoAt(onRight, position);
            }
            if (here.join(there).sameValuesAs(extent)) {
                whole = alsoAt(whole, position);
            }
        }
        return new WhatTheAlternativesLeave(orNothing(onLeft), orNothing(onRight),
                orNothing(whole));
    }

    /** The same set with one more position in it, made where the first one arrives. */
    private static Set<FactSubject> alsoAt(Set<FactSubject> these, FactSubject position) {
        Set<FactSubject> out = these == null ? new LinkedHashSet<>() : these;
        out.add(position);
        return out;
    }

    /** And nothing where none ever did. */
    private static Set<FactSubject> orNothing(Set<FactSubject> these) {
        return these == null ? Set.of() : these;
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

    /**
     * Whether the choice itself stops {@code position} short of its order, wherever it stands.
     *
     * <p>Both halves, because the positions there is anything to answer about are the positions an
     * alternative held down. A choice stops nothing its alternatives did not — what it leaves is
     * what one of them leaves or wider — so a position neither of them holds down is one the choice
     * leaves whole, and it is outside {@link #mayLeaveWhole} for the same reason it is outside
     * these: nothing here was asked about it.
     *
     * <p>Read off {@code mayLeaveWhole} alone, an absence stands for two things — a position the
     * choice was shown to stop, and a position nobody put the question about — and the second is
     * every position of every declaration this choice says nothing about. What kept that from
     * showing is the caller happening to ask only about positions its branches stopped, which is a
     * condition on the caller that nothing here stated.
     */
    boolean stops(FactSubject position) {
        return (mayHoldDownOnLeft.contains(position) || mayHoldDownOnRight.contains(position))
                && !mayLeaveWhole.contains(position);
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
