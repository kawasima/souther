package souther.compiler.check;

import souther.compiler.types.TypeSymbol;

import java.util.Objects;

/**
 * A declaration named by another declaration, as what the naming says rather than as how it was
 * written.
 *
 * <p>What a spread or a case is, once where it was written is somebody else's question. A name in
 * the tree carries the spelling the author reached it by, which module reached it, and where every
 * segment of it sits; none of that is what the declaration says about itself, and all of it moves
 * when a line above the declaration is written.
 *
 * <p><b>Two arms, because a name that reaches nothing is a state of the model and not a missing
 * answer.</b> Name resolution reports what it could not reach and carries on, so a declaration
 * spreading a name nothing declares is a declaration this compiler has read and has something to say
 * about. Collapsed into an absence, two declarations spreading two different names that reach
 * nothing would say the same thing — and what one of them is short of is not what the other is.
 */
public sealed interface DeclarationReference {

    /**
     * It names a declaration, and this is which.
     *
     * <p>The identity and not the spelling. Two modules reach one declaration under two spellings
     * and are naming one thing, which is what an identity says and what a spelling cannot.
     *
     * <p>A {@link TypeSymbol} and not a {@link souther.compiler.types.TypeKey}, because a case may
     * name a declaration no module of this compilation wrote — the language declares some, and
     * those have no module to be addressed in. Every arm of the identity is a closed set of what
     * was declared, and none of them holds where anything was written.
     */
    record Named(TypeSymbol declaration) implements DeclarationReference {

        public Named {
            Objects.requireNonNull(declaration, "a reference that names a declaration says which");
        }
    }

    /**
     * It names nothing this compilation declares, and this is the name it was written under.
     *
     * <p>The canonical name, which is what a declaration says here: spreading one name that reaches
     * nothing is not spreading another. Not the spelling — a name and its casing are one name, and
     * how a name was spelled is where the declaration stands rather than what it says.
     */
    record Unanswered(String name) implements DeclarationReference {

        public Unanswered {
            Objects.requireNonNull(name, "a reference that names nothing was still written as"
                    + " something");
        }
    }
}
