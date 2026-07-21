package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A behavior may be imported and composed two hops away (spec 4, 14): module {@code c} composes
 * {@code b}'s {@code twice}, whose own definition composes {@code a}'s {@code inc}. Resolving
 * {@code twice}'s signature for {@code c} has to resolve {@code b}'s imports in turn, not just
 * {@code b}'s own definitions — an import chain deeper than one hop.
 */
class CompileModuleChainTest {

    private static final String A = """
            module a exposing ( N, inc )
            data N = Int
            behavior inc : (n: N) -> N
            let inc (n) = n
            """;
    private static final String B = """
            module b exposing ( twice : N )
            import a ( N, inc )
            behavior twice = inc >-> inc
            """;
    // c imports N as well: its generated `quad` carries a `Behavior<N, N>` signature (spec 19.8,
    // 24), so N's class must be nameable here — the same reason Java code importing a generic method
    // imports its type arguments.
    private static final String C = """
            module c
            import a ( N )
            import b ( twice )
            behavior quad = twice >-> twice
            """;

    @Test
    void composesABehaviorTwoImportHopsAway() throws Exception {
        Map<String, byte[]> classes = Compiler.compileModules(List.of(A, B, C));
        assertTrue(classes.containsKey("c.Quad"), "the two-hop composition generates c.Quad");

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());
        Object five = Codecs.decoded(loader, "a.N", 5L);
        Object quad = loader.loadClass("c.Quad" + "$Impl").getConstructor().newInstance();
        Object r = Codecs.apply(quad, five);

        assertEquals(5L, Codecs.encode(loader, "a.N", r), "inc is identity, so quad round-trips 5 through the chain");
    }

    // A `>->` whose departed case comes from an imported behavior would need that upstream case to
    // join the composition's sealed result union, which it cannot (its class is in another package).
    // The compiler rejects it (E1606) rather than emit a union permitting a case it never generates.
    private static final String PA = """
            module pa exposing ( Priced, Empty, price )
            data Priced = { total: Int }
            data Empty
            behavior price : (amount: Int) -> Priced | Empty
                constructs Priced, Empty
            let price (amount) = if amount > 0 then Priced { total = amount } else Empty
            """;
    private static final String PB = """
            module pb
            import pa ( Priced, Empty, price )
            data Receipt = { total: Int }
            behavior bill : (p: Priced) -> Receipt
                constructs Receipt
            let bill (p) = Receipt { total = p.total }
            behavior checkout = price >-> bill
            """;

    @Test
    void aDepartedImportedCaseInACompositionUnionIsRejected() {
        // checkout's output is Receipt | Empty; Empty is imported from pa, so it cannot be a member of
        // pb's sealed CheckoutResult.
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(PA, PB)));
        assertEquals("E1606", e.diagnostic().code());
    }
}
