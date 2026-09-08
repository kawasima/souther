package souther.compiler.observe;

import souther.compiler.types.TypeSymbol;


/**
 * What a text stated of an answer.
 *
 * <p>Two grains, and a row that states neither. A statement writes a value, or it names the case
 * the answer is and nothing under it — {@code | Approved} says which case and says nothing about
 * what is in it, and that is weaker evidence and still evidence. A row written {@code <?>} states
 * that its answer is owed, which is a thing the text says and not a grain of assertion: it is
 * {@link Owed}, and a reader that would compare an answer against a statement has to meet it.
 *
 * <p>What it states and nothing more. Whether an answer keeps it is asked of what is bound to the
 * declarations the row was read against ({@code CheckedRow.SelfContained#holds}), and not of this:
 * a comparison reachable from a statement is one a reader can make with declarations of its own,
 * and two readings of one row would then answer differently about one answer — which is the whole
 * of what asking the language rather than the reader means.
 */
public sealed interface Expectation {

    /**
     * A statement an answer can be held to, at either grain.
     *
     * <p>What a comparison takes. Whether an answer is what a row states is a question about a row
     * that states one, and a row whose answer is owed cannot be asked it — so the two grains are
     * gathered here and the comparison takes this rather than an {@link Expectation}, which makes
     * the row that states nothing something a caller cannot hand over rather than something every
     * comparison has to have a word for.
     */
    sealed interface Asserts extends Expectation permits TheValue, TheCase {}

    /** The whole value. */
    record TheValue(Asserted value) implements Asserts {

        public TheValue {
            if (value == null) {
                throw new IllegalArgumentException("a stated value is a value");
            }
        }
    }

    /**
     * The case the answer is, and nothing under it.
     *
     * <p>Compared on the case, because there is no value under it to compare: holding a whole value
     * against it would report a difference that was never stated. The case an answer is is the
     * declaration it is of, which is what the reading that produced the answer already settled.
     */
    record TheCase(TypeSymbol name) implements Asserts {

        public TheCase {
            if (name == null) {
                throw new IllegalArgumentException("a stated case is a case");
            }
        }
    }

    /**
     * The row's answer is owed: it is written {@code <?>} and nobody has written what the system
     * answers.
     *
     * <p>A state of the text and not of this compile. The row is well formed, it hands over its
     * inputs like any other, and what it is short of is the author's half — so a run of it observes
     * where it goes and holds the answer to nothing, and the work left is reported as the row's
     * rather than as an arm nothing reaches.
     */
    record Owed() implements Expectation {}
}
