package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A behavior's generated class capitalizes its first letter (spec 19.5). Data names are already
 * capitalized, so {@code behavior quote} producing {@code data Quote} would generate two classes
 * named {@code Quote}. The compiler rejects the collision rather than let one class silently
 * overwrite the other.
 */
class CompileBehaviorNameCollisionTest {

    @Test
    void aBehaviorWhoseClassCollidesWithADataIsRejected() {
        String src = """
                module demo
                data Price = Decimal
                data Quote = { subtotal: Decimal }
                behavior quote : (p: Price) -> Quote constructs Quote
                let quote (p) = Quote { subtotal = p.value }
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("Quote"), e.getMessage());
    }

    @Test
    void twoBehaviorsWhoseClassesCollideAreRejected() {
        // `run` and `Run` capitalize to the same class name.
        String src = """
                module demo
                data N = Int
                behavior run : (n: N) -> N constructs N
                let run (n) = n
                behavior Run : (n: N) -> N constructs N
                let Run (n) = n
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }

    @Test
    void distinctBehaviorAndDataNamesCompile() {
        // The idiomatic split: a verb behavior and its noun output that do not share a base name.
        String src = """
                module demo
                data Price = Decimal
                data Quote = { subtotal: Decimal }
                behavior makeQuote : (p: Price) -> Quote constructs Quote
                let makeQuote (p) = Quote { subtotal = p.value }
                """;
        assertTrue(Compiler.compile(src).containsKey("demo.MakeQuote"),
                "the behavior class is MakeQuote, distinct from data Quote");
        assertTrue(Compiler.compile(src).containsKey("demo.Quote"), "the data class is still Quote");
    }
}
