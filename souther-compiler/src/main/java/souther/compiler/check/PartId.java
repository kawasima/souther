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
 * split into the parts its author wrote ({@link ClauseHelpers}) and carried from there,
 * and {@link ClauseHelpers.AuthoredPart#idFor} is the one place this is made. The representation is
 * public because a report and a reading of inputs both hold one; making one is not.
 *
 * <p><b>The number is a position and not a name.</b> Which conjunct of a clause a part is says where
 * it stands among the parts of that clause and nothing else, so it means something only beside the
 * rule — two clauses each have a part numbered nought, and they are two parts.
 *
 * <p><b>A clause the author named, and not any rule of the model.</b> Those are the clauses written
 * in parts: a clause of a {@code data}'s invariant and a clause of a behavior's {@code ensures} are
 * each what the author joined out of conjuncts, and a rule written in a body is a rule apiece with
 * nothing an author wrote as several. Which of the two a rule is is {@link RuleRef.Named}'s answer
 * and the seal is read here rather than restated: a kind of rule added to it is a kind this admits
 * or refuses by having been put on that side of the seal.
 *
 * <p><b>And which of them, kept.</b> A reader that has to hold the parts of one kind says so in the
 * type of what it holds, so the two never arrive at one another's readers: what a declaration's
 * clause drew is looked up by the words the declaration wrote ({@link DeclaredBorders}), and there
 * is no such reading of a behavior's. Written as one type over both, that reader had to ask again
 * which kind it was holding and say what it would do with the one that cannot arrive — a narrowing
 * downstream restoring what the type above it gave away.
 *
 * @param <R>     which kind of clause this is a part of, which is what a holder of one is held to
 * @param rule    the clause this is a part of, as a report names it
 * @param ordinal which of that clause's parts it is, counted from zero over all of them
 */
public record PartId<R extends RuleRef.Named>(R rule, int ordinal) {

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
