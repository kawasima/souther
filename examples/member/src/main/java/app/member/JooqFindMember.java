// findMember の実装は生成パッケージの外（アプリ側 app.member）に置ける。
// ドメインの失敗ケース（会員なし / 保存データ不正）は、生成された抽象基底 findMember が持つ
// protected ファクトリ経由でだけ構築する（spec 2.1 / 13.3）。data のコンストラクタは非公開の
// ままなので、この behavior が宣言した出力ケース以外は作れない——`new` の抜け道は無い。
package app.member;

import example.member.FindMember;
import example.member.FindMember結果;
import example.member.会員;
import example.member.会員ID;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;

import org.jooq.DSLContext;
import org.jooq.Record3;

import java.util.Map;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/**
 * required behavior {@code findMember} の jOOQ 実装。生成された抽象基底 {@code findMember}
 * （{@code Behavior} を継承）を extends する。入力は {@code 会員ID}、出力はドメインの帰結の直和
 * {@code 会員 | 会員なし | 保存データ不正} のいずれかのケース値。
 *
 * <p>成功値 {@code 会員} は decoder で組み立て（不変条件を検査）、失敗ケースは基底から
 * 継承した {@code 会員なし()} / {@code 保存データ不正()} で作る。decode の失敗は Raoh の
 * {@code Result} が運び（spec 10）、ここでは {@code 保存データ不正} に翻訳する。
 *
 * <p>DB ダウンのような<em>プラットフォーム障害</em>はケースに畳まず、例外として投げる。Souther は
 * それを素通しし、境界（Java／フレームワーク側）が扱う（spec 13.4 / ADR-0029）。jOOQ の
 * {@code DataAccessException} は非検査例外なので、そのまま {@code apply} の外へ伝播する。
 */
public final class JooqFindMember extends FindMember {

    private final DSLContext dsl;

    public JooqFindMember(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public FindMember結果 apply(会員ID id) {
        // findMember結果 は生成された sealed interface（spec 19.8）で、宣言した3ケースを permits する。
        // 値を data の外へ取り出すのは encoder を通す（spec 8.5）。会員ID は newtype なので
        // encoder() は Encoder<会員ID, String>、encode は裸の String を返す（キャスト不要）。
        String idStr = 会員ID.encoder().encode(id);

        // DB 例外はここで捕まえない。プラットフォーム障害は例外のまま Souther を素通りさせる。
        Record3<String, String, String> row = dsl
                .select(field(name("id"), String.class),
                        field(name("email"), String.class),
                        field(name("display_name"), String.class))
                .from(table(name("member")))
                .where(field(name("id"), String.class).eq(idStr))
                .fetchOne();

        if (row == null) {
            return 会員なし();                            // 継承した protected ファクトリ
        }

        // DB の行を中立な Map（外部表現。spec 6）に組み立て、生成された Raoh decoder に渡す。
        // decoder は不変条件（メールに @ を含む 等）を検査して Result<会員> を返す。
        Map<String, Object> raw = Map.of(
                "id",    row.value1(),
                "メール", row.value2(),
                "表示名", row.value3());

        // 会員.decoder() は Decoder<Map<String,Object>, 会員>。decode は Result<会員> を返す。
        return switch (会員.decoder().decode(raw, Path.ROOT)) {
            case Ok<会員> ok -> ok.value();               // 会員（本線）
            case Err<会員> ignored -> 保存データ不正();     // 保存値がドメイン不変条件を破っていた
        };
    }
}
