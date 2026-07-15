// findMember の実装は生成パッケージの外（アプリ側 app.member）に置ける。
// 失敗アーム（会員なし / 保存データ不正 / DB不通）は、生成された抽象基底 findMember が持つ
// protected ファクトリ経由でだけ構築する（spec 2.1 / 13.3）。data のコンストラクタは非公開の
// ままなので、この behavior が宣言した出力アーム以外は作れない——`new` の抜け道は無い。
package app.member;

import example.member.findMember;
import example.member.会員;
import example.member.会員ID;

import net.unit8.souther.runtime.DecodeFailure;
import net.unit8.souther.runtime.Raw;

import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;

import java.util.Map;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/**
 * required behavior {@code findMember} の jOOQ 実装。生成された抽象基底 {@code findMember}
 * （{@code Behavior} を継承）を extends する。入力は {@code 会員ID}、出力は素の直和
 * {@code 会員 | 会員なし | 保存データ不正 | DB不通} のいずれかのアーム値。
 *
 * <p>成功値 {@code 会員} は decoder で組み立て（不変条件を検査）、失敗アームは基底から
 * 継承した {@code 会員なし()} / {@code 保存データ不正()} / {@code DB不通()} で作る。DB 例外は
 * {@code DB不通} アームに畳み、言語側へ例外として漏らさない（spec 13.4）。
 */
public final class JooqFindMember extends findMember {

    private final DSLContext dsl;

    public JooqFindMember(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Object apply(Object input) {
        会員ID id = (会員ID) input;
        // 値を data の外へ取り出すのは encoder を通す（spec 8.5）。会員ID は newtype なので
        // encode すると裸の Raw.Text になる。
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
                return 会員なし();                            // 継承した protected ファクトリ
            }

            // DB の行を外部表現 Raw に組み立て、生成された decoder に渡す。
            // decoder は不変条件（メールに @ を含む 等）を検査して 会員 | 復号失敗 を返す。
            Raw raw = Raw.object(Map.of(
                    "id",    Raw.text(row.value1()),
                    "メール", Raw.text(row.value2()),
                    "表示名", Raw.text(row.value3())));

            Object decoded = 会員.decoder().decode(raw);
            if (decoded instanceof DecodeFailure) {
                return 保存データ不正();                       // 保存値がドメイン不変条件を破っていた
            }
            return decoded;                                  // 会員（本線）

        } catch (DataAccessException e) {
            return DB不通();                                 // 予期しない障害はアームに畳む
        }
    }
}
