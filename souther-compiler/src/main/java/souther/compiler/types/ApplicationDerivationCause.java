package souther.compiler.types;

/**
 * Why a pass wrote an application of its own.
 *
 * <p>A pass writes an application where the language has no syntax for what a body means — the
 * library operation a collection written in brackets stands for, the call a checked value is
 * written back as. The application is the pass's and the reason is not, so this is the reason, and
 * {@link ApplicationOrigin.Derived} adds the pass's own count over what one reason produced.
 *
 * <p><b>Its own vocabulary, beside the one references have.</b> A reason for writing an application
 * is not a reason for writing a name. Held as one vocabulary with {@link ReferenceDerivationCause},
 * an arm added for one of them would be offered to the other.
 *
 * <p>What an arm holds is whatever that reason actually is, and that is not always a construct or
 * an application: a name read where a value goes makes an application out of a reference, there
 * being no application to make it out of. What every one of them does hold is something that can be
 * told from every other of its kind, which is what a thing derived from it is derived from.
 */
public sealed interface ApplicationDerivationCause {

    /**
     * A collection the author wrote in brackets — {@code [a, b]}, and the empty {@code []}.
     *
     * <p>What a body holds where the brackets were is an application of a library operation, and
     * the collection is what made a pass write it. The construct is one a source wrote, and one
     * that says nobody did is refused: a pass may compose brackets of its own, and an application
     * numbered under those would be numbered under nothing.
     */
    record CollectionLiteral(SourceConstructOrigin construct) implements ApplicationDerivationCause {

        public CollectionLiteral {
            // And written as a collection. The construct says what the author put there, so one
            // saying they wrote a call is not the brackets this is derived from.
            if (construct == null || !construct.isWritten()
                    || construct.kind() != SourceConstruct.COLLECTION_LITERAL) {
                throw new IllegalArgumentException(
                        "a collection written in brackets is one a source counted as a"
                                + " collection: " + construct);
            }
        }
    }

    /**
     * An application written out again, from the value checking made of it.
     *
     * <p>A rule about a value is reported by quoting what was applied, and what is left by then is
     * the checked value rather than the body. What is written back is a new application: the block
     * a name was expanded into is one thing, and the call composed later to show what that block
     * computed is another. So this is not the application it was written back from — it says which
     * one it was written back from.
     *
     * <p>Whatever that one was, so long as it is one that can be told from every other of its kind.
     * One the author wrote, the application inside a block a name was expanded into, one another
     * pass derived: writing an application out again is the same act over any of them, and this
     * does not interpret which it was.
     *
     * <p><b>And not one that cannot.</b> What is written back from a composed fixture is another
     * composed thing, not a derivation of it — given one here, two of them would come out as one
     * value and the {@link ApplicationOrigin.Identified} this sits under would be promising an
     * identity it does not have. The caller says which case it is in; the type will not let it skip
     * the question.
     */
    record ApplicationWrittenBack(ApplicationOrigin.Identified from)
            implements ApplicationDerivationCause {

        public ApplicationWrittenBack {
            if (from == null) {
                throw new IllegalArgumentException(
                        "an application written back out was some application");
            }
        }
    }

    /**
     * A name read where a value goes, which a representation keeps standing.
     *
     * <p>The author wrote a name and applied nothing, and what a kept value is built as is a call of
     * no arguments. So the application is this reading's, and what made it necessary is the name —
     * a reference and not an application, there being none to derive from.
     *
     * <p>Not the block a name used as a value is expanded into. That one applies the function to
     * parameters it writes, and this one applies nothing; naming this by that would put a block in
     * the account where no block was written.
     */
    record NameReadAsAValue(ReferenceOrigin name) implements ApplicationDerivationCause {

        public NameReadAsAValue {
            if (name == null) {
                throw new IllegalArgumentException("a name read where a value goes is some name");
            }
        }
    }

    /**
     * The size a written comparison means, read as a comparison against a count.
     *
     * <p>Not the application that was written. A rule comparing a collection is read as a rule about
     * how many it holds, and the call that answers the count is one this compiler wrote — so it is
     * an application of its own, derived from the one the author wrote. Given that one's identity
     * instead, two applications would answer as one.
     */
    record SizeMeaningOfApplication(ApplicationOrigin.Identified from)
            implements ApplicationDerivationCause {

        public SizeMeaningOfApplication {
            if (from == null) {
                throw new IllegalArgumentException(
                        "a size read off a comparison read some application");
            }
        }
    }
}
