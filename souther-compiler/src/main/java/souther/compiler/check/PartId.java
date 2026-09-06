package souther.compiler.check;

/**
 * Which part of which rule, as the identity a part carries wherever it is recorded.
 *
 * <p>An authored part is one conjunct of one clause, and the pair below is what every reader of one
 * already keyed by — a line is named by the clause and the conjunct it came out of, a question is
 * raised against them, a report prints them. Written as two fields side by side, the pair had to be
 * assembled by whoever held them, and the number in it had to be counted by somebody: a second walk
 * with a counter of its own calls one authored part two the day the two disagree about which parts
 * there are.
 *
 * <p>So the number is not counted here or anywhere a reader stands. It is assigned where a clause is
 * split into the parts its author wrote ({@link ClauseHelpers#conjunctsOf}) and carried from there,
 * and {@link ClauseHelpers.AuthoredPart#idFor} is the one place this is made. The representation is
 * public because a report and a reading of inputs both hold one; making one is not.
 *
 * <p><b>The number is a position and not a name.</b> Which conjunct of a clause a part is says where
 * it stands among the parts of that clause and nothing else, so it means something only beside the
 * rule — two clauses each have a part numbered nought, and they are two parts.
 *
 * <p><b>A clause of a declaration's invariant, and not any rule of the model.</b> Those are the
 * clauses that are split into parts: a rule written in a body is a rule apiece, and the lines a
 * behavior's rule draws are counted over the comparisons it states rather than over anything an
 * author wrote as several. Written wider, every reader of a part had to narrow it again and say
 * what it would do with a rule that cannot arrive — one decision, made in as many places as hold a
 * part.
 *
 * @param rule    the clause this is a part of, as a report names it
 * @param ordinal which of that clause's parts it is, counted from zero over all of them
 */
public record PartId(RuleRef.Invariant rule, int ordinal) {

    public PartId {
        if (rule == null) {
            throw new IllegalArgumentException("a part is some rule's own");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException(
                    "a part of a clause is counted from zero: " + ordinal);
        }
    }

    @Override
    public String toString() {
        return rule + "#" + ordinal;
    }
}
