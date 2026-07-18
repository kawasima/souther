package app.ordering;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 実際に Spring Boot を起動し（{@code @SpringBootTest}、RANDOM_PORT で組み込み Tomcat を上げる）、
 * H2 に接続してトランザクション制御を検証する。主眼は「後段 在庫を引き当てる が 在庫不足 を返したら、
 * 前段 注文を記録する の INSERT ごと巻き戻る」こと ── 失敗ケースが setRollbackOnly を駆動し、DB に
 * 注文行が残らないことを実 DB で確かめる。
 *
 * <p>{@code POST /orders} は JDK の {@link HttpClient} で本物の HTTP を投げ、実サーバの JSON 応答と
 * ステータスを見る（Tomcat → Jackson → コントローラ → トランザクション → H2 → JSON）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderingTransactionTest {

    @Autowired DSLContext dsl;
    @Autowired Environment env;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        // 各テストを既知状態から始める（コンテキスト＝DB は共有なので明示的に戻す）。
        dsl.deleteFrom(table(name("orders"))).execute();
        dsl.update(table(name("stock")))
                .set(field(name("qty"), Integer.class), 10)
                .where(field(name("item_id"), String.class).eq("ITEM-A"))
                .execute();
    }

    private HttpResponse<String> postOrder(String json) throws Exception {
        int port = env.getRequiredProperty("local.server.port", Integer.class);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/orders"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private int stockOf(String item) {
        return dsl.select(field(name("qty"), Integer.class))
                .from(table(name("stock")))
                .where(field(name("item_id"), String.class).eq(item))
                .fetchOne(0, Integer.class);
    }

    private int orderCount() {
        return dsl.fetchCount(table(name("orders")));
    }

    @Test
    void POST注文_確定は201で残在庫を返す() throws Exception {
        HttpResponse<String> res = postOrder("{\"商品\":\"ITEM-A\",\"個数\":3}");

        assertEquals(201, res.statusCode(), "確定 → 201");
        assertTrue(res.body().contains("\"残在庫\":7"), "残在庫 7 を返す: " + res.body());
        assertEquals(1, orderCount(), "注文行がコミットされている");
        assertEquals(7, stockOf("ITEM-A"), "在庫が 10 → 7 に引き当てられている");
    }

    @Test
    void POST注文_在庫不足は409で注文行は巻き戻る() throws Exception {
        // 在庫10に対し100の注文。前段 INSERT は走るが、後段が 在庫不足 を返し setRollbackOnly。
        HttpResponse<String> res = postOrder("{\"商品\":\"ITEM-A\",\"個数\":100}");

        assertEquals(409, res.statusCode(), "在庫不足 → 409");
        assertTrue(res.body().contains("insufficient_stock"), res.body());
        // トランザクション制御の証拠：前段で入れた注文行が残っていない＝巻き戻った。
        assertEquals(0, orderCount(), "注文行はロールバックされ DB に残らない");
        assertEquals(10, stockOf("ITEM-A"), "在庫は据え置き（引き当ては起きていない）");
    }

    @Test
    void POST注文_invariant違反は400でissueを返す() throws Exception {
        // 個数 0 は 数量 の invariant(value > 0) 違反 → 境界の decode で弾かれる。
        HttpResponse<String> res = postOrder("{\"商品\":\"ITEM-A\",\"個数\":0}");

        assertEquals(400, res.statusCode(), "invariant 違反 → 400");
        // Err の Issues を捨てず、どこで何に反したかを本文に載せる。
        assertTrue(res.body().contains("invariant_violation"), res.body());
        assertTrue(res.body().contains("個数"), "違反フィールドの path を返す: " + res.body());
        assertEquals(0, orderCount());
    }

    // 在庫テーブルを落として DB 障害を再現するので、この後はコンテキストを作り直す
    // （共有スキーマを壊したまま後続テストに渡さない）。
    //
    // 注意（この隔離が依存している load-bearing な前提）: H2 は DB_CLOSE_DELAY=-1 で in-mem DB が
    // JVM 終了まで生き残るため、@DirtiesContext は Spring コンテキストを作り直すが DB 自体はリセット
    // しない。落とした stock テーブルが戻るのは、再起動時に spring.sql.init.mode=always が schema.sql
    // （CREATE TABLE IF NOT EXISTS）と data.sql を再実行するからである。この3つ（DirtiesContext /
    // init.mode=always / IF NOT EXISTS）のどれかを外すと隔離が黙って壊れ、テスト順序依存のフレークが
    // 再発する。
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @Test
    void POST注文_プラットフォーム障害は例外で素通りし503_注文行は巻き戻る() throws Exception {
        // 後段 在庫を引き当てる の UPDATE が例外を投げ、それはケースに畳まれず Souther を素通りして
        // 境界へ届く（言語に例外は無いが Java Binding は投げる）。境界の @ExceptionHandler が 503 に写す。
        dsl.execute("DROP TABLE stock");

        HttpResponse<String> res = postOrder("{\"商品\":\"ITEM-A\",\"個数\":3}");

        assertEquals(503, res.statusCode(), "プラットフォーム障害 → 503");
        assertTrue(res.body().contains("database_unavailable"), res.body());
        // 前段 INSERT は走ったが、例外で TransactionTemplate が自動ロールバックしたので残らない。
        assertEquals(0, orderCount(), "例外時も注文行は巻き戻っている");
    }
}
