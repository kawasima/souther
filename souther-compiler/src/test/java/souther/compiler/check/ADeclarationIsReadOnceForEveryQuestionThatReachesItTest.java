package souther.compiler.check;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSources(List.of(MODULE), ModulePath.EMPTY);
        compilation.answerEverything();
        assertTrue(compilation.diagnostics().values().stream().allMatch(List::isEmpty),
                "the model under test compiles clean");
        return compilation;
    }

    /**
     * A compile reads each declaration a handful of times and no more — where the readers each read
     * for themselves it is a multiple of how many declarations there are, and that multiple is what
     * this refuses.
     */
    @Test
    void aWholeCompileReadsEachDeclarationAboutOnce() {
        long before = InvariantChecker.readingsMade();
        Compilation compilation = compiled();
        long made = InvariantChecker.readingsMade() - before;
        long declarations = compilation.module("demo").defs().size();

        assertTrue(made <= declarations * 2,
                () -> "a compile of " + declarations + " declarations made " + made
                        + " readings, so the questions that reach a declaration are reading it"
                        + " again rather than being lent the one reading there is");
    }

    /**
     * Two askers, one reading, and it is the same one. The count is what the claim rests on; that
     * the second is handed the first is what the count means, and is worth saying once here because
     * a count could be held down by reading less rather than by sharing.
     */
    @Test
    void asecondAskerIsLentTheFirstsReading() {
        Compilation compilation = compiled();
        DeclarationReadings readings = souther.compiler.query.Machines.of(compilation.db());
        RuleReadingSource source = RuleReadings.of(compilation, "demo");
        ReadingPolicy policy = AS_THE_COMPILE_READS;
        TypeSymbol.AtModule code = TypeSymbols.declared(new TypeKey("demo", "Code"));

        long before = InvariantChecker.readingsMade();
        InvariantChecker.Seeded first = InvariantChecker.seedFields(code, source, policy, readings);
        InvariantChecker.Seeded second = InvariantChecker.seedFields(code, source, policy, readings);

        assertSame(first, second, "the second asker is handed the reading the first was");
        assertEquals(0, InvariantChecker.readingsMade() - before,
                "and the declaration was read for neither of them: the compile had read it");
    }

    /** A second policy is a second reading, not the same one under other terms. */
    @Test
    void anotherPolicyIsAnotherReading() {
        Compilation compilation = compiled();
        DeclarationReadings readings = souther.compiler.query.Machines.of(compilation.db());
        RuleReadingSource source = RuleReadings.of(compilation, "demo");
        TypeSymbol.AtModule code = TypeSymbols.declared(new TypeKey("demo", "Code"));

        long before = InvariantChecker.readingsMade();
        InvariantChecker.seedFields(code, source, AS_THE_COMPILE_READS, readings);
        assertEquals(0, InvariantChecker.readingsMade() - before, "lent, as above");

        InvariantChecker.seedFields(code, source, OTHER_TERMS, readings);
        assertEquals(1, InvariantChecker.readingsMade() - before,
                "and read again under terms the reading in hand was not made under");
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
            souther.compiler.regex.PatternPlan.Budget.OF_ADMITTED_VALUES,
            souther.compiler.regex.PatternPlan.Budget.OF_WHAT_A_RULE_LEAVES);

    /** Terms a reading of the same declaration comes to something else under: what a rule may
     *  spend building a machine is far less than the reading above allows. */
    private static final ReadingPolicy OTHER_TERMS = new ReadingPolicy(64, 12,
            new souther.compiler.regex.PatternPlan.Budget(1, 1),
            new souther.compiler.regex.PatternPlan.Budget(1, 1));

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
        LentReadings lender = new LentReadings(DeclarationReadings.NONE, () -> world[0]);

        InvariantChecker.Seeded read =
                InvariantChecker.seedFields(code, source, AS_THE_COMPILE_READS, lender);
        assertSame(read, lender.seeded(code.key(), AS_THE_COMPILE_READS),
                "what was read of this world is lent while it is this world");

        world[0]++;
        assertNull(lender.seeded(code.key(), AS_THE_COMPILE_READS),
                "and is not lent into the next, which it is not a reading of");
    }
}
