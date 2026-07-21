// 配置: 任意のアプリ側パッケージ（生成パッケージの外）。
// コントローラは data を構築できない（コンストラクタは非公開）。できるのは
// 「出力ケースを型で見分ける（instanceof / switch）」ことと「encoder で外へ出す」ことだけ。
// これがドメインとの境界になる（spec 8.5, 2.1）。
package app.member.web;

import example.member.会員ID;
import example.member.会員なし;
import example.member.会員を照会し整形する;
import example.member.会員表示;
import example.member.保存データ不正;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 会員照会の HTTP 境界。流れは
 * {@code HTTP → decode → behavior(>->) → 出力ケースを match → encode → HTTP}。
 * decode / encode は behavior ではなく境界の縁で、{@code >->} には乗らない（spec 14.1）。
 *
 * <p>{@code 照会} は findMember を jOOQ 実装で束縛したパイプライン（{@link SoutherBeans} で
 * Bean 化）。その出力はドメインの帰結の直和 {@code 会員表示 | 会員なし | 保存データ不正} で、switch で
 * HTTP ステータスへ畳む（200 / 404 / 500）。DB ダウンのようなプラットフォーム障害はケースでなく
 * 例外として素通りしてくるので、{@link #onPlatformFailure} が 503 に写す（spec 13.4 / ADR-0029）。
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
        //    decoder() は Decoder<Object, 会員ID> なので decode は Result<会員ID>。Ok を record パターンで
        //    分解し、Err なら 400 で早期に返す（生成物の外では data を作れない。spec 8.5）。
        if (!(会員ID.decoder().decode(id, Path.ROOT) instanceof Ok<会員ID>(var memberId))) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_member_id"));
        }

        // 2. パイプラインを走らせ、ドメインの出力ケースを畳んで HTTP へ。
        //    会員なし/保存データ不正 は findMember から整形段を素通りしてここへ届く（sealed で網羅的）。
        //    DB ダウン等のプラットフォーム障害はここに来ず、例外として抜ける（onPlatformFailure が受ける）。
        //    照会 は interface（会員を照会し整形する）なので apply は型付き。出力は 会員を照会し整形するResult。
        return switch (照会.apply(memberId)) {
            case 会員表示 v ->
                    // encode は素の Map（外部表現。spec 6）を返す。Spring/Jackson がそのまま JSON 化する。
                    ResponseEntity.ok(会員表示.encoder().encode(v));
            case 会員なし _ ->
                    ResponseEntity.notFound().build();
            case 保存データ不正 _ ->
                    ResponseEntity.status(500).body(Map.of("error", "corrupt_stored_data"));
        };
    }

    /**
     * プラットフォーム障害（DB ダウン等）。Java Binding が投げ Souther が素通しした例外を 503 に写す
     * （spec 13.4 / ADR-0029）。ドメインの帰結でないものはケースでなく例外で扱う。
     *
     * <p>捕まえるのは Spring の {@link DataAccessException}。jOOQ 自身の
     * {@code org.jooq.exception.DataAccessException} はこの型のサブクラスではないので、注入する
     * {@code DSLContext} は Spring の例外変換を効かせておく必要がある（Spring Boot の jOOQ
     * auto-config が既定で入れる）。素の {@code DSLContext} だと jOOQ の型で投げられ、ここを
     * 素通りして 500 になる。
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Object> onPlatformFailure(DataAccessException e) {
        return ResponseEntity.status(503).body(Map.of("error", "database_unavailable"));
    }
}
