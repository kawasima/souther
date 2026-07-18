package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * On an fn-backed behavior, {@code constructs} is optional (spec 12.3, ADR-0002): its construction
 * permission is internal — invisible to callers, unlike {@code requires} — so with the body visible
 * the set can be inferred. Omit it and inference stands; declare it and it must match the body
 * exactly (under-declaration E1002, over-declaration E1006). Injected behaviors still declare it.
 */
class CompileConstructsOptionalTest {

    private static final String BASE = """
            module demo
            import String { contains }
            data Email = String
                invariant contains(value, "@")
            data Member = { email: Email }
            data Audit = { at: String }
            """;

    @Test
    void omittingConstructsInfersAndCompiles() {
        // no `constructs` clause, yet the body builds Member — inferred, so it compiles
        assertDoesNotThrow(() -> Compiler.compile(BASE + """
                behavior make : (e: Email) -> Member
                let make (e) = Member { email = e }
                """));
    }

    @Test
    void aDeclaredConstructsMustBeComplete_E1002() {
        // builds Member and Audit but declares only Member — the missing Audit is E1002
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(BASE + """
                behavior make : (e: Email) -> Member | Audit constructs Member
                let make (e) = if contains(e.value, "x") then Audit { at = "now" } else Member { email = e }
                """));
        assertEquals("E1002", e.code());
    }

    @Test
    void anOverDeclaredConstructsIs_E1006() {
        // passes its input Member through but declares `constructs Member` — over-declaration is E1006
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(BASE + """
                behavior pass : (m: Member) -> Member constructs Member
                let pass (m) = m
                """));
        assertEquals("E1006", e.code());
    }

    @Test
    void anExactDeclarationCompiles() {
        assertDoesNotThrow(() -> Compiler.compile(BASE + """
                behavior make : (e: Email) -> Member constructs Member
                let make (e) = Member { email = e }
                """));
    }
}
