package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test: a slice of the SMDD travel-expense model written in Japanese. The domain
 * declares only data + invariant + behavior; decoders/encoders are derived (JSON key = the
 * Japanese field name, single-primitive-field data is a newtype). It exercises value types
 * with invariants, {@code ...} spread for common fields, and a state-transition behavior whose
 * output is an unmarked sum (提出済み | 却下) guarded with {@code require}.
 */
class CompileBusinessTripTest {

    private static final String MODEL = """
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

            data 申請準備中 = {
                ...出張申請共通項目
            }

            data 提出済み = {
                ...出張申請共通項目
                , 提出日時: String
            }

            data 却下 = { 理由: String }

            // 提出する: 予定費用が上限内なら提出済みへ遷移、超過なら却下（失敗も普通の data ケース）
            behavior 提出する : (申請: 申請準備中, 提出日時: String) -> 提出済み | 却下
                constructs 提出済み, 却下

            let 提出する (申請, 提出日時) = {
                require 申請.予定費用.value <= 100000 else 却下 { 理由 = "high_cost" }
                提出済み { ...申請, 提出日時 = 提出日時 }
            }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODEL), getClass().getClassLoader());
    }

    private Object draft(BytesClassLoader loader, long cost) throws Exception {
        Decoder d = (Decoder) loader.loadClass("example.businesstrip.申請準備中")
                .getMethod("decoder").invoke(null);
        Map<String, Object> input = Map.of("申請者", "emp-1", "予定費用", cost);
        return ((Ok) d.decode(input, Path.ROOT)).value();
    }

    private Object submit(BytesClassLoader loader, Object draft, String at) throws Exception {
        Object 提出する = loader.loadClass("example.businesstrip.提出する").getConstructor().newInstance();
        return 提出する.getClass()
                .getMethod("apply", Object.class, Object.class)
                .invoke(提出する, draft, at);
    }

    @Test
    void submittingWithinBudgetTransitionsToSubmitted() throws Exception {
        BytesClassLoader loader = loader();
        Object r = submit(loader, draft(loader, 50000), "2026-07-14");
        assertEquals("example.businesstrip.提出済み", r.getClass().getName(),
                "50000 is within the 100000 budget");

        Encoder enc = (Encoder) loader.loadClass("example.businesstrip.提出済み")
                .getMethod("encoder").invoke(null);
        Map<?, ?> out = (Map<?, ?>) enc.encode(r);
        assertEquals("emp-1", out.get("申請者"));        // carried via ...申請
        assertEquals(50000L, out.get("予定費用"));        // carried via ...申請
        assertEquals("2026-07-14", out.get("提出日時"));
    }

    @Test
    void submittingOverBudgetIsRejected() throws Exception {
        BytesClassLoader loader = loader();
        Object r = submit(loader, draft(loader, 200000), "2026-07-14");
        // 200000 exceeds the budget, so the request yields the 却下 case
        assertEquals("example.businesstrip.却下", r.getClass().getName());
    }
}
