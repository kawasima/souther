package souther.compiler.types;

/**
 * Which parameter of a declaration, counted along the parameters it declares.
 *
 * <p>A position in a written sequence, and that is the whole of why it is a number. What a
 * declaration writes is a list, so its entries have positions, and the position of one does not move
 * unless the declaration is edited. That is what separates this from the counts this compiler hands
 * out while running — a binding numbered as it was minted, a writing numbered as the pass performed
 * it — which say when something was reached rather than where it stands.
 *
 * <p><b>Read off the parameters and never off a binding.</b> An expansion mints a binding for what
 * a call handed to a parameter, and the number that binding carries is the minter's count over
 * everything it wrote. The two may agree; a value that took the second and called it the first
 * would be an identity that agrees with the declaration until something else is minted first.
 */
public record ParameterSlot(int index) {

    public ParameterSlot {
        if (index < 0) {
            throw new IllegalArgumentException(
                    "a parameter stands somewhere among the ones declared: " + index);
        }
    }

    @Override
    public String toString() {
        return "parameter " + index;
    }
}
