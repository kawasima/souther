package souther.compiler.types;

/**
 * What a source wrote that made a pass write something of its own.
 *
 * <p>A pass writes a name or an application where the language has no syntax for what it means: the
 * operation an empty collection stands for, the library call a checked value is written back as. The
 * author wrote something there all the same, and that is what is here — the cause, and never what
 * the pass produced. Named by its output, a derived thing would be named by the counter its own
 * names are spelled from, which is what identity is wanted instead of.
 *
 * <p><b>The cause is not the identity.</b> One construct may make a pass write several things, so
 * what is here is half the answer and the other half is the producer's own count over what it
 * derived from this ({@link DerivedReferenceOrigin}). A cause used alone would make two of them one.
 *
 * <p><b>Named for what was written and not for the pass that read it.</b> A pass is renamed, split,
 * or has its work moved, and a cause named after one says something different afterwards about the
 * same source.
 *
 * <p>Every arm holds a construct the author wrote, and refuses one that says nobody did. A pass may
 * compose brackets or an application no source wrote — and something derived from one of those has
 * no source cause to name, so it does not get to say it has one.
 */
public sealed interface DerivationCause {

    /** The construct of the source this is a cause of. */
    SourceConstructOrigin construct();

    /**
     * A collection the author wrote in brackets — {@code [a, b]}, and the empty {@code []}.
     *
     * <p>What the brackets stand for is a library operation, and which operation is settled by the
     * position the collection is written at: {@code []} at a set is that set's empty, and the same
     * brackets in a body are a list. So the operation is a name no source wrote, and the collection
     * is what made a pass write it.
     */
    record CollectionLiteral(SourceConstructOrigin construct) implements DerivationCause {

        public CollectionLiteral {
            requireWritten(construct, "a collection written in brackets");
        }
    }

    /**
     * An application the author wrote, read back out of what checking made of it.
     *
     * <p>A rule about a value is reported by quoting what the author applied, and what is left by
     * then is the checked value rather than the body. Written back out, the operation is named
     * again — by this pass, since the name the author wrote is gone — and the application they
     * wrote is what made that necessary.
     */
    record ApplicationWrittenBack(SourceConstructOrigin construct) implements DerivationCause {

        public ApplicationWrittenBack {
            requireWritten(construct, "an application the author wrote");
        }
    }

    private static void requireWritten(SourceConstructOrigin construct, String what) {
        if (construct == null || !construct.isWritten()) {
            throw new IllegalArgumentException(
                    what + " is one a source wrote: " + construct);
        }
    }
}
