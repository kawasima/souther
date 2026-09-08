package souther.compiler.query;

import souther.compiler.Emitted;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.generated.EvaluationArtifact;
import souther.compiler.meta.ModulePath;
import souther.compiler.meta.PublishedClasses;
import souther.compiler.observe.ArmObservation;
import souther.compiler.observe.Disposition;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A module's declarations are written into the classes it publishes and into nothing else.
 *
 * <p>They are there so that another compilation can import the module from a jar without its
 * source, so they are written down as the author wrote them — a declaration's own text, comments
 * and all. That makes them move when nothing about the program does, which is the whole reason the
 * classes an evaluation runs against must not carry them: a row of a module would otherwise be
 * something to establish again whenever anybody typed a comment near a declaration it names.
 *
 * <p>Which is a claim about two artifacts and not about a speed, so it is asked as one. What ships
 * carries the declarations and reads back; what runs carries none of them and the rows still hold.
 */
class WhatShipsCarriesTheDeclarationsAndWhatRunsDoesNotTest {

    /**
     * Carries a comment of its own, so that the edit below can reword it.
     *
     * <p>Reworded rather than written, and to a line of the same width, so that the edit moves no
     * position: what the two texts differ in is what a declaration publishes and nothing else. An
     * edit that added the line would move every position under it, and the answers about a module
     * carry positions, so the round would be timing that as well.
     */
    private static final String PRICES = """
            module shop.prices exposing ( Amount )

            // What an amount is here.
            data Amount = Int
                invariant value >= 0
            """;

    private static final String REWORDED = PRICES.replace(
            "// What an amount is here.",
            "// What a price is here..");

    /** Imports it, so what an evaluation of this loads is two modules' classes and not one. */
    private static final String CART = """
            module shop.cart exposing ( Total )

            import shop.prices ( Amount )

            data Total = { paid: Amount }

            behavior settle : (paid: Amount) -> Total
                constructs Total

            let settle (paid) = Total { paid = paid }
            """;

    private static final String ROWS = """
            examples for shop.cart

            example settle
                | "one" : (Amount(3)) -> Total
            """;

    private static final SourceId ROWS_AT = new SourceId("cart-examples.sou");

    private static Map<String, String> workspace(String prices) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", prices);
        byId.put("cart.sou", CART);
        byId.put(ROWS_AT.value(), ROWS);
        return byId;
    }

    private static Compilation started() {
        Compilation compilation =
                Compilation.ofDocuments(workspace(PRICES), Set.of(), ModulePath.EMPTY);
        compilation.answerEverything();
        assertEquals(List.of(), compilation.db().allReports().stream().map(String::valueOf).toList(),
                "the workspace compiles to begin with");
        return compilation;
    }

    /**
     * A comment written above a declaration is published, and reaches nothing that runs.
     *
     * <p>Both halves, because either alone would pass on a compiler that had this wrong. That the
     * evaluation artifact was kept says nothing unless the edit was one publication had to take in,
     * and an edit publication ignores would be kept by everything.
     */
    @Test
    void aCommentIsPublishedAndDoesNotReachWhatTheModuleIsEvaluatedAgainst() {
        Compilation compilation = started();
        Answer<?> ships = compilation.db().ask(new Output.Classes("shop.prices"));
        Answer<?> runs = compilation.db()
                .ask(new Output.Evaluated("shop.prices", ArmObservation.OMIT));

        compilation.update(workspace(REWORDED), Set.of());
        compilation.answerEverything();

        assertNotSame(ships, compilation.db().ask(new Output.Classes("shop.prices")),
                "the classes that ship carry the declaration as it is now written");
        assertSame(runs, compilation.db()
                        .ask(new Output.Evaluated("shop.prices", ArmObservation.OMIT)),
                "and the classes the module is evaluated against are the ones they were");
    }

    /** The class the declarations go on is published, and is not among the classes that run. */
    @Test
    void theClassTheDeclarationsGoOnIsPublishedAndIsNotRun() {
        Compilation compilation = started();
        String moduleClass = Emitted.declarations("shop.prices");

        Map<String, ClassFileImage> ships =
                compilation.db().ask(new Output.Classes("shop.prices")).value();
        EvaluationArtifact runs = compilation.db()
                .ask(new Output.Evaluated("shop.prices", ArmObservation.OMIT)).value();

        assertNotNull(ships);
        assertNotNull(runs);
        assertTrue(ships.containsKey(moduleClass),
                "what ships carries the class an importer reads the declarations off");
        assertFalse(runs.classes().containsKey(moduleClass),
                "what runs carries no class that exists to be read rather than run");
    }

    /** And what ships reads back, which is what carrying them is for. */
    @Test
    void whatShipsReadsBackAsTheModuleItWasWrittenAs() {
        Compilation compilation = started();
        compilation.db().ask(new Output.All());

        PublishedClasses.Carried carried =
                Output.declarationsRead(compilation.db()).of(Emitted.declarations("shop.prices"));

        PublishedClasses.Carried.Declared declared = assertInstanceOf(
                PublishedClasses.Carried.Declared.class, carried,
                "a compile reads the declarations off the classes it publishes");
        assertNotNull(declared.declarations().module(),
                "the class the declarations go on carries the module's own");
        assertTrue(declared.declarations().module().header().contains("shop.prices"),
                () -> "the header is the module as its source declared it, and says `"
                        + declared.declarations().module().header() + "`");
    }

    /**
     * A row still holds against classes that carry none of this.
     *
     * <p>Written across an import, so that what the row runs against is the classes of a module it
     * names as well as its own — the case where a class missing from one of them would be found by
     * failing to load rather than by anything about the model.
     */
    @Test
    void aRowHoldsAgainstClassesThatCarryNoDeclarations() {
        Compilation compilation = started();

        Output.Examples.Of rows = compilation.db()
                .ask(new Output.Examples("shop.cart", ROWS_AT, ArmObservation.OMIT)).value();

        assertNotNull(rows);
        assertEquals(List.of(Disposition.HELD),
                rows.rows().stream().map(row -> row.disposition()).toList(),
                "the row runs and holds");
    }
}
