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
 * Boots Spring for real ({@code @SpringBootTest}, RANDOM_PORT brings up embedded Tomcat), connects to
 * H2, and drives {@code POST /checkout} over real HTTP (Tomcat -> Jackson -> controller -> transaction
 * -> H2 -> JSON). It exercises the cross-module pipeline checkout = quote {@code >->} place: quote
 * (example.cart) prices the cart with list combinators, place (example.ordering) records and reserves
 * over the DB.
 *
 * <p>The load-bearing check is that reservation is all-or-nothing across the whole cart: when a later
 * line is short, the earlier line's decrement and the recorded rows all roll back — the failure case
 * drives {@code setRollbackOnly}, verified against the real DB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckoutTransactionTest {

    @Autowired DSLContext dsl;
    @Autowired Environment env;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        // Start each test from a known state (the context — and so the DB — is shared).
        dsl.deleteFrom(table(name("orders"))).execute();
        dsl.deleteFrom(table(name("order_lines"))).execute();
        setStock("apple", 10);
        setStock("tv", 3);
    }

    private HttpResponse<String> postCheckout(String json) throws Exception {
        int port = env.getRequiredProperty("local.server.port", Integer.class);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/checkout"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void setStock(String sku, int qty) {
        dsl.mergeInto(table(name("stock")))
                .columns(field(name("sku")), field(name("qty")))
                .key(field(name("sku")))
                .values(sku, qty)
                .execute();
    }

    private int stockOf(String sku) {
        return dsl.select(field(name("qty"), Integer.class))
                .from(table(name("stock")))
                .where(field(name("sku"), String.class).eq(sku))
                .fetchOne(0, Integer.class);
    }

    private int orderCount() {
        return dsl.fetchCount(table(name("orders")));
    }

    private int lineCount() {
        return dsl.fetchCount(table(name("order_lines")));
    }

    private static String line(String sku, int quantity, int unitPrice) {
        return "{\"sku\":\"" + sku + "\",\"quantity\":" + quantity + ",\"unitPrice\":" + unitPrice + "}";
    }

    private static String cart(boolean open, String... lines) {
        return "{\"open\":" + open + ",\"items\":[" + String.join(",", lines) + "]}";
    }

    @Test
    void POST_confirmed_returns201_andReservesEveryLine() throws Exception {
        // apple 2 x 150 = 300, tv 1 x 200000 = 200000; total 200300, both lines in stock.
        HttpResponse<String> res = postCheckout(cart(true, line("apple", 2, 150), line("tv", 1, 200000)));

        assertEquals(201, res.statusCode(), "confirmed -> 201");
        assertTrue(res.body().contains("\"total\":200300"), "returns the total: " + res.body());
        assertEquals(1, orderCount(), "the order header is committed");
        assertEquals(2, lineCount(), "both order lines are committed");
        assertEquals(8, stockOf("apple"), "apple 10 -> 8");
        assertEquals(2, stockOf("tv"), "tv 3 -> 2");
    }

    @Test
    void POST_outOfStock_returns409_andRollsBackEveryLine() throws Exception {
        // apple 2 (in stock) then tv 5 (only 3): the first line decrements, the second is short, so the
        // whole order — recorded rows and the apple decrement — rolls back.
        HttpResponse<String> res = postCheckout(cart(true, line("apple", 2, 150), line("tv", 5, 200000)));

        assertEquals(409, res.statusCode(), "out of stock -> 409");
        assertTrue(res.body().contains("out_of_stock"), res.body());
        assertEquals(0, orderCount(), "no order header remains");
        assertEquals(0, lineCount(), "no order lines remain");
        assertEquals(10, stockOf("apple"), "the earlier line's decrement rolled back too");
        assertEquals(3, stockOf("tv"), "tv untouched");
    }

    @Test
    void POST_invalidQuantity_returns422_fromTheDomain() throws Exception {
        // quantity 0 decodes fine (quantity is a plain Int), but quote's all(quantity >= 1) departs to
        // InvalidQuantity — a domain outcome, not a decode failure.
        HttpResponse<String> res = postCheckout(cart(true, line("apple", 0, 150)));

        assertEquals(422, res.statusCode(), "invalid quantity -> 422");
        assertTrue(res.body().contains("invalid_quantity"), res.body());
        assertTrue(res.body().contains("\"invalidCount\":1"), res.body());
        assertEquals(0, orderCount());
    }

    @Test
    void POST_closedCart_returns422_empty() throws Exception {
        // open = false, so quote prices nothing and departs to EmptyCart.
        HttpResponse<String> res = postCheckout(cart(false, line("apple", 2, 150)));

        assertEquals(422, res.statusCode(), "empty cart -> 422");
        assertTrue(res.body().contains("empty_cart"), res.body());
        assertEquals(0, orderCount());
    }

    @Test
    void POST_emptySku_returns400_atDecode() throws Exception {
        // sku "" breaks Sku's invariant (length > 0), so the boundary decode rejects it at 400 — before
        // the pipeline runs at all.
        HttpResponse<String> res = postCheckout(cart(true, line("", 1, 150)));

        assertEquals(400, res.statusCode(), "invariant violation -> 400");
        assertTrue(res.body().contains("invalid_cart"), res.body());
        assertTrue(res.body().contains("sku"), "returns the offending field's path: " + res.body());
        assertEquals(0, orderCount());
    }

    // Dropping the stock table reproduces a DB failure, so the context is rebuilt afterwards (the
    // shared schema is not handed broken to later tests).
    //
    // The isolation depends on the same load-bearing facts as before: H2 keeps the in-mem DB alive to
    // JVM exit (DB_CLOSE_DELAY=-1), so @DirtiesContext rebuilds the Spring context but not the DB; the
    // dropped table returns because restart re-runs schema.sql (CREATE TABLE IF NOT EXISTS) and
    // data.sql under spring.sql.init.mode=always. Removing any of the three (DirtiesContext /
    // init.mode=always / IF NOT EXISTS) silently breaks the isolation.
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @Test
    void POST_platformFailure_passesThroughAs503_andRollsBack() throws Exception {
        // reserveStock's UPDATE throws (table gone); the exception is not folded into a case but passes
        // through Souther to the boundary, whose @ExceptionHandler maps it to 503.
        dsl.execute("DROP TABLE stock");

        HttpResponse<String> res = postCheckout(cart(true, line("apple", 1, 150)));

        assertEquals(503, res.statusCode(), "platform failure -> 503");
        assertTrue(res.body().contains("database_unavailable"), res.body());
        // recordOrder's INSERTs ran, but the exception made TransactionTemplate auto-roll-back.
        assertEquals(0, orderCount(), "recorded rows rolled back on the exception too");
        assertEquals(0, lineCount());
    }
}
