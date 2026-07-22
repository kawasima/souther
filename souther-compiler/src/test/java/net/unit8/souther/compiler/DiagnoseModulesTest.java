package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.Diagnostic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link Compiler#diagnoseModules} links a module set the way {@link Compiler#compileModules} does,
 * but collects diagnostics per source (keyed by the caller's id) instead of throwing on the first
 * error — the entry point the LSP uses to publish diagnostics per file across a workspace.
 */
class DiagnoseModulesTest {

    private static final String A = """
            module a exposing ( N )
            data N = { v: Int }
            """;

    // b imports a and carries a failing inline example (E1905): its declared output does not match
    // what the identity `f` returns.
    private static final String B_FAILING_EXAMPLE = """
            module b
            import a ( N )
            data M = { n: Int }
            behavior f : (x: M) -> M
            let f (x) = x
            example f
              | (M { n = 1 }) -> M { n = 2 }
            """;

    @Test
    void collectsEverySemanticErrorInAModuleNotJustTheFirst() {
        // two behaviors, each with an unbound name in its body — independent errors in separate defs
        String m = """
                module m
                data N = { v: Int }
                behavior f : (n: N) -> N
                let f (n) = bogusOne
                behavior g : (n: N) -> N
                let g (n) = bogusTwo
                """;
        Map<String, List<Diagnostic>> diags = Compiler.diagnoseModules(Map.of("m", m));
        assertEquals(2, diags.get("m").size(), diags.get("m").toString());
    }

    @Test
    void attributesAFailingExampleToTheModuleThatHasIt() {
        Map<String, List<Diagnostic>> diags = Compiler.diagnoseModules(Map.of("a", A, "b", B_FAILING_EXAMPLE));
        assertEquals(List.of(), diags.get("a"), "the clean imported module has no diagnostics");
        assertEquals(1, diags.get("b").size(), diags.get("b").toString());
        assertEquals("E1905", diags.get("b").get(0).code());
    }

    @Test
    void aCleanModuleSetYieldsEmptyDiagnosticsForEveryModule() {
        String bClean = """
                module b
                import a ( N )
                data M = { n: Int }
                behavior f : (x: M) -> M
                let f (x) = x
                """;
        Map<String, List<Diagnostic>> diags = Compiler.diagnoseModules(Map.of("a", A, "b", bClean));
        assertEquals(List.of(), diags.get("a"));
        assertEquals(List.of(), diags.get("b"));
    }

    @Test
    void skipsAModuleDownstreamOfAFailedImport() {
        // a is broken (a field with an unknown type); b imports a. b must not produce a cascade
        // diagnostic — the error belongs to a.
        String aBroken = """
                module a exposing ( N )
                data N = { v: Nope }
                """;
        String b = """
                module b
                import a ( N )
                behavior g : (n: N) -> N
                let g (n) = n
                """;
        Map<String, List<Diagnostic>> diags = Compiler.diagnoseModules(Map.of("a", aBroken, "b", b));
        assertFalse(diags.get("a").isEmpty(), "the broken module reports its own error");
        assertEquals(List.of(), diags.get("b"), "the importing module is skipped, not cascaded");
    }

    @Test
    void attributesAnExamplesForFilesFailureToThatFile() {
        String a = """
                module a exposing ( M, f )
                data M = { n: Int }
                behavior f : (x: M) -> M
                let f (x) = x
                """;
        // a separate `examples for a` file carries a failing example
        String aExamples = """
                examples for a
                example f
                  | (M { n = 1 }) -> M { n = 2 }
                """;
        Map<String, List<Diagnostic>> diags =
                Compiler.diagnoseModules(Map.of("a.sou", a, "a.examples.sou", aExamples));

        assertEquals(List.of(), diags.get("a.sou"), "the module file itself is clean");
        assertEquals(1, diags.get("a.examples.sou").size(), diags.get("a.examples.sou").toString());
        assertEquals("E1905", diags.get("a.examples.sou").get(0).code());
    }
}
