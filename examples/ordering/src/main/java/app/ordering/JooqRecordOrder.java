// The jOOQ implementation of the injected behavior recordOrder. It extends the generated abstract
// base RecordOrder (which implements Behavior<PricedCart, RecordedOrder>). Input is the priced cart
// from example.cart's quote; output is a RecordedOrder carrying an assigned order id and the lines.
//
// A platform failure (DB down) is not folded into a case — it is thrown as an exception that passes
// straight through Souther (the language has no exceptions, but a Java binding may throw). The
// boundary's TransactionTemplate rolls back and the @ExceptionHandler maps it to 503 (ADR-0029).
package app.ordering;

import example.cart.PricedCart;
import example.ordering.OrderId;
import example.ordering.RecordOrder;
import example.ordering.RecordedOrder;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;

import org.jooq.DSLContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/**
 * Assigns an order id, inserts the order header and one row per line, and returns the RecordedOrder
 * the next stage (reserveStock) consumes.
 *
 * <p>Values are read through the encoder, not field access: {@code PricedCart.encoder().encode(p)}
 * returns a neutral Map (spec 6) whose {@code items} is a list of per-line maps ({@code sku} /
 * {@code quantity} / {@code unitPrice}), each already unwrapped to plain values. The order id is a
 * concern outside the language (Souther has no clock or randomness), so Java mints it here.
 *
 * <p>This INSERT runs inside the boundary's transaction (see {@code CheckoutController}). If a later
 * line is out of stock, or the DB throws, every row inserted here rolls back.
 */
public final class JooqRecordOrder extends RecordOrder {

    private final DSLContext dsl;

    public JooqRecordOrder(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecordedOrder apply(PricedCart p) {
        Map<String, Object> in = PricedCart.encoder().encode(p);
        List<Map<String, Object>> items = (List<Map<String, Object>>) in.get("items");
        long total = ((Number) in.get("total")).longValue();

        String orderId = UUID.randomUUID().toString();

        // DB exceptions are not caught here — a platform failure passes through as an exception.
        dsl.insertInto(table(name("orders")))
                .columns(field(name("id")), field(name("total")))
                .values(orderId, total)
                .execute();
        for (Map<String, Object> item : items) {
            dsl.insertInto(table(name("order_lines")))
                    .columns(field(name("order_id")), field(name("sku")),
                            field(name("qty")), field(name("unit_price")))
                    .values(orderId, item.get("sku"),
                            ((Number) item.get("quantity")).longValue(),
                            ((Number) item.get("unitPrice")).longValue())
                    .execute();
        }

        // RecordedOrder holds fields, so build it through the decoder from a neutral Map (spec 8.5):
        // the lines are the same neutral maps read above; OrderId's invariant is re-checked here.
        Map<String, Object> raw = Map.of("orderId", orderId, "items", items, "total", total);
        return switch (RecordedOrder.decoder().decode(raw, Path.ROOT)) {
            case Ok<RecordedOrder> ok -> ok.value();
            case Err<RecordedOrder> e ->
                    // A freshly minted id and just-read lines, so this cannot fail; if it does it is a
                    // bug, not a domain outcome (spec 13.4).
                    throw new IllegalStateException("failed to build RecordedOrder: " + e.issues().asList());
        };
    }
}
