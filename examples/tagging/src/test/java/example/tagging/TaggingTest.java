package example.tagging;

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
 * tags.sou の behavior {@code 正規化} を実行し、文字列編集の道具（split / trim / lowercase /
 * join）とリストコンビネータ（map / filter / sort）が一本のパイプラインとして走ることを確かめる。
 */
class TaggingTest {

    private タグ入力 decode(String 生) {
        Result<タグ入力> r = タグ入力.decoder().decode(Map.of("生", 生), Path.ROOT);
        if (r instanceof Err<タグ入力> e) {
            throw new AssertionError("should decode: " + e.issues().asList());
        }
        return ((Ok<タグ入力>) r).value();
    }

    @Test
    void 空白と大小と空片が混ざった入力を辞書順の正規形にする() {
        // "Scala,  FP ,  DDD " → split → 各片 trim+lowercase → 空片除外 → sort。
        タグ入力 入力 = decode("Scala,  FP ,  DDD ");
        Object 結果 = 正規化.of().apply(入力);

        タグ集合 集合 = assertInstanceOf(タグ集合.class, 結果);
        Map<String, Object> encoded = タグ集合.encoder().encode(集合);
        assertEquals(List.of("ddd", "fp", "scala"), encoded.get("タグ"));
        assertEquals("ddd, fp, scala", encoded.get("表示"));
    }

    @Test
    void 空白とカンマだけの入力はタグ無しへ() {
        // trim すると全片が空になり、filter が全部落とす → length 0 → タグ無し。
        タグ入力 入力 = decode("  , ,,  ");
        assertInstanceOf(タグ無し.class, 正規化.of().apply(入力));
    }
}
