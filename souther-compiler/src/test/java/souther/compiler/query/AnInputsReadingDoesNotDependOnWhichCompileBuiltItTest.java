package souther.compiler.query;

import souther.compiler.meta.ModulePath;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a reading of an input is, said of two compiles of one text.
 *
 * <p>A reading is answered out of the declarations a behavior's parameters reach, and those are read
 * into trees a compile builds for itself. Two compiles build two sets of them, so a reading that
 * held any of what a compile happened to allocate would come back different from a run that read
 * the same source — and every measure that reads a reading would be taken again for a compile that
 * learned nothing.
 *
 * <p>Held to the value and to the hash, because both are what a store compares an answer by, and to
 * a reading that came out of another compile's objects rather than to a second reading of one
 * compile's, which would be one object answering about itself.
 */
class AnInputsReadingDoesNotDependOnWhichCompileBuiltItTest {

    private static final String DECLARING = """
            module shop.prices exposing ( Amount )

            data Amount = Int
                invariant value >= 0
            """;

    /**
     * And one no finite machine reads, so the reading is short of something a rule is answerable
     * for.
     *
     * <p>Beside the one above and not instead of it. What a reading holds of a rule it took in and
     * what it holds of one it gave up on are written down by different parts of it, and a rule the
     * reading read all of leaves the second of those empty — so a text of only readable rules holds
     * this to nothing about the accounting a shortfall goes into.
     */
    private static final String DECLARING_UNREADABLE = """
            module shop.prices exposing ( Amount )

            data Amount = String
                invariant shape = String.matches("(a+)\\\\1.*", value)
            """;

    private static final String IMPORTING = """
            module shop.cart exposing ( Basket, paidOn )

            import shop.prices ( Amount )

            data Basket = { paid: Amount }

            behavior paidOn : (t: Basket) -> Int
            let paidOn (t) = t.paid.value

            example paidOn
                | "one" : (Basket { paid = Amount { value = 1 } }) -> 1
            """;

    private static final String IMPORTING_STRING = """
            module shop.cart exposing ( Basket, paidOn )

            import shop.prices ( Amount )

            data Basket = { paid: Amount }

            behavior paidOn : (t: Basket) -> String
            let paidOn (t) = t.paid.value

            example paidOn
                | "one" : (Basket { paid = Amount { value = "aabb" } }) -> "aabb"
            """;

    @Test
    void twoCompilesOfOneTextReadTheInputTheSameWay() {
        sameFromTwoCompiles(DECLARING, IMPORTING);
    }

    @Test
    void andSoWhereTheDeclarationWritesARuleNoFiniteMachineReads() {
        sameFromTwoCompiles(DECLARING_UNREADABLE, IMPORTING_STRING);
    }

    /**
     * And a text that says something else is read as something else.
     *
     * <p>Without this the two above are met by a reading that holds nothing of the declaration at
     * all, which is the other way for an answer to be the same every time.
     */
    @Test
    void andATextThatSaysSomethingElseIsReadAsSomethingElse() {
        Object one = inputsOf(started(DECLARING, IMPORTING));
        Object other = inputsOf(started(
                DECLARING.replace("invariant value >= 0", "invariant value >= 1"), IMPORTING));
        assertNotEquals(one, other, "the declaration was given a rule it did not have");
    }

    private static void sameFromTwoCompiles(String prices, String cart) {
        Object one = inputsOf(started(prices, cart));
        Object other = inputsOf(started(prices, cart));
        assertNotSame(one, other, "two compiles answered with one object, so this compares nothing");
        assertEquals(one, other, "a reading of one text came out different from another compile's");
        assertEquals(one.hashCode(), other.hashCode(),
                "the readings are equal and hash apart, so a store keyed by them holds two");
    }

    private static Object inputsOf(Compilation c) {
        return c.db().ask(new Adequacy.Inputs("shop.cart")).value();
    }

    private static Compilation started(String prices, String cart) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", prices);
        byId.put("cart.sou", cart);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace compiles to begin with: "
                + c.db().allReports());
        return c;
    }
}
