// The jOOQ implementation of the injected behavior reserveStock. It extends the generated abstract
// base ReserveStock (Behavior<RecordedOrder, ReserveStockResult>, where the result is
// ConfirmedOrder | OutOfStock). It reserves stock for every line of the order; if any single line is
// short, it returns OutOfStock and writes nothing further.
//
// OutOfStock is a unit case: it is built through the protected factory the generated base inherits
// (no constructor is public outside the generated code). A platform failure (DB down) is not this —
// it is an exception that passes through Souther and becomes a 503 at the boundary (ADR-0029).
package app.ordering;

import example.ordering.ConfirmedOrder;
import example.ordering.RecordedOrder;
import example.ordering.ReserveStock;
import example.ordering.ReserveStockResult;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;

import org.jooq.DSLContext;

import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/**
 * Reserves stock line by line with a conditional UPDATE per line: {@code UPDATE stock SET qty =
 * qty - ? WHERE sku = ? AND qty >= ?}. A row updated means that line was reserved; zero rows means
 * that line is short, so the whole order is out of stock.
 *
 * <p>On the first short line it returns {@code OutOfStock()} and stops. Earlier lines in this order
 * may already have decremented, but the boundary ({@code CheckoutController}) sees the OutOfStock
 * case and calls {@code setRollbackOnly}, so those decrements and the recorded rows all roll back —
 * the reservation is all-or-nothing across the whole cart.
 *
 * <p>On success every line was reserved; ConfirmedOrder carries the order id and total, built through
 * the decoder (both values come from the input RecordedOrder).
 */
public final class JooqReserveStock extends ReserveStock {

    private final DSLContext dsl;

    public JooqReserveStock(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ReserveStockResult apply(RecordedOrder recorded) {
        Map<String, Object> in = RecordedOrder.encoder().encode(recorded);
        String orderId = (String) in.get("orderId");
        long total = ((Number) in.get("total")).longValue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) in.get("items");

        for (Map<String, Object> item : items) {
            String sku = (String) item.get("sku");
            long qty = ((Number) item.get("quantity")).longValue();

            // The subtraction and the availability check are one atomic statement per line.
            int updated = dsl.update(table(name("stock")))
                    .set(field(name("qty"), Long.class), field(name("qty"), Long.class).minus(qty))
                    .where(field(name("sku"), String.class).eq(sku))
                    .and(field(name("qty"), Long.class).ge(qty))
                    .execute();

            if (updated == 0) {
                return OutOfStock();            // a short line: write nothing more → boundary rolls back
            }
        }

        // Every line reserved. ConfirmedOrder holds fields, so build it through the decoder.
        Map<String, Object> raw = Map.of("orderId", orderId, "total", total);
        return switch (ConfirmedOrder.decoder().decode(raw, Path.ROOT)) {
            case Ok<ConfirmedOrder> ok -> ok.value();
            case Err<ConfirmedOrder> e ->
                    throw new IllegalStateException("failed to build ConfirmedOrder: " + e.issues().asList());
        };
    }
}
