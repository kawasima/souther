package souther.compiler.check;

import souther.compiler.diag.SourcePos;

/**
 * One choice an author wrote, as somewhere to send them.
 *
 * <p>The identity and the place beside each other, rather than one made to answer for the other.
 * Which choice this is, is a question the whole reading asks and a source position cannot answer —
 * a helper expanded twice writes one operator at one place and is two choices — and where it is
 * written is a question the identity cannot answer, being nothing but itself. So the choice carries
 * both from where it is made.
 *
 * <p><b>One type over both readings.</b> A choice offering an alternative nothing could read leaves
 * two things open, and each reading finds its own: which values may stand at a position, and where
 * they stop. They are the same choice, and an author lifting it lifts both — so what names it has
 * to be one thing, or the day the two are put side by side there is nothing to put them together
 * by.
 *
 * @param writtenAt the operator, which is what a reader is sent to: the position an author wrote
 *                  the {@code ||} at and not where the operand under it begins
 */
public record ChoiceSite(ChoiceId id, SourcePos writtenAt) implements RuleShortfall.Site {

    public ChoiceSite {
        if (id == null || writtenAt == null) {
            throw new IllegalArgumentException(
                    "a choice is some choice of a clause, written somewhere");
        }
    }

    /** This choice and no other, which is what the identity is for and what a place cannot say. */
    @Override
    public boolean equals(Object other) {
        return other instanceof ChoiceSite it && id == it.id;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(id);
    }
}
