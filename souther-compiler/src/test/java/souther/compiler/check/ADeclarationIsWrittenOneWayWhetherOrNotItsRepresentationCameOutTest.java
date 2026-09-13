package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.frontend.CstFrontend;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The form a declaration is written in below the settling does not depend on whether a
 * representation could be derived for it.
 *
 * <p>Two things happen to a declaration at the derived stage, and they are told apart here: the
 * newtype constructions in what it says about itself are written as the constructions they denote,
 * and a product's decoder and encoder are read off its shape. The second can fail — a field whose
 * type nobody could name has no representation — and the first cannot fail for that reason.
 *
 * <p>What rests on it is the surface a best-effort reading of a module is given. A module holding
 * one declaration that could not be derived is still read for what its other definitions say, and
 * the declaration that could not be derived is on that surface in the form every reader below the
 * settling expects. Were the two written apart, the surface would carry an earlier spelling of one
 * declaration beside the settled spelling of the rest, and nothing in the tree would say which was
 * which.
 */
class ADeclarationIsWrittenOneWayWhetherOrNotItsRepresentationCameOutTest {

    /** A declaration whose clause writes a construction, beside one whose field names nothing. */
    private static final String MODULE = """
            module m exposing ( Wrapped, Amount )

            data Wrapped = Int
            data Amount = Int
                invariant isOk(value)

            data Broken = { value: Nowhere }

            let isOk (n: Int) : Bool = Wrapped(n) == Wrapped(0)
            """;

    private final ResolvedSymbols scope = symbols();
    private final InvariantSettled settled = settle();

    @Test
    void aDeclarationThatDerivedIsTheNormalisedDeclaration() {
        Normalized.Def amount = normalizedNamed("Amount");

        Derived.Def derived =
                Derived.Def.derive(amount, scope, ScopedDeclarations.kindsOf(scope),
                        ScopedDeclarations.of(scope));

        assertNotNull(derived, "`Amount` has a representation to derive");
        assertEquals(amount, derived.declaration(),
                "what the derived declaration holds is the normalised declaration it was made from");
    }

    /**
     * And one that did not derive is normalised all the same.
     *
     * <p>The two halves measured apart: the representation is absent and the constructions in the
     * clauses are still written as constructions. A reader given the settled spelling instead would
     * be given a declaration written one rung up from the ones beside it.
     */
    @Test
    void aDeclarationThatDidNotDeriveIsStillNormalised() {
        Normalized.Def broken = normalizedNamed("Broken");

        assertNull(Derived.Def.derive(broken, scope, ScopedDeclarations.kindsOf(scope), ScopedDeclarations.of(scope)),
                "nothing could be derived for a field whose type names nothing");
        assertNotNull(broken.node(),
                "and the declaration is still written in the form the stage below reads");
    }

    /**
     * The derivation does not normalise: it is handed a normalised declaration and hands the same
     * one back.
     *
     * <p>Which is what makes the rung above the one producer. Normalising again here would be a
     * second producer of that form, and the declaration a reader gets and the declaration a
     * representation was derived for could stop being one node.
     */
    @Test
    void everyDeclarationIsNormalisedByTheOneOperation() {
        for (InvariantSettled.Def def : settled.defs()) {
            Normalized.Def normalised = Normalized.Def.of(def, scope);
            Derived.Def derived =
                    Derived.Def.derive(normalised, scope, ScopedDeclarations.kindsOf(scope), ScopedDeclarations.of(scope));
            if (derived != null) {
                assertSame(normalised, derived.declaration(),
                        "`" + def.name() + "` reached the derivation already written this way");
            }
        }
    }

    /** A declaration with nothing to rewrite comes back as the node it already was, which is what
     *  says the operation is a rewrite and not a copy. */
    @Test
    void aDeclarationWithNothingToRewriteIsTheDeclarationItWas() {
        InvariantSettled.Def wrapped = defNamed("Wrapped");

        assertSame(wrapped.def(), Normalized.Def.of(wrapped, scope).node());
    }

    private Normalized.Def normalizedNamed(String name) {
        return Normalized.Def.of(defNamed(name), scope);
    }

    private InvariantSettled.Def defNamed(String name) {
        for (InvariantSettled.Def def : settled.defs()) {
            if (def.name().equals(name)) {
                return def;
            }
        }
        throw new AssertionError("the module declares `" + name + "`");
    }

    private static ResolvedSymbols symbols() {
        return TypeChecker.symbols(resolved(), DefaultStdlib.get());
    }

    private InvariantSettled settle() {
        return InvariantSettled.settle(
                Expandable.check(resolved(), Map.of(), DefaultStdlib.get()), scope,
                ScopedDeclarations.kindsOf(scope), Map.of());
    }

    private static Hir.Module resolved() {
        Ast.Module parsed = CstFrontend.parse(MODULE);
        return Resolve.resolving(parsed, SyntaxSymbols.of(parsed, DefaultStdlib.get())).module();
    }
}
