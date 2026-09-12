package souther.compiler.check;

/**
 * One occurrence of a clause's structure, counted from the outside in.
 *
 * <p>A position among the occurrences of one clause and not a name, so it means something only
 * beside the clause it is of — two clauses each have an occurrence numbered nought, and they are two
 * occurrences. What pairs it with the clause is the reader's, which holds the rule and the part it
 * is reading ({@link InvariantStatementId}).
 *
 * <p>Independent of how the clause stands. A denial changes what a connective composes and changes
 * nothing about which occurrences there are, so a clause read as stated and the same clause read as
 * denied number their occurrences alike — and an answer filed about one is an answer about the
 * other.
 *
 * <p><b>Of the tree the reading was built over, and not of the clause its author wrote.</b> A
 * clause is read at each place a walk opens a value, with that construction's fields put in for the
 * reads, and what a construction gives a field is an expression of its own: give one a conjunction
 * and the tree has a connective the clause's author did not write. It is numbered like any other
 * and everything after it takes the next number along, so two constructions of one declaration put
 * the part written in one place at two numbers
 * ({@code AnOccurrenceIsACoordinateOfTheTreeAReadingWasBuiltOverTest}).
 *
 * <p>So this names a part among the parts of one reading, and no answer crossing out of a reading
 * may be keyed by it. What survives a construction is which statement of which conjunct the part is
 * ({@link InvariantStatementId}), counted over what the author wrote.
 *
 * <p>Which occurrences a clause has is decided where the clause is read into its shape
 * ({@link ClauseExpr}) and nowhere else: a reader that numbered them for itself would be a second
 * answer to how many there are, and two answers to that is what reading the connectives twice came
 * to. That is a rule about who assigns the numbers of a clause being read, and not about who may
 * name one — a coordinate of a clause is said in the vocabulary of everything that files an answer
 * about a clause, so it is written here rather than inside the shape.
 */
public record ClauseOccurrence(int ordinal) {

    /** The clause itself, which takes the first number before anything under it does — see
     *  {@link ClauseExpr#under}. */
    static ClauseOccurrence ofTheClause() {
        return new ClauseOccurrence(0);
    }

    public ClauseOccurrence {
        if (ordinal < 0) {
            throw new IllegalArgumentException(
                    "an occurrence of a clause is counted from zero: " + ordinal);
        }
    }

    @Override
    public String toString() {
        return "#" + ordinal;
    }
}
