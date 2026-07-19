// 注文受付の HTTP 境界。流れは HTTP → decode → トランザクション付きパイプライン → 出力ケースを
// match → encode / HTTP ステータス。コントローラは data を構築できない（コンストラクタは非公開）。
// できるのは入力を decoder に通すことと、出力ケースを型で見分けて encode / ステータス / 巻き戻しに
// 畳むことだけ ── これが境界（spec 8.5, 2.1）。トランザクション制御もここに置く。
package app.ordering.web;

import example.ordering.在庫不足;
import example.ordering.注文;
import example.ordering.注文を処理する結果;
import example.ordering.注文確定;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.souther.runtime.Behavior;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code POST /orders}。ボディ {@code {"商品": "...", "個数": n}} を {@code 注文.decoder()} で
 * decode し（invariant 検査）、束縛済みパイプライン {@code 注文を処理する}（= 注文を記録する
 * {@code >->} 在庫を引き当てる）を 1 つのトランザクションの中で走らせる。
 *
 * <p>ロールバックは 2 通り。<b>ドメインの失敗（在庫不足）</b>はケース＝値として届くので、その case で
 * {@code setRollbackOnly()} を呼び前段 INSERT を巻き戻す（失敗は値、巻き戻しは境界の判断）。
 * <b>プラットフォーム障害</b>（DB ダウン等）はケースでなく例外として Souther を素通りして来るので、
 * {@code TransactionTemplate} が自動ロールバックし、{@link #onPlatformFailure} が 503 に写す
 * （spec 13.4 / ADR-0029）。出力ケースの HTTP ステータス：確定=201 / 在庫不足=409 / decode 失敗=400。
 */
@RestController
@RequestMapping("/orders")
public final class OrderController {

    private final Behavior<注文, 注文を処理する結果> pipeline;
    private final TransactionTemplate tx;

    public OrderController(Behavior<注文, 注文を処理する結果> pipeline, TransactionTemplate tx) {
        this.pipeline = pipeline;
        this.tx = tx;
    }

    @PostMapping
    public ResponseEntity<Object> place(@RequestBody Map<String, Object> body) {
        // 入力を境界で decode する。生成物の外では data を作れないので decoder に通す（spec 8.5）。
        // 注文.decoder() は Decoder<Map<String,Object>, 注文>、decode は Result<注文>。
        return switch (注文.decoder().decode(body, Path.ROOT)) {
            // decode 失敗：Err の Issues（どのフィールドがどの規則に反したか）を捨てず 400 で返す。
            case Err<注文>(var issues) -> ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_order",
                    "issues", issues.asList().stream().map(OrderController::describe).toList()));

            // decode 成功：トランザクションの中でパイプラインを走らせ、出力ケースで巻き戻しと HTTP を
            // 同時に畳む。プラットフォーム障害の例外はここを素通りし、TransactionTemplate が自動
            // ロールバックして onPlatformFailure が受ける。
            case Ok<注文>(var order) -> tx.execute(status -> switch (pipeline.apply(order)) {
                case 注文確定 confirmed ->
                        // encode は素の Map（外部表現。spec 6）。Spring/Jackson がそのまま JSON 化する。
                        ResponseEntity.status(201).body(注文確定.encoder().encode(confirmed));
                case 在庫不足 _ -> {
                    status.setRollbackOnly();          // ドメイン失敗 → 前段 INSERT を巻き戻す
                    yield ResponseEntity.status(409).body(Map.of("error", "insufficient_stock"));
                }
            });
        };
    }

    /** Raoh の {@link Issue} を JSON に載る Map に整形する（どこで・どの規則が・何の説明か）。 */
    private static Map<String, Object> describe(Issue issue) {
        Map<String, Object> m = new LinkedHashMap<>();     // message が null でも載せられる
        m.put("path", issue.path().toString());
        m.put("code", issue.code());
        m.put("message", issue.message());
        return m;
    }

    /**
     * プラットフォーム障害（DB ダウン等）。Java Binding が投げ Souther が素通しした例外を 503 に写す。
     * トランザクションは {@code TransactionTemplate} が既に巻き戻している。
     *
     * <p>捕まえるのは Spring の {@link DataAccessException} だけ（spec 13.4 / ADR-0029）。jOOQ の
     * 例外は Spring Boot の jOOQ auto-config が Spring の型へ変換するのでここに入る。これは意図的に
     * 狭い ── 実装バグ（例えば decode 不能で投げる {@code IllegalStateException}）はプラットフォーム
     * 障害ではないので、この 503 に混ぜず一般の 500 に落とす。
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Object> onPlatformFailure(DataAccessException e) {
        return ResponseEntity.status(503).body(Map.of("error", "database_unavailable"));
    }
}
