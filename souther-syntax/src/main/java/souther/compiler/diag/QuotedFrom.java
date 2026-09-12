package souther.compiler.diag;

import souther.compiler.source.SourceId;

import java.util.Objects;

/**
 * Which source of this compilation a position is read from, as a finished answer.
 *
 * <p>What a caller asks when it has to name a file: to file a report under one, to look up which
 * module a place is in, to open a document in an editor. The answer is a source this compilation
 * holds, or it is not — and where it is not, this says which of the two reasons, because they are
 * not the same reason and a caller does different things with them.
 *
 * <p>This is the question that used to be asked of a source identity that could be null. Reading the
 * absence was how five consumers came to answer a question nobody had given them: whether a reader
 * could be sent here. One took it to mean "file this under the module's source", one to mean "there
 * is no module", one to mean "open the module's document", one to mean "this report has not been
 * anchored yet", and one to mean "read it in the diagnostic's own file". Those are five answers to
 * three different questions, arrived at privately, out of a value that answered none of them.
 *
 * <p>So the classification is here and is made once, and what to do with each answer stays with the
 * caller. Those are different things: which answer a position has is a fact about the position, and
 * what a build does when there is no file to name is a policy of whatever is asking. A caller that
 * files a report under the source it was computing for and a caller that gives up are both right,
 * and neither of them is classifying.
 *
 * <p>Not an {@code Optional<SourceId>}. The two negative arms carry what a caller can say instead of
 * naming a file, and an optional would put "the code is in a module you do not have" and "this text
 * has no name yet" under one empty — which is the drop {@link DiagnosticPlace.Unavailable} exists to
 * stop, one layer up.
 */
public sealed interface QuotedFrom {

    /** A source this compilation holds and can quote from. */
    record ASourceThisCompileHolds(SourceId source) implements QuotedFrom {

        public ASourceThisCompileHolds {
            Objects.requireNonNull(source, "a source this compile holds is one it has named");
        }
    }

    /**
     * A text put back together out of what {@code publishedBy} published. There is no file to quote,
     * and which module's text it is, is what can be said instead.
     *
     * <p>Which module's <em>text</em>, and not where the code at a position in it is written. Those
     * are two questions and they have different answers for a body spliced into that text while the
     * module was being read back: the text is the module's and the code is the spliced body's.
     * {@link Citation} answers the second, and a reader that took this for it would be putting them
     * back together — which is the inference the position's own components exist to keep apart.
     */
    record TextItCannotShow(SourceProvenance publishedBy) implements QuotedFrom {

        public TextItCannotShow {
            Objects.requireNonNull(publishedBy, "a published text is a module's");
        }
    }

    /**
     * A text this compilation is reading and has no name for — an editor's buffer before it is a file
     * of any compile, a snippet somebody parsed.
     *
     * <p>Nothing to say beyond that. Whoever is showing this knows which text is being read, because
     * they handed it over; the position does not, and a report that guessed would quote whatever sat
     * at those numbers in the file the reader had in mind.
     */
    record TextItCannotName() implements QuotedFrom {
    }

    /**
     * Where this stands among these, for a reader putting some of them in a steady order.
     *
     * <p>Beside the members, so that whoever adds one places it. What the numbers mean is nothing
     * beyond which comes first, and nothing may be read off the result: a text this compile holds
     * coming before one it cannot show says nothing about either.
     */
    static java.util.Comparator<QuotedFrom> inASteadyOrder() {
        return java.util.Comparator.<QuotedFrom>comparingInt(QuotedFrom::rank)
                .thenComparing(QuotedFrom::named, java.util.Comparator.naturalOrder())
                .thenComparing(QuotedFrom::publishedBy,
                        java.util.Comparator.nullsFirst(SourceProvenance.inASteadyOrder()));
    }

    private static int rank(QuotedFrom from) {
        return switch (from) {
            case ASourceThisCompileHolds _ -> 0;
            case TextItCannotShow _ -> 1;
            case TextItCannotName _ -> 2;
        };
    }

    /** What a source of this compile is called, which is what tells two of those apart. */
    private static String named(QuotedFrom from) {
        return switch (from) {
            case ASourceThisCompileHolds it -> it.source().value();
            case TextItCannotShow _, TextItCannotName _ -> "";
        };
    }

    /**
     * And where a text out of sight was published, compared as what it is.
     *
     * <p>Through its own order and not through the words it renders as. Two provenances rendering
     * alike are two provenances — a module this compile was handed and one the compiler ships are
     * told apart by which they are and not by the names they carry — and an order that compared the
     * rendering would leave them wherever the walk put them.
     */
    private static SourceProvenance publishedBy(QuotedFrom from) {
        return switch (from) {
            case ASourceThisCompileHolds _, TextItCannotName _ -> null;
            case TextItCannotShow it -> it.publishedBy();
        };
    }
}
