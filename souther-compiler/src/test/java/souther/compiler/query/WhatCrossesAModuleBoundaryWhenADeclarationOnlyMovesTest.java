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
 * What an edit that only moves a declaration reaches in the module that imports it.
 *
 * <p>The chain #1472 was opened about, written down where it can be watched while the readers are
 * moved one at a time. A comment line written in the declaring module moves every position under it
 * and says nothing different; the importer's answers were worked out again for it, through the
 * declaration answers that carry the authored tree.
 *
 * <p><b>Written to be red until it is not.</b> Each answer below is asked before and after the edit
 * and held to whichever of the two it is today, so moving a reader onto the boundary shows up here
 * as this test failing rather than as nothing. What it is holding is the measurement, not the goal:
 * a line saying an answer is remade is a line to change when it stops being remade, and the
 * comparison beside it — that an edit changing what the declaration says does reach the importer —
 * is what keeps the goal from being met by an answer that never moves.
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

    @Test
    void aCommentWrittenInTheDeclaringModuleStillReachesTheImporter() {
        Compilation c = started();
        Answer<?> inputs = c.db().ask(new Adequacy.Inputs("shop.cart"));
        Answer<?> checked = c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn"));
        Answer<?> published = c.db().ask(new Shapes.MeaningOf(
                new souther.compiler.types.TypeKey("shop.prices", "Amount")));
        Answer<?> expanded = c.db().ask(new Shapes.ClausesExpandedFor(
                new souther.compiler.types.TypeKey("shop.prices", "Amount")));

        edit(c, "// a line written above the declaration\n" + DECLARING);

        // Worked out again and come out the same, which is what stops the work above it. The answer
        // is a new object either way -- what an edit is absorbed by is the value being equal, not
        // the store having skipped the question.
        assertEquals(published.value(), c.db().ask(new Shapes.MeaningOf(
                        new souther.compiler.types.TypeKey("shop.prices", "Amount"))).value(),
                "what the declaration says is the same, and the boundary answer moved");
        // Which answer carries the move across. The clauses a reading is answered from are the
        // declaration's own, in the representation its module expanded them into -- an authored
        // tree, so moving the declaration makes a different one.
        assertNotEquals(expanded.value(), c.db().ask(new Shapes.ClausesExpandedFor(
                        new souther.compiler.types.TypeKey("shop.prices", "Amount"))).value(),
                "the clauses came out the same, so this is no longer what carries the move");
        assertNotSame(checked, c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn")),
                "the importer's checked body no longer moves for a comment written next door —"
                        + " which is the goal, so this line is the one to change");
        assertNotSame(inputs, c.db().ask(new Adequacy.Inputs("shop.cart")),
                "the importer's inputs no longer move for a comment written next door — which is"
                        + " the goal, so this line is the one to change");
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
                new souther.compiler.types.TypeKey("shop.prices", "Amount")));
        Answer<?> checked = c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn"));

        edit(c, DECLARING.replace("invariant value >= 0", "invariant value >= 1"));

        assertNotEquals(published.value(), c.db().ask(new Shapes.MeaningOf(
                        new souther.compiler.types.TypeKey("shop.prices", "Amount"))).value(),
                "the declaration was given a rule it did not have");
        assertNotSame(checked, c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn")),
                "a body checked against the declaration was not checked again");
    }

    private static void edit(Compilation c, String prices) {
        Map<String, String> edited = new LinkedHashMap<>();
        edited.put("prices.sou", prices);
        c.update(edited, Set.of());
        c.answerEverything();
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
