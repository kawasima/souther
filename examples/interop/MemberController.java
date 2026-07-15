// 配置: 任意のアプリ側パッケージ（生成パッケージの外）。
// コントローラは data を構築できない（コンストラクタは非公開）。できるのは
// 「出力アームを型で見分ける（instanceof / switch）」ことと「encoder で外へ出す」ことだけ。
// これがドメインとの境界になる（spec 8.5, 2.1）。
package app.member.web;

import example.member.DB不通;
import example.member.会員ID;
import example.member.会員なし;
import example.member.会員を照会し整形する;
import example.member.会員表示;
import example.member.保存データ不正;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 会員照会の HTTP 境界。流れは
 * {@code HTTP → decode → behavior(>>) → 出力アームを match → encode → HTTP}。
 * decode / encode は behavior ではなく境界の縁で、{@code >>} には乗らない（spec 14.1）。
 *
 * <p>{@code 照会} は findMember を jOOQ 実装で束縛したパイプライン（{@link SoutherBeans} で
 * Bean 化）。その出力 {@code 会員表示 | 会員なし | 保存データ不正 | DB不通} を switch で
 * 畳んで HTTP ステータスへ写す。decode 失敗は Raoh の {@code Result} が運び、behavior 出力は
 * 素の直和のアーム。前者は {@code Ok/Err} を見分け、後者はアームを見分ける。
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
        // 1. 入力を境界で decode する。会員ID は newtype なので裸の String から読む。
        //    decoder() は Decoder<Object, 会員ID> なので decode は Result<会員ID> を返す。
        //    その Ok / Err をパターンマッチで見分ける（生成物の外では data を作れない。spec 8.5）。
        会員ID memberId;
        switch (会員ID.decoder().decode(id, Path.ROOT)) {
            case Ok<会員ID> ok -> memberId = ok.value();
            case Err<会員ID> ignored -> {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_member_id"));
            }
        }

        // 2. パイプラインを走らせ、出力アームを畳んで HTTP へ。
        //    会員なし/保存データ不正/DB不通 は findMember から整形段を素通りしてここへ届く。
        return switch (照会.apply(memberId)) {
            case 会員表示 v ->
                    // encode は素の Map（外部表現。spec 6）を返す。Spring/Jackson がそのまま JSON 化する。
                    ResponseEntity.ok(会員表示.encoder().encode(v));
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
