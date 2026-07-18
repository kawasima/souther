// 注入 behavior 注文を記録する の jOOQ 実装。生成された抽象基底 注文を記録する（Behavior を継承）を
// extends する。入力は 注文、出力は 注文受付。成功ケース 注文受付 は field を持つので decoder で
// 組み立てる（spec 8.5）。data のコンストラクタは非公開のままなので、生成物の外ではこの経路でしか
// 値を作れない。
//
// プラットフォーム障害（DB ダウン等）はケースに畳まず、例外として投げる。Souther（言語）に例外は
// 無いが、境界の Java Binding は投げてよく、Souther はそれを素通しする（生成された >-> パイプラインは
// 例外を握り潰さない）。伝播した例外は境界で 503／トランザクションの自動ロールバックになる。
package app.ordering;

import example.ordering.注文;
import example.ordering.注文を記録する;
import example.ordering.注文受付;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;

import org.jooq.DSLContext;

import java.util.Map;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/**
 * 注文を orders テーブルに INSERT し、採番した注文番号を積んだ 注文受付 を返す。
 *
 * <p>入力 {@code 注文} のフィールドは別パッケージからは読めない（生成 data の field は
 * 非公開。spec 8.5）。値を取り出すのは encoder を通す ── {@code 注文.encoder().encode(注文)} が
 * 中立な Map（キー＝フィールド名）を返す。注文番号は言語の外の関心事なので Java 側で採番する
 * （Souther は時刻や乱数などの環境作用を言語内に持たない）。
 *
 * <p>この INSERT はトランザクションの中で走る（{@link app.ordering.web.OrderService}）。後段の
 * 在庫を引き当てる が 在庫不足 を返すか、あるいは DB 例外が飛べば、ここで入れた行はロールバックされる。
 */
public final class JooqRecordOrder extends 注文を記録する {

    private final DSLContext dsl;

    public JooqRecordOrder(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public 注文受付 apply(注文 o) {
        // 値の取り出しは encoder 経由。注文 = { 商品: 商品ID(String), 個数: 数量(Int) }。
        Map<String, Object> in = 注文.encoder().encode(o);
        String 商品 = (String) in.get("商品");
        long 個数 = ((Number) in.get("個数")).longValue();

        String 注文番号 = UUID.randomUUID().toString();

        // DB 例外はここで捕まえない。プラットフォーム障害は例外のまま Souther を素通りさせる。
        dsl.insertInto(table(name("orders")))
                .columns(field(name("id")), field(name("item_id")), field(name("qty")))
                .values(注文番号, 商品, 個数)
                .execute();

        // 注文受付 は field を持つ成功ケース。生成物の外では data を作れないので decoder に通す。
        // 中立な Map（外部表現。spec 6）から組み立て、invariant（注文ID/商品ID の長さ・数量>0）を再検査する。
        Map<String, Object> raw = Map.of("注文番号", 注文番号, "商品", 商品, "個数", 個数);
        return switch (注文受付.decoder().decode(raw, Path.ROOT)) {
            case Ok<注文受付> ok -> ok.value();          // 注文受付（後段へ流れる本線）
            case Err<注文受付> e ->
                    // 採番済みの既知値なので起こり得ない。起きたら実装バグ（spec 13.4 の保証対象外）。
                    throw new IllegalStateException("failed to build 注文受付: " + e.issues().asList());
        };
    }
}
