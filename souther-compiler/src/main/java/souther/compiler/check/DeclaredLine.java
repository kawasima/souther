package souther.compiler.check;

import java.util.Set;

/**
 * Which line of a declaration's clause an end is, at the grain the clause draws it.
 *
 * <p>Two grains and not one, because a clause draws lines at both. A comparison inside a conjunct
 * draws its own — {@code Bool.not(String.length(name) < 1 || String.length(code) < 1)} states one
 * per leaf, on two numbers, and a row at either says nothing about the other. A choice draws one
 * between them all: {@code value == "a" || value == "b"} leaves the value running from one string to
 * the other, and neither side of it places that end. Held at one grain, the first kind is a line per
 * conjunct and the second is a line named after whichever branch was written first.
 *
 * <p><b>And not how the end was found.</b> A comparison places an end and a counterfactual finds the
 * conjunct accounts for one, and where they are about the same statement they are one line owed one
 * row ({@link DeclaredBounds.End#tighter}). What each reading established stays with the reading
 * ({@link LineProvenance}) and is spent on the way here — carried into the line, one line came back
 * twice because two readers had found it.
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
     * The line a conjunct draws between its own statements, which is no one of them.
     *
     * <p>What a choice leaves. {@code value == "a" || value == "b"} stops the value at "a" and at
     * "b", and neither branch stops it anywhere: each of them names a value and orders nothing, and
     * what places the ends is the two of them together. So the line is the conjunct's, and the
     * statements are what tells it from the line the same conjunct draws on another number.
     *
     * <p>Which conjunct it is is read off them. Held beside them, the pair would be a second way to
     * the rule this line is about, and a line with two of those can be built about two rules.
     *
     * @param among the conjunct's statements which are about this number. Not the one that drew the
     *              line — none of them did — and not left out either, since two lines of one
     *              conjunct on two numbers are told apart by nothing else
     */
    record OfAChoice(Set<InvariantStatementId> among) implements DeclaredLine {

        public OfAChoice {
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
