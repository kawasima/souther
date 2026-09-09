package souther.compiler.check;

import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which positions each alternative of a choice holds down, out of the values its ends leave them.
 *
 * <p>A fact about one branch and about the values it leaves, which is what tells it from
 * {@link Settlement.Width}: the width is a relation over both alternatives and asks whether
 * dropping one of them would narrow the choice, and this asks of one of them whether it narrows
 * anything at all. A reader wanting the second and given the first would be asking about the branch
 * beside the one it is holding.
 *
 * <p><b>Off what the ends leave and never off what some rule mentioned.</b> A position some rule of
 * the branch bounded is not thereby a position the branch holds down: {@code n >= 2 || n <= 0}
 * bounds an {@code Int} twice and leaves it every value it had. Read off which positions were
 * bounded, a choice beside such a branch was told that the branch holds the position down, and an
 * end nothing worked out stayed open at a position the model draws no line at.
 *
 * <p><b>A may on each side, and the fact a reader spends is that it is not a member.</b> The same
 * written choice stands wherever a conjunction beside it was distributed in, and one copy's branch
 * can hold a position down where another copy's leaves it alone. What a reader acts on is that the
 * branch leaves the position at every value, which has to hold of every copy — so the copies are
 * taken in by union ({@link #alsoSeen}), and the answer is read as
 * {@link #leavesEveryValueOnLeft}. Kept the other way round, as the positions every copy holds
 * down, a position one copy left alone would be published as one the branch leaves alone
 * everywhere.
 *
 * <p>Union is associative, commutative and idempotent, which is what keeps the order the copies
 * were met in out of the answer.
 *
 * <p><b>Of where the orders stop and of nothing else.</b> What a set of values leaves a position is
 * a different question with a different word for "every value", and the reading that asks it
 * answers for its own rules at its own altitude ({@code ReadByClauses.OfARule#narrows}). Held here
 * as well, a branch would be asked whether it holds a position down and answer out of two languages
 * neither of which had read the other's rules.
 *
 * @param mayBeOnLeft  the positions some occurrence of the left alternative holds down
 * @param mayBeOnRight the same for the right
 */
record NarrowedByABranch(Set<FactSubject> mayBeOnLeft, Set<FactSubject> mayBeOnRight) {

    NarrowedByABranch {
        mayBeOnLeft = Set.copyOf(mayBeOnLeft);
        mayBeOnRight = Set.copyOf(mayBeOnRight);
    }

    /** A choice neither alternative of which holds anything down. */
    static NarrowedByABranch nothing() {
        return new NarrowedByABranch(Set.of(), Set.of());
    }

    /**
     * What one occurrence of a choice between these two branches holds down, read off the values
     * each of them leaves and building nothing.
     *
     * <p>Asked of the branches whatever their fate. An occurrence one alternative of which nobody
     * can be in is no choice there and its width rests on neither side
     * ({@link Settlement.WidthDependency#of}); what its ends leave is still what they leave, and an
     * occurrence answering that it holds nothing down would let a copy that is not a choice say the
     * branch leaves a position alone everywhere.
     */
    static NarrowedByABranch of(Confinement.Planned<FactSubject> one,
                                Confinement.Planned<FactSubject> other) {
        return new NarrowedByABranch(heldDownBy(one), heldDownBy(other));
    }

    /**
     * The positions this branch leaves holding less than every value of their own order.
     *
     * <p>Over the positions its ends bounded, since a position nothing bounded is left every value
     * its order has by the reading itself.
     */
    private static Set<FactSubject> heldDownBy(Confinement.Planned<FactSubject> branch) {
        OrderedIntervals<FactSubject> ordered = branch.ordered();
        Map<FactSubject, Carrier> carriers = branch.carriers();
        Set<FactSubject> out = new LinkedHashSet<>();
        for (FactSubject position : ordered.boundedAt()) {
            Carrier on = carriers.get(position);
            if (on == null) {
                // Ordered on nothing this vocabulary names, so there is no order for the ends to be
                // held against and nothing here can say they leave all of it.
                out.add(position);
                continue;
            }
            OrderedInterval extent = on.extent();
            if (!ordered.valuesAt(position, on).sameValuesAs(extent)) {
                out.add(position);
            }
        }
        return out;
    }

    /** Whether the left alternative leaves {@code position} at every value of its order, wherever
     *  the choice stands. */
    boolean leavesEveryValueOnLeft(FactSubject position) {
        return !mayBeOnLeft.contains(position);
    }

    /** The same asked of the right. */
    boolean leavesEveryValueOnRight(FactSubject position) {
        return !mayBeOnRight.contains(position);
    }

    /** What one more occurrence of the same choice holds down, taken in beside this. */
    NarrowedByABranch alsoSeen(NarrowedByABranch occurrence) {
        Set<FactSubject> left = new LinkedHashSet<>(mayBeOnLeft);
        left.addAll(occurrence.mayBeOnLeft());
        Set<FactSubject> right = new LinkedHashSet<>(mayBeOnRight);
        right.addAll(occurrence.mayBeOnRight());
        return new NarrowedByABranch(left, right);
    }
}
