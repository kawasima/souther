package souther.compiler.check;

/**
 * One statement of one conjunct of a declaration's clause, as the identity a statement carries
 * wherever it is recorded.
 *
 * <p>A declaration's clause is decomposed twice, and this is the second of them. The first is what
 * the author joined with {@code &&}, which {@link PartId} names. The second is what a reading
 * arrives at inside one of those conjuncts: a denial is carried to the leaves as a clause is read,
 * so a conjunction an author wrote as a denied choice is one conjunct and states as many
 * comparisons as it has leaves. Named by the conjunct alone, the second of them is the first said
 * again.
 *
 * <p><b>Which of the conjunct's statements, and not where in the tree it sits.</b> A rule written
 * out and the same rule reached through a helper are one line of the model, and the trees they are
 * read from are not alike: the helper's bindings stand between the conjunct and the comparison, so
 * the place the comparison holds in the expanded clause ({@link ClauseOccurrence}) moves with how
 * the author spelt it. What does not move is which of the conjunct's statements it is — bindings
 * are how a reading gets to a statement and are no statement of their own.
 *
 * <p><b>Issued where a statement is recognised, before anything is made of it.</b> Counted from
 * nought within each conjunct, over every statement the reading arrives at rather than over the
 * ones something came of: numbered by outcome, a statement no end was read from would leave the
 * next one holding its number, and one reading of a clause would number the statements differently
 * from another that made more of them.
 *
 * <p><b>And not where it was read from.</b> A clause is read once per place a walk opens a value at,
 * so a record holding two of a type reads that type's clause twice — one written statement, and a
 * row is owed for it once. Which reading arrived at it is {@link InvariantChecker.ReadingPlace}'s,
 * and a statement that held it would be a statement per position the value is carried to.
 *
 * @param part    the conjunct this is a statement of
 * @param ordinal which of that conjunct's statements it is, counted from zero over all of them
 */
public record InvariantStatementId(PartId<RuleRef.Invariant> part, int ordinal) {

    public InvariantStatementId {
        if (part == null) {
            throw new IllegalArgumentException("a statement is some conjunct's");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException(
                    "a statement of a conjunct is counted from zero: " + ordinal);
        }
    }

    /** Which clause of which declaration this is a statement of. */
    public RuleRef.Invariant rule() {
        return part.rule();
    }

    @Override
    public String toString() {
        return part + "/" + ordinal;
    }
}
