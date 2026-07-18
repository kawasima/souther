// 注入 behavior 在庫を引き当てる の jOOQ 実装。入力は 注文受付、出力は 注文確定 | 在庫不足。
// 在庫を条件付き UPDATE で引き当て、更新0行なら 在庫不足（継承ファクトリ）。成功時は残在庫を
// jOOQ Record として読み直し、注文確定.recordDecoder() で組み立てる ── raoh-jooq の Record 源
// decoder のショーケース（member は Map 源しか使っていなかった。spec 10.6）。
//
// プラットフォーム障害（DB ダウン等）はアームに畳まず例外で抜ける。Souther はそれを素通しし、
// トランザクションは自動ロールバック、境界は 503 に写す。ドメインの失敗（在庫不足）だけがアーム。
package app.ordering;

import example.ordering.在庫を引き当てる;
import example.ordering.在庫を引き当てる結果;
import example.ordering.注文確定;
import example.ordering.注文受付;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;

import org.jooq.DSLContext;
import org.jooq.Record;

import java.util.Map;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.val;

/**
 * 条件付き UPDATE で在庫を引き当てる。{@code UPDATE stock SET qty = qty - ? WHERE item_id = ? AND
 * qty >= ?} が 1 行更新すれば確定、0 行なら在庫不足 ── 引き算と在庫チェックを 1 文で原子的に行う。
 *
 * <p>在庫不足のときは何も書かずに {@code 在庫不足()}（継承した protected ファクトリ）を返す。
 * このアームを見た境界（{@link app.ordering.web.OrderService}）がトランザクションを
 * {@code setRollbackOnly} するので、前段 {@link JooqRecordOrder} の INSERT ごと巻き戻る。
 *
 * <p>確定時の残在庫は、採番済みの注文番号（入力の 注文受付 から encoder で取り出す）を列に混ぜた
 * jOOQ {@code Record} として読み、{@code 注文確定.recordDecoder()} で decode する。注文ID の
 * invariant はこの Record 源 decoder が検査する。
 */
public final class JooqAllocateStock extends 在庫を引き当てる {

    private final DSLContext dsl;

    public JooqAllocateStock(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public 在庫を引き当てる結果 apply(注文受付 受付) {
        // 値の取り出しは encoder 経由。注文受付 = { 注文番号: 注文ID, 商品: 商品ID, 個数: 数量 }。
        Map<String, Object> in = 注文受付.encoder().encode(受付);
        String 注文番号 = (String) in.get("注文番号");
        String 商品 = (String) in.get("商品");
        long 個数 = ((Number) in.get("個数")).longValue();

        // DB 例外はここで捕まえない。プラットフォーム障害は例外のまま Souther を素通りさせる。
        int updated = dsl.update(table(name("stock")))
                .set(field(name("qty"), Long.class), field(name("qty"), Long.class).minus(個数))
                .where(field(name("item_id"), String.class).eq(商品))
                .and(field(name("qty"), Long.class).ge(個数))
                .execute();

        if (updated == 0) {
            return 在庫不足();                            // 在庫が足りない。何も書かない → 境界が巻き戻す
        }

        // 残在庫を Record として読み直す。注文番号は DB 列ではなく入力由来なので、列に混ぜる。
        // 注文確定 = { 注文番号: 注文ID, 残在庫: Int } は平坦（スカラ＋newtype）なので Record 源 decoder を持つ。
        Record row = dsl
                .select(val(注文番号).as("注文番号"),
                        field(name("qty"), Long.class).as("残在庫"))
                .from(table(name("stock")))
                .where(field(name("item_id"), String.class).eq(商品))
                .fetchOne();

        return switch (注文確定.recordDecoder().decode(row, Path.ROOT)) {
            case Ok<注文確定> ok -> ok.value();            // 注文確定（本線）
            case Err<注文確定> e ->
                    // 直前に書いた行なので起こり得ない。起きたら実装バグ（spec 13.4 の保証対象外）。
                    throw new IllegalStateException("failed to build 注文確定: " + e.issues().asList());
        };
    }
}
