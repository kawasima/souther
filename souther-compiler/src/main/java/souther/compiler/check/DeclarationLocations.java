package souther.compiler.check;

import souther.compiler.diag.DiagnosticPlace;
import souther.compiler.types.TypeKey;

/**
 * Where a declaration is written, for a reader that is about to point at one.
 *
 * <p>Beside {@link PublishedDeclarations} and not inside it, for the reason {@link ClauseLocations}
 * is beside the clauses: they are two facts about one declaration and a reader uses one of them.
 * Answered together, an edit that moves a declaration and changes nothing it says is an edit that
 * changes what every module importing it was told.
 *
 * <p>One declaration is found once. What is refused is a reader resolving a name to a declaration
 * for its meaning and resolving it again for its place — the address is settled first, and these are
 * two questions asked of that one address.
 *
 * <p>The answer is a {@link DiagnosticPlace} and not a position, so what a reader does with a
 * declaration it cannot send anybody to is decided by reading the two arms rather than by reading a
 * position and classifying it again. {@link DiagnosticPlace.Unavailable} is one of the answers and
 * not the absence of one: the declaration is there, and what this compilation does not hold is the
 * text it was written in.
 */
public interface DeclarationLocations {

    /**
     * Where {@code declaration} is written.
     *
     * @throws NoSuchDeclarationIsWritten where nothing declares it. Two of this compiler's answers
     *     disagreeing: a reader holding the address got it from something that said a declaration is
     *     there
     */
    DiagnosticPlace of(TypeKey declaration);

    /** Raised where a reader asks where a declaration is and nothing declares one. */
    final class NoSuchDeclarationIsWritten extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        public NoSuchDeclarationIsWritten(TypeKey declaration) {
            super("`" + declaration + "` was asked where it is written and nothing declares it");
        }
    }
}
