package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * A helper whose value parameter is declared as a sum keeps that declared type when the helper is
 * inlined, so a {@code match} inside it still sees the sum even though the argument is a specific
 * case. This is what lets a per-element failing load be aggregated with an ordinary {@code fold} +
 * {@code match} whose accumulator is a {@code Success | Failure} sum ([#18]).
 */
class CompileSumParamMatchTest {

    @Test
    void aCaseArgumentToANamedSumParamIsStillMatchable() {
        assertDoesNotThrow(() -> Compiler.compile("""
                module demo
                data A = { x: Int }
                data B = { y: Int }
                data S = A | B
                let use (s: S) : Int = match s with
                    | A a -> a.x
                    | B b -> b.y
                let caller () : Int = use(A { x = 1 })
                """));
    }

    @Test
    void aCaseArgumentToAnAnonymousUnionParamIsStillMatchable() {
        assertDoesNotThrow(() -> Compiler.compile("""
                module demo
                data A = { x: Int }
                data B = { y: Int }
                let use (s: A | B) : Int = match s with
                    | A a -> a.x
                    | B b -> b.y
                let caller () : Int = use(A { x = 1 })
                """));
    }

    @Test
    void aMismatchedCaseArgumentIsStillRejected() {
        // C is not a member of S; passing it to `use` must still fail.
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> Compiler.compile("""
                module demo
                data A = { x: Int }
                data B = { y: Int }
                data C = { z: Int }
                data S = A | B
                let use (s: S) : Int = match s with
                    | A a -> a.x
                    | B b -> b.y
                let caller () : Int = use(C { z = 1 })
                """));
    }

    private static final String PRICING = """
            module demo
            import List ( fold )
            data Sku = Int
            data Line = { sku: Sku, quantity: Int }
            data Priced = { sku: Sku, unitPrice: Int }
            data PricedCart = { lines: List<Priced> }
            data NotFound = { missing: Sku }
            data PriceResult = Priced | NotFound
            data Acc = PricedCart | NotFound
            data In = { lines: List<Line> }

            behavior priceLines : (i: In) -> PricedCart | NotFound
                constructs Priced, NotFound, PricedCart

            let priceLines (i) =
                fold((acc, line) -> step(acc, price(line)), PricedCart { lines = [] }, i.lines)

            let price (line: Line) : PriceResult =
                if line.quantity > 0
                then Priced { sku = line.sku, unitPrice = line.quantity * 100 }
                else NotFound { missing = line.sku }

            let step (acc: Acc, r: PriceResult) : Acc = match acc with
                | NotFound n -> n
                | PricedCart c -> match r with
                    | Priced p -> PricedCart { lines = c.lines ++ [p] }
                    | NotFound n -> n
            """;

    @Test
    void perLineFailingLoadAggregatesWithFoldAndMatch() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(PRICING), getClass().getClassLoader());
        Object behavior = loader.loadClass("demo.PriceLines" + "$Impl").getConstructor().newInstance();

        // all quantities > 0 -> a PricedCart carrying every priced line
        Object okIn = Codecs.decoded(loader, "demo.In",
                Map.of("lines", List.of(Map.of("sku", 1L, "quantity", 2L), Map.of("sku", 2L, "quantity", 3L))));
        Map<?, ?> ok = (Map<?, ?>) Codecs.encode(loader, "demo.PricedCart", Codecs.apply(behavior, okIn));
        assertEquals(2, ((List<?>) ok.get("lines")).size());

        // one zero quantity -> the whole thing departs as NotFound for that sku
        Object badIn = Codecs.decoded(loader, "demo.In",
                Map.of("lines", List.of(Map.of("sku", 1L, "quantity", 2L), Map.of("sku", 9L, "quantity", 0L))));
        Map<?, ?> bad = (Map<?, ?>) Codecs.encode(loader, "demo.NotFound", Codecs.apply(behavior, badIn));
        assertEquals(9L, bad.get("missing"));
    }
}
