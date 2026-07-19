package example.businesstrip;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** businesstrip.sou の {@code ...} スプレッド（フィールド合成）とネストした newtype invariant を decode で検査する。 */
class BusinessTripTest {

    @Test
    void includeしたフィールドをネストしたnewtypeとしてdecodeする() {
        // 申請者 = 従業員ID(newtype String), 予定費用 = 金額(newtype Int)
        Map<String, Object> raw = Map.of("申請者", "e-001", "予定費用", 50000L);
        Result<申請準備中> ok = 申請準備中.decoder().decode(raw, Path.ROOT);
        assertInstanceOf(Ok.class, ok);
    }

    @Test
    void ネストしたinvariant違反はErrになる() {
        // 申請者 空文字（length > 0 に反する）＋ 予定費用 負（value >= 0 に反する）
        Map<String, Object> raw = Map.of("申請者", "", "予定費用", -1L);
        Result<申請準備中> err = 申請準備中.decoder().decode(raw, Path.ROOT);
        assertInstanceOf(Err.class, err);
    }

    @Test
    void 自己参照した組織図の上司連鎖を再帰でたどって決裁の深さを測る() {
        // 一般社員 e-1 → 課長 e-2 → 部長 e-3（頂点、上司なし）。導出 decoder が入れ子の
        // 社員 を辿って復号し、再帰ヘルパー 決裁の深さ が上司連鎖を根まで登る。
        Map<String, Object> 組織図 = Map.of(
                "ID", "e-1", "上司", Map.of(
                        "ID", "e-2", "上司", Map.of(
                                "ID", "e-3")));
        社員 一般社員 = ((Ok<社員>) 社員.decoder().decode(組織図, Path.ROOT)).value();

        決裁レベル レベル = (決裁レベル) new 決裁レベルを測る().apply(一般社員);

        // e-1 は 2 階層上（課長・部長）に承認者を持つ。頂点 e-3 は 0。
        assertEquals(2L, 決裁レベル.encoder().encode(レベル));
    }
}
