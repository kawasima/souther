package example.cart;

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
 * cart.sou の behavior {@code 見積る} を実行して、リストコンビネータが走ることを確かめる。
 * {@code 見積る} は {@code all} / {@code any} / {@code filter} / {@code map} / {@code fold} と
 * 空リスト {@code []} を使う——どれも souther.list が {@code fold} から導いた自言語の関数で、
 * 呼び出し位置に展開される。生成 behavior は {@code new 見積る().apply(カート)} で呼ぶ。
 */
class CartTest {

    private カート decode(boolean 受付中, List<Map<String, Object>> 明細) {
        Result<カート> r = カート.decoder().decode(
                Map.of("受付中", 受付中, "明細リスト", 明細), Path.ROOT);
        if (r instanceof Err<カート> e) {
            throw new AssertionError("should decode: " + e.issues().asList());
        }
        return ((Ok<カート>) r).value();
    }

    private static Map<String, Object> 明細(String 名前, long 数量, long 単価) {
        return Map.of("名前", 名前, "数量", 数量, "単価", 単価);
    }

    @Test
    void すべて有効な明細はmap_fold_anyで見積になる() {
        // りんご 2×150 = 300、テレビ 1×200000 = 200000。合計 200300、うち 1 件が高額（>= 100000）。
        カート c = decode(true, List.of(明細("りんご", 2, 150), 明細("テレビ", 1, 200000)));
        Object result = new 見積る().apply(c);

        見積 q = assertInstanceOf(見積.class, result);
        Map<String, Object> encoded = 見積.encoder().encode(q);
        assertEquals(200300L, encoded.get("合計金額"));
        assertEquals(2L, encoded.get("品目数"));
        assertEquals(true, encoded.get("高額あり"));
    }

    @Test
    void 受付停止中は対象が空リストになり空カートへ() {
        // 受付中 = false なので `対象明細` は `[]`（型は文脈から List<明細>）。length 0 → 空カート。
        カート c = decode(false, List.of(明細("りんご", 2, 150)));
        assertInstanceOf(空カート.class, new 見積る().apply(c));
    }

    @Test
    void 数量0の明細があればallが偽になり数量不正へ() {
        // テレビの数量が 0。all(..., m -> m.数量 >= 1) が偽 → filter で不正件数を数えて数量不正へ。
        カート c = decode(true, List.of(明細("りんご", 2, 150), 明細("テレビ", 0, 200000)));
        Object result = new 見積る().apply(c);

        数量不正 bad = assertInstanceOf(数量不正.class, result);
        assertEquals(1L, 数量不正.encoder().encode(bad).get("不正件数"));
    }
}
