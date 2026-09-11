package souther.compiler.query;

import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an edit that only moves a declaration reaches in the module that imports it.
 *
 * <p>Nothing. A comment line written above a declaration says nothing different about it, and a
 * place is which of the things written in a text it is — so the declaration's clauses come out the
 * same, what it publishes comes out the same, and the importer's body is not checked again.
 *
 * <p>This was written to be red until it was not. Every line of it said an answer was remade, and
 * said beside itself that the line was the one to change when it stopped being; what it holds now
 * is that they are not. The comparison beside it is what keeps this from being met by an importer
 * that never looks at the declaration at all: an edit that changes what the declaration says does
 * reach it.
 */
class WhatCrossesAModuleBoundaryWhenADeclarationOnlyMovesTest {

    private static final String DECLARING = """
            module shop.prices exposing ( Amount )

            data Amount = Int
                invariant value >= 0
            """;

    /** Imports it, names it in a signature, and writes a row about it, so the importer measures. */
    private static final String IMPORTING = """
            module shop.cart exposing ( Basket, paidOn )

            import shop.prices ( Amount )

            data Basket = { paid: Amount }

            behavior paidOn : (t: Basket) -> Int
            let paidOn (t) = t.paid.value

            example paidOn
                | "one" : (Basket { paid = Amount { value = 1 } }) -> 1
            """;

    private static final TypeKey AMOUNT = new TypeKey("shop.prices", "Amount");

    @Test
    void aCommentWrittenInTheDeclaringModuleStillReachesTheImporter() {
        Compilation c = started();
        Answer<?> inputs = c.db().ask(new Adequacy.Inputs("shop.cart"));
        Answer<?> checked = c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn"));
        Answer<?> published = c.db().ask(new Shapes.MeaningOf(
                AMOUNT));
        Answer<?> expanded = c.db().ask(new Shapes.ClausesExpandedFor(
                AMOUNT));

        edit(c, "// a line written above the declaration\n" + DECLARING);

        // Worked out again and come out the same, which is what stops the work above it. The answer
        // is a new object either way -- what an edit is absorbed by is the value being equal, not
        // the store having skipped the question.
        assertEquals(published.value(), c.db().ask(new Shapes.MeaningOf(
                        AMOUNT)).value(),
                "what the declaration says is the same");
        assertEquals(expanded.value(), c.db().ask(new Shapes.ClausesExpandedFor(
                        AMOUNT)).value(),
                "and so are the clauses it was expanded into: a comment is not a token");
        assertSame(checked, c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn")),
                "so the importer's body was not checked again");
        assertSame(inputs, c.db().ask(new Adequacy.Inputs("shop.cart")),
                "and what its rows are measured over was not worked out again");
    }

    /**
     * And the edit that changes what the declaration says does reach it.
     *
     * <p>Without this the goal above is met by an importer that never looks at the declaration at
     * all, which is the other way to be wrong about a boundary.
     */
    @Test
    void andAnEditThatChangesWhatItSaysReachesTheImporter() {
        Compilation c = started();
        Answer<?> published = c.db().ask(new Shapes.MeaningOf(
                AMOUNT));
        Answer<?> checked = c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn"));

        edit(c, DECLARING.replace("invariant value >= 0", "invariant value >= 1"));

        assertNotEquals(published.value(), c.db().ask(new Shapes.MeaningOf(
                        AMOUNT)).value(),
                "the declaration was given a rule it did not have");
        assertNotSame(checked, c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn")),
                "a body checked against the declaration was not checked again");
    }

    /**
     * The workspace with the declaring module written over, and the importer where it was.
     *
     * <p>Both documents, because an update is the whole workspace: handed the edited file alone,
     * this left the importer out of the compilation altogether, and every answer about it came back
     * absent. Which is not an answer moving — it is a module that is no longer there — and it is
     * what two of the lines above used to be measuring.
     */
    private static void edit(Compilation c, String prices) {
        Map<String, String> edited = new LinkedHashMap<>();
        edited.put("prices.sou", prices);
        edited.put("cart.sou", IMPORTING);
        c.update(edited, Set.of());
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the edited workspace still compiles: "
                + c.db().allReports());
    }

    private static Compilation started() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", DECLARING);
        byId.put("cart.sou", IMPORTING);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace compiles to begin with: "
                + c.db().allReports());
        return c;
    }
}
