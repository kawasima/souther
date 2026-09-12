package souther.compiler.inputs;

import souther.compiler.check.RuleRef;

/**
 * One rule whose end at a position the reading of ends did not work out, and whether a choice an
 * author wrote is answerable for it.
 *
 * <p><b>Two questions and one value, because they are answered about the same thing and by
 * different readers.</b> Whether the line at this position was derived is what the border measure
 * asks, and it is answered by the rule and the position alone: two choices leaving one line open
 * leave one line open. Which clause an author can act on is what a document asks, and there the two
 * are two — lifting one of them leaves the other exactly where it was. Filed as one fact answering
 * both, whichever question was asked second got the other's answer.
 *
 * <p><b>One of these per choice the reading met, which is not how many things an author is left
 * with.</b> Which choice each of them is, is the construct its author wrote and the copy of it this
 * is ({@code ChoiceToLift}), counted over what the source settles rather than over what a walk met.
 * So these are as many as there are copies, and a helper holding one choice and expanded twice
 * makes two of them for one operator somebody wrote.
 *
 * <p>How many an author has to go and look at is a different count, taken where a document is
 * written: it is how many places they can edit, and the two expansions above come to one of those
 * because the copies carry the one construct. Read off the number of these instead, a report would
 * tell an author to do twice what rewriting the operator once answers.
 *
 * @param rule      the rule whose end it is. What a reader is sent to, and what tells two of these
 *                  at one position apart when they are of different rules
 * @param byAChoice whether a choice is answerable for it. False where none is — an end left open
 *                  under a conjunction, or beside an alternative nobody can be in, where the choice
 *                  is not a choice any more and there is no branch to look at. Not a reason to say
 *                  nothing: the line at the position was still not derived, and that is the
 *                  measure's business rather than the author's
 */
public record EndLeftOpen(RuleRef rule, boolean byAChoice) {

    public EndLeftOpen {
        if (rule == null) {
            throw new IllegalArgumentException("an end left open is some rule's");
        }
    }
}
