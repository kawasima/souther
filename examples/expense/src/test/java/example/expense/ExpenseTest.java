package example.expense;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** expense.sou の List&lt;T&gt; とネストした data を持つ直積を decode / encode する。 */
class ExpenseTest {

    @Test
    void リストとネストdataを持つ申請がdecodeされencodeで往復する() {
        // Int は Long。理由 = { コード: String } は単一プリミティブ = newtype なので、
        // List<理由> は裸の文字列のリストから読む（Map ではない）。
        Map<String, Object> raw = Map.of(
                "予定費用", 50000L,
                "理由リスト", List.of("A100", "B200"));
        Result<申請> decoded = 申請.decoder().decode(raw, Path.ROOT);

        switch (decoded) {
            case Ok<申請> ok -> {
                Map<String, Object> encoded = 申請.encoder().encode(ok.value());
                assertEquals(50000L, encoded.get("予定費用"));
                assertEquals(2, ((List<?>) encoded.get("理由リスト")).size());
            }
            case Err<申請> err -> throw new AssertionError("should decode: " + err.issues().asList());
        }
    }
}
