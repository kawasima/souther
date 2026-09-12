package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;

/**
 * Which declarations are one value wearing a name, for a reader that only has to know that much.
 *
 * <p>Beside {@link DeclarationKinds} and not a fourth value of it. A declaration written over again
 * as a sum is a different form and is no more a newtype than it was, so the two answers move
 * separately — and a reader asking only this keeps what it worked out through an edit that changes
 * only which form the declaration is.
 *
 * <p>Settled when the module was indexed. Which way a declaration was written is what this says, and
 * resolution carries that along rather than deciding it, so a reader of this asks nothing of the
 * names in the declaration resolving.
 *
 * <p>What a newtype <em>wraps</em> is not here. That is the type its one field was written as, which
 * is a name resolving, and a reader wanting it asks the world that has resolved declarations.
 */
@FunctionalInterface
public interface DeclarationNewtypes {

    /** Whether {@code declaration} was written as one value wearing a name. */
    boolean of(TypeKey declaration);

    /** Nothing declared anywhere — for a reading over primitives, which asks of no declaration. */
    DeclarationNewtypes NONE = _ -> false;

    /**
     * The same question read off the declarations in {@code symbols}.
     *
     * <p>For a walk that has not been given one of these and reads the declaration for other things
     * besides — how far a newtype reaches, what a field holds — so that asking the compilation here
     * would leave the walk depending on the declaration anyway. What it answers is the flag the
     * author wrote, which is what the compilation answers from its index; the rule that flag decides
     * stays in its one reader ({@link Location#isStep}).
     *
     * <p>Every caller of this is a reader still holding a declaration for a positionless question.
     * There is a test that counts them, so the number can fall and cannot quietly rise.
     */
    static DeclarationNewtypes asWritten(Symbols symbols) {
        return declaration -> symbols.declaredNode(declaration) instanceof Hir.Data data
                && data.newtype();
    }

    /**
     * Whether a value of {@code type} is one value wearing a name.
     *
     * <p>The way in for a reader holding a type rather than an address, which is most of them. Only a
     * declaration of a module can be one: a primitive is not written, and a list, a map or an
     * optional is not a declaration to wear a name.
     */
    default boolean wraps(Type type) {
        return type instanceof Type.Ref ref
                && ref.name() instanceof TypeSymbol.AtModule at
                && of(at.key());
    }
}
