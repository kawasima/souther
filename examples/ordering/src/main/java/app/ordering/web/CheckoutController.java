// The HTTP boundary for checkout. The flow is HTTP -> decode -> quote (example.cart) -> for a priced
// cart, place (example.ordering) inside a transaction -> match the output case -> encode / HTTP
// status. The controller cannot construct data (constructors are not public); all it does is run the
// input through a decoder and fold the output cases into encode / status / rollback — that is what a
// boundary is (spec 8.5, 2.1). Transaction control lives here.
//
// This is also where the two modules are composed. cart's quote and ordering's place have different
// failure vocabularies and each keeps its own single-module sealed output, so the boundary runs quote
// first and, only for a priced cart, runs place — an anti-corruption seam between two bounded
// contexts, which is the honest place for cross-context orchestration.
package app.ordering.web;

import example.cart.Cart;
import example.cart.EmptyCart;
import example.cart.InvalidQuantity;
import example.cart.PricedCart;
import example.cart.Quote;
import example.ordering.ConfirmedOrder;
import example.ordering.OutOfStock;
import example.ordering.Place;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;

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
 * {@code POST /checkout}. The body is a cart {@code {"open": true, "items": [{"sku": "...",
 * "quantity": n, "unitPrice": m}, ...]}}. It is decoded by {@code Cart.decoder()} (which re-checks
 * invariants — an empty {@code sku} is rejected here), priced by quote, and — for a priced cart —
 * recorded and reserved by place inside one transaction.
 *
 * <p>Rollback happens two ways. A <b>domain failure</b> (out of stock) arrives as a case value, and
 * its case calls {@code setRollbackOnly()} to undo the recorded rows and any earlier line's
 * decrement (a failure is a value; the rollback is the boundary's decision). A <b>platform failure</b>
 * (DB down) arrives not as a case but as an exception passing through Souther, so
 * {@code TransactionTemplate} auto-rolls-back and {@link #onPlatformFailure} maps it to 503
 * (spec 13.4 / ADR-0029). Output statuses: confirmed = 201 / out of stock = 409 / empty or invalid
 * cart = 422 / decode failure = 400.
 */
@RestController
@RequestMapping("/checkout")
public final class CheckoutController {

    private final Quote quote;
    private final Place place;
    private final TransactionTemplate tx;

    public CheckoutController(Quote quote,
                              Place place,
                              TransactionTemplate tx) {
        this.quote = quote;
        this.place = place;
        this.tx = tx;
    }

    @PostMapping
    public ResponseEntity<Object> checkout(@RequestBody Map<String, Object> body) {
        // Decode the input at the boundary; data cannot be built outside the generated code (spec 8.5).
        return switch (Cart.decoder().decode(body, Path.ROOT)) {
            // Decode failure: keep the Issues (which field broke which rule) and return them at 400.
            case Err<Cart>(var issues) -> ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_cart",
                    "issues", issues.asList().stream().map(CheckoutController::describe).toList()));

            // Decode success: price the cart (pure, no DB), then branch on quote's outcome.
            case Ok<Cart>(var cart) -> switch (quote.apply(cart)) {
                // A priced cart: record and reserve inside a transaction. A platform-failure exception
                // passes through here; TransactionTemplate auto-rolls-back and onPlatformFailure receives it.
                case PricedCart priced -> tx.execute(status -> switch (place.apply(priced)) {
                    case ConfirmedOrder confirmed ->
                            ResponseEntity.status(201).body(ConfirmedOrder.encoder().encode(confirmed));
                    case OutOfStock _ -> {
                        status.setRollbackOnly();      // domain failure -> undo the recorded rows
                        yield ResponseEntity.status(409).body(Map.of("error", "out_of_stock"));
                    }
                });
                // quote's own failures need no DB and no transaction.
                case EmptyCart _ ->
                        ResponseEntity.status(422).body(Map.of("error", "empty_cart"));
                case InvalidQuantity invalid ->
                        ResponseEntity.status(422).body(Map.of(
                                "error", "invalid_quantity",
                                "invalidCount", InvalidQuantity.encoder().encode(invalid).get("invalidCount")));
            };
        };
    }

    /** Shapes a Raoh {@link Issue} into a JSON-carriable map (where, which rule, what description). */
    private static Map<String, Object> describe(Issue issue) {
        Map<String, Object> m = new LinkedHashMap<>();     // tolerates a null message
        m.put("path", issue.path().toString());
        m.put("code", issue.code());
        m.put("message", issue.message());
        return m;
    }

    /**
     * A platform failure (DB down): an exception the Java binding threw and Souther passed through,
     * mapped to 503. The transaction is already rolled back by {@code TransactionTemplate}.
     *
     * <p>Only Spring's {@link DataAccessException} is caught (spec 13.4 / ADR-0029) — deliberately
     * narrow, so an implementation bug (e.g. an {@code IllegalStateException} from a decode that
     * cannot fail) is not disguised as a platform failure but falls through to a plain 500.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Object> onPlatformFailure(DataAccessException e) {
        return ResponseEntity.status(503).body(Map.of("error", "database_unavailable"));
    }
}
