package souther.compiler.check;

import java.util.Set;

/**
 * Which line of a declaration's clause an end is, at the grain the clause draws it.
 *
 * <p>Two grains and not one, because a clause draws lines at both. A comparison inside a conjunct
 * draws its own — {@code Bool.not(String.length(name) < 1 || String.length(code) < 1)} states one
 * per leaf, on two numbers, and a row at either says nothing about the other. And a conjunct can
 * leave the values somewhere none of its statements does: {@code n /= 0 && n /= 1} written into one
 * conjunct stops nothing on its own and leaves the number starting at two. Held at one grain, the
 * first kind is a line per conjunct and the second is a line named after whichever statement was
 * written first.
 *
 * <p><b>The conjunct's own text and not the number it was written about.</b> Which coordinate a
 * clause bounded is read from whatever value the reading started at — {@code Day}'s own clause is
 * about {@code value} read from {@code Day} and about {@code d} read from the {@code Span} holding
 * it — so two readings of one line spell the coordinate two ways, and an identity built on it calls
 * one authored line two.
 *
 * <p><b>And not how the end was found.</b> A comparison places an end and a counterfactual finds
 * that a conjunct accounts for one, and where they are about the same statement they are one line
 * owed one row. What each reading established stays with the reading ({@link LineProvenance}) and is
 * spent where the lines at one end are read off the evidence gathered there
 * ({@link DeclaredBounds.End#drawn}) — carried into the line, one line came back twice because two
 * readings had found it.
 */
public sealed interface DeclaredLine {

    /** Which conjunct of which clause drew it, which is what a rule is named by. */
    PartId<RuleRef.Invariant> part();

    /**
     * The line one statement of a conjunct drew.
     *
     * @param statement the statement, which is the finest a clause is decomposed into
     */
    record OfAStatement(InvariantStatementId statement) implements DeclaredLine {

        public OfAStatement {
            if (statement == null) {
                throw new IllegalArgumentException("a line a statement drew is some statement's");
            }
        }

        @Override
        public PartId<RuleRef.Invariant> part() {
            return statement.part();
        }
    }

    /**
     * The line several statements of one conjunct leave together, which is no one of them.
     *
     * <p>What the reading established, and no more. {@code n /= 0 && n /= 1} written into one
     * conjunct places no end at all — each side names a value and orders nothing — and what the
     * values are left running from is the two of them together, found by taking the conjunct away.
     * So the line is the conjunct's, and which of its statements are about this number is what
     * tells it from the line the same conjunct leaves on another.
     *
     * <p><b>Together, and not what the author wrote between them.</b> These reach this reading from
     * a conjunction as readily as from a choice: what is known is that the conjunct was taken away
     * and the end moved, which says nothing about the connective. Named for one of the two, the type
     * would be stating something the reading never established, and every reader of a line would be
     * told a connective by a value that never saw one.
     *
     * <p>Which conjunct it is is read off them. Held beside them, the pair would be a second way to
     * the rule this line is about, and a line with two of those can be built about two rules.
     *
     * @param among the conjunct's statements which are about this number. Not the one that drew the
     *              line — none of them did — and not left out either, since two lines of one
     *              conjunct on two numbers are told apart by nothing else
     */
    record OfStatementsTogether(Set<InvariantStatementId> among) implements DeclaredLine {

        public OfStatementsTogether {
            among = Set.copyOf(among);
            if (among.size() < 2) {
                throw new IllegalArgumentException(
                        "a line between a conjunct's statements is drawn by more than one of them: "
                                + among);
            }
            if (among.stream().map(InvariantStatementId::part).distinct().count() != 1) {
                throw new IllegalArgumentException(
                        "a conjunct draws its line between statements of its own: " + among);
            }
        }

        @Override
        public PartId<RuleRef.Invariant> part() {
            return among.iterator().next().part();
        }
    }
}
