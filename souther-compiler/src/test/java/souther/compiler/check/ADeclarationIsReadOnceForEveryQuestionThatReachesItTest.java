package souther.compiler.check;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.regex.PatternPlan;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A declaration's own reading is made once, however many questions reach the declaration.
 *
 * <p>What its rules leave is read by the count of how many values a module's types have, by the
 * domain of every input a behavior takes, and by every construction of it in a body. Each of those
 * used to read it again, and the readings were the same reading: the same declaration, the same
 * world, the same terms, nothing supposed and nothing left out. So the first is made and the rest
 * are lent it.
 *
 * <p>Held by counting readings rather than by timing them. What a reader is held to is that a
 * second asker reads not at all, which is a shape; a measurement of how long a compile takes would
 * pass on an implementation that had the shape wrong and the machine fast.
 *
 * <p>The lending is of work and not of an answer. A reading is not a value — what it holds is named
 * by identity — so nothing keeps it as one, and what makes handing it on sound is that within a
 * revision there is one world for it to be a reading of. When an input is given a value it did not
 * hold, that is a new world and what was lent is dropped, which the last of these holds.
 */
class ADeclarationIsReadOnceForEveryQuestionThatReachesItTest {

    /**
     * Declarations every question reaches: rules of their own to read, a record over them so the
     * count walks from one to the next, and a behavior taking the record so its input domain is
     * read too.
     */
    private static final String MODULE = """
            module demo

            data Code = String
                invariant String.matches("[A-Z]{2}[0-9]{3}", value)
            data Amount = Int
                invariant value >= 0 && value <= 1000
            data Line = { code: Code, amount: Amount }
            data Order = { line: Line, note: String }

            behavior priceOf : (order: Order) -> Amount
            let priceOf (order) = order.line.amount

            behavior lineOf : (order: Order) -> Line
            let lineOf (order) = order.line
            """;

    /** The same declarations, with more questions reaching them: four more behaviors, each
     *  constructing and taking them apart again. */
    private static final String MORE_QUESTIONS = MODULE + """

            behavior codeOf : (order: Order) -> Code
            let codeOf (order) = order.line.code

            behavior noteOf : (order: Order) -> String
            let noteOf (order) = order.note

            behavior lineFor : (code: Code, amount: Amount) -> Line
            let lineFor (code, amount) = Line { code = code, amount = amount }

            behavior orderFor : (line: Line) -> Order
            let orderFor (line) = Order { line = line, note = "" }
            """;

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSources(List.of(MODULE), ModulePath.EMPTY);
        compilation.answerEverything();
        assertTrue(compilation.diagnostics().values().stream().allMatch(List::isEmpty),
                "the model under test compiles clean");
        return compilation;
    }

    /**
     * What a compile reads is settled by how many declarations it has, and not by how many
     * questions reach them.
     *
     * <p>Two models with the same declarations and different numbers of questions over them: the
     * constructions in the bodies, the domains of the inputs and the count of what the module's
     * types hold all reach the same declarations, and the second model reaches them far more often.
     * Where each question reads for itself the second costs more; where a reading is lent, the two
     * cost the same.
     *
     * <p>Said as a comparison rather than as a number, because the number is what a reading is
     * asked for under — one for each place clauses are read from — and a test pinning it would be
     * pinning how many readers there are rather than that they share.
     */
    @Test
    void moreQuestionsOverTheSameDeclarationsReadThemNoMoreTimes() {
        long few = readingsMadeCompiling(MODULE);
        long many = readingsMadeCompiling(MORE_QUESTIONS);

        assertEquals(few, many,
                () -> "a model with more questions over the same declarations read them " + many
                        + " times against " + few + ", so a question that reaches a declaration is"
                        + " reading it again rather than being lent the reading there is");
    }

    private static long readingsMadeCompiling(String source) {
        long before = InvariantChecker.readingsMade();
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.answerEverything();
        assertTrue(compilation.diagnostics().values().stream().allMatch(List::isEmpty),
                "the model under test compiles clean");
        return InvariantChecker.readingsMade() - before;
    }

    /**
     * Two askers, one reading, and it is the same one. The count is what the claim rests on; that
     * the second is handed the first is what the count means, and is worth saying once here because
     * a count could be held down by reading less rather than by sharing.
     */
    @Test
    void asecondAskerIsLentTheFirstsReading() {
        Compilation compilation = compiled();
        DeclarationReadings readings = compilation.db().readings();
        RuleReadingSource source = RuleReadings.of(compilation, "demo");
        ReadingPolicy policy = AS_THE_COMPILE_READS;
        TypeSymbol.AtModule code = TypeSymbols.declared(new TypeKey("demo", "Code"));

        InvariantChecker.Seeded first = InvariantChecker.seedFields(code, source, policy, readings);
        long afterTheFirst = InvariantChecker.readingsMade();
        InvariantChecker.Seeded second = InvariantChecker.seedFields(code, source, policy, readings);

        assertSame(first, second, "the second asker is handed the reading the first was");
        assertEquals(afterTheFirst, InvariantChecker.readingsMade(),
                "and the declaration was not read for the second of them");
    }

    /**
     * The reading the store's answer for a declaration was made by is the one the next reader is
     * given, though that reader asks the compilation where to read for itself.
     *
     * <p>This is where the sharing has to happen and the one place a test can see it happen. Every
     * question that reads a declaration asks the compilation for a source of its own, and what
     * comes back is a fresh pair over the same scope and the same clauses — so a reader that told
     * two sources apart by comparing the pairs would find every reader reading alone, while a
     * compile still came out right and every count still fell.
     */
    @Test
    void theReadingTheAnswerWasMadeByIsTheOneTheNextReaderIsGiven() {
        Compilation compilation = compiled();
        DeclarationReadings readings = compilation.db().readings();
        TypeSymbol.AtModule code = TypeSymbols.declared(new TypeKey("demo", "Code"));

        readings.of(code.key());
        long afterTheAnswer = InvariantChecker.readingsMade();

        RuleReadingSource asked = RuleReadings.of(compilation, "demo");
        InvariantChecker.seedFields(code, asked, AS_THE_COMPILE_READS, readings);

        assertEquals(afterTheAnswer, InvariantChecker.readingsMade(),
                "the reader that asked for the answer is handed the reading it was made by");
    }

    /**
     * What says a source is the compilation's own is not something a reader can get said of a
     * source of its own.
     *
     * <p>A reading is handed to a reader whose source has the same origin, so an origin saying the
     * source is the one a compilation reads a module's rules under is what admits a reader to
     * another reader's work. A reader that could have it said of a pair it assembled — its own
     * scope, or a lookup answering for no clause at all — would be handed a reading of rules it was
     * not reading, and nothing about the compile would look wrong.
     *
     * <p>So there is nothing to ask. What a reader holds hands out what somebody has already made
     * and nothing that makes a source; the sources are the compilation's, made from its own scope
     * and its own clauses, and a reader that assembles a pair gets one nobody shares.
     */
    @Test
    void aReaderCannotHaveItSaidOfASourceOfItsOwn() {
        assertEquals(List.of(), Arrays.stream(DeclarationReadings.class.getMethods())
                        .filter(each -> each.getReturnType() == RuleReadingSource.class)
                        .map(Method::getName).toList(),
                "what a reader holds makes a source, so a reader can have one made of its own parts");

        Compilation compilation = compiled();
        DeclarationReadings readings = compilation.db().readings();
        TypeSymbol.AtModule code = TypeSymbols.declared(new TypeKey("demo", "Code"));
        RuleReadingSource asTheCompilationReads = RuleReadings.of(compilation, "demo");
        InvariantChecker.seedFields(code, asTheCompilationReads, AS_THE_COMPILE_READS, readings);

        // The nearest thing a reader can assemble: the compilation's own scope, and a lookup that
        // answers for no clause anybody wrote.
        RuleReadingSource ofItsOwn = new RuleReadingSource(asTheCompilationReads.symbols(),
                RuleReadings.noClauseFiled(), PublishedDeclarations.NONE, ClauseLocations.NONE);
        long beforeItsOwn = InvariantChecker.readingsMade();
        InvariantChecker.seedFields(code, ofItsOwn, AS_THE_COMPILE_READS, readings);

        assertEquals(beforeItsOwn + 1, InvariantChecker.readingsMade(),
                "a reader reading under a source of its own was handed the reading made under the"
                        + " compilation's, which is a reading of clauses its own source has not got");

        // And the same, minted: sources of its own over the compilation's scope and a lookup that
        // answers for nothing, saying of what they make the name the compilation writes.
        RuleReadingSource minted = new TheCompilationsSources(
                _ -> asTheCompilationReads.symbols(), RuleReadings.noClauseFiled(),
                PublishedDeclarations.NONE, ClauseLocations.NONE).of("demo");
        long beforeMinted = InvariantChecker.readingsMade();
        InvariantChecker.seedFields(code, minted, AS_THE_COMPILE_READS, readings);

        assertEquals(beforeMinted + 1, InvariantChecker.readingsMade(),
                "a reader that built sources of its own was handed the reading the compilation's"
                        + " own sources made: writing the module's name is enough to be read as"
                        + " the compilation");
    }

    /** A second policy is a second reading, not the same one under other terms. */
    @Test
    void anotherPolicyIsAnotherReading() {
        Compilation compilation = compiled();
        DeclarationReadings readings = compilation.db().readings();
        RuleReadingSource source = RuleReadings.of(compilation, "demo");
        TypeSymbol.AtModule code = TypeSymbols.declared(new TypeKey("demo", "Code"));

        InvariantChecker.seedFields(code, source, AS_THE_COMPILE_READS, readings);
        long afterTheFirst = InvariantChecker.readingsMade();
        InvariantChecker.seedFields(code, source, AS_THE_COMPILE_READS, readings);
        assertEquals(afterTheFirst, InvariantChecker.readingsMade(), "lent, as above");

        InvariantChecker.seedFields(code, source, OTHER_TERMS, readings);
        assertEquals(afterTheFirst + 1, InvariantChecker.readingsMade(),
                "and read again under terms the reading in hand was not made under");
    }

    /**
     * Supposing nothing is reading the declaration whole, and is that reading rather than one that
     * happens to leave nothing out.
     *
     * <p>What a reader supposes arrives as a set, and four of the readers that reach a declaration
     * suppose nothing. Said as a reach that stops at nobody, each of those would be asking for a
     * reading no lender could see is the declaration's own, and each would read it again for want
     * of a way to ask.
     */
    @Test
    void supposingNothingIsReadingTheDeclarationWhole() {
        assertTrue(InvariantChecker.Reach.stoppingAt(Set.of()).everything(),
                "a reader that supposes nothing is reading the declaration whole");
        assertFalse(InvariantChecker.Reach.stoppingAt(
                        Set.of(TypeSymbols.declared(new TypeKey("demo", "Code")))).everything(),
                "and one that supposes a declaration has values is not");
    }

    /**
     * The terms the compilation reads under, said again here.
     *
     * <p>Written out rather than reached for, because what a reading is made under is handed to it
     * and a reader that could pick one up is a reader two readings of a declaration can differ by
     * (see {@code Front.Reading}). Equal to what the compile used, which is what makes a reading
     * made under it the one the compile made.
     */
    private static final ReadingPolicy AS_THE_COMPILE_READS = new ReadingPolicy(64, 1000,
            PatternPlan.Budget.OF_ADMITTED_VALUES,
            PatternPlan.Budget.OF_WHAT_A_RULE_LEAVES);

    /** Terms a reading of the same declaration comes to something else under: what a rule may
     *  spend building a machine is far less than the reading above allows. */
    private static final ReadingPolicy OTHER_TERMS = new ReadingPolicy(64, 12,
            new PatternPlan.Budget(1, 1),
            new PatternPlan.Budget(1, 1));

    /**
     * What was read of one world is not lent into the next.
     *
     * <p>Asked of the lender directly, and with the world moved by hand, because that is the only
     * place the question is settled: a compile that edits a source recomputes the answers about
     * what it edited, so a reading made again for that reason would come out fresh whether or not
     * anything was dropped, and a test watching a compile would say nothing about the dropping.
     *
     * <p>What is lent is sound because within a revision there is one world for a reading to be a
     * reading of. When the revision moves that is no longer so, and this is what makes it not so.
     */
    @Test
    void whatWasReadOfOneWorldIsNotLentIntoTheNext() {
        Compilation compilation = compiled();
        RuleReadingSource source = RuleReadings.of(compilation, "demo");
        TypeSymbol.AtModule code = TypeSymbols.declared(new TypeKey("demo", "Code"));
        long[] world = { 7 };
        LentReadings lender = new LentReadings(DeclarationReadings.NONE, () -> world[0],
                StoreWork.UNWATCHED);

        InvariantChecker.Seeded read =
                InvariantChecker.seedFields(code, source, AS_THE_COMPILE_READS, lender);
        assertSame(read, InvariantChecker.seedFields(code, source, AS_THE_COMPILE_READS, lender),
                "what was read of this world is lent while it is this world");

        world[0]++;
        long beforeTheNext = InvariantChecker.readingsMade();
        InvariantChecker.seedFields(code, source, AS_THE_COMPILE_READS, lender);
        assertEquals(beforeTheNext + 1, InvariantChecker.readingsMade(),
                "and is not lent into the next, which it is not a reading of");
    }
}
