package souther.compiler.query;

import souther.compiler.check.DeclarationMeaning;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A declaration written somewhere else in its file is published as the same declaration.
 *
 * <p>What the boundary is for, asked of a store that has been edited rather than of two stores built
 * from two texts. The comparisons beside {@link souther.compiler.check.DeclarationMeaning} hold the
 * producer to reading the same things; this holds the answer a store keeps to coming back equal
 * after the edit that used to make every importer look again.
 *
 * <p>Both directions, because an answer that never changes stops work it should not stop. The
 * declaration is moved and then given a different rule, and the second is what says the first is
 * about the move.
 */
class AMovedDeclarationIsPublishedAsTheSameDeclarationTest {

    private static final String PRICES = """
            module shop.prices exposing ( Amount )

            data Amount = Int
                invariant value >= 0
            """;

    /** Imports it, so the workspace is one an edit could cross. */
    private static final String CART = """
            module shop.cart exposing ( Total )

            import shop.prices ( Amount )

            data Total = { paid: Amount }
            """;

    private static final TypeKey AMOUNT = new TypeKey("shop.prices", "Amount");

    @Test
    void movingADeclarationDownItsFileDoesNotChangeWhatItSays() {
        Compilation c = started();
        DeclarationMeaning before = published(c);

        edit(c, PRICES.replace("data Amount",
                "// a line written above it, which moves every position below\ndata Amount"));

        assertEquals(before, published(c),
                "a line written above the declaration is an edit no module importing it can see,"
                        + " and what it says came back different");
    }

    @Test
    void andGivingItAnotherRuleDoes() {
        Compilation c = started();
        DeclarationMeaning before = published(c);

        edit(c, PRICES.replace("invariant value >= 0", "invariant value >= 1"));

        assertNotEquals(before, published(c),
                "the declaration was given a rule it did not have and was published as the one it"
                        + " had before");
    }

    /** What the store says {@code shop.prices.Amount} says. */
    private static DeclarationMeaning published(Compilation c) {
        Answer<DeclarationMeaning> answer = c.db().ask(new Shapes.MeaningOf(AMOUNT));
        assertTrue(answer.present(), "the store has nothing to say about `" + AMOUNT + "`");
        return answer.value();
    }

    /** The same workspace with {@code prices} written into it. */
    private static void edit(Compilation c, String prices) {
        assertNotEquals(PRICES, prices, "this edit matched nothing, so nothing was compared");
        Map<String, String> edited = new LinkedHashMap<>();
        edited.put("prices.sou", prices);
        c.update(edited, Set.of());
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace still compiles after the edit");
    }

    private static Compilation started() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", PRICES);
        byId.put("cart.sou", CART);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace compiles to begin with");
        return c;
    }
}
