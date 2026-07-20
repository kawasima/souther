package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Compile-time evaluation of {@code example}s: an example that holds compiles, one that
 * does not fails the build with {@code E1905}, and the surrounding diagnostics ({@code E1902},
 * {@code E1904}, attached-file {@code E1906}/{@code E1907}) are exercised.
 */
class CompileExampleTest {

    private static final String BASE = """
            module example.businesstrip
            import String ( length )

            data 従業員ID = String
                invariant length(value) > 0

            data 金額 = Int
                invariant value >= 0

            data 出張申請共通項目 = {
                申請者: 従業員ID
                , 予定費用: 金額
            }

            data 申請準備中 = { ...出張申請共通項目 }
            data 提出済み = { ...出張申請共通項目, 提出日時: String }
            data 却下 = { 理由: String }

            behavior 提出する : (申請: 申請準備中, 提出日時: String) -> 提出済み | 却下
                constructs 提出済み, 却下

            let 提出する (申請, 提出日時) = {
                require 申請.予定費用.value <= 100000 else 却下 { 理由 = "high_cost" }
                提出済み { ...申請, 提出日時 = 提出日時 }
            }
            """;

    private static CompileException err(String model) {
        return assertThrows(CompileException.class, () -> Compiler.compile(model));
    }

    @Test
    void holdingExamplesCompile() {
        String model = BASE + """
                example 提出する
                  | "within budget" : (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(50000) }, "2026-07-14") -> 提出済み
                  | "over budget"   : (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(200000) }, "2026-07-14") -> 却下 { 理由 = "high_cost" }
                """;
        assertDoesNotThrow(() -> Compiler.compile(model));
    }

    @Test
    void armOnlyExpectedChecksTheCase() {
        String bad = BASE + """
                example 提出する
                  | (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(200000) }, "2026-07-14") -> 提出済み
                """;
        assertEquals("E1905", err(bad).diagnostic().code());
    }

    @Test
    void fullValueExpectedChecksEveryField() {
        String bad = BASE + """
                example 提出する
                  | (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(200000) }, "2026-07-14") -> 却下 { 理由 = "wrong_reason" }
                """;
        assertEquals("E1905", err(bad).diagnostic().code());
    }

    @Test
    void expectedArmNotInOutputIsE1904() {
        String bad = BASE + """
                example 提出する
                  | (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(50000) }, "2026-07-14") -> 申請準備中
                """;
        assertEquals("E1904", err(bad).diagnostic().code());
    }

    @Test
    void injectedTargetIsNotEvaluableE1902() {
        String model = BASE + """
                behavior 現在時刻 : () -> String

                example 現在時刻
                  | () -> 提出済み
                """;
        assertEquals("E1902", err(model).diagnostic().code());
    }

    @Test
    void unknownTargetIsE1901() {
        String bad = BASE + """
                example 存在しない
                  | (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(50000) }, "2026-07-14") -> 提出済み
                """;
        assertEquals("E1901", err(bad).diagnostic().code());
    }

    @Test
    void reportsMoreThanOneFailure() {
        String bad = BASE + """
                example 提出する
                  | (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(200000) }, "2026-07-14") -> 提出済み
                  | (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(300000) }, "2026-07-14") -> 提出済み
                """;
        // Both rows fail; the build reports the aggregate (E1905) mentioning the extra failure.
        CompileException e = err(bad);
        assertEquals("E1905", e.diagnostic().code());
    }

    @Test
    void attachedExampleFileMergesIntoTarget() {
        String base = BASE;   // module example.businesstrip
        String attached = """
                examples for example.businesstrip

                example 提出する
                  | (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(200000) }, "2026-07-14") -> 提出済み
                """;
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(java.util.List.of(base, attached)));
        assertEquals("E1905", e.diagnostic().code());
    }

    @Test
    void attachedFileWithUnknownTargetIsE1907() {
        String base = BASE;
        String attached = """
                examples for example.nope

                example 提出する
                  | (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(50000) }, "2026-07-14") -> 提出済み
                """;
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(java.util.List.of(base, attached)));
        assertEquals("E1907", e.diagnostic().code());
    }

    @Test
    void attachedFileWithNonExampleContentIsE1906() {
        String bad = """
                examples for example.businesstrip
                data 迷子 = { x: String }
                """;
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(java.util.List.of(BASE, bad)));
        assertEquals("E1906", e.diagnostic().code());
    }

    @Test
    void reportAllReturnsEveryFailure() {
        // The list-returning API surfaces every failing row (fail-fast is not used).
        String model = BASE + """
                example 提出する
                  | (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(200000) }, "2026-07-14") -> 提出済み
                  | (申請準備中 { 申請者 = 従業員ID("emp-1"), 予定費用 = 金額(300000) }, "2026-07-14") -> 提出済み
                """;
        net.unit8.souther.compiler.ast.Ast.Module module =
                net.unit8.souther.compiler.frontend.CstFrontend.parse(model, "Main");
        module = net.unit8.souther.compiler.derive.Deriver.derive(
                net.unit8.souther.compiler.check.Exposing.rewrite(module));
        module = net.unit8.souther.compiler.check.HelperInliner.forModule(module)
                .withInlinedInvariants(module);
        module = net.unit8.souther.compiler.check.NewtypeDesugar.rewrite(module,
                net.unit8.souther.compiler.check.TypeChecker.symbols(module));
        var lowered = net.unit8.souther.compiler.check.Lower.run(module);
        var symbols = net.unit8.souther.compiler.check.TypeChecker.symbols(module);
        net.unit8.souther.compiler.check.TypeChecker.check(module, symbols, java.util.Map.of(), lowered);
        var classes = net.unit8.souther.compiler.codegen.Backend.generate(lowered);
        var sigs = net.unit8.souther.compiler.check.TypeChecker.signatures(module, symbols);
        var fails = ExampleVerifier.check(module, symbols, sigs, classes);
        assertEquals(2, fails.size());
    }
}
