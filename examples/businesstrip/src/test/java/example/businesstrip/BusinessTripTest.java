package example.businesstrip;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** businesstrip.sou の include（フィールド合成）とネストした newtype invariant を decode で検査する。 */
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
}
