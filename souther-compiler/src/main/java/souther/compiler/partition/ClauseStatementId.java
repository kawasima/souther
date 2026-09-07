package souther.compiler.partition;

import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;

/**
 * Which statement of which part of a behavior's clause, as the identity one carries wherever it is
 * recorded.
 *
 * <p>The second of the two decompositions a clause has, and it is not the first. What an author
 * joined with {@code &&} is the parts of the clause and is named where the clause is split
 * ({@link PartId}); what one of those parts states is read off the tree that part expanded into,
 * where a helper's body brings connectives the author never wrote. So a part states as many things
 * as the reading finds in it, and which of them a line came out of is this.
 *
 * <p><b>Counted within the part and never across the clause.</b> Held as one number over the whole
 * clause, which statement of it a line was is a number that moves when a part before it states one
 * thing more — and it moves for the parts after, whose author wrote nothing. Two lines that are one
 * line then come out as two the day a helper above them gains a conjunct.
 *
 * <p><b>Statements and not comparisons.</b> Every arm of what a clause states holds its place in the
 * order, including the ones no reader of lines reads: a choice states neither of its sides and is
 * counted, a form nothing took apart is counted. So this says which statement, and the second
 * statement of a part is not the second comparison of it. Numbered over the comparisons alone, the
 * number would move with what a reading managed rather than with what the author wrote.
 *
 * <p>The number is not counted here or anywhere a reader stands. It is assigned where a part is read
 * for what it states ({@link ClauseStatements}) and carried from there.
 *
 * @param part    the part of the clause whose statement this is
 * @param ordinal which of that part's statements it is, counted from zero over all of them
 */
public record ClauseStatementId(PartId<RuleRef.Ensures> part, int ordinal) {

    public ClauseStatementId {
        if (part == null) {
            throw new IllegalArgumentException("a statement is some part of some clause's");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException(
                    "the statements of a part are counted from zero: " + ordinal);
        }
    }

    /** The rule the part this is a statement of belongs to. */
    public RuleRef.Ensures rule() {
        return part.rule();
    }

    @Override
    public String toString() {
        return part + "." + ordinal;
    }
}
