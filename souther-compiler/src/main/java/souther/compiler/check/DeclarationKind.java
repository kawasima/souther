package souther.compiler.check;

/**
 * Which of the three forms a declaration was written in.
 *
 * <p>Settled where a module's declarations are indexed and never again: what the author wrote is a
 * product, a sum or a unit, and no stage below changes which. So this is the whole of what a reader
 * asking about the form has to depend on — not the declaration it was read off, whose answer moves
 * when a line above it moves, and not what the declaration says, which is not worked out yet
 * wherever a declaration's own meaning is being made.
 *
 * <p>Three values and nothing beside them. What a sum's cases are is a fact about the names in it
 * resolving, what a product's fields hold is a fact about the declarations they name, and neither is
 * settled here — a reader wanting one asks what the declaration says. Carrying either here would put
 * a fact that moves inside an answer that does not, and every reader of the form would be told
 * about it.
 */
public enum DeclarationKind {

    /** {@code data X = { ... }}, and the newtype written as one field. */
    PRODUCT,

    /** {@code data X = A | B}. */
    SUM,

    /** {@code data X}. */
    UNIT
}
