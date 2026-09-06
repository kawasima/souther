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
}
