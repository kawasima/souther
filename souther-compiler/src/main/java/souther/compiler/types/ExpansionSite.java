package souther.compiler.types;

/**
 * Where a copy of a body was made, said in words the source settles.
 *
 * <p>What tells one copy of an expanded body from another. A helper is spliced into each call of it,
 * so a construct inside it stands once per call, and what says which of those a construct is in is
 * the call. Not a count of the expansions a pass performed: that number moves with what the pass was
 * asked to expand, and the two representations of one body are asked to expand different things.
 *
 * <p><b>A projection of {@link ApplicationOrigin} and not a wrapper of it.</b> An application says
 * why it is here, which is a wider question than which call this is — a block a name was expanded
 * into is named by the reference behind it, and that reference may be one this compiler composed,
 * which says nothing but a number; a block bound inside an expansion is named by the binding, whose
 * owner is a chain the passes wrote and count within. Held as the application itself, a copy would
 * be named by whichever of those turned up, and the ones that carry a count would put how the
 * compiler ran inside the identity of a construct.
 *
 * <p><b>Three arms, because three kinds of application reach an expansion.</b> What is here is what
 * each of them projects to when the projection lands on something the source settles, and the
 * projection refuses when it does not — an application of a fourth kind, or one of these three whose
 * parts do not reach a written thing, stops the expansion rather than being given a name. So this
 * says which sites the compiler can name, and never that the ones it cannot are impossible.
 */
public sealed interface ExpansionSite {

    /** A call the author wrote. */
    record Written(SourceConstructOrigin origin) implements ExpansionSite {

        public Written {
            if (origin == null || !origin.isWritten()
                    || origin.kind() != SourceConstruct.CALL) {
                throw new IllegalArgumentException(
                        "a call the source wrote is a construct it counted as a call: " + origin);
            }
        }

        @Override
        public String toString() {
            return String.valueOf(origin);
        }
    }

    /**
     * A name the author wrote where a value goes, expanded into the block that applies it.
     *
     * <p>No call was written, so there is no call to name it by; what the author wrote is the
     * reference, and the reference is counted within what wrote it. Told by the reference and not by
     * what it reaches: two occurrences of one name are two references and two blocks.
     */
    record Named(SourceReferenceOrigin origin) implements ExpansionSite {

        public Named {
            if (origin == null) {
                throw new IllegalArgumentException(
                        "a name written where a value goes is some reference of it");
            }
        }

        @Override
        public String toString() {
            return origin.owner() + " ref " + origin.ordinal();
        }
    }

    /**
     * A block a call handed to a parameter, expanded where the copy taking it applies it.
     *
     * <p>Which block is which is not a question about the block: two calls of one combinator hand it
     * two, and what tells them apart is the copy each was handed to. So this is that copy and the
     * parameter it filled — both settled by the source, the first by the calls the splicing went
     * through and the second by the position the callee declares.
     *
     * @param copy      the copy the block was handed to
     * @param parameter which of that callee's parameters it filled
     */
    record Supplied(ExpansionLineage copy, ParameterSlot parameter) implements ExpansionSite {

        public Supplied {
            if (copy == null || parameter == null) {
                throw new IllegalArgumentException(
                        "a block handed to a parameter was handed to some copy at some parameter: "
                                + copy + " " + parameter);
            }
        }

        @Override
        public String toString() {
            return copy + " " + parameter;
        }
    }
}
