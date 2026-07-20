package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.diag.Diagnostic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The high-value diagnostics rewritten Elm-style: found-vs-expected type blocks, secondary regions
 * for the two branches of a disagreeing {@code if}, and a non-exhaustive match that lists every
 * missing case. */
class HighValueDiagnosticTest {

    private static Diagnostic diagnosticOf(String src) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        return e.diagnostic();
    }

    @Test
    void typeShowReadsLikeSourceSyntax() {
        assertEquals("Int", Type.show(Type.INT));
        assertEquals("Bool", Type.show(Type.BOOL));
        assertEquals("List<Int>", Type.show(Type.list(Type.INT)));
        assertEquals("N", Type.show(Type.ref("N")));
        assertEquals("Int?", Type.show(Type.option(Type.INT)));
    }

    @Test
    void typeMismatchCarriesAFoundExpectedDiff() {
        Diagnostic d = diagnosticOf("""
                module demo
                data N = Int
                behavior f : (n: N) -> N constructs N
                let f (n) = if n then N { value = 1 } else N { value = 2 }
                """);
        assertNotNull(d.diff(), "expected a found/expected diff on an if-condition type error");
        assertEquals("N", d.diff().actualType());
        assertEquals("Bool", d.diff().expectedType());
        assertEquals("check.type.mismatch.title", d.titleKey());
    }

    @Test
    void ifBranchDisagreementPointsAtBothBranches() {
        Diagnostic d = diagnosticOf("""
                module demo
                data Out = Int
                behavior bad : (x: Int) -> Out constructs Out
                let bad (x) = Out { value = if x >= 0 then 1 else "no" }
                """);
        assertEquals(2, d.secondary().size(), "the then and else branches are each pointed at");
        assertNull(d.diff());
        assertEquals("check.if.title", d.titleKey());
    }

    @Test
    void unknownIdentifierCarriesADidYouMeanSuggestion() {
        Diagnostic d = diagnosticOf("""
                module demo
                data N = Int
                behavior f : (amount: N) -> N constructs N
                let f (amount) = N { value = amont }
                """);
        assertEquals("check.unknown.title", d.titleKey());
        assertEquals("amount", d.suggestion());
    }

    @Test
    void concatMismatchPointsAtBothOperands() {
        Diagnostic d = diagnosticOf("""
                module demo
                data N = Int
                behavior f : (n: N) -> N constructs N
                let f (n) = N { value = 1 ++ 2 }
                """);
        assertEquals(2, d.secondary().size());
    }

    @Test
    void compositionMismatchIsE1701WithAFoundExpectedDiff() {
        Diagnostic d = diagnosticOf("""
                module demo
                data A
                data B
                data C
                behavior f : (a: A) -> B
                behavior g : (c: C) -> C
                behavior p = f >-> g
                """);
        assertEquals("E1701", d.code());
        assertNotNull(d.diff());
        assertEquals("B", d.diff().actualType());
        assertEquals("C", d.diff().expectedType());
    }

    @Test
    void nonExhaustiveMatchIsE1201AndListsMissingCases() {
        Diagnostic d = diagnosticOf("""
                module demo
                data A
                data B
                data C
                data S = A | B | C
                behavior pick : (s: S) -> A | B | C constructs A
                let pick (s) = match s with
                    | A as a -> a
                """);
        assertEquals("E1201", d.code());
        assertTrue(d.notes().stream().anyMatch(n -> "e1201.hint".equals(n.messageKey())),
                "the missing cases should be listed in a hint");
    }
}
