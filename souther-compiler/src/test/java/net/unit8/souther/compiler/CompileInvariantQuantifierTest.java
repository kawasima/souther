package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fold-derived list quantifier (`List.all` / `any` / `member` / `distinct`) may appear in an
 * invariant. After fold de-privileging (ADR-0051) each derives from the recursive helper
 * `List.foldFrom`, which is total (ADR-0052): it terminates on the finite list. The invariant
 * termination rule bars only `partial` recursion, not a total helper (spec §invariant-expressions).
 * Construction inside an invariant stays forbidden — including inside a quantifier's closure.
 */
class CompileInvariantQuantifierTest {

    // The spec §invariant-expressions example, with the stdlib `all(p, xs)` argument order.
    private static final String CART = """
            module demo
            let 正の数 (v: Int): Bool = v >= 1
            data 明細 = { 数量: Int }
            data カゴ = { items: List<明細> } invariant List.all(x -> 正の数(x.数量), items)
            """;

    private Decoder<Object, ?> cartDecoder() throws Exception {
        ClassLoader loader = new BytesClassLoader(Compiler.compile(CART), getClass().getClassLoader());
        return Codecs.decoder(loader, "demo.カゴ");
    }

    @Test
    void allInInvariantCompilesAndCanSucceed() throws Exception {
        Result<?> r = cartDecoder().decode(
                Map.of("items", List.of(Map.of("数量", 2L), Map.of("数量", 1L))), Path.ROOT);
        assertTrue(r instanceof Ok, "every 数量 >= 1, so the invariant holds and decoding succeeds");
    }

    @Test
    void allInInvariantRejectsAViolatingElement() throws Exception {
        Result<?> r = cartDecoder().decode(
                Map.of("items", List.of(Map.of("数量", 2L), Map.of("数量", 0L))), Path.ROOT);
        assertTrue(r instanceof Err, "one 数量 is 0, so the all() invariant must fail");
    }

    @Test
    void issue49ExactModuleCompiles() throws Exception {
        // The module reported in Issue #49, verbatim: a bare `all` imported from List, in an invariant.
        Compiler.compile("""
                module p
                import List ( all )
                data 明細 = { 数量: Int }
                data カゴ = { items: List<明細> } invariant all(i -> i.数量 >= 1, items)
                """);
    }

    @Test
    void anyMemberDistinctInInvariantAllCompile() throws Exception {
        // any
        Compiler.compile("""
                module demo
                data 明細 = { 数量: Int }
                data カゴ = { items: List<明細> } invariant List.any(x -> x.数量 >= 1, items)
                """);
        // member
        Compiler.compile("""
                module demo
                data カゴ = { skus: List<String> } invariant List.member("x", skus)
                """);
        // distinct — no duplicate SKUs
        Compiler.compile("""
                module demo
                data カゴ = { skus: List<String> }
                    invariant List.length(List.distinct(skus)) == List.length(skus)
                """);
    }

    @Test
    void aTotalUserRecursiveHelperMayAppearInAnInvariant() throws Exception {
        // 深さ recurses structurally on an Option field, so the totality checker proves it total; a
        // total helper is admissible in an invariant even though it is recursive.
        String src = """
                module demo
                data 木 = { 子: Option<木> }
                let 深さ (t: 木): Int = match t.子 with
                    | Some c -> 深さ(c) + 1
                    | None -> 0
                data 制限木 = { root: 木 } invariant 深さ(root) >= 0
                """;
        Compiler.compile(src);
    }

    @Test
    void aFieldShadowsASameNamedHelperInItsOwnInvariant() throws Exception {
        // A bare name in an invariant is a field reference. When a field shares a name with a total
        // recursive helper, the field wins — `深さ` here is the Int field, not the helper.
        String src = """
                module demo
                data 木 = { 子: Option<木> }
                let 深さ (t: 木): Int = match t.子 with
                    | Some c -> 深さ(c) + 1
                    | None -> 0
                data 記録 = { 深さ: Int } invariant 深さ >= 0
                """;
        Compiler.compile(src);
    }

    @Test
    void aTotalHelperReferencedAsABareValueInAnInvariantIsRejected() {
        // Putting recursive-helper signatures in the invariant's type environment lets a call resolve,
        // but a recursive helper is a static method, not a first-class value. Referencing one as a bare
        // value (not in call position) is rejected, exactly as in a behavior body — it does not slip
        // through to produce unemittable code.
        String src = """
                module demo
                data 木 = { 子: Option<木> }
                let 深さ (t: 木): Int = match t.子 with
                    | Some c -> 深さ(c) + 1
                    | None -> 0
                data X = { root: 木 } invariant {
                    let g = 深さ
                    g(root) >= 0
                }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }

    @Test
    void aQuantifierInvariantCompilesOnTheMultiModulePath() throws Exception {
        // The multi-module path (Compiler.compileModules) must inject `List.foldFrom` for a data whose
        // invariant reaches it, the same as the single-module path does.
        String goods = """
                module goods
                data 明細 = { 数量: Int }
                data カゴ = { items: List<明細> } invariant List.all(x -> x.数量 >= 1, items)
                """;
        String other = """
                module other
                data X = Int invariant value >= 0
                """;
        Compiler.compileModules(List.of(goods, other));
    }

    @Test
    void aPartialRecursiveHelperIsStillRejected() {
        String src = """
                module demo
                partial let count (n: Int): Int = if n == 0 then 0 else count(n - 1) + 1
                data X = Int
                    invariant count(value) < 100
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("count") && ex.getMessage().contains("partial"),
                ex.getMessage());
    }

    @Test
    void constructingDataDirectlyInAnInvariantIsRejected() {
        String src = """
                module demo
                data Wrapper = { ok: Bool }
                data X = Int invariant {
                    let w = Wrapper { ok = value >= 0 }
                    w.ok
                }
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("Wrapper") || ex.getMessage().contains("construct"),
                ex.getMessage());
    }

    @Test
    void constructingDataInsideAQuantifierClosureIsRejected() {
        String src = """
                module demo
                data Made = { ok: Bool }
                data 明細 = { 数量: Int }
                data カゴ = { items: List<明細> }
                    invariant List.all(x -> {
                        let m = Made { ok = x.数量 >= 1 }
                        m.ok
                    }, items)
                """;
        CompileException ex = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(ex.getMessage().contains("Made") || ex.getMessage().contains("construct"),
                ex.getMessage());
    }
}
