package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recursion is total by default (spec §fn-declaration): a recursive helper must be structurally
 * recursive — every recursive call passes a part of an argument obtained by {@code match} (a field
 * or a case), which is strictly smaller, so it bottoms out. A helper that recurses on a computed
 * value instead (numeric {@code n - 1}, an index {@code i + 1}) is not structural and must be marked
 * {@code partial} to opt out of the check. The stdlib {@code List.foldFrom} is trusted total.
 */
class CompileTotalityTest {

    // Walking a manager chain to the root: `b` is `s.上司` unwrapped, a strictly smaller `社員`.
    private static final String DEPTH = """
            module demo
            data 社員 = { 上司: 社員?, 氏名: String }
            data Depth = Int
            behavior measure : (e: 社員) -> Depth constructs Depth
            let 深さ (s: 社員): Int =
                match s.上司 with
                    | Some b -> 深さ(b) + 1
                    | None -> 1
            let measure (e) = Depth(深さ(e))
            """;

    @Test
    void aStructurallyRecursiveHelperIsTotalByDefault() {
        assertDoesNotThrow(() -> Compiler.compile(DEPTH));
    }

    @Test
    void aNumericRecursiveHelperIsRejectedUnlessPartial() {
        String src = """
                module demo
                data N = Int
                data Out = Int
                behavior run : (n: N) -> Out constructs Out
                let count (n: Int): Int = if n == 0 then 0 else count(n - 1) + 1
                let run (n) = Out(count(n.value))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("count") && ex.getMessage().contains("structural"),
                ex.getMessage());
    }

    @Test
    void aNumericRecursiveHelperCompilesWhenMarkedPartial() {
        String src = """
                module demo
                data N = Int
                data Out = Int
                behavior run : (n: N) -> Out constructs Out
                partial let count (n: Int): Int = if n == 0 then 0 else count(n - 1) + 1
                let run (n) = Out(count(n.value))
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }

    @Test
    void anIndexRecursiveHelperIsRejectedUnlessPartial() {
        String src = """
                module demo
                data Bag = { xs: List<Int> }
                data Out = Int
                behavior run : (b: Bag) -> Out constructs Out
                let sumFrom (acc: Int, xs: List<Int>, i: Int): Int =
                    match List.get(i, xs) with
                        | Some x -> sumFrom(acc + x, xs, i + 1)
                        | None -> acc
                let run (b) = Out(sumFrom(0, b.xs, 0))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("sumFrom") && ex.getMessage().contains("structural"),
                ex.getMessage());
    }

    @Test
    void aNonTerminatingPartialRecursionInAnExampleIsBoundedNotHung() {
        // `partial` opts out of the totality check, so `spin` may loop forever. An example that reaches
        // it must not hang the compiler: the evaluation is bounded and reported (E1910), not a hang.
        String src = """
                module demo
                data N = Int
                data Out = Int
                partial let spin (n: Int): Int = spin(n)
                behavior run : (n: N) -> Out constructs Out
                let run (n) = Out(spin(n.value))
                example run
                  | "loops" : (N(1)) -> Out(0)
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue("E1910".equals(ex.code()), "expected E1910 (non-termination), got " + ex.code()
                + ": " + ex.getMessage());
    }

    @Test
    void anNaryTreeRecursingThroughAFoldClosureIsTotal() {
        // Walking a `List<Tree>` of children: each `c` is an element of `t.children`, a strictly
        // smaller part of `t`, so recursing on it inside the fold's closure is structural.
        String src = """
                module demo
                data Tree = { children: List<Tree>, label: Int }
                data Out = Int
                behavior run : (t: Tree) -> Out constructs Out
                let total (t: Tree): Int = List.fold((acc, c) -> acc + total(c), t.label, t.children)
                let run (t) = Out(total(t))
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }

    @Test
    void recursingOnAParameterInsideAFoldClosureIsStillRejected() {
        // The closure binds `c` as smaller, but the recursion passes `t` (the whole parameter),
        // which does not decrease — must stay rejected even though it is inside a combinator.
        String src = """
                module demo
                data Tree = { children: List<Tree>, label: Int }
                data Out = Int
                behavior run : (t: Tree) -> Out constructs Out
                let bad (t: Tree): Int = List.fold((acc, c) -> bad(t), t.label, t.children)
                let run (t) = Out(bad(t))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("bad") && ex.getMessage().contains("structural"),
                ex.getMessage());
    }

    @Test
    void aStackOverflowingPartialRecursionInAnExampleIsReportedAsNonTermination() {
        // A non-tail `partial` recursion overflows the stack rather than tail-looping; it must be
        // reported as a non-termination (E1910), the same as a runaway loop, not a generic failure.
        String src = """
                module demo
                data N = Int
                data Out = Int
                partial let deep (n: Int): Int = deep(n) + 1
                behavior run : (n: N) -> Out constructs Out
                let run (n) = Out(deep(n.value))
                example run
                  | "overflows" : (N(1)) -> Out(0)
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue("E1910".equals(ex.code()), "expected E1910, got " + ex.code() + ": " + ex.getMessage());
    }

    @Test
    void foldDerivedCombinatorsStayTotal() {
        // `List.fold` is `List.foldFrom` (trusted total); a behavior folding a list is unaffected.
        String src = """
                module demo
                data Bag = { xs: List<Int> }
                data Out = Int
                behavior run : (b: Bag) -> Out constructs Out
                let run (b) = Out(List.fold((acc, x) -> acc + x, 0, b.xs))
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }
}
