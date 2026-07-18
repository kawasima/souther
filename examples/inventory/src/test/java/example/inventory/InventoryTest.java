package example.inventory;

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
 * inventory.sou の behavior を実行し、fold から導いたリスト操作——{@code sum} / {@code sort} /
 * {@code member} / {@code isEmpty}——が map / filter と組んでドメインの上で走ることを確かめる。
 */
class InventoryTest {

    private static Map<String, Object> 品目(String コード, long 数量, long 単価) {
        return Map.of("コード", コード, "数量", 数量, "単価", 単価);
    }

    private 在庫 decode(List<Map<String, Object>> 品目リスト) {
        Result<在庫> r = 在庫.decoder().decode(Map.of("品目リスト", 品目リスト), Path.ROOT);
        if (r instanceof Err<在庫> e) {
            throw new AssertionError("should decode: " + e.issues().asList());
        }
        return ((Ok<在庫>) r).value();
    }

    @Test
    void 棚卸はsum_sort_isEmptyで総額とコード一覧と品切れ有無を出す() {
        // APPLE 3×120=360、MELON 1×800=800。総額 1160。品切れ（数量0）は無い。
        在庫 在 = decode(List.of(品目("MELON", 1, 800), 品目("APPLE", 3, 120)));
        Object 結果 = new 棚卸する().apply(在);

        棚卸 卸 = assertInstanceOf(棚卸.class, 結果);
        Map<String, Object> encoded = 棚卸.encoder().encode(卸);
        assertEquals(1160L, encoded.get("総額"));
        assertEquals(List.of("APPLE", "MELON"), encoded.get("コード一覧"), "sort が辞書順に整える");
        assertEquals(true, encoded.get("品切れなし"));
    }

    @Test
    void 数量0の品目があると品切れなしが偽になる() {
        在庫 在 = decode(List.of(品目("APPLE", 3, 120), 品目("MELON", 0, 800)));
        棚卸 卸 = assertInstanceOf(棚卸.class, new 棚卸する().apply(在));
        assertEquals(false, 棚卸.encoder().encode(卸).get("品切れなし"));
    }

    @Test
    void 品目が無い在庫は空在庫へ() {
        assertInstanceOf(空在庫.class, new 棚卸する().apply(decode(List.of())));
    }

    @Test
    void 取扱うはmemberでコードの有無を返す() {
        在庫 在 = decode(List.of(品目("APPLE", 3, 120), 品目("MELON", 1, 800)));
        品目コード melon = ((Ok<品目コード>) 品目コード.decoder().decode("MELON", Path.ROOT)).value();
        品目コード grape = ((Ok<品目コード>) 品目コード.decoder().decode("GRAPE", Path.ROOT)).value();

        assertEquals(true, 取扱.encoder().encode((取扱) new 取扱う().apply(在, melon)).get("あり"));
        assertEquals(false, 取扱.encoder().encode((取扱) new 取扱う().apply(在, grape)).get("あり"));
    }
}
