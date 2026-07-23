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

    @Test
    void treeForestMutualRecursionThroughAFoldClosureIsTotal() {
        // `sumTree` hands a strictly smaller `t.children` to `sumForest`; `sumForest` hands each
        // element (a strictly smaller part of that list) back to `sumTree` inside the fold closure.
        // Every cycle strictly decreases, so the group is size-change terminating.
        String src = """
                module demo
                data Tree = { children: List<Tree>, label: Int }
                data Out = Int
                behavior run : (t: Tree) -> Out constructs Out
                let sumTree (t: Tree): Int = t.label + sumForest(t.children)
                let sumForest (ts: List<Tree>): Int = List.fold((acc, x) -> acc + sumTree(x), 0, ts)
                let run (t) = Out(sumTree(t))
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }

    @Test
    void mutualRecursionPassingASmallerPartBothWaysIsTotal() {
        // `f` and `g` each unwrap the other's `.c` (a strictly smaller `T`) and recurse — both
        // directions decrease, so the cycle terminates.
        String src = """
                module demo
                data T = { c: T?, n: Int }
                data Out = Int
                behavior run : (t: T) -> Out constructs Out
                let f (t: T): Int = match t.c with | Some x -> g(x) | None -> t.n
                let g (t: T): Int = match t.c with | Some x -> f(x) | None -> t.n
                let run (t) = Out(f(t))
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }

    @Test
    void mutualRecursionCarryingOneArgWhileTheOtherShrinksIsTotal() {
        // `f` keeps `a` and shrinks `b`; `g` shrinks `a` and keeps `b`. No single position decreases
        // in every call, but the composed cycle `f;g` strictly decreases on both — only full
        // size-change termination (not a single-decreasing-position check) accepts this.
        String src = """
                module demo
                data T = { c: T?, n: Int }
                data Out = Int
                behavior run : (t: T) -> Out constructs Out
                let f (a: T, b: T): Int = match b.c with | Some bc -> g(a, bc) | None -> a.n
                let g (a: T, b: T): Int = match a.c with | Some ac -> f(ac, b) | None -> b.n
                let run (t) = Out(f(t, t))
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }

    @Test
    void mutualRecursionWithNoDescentIsRejected() {
        // `ping`/`pong` pass the whole parameter back and forth — no argument decreases, so the group
        // is not size-change terminating and is rejected.
        String src = """
                module demo
                data T = { n: Int }
                data Out = Int
                behavior run : (t: T) -> Out constructs Out
                let ping (t: T): Int = pong(t)
                let pong (t: T): Int = ping(t)
                let run (t) = Out(ping(t))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue("E2001".equals(ex.code()), "expected E2001, got " + ex.code() + ": " + ex.getMessage());
        assertTrue(ex.getMessage().contains("size-change")
                && ex.getMessage().contains("ping") && ex.getMessage().contains("pong"), ex.getMessage());
    }

    @Test
    void numericMutualRecursionIsRejectedUnlessAMemberIsPartial() {
        // `isEven`/`isOdd` recurse on `n - 1`, a computed value that is not a structural sub-term
        // (Souther has no inductive Nat), so the group is rejected.
        String src = """
                module demo
                data N = Int
                data Out = Int
                behavior run : (n: N) -> Out constructs Out
                let isEven (n: Int): Int = if n == 0 then 1 else isOdd(n - 1)
                let isOdd (n: Int): Int = if n == 0 then 0 else isEven(n - 1)
                let run (n) = Out(isEven(n.value))
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue("E2001".equals(ex.code()), "expected E2001, got " + ex.code() + ": " + ex.getMessage());
    }

    @Test
    void markingOneMemberPartialOptsTheWholeMutualGroupOut() {
        // Marking `isOdd` partial opts the whole cycle out of the totality check — `isEven`, sharing
        // the cycle, is not independently certified either.
        String src = """
                module demo
                data N = Int
                data Out = Int
                behavior run : (n: N) -> Out constructs Out
                let isEven (n: Int): Int = if n == 0 then 1 else isOdd(n - 1)
                partial let isOdd (n: Int): Int = if n == 0 then 0 else isEven(n - 1)
                let run (n) = Out(isEven(n.value))
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }

    @Test
    void mutualRecursionCarryingAParamThroughALetAliasIsTotal() {
        // `f` binds `a2 = a` and carries it unchanged into `g` while shrinking `b`; the `=` relation
        // must survive the `let` alias or the cycle would be wrongly rejected though it terminates.
        String src = """
                module demo
                data T = { c: T?, n: Int }
                data Out = Int
                behavior run : (t: T) -> Out constructs Out
                let f (a: T, b: T): Int = {
                    let a2 = a
                    match b.c with | Some bc -> g(a2, bc) | None -> a2.n
                }
                let g (a: T, b: T): Int = match a.c with | Some ac -> f(ac, b) | None -> b.n
                let run (t) = Out(f(t, t))
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }

    @Test
    void structuralLexicographicSelfRecursionIsTotal() {
        // `ack` decreases the first argument in one call and the second in the other — no single
        // position decreases in every call, so the old single-position check rejected it. Full
        // size-change termination accepts it: every idempotent cycle carries a strict decrease.
        String src = """
                module demo
                data T = { c: T?, n: Int }
                data Out = Int
                behavior run : (t: T) -> Out constructs Out
                let ack (m: T, n: T): Int =
                    match m.c with
                        | None -> n.n
                        | Some mc ->
                            match n.c with
                                | None -> ack(mc, m)
                                | Some nc -> ack(m, nc)
                let run (t) = Out(ack(t, t))
                """;
        assertDoesNotThrow(() -> Compiler.compile(src));
    }
}
