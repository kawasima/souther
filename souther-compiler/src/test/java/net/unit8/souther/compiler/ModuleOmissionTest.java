package net.unit8.souther.compiler;

import net.unit8.souther.compiler.syntax.Parser;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A single-file compilation unit may omit its {@code module} header (F#/Elm treat a header-less
 * single file as an implicit module). The name defaults to the caller-supplied one — the file stem,
 * or {@code Main} through the string API. A module linked by imports must still be named.
 */
class ModuleOmissionTest {

    private static final String HEADERLESS = """
            behavior greet : (name: String) -> String
            let greet (name) = String.concat("Hello, ", name)
            """;

    @Test
    void parseDefaultsAHeaderlessModuleToMain() {
        assertEquals("Main", Parser.parse(HEADERLESS).name());
    }

    @Test
    void parseUsesTheGivenDefaultNameWhenTheHeaderIsOmitted() {
        assertEquals("hello", Parser.parse(HEADERLESS, "hello").name());
    }

    @Test
    void anExplicitHeaderStillWins() {
        assertEquals("demo", Parser.parse("module demo\n" + HEADERLESS, "ignored").name());
    }

    @Test
    void compileNamesTheGeneratedClassesAfterTheDefault() {
        Map<String, byte[]> classes = Compiler.compile(HEADERLESS, "hello");
        assertTrue(classes.containsKey("hello.Greet"), classes.keySet().toString());
    }

    @Test
    void compileModulesRejectsAHeaderlessSource() {
        assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(HEADERLESS)));
    }

    @Test
    void aNullDefaultMakesTheHeaderRequired() {
        assertThrows(RuntimeException.class, () -> Parser.parse(HEADERLESS, null));
    }
}
