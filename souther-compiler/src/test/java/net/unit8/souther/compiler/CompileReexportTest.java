package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code exposing} lists a module's own definitions. A name it merely imports is not re-exported —
 * an importer reaches that name from its declaring module, not second-hand. Exposing an imported
 * name is rejected at the module that tries it, with a message that says the name is imported rather
 * than the misleading "not defined".
 */
class CompileReexportTest {

    @Test
    void exposingAnImportedNameIsRejected() {
        String a = """
                module a exposing { N }
                data N = Int
                """;
        String b = """
                module b exposing { N }
                import a { N }
                """;
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(a, b)));
        assertTrue(e.getMessage().contains("imported"), e.getMessage());
    }

    @Test
    void exposingOwnDefinitionsIsAccepted() {
        String a = """
                module a exposing { N }
                data N = Int
                """;
        String b = """
                module b exposing { M }
                import a { N }
                data M = { n: N }
                """;
        // b exposes its own M — which references the imported N — and that compiles.
        assertTrue(Compiler.compileModules(List.of(a, b)).containsKey("b.M"));
    }
}
