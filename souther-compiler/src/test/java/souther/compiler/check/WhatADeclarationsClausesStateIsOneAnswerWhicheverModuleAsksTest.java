package souther.compiler.check;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Db;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a declaration's clauses state is the declaring module's answer, whichever module asks.
 *
 * <p>The question a boundary has to answer before it can be one. An answer keyed by a declaration is
 * the declaring module's or it is not an answer about the declaration: asked from two modules and
 * coming back differently, it would be a value two readers hold under one name, and a store that
 * kept the first would hand the second somebody else's reading.
 *
 * <p>The reading is taken where the clauses become {@code Core} — {@link Clauses#typed} — because
 * that is the highest rung at which a clause has a form that says what it states and not how it was
 * written. Below it, {@link Clauses#statedAt} puts a construction's own values where the fields are
 * read, which is a fact about the use and not about the declaration.
 *
 * <p><b>The agreement alone is worth nothing, so three things are held beside it.</b> That the
 * clauses compared reached a form, so agreement is over something; that a clause given a different
 * rule reads as a different clause and one merely moved down the file does not; and that a reading
 * answers for the declaration it was asked about rather than for the spelling. Each is its own
 * assertion, so a failure says which.
 *
 * <p><b>Both arms are held, over two sources.</b> A reading answers a clause it has no form for with
 * {@link TypedClause.Stopped}, and nothing that compiles reaches that arm — the control below pins
 * that for the declarations swept here, by holding what reached a form to what was reached at all.
 * The arm itself is held over a source this compiler refuses, where a clause names something nothing
 * declares.
 */
class WhatADeclarationsClausesStateIsOneAnswerWhicheverModuleAsksTest {

    /**
     * What a module declares, in the shapes a clause is written over.
     *
     * <p>A newtype whose clause is arithmetic, a product whose clause reads two of its own fields, a
     * product whose clause reaches a list of another declaration, one whose clause calls a helper
     * this module defines, and one that spreads two others. The helper is the case a reader-module
     * dependency would show in: what a clause names is resolved somewhere, and a clause that names
     * only the language reaches the same answer in any scope by having nothing of the module in it.
     */
    private static final String DECLARING = """
            module demo.base exposing
                ( Amount, Line, Order, Even, Named, Priced, Item )

            data Amount = Decimal
                invariant value >= 0.00m

            data Line = { product: String, qty: Int }
                invariant qty >= 1 && String.length(product) >= 1

            data Order = { lines: List<Line>, total: Amount }
                invariant List.length(lines) >= 1

            let remainder (n: Int) : Int = Int.floorMod(n, 2)

            data Even = Int
                invariant remainder(value) == 0

            data Named = { name: String }
                invariant String.length(name) >= 1

            data Priced = { amount: Int }
                invariant amount >= 0

            data Item = { ...Named, ...Priced }
            """;

    /**
     * A module that imports every one of them and names each in a body.
     *
     * <p>Each is written bare somewhere, because an import nothing spells is reported rather than
     * reached, and what this test needs is a module whose scope has these declarations in it.
     */
    private static final String IMPORTING = """
            module demo.user exposing ( total, count, sound, label, priced )

            import demo.base as Base ( Amount, Line, Order, Even, Item )

            behavior total : (o: Order) -> Amount
            let total (o) = o.total

            behavior count : (l: Line) -> Int
            let count (l) = l.qty

            behavior sound : (e: Even) -> Int
            let sound (e) = e.value

            behavior label : (i: Item) -> String
            let label (i) = i.name

            behavior priced : (i: Item) -> Int
            let priced (i) = i.amount
            """;

    /**
     * A third module that declares the same spelling and reaches the first one's qualified.
     *
     * <p>What tells the comparison beside this from one that answers alike whatever it is asked. Two
     * modules declaring {@code Amount} is the case {@link Clauses#bindingsOf} names, and a reading
     * keyed by the declaration has to come back with the rule the declaring module wrote — not the
     * one the asking module wrote under that spelling, and not one that merged them.
     */
    private static final String RIVAL = """
            module demo.rival exposing ( Amount, theirs, mine )

            data Amount = Decimal
                invariant value >= 100.00m

            behavior theirs : (a: demo.base.Amount) -> Decimal
            let theirs (a) = a.value

            behavior mine : (a: Amount) -> Decimal
            let mine (a) = a.value
            """;

    /** The two modules of the sources above, in the order a sweep reads them. */
    private static final List<String> MODULES = List.of("demo.base", "demo.user");

    /**
     * The declarations {@code demo.user} imports, in the order its scope reaches them.
     *
     * <p>Written down rather than counted, because a count says nothing about which. {@code Item}
     * writes no clause of its own and is here all the same: what it spreads wrote two, and a reading
     * of {@code Item} reaches both.
     */
    private static final List<String> IMPORTED =
            List.of("Amount", "Line", "Order", "Even", "Item");

    @Test
    void anImportedDeclarationStatesTheSameThingInBothModules() {
        Db db = analysed(List.of(DECLARING, IMPORTING));
        List<ClauseReadings.Edge> edges = ClauseReadings.importsOf(db, MODULES);
        assertEquals(IMPORTED, edges.stream().map(each -> each.named().name()).toList(),
                "the sweep is not reaching the declarations these sources were written to import");
        for (ClauseReadings.Edge edge : edges) {
            assertEquals(ClauseReadings.readBy(db, edge.declaring(), edge.named()).stated(),
                    ClauseReadings.readBy(db, edge.asking(), edge.named()).stated(),
                    "`" + edge.named() + "` states one thing where it was written and another"
                            + " where `" + edge.asking() + "` reads it");
        }
    }

    /**
     * The control the comparison above is worth nothing without.
     *
     * <p>A reading in which nothing typed agrees with another in which nothing typed. So what is
     * counted is the clauses that reached a form, and it is counted from the importing module —
     * the side that would come back empty if a clause of somebody else's declaration were the thing
     * this reader could not type.
     */
    @Test
    void theImportingModuleTypesTheClausesItWasComparedOver() {
        Db db = analysed(List.of(DECLARING, IMPORTING));
        int stated = 0;
        int reached = 0;
        List<Clause.Ref> stopped = new ArrayList<>();
        for (ClauseReadings.Edge edge : ClauseReadings.importsOf(db, MODULES)) {
            ClauseReadings.Read read = ClauseReadings.readBy(db, edge.asking(), edge.named());
            stated += read.stated().size();
            reached += read.reached();
            stopped.addAll(read.stopped());
        }
        assertEquals(reached, stated,
                "the importing module reached " + reached + " clauses of declarations it imports"
                        + " and has a form for " + stated + " of them; these it could not type: "
                        + stopped);
        assertTrue(stated > 0,
                "no clause of an imported declaration reached a form, so the comparison beside this"
                        + " one compared nothing");
    }

    /**
     * And the control that says the comparison can come back unequal.
     *
     * <p>Two edits, and they are the two halves of what a boundary is for. One changes what a clause
     * states and must be a different reading; the other moves every position under it and states the
     * same thing, and must be the same reading. A comparison that failed the first would call two
     * declarations one; a comparison that failed the second is the defect this boundary is built to
     * end.
     */
    @Test
    void aClauseThatStatesSomethingElseIsANewReadingAndOneMerelyMovedIsNot() {
        Map<Clause.Ref, TermMeaning> asWritten = amountAsTheImporterReadsIt(DECLARING);
        Map<Clause.Ref, TermMeaning> stating = amountAsTheImporterReadsIt(
                DECLARING.replace("invariant value >= 0.00m", "invariant value >= 1.00m"));
        Map<Clause.Ref, TermMeaning> moved = amountAsTheImporterReadsIt(
                DECLARING.replace("data Amount = Decimal",
                        "// a line written above it, which moves every position below\n"
                                + "data Amount = Decimal"));

        assertTrue(!asWritten.isEmpty(), "`Amount` states a clause, or nothing was compared");
        assertNotEquals(asWritten, stating,
                "`Amount` was given a different rule and read as the same one");
        assertEquals(asWritten, moved,
                "`Amount` was moved down the file and read as a different declaration");
    }

    /**
     * A reading answers for the declaration it was asked about and not for the spelling.
     *
     * <p>Which is what says the agreement above is worth something. A {@code readBy} that answered
     * from the asking module's own declaration of the spelling would agree with itself everywhere,
     * and every comparison in this class would be green over a reading that never left home. So the
     * same key is asked of two modules one of which declares that spelling itself, and the two
     * declarations are asked of the one module that can see both.
     */
    @Test
    void aReadingAnswersForTheDeclarationAskedAboutAndNotForTheSpelling() {
        Db db = analysed(List.of(DECLARING, IMPORTING, RIVAL));
        TypeSymbol.AtModule base = TypeSymbols.declared(new TypeKey("demo.base", "Amount"));
        TypeSymbol.AtModule rival = TypeSymbols.declared(new TypeKey("demo.rival", "Amount"));

        assertEquals(ClauseReadings.readBy(db, "demo.base", base).stated(),
                ClauseReadings.readBy(db, "demo.rival", base).stated(),
                "`demo.base.Amount` is read as one thing where it was written and as another"
                        + " in a module that declares the spelling itself");

        List<TermMeaning> theirs =
                List.copyOf(ClauseReadings.readBy(db, "demo.rival", base).stated().values());
        List<TermMeaning> mine =
                List.copyOf(ClauseReadings.readBy(db, "demo.rival", rival).stated().values());
        assertTrue(!theirs.isEmpty() && !mine.isEmpty(),
                "both declarations state a clause, or this compares nothing: " + theirs + mine);
        assertNotEquals(theirs, mine,
                "two declarations of one spelling, stating different rules, were read alike");
    }

    /**
     * A module whose one clause names something nothing declares, and a module that imports it.
     *
     * <p>Apart from the sources above because this one does not compile, and that is what it is for:
     * a clause the discharge reader has no form for is answered with
     * {@link TypedClause.Stopped}, and nothing that compiles reaches that arm. Name resolution
     * reports and carries on, so the declaration is still there to be read — which is what makes the
     * reading askable at all.
     */
    private static final String A_CLAUSE_WITH_NO_FORM = """
            module demo.stopped exposing ( Amount )

            data Amount = Decimal
                invariant value >= 0.00m && Absent.nothing(value)
            """;

    /** A module that imports it, so the reading can be taken from a module that did not write it. */
    private static final String READING_IT = """
            module demo.reader exposing ( f )

            import demo.stopped as Stopped ( Amount )

            behavior f : (a: Amount) -> Decimal
            let f (a) = a.value
            """;

    /**
     * The other arm: a clause with no form is the same no-form whichever module asks.
     *
     * <p>Held because the two arms are two answers and only one of them was reached by anything that
     * compiles. Two readings that both stopped agree over what they stated by having stated nothing,
     * so the comparisons above say nothing about this arm however many declarations they sweep.
     *
     * <p>What is compared is the whole reading and not the half of it that typed. A clause this
     * reader has no form for and one the declaring module has no form for are the same clause, named
     * the same way, and the two lists are held to being the same list.
     */
    @Test
    void aClauseWithNoFormIsTheSameNoFormWhicheverModuleAsks() {
        Compilation compilation =
                Compilation.ofSources(List.of(A_CLAUSE_WITH_NO_FORM, READING_IT), ModulePath.EMPTY);
        compilation.answerEverything();
        assertFalse(compilation.errors().isEmpty(),
                "this source is supposed to name something nothing declares");

        Db db = compilation.db();
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey("demo.stopped", "Amount"));
        ClauseReadings.Read declaring = ClauseReadings.readBy(db, "demo.stopped", named);
        ClauseReadings.Read asking = ClauseReadings.readBy(db, "demo.reader", named);

        assertFalse(declaring.stopped().isEmpty(),
                "the clause this fixture writes is supposed to have no form, and the declaring"
                        + " module found one for it: " + declaring);
        assertEquals(declaring, asking,
                "`demo.stopped.Amount` reads one way where it was written and another where"
                        + " `demo.reader` reads it");
    }

    /** `demo.base.Amount` as `demo.user` reads it, over a compilation of {@code declaring}. */
    private static Map<Clause.Ref, TermMeaning> amountAsTheImporterReadsIt(String declaring) {
        Db db = analysed(List.of(declaring, IMPORTING));
        return ClauseReadings.readBy(db, "demo.user",
                TypeSymbols.declared(new TypeKey("demo.base", "Amount"))).stated();
    }

    /**
     * A compilation of {@code sources} with every question of it answered.
     *
     * <p>Held to compiling after the questions are asked and not before. What a compilation says is
     * read off the reports its answers carry, so a store nothing has been asked of is silent about
     * every source — and a fixture that stopped compiling would go past a check made there.
     */
    private static Db analysed(List<String> sources) {
        Compilation compilation = Compilation.ofSources(sources, ModulePath.EMPTY);
        compilation.answerEverything();
        assertEquals(List.of(),
                compilation.errors().stream().map(each -> each.diagnostic().code()).toList(),
                "these modules are supposed to compile");
        return compilation.db();
    }
}
