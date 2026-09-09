package souther.compiler.check;

import souther.compiler.types.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a written declaration came to: each parameter beside the shape it was admitted as, and what
 * the behavior answers.
 *
 * <p>A {@link Sig} says what crosses the boundary and nothing about where it came from. That is the
 * whole of what a codec, a composition and an emitter need, and it is not what a reader pairing a
 * declaration with its signature needs: those held the parameter list and the signature separately
 * and put them back together by position, each deciding for itself whether the two could be trusted
 * to line up. The pairing is not a fact to be re-established, because the walk that admits a
 * parameter makes exactly one shape from exactly one parameter. So it is carried: an {@link Input}
 * is a declared parameter and the shape that parameter was admitted as, and there is no arrangement
 * of two lists left for a reader to check.
 *
 * <p>Made by {@link SignatureBoundary} alone, from a behavior that wrote parameters. A composition
 * writes none — its inputs are its first stage's, borrowed as shapes — so it has no declaration to
 * pair with anything and has a {@code Sig} and no {@code DeclaredSig}. A reader that wants the names
 * a behavior gave its inputs is asking about a declaration, and asking for one of these is how it
 * says so.
 *
 * <p>The signature is derived here rather than beside here. {@link #boundary()} is built once, in
 * the constructor, out of the inputs: holding both is not two things to keep in agreement when one
 * is worked out from the other and neither can be handed in from outside.
 */
public final class DeclaredSig {

    /**
     * One parameter as it was written, and the shape it is admitted to arrive as.
     *
     * <p>Closed for the reason a {@link Sig} is closed. A {@link BoundaryInput} that exists has been
     * through the walk, and holding one means the shape was admitted; what an {@code Input} adds is
     * that this parameter is where that shape came from, and a public constructor would let anything
     * pair an admitted shape with a name no declaration wrote it under. The reader below would take
     * that for the declaration's own answer, which is the whole of what it is being handed.
     */
    public static final class Input {

        private final String name;
        private final BoundaryInput boundary;

        Input(String name, BoundaryInput boundary) {
            this.name = Objects.requireNonNull(name, "a parameter is written under a name");
            this.boundary = Objects.requireNonNull(boundary, "a parameter arrives as something");
        }

        /** What the declaration calls this parameter. */
        public String name() {
            return name;
        }

        /** What it can arrive as. */
        public BoundaryInput boundary() {
            return boundary;
        }

        /** The type it has, read off the shape as every type is. */
        public Type type() {
            return boundary.type();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Input input
                    && name.equals(input.name) && boundary.equals(input.boundary);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, boundary);
        }

        @Override
        public String toString() {
            return "Input[" + name + ": " + boundary + "]";
        }
    }

    private final List<Input> inputs;
    private final Sig boundary;

    DeclaredSig(List<Input> inputs, BoundaryOutput output) {
        this.inputs = List.copyOf(inputs);
        List<BoundaryInput> shapes = new ArrayList<>(this.inputs.size());
        for (Input input : this.inputs) {
            shapes.add(input.boundary());
        }
        this.boundary = new Sig(shapes, output);
    }

    /** The parameters the behavior declares, in the order they are written. */
    public List<Input> inputs() {
        return inputs;
    }

    /** The same declaration as what crosses its boundary, which is what a reader with no question
     *  about the declaration asks for. */
    public Sig boundary() {
        return boundary;
    }

    /**
     * Two readings of one declaration are the same when they name the same parameters, admit the
     * same shapes and answer the same thing. Renaming a parameter changes this and leaves
     * {@link #boundary()} alone, which is what keeps a rename from reaching a reader that only ever
     * asked what crosses.
     *
     * <p>The inputs and the answer, rather than the inputs and the whole projection: the shapes the
     * projection carries are these inputs' own, so comparing both would walk them twice. What the
     * store does with these is compare them — an answer is recomputed and asked whether it changed
     * — so the second walk would be paid on every edit that reaches a module.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof DeclaredSig declared
                && inputs.equals(declared.inputs) && boundary.out().equals(declared.boundary.out());
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputs, boundary.out());
    }

    @Override
    public String toString() {
        return "DeclaredSig[inputs=" + inputs + ", out=" + boundary.out() + "]";
    }
}
