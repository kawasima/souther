package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code souther} namespace is reserved for the shipped core (ADR-0028). A user module cannot
 * declare itself inside it, or it could grant itself the core's privileges (generics, recursion,
 * intrinsics). Names that merely begin with the letters "souther" but are not under the dotted
 * namespace are ordinary user names.
 */
class CompileReservedNamespaceTest {

    @Test
    void aModuleUnderTheReservedNamespaceIsRejected() {
        String src = """
                module souther.list
                data X = Int
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("reserved"), e.getMessage());
    }

    @Test
    void theBareReservedNameIsRejected() {
        String src = """
                module souther
                data X = Int
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }

    @Test
    void aModuleNamedAfterAStdlibQualifierIsRejected() {
        // `List` is the qualifier the standard library is reached through (`List.map`,
        // `import List ( ... )`); a user module by that name would shadow it and be unimportable.
        String src = """
                module List
                data X = Int
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("qualifier"), e.getMessage());
    }

    @Test
    void aNameThatMerelyStartsWithSoutherIsAllowed() {
        // `southernmost` is not under the `souther.` namespace — it is an ordinary user module.
        String src = """
                module southernmost
                data X = Int
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }
}
