package souther.compiler.partition;

import souther.compiler.inputs.NumericTerm;

/**
 * One quantity a comparison of a decision is written over.
 *
 * <p>Two sources and one arithmetic. What the input's numbers are is {@link NumericTerm}'s and is
 * taken as it is — the same terms a line is drawn on, so a column of the table and a border on the
 * same comparison cannot be about different quantities. What a dependency answered is this
 * reading's own, and it is held here rather than added to the input's vocabulary: what a search may
 * assume about the input and what a row may pin a dependency to are different licences, and a type
 * that carried both would hand each reader the other's.
 */
public sealed interface DecisionAtom {

    /** A number of the behavior's input. */
    record OfTheInput(NumericTerm term) implements DecisionAtom {

        public OfTheInput {
            if (term == null) {
                throw new IllegalArgumentException("a number of the input is some term");
            }
        }

        @Override
        public String toString() {
            return term.toString();
        }
    }

    /** A number a dependency answered with, at the place inside its answer the body read. */
    record OfAnAnswer(DecisionSubject.AnAnswer at) implements DecisionAtom {

        public OfAnAnswer {
            if (at == null) {
                throw new IllegalArgumentException("an answered number is some answer's");
            }
        }

        @Override
        public String toString() {
            return at.toString();
        }
    }
}
