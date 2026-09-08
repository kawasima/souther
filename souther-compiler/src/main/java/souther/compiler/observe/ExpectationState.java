package souther.compiler.observe;

/**
 * What a row's source put where its answer goes.
 *
 * <p>A fact about the text, settled where the row was read and true whatever becomes of the
 * evaluation. Carried rather than worked out again: what a row states of the answer travels in
 * {@link RowStatement}, which answers a different question — whether the row's values could be
 * handed to a reader — and drops what it was carrying whenever they could not. A row whose input
 * is larger than a snapshot keeps is exactly that case, and reading this off the statement lost
 * the answer being owed for a row that is perfectly well written.
 *
 * <p>Three, because the source has three and folding two of them here would put the choice back
 * where it was. Only {@link #OWED} is asked about downstream; the other two are told apart because
 * they are told apart where they are read, and a state that arrives as another is a state nothing
 * can be held to.
 */
public enum ExpectationState {

    /** The row states what it expects, at one grain or the other. */
    ASSERTED,

    /** The row is written {@code <?>}: its answer is owed and nobody has written it. */
    OWED,

    /**
     * No answer was read at the row at all.
     *
     * <p>The row is malformed and a parse diagnostic says so. Not {@link #OWED} — nobody left this
     * deliberately and nobody is waiting on an author for it — and not {@link #ASSERTED} either,
     * since there is nothing there to have been asserted.
     */
    UNWRITTEN
}
