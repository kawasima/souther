// 配置: 生成パッケージ example.member に置く。理由は「失敗アーム（会員なし 等）を構築するため」
// だけ（値の取り出しは encoder 経由なので、フィールド直読みのための in-package ではない）。
//
// 注意: 失敗アームを `new` で作れているのは、コンストラクタが package-private で、この実装を
// 同一パッケージに置いたからにすぎない。これは生成経路の封じ込め（spec 2.1）を厳密には満たさない
// 暫定策で、in-package なら任意の data を `new` できてしまう。本来は「required behavior に、宣言した
// 出力アームだけを構築する capability を渡す」形にして、この `new` を無くしたい（README の設計メモ参照）。
package example.member;

import net.unit8.souther.runtime.DecodeFailure;
import net.unit8.souther.runtime.Raw;

import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;

import java.util.Map;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/**
 * required behavior {@code findMember} の jOOQ 実装。
 *
 * <p>Souther が生成した {@code findMember} インターフェース（{@code Behavior} を継承、
 * {@code Object apply(Object)}）を実装する。入力は {@code 会員ID}、出力は素の直和
 * {@code 会員 | 会員なし | 保存データ不正 | DB不通} のいずれかのアーム値。DB 例外は
 * {@code DB不通} アームに畳み、言語側へ例外として漏らさない（spec 13.4）。
 */
public final class JooqFindMember implements findMember {

    private final DSLContext dsl;

    public JooqFindMember(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Object apply(Object input) {
        会員ID id = (会員ID) input;
        // 値を data の外へ取り出すのは encoder を通す（spec 8.5）。会員ID は newtype なので
        // encode すると裸の Raw.Text になる。フィールド直読み（id.value）はパッケージ内でしか
        // 効かず境界も破るので使わない。
        String idStr = ((Raw.TextValue) 会員ID.encoder().encode(id)).value();
        try {
            var row = dsl
                    .select(field(name("id"), String.class),
                            field(name("email"), String.class),
                            field(name("display_name"), String.class))
                    .from(table(name("member")))
                    .where(field(name("id"), String.class).eq(idStr))
                    .fetchOne();

            if (row == null) {
                return new 会員なし();                        // 失敗アームを構築（in-package）
            }

            // DB の行を外部表現 Raw に組み立て、生成された decoder に渡す。
            // decoder は不変条件（メールに @ を含む 等）を検査して 会員 | 復号失敗 を返す。
            Raw raw = Raw.object(Map.of(
                    "id",    Raw.text(row.value1()),
                    "メール", Raw.text(row.value2()),
                    "表示名", Raw.text(row.value3())));

            Object decoded = 会員.decoder().decode(raw);
            if (decoded instanceof DecodeFailure) {
                return new 保存データ不正();                  // 保存値がドメイン不変条件を破っていた
            }
            return decoded;                                  // 会員（本線）

        } catch (DataAccessException e) {
            return new DB不通();                             // 予期しない障害はアームに畳む
        }
    }
}
