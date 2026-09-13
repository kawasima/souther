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
 * <p>Nothing that means what the declaration says. Writing the same two declarations the other way
 * round says nothing different about either of them, so what each publishes comes out the same and
 * the importer's body is not checked again.
 *
 * <p>A comment is the weaker of the two edits and is here for what it separates: a place is which of
 * the things written in a text it is, so a line written above a declaration does not move it at all
 * and never reaches the answers below it. Writing the declarations in the other order does move
 * them, which is the edit this is about.
 *
 * <p>What is measured is the importer keeping its answer and not merely working out the same one
 * again. An answer that comes out equal has already been paid for, and equality alone is met by a
 * store that recomputes the whole workspace on every keystroke.
 *
 * <p>Two kinds of importer answer are deliberately left out, because what they mean is not what the
 * declaration says. A module's scope is read off the whole table of what another module declares,
 * and what an example is run against is the classes its module reaches — which carry the lines the
 * declaration was written on. Both move when a declaration moves, and should.
 *
 * <p>The comparison beside all of it is what keeps this from being met by an importer that never
 * looks at the declaration at all: an edit that changes what the declaration says does reach it.
 */
class WhatCrossesAModuleBoundaryWhenADeclarationOnlyMovesTest {

    private static final String DECLARING = """
            module shop.prices exposing ( Amount )

            data Amount = Int
                invariant value >= 0

            data Note = Int
            """;

    /** The same two declarations, written the other way round. Nothing is added or taken away. */
    private static final String MOVED = """
            module shop.prices exposing ( Amount )

            data Note = Int

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
    void aCommentWrittenInTheDeclaringModuleDoesNotReachTheImporter() {
        Compilation c = started();
        Answer<?> inputs = c.db().ask(new Adequacy.Inputs("shop.cart"));
        Answer<?> checked = c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn"));
        Answer<?> published = c.db().ask(new Shapes.MeaningOf(AMOUNT));
        Answer<?> expanded = c.db().ask(new Shapes.ClausesExpandedFor(AMOUNT));

        edit(c, "// a line written above the declarations\n" + DECLARING);

        assertEquals(published.value(), c.db().ask(new Shapes.MeaningOf(AMOUNT)).value(),
                "what the declaration says is the same");
        assertEquals(expanded.value(), c.db().ask(new Shapes.ClausesExpandedFor(AMOUNT)).value(),
                "and so are the clauses it was expanded into: a comment is not a token");
        assertSame(checked, c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn")),
                "so the importer's body was not checked again");
        assertSame(inputs, c.db().ask(new Adequacy.Inputs("shop.cart")),
                "and what its rows are measured over was not worked out again");
    }

    /**
     * And the declarations written in the other order do not reach it either.
     *
     * <p>The edit the comment cannot make. Where a declaration stands is which of the things written
     * in the text it is, so this one moves both declarations while leaving what either says alone —
     * and the answers that hold where they are written do come out different. What is held here is
     * that the importer is not among them.
     */
    @Test
    void theDeclarationsWrittenInTheOtherOrderDoNotReachTheImporterEither() {
        Compilation c = started();
        Answer<?> inputs = c.db().ask(new Adequacy.Inputs("shop.cart"));
        Answer<?> checked = c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn"));
        Answer<?> module = c.db().ask(new Bodies.Checked("shop.cart"));
        Answer<?> published = c.db().ask(new Shapes.MeaningOf(AMOUNT));
        Answer<?> written = c.db().ask(new Names.ResolvedDeclaration(AMOUNT));

        edit(c, MOVED);

        assertNotEquals(written.value(), c.db().ask(new Names.ResolvedDeclaration(AMOUNT)).value(),
                "the declaration really did move: what holds where it is written says so");
        assertEquals(published.value(), c.db().ask(new Shapes.MeaningOf(AMOUNT)).value(),
                "and what it says is the same");
        assertSame(checked, c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn")),
                "so the importer's body was not checked again");
        assertSame(module, c.db().ask(new Bodies.Checked("shop.cart")),
                "nor was the module it is written in");
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
        Answer<?> published = c.db().ask(new Shapes.MeaningOf(AMOUNT));
        Answer<?> checked = c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn"));

        edit(c, DECLARING.replace("invariant value >= 0", "invariant value >= 1"));

        assertNotEquals(published.value(), c.db().ask(new Shapes.MeaningOf(AMOUNT)).value(),
                "the declaration was given a rule it did not have");
        assertNotSame(checked, c.db().ask(new Bodies.CheckedBehavior("shop.cart", "paidOn")),
                "a body checked against the declaration was not checked again");
    }

    /**
     * The workspace with the declaring module written over, and the importer where it was.
     *
     * <p>Both documents, because an update is the whole workspace: handed the edited file alone,
     * this left the importer out of the compilation altogether, and every answer about it came back
     * absent. Which is not an answer moving — it is a module that is no longer there.
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
