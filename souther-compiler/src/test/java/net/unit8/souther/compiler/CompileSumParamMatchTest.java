package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertThrows(CompileException.class, () -> Compiler.compile("""
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
    void aFoldSeededWithACaseAcceptsAnEmptyList() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(PRICING), getClass().getClassLoader());
        Object behavior = loader.loadClass("demo.PriceLines" + "$Impl").getConstructor().newInstance();
        // no lines -> the seed is returned unchanged, an empty PricedCart, typed at the union
        Object emptyIn = Codecs.decoded(loader, "demo.In", Map.of("lines", List.of()));
        Map<?, ?> empty = (Map<?, ?>) Codecs.encode(loader, "demo.PricedCart", Codecs.apply(behavior, emptyIn));
        assertEquals(0, ((List<?>) empty.get("lines")).size());
    }

    @Test
    void aFoldBlockThatAssumesTheSeedsNarrowCaseIsRejected() {
        // `bump`'s accumulator is declared as the narrow case `Ok`, but the fold grows it to `R`.
        // At the widened type the block is not a fixpoint (R is not assignable to Ok), so it is rejected
        // rather than accepted into an unsound fold that could read `acc.n` on a `Bad`.
        assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                import List ( fold )
                data Ok = { n: Int }
                data Bad = { why: Int }
                data R = Ok | Bad
                data In = { xs: List<Int> }
                behavior go : (i: In) -> Ok | Bad constructs Ok, Bad
                let go (i) = fold((acc, x) -> bump(acc, x), Ok { n = 0 }, i.xs)
                let bump (acc: Ok, x: Int) : R =
                    if x > 0 then Ok { n = acc.n + x } else Bad { why = x }
                """));
    }

    @Test
    void aFoldGrowingAListToAWiderElementIsNotWidenedAsASum() {
        // The seed is `List<A>` and the block returns `List<A | B>`. That target is a list, not a sum,
        // so the sum-widening branch does not apply and the accumulator-type mismatch still holds.
        assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                import List ( fold )
                data A = { x: Int }
                data B = { y: Int }
                data AB = A | B
                let go (xs: List<Int>) : List<A> = fold((acc, x) -> acc ++ [pick(x)], [A { x = 0 }], xs)
                let pick (x: Int) : AB = if x > 0 then A { x = x } else B { y = x }
                """));
    }

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

    @Test
    void aCaseSeededFoldThatStaysInThatCaseIsNotWidenedToItsSum() throws Exception {
        // `Cart` is a case of `R = Cart | Err`, but this fold seeds `Cart`, reads its field, and always
        // returns a `Cart` — it never grows to the sum. The accumulator stays `Cart`, so the behavior
        // may declare its output as the narrow `Cart`. (A fold is only widened to the sum when its step
        // actually matches on, or grows into, that sum.)
        String src = """
                module demo
                import List ( fold )
                data Cart = { total: Int }
                data Err = { code: Int }
                data R = Cart | Err
                data In = { xs: List<Int> }
                behavior total : (i: In) -> Cart constructs Cart
                let total (i) = fold((acc, x) -> Cart { total = acc.total + x }, Cart { total = 0 }, i.xs)
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object behavior = loader.loadClass("demo.Total" + "$Impl").getConstructor().newInstance();
        Object in = Codecs.decoded(loader, "demo.In", Map.of("xs", List.of(1L, 2L, 3L, 4L)));
        Map<?, ?> out = (Map<?, ?>) Codecs.encode(loader, "demo.Cart", Codecs.apply(behavior, in));
        assertEquals(10L, out.get("total"));
    }
}
