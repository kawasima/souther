package app.member;

import example.member.会員;
import example.member.会員ID;
import example.member.会員表示;
import example.member.メールアドレス;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 開発ライフサイクルの検証: member.sou を SoutherProcessor がコンパイル時に生成し、その型付き
 * decoder / encoder を Java から直接叩く。生成型を参照できている時点で「.sou → 生成 → 型付き利用」
 * が通っている（ワイルドカードもキャストも要らず Result&lt;T&gt; が得られる）。
 */
class MemberLifecycleTest {

    @Test
    void 会員IDはnewtypeとして裸の文字列からdecodeされる() {
        Result<会員ID> ok = 会員ID.decoder().decode("m-001", Path.ROOT);
        assertInstanceOf(Ok.class, ok);

        Result<会員ID> empty = 会員ID.decoder().decode("", Path.ROOT);
        assertInstanceOf(Err.class, empty, "invariant length(value) > 0 に反する");
    }

    @Test
    void メールアドレスのinvariantが検査される() {
        assertInstanceOf(Ok.class, メールアドレス.decoder().decode("a@example.com", Path.ROOT));
        assertInstanceOf(Err.class, メールアドレス.decoder().decode("no-at-sign", Path.ROOT));
    }

    @Test
    void 会員はMapからdecodeされネストしたinvariant違反を集積する() {
        Map<String, Object> good = Map.of("id", "m-001", "メール", "a@example.com", "表示名", "Bob");
        assertInstanceOf(Ok.class, 会員.decoder().decode(good, Path.ROOT));

        Map<String, Object> bad = Map.of("id", "", "メール", "nope", "表示名", "Bob");
        Result<会員> err = 会員.decoder().decode(bad, Path.ROOT);
        assertInstanceOf(Err.class, err);
        if (err instanceof Err<会員> e) {
            assertTrue(e.issues().asList().size() >= 1, "id / メール の invariant 違反が集積される");
        }
    }

    @Test
    void 会員表示はdecodeしてencodeで往復する() {
        Map<String, Object> raw = Map.of("id", "m-001", "メール", "a@example.com", "表示名", "Bob");
        Result<会員表示> decoded = 会員表示.decoder().decode(raw, Path.ROOT);

        switch (decoded) {
            case Ok<会員表示> ok -> {
                Map<String, Object> encoded = 会員表示.encoder().encode(ok.value());
                assertEquals("m-001", encoded.get("id"));
                assertEquals("a@example.com", encoded.get("メール"));
                assertEquals("Bob", encoded.get("表示名"));
            }
            case Err<会員表示> err -> throw new AssertionError("should decode: " + err.issues().asList());
        }
    }
}
