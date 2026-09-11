package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A {@code depends on} clause names injection targets (spec §depends-on, §injected-behavior). A name that is not one is
 * reported where it was written, saying which rule it broke — not left to the call site, where it
 * used to surface as an arbitrary JVM call (E1401), advice for a problem the author did not have
 * (issue #96).
 */
class CompileDependsOnClauseTest {

    @Test
    void dependsOnNamingAnImplementedBehaviorIsReportedAtTheClause() {
        String src = """
                module demo
                data A = { x: Int }
                data R = { z: Int }

                behavior helper : (a: A) -> R
                    constructs R
                let helper (a) = R { z = a.x }

                behavior use : (a: A) -> R
                    depends on helper
                let use (a, helper) = helper(a)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1607", e.code(), e.getMessage());
        assertTrue(e.getMessage().contains("helper"), e.getMessage());
    }

    @Test
    void dependsOnNamingACompositionIsReportedWithoutAdvisingToRemoveALet() {
        // a `>->` composition is its own implementation and has no `let` to remove, so the advice has
        // to be the one that holds for every implementation: take the name out of `depends on`
        String src = """
                module demo
                data A = { x: Int }
                data R = { z: Int }

                behavior one : (a: A) -> A
                    constructs A
                let one (a) = A { x = a.x }

                behavior two : (a: A) -> R
                    constructs R
                let two (a) = R { z = a.x }

                behavior chain = one >-> two

                behavior use : (a: A) -> R
                    depends on chain
                let use (a, chain) = chain(a)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1607", e.code(), e.getMessage());
        String hint = new HumanRenderer(false).render(e.diagnostic(),
                new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.ENGLISH);
        assertTrue(hint.contains("Remove `chain` from `depends on`"), hint);
        assertFalse(hint.contains("`let`"), "a composition has no `let` to remove: " + hint);
    }

    @Test
    void dependsOnNamingAnUnknownBehaviorIsReportedAtTheClause() {
        String src = """
                module demo
                data A = { x: Int }
                data R = { z: Int }

                behavior use : (a: A) -> R
                    depends on nosuch
                let use (a, nosuch) = nosuch(a)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1607", e.code(), e.getMessage());
        assertTrue(e.getMessage().contains("nosuch"), e.getMessage());
    }

    @Test
    void dependsOnNamingAnImportedImplementedBehaviorIsReportedAtTheClause() {
        // an imported behavior with a `let` in its own module is an implementation, not an injection
        // target: it is composed with `>->`, not injected.
        String lib = """
                module demo.lib exposing ( A, R, price )
                data A = { x: Int }
                data R = { z: Int }
                behavior price : (a: A) -> R
                    constructs R
                let price (a) = R { z = a.x }
                """;
        String app = """
                module demo.app
                import demo.lib ( A, R, price )
                behavior use : (a: A) -> R
                    depends on price
                let use (a, price) = price(a)
                """;
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(lib, app)));
        assertEquals("E1607", e.code(), e.getMessage());
    }

    /** A callee nothing declares is a name that resolves to nothing, whatever the clause says: the
     *  absence of a `depends on` neither excuses it nor makes it a different kind of error. */
    @Test
    void aCalleeNoDependsOnClauseNamesIsStillAnUnresolvedName() {
        String src = """
                module demo
                data A = { x: Int }
                data R = { z: Int }
                behavior use : (a: A) -> R
                    constructs R
                let use (a) = someJavaMethod(a)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1023", e.code(), e.getMessage());
    }

    /**
     * Both ways a `depends on` can be wrong are reported at the name it writes: one clause, one place
     * to look. The unknown one is answered where the module's names are resolved and the implemented
     * one where the injection targets are known, so the two would otherwise drift apart.
     */
    @Test
    void bothKindsOfBadDependsOnArePointedAtTheName() {
        String implemented = """
                module demo
                data A = { x: Int }
                data R = { z: Int }

                behavior one : (a: A) -> R constructs R
                let one (a) = R { z = a.x }

                behavior use : (a: A) -> R
                    depends on one
                let use (a, one) = one(a)
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(implemented));
        assertEquals("E1607", e.code(), e.getMessage());
        assertTrue(e.getMessage().startsWith("9:16 "),
                "at the `one` of `depends on one`: " + e.getMessage());

        String unknown = implemented.replace("depends on one", "depends on nosuch")
                .replace("let use (a, one) = one(a)", "let use (a, nosuch) = nosuch(a)");
        CompileException absent = assertThrows(CompileException.class, () -> Compiler.compile(unknown));
        assertEquals("E1607", absent.code(), absent.getMessage());
        assertTrue(absent.getMessage().startsWith("9:16 "),
                "and at the `nosuch` of `depends on nosuch`: " + absent.getMessage());
    }

    /**
     * Two entries of one clause go by different bare names, whichever modules declare them.
     *
     * <p>They are two behaviors and the clause is refused all the same, because what it decides is
     * the implementing {@code let}'s trailing parameters and each is named after the entry it takes
     * (spec §depends-on). A pair sharing a spelling leaves no {@code let} that could be written, so
     * comparing the declarations rather than the names would admit a behavior nobody can implement.
     */
    @Test
    void twoDependenciesOfOneBareNameAreRefusedHoweverTheyAreWritten() {
        CompileException refused = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of("""
                        module probe.up exposing ( N, lookup )
                        data N = Int
                        behavior lookup : (n: N) -> N
                        """, """
                        module probe.other exposing ( M, lookup )
                        data M = Int
                        behavior lookup : (m: M) -> M
                        """, """
                        module probe.down
                        import probe.up as Up ( N )
                        import probe.other as Other ( M )

                        data Out = { n: N }

                        behavior use : (n: N, m: M) -> Out
                            constructs Out
                            depends on Up.lookup, Other.lookup

                        let use (n, m, lookup, lookup) = Out { n = lookup(n) }
                        """), souther.compiler.meta.ModulePath.EMPTY));

        assertEquals("E1011", refused.code(), refused.getMessage());
        assertTrue(refused.getMessage().contains("lookup"),
                "the spelling the two would take is the one named: " + refused.getMessage());
    }
}
