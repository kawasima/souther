package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A single-value newtype ({@code data 金額 = Int}) compares by its underlying value, so {@code .value}
 * is not written: {@code m.額 <= m.予算} (same newtype) and {@code m.額 <= 100} (a bare literal taken
 * as the newtype) both work, while {@code 金額 <= 数量} (different newtype) and {@code 金額 <= x}
 * (a non-literal of the underlying type) stay type errors.
 */
class CompileNewtypeComparisonTest {

    private static final String BASE = """
            module demo

            data 金額 = Int
                invariant value >= 0

            data 見積 = { 額: 金額, 予算: 金額 }
            data 予算内 = { }
            data 予算超過 = { }
            """;

    private String result(String body, long amount, long budget) throws Exception {
        String model = BASE + """
                behavior 判定 : (m: 見積) -> 予算内 | 予算超過
                    constructs 予算内, 予算超過
                """ + body;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(model), getClass().getClassLoader());
        Object m = Codecs.decoded(loader, "demo.見積", Map.of("額", amount, "予算", budget));
        Object 判定 = loader.loadClass("demo.判定").getConstructor().newInstance();
        Object r = 判定.getClass().getMethod("apply", Object.class).invoke(判定, m);
        return r.getClass().getName();
    }

    @Test
    void sameNewtypeComparisonReadsTheUnderlyingValue() throws Exception {
        String body = """
                let 判定 (m) = {
                    require m.額 <= m.予算 else 予算超過 { }
                    予算内 { }
                }
                """;
        assertEquals("demo.予算内", result(body, 50, 100));
        assertEquals("demo.予算超過", result(body, 150, 100));
    }

    @Test
    void bareLiteralIsTakenAsTheNewtype() throws Exception {
        String body = """
                let 判定 (m) = {
                    require m.額 <= 100 else 予算超過 { }
                    予算内 { }
                }
                """;
        assertEquals("demo.予算内", result(body, 100, 0));   // boundary: 100 <= 100
        assertEquals("demo.予算超過", result(body, 101, 0));
    }

    @Test
    void equalityAcceptsABareLiteral() throws Exception {
        String body = """
                let 判定 (m) = {
                    require m.額 == 0 else 予算超過 { }
                    予算内 { }
                }
                """;
        assertEquals("demo.予算内", result(body, 0, 0));
        assertEquals("demo.予算超過", result(body, 5, 0));
    }

    @Test
    void comparingTwoDifferentNewtypesIsATypeError() {
        String model = BASE + """
                data 数量 = Int
                data 明細 = { 額: 金額, 個数: 数量 }
                behavior 判定 : (d: 明細) -> 予算内 | 予算超過
                    constructs 予算内, 予算超過
                let 判定 (d) = {
                    require d.額 <= d.個数 else 予算超過 { }
                    予算内 { }
                }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(model));
    }

    @Test
    void comparingANewtypeToAnUnderlyingVariableIsATypeError() {
        // `限度` is an Int parameter (not a literal), so it must be wrapped as 金額(限度) to compare.
        String model = BASE + """
                behavior 判定 : (m: 見積, 限度: Int) -> 予算内 | 予算超過
                    constructs 予算内, 予算超過
                let 判定 (m, 限度) = {
                    require m.額 <= 限度 else 予算超過 { }
                    予算内 { }
                }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(model));
    }

    @Test
    void writingDotValueStillWorks() {
        // the explicit `.value` form remains valid (comparing the underlying Int directly)
        String model = BASE + """
                behavior 判定 : (m: 見積) -> 予算内 | 予算超過
                    constructs 予算内, 予算超過
                let 判定 (m) = {
                    require m.額.value <= 100 else 予算超過 { }
                    予算内 { }
                }
                """;
        assertDoesNotThrow(() -> Compiler.compile(model));
    }
}
