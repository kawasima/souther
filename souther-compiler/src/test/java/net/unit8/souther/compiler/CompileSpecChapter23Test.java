package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MVP acceptance criterion 27.1: the 出張申請 model of spec chapter 23 compiles, as written.
 *
 * <p>It had failed on four counts at once — a zero-argument required behavior would not parse,
 * a {@code List} of a sum could not derive an encoder, {@code ==} between two data was rejected,
 * and a sum with a sum as a case was refused — so the language's own worked example was not a
 * program. Kept verbatim from the spec so drift shows up here.
 */
class CompileSpecChapter23Test {

    private static final String MODULE = """
            module example.businesstrip

            import String { length }

            data 金額 = Int
                invariant value >= 0

            data 従業員ID = String
                invariant length(value) > 0

            data 役職 = 管理職 | 一般社員
            data 管理職 = { level: Int } invariant level >= 1 && level <= 5
            data 一般社員

            data 費用負担区分 = 自社負担 | 先方負担
            data 自社負担     = 立替 | 仮払い | 会社カード
            data 立替
            data 仮払い
            data 会社カード
            data 先方負担

            data 従業員 = {
                id:    従業員ID
                , 役職:   役職
                , 上長ID: 従業員ID
            }

            data 出張申請共通項目 = {
                申請者:    従業員
                , 予定費用:   金額
                , 出張先:    String
                , 出張目的:   String
                , 出張開始日: Date
                , 出張終了日: Date
            }

            data 出張申請 =
                申請準備中 | 提出済み | 事前承認待ち | 事前承認済み

            data 申請準備中 = { ...出張申請共通項目 }
            data 提出済み = { ...出張申請共通項目, 提出日時: DateTime }

            data 事前承認理由 = 高額出張 | 権限不足 | 先方費用負担
            data 高額出張 = { 基準金額: 金額 }
            data 権限不足 = { 役職: 役職 }
            data 先方費用負担

            data 事前承認待ち = {
                ...出張申請共通項目
                , 提出日時:         DateTime
                , 事前承認理由リスト: List<事前承認理由>
            }

            data 事前承認済み = {
                ...出張申請共通項目
                , 提出日時:    DateTime
                , 事前承認日時: DateTime
                , 事前承認者:  従業員ID
            }

            data 承認権限なし

            behavior 現在時刻 : () -> DateTime

            behavior 事前承認する : (
                申請:    事前承認待ち,
                承認者ID: 従業員ID
            ) -> 事前承認済み | 承認権限なし
                constructs 事前承認済み, 承認権限なし
                requires 現在時刻

            let 事前承認する (申請, 承認者ID, 現在時刻) = {
                require 承認者ID == 申請.申請者.上長ID
                    else 承認権限なし

                事前承認済み { ...申請, 事前承認日時 = 現在時刻(), 事前承認者 = 承認者ID }
            }
            """;

    @Test
    void theChapter23ModelCompiles() {
        Map<String, byte[]> classes = Compiler.compile(MODULE);
        assertTrue(classes.containsKey("example.businesstrip.事前承認する"));
        assertTrue(classes.containsKey("example.businesstrip.出張申請$Dec"), "the state sum derives a decoder");
        assertTrue(classes.containsKey("example.businesstrip.現在時刻"), "the clock gets a Java base class");
    }

    /** The nested sum of 8.3 dispatches over its leaves, so 自社負担 never appears on the wire. */
    @Test
    @SuppressWarnings("unchecked")
    void theNestedCostSumDecodesALeaf() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Decoder<Map<String, Object>, Object> dec =
                (Decoder<Map<String, Object>, Object>) loader.loadClass("example.businesstrip.費用負担区分")
                        .getMethod("decoder").invoke(null);
        Object v = ((Ok<Object>) dec.decode(Map.of("type", "会社カード"), Path.ROOT)).value();
        assertEquals("example.businesstrip.会社カード", v.getClass().getName());
    }

    /**
     * 事前承認する compares two 従業員ID with {@code ==} and calls the injected clock. When the
     * approver is not the applicant's manager the guard returns 承認権限なし.
     */
    @Test
    @SuppressWarnings("unchecked")
    void approvalRequiresTheApplicantsManager() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        Decoder<Object, Object> idDec = (Decoder<Object, Object>) loader
                .loadClass("example.businesstrip.従業員ID").getMethod("decoder").invoke(null);
        Decoder<Map<String, Object>, Object> reqDec = (Decoder<Map<String, Object>, Object>) loader
                .loadClass("example.businesstrip.事前承認待ち").getMethod("decoder").invoke(null);

        Map<String, Object> 申請 = Map.of(
                "申請者", Map.of("id", "e1", "役職", Map.of("type", "一般社員"), "上長ID", "boss"),
                "予定費用", 120000L,
                "出張先", "大阪", "出張目的", "商談",
                "出張開始日", java.time.LocalDate.parse("2026-08-01"),
                "出張終了日", java.time.LocalDate.parse("2026-08-02"),
                "提出日時", java.time.LocalDateTime.parse("2026-07-15T10:00:00"),
                "事前承認理由リスト", List.of(Map.of("type", "高額出張", "基準金額", 100000L)));
        Object 事前承認待ち = ((Ok<Object>) reqDec.decode(申請, Path.ROOT)).value();

        Object clock = java.lang.reflect.Proxy.newProxyInstance(loader,
                new Class<?>[]{Behavior.class}, (p, m, a) -> java.time.LocalDateTime.parse("2026-07-15T12:00:00"));
        Object 事前承認する = loader.loadClass("example.businesstrip.事前承認する")
                .getConstructor(Behavior.class).newInstance(clock);
        var apply = 事前承認する.getClass().getMethod("apply", Object.class, Object.class);

        Object stranger = ((Ok<Object>) idDec.decode("someone", Path.ROOT)).value();
        assertEquals("example.businesstrip.承認権限なし",
                apply.invoke(事前承認する, 事前承認待ち, stranger).getClass().getName(),
                "承認者ID /= 上長ID leaves via the guard");
    }
}
