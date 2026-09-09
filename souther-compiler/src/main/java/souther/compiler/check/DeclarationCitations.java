package souther.compiler.check;

import souther.compiler.diag.Citation;
import souther.compiler.types.TypeKey;

/**
 * Where a declaration's code is written, and where this compilation saw it.
 *
 * <p>Not {@link DeclarationLocations}, and not a coarser or finer version of it. That one answers
 * where a report may send a reader, which is a question with two answers: a stretch of text, or a
 * note about code nobody here holds. This answers what a {@link Citation} answers — which of the
 * ways a place can come to be this one is — and a reader that carries a place into an account rather
 * than into a caret means this. A place nobody settled and a place settled in a text this
 * compilation does not hold are two of the states here and neither of them is a place to point at,
 * so the other capability cannot express them at all.
 *
 * <p>Both are asked of the one address. One declaration is found once, and these are two questions
 * put to what was found — which is what the two of them being separate capabilities says, and what a
 * reader looking a declaration up a second time for its place would not.
 */
public interface DeclarationCitations {

    /**
     * Where {@code declaration}'s code is, as a citation.
     *
     * @throws NoSuchDeclarationIsCited where nothing declares it. Two of this compiler's answers
     *     disagreeing: a reader holding the address got it from something that said a declaration is
     *     there
     */
    Citation of(TypeKey declaration);

    /** Raised where a reader asks where a declaration's code is and nothing declares one. */
    final class NoSuchDeclarationIsCited extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        public NoSuchDeclarationIsCited(TypeKey declaration) {
            super("`" + declaration + "` was asked where its code is and nothing declares it");
        }
    }
}
