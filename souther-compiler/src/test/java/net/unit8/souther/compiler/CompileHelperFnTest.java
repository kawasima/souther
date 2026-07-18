package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A helper {@code fn} — one with no matching behavior — writes its own parameter types (spec 13.1)
 * and is expanded inline at each call site (spec 12.5: a named fn is the same as an inline block).
 * This first caseName is a pure computation: it constructs no data and calls no injected behavior.
 */
class CompileHelperFnTest {

    private static final String MODULE = """
            module demo

            data Order = { price: Int, rate: Int }
            data Receipt = { subtotal: Int, total: Int }

            behavior bill : (o: Order) -> Receipt
                constructs Receipt

            let bill (o) = Receipt { subtotal = o.price, total = discount(o.price, o.rate) }

            let discount (price: Int, rate: Int) = price * rate
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object decode(BytesClassLoader loader, String type, Object input) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
        return ((Ok) d.decode(input, Path.ROOT)).value();
    }

    @Test
    void behaviorInlinesAPureHelper() throws Exception {
        BytesClassLoader loader = loader();
        Object bill = loader.loadClass("demo.Bill").getDeclaredConstructor().newInstance();

        Object order = decode(loader, "Order", java.util.Map.of("price", 6L, "rate", 7L));
        Object r = ((Behavior) bill).apply(order);

        Encoder enc = (Encoder) loader.loadClass("demo.Receipt").getMethod("encoder").invoke(null);
        java.util.Map<?, ?> receipt = (java.util.Map<?, ?>) enc.encode(r);
        assertEquals(6L, receipt.get("subtotal"));
        assertEquals(42L, receipt.get("total"), "the inlined helper computed price * rate");
    }

    /**
     * A helper does not declare {@code constructs}; the data it builds is inlined into the caller,
     * so the caller must hold the permission (spec 12.5). A behavior whose helper constructs a data
     * it does not declare is E1002 — the inline expansion makes the construction the caller's.
     */
    @Test
    void aHelpersConstructionCountsAgainstTheCallersPermission() {
        // the helper `makeTag` builds `Tag`, attributed to the caller `label`: `Blank` is declared
        // but `Tag` (via the helper) is also built, so the undeclared `Tag` is E1002.
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo
                import String ( length )

                data Id = String
                data Tag = { a: String, b: String }
                data Blank

                behavior label : (id: Id) -> Tag | Blank constructs Blank

                let label (id) = if length(id.value) > 0 then makeTag(id) else Blank

                let makeTag (id: Id) = Tag { a = id.value, b = id.value }
                """));
        assertEquals("E1002", e.code(), "the caller must declare `constructs Tag` for the helper's build");
    }

    /** Declaring the helper's construction lets the behavior compile (spec 12.5). */
    @Test
    void declaringTheHelpersConstructionCompiles() {
        Compiler.compile("""
                module demo

                data Id = String
                data Tag = { a: String, b: String }

                behavior label : (id: Id) -> Tag
                    constructs Tag

                let label (id) = makeTag(id)

                let makeTag (id: Id) = Tag { a = id.value, b = id.value }
                """);
    }

    /** A helper is expanded inline, so it must bottom out; mutual recursion is rejected (spec 13.1). */
    @Test
    void mutuallyRecursiveHelpersAreRejected() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                data Id = String

                behavior noop : (id: Id) -> Id

                let noop (id) = id

                let ping (x: Int) = pong(x)
                let pong (x: Int) = ping(x)
                """));
        assertEquals(null, e.code());
    }

    /** A helper may call another helper; the expansion nests, α-renaming each level (spec 12.5). */
    @Test
    void helpersNestOneCallingAnother() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data Order = { price: Int, rate: Int }
                data Receipt = { subtotal: Int, total: Int }

                behavior bill : (o: Order) -> Receipt
                    constructs Receipt

                let bill (o) = Receipt { subtotal = o.price, total = taxed(o.price, o.rate) }

                let taxed (price: Int, rate: Int) = price * withRate(rate)
                let withRate (rate: Int) = rate * rate
                """), getClass().getClassLoader());
        Object bill = loader.loadClass("demo.Bill").getDeclaredConstructor().newInstance();
        Object order = decode(loader, "Order", java.util.Map.of("price", 2L, "rate", 3L));
        Object r = ((Behavior) bill).apply(order);
        Encoder enc = (Encoder) loader.loadClass("demo.Receipt").getMethod("encoder").invoke(null);
        java.util.Map<?, ?> receipt = (java.util.Map<?, ?>) enc.encode(r);
        assertEquals(18L, receipt.get("total"), "2 * (3 * 3)");
    }
}
