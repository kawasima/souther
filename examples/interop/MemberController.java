// 配置: 任意のアプリ側パッケージ（生成パッケージの外）。
// コントローラは data を構築できない（コンストラクタは非公開）。できるのは
// 「出力アームを型で見分ける（instanceof / switch）」ことと「encoder で外へ出す」ことだけ。
// これがドメインとの境界になる（spec 8.5, 2.1）。
package example.member.web;

import example.member.DB不通;
import example.member.会員ID;
import example.member.会員なし;
import example.member.会員を照会し整形する;
import example.member.会員表示;
import example.member.保存データ不正;

import net.unit8.souther.runtime.DecodeFailure;
import net.unit8.souther.runtime.Raw;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 会員照会の HTTP 境界。流れは
 * {@code HTTP → decode → behavior(>>) → 出力アームを match → encode → HTTP}。
 *
 * <p>{@code 照会} は findMember を jOOQ 実装で束縛したパイプライン（{@link SoutherBeans} で
 * Bean 化）。その出力 {@code 会員表示 | 会員なし | 保存データ不正 | DB不通} を switch で
 * 畳んで HTTP ステータスへ写す。Result も例外もなく、素の直和のアームを見分けるだけ。
 */
@RestController
@RequestMapping("/members")
public final class MemberController {

    private final 会員を照会し整形する 照会;

    public MemberController(会員を照会し整形する 照会) {
        this.照会 = 照会;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> get(@PathVariable String id) {
        // 1. 入力を境界で decode する（会員ID | 復号失敗）。生成物の外では data を作れないので、
        //    値の構築は必ず decoder を通す。
        Object idOrFail = 会員ID.decoder().decode(Raw.text(id));
        if (idOrFail instanceof DecodeFailure) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_member_id"));
        }

        // 2. パイプラインを走らせ、出力アームを畳んで HTTP へ。
        //    会員なし/保存データ不正/DB不通 は findMember から整形段を素通りしてここへ届く。
        return switch (照会.apply(idOrFail)) {
            case 会員表示 v ->
                    ResponseEntity.ok(RawJson.toPlain(会員表示.encoder().encode(v)));
            case 会員なし ignored ->
                    ResponseEntity.notFound().build();
            case 保存データ不正 ignored ->
                    ResponseEntity.status(500).body(Map.of("error", "corrupt_stored_data"));
            case DB不通 ignored ->
                    ResponseEntity.status(503).body(Map.of("error", "database_unavailable"));
            default ->
                    ResponseEntity.status(500).build();
        };
    }
}
