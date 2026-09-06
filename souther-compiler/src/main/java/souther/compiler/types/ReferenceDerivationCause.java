package souther.compiler.types;

/**
 * Why a pass wrote a reference of its own.
 *
 * <p>A pass writes a name where the language has no syntax for what a body means — the operation an
 * empty collection stands for, the operation a checked value is written back as. The reference is
 * the pass's and the reason is not, so this is the reason, and {@link DerivedReferenceOrigin} adds
 * the pass's own count over what one reason produced.
 *
 * <p><b>References and applications have separate vocabularies.</b> A reason for writing a name is
 * not a reason for writing an application, and the two do not take each other's roots: what a
 * reference is derived from is a reference or a construct, and what an application is derived from
 * is an application. Held as one vocabulary, an arm added for one of them would be offered to the
 * other, and a producer could name a reference by the application it sits in.
 *
 * <p><b>Named for what was written and not for the pass that read it.</b> A pass is renamed, split,
 * or has its work moved, and a cause named after one says something different afterwards about the
 * same source.
 */
public sealed interface ReferenceDerivationCause {

    /**
     * A collection the author wrote in brackets — {@code [a, b]}, and the empty {@code []}.
     *
     * <p>What the brackets stand for is a library operation, and which operation is settled by the
     * position the collection is written at. So the operation is a name no source wrote, and the
     * collection is what made a pass write it.
     *
     * <p>The construct is one a source wrote, and one that says nobody did is refused: a pass may
     * compose brackets of its own, and a reference numbered under those would be numbered under
     * nothing.
     */
    record CollectionLiteral(SourceConstructOrigin construct) implements ReferenceDerivationCause {

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
     * A reference written out again, where the name that carried it is gone.
     *
     * <p>A rule about a value is reported by quoting what was applied, and what is left by then is
     * the checked value rather than the body. Written back out, the operation is named again — by
     * the pass doing the writing — and this says which reference that new name stands for.
     *
     * <p>Whatever the first one was. An author's, one another pass derived, one the generator
     * composed: writing a name out again is the same act over any of them, and a reader here is not
     * interpreting which kind it was. So a reference arriving in a form this did not foresee is
     * carried rather than refused, and nothing has to be added when another kind is.
     */
    record ReferenceWrittenBack(ReferenceOrigin from) implements ReferenceDerivationCause {

        public ReferenceWrittenBack {
            if (from == null) {
                throw new IllegalArgumentException("a reference written back out was some reference");
            }
        }
    }

    /**
     * The size a written comparison means, read as a comparison against a count.
     *
     * <p>Not the reference that was written. A rule comparing a collection is read as a rule about
     * how many it holds, and the operation that answers the count is a name this compiler wrote —
     * so it is a reference of its own, derived from the one the author's comparison reached.
     */
    record SizeMeaningOfReference(ReferenceOrigin from) implements ReferenceDerivationCause {

        public SizeMeaningOfReference {
            if (from == null) {
                throw new IllegalArgumentException("a size read off a comparison read some name");
            }
        }
    }
}
