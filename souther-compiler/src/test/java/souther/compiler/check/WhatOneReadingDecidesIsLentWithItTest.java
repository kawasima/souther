package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.meta.ModulePath;
import souther.compiler.numeric.Count;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.KnownExtents;
import souther.compiler.values.StringMachineAnswers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a reading of a declaration decides is decided once, and every question handed that reading
 * is handed it.
 *
 * <p>Which conjuncts account for where a coordinate stops is read by reading the declaration again
 * without each of them, so a reader working it out costs a reading of the declaration for every
 * end it attributes. The reading those come off is lent to every question that reaches the
 * declaration; what it came to has to be lent with it, or each borrower puts the whole attribution
 * again and the lending saves the first reading and no other.
 *
 * <p>Held by identity where the claim is about one object, and by counting readings where it is
 * about the work. The two say different things: one asker being handed another's answer is what
 * makes the count what it is, and a count alone would also be held down by an implementation that
 * attributed less.
 */
class WhatOneReadingDecidesIsLentWithItTest {

    /**
     * Two declarations bounding one coordinate, and a conjunct about it that places no end.
     *
     * <p>Both kinds of counterfactual are asked of this. Which of {@code Common} and {@code Held}
     * holds the floor under {@code hi} is asked by leaving each declaration's clauses out, and what
     * the conjuncts about it were holding is asked by leaving each conjunct out. The behavior is
     * what makes an input walk read the declaration beside the questions the module's own types
     * put.
     */
    private static final String MODULE = """
            module demo exposing ( Held, keep )

            data Common =
                { lo: Int
                , code: String
                }
                invariant floor = lo >= 0
                invariant hole = lo /= 0
                invariant ceiling = lo <= 100
                invariant coded = String.matches("[A-Z]{2}[0-9]{3}", code)

            data Held = { ...Common }
                invariant tighter = lo <= 50

            behavior keep : (h: Held) -> Held

            let keep (h) = h
            """;

    /** The same declarations with three more behaviors over them, each of which walks the input it
     *  takes and reaches the declaration the same way the first does. */
    private static final String MORE_QUESTIONS = MODULE + """

            behavior alsoKeep : (h: Held) -> Held
            let alsoKeep (h) = h

            behavior floorOf : (h: Held) -> Int
            let floorOf (h) = h.lo

            behavior heldBy : (h: Held) -> Held
            let heldBy (h) = h
            """;

    /**
     * The second asker is handed what the first's reading came to, and it is the same object.
     *
     * <p>The counterfactual readings an attribution needs are kept by the reading that was asked
     * for them, so two askers holding one reading put such a question once between them and two
     * holding a reading each put it twice. Said as identity because that is what decides it: what
     * is shared is the reading, and an answer equal to it made beside it shares nothing.
     */
    @Test
    void aSecondAskerIsHandedWhatTheFirstsReadingCameTo() {
        Compilation compilation = compiled(MODULE);
        FieldDomains first = asTheCompilationReads(compilation, "Held");
        FieldDomains second = asTheCompilationReads(compilation, "Held");

        assertSame(first, second,
                "the second asker is handed the answer the first's reading came to");
    }

    /**
     * A declaration's ends are attributed as many times as the declaration is read, and not as many
     * times as it is asked about.
     *
     * <p>Said as a comparison between two models rather than as a number. What the number would pin
     * is how many readers there are, which is a fact about who asks; what this is about is that a
     * reader beyond the first pays nothing.
     */
    @Test
    void moreQuestionsOverOneDeclarationAttributeItsEndsNoMoreOften() {
        long few = readingsMadeCompiling(MODULE);
        long many = readingsMadeCompiling(MORE_QUESTIONS);

        assertEquals(few, many,
                () -> "a model with more questions over the same declarations read them " + many
                        + " times against " + few + ", so a question reaching a declaration is"
                        + " attributing its ends again rather than being handed the attribution");
    }

    /**
     * A reading with something settled at a value is not the declaration's own, and what it comes
     * to is not lent as one.
     *
     * <p>What a coordinate's rules leave once another is fixed is a different answer about the same
     * clauses, and a reader handed the canonical one under a settling would be reading a range that
     * runs where nothing fixed it.
     */
    @Test
    void whatAReadingWithSomethingSettledComesToIsNotLent() {
        Compilation compilation = compiled(MODULE);
        FieldDomains canonical = asTheCompilationReads(compilation, "Held");
        FieldDomains settled = FieldDomains.of(declared("Held"),
                RuleReadings.of(compilation, "demo"), ReadAs.THE_COMPILATION_DOES,
                Map.of(RuleKey.of("lo"), Count.of(500)),
                compilation.db().readings());

        assertNotSame(canonical, settled,
                "a reading with a coordinate fixed is not the declaration's own reading");
    }

    /**
     * Nor is one that leaves something out, which is what every counterfactual does.
     *
     * <p>Supposing a declaration holds values is reading the rules under it and no further, and the
     * answer that comes of it says what would be left if it did. Lent as the declaration's own, it
     * would say the rules under a name state nothing.
     */
    @Test
    void whatAReadingWithSomethingLeftOutComesToIsNotLent() {
        Compilation compilation = compiled(MODULE);
        FieldDomains canonical = asTheCompilationReads(compilation, "Held");
        TypeSymbol.AtModule held = declared("Held");
        RuleReadingSource source = RuleReadings.of(compilation, "demo");
        Hir.Data data = (Hir.Data) source.symbols().declaredNode(held.key());
        FieldDomains granting = FieldDomains.granting(held, data, source,
                ReadAs.THE_COMPILATION_DOES, Set.of(declared("Common")),
                compilation.db().readings());

        assertNotSame(canonical, granting,
                "a reading that supposes a declaration has values is not the declaration's own");
    }

    /**
     * What was worked out of one world is not handed to a reader of the next.
     *
     * <p>The reading is lent for as long as the world it was read from is the current one, and what
     * was derived from it is the reading's. Moved by hand, because a compile that edits a source
     * answers its questions again for that reason and would come out fresh whether or not anything
     * was dropped.
     */
    @Test
    void whatWasDerivedOfOneWorldIsNotHandedIntoTheNext() {
        Compilation compilation = compiled(MODULE);
        RuleReadingSource source = RuleReadings.of(compilation, "demo");
        long[] world = { 7 };
        LentReadings lender = new LentReadings(DeclarationReadings.NONE, () -> world[0],
                StoreWork.UNWATCHED);

        FieldDomains read = FieldDomains.of(declared("Held"), source,
                ReadAs.THE_COMPILATION_DOES, lender);
        assertSame(read, FieldDomains.of(declared("Held"), source,
                        ReadAs.THE_COMPILATION_DOES, lender),
                "what this world's reading came to is handed on while it is this world");

        world[0]++;
        assertNotSame(read, FieldDomains.of(declared("Held"), source,
                        ReadAs.THE_COMPILATION_DOES, lender),
                "and is not handed into the next, which its reading is not a reading of");
    }

    /**
     * What the declaration's own reading came to is what a reader working it out for itself would
     * come to.
     *
     * <p>The reading that is lent is made while the store's answer about the declaration's string
     * machines is being made, so it builds those machines rather than borrowing them, and every
     * counterfactual of it borrows what it built. A reader of its own borrows the finished answer
     * and its counterfactuals borrow that. The two are different worlds to have read in and the
     * same rules to have read, and what they leave has to be the same — otherwise which of them a
     * question was handed decides what the model says.
     *
     * <p>Compared by what the reading answers rather than by what it holds. Which fields it built
     * are its own business and are allowed to differ; the ends, the attributions and what stands at
     * each name are what a report is written from.
     *
     * <p>That a machine says the same thing whoever built it is held where machines are
     * ({@link ACounterfactualBorrowsTheMachinesTheReadingMadeTest}), and this rests on it rather
     * than restating it: what a set admits is compared here, but a reading that built its own
     * machines and one handed the store's are the same reading of the same rules, so no model
     * written here tells them apart.
     */
    @Test
    void whatTheLentReadingCameToIsWhatAReaderOfItsOwnComesTo() {
        Compilation compilation = compiled(MODULE);
        FieldDomains lent = asTheCompilationReads(compilation, "Held");
        FieldDomains ofItsOwn = FieldDomains.of(declared("Held"),
                RuleReadings.of(compilation, "demo"), ReadAs.THE_COMPILATION_DOES,
                borrowingMachinesAndNoReading(compilation));

        assertNotSame(lent, ofItsOwn, "the reader of its own read for itself");
        assertFalse(lent.movedEnds().isEmpty(),
                "the model under test has ends its conjuncts moved rather than placed");
        assertEquals(lent.movedEnds(), ofItsOwn.movedEnds(),
                "the ends the conjuncts moved are the same ends, attributed to the same parts");
        assertEquals(lent.placed(), ofItsOwn.placed(),
                "and so is every end the declaration places");
        for (String field : List.of("lo", "code")) {
            RuleKey path = RuleKey.of(field);
            assertEquals(lent.at(path), ofItsOwn.at(path),
                    () -> "what may stand at `" + field + "` is the same either way");
            assertEquals(lent.noLineAt(path), ofItsOwn.noLineAt(path),
                    () -> "and so are the rules about `" + field + "` that placed no end");
            assertEquals(lent.admits(path), ofItsOwn.admits(path),
                    () -> "and which values `" + field + "` admits, which is what the machines the"
                            + " two worlds differ by decide");
        }
        assertEquals(lent.infeasible(), ofItsOwn.infeasible(),
                "and whether the rules leave a value at all");
    }

    /**
     * A lender of the store's answers about string machines that lends no reading.
     *
     * <p>What a borrower of the store had before a reading could be handed on: the machines are the
     * store's answer and the reading is the borrower's own. Written here rather than reached for,
     * because the point of the comparison is that the two readings were made in different worlds.
     */
    private static DeclarationReadings borrowingMachinesAndNoReading(Compilation compilation) {
        DeclarationReadings store = compilation.db().readings();
        return new DeclarationReadings() {

            @Override
            public StringMachineAnswers of(TypeKey declaration) {
                return store.of(declaration);
            }

            @Override
            public KnownExtents extents() {
                return store.extents();
            }
        };
    }

    /** What the rules of {@code declaration} leave, asked the way the compilation asks it. */
    private static FieldDomains asTheCompilationReads(Compilation compilation,
                                                     String declaration) {
        return FieldDomains.of(declared(declaration), RuleReadings.of(compilation, "demo"),
                ReadAs.THE_COMPILATION_DOES, compilation.db().readings());
    }

    private static TypeSymbol.AtModule declared(String declaration) {
        return TypeSymbols.declared(new TypeKey("demo", declaration));
    }

    private static Compilation compiled(String source) {
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.answerEverything();
        assertTrue(compilation.diagnostics().values().stream().allMatch(List::isEmpty),
                () -> "the model under test compiles clean: " + compilation.diagnostics());
        return compilation;
    }

    private static long readingsMadeCompiling(String source) {
        long before = InvariantChecker.readingsMade();
        compiled(source);
        return InvariantChecker.readingsMade() - before;
    }
}
