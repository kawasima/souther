package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * An {@code example}/{@code fake} fixture may name {@code None} for an optional ({@code T?}) field,
 * mirroring how a {@code let} body writes an empty optional (spec 6.2). {@code None} decodes to the
 * absent optional, the same as omitting the field (spec 8, absent key -> None). A present value is
 * written directly (wrapped in {@code Some}); omission and a present value already worked, so those
 * are regression guards.
 */
class CompileOptionalFixtureTest {

    private static final String BASE = """
            module demo
            import String ( length )

            data 従業員ID = String
                invariant length(value) > 0

            data 社員 = { ID: 従業員ID, 上司: 社員? }

            data 深さ = { n: Int }

            behavior 測る : (社員: 社員) -> 深さ
                constructs 深さ

            let 深さ計算 (社員: 社員): Int =
                match 社員.上司 with
                    | Some 上長 -> 深さ計算(上長) + 1
                    | None -> 0

            let 測る (社員) = 深さ { n = 深さ計算(社員) }
            """;

    @Test
    void noneNamesAnEmptyOptionalField() {
        String model = BASE + """
                example 測る
                  | "top of chain" : (社員 { ID = 従業員ID("ceo"), 上司 = None }) -> 深さ { n = 0 }
                """;
        assertDoesNotThrow(() -> Compiler.compile(model));
    }

    @Test
    void omittingAnOptionalFieldStillWorks() {
        String model = BASE + """
                example 測る
                  | "omit 上司" : (社員 { ID = 従業員ID("ceo") }) -> 深さ { n = 0 }
                """;
        assertDoesNotThrow(() -> Compiler.compile(model));
    }

    @Test
    void aPresentValueWrapsInSomeAndNoneTerminatesTheChain() {
        String model = BASE + """
                example 測る
                  | "one boss over a None top" : (社員 { ID = 従業員ID("e"), 上司 = 社員 { ID = 従業員ID("boss"), 上司 = None } }) -> 深さ { n = 1 }
                """;
        assertDoesNotThrow(() -> Compiler.compile(model));
    }
}
