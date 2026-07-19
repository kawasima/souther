package example.sales;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * sales.sou の behavior を実行し、{@code distinct} が map の結果から取扱商品を重複排除して
 * 初出順で返すこと——[] 種の fold accumulator を member で読む形——をドメインの上で確かめる。
 */
class SalesTest {

    private static Map<String, Object> 明細(String 商品, long 数量, long 金額) {
        return Map.of("商品", 商品, "数量", 数量, "金額", 金額);
    }

    private 注文 decode(List<Map<String, Object>> 明細リスト) {
        Result<注文> r = 注文.decoder().decode(Map.of("明細リスト", 明細リスト), Path.ROOT);
        if (r instanceof Err<注文> e) {
            throw new AssertionError("should decode: " + e.issues().asList());
        }
        return ((Ok<注文>) r).value();
    }

    @Test
    void 集計は取扱商品を重複排除し総額と件数を出す() {
        // APPLE が3回・MELON が1回・GRAPE が1回。取扱商品は初出順で APPLE, MELON, GRAPE。
        // 総額 = 120 + 800 + 1500 + 60 + 900 = 3380。高額(1000以上)は GRAPE の1件。
        注文 注 = decode(List.of(
                明細("APPLE", 1, 120),
                明細("MELON", 2, 800),
                明細("GRAPE", 3, 1500),
                明細("APPLE", 1, 60),
                明細("APPLE", 5, 900)));
        Object 結果 = new 集計する().apply(注);

        レポート レ = assertInstanceOf(レポート.class, 結果);
        Map<String, Object> encoded = レポート.encoder().encode(レ);
        assertEquals(3380L, encoded.get("総額"));
        assertEquals(List.of("APPLE", "MELON", "GRAPE"), encoded.get("取扱商品"),
                "distinct が初出順で重複を排す");
        assertEquals(5L, encoded.get("明細数"));
        assertEquals(1L, encoded.get("高額明細数"));
    }

    @Test
    void 明細が無い注文は空注文へ() {
        assertInstanceOf(空注文.class, new 集計する().apply(decode(List.of())));
    }
}
