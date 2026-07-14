package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test: a slice of the SMDD travel-expense model written in Japanese. The domain
 * declares only data + invariant + behavior; decoders/encoders are derived (JSON key = the
 * Japanese field name, single-primitive-field data is a newtype). It exercises value types
 * with invariants, {@code include} for common fields, and a state-transition behavior that
 * guards with {@code require} and builds the next state by spreading the previous one.
 */
class CompileBusinessTripTest {

    private static final String MODEL = """
            module example.businesstrip

            data 従業員ID {
                value: String
                invariant length(value) > 0
            }

            data 金額 {
                value: Int
                invariant value >= 0
            }

            data 出張申請共通項目 {
                申請者: 従業員ID
                予定費用: 金額
            }

            data 申請準備中 {
                include 出張申請共通項目
            }

            data 提出済み {
                include 出張申請共通項目
                提出日時: String
            }

            data 却下 { 理由: String }

            // 提出する: 予定費用が上限内なら提出済みへ遷移、超過なら却下
            behavior 提出する(申請: 申請準備中, 提出日時: String) -> Result<提出済み, 却下>
                constructs 提出済み
            {
                require 申請.予定費用.value <= 100000 else 却下 { 理由: "high_cost" }
                提出済み { ..申請, 提出日時: 提出日時 }
            }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODEL), getClass().getClassLoader());
    }

    private Object draft(BytesClassLoader loader, long cost) throws Exception {
        Decoder<?> d = (Decoder<?>) loader.loadClass("example.businesstrip.申請準備中")
                .getMethod("decoder").invoke(null);
        Raw raw = Raw.object(Map.of("申請者", Raw.text("emp-1"), "予定費用", Raw.integer(cost)));
        return ((Result.Ok<?, ?>) d.decode(raw)).value();
    }

    private Result<?, ?> submit(BytesClassLoader loader, Object draft, String at) throws Exception {
        Object 提出する = loader.loadClass("example.businesstrip.提出する").getConstructor().newInstance();
        return (Result<?, ?>) 提出する.getClass()
                .getMethod("apply", Object.class, Object.class)
                .invoke(提出する, draft, at);
    }

    @Test
    void submittingWithinBudgetTransitionsToSubmitted() throws Exception {
        BytesClassLoader loader = loader();
        Result<?, ?> r = submit(loader, draft(loader, 50000), "2026-07-14");
        assertTrue(r.isOk(), "50000 is within the 100000 budget");

        Encoder enc = (Encoder) loader.loadClass("example.businesstrip.提出済み")
                .getMethod("encoder").invoke(null);
        Raw.ObjectValue out = (Raw.ObjectValue) enc.encode(((Result.Ok<?, ?>) r).value());
        assertEquals(Raw.text("emp-1"), out.value().get("申請者"));       // carried via ..申請
        assertEquals(Raw.integer(50000), out.value().get("予定費用"));    // carried via ..申請
        assertEquals(Raw.text("2026-07-14"), out.value().get("提出日時"));
    }

    @Test
    void submittingOverBudgetIsRejected() throws Exception {
        BytesClassLoader loader = loader();
        Result<?, ?> r = submit(loader, draft(loader, 200000), "2026-07-14");
        assertTrue(r.isErr(), "200000 exceeds the budget, so the request is rejected");
        assertEquals("example.businesstrip.却下", ((Result.Err<?, ?>) r).error().getClass().getName());
    }
}
