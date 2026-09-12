package souther.compiler.check;

import java.util.Map;

/**
 * What every clause of one value says between them, with the choices still open.
 *
 * <p>One of two trees over the same clauses, and the one that derives values. Which values a
 * position may take is settled by every rule of the declaration together, so a conjunction here
 * distributes over a choice ({@link #meet}) and a branch is refined by clauses its author wrote
 * elsewhere. Which rules each clause took in is not here at all: that is a question about one
 * rule's own clauses, answered over {@link StatedByClauses}, and a constraint distributed in from a
 * neighbouring rule must not answer it. This type has no account to contaminate, and the account's
 * type has no way to take another rule's reading in — the separation is the two types.
 *
 * <p>What the two share is the choices. A {@link Choice} here says which written {@code ||} it came
 * from, and settling this tree is what decides, for every one of them, whether anybody can be in
 * each of its branches ({@link Settlement}). The account reads that decision; it never makes one.
 */
sealed interface StatedTogether {

    /**
     * What the clauses reaching here leave, in both languages.
     *
     * <p>One field and not two. Which values a position may take and where its order stops are one
     * answer, and a reader holding them apart is a reader that can ask each of them whether anything
     * satisfies the rules — which is what read a branch nobody can be in as one somebody can.
     */
    record Said(Confinement.Planned<FactSubject> confinement) implements StatedTogether {}

    /**
     * A choice whose branches are not settled yet, standing for the {@code ||} written at
     * {@code at} of {@code rule}'s clause — one of however many places distribution put it.
     *
     * <p>Both halves, because this tree is met out of the trees of every rule and an occurrence is
     * a coordinate of one clause: two rules each write a choice at their own occurrence nought.
     */
    record Choice(RuleRef.Invariant rule, ClauseOccurrence at,
                  StatedTogether left, StatedTogether right)
            implements StatedTogether {

        public Choice {
            if (rule == null || at == null || left == null || right == null) {
                throw new IllegalArgumentException(
                        "a choice is between two readings, written at some occurrence of a clause");
            }
        }
    }

    /** Nothing read, so nothing ruled out — the identity of {@link #meet}. */
    static StatedTogether top(Map<FactSubject, Carrier> carriers) {
        return new Said(Confinement.Planned.top(carriers));
    }

    /**
     * Both holding at once, distributed over every choice still open.
     *
     * <p>A conjunction of a choice is the choice between the conjunctions, and which written choice
     * it is goes with each copy: what is multiplied is where the branch stands, never which choice
     * it is a branch of.
     */
    default StatedTogether meet(StatedTogether other) {
        if (this instanceof Choice it) {
            return new Choice(it.rule(), it.at(), it.left().meet(other), it.right().meet(other));
        }
        if (other instanceof Choice it) {
            return new Choice(it.rule(), it.at(), meet(it.left()), meet(it.right()));
        }
        Said here = (Said) this;
        Said there = (Said) other;
        return new Said(here.confinement().meet(there.confinement()));
    }
}
