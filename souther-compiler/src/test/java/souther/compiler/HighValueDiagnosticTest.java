package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.CompileException;

import souther.compiler.types.Type;
import souther.compiler.diag.Diagnostic;

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
        assertEquals("N", Type.show(Type.ref(souther.compiler.types.TypeSymbols.declared(new souther.compiler.types.TypeKey("demo", "N")))));
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
        assertEquals("check.type.mismatch.title", d.titleKey());
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
        assertEquals("amount", d.repair().with());
    }

    @Test
    void foldOverEmptySeedWithNoContextPointsAtTheSeed() {
        // No expected type reaches the fold: its value is bound to a local the source left
        // un-annotated, so the accumulator's value type is genuinely unknown. The error must point at
        // the empty seed and say how to type it — annotate the binding (issue #71) or move the fold
        // into a typed position — not at the arithmetic deep inside the inlined Map.updateOrInsert (issue #70,
        // the misleading-location half).
        Diagnostic d = diagnosticOf("""
                module demo
                import List ( fold )
                data In = { keys: List<String> }
                data Out = { m: Map<String, Int> }
                behavior run : (i: In) -> Out constructs Out
                let run (i) = {
                    let counts = fold((acc, k) -> Map.updateOrInsert(k, 1, n -> n + 1, acc), Map.empty, i.keys)
                    Out { m = counts }
                }
                """);
        assertEquals("check.fold.seed.title", d.titleKey());
        // the primary caret is on the Map.empty seed (line 7), not the `+` inside upsert
        assertEquals(7, ((Primary.InSource) d.primary()).place().region().start().line());
    }

    @Test
    void anUnrelatedErrorInAFoldStepIsNotReattributedToTheSeed() {
        // The step over a Map.empty seed references an undefined identifier `bogus`. Even though the
        // accumulator is a bottom, the failure is NOT the unresolved-bottom error, so it must surface
        // as the unknown-identifier error at `bogus`, not be masked by the seed-inference message.
        Diagnostic d = diagnosticOf("""
                module demo
                import List ( fold )
                data In = { keys: List<String> }
                data Out = { m: Map<String, Int> }
                behavior run : (i: In) -> Out constructs Out
                let run (i) = {
                    let counts = fold((acc, k) -> Map.updateOrInsert(k, 1, n -> bogus, acc), Map.empty, i.keys)
                    Out { m = counts }
                }
                """);
        assertEquals("check.unknown.title", d.titleKey());
    }

    @Test
    void aMapOverEmptyLiteralIsNotReattributedToAFoldSeed() {
        // `List.map` over an empty literal cannot infer its element type, so its closure fails on the
        // bottom-typed parameter. `List.map` inlines to a `List.foldFrom` whose accumulator seed is
        // the combinator's own internal `[]`, stamped onto the call site — not a seed the caller
        // wrote. The failure must surface as its own step error, not a misleading `check.fold.seed.*`
        // naming `List.foldFrom`, a helper the user never called (issue #70 review).
        Diagnostic d = diagnosticOf("""
                module demo
                data Out = { xs: List<Int> }
                behavior run : (i: Int) -> Out constructs Out
                let run (i) = {
                    let ys = List.map(n -> n + 1, [])
                    Out { xs = ys }
                }
                """);
        assertEquals("check.type.mismatch.title", d.titleKey(),
                "a non-fold combinator over [] surfaces its own step error, not a fold-seed message");
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
                behavior pick : (s: S) -> A | B | C
                let pick (s) = match s with
                    | A as a -> a
                """);
        assertEquals("E1201", d.code());
        assertTrue(d.notes().stream().anyMatch(n -> n.said() instanceof DeclarationMessage.AddACaseFor),
                "the missing cases should be listed in a hint");
    }
}
