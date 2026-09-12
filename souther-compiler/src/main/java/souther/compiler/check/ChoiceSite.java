package souther.compiler.check;

import souther.compiler.diag.SourcePos;

/**
 * One choice an author wrote, as somewhere to send them.
 *
 * <p>Which choice this is, is where in its clause the {@code ||} stands. A source position cannot
 * say it — a helper expanded twice writes one operator at one place and is two choices — and the
 * object a walk happened to build cannot either, since a clause is read once for every place a walk
 * opens a value at and distribution copies a branch into every choice met with it.
 *
 * <p>Which clause's occurrence it is, is the carrier's. What a rule is answerable for is filed under
 * that rule and never met with another's, so a rule kept here as well would be a second answer to
 * which rule the carrier is about — and two of them can be built disagreeing.
 *
 * <p><b>One type over both readings.</b> A choice offering an alternative nothing could read leaves
 * two things open, and each reading finds its own: which values may stand at a position, and where
 * they stop. They are the same choice, and an author lifting it lifts both — so what names it has
 * to be one thing, or the day the two are put side by side there is nothing to put them together
 * by.
 *
 * @param at        where in the clause the {@code ||} stands, in the clause's own numbering
 * @param writtenAt the operator, which is what a reader is sent to: the position an author wrote
 *                  the {@code ||} at and not where the operand under it begins
 */
public record ChoiceSite(ClauseOccurrence at, SourcePos writtenAt) implements RuleShortfall.Site {

    public ChoiceSite {
        if (at == null || writtenAt == null) {
            throw new IllegalArgumentException(
                    "a choice is some occurrence of a clause, written somewhere");
        }
    }

    /** This choice of this clause and no other, whichever reading met it. */
    @Override
    public boolean equals(Object other) {
        return other instanceof ChoiceSite it && at.equals(it.at);
    }

    @Override
    public int hashCode() {
        return at.hashCode();
    }
}
