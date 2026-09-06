package souther.compiler.types;

/**
 * Which reference a name used as a value is, whoever wrote it.
 *
 * <p>Three answers, because a body holds names from more than one hand. An author writes one and
 * this source counted it ({@link SourceReferenceOrigin}). A pass writes one where the language has
 * no syntax for what it means — the operation an empty collection stands for, the library call a
 * checked value is written back as — and no source counted that, so it is named by what made the
 * pass write it ({@link DerivedReferenceOrigin}). And the generator composes a row that names a
 * value the module states, which no source wrote and nothing derived from one, so the occurrence
 * begins where it is composed ({@link FixtureReferenceOrigin}).
 *
 * <p><b>Not one answer with the second left out.</b> A reference a pass wrote is a reference: two of
 * them are two, an expansion of each writes a block of its own, and a reader telling one occurrence
 * from another has to be able to tell those apart as well. Left without an identity, the only thing
 * to tell them by is the place, and one helper expanded at two of its calls puts two of them at one
 * place.
 *
 * <p><b>And not one answer with the second given the first's numbers.</b> A pass's reference handed
 * the number of one an author wrote is this compiler's work standing among the model's — which is
 * what {@link SourceReferenceOrigin} says it is not. So the two are told apart by which they are,
 * and a reader that only accepts the author's says so by asking for that one.
 *
 * <p><b>Three arms and not a place for a fourth to be put quietly.</b> Which of these a producer
 * answers with was settled by asking each of them what it has behind it, and the one with nothing
 * is named for what it is rather than for having nothing. A producer added later with no source
 * behind it is a question about what its occurrences are; an arm it has to be given is how that
 * question gets asked, and a name for "composed by some pass" is how it goes unasked.
 */
public sealed interface ReferenceOrigin
        permits SourceReferenceOrigin, DerivedReferenceOrigin, FixtureReferenceOrigin {

    /**
     * What a reference composed out of {@code from} says about where it came from, given how this
     * composer would name a cause.
     *
     * <p>The one place the question is put, for the reason
     * {@link ApplicationOrigin#composedOutOf} is: a pass composing a name out of another has the
     * same cases to answer wherever it does it, and answering them apiece is how two readers come
     * to answer differently.
     *
     * <p>Two cases and not three, because every reference can be told from every other of its kind
     * — an author's, one a pass derived, one a run composed. What is left is the term with its
     * places taken out, which carries none and out of which nothing can be said.
     *
     * <p>Which case it is in is this type's to say, and which of what a composer derived it is, is
     * the composer's: one cause may make a pass write more than one name, and only that pass knows
     * how many.
     *
     * @param ordinal which of the names this composer derives from {@code from} is being made, by
     *                the composer's own count over them
     */
    static ReferenceOrigin composedOutOf(
            ReferenceOrigin from, int ordinal,
            java.util.function.Function<ReferenceOrigin, ReferenceDerivationCause> cause) {
        return from == null ? null : new DerivedReferenceOrigin(cause.apply(from), ordinal);
    }
}
