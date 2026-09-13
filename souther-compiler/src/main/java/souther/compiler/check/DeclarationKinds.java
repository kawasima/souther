package souther.compiler.check;

import souther.compiler.types.TypeKey;

/**
 * Which form each declaration was written in, for a reader that has to tell them apart.
 *
 * <p>Beside {@link PublishedDeclarations} and not inside it. What a declaration says is worked out
 * from the names in it resolving; which form it is was settled when the module was indexed. A reader
 * that only has to tell a sum from a product depends on the second and not the first — which is what
 * lets it ask while a declaration's own meaning is being made, and what keeps it from being told
 * that a declaration moved.
 */
@FunctionalInterface
public interface DeclarationKinds {

    /** The form {@code declaration} was written in, or null where nothing declares it. */
    DeclarationKind of(TypeKey declaration);

    /** Nothing declared anywhere — for a reading over primitives, which asks of no declaration. */
    DeclarationKinds NONE = _ -> null;

    /** Whether {@code declaration} is a sum, which is the question most readers of this have. */
    default boolean isSum(TypeKey declaration) {
        return of(declaration) == DeclarationKind.SUM;
    }
}
