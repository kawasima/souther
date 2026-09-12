package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.TypeSymbols;

/**
 * What a scope a test built for itself says its declarations mean.
 *
 * <p>For the tests that hold a {@link Symbols} and no compilation: there is no compile to have
 * published anything, so the question a reader asks of {@link PublishedDeclarations} has no answer
 * to be looked up, and the declaration in the scope is where it comes from instead.
 *
 * <p>The meaning is made by {@link DeclarationMeaning}, which is the one place a declaration is
 * turned into what it says. What is different here is only where the declaration came from — the
 * scope the test wrote rather than a compilation's own rung — which is the same difference these
 * tests already make when they hand a reading a scope of their own.
 */
public final class ScopedDeclarations {

    private ScopedDeclarations() {}

    /** Which form each of {@code symbols}'s declarations was written in, read off the same scope.
     *  A compilation answers this from its index; a test holding a scope of its own answers it from
     *  the declaration there, which is the same three-way split. */
    public static DeclarationKinds kindsOf(Symbols symbols) {
        return declaration -> switch (symbols.declaredNode(TypeSymbols.declared(declaration))) {
            case Hir.Data _ -> DeclarationKind.PRODUCT;
            case Hir.SumData _ -> DeclarationKind.SUM;
            case Hir.UnitData _ -> DeclarationKind.UNIT;
            case null -> null;
        };
    }

    /** What {@code symbols} declares, as the readers of a published declaration ask for it. */
    public static PublishedDeclarations of(Symbols symbols) {
        RuleReadingSource source = RuleReadings.ofNoClauseFiled(symbols);
        return declaration -> {
            Hir.Def declared = symbols.declaredNode(TypeSymbols.declared(declaration));
            return declared == null ? null : DeclarationMeaning.of(declared, source);
        };
    }
}
