package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A {@code fake} supplies a test double for an injected {@code requires} dependency so an example can
 * evaluate a behavior that depends on it: a {@code with dep = value} on the row (a value dependency)
 * or a {@code fake dep | table} declaration (a function dependency). A missing fake, a table miss,
 * and examples of an injected behavior itself are diagnosed.
 */
class CompileFakeTest {

    private static CompileException err(String model) {
        return assertThrows(CompileException.class, () -> Compiler.compile(model));
    }

    // --- value dependency via `with` ----------------------------------------------------------

    private static final String CLOCK = """
            module demo

            data 申請 = { 額: Int }
            data 受理 = { 時刻: String }

            behavior 現在時刻 : () -> String

            behavior 受け付ける : (a: 申請) -> 受理
                requires 現在時刻
                constructs 受理

            let 受け付ける (a, 現在時刻) = 受理 { 時刻 = 現在時刻() }
            """;

    @Test
    void valueDependencyIsFakedWithWith() {
        String ok = CLOCK + """
                example 受け付ける
                  | (申請 { 額 = 1 }) with 現在時刻 = "2026-07-20T09:00" -> 受理 { 時刻 = "2026-07-20T09:00" }
                """;
        assertDoesNotThrow(() -> Compiler.compile(ok));
    }

    @Test
    void valueDependencyMismatchIsE1905() {
        String bad = CLOCK + """
                example 受け付ける
                  | (申請 { 額 = 1 }) with 現在時刻 = "2026-07-20T09:00" -> 受理 { 時刻 = "wrong" }
                """;
        assertEquals("E1905", err(bad).diagnostic().code());
    }

    @Test
    void aRequiresWithoutAFakeIsE1908() {
        String bad = CLOCK + """
                example 受け付ける
                  | (申請 { 額 = 1 }) -> 受理 { 時刻 = "2026-07-20T09:00" }
                """;
        assertEquals("E1908", err(bad).diagnostic().code());
    }

    @Test
    void examplingAnInjectedBehaviorIsE1902() {
        String bad = CLOCK + """
                example 現在時刻
                  | () -> 受理
                """;
        assertEquals("E1902", err(bad).diagnostic().code());
    }

    // --- function dependency via `fake` table -------------------------------------------------

    private static final String LOOKUP = """
            module demo
            import String ( length )

            data 従業員ID = String
                invariant length(value) > 0

            data 決定 = { 承認者: 従業員ID }

            behavior 上長を探す : (id: 従業員ID) -> 従業員ID

            behavior 承認者を決める : (申請者: 従業員ID) -> 決定
                requires 上長を探す
                constructs 決定

            let 承認者を決める (申請者, 上長を探す) = 決定 { 承認者 = 上長を探す(申請者) }
            """;

    @Test
    void functionDependencyIsFakedWithATable() {
        String ok = LOOKUP + """
                fake 上長を探す
                  | (従業員ID("e-1")) -> 従業員ID("boss-1")

                example 承認者を決める
                  | (従業員ID("e-1")) -> 決定 { 承認者 = 従業員ID("boss-1") }
                """;
        assertDoesNotThrow(() -> Compiler.compile(ok));
    }

    @Test
    void aTableMissIsE1909() {
        String bad = LOOKUP + """
                fake 上長を探す
                  | (従業員ID("e-1")) -> 従業員ID("boss-1")

                example 承認者を決める
                  | (従業員ID("e-9")) -> 決定 { 承認者 = 従業員ID("boss-1") }
                """;
        assertEquals("E1909", err(bad).diagnostic().code());
    }

    @Test
    void aFakeAndExampleEmitNoClass() {
        // The fake is a proxy at evaluation and the example is checked at compile time, so neither
        // adds anything to the generated output (zero Jar footprint).
        var bare = Compiler.compile(LOOKUP).keySet();
        String withFakeAndExample = LOOKUP + """
                fake 上長を探す
                  | (従業員ID("e-1")) -> 従業員ID("boss-1")

                example 承認者を決める
                  | (従業員ID("e-1")) -> 決定 { 承認者 = 従業員ID("boss-1") }
                """;
        var withExtra = Compiler.compile(withFakeAndExample).keySet();
        assertEquals(bare, withExtra, "a fake and an example must not emit any class");
    }

    @Test
    void aWildcardDefaultCoversAMiss() {
        String ok = LOOKUP + """
                fake 上長を探す
                  | (従業員ID("e-1")) -> 従業員ID("boss-1")
                  | _ -> 従業員ID("unknown")

                example 承認者を決める
                  | (従業員ID("e-9")) -> 決定 { 承認者 = 従業員ID("unknown") }
                """;
        assertDoesNotThrow(() -> Compiler.compile(ok));
    }

    // --- a dependency that returns a sum, faked per case ---------------------------------------

    private static final String RESERVE = """
            module demo
            import String ( length )

            data OrderId = String
                invariant length(value) > 0

            data Confirmed = { id: OrderId }
            data OutOfStock

            behavior reserve : (id: OrderId) -> Confirmed | OutOfStock

            behavior place : (id: OrderId) -> Confirmed | OutOfStock
                requires reserve

            let place (id, reserve) = reserve(id)
            """;

    @Test
    void aSumReturningDependencyIsFakedPerCase() {
        // Each fake row names one case of the sum, decoded against that case (the declared sum has no
        // single decoder): a record case, and a unit case as the `_` default.
        String ok = RESERVE + """
                fake reserve
                  | (OrderId("ok")) -> Confirmed { id = OrderId("ok") }
                  | _               -> OutOfStock

                example place
                  | "reserved"     : (OrderId("ok")) -> Confirmed { id = OrderId("ok") }
                  | "out of stock" : (OrderId("no")) -> OutOfStock
                """;
        assertDoesNotThrow(() -> Compiler.compile(ok));
    }

    @Test
    void aSumFakeStillCatchesAMismatch() {
        // The fake maps "ok" to Confirmed, so asserting OutOfStock for it does not hold.
        String bad = RESERVE + """
                fake reserve
                  | (OrderId("ok")) -> Confirmed { id = OrderId("ok") }
                  | _               -> OutOfStock

                example place
                  | (OrderId("ok")) -> OutOfStock
                """;
        assertEquals("E1905", err(bad).diagnostic().code());
    }
}
