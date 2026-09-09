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
public sealed interface ExpansionSite permits ExpansionSite.Direct, ExpansionSite.Supplied {

    /**
     * A site the source wrote where the copy was made: the call, or the name standing where a value
     * goes.
     *
     * <p>Told from a supplied site by what it takes to name it. These two are read off the
     * application alone, and a supplied one is read off where the block being applied came from —
     * which is a fact about a call further out. So these are what a supplied site names its own
     * application by, and what keeps that naming from asking the same question again.
     */
    sealed interface Direct extends ExpansionSite permits Written, Named { }

    /** A call the author wrote. */
    record Written(SourceConstructOrigin origin) implements Direct {

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
    record Named(SourceReferenceOrigin origin) implements Direct {

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
     * A block a call handed to a parameter, expanded at one of the applications of it.
     *
     * <p>Which block is which is not a question about the block: two calls of one combinator hand it
     * two, and what tells them apart is where each came in. Which copy of it is which is a second
     * question, and the answer is not the same one: an operation may apply a block it was handed
     * more than once — {@code List.distinctBy} asks its key twice — and every one of those is a copy
     * of the block's body standing on its own in the tree that runs.
     *
     * <p><b>Both, because neither answers alone.</b> The handover alone puts every application of
     * one supplied block under one name, so two copies of it would be one construct with two places
     * a run through them is recorded and nothing to say which. The application alone says where
     * inside the operation the copy was made and not whose code was copied, which is what the
     * crossing out of the operation reads.
     *
     * <p><b>The copy the block crossed into, not every copy it was handed to since.</b> The copy
     * named is the one whoever wrote the block handed it to. Whose code handed it over is what
     * decides it, and not how many hands it went through: a copy that was given the block by
     * something further out is passing on what it was given.
     * {@code HelperInliner#crossedInto} settles it where both the call and what was in force at it
     * are still in hand.
     *
     * @param from        where the block crossed out of the code that wrote it
     * @param application which application of it this copy was made at, as the source wrote it.
     *                    A site of the body being copied into and not of the caller's, so it tells
     *                    the applications of one operation apart and says nothing about which call
     *                    supplied the block. {@linkplain Direct Direct}, because what names an
     *                    application here is the application — asked of where the block came from,
     *                    this would be the question it is a component of
     */
    record Supplied(Handover from, Direct application) implements ExpansionSite {

        public Supplied {
            if (from == null || application == null) {
                throw new IllegalArgumentException(
                        "a block handed to a parameter was handed over somewhere and applied"
                                + " somewhere: " + from + " at " + application);
            }
        }

        /**
         * Where a block crossed out of the code that wrote it: the copy that took it and the
         * parameter it filled.
         *
         * <p>Its own value because it is settled a step before the site is. A call binds the
         * callables it hands over and the body applies them afterwards, so what the binding can say
         * is this and the application is the applying body's to say. Held as a whole
         * {@link Supplied} at binding time, one would have to be made without the half that was not
         * yet known — and what a walk would then meet is a site that had already named the copy.
         *
         * <p>Said as a {@linkplain ExpansionLineage.Step step} and not as a chain, which is what a
         * value standing inside a lineage can be. A chain is derived by whatever walks the copies
         * and is built again wherever an already-expanded body is walked, so a chain written down
         * here would name the copy under whichever walk first reached it — and a walk that had since
         * put the same construct somewhere else would find it naming nothing it holds.
         *
         * @param copy      the copy the block was handed to
         * @param parameter which of that callee's parameters it filled
         */
        public record Handover(ExpansionLineage.Step copy, ParameterSlot parameter) {

            public Handover {
                if (copy == null || parameter == null) {
                    throw new IllegalArgumentException(
                            "a block handed to a parameter was handed to some copy at some"
                                    + " parameter: " + copy + " " + parameter);
                }
            }

            @Override
            public String toString() {
                return copy + " " + parameter;
            }
        }

        /** The copy the block was handed to, which is what a crossing out of the operation reads. */
        public ExpansionLineage.Step copy() {
            return from.copy();
        }

        @Override
        public String toString() {
            return from + " applied at " + application;
        }
    }
}
