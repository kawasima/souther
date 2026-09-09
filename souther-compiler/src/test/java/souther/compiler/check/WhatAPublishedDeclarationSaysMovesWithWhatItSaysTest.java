package souther.compiler.check;

import souther.compiler.query.Compilation;
import souther.compiler.query.Db;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A published declaration says something else when the declaration does, and the same thing when it
 * only moved.
 *
 * <p>What the audit beside this cannot see. Reading every component a declaration says is not the
 * same as carrying what they say: a producer that gathered the fields into a map would read both of
 * them and publish an answer two declarations laid out differently share, and every accessor it
 * called would be accounted for. So the order the parts are in is held here, by editing a
 * declaration in a way that changes nothing else.
 *
 * <p>Each edit below changes one thing. What a reader elsewhere is told has to move with the four
 * that change what the declaration says, and stay put for the one that changes only where it is.
 */
class WhatAPublishedDeclarationSaysMovesWithWhatItSaysTest {

    private static final String DECLARING = """
            module demo

            data Named = { name: String }
                invariant String.length(name) >= 1

            data Priced = { amount: Int }
                invariant amount >= 0

            data Item = { ...Named, ...Priced }

            data Pair = { a: Int, b: Int }

            data Line = { product: String, qty: Int }
                invariant qty >= 1
                invariant String.length(product) >= 1
            """;

    @Test
    void aDeclarationThatOnlyMovedSaysTheSameThing() {
        assertEquals(published("Line", DECLARING),
                published("Line", requireEdited(DECLARING, "data Named",
                        "// a line written above every declaration below it\ndata Named")),
                "a line written above a declaration moves every position under it and changes"
                        + " nothing any of them says");
    }

    @Test
    void aProductWhoseFieldsAreLaidOutDifferentlySaysSomethingElse() {
        assertNotEquals(published("Pair", DECLARING),
                published("Pair",
                        requireEdited(DECLARING, "{ a: Int, b: Int }", "{ b: Int, a: Int }")),
                "the fields are the parameters of the entry a value is built through, in order, so"
                        + " a module elsewhere builds one of these two differently");
    }

    @Test
    void aFieldOfAnotherTypeSaysSomethingElse() {
        assertNotEquals(published("Pair", DECLARING),
                published("Pair",
                        requireEdited(DECLARING, "{ a: Int, b: Int }", "{ a: Int, b: Decimal }")),
                "what a field is is what a value of the product holds there");
    }

    @Test
    void aProductThatSpreadsInAnotherOrderSaysSomethingElse() {
        assertNotEquals(published("Item", DECLARING),
                published("Item", requireEdited(DECLARING, "{ ...Named, ...Priced }",
                        "{ ...Priced, ...Named }")),
                "what a product spreads is reached in the order it is written, and the fields it"
                        + " reaches are laid out in that order");
    }

    @Test
    void aDeclarationWhoseClausesAreWrittenInAnotherOrderSaysSomethingElse() {
        // Written with the indent spelled out rather than as a text block: a block strips the indent
        // its own lines share, and a search string that lost it matches nothing in the source it is
        // looking through — which is an edit that did not happen and a comparison of one thing with
        // itself.
        String asWritten = "    invariant qty >= 1\n"
                + "    invariant String.length(product) >= 1";
        String swapped = "    invariant String.length(product) >= 1\n"
                + "    invariant qty >= 1";
        assertNotEquals(published("Line", DECLARING),
                published("Line", requireEdited(DECLARING, asWritten, swapped)),
                "a clause is addressed by which of the declaration's own it is, so the order they"
                        + " are written in is what a reader elsewhere names them by");
    }

    /**
     * {@code source} with {@code from} written as {@code to}, where that changed something.
     *
     * <p>Every comparison here rests on the edit having happened. A search string that matches
     * nothing leaves the source as it was, and what the assertion then compares is one declaration
     * with itself — which reads as the producer having carried the difference for an equality, and
     * as the producer having lost it for an inequality. Neither is what the edit was for.
     */
    private static String requireEdited(String source, String from, String to) {
        String edited = source.replace(from, to);
        assertNotEquals(source, edited, "this edit matched nothing, so nothing was compared: " + from);
        return edited;
    }

    /** What {@code demo} publishes about {@code declared}, over a compilation of {@code source}. */
    private static DeclarationMeaning published(String declared, String source) {
        Compilation compilation = Compilation.ofSources(List.of(source), null);
        compilation.answerEverything();
        assertEquals(List.of(),
                compilation.errors().stream().map(each -> each.diagnostic().code()).toList(),
                "this source is supposed to compile");
        Db db = compilation.db();
        return ClauseReadings.meaningOf(db, "demo",
                TypeSymbols.declared(new TypeKey("demo", declared)));
    }
}
