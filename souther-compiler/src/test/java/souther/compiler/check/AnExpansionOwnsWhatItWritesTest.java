package souther.compiler.check;

import souther.compiler.WhereItSits;
import souther.compiler.DefaultStdlib;
import souther.compiler.diag.Primary;

import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.frontend.CstFrontend;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every binding an expansion writes belongs to that expansion.
 *
 * <p>Expanding a call writes several bindings: one per argument, one for a lambda given to a function
 * parameter, and one for every binding copied out of the callee's body. They are one call's, and a reader that has to answer something about the call — which
 * signature they came from, what one variable of it stands for — can only do that if it can tell
 * which of them are the same call's. Owning them is what says so; before this, only the copied body's
 * were owned by the expansion and the rest belonged to the pass.
 */
class AnExpansionOwnsWhatItWritesTest {

    private static Hir.Module resolved(String source) {
        Ast.Module parsed = CstFrontend.parse(source);
        return Resolve.module(parsed, SyntaxSymbols.of(parsed, DefaultStdlib.get()));
    }

    /** Every binding in {@code e}, in the order they are written. */
    private static List<Hir.Binder> binders(Hir.Expr e) {
        List<Hir.Binder> out = new ArrayList<>();
        collect(e, out);
        return out;
    }

    private static void collect(Hir.Expr e, List<Hir.Binder> out) {
        if (e instanceof Hir.LetIn li) {
            out.add(li.binder());
        }
        if (e instanceof Hir.Expansion ex) {
            ex.bound().forEach(b -> out.add(b.binder()));
        }
        if (e instanceof Hir.Block b) {
            out.addAll(b.params());
        }
        TypeChecker.forEachChild(e, c -> collect(c, out));
    }

    /** The expansion each of {@code binders} belongs to, or null where it belongs to something else. */
    private static Set<BindingOwner.Expansion> expansionsOf(List<Hir.Binder> binders) {
        Set<BindingOwner.Expansion> out = new LinkedHashSet<>();
        for (Hir.Binder b : binders) {
            if (b.id().owner() instanceof BindingOwner.Expansion x) {
                out.add(x);
            }
        }
        return out;
    }

    private static Hir.Expr expanded(String defs, String of) {
        Hir.Module m = resolved("""
                module demo
                data X = Int
                behavior f : (x: X) -> X
                %s
                let f (x) = x
                """.formatted(defs));
        HelperInliner inliner = HelperInliner.forModule(m, DefaultStdlib.get());
        return inliner.inline(inliner.held().get(new souther.compiler.ast.DefinitionName(of))
                .definition().writtenBody(), inliner.bodyOf(of));
    }

    @Test
    void everyBindingOneExpansionWritesIsThatExpansionsOwn() {
        // `taxed` takes two arguments and its body binds one of its own, so the expansion writes
        // three bindings. All three are this call's.
        Hir.Expr body = expanded("""
                let taxed (n: Int, rate: Int) = {
                    let scaled = n * rate
                    scaled / 100
                }
                let go (m: Int) = taxed(m, 11)""", "go");
        List<Hir.Binder> written = binders(body);
        assertFalse(written.isEmpty(), "the expansion writes bindings");
        assertEquals(1, expansionsOf(written).size(), "they are one call's");
        for (Hir.Binder b : written) {
            assertTrue(b.id().owner() instanceof BindingOwner.Expansion,
                    "`" + b.name() + "` belongs to " + b.id().owner());
        }
    }

    @Test
    void twoCallsOfOneHelperAreTwoExpansions() {
        Hir.Expr body = expanded("""
                let twice (n: Int) = n * 2
                let go (m: Int) = twice(m) + twice(m + 1)""", "go");
        assertEquals(2, expansionsOf(binders(body)).size(), "two calls, two expansions");
    }

    @Test
    void anExpansionInsideAnExpansionIsAnotherExpansion() {
        Hir.Expr body = expanded("""
                let inner (n: Int) = n * 2
                let outer (n: Int) = inner(n) + 1
                let go (m: Int) = outer(m)""", "go");
        assertEquals(2, expansionsOf(binders(body)).size(), "the inner call is its own expansion");
    }

    /**
     * A report about a lambda given to a function parameter names the lambda it is about. The lambda
     * is registered under a synthetic name, and two combinators nested one inside the other name
     * their function parameters the same as often as not — so which one a report finds is asked by
     * the binding it was registered under and not by that name.
     */
    @Test
    void aReportAboutALambdaNamesTheLambdaItIsAbout() {
        String source = """
                module demo
                data X = Int
                behavior go : (x: X) -> X
                let go (x) = x
                let counts (ms: List<Map<String, Int>>) =
                    List.map((m) -> Map.size(Map.mapValues((k) -> k, m)), ms)
                """;
        // `Map.mapValues` takes a two-argument step and `List.map` a one-argument one, and both call the
        // parameter `f`. The inner one is written with one, and the report is about the inner one.
        souther.compiler.diag.CompileException e = org.junit.jupiter.api.Assertions.assertThrows(
                souther.compiler.diag.CompileException.class,
                () -> souther.compiler.Compiler.compile(source));
        assertTrue(e.getMessage().contains("takes 2 argument(s), but is written with 1"),
                e.getMessage());
        assertEquals(6, WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).start().line(),
                "the inner lambda's line, not the outer one's: " + e.getMessage());
    }

    /** Two bindings of one expansion are two bindings: one minter, so the ordinals do not repeat. */
    @Test
    void noTwoBindingsOfOneExpansionAreTheSameBinding() {
        Hir.Expr body = expanded("""
                let taxed (n: Int, rate: Int) = {
                    let scaled = n * rate
                    scaled / 100
                }
                let go (m: Int) = taxed(m, 11) + taxed(m, 12)""", "go");
        List<Hir.Binder> written = binders(body);
        Set<BindingId> distinct = new LinkedHashSet<>();
        for (Hir.Binder b : written) {
            assertTrue(distinct.add(b.id()), "`" + b.name() + "` repeats " + b.id());
        }
        assertEquals(written.size(), distinct.size());
    }
}
