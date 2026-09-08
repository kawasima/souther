package souther.compiler.check;

import souther.compiler.types.TypeSymbol;

import java.util.Objects;
import java.util.Optional;

/**
 * What a reading of a declaration's invariants says about one clause of it.
 *
 * <p>Three questions, kept apart because none of them answers another. Which clause it is, is
 * {@link Id}. What a sentence can call it, is a name — a clause MAY be written without one. Those
 * two are {@link Ref}, which is what a reading carries. What was proved of it is neither and is
 * {@link InvariantChecker.Judgment}'s, which is why a clause is put on one of its sides rather than
 * carrying a verdict.
 *
 * <p>Where a clause is written is a fourth question, and it is asked of the declaration rather than
 * carried from the reading that judged the clause ({@link ClauseLocations}). It follows from the
 * text and from nothing a reading did, so a reading that carried it would be worked out again for
 * an edit that moved the clause and changed nothing it states — and a reader that kept its answer
 * would keep the place the clause used to be at.
 */
public final class Clause {

    private Clause() {}

    /**
     * Which clause it is and what a report calls it.
     *
     * <p>What a walk over a declaration's invariants hands on, and the one way to name a clause.
     * Everything that names one — a diagnostic about what it could not prove, a line a bound of it
     * drew — is naming the same rule, and a second construction is a second chance for two surfaces
     * to call one clause by different words.
     *
     * <p>Inside the reading of a declaration's clauses, and no further. This says which clause of
     * which declaration, which is what a walk over a declaration's invariants has to hand; a clause
     * is one of the things a rule of the model can be, and once something has been attributed to a
     * rule what carries it is {@link RuleRef}. Handed on as this, every reader downstream had to
     * work the rule back out for itself, and the ones written while only invariants arrived assumed
     * the answer.
     */
    public record Ref(Id id, Optional<ClauseName> name) {

        public Ref {
            Objects.requireNonNull(id, "a clause is one clause");
            Objects.requireNonNull(name, "a clause was written with a name or without one");
        }

        /** The clause a walk over a declaration's invariants arrived at. */
        public static Ref of(TypeOps.Declared declared) {
            return new Ref(new Id(declared.declaredOn(), declared.ordinal()),
                    declared.clause().name().map(ClauseName::new));
        }

        /**
         * The two readings of one clause, together.
         *
         * <p>Commutative, associative and idempotent, so the order the readings of a construction
         * are combined in does not decide what is reported. Which is the whole of what this is for:
         * two branches of a conditional read one construction once each, and a first-wins union
         * would let the walk's order decide what a warning names.
         *
         * <p>Two readings of one clause say the same thing about it, or the model contradicts
         * itself. Nothing here picks between them, which is what makes the three properties above
         * hold rather than being claimed: an operation that preferred one reading to another is a
         * first-wins union whichever rule it prefers by, and one that refused some disagreements
         * while absorbing others finds a contradiction or not depending on which pair was merged
         * first.
         */
        public static Ref merge(Ref a, Ref b) {
            if (!a.id.equals(b.id)) {
                throw new NotOneClause("two clauses, " + a.id + " and " + b.id + ", merged as one");
            }
            if (!a.name.equals(b.name)) {
                throw new NotOneClause("clause " + a.id + " is named " + a.name.orElse(null)
                        + " in one reading and " + b.name.orElse(null) + " in another");
            }
            return a;
        }

        @Override
        public String toString() {
            return id.declaredOn().name() + "#" + id.ordinal()
                    + name.map(n -> " (" + n + ")").orElse("");
        }
    }

    /**
     * Which clause: the declaration that wrote it, and which of that declaration's clauses it is.
     *
     * <p>Not the name, which a clause need not have, and not where it is written, which this compile
     * need not be able to quote — a set keyed on either of those puts two clauses the author wrote
     * separately under one key and reports one of them. Two spreads bringing one clause in twice
     * answer with one of these, which is what makes them one clause again.
     */
    public record Id(TypeSymbol.AtModule declaredOn, int ordinal) {

        public Id {
            Objects.requireNonNull(declaredOn, "a clause is written on a declaration");
        }
    }

    /**
     * Two readings of one clause that do not agree about the clause.
     *
     * <p>What a declaration says is one thing, and two readings of it are two accounts of that one
     * thing. Where they disagree about which clause it is or what it is called, one of them is about
     * a clause the other is not, and everything either says of it is filed against the wrong
     * obligation. There is
     * no answer to compose out of the two: taking one publishes a reading the model does not decide,
     * and a body that came back with nothing to say for that reason is a body whose invariants all
     * discharge, said in the same words.
     */
    static final class NotOneClause extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        NotOneClause(String message) {
            super(message);
        }
    }
}
