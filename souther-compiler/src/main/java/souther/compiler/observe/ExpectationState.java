package souther.compiler.observe;

/**
 * Whether a row's answer is owed.
 *
 * <p>A fact about the text, settled where the row was read and true whatever becomes of the
 * evaluation. Carried rather than worked out again: what a row states of the answer travels in
 * {@link RowStatement}, which answers a different question — whether the row's values could be
 * handed to a reader — and drops what it was carrying whenever they could not. A row whose input is
 * larger than a snapshot keeps is exactly that case, and reading this off the statement lost the
 * answer being owed for a row that is perfectly well written.
 *
 * <p>Two, because that is how many the question has. The source has three things it can put where
 * an answer goes, and the third — nothing a parse could read — is not a third answer here: such a
 * row is refused where it is written and no evaluation reaches it, so nobody is owed anything for
 * it and no outcome carries one.
 */
public enum ExpectationState {

    /** The row states what it expects, so nothing is owed for it. */
    NOT_OWED,

    /** The row is written {@code <?>}: its answer is owed and nobody has written it. */
    OWED
}
