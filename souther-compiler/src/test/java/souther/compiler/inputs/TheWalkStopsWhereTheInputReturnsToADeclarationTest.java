package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.CaseSelector;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A branch of the walk ends where opening what is under a position would open a declaration the
 * path has already opened.
 *
 * <p>What makes the reading finite, and the only thing that does. A type that names itself is an
 * ordinary thing to declare and an ordinary thing to hold a value of: a tree holds a list of
 * itself, a chain holds an option of itself, an expression holds two of itself, and all three
 * compile, are constructible, and have an input path for every depth. Nothing else stops them —
 * a declaration that names itself with no container in between is refused for being unconstructible,
 * which is a different answer to a different question.
 *
 * <p>The occurrence is read. What the position divides into, what its rules bound, what a report
 * names it as are all answered where the path returns; what is refused is unfolding the declaration
 * a second time. A rule that names a position past it names one path rather than asking for a
 * listing, and is measured there — which is
 * {@link souther.compiler.partition.LinesWhereTheyFall}'s and not this walk's.
 */
class TheWalkStopsWhereTheInputReturnsToADeclarationTest {

    /** Through what a list holds: a tree. */
    private static final String THROUGH_A_LIST = """
            module g

            data Node = { n: Int, kids: List<Node> }

            data Ok

            behavior read : (x: Node) -> Ok
            """;

    /** Through what an option holds: a chain. */
    private static final String THROUGH_AN_OPTION = """
            module g

            data Node = { n: Int, next: Option<Node> }

            data Ok

            behavior read : (x: Node) -> Ok
            """;

    /** Through a case of a sum: an expression. */
    private static final String THROUGH_A_CASE = """
            module g

            data Lit = { n: Int }
            data Add = { l: Expr, r: Expr }
            data Expr = Lit | Add

            data Ok

            behavior read : (x: Expr) -> Ok
            """;

    /** And through two declarations that name each other. */
    private static final String MUTUAL = """
            module g

            data B = { m: Int, a: Option<A> }
            data A = { n: Int, b: B }

            data Ok

            behavior read : (x: A) -> Ok
            """;

    /**
     * Each of them comes back with positions, which is what terminating looks like from outside.
     *
     * <p>Said as a bound on the count rather than as "it returned", because what these models would
     * do unbounded is not return at all. A number small enough to be wrong if a declaration were
     * unfolded twice.
     */
    @Test
    void everyRecursiveShapeIsRead() {
        for (String source : List.of(THROUGH_A_LIST, THROUGH_AN_OPTION, THROUGH_A_CASE, MUTUAL)) {
            InputDomain read = reading(source, "read");
            assertFalse(read.positions().isEmpty(), source);
            assertTrue(read.positions().size() < 16,
                    () -> "one unfolding of each declaration: " + spelled(read));
        }
    }

    /** The tree stops at what the list holds, which is the declaration the path started at. */
    @Test
    void aTreeStopsAtWhatItsListHolds() {
        InputDomain read = reading(THROUGH_A_LIST, "read");
        TermPath element = TermPath.of("x").then("kids").element();

        assertNotNull(read.at(element), () -> spelled(read));
        assertEquals("x", returnedAt(read, element).openedAt().toString(),
                "which is where the path opened `Node`");
        assertTrue(read.positions().stream()
                        .noneMatch(each -> each.path().isAtOrUnder(element)
                                && !each.path().equals(element)),
                () -> "and nothing is unfolded under it: " + spelled(read));
    }

    /** The chain stops at what the option holds. */
    @Test
    void aChainStopsAtWhatItsOptionHolds() {
        InputDomain read = reading(THROUGH_AN_OPTION, "read");
        TermPath held = pathOf(read, "x.next@Some");

        assertNotNull(read.at(held), () -> spelled(read));
        assertEquals("x", returnedAt(read, held).openedAt().toString());
    }

    /** And an expression stops at the operands of its first operator. */
    @Test
    void anExpressionStopsAtTheOperandsOfItsOperator() {
        InputDomain read = reading(THROUGH_A_CASE, "read");
        TermPath left = TermPath.of("x").refine(toLeaf(named("Add"))).then("l");

        assertNotNull(read.at(left), () -> spelled(read));
        assertEquals("x", returnedAt(read, left).openedAt().toString(),
                "which is where the path opened `Expr`");
    }

    /**
     * Two declarations that name each other stop at the second time round.
     *
     * <p>Not only self-reference. What terminates the walk is the declaration being open on this
     * path, and {@code A -> B -> A} opens {@code A} twice as surely as a field of {@code A} named
     * {@code A} would.
     */
    @Test
    void twoDeclarationsThatNameEachOtherStopAtTheReturn() {
        InputDomain read = reading(MUTUAL, "read");
        TermPath again = pathOf(read, "x.b.a@Some");

        assertNotNull(read.at(again), () -> spelled(read));
        assertEquals("x", returnedAt(read, again).openedAt().toString(),
                "which is where the path opened `A`");
    }

    /**
     * The position the path returns to is read, and only what is under it is not.
     *
     * <p>The difference between a stop and an absence. A value stands there and a row writes one, so
     * the sum at the end of a chain still divides into its cases — read as "nothing here", the last
     * link of every recursive model would be a position the report says the model is silent about.
     */
    @Test
    void theReturningOccurrenceIsStillRead() {
        InputDomain read = reading(THROUGH_A_CASE, "read");
        Position left = read.at(
                TermPath.of("x").refine(toLeaf(named("Add"))).then("l"));

        assertEquals(2, left.obligationCases().size(),
                "the sum there divides into its cases whichever time round it is");
    }

    /** Why the walk stopped at {@code path}, which has to be the return and nothing else. */
    private static BlockReason.RecursiveExpansion returnedAt(InputDomain read, TermPath path) {
        BlockedDescent stopped = BlockedDescent.of(read.at(path).structure());
        assertNotNull(stopped, () -> path + " is where the path returns, and it says so");
        return assertInstanceOf(BlockReason.RecursiveExpansion.class, stopped.why());
    }

    /** The narrowing to one leaf, spelled the way the checker's resolution of an arm spells it: a
     *  leaf is a case that covers itself, so selecting it narrows to that one distinction. */
    private static Refinement toLeaf(souther.compiler.types.TypeSymbol leaf) {
        return Refinement.of(souther.compiler.types.ResolvedCase.of(
                CaseSelector.direct(leaf), List.of(leaf)));
    }

    /** The declaration {@code name} stands for in the model under test. */
    private static souther.compiler.types.TypeSymbol named(String name) {
        return souther.compiler.types.TypeSymbols.declared(
                new souther.compiler.types.TypeKey("g", name));
    }

    /** The position this reading made at {@code spelled}. */
    private static TermPath pathOf(InputDomain read, String spelled) {
        return read.positions().stream().map(Position::path)
                .filter(each -> each.toString().equals(spelled))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no position at " + spelled + " among " + spelled(read)));
    }

    /** The positions, spelled the way a report names them. */
    private static String spelled(InputDomain read) {
        return read.positions().stream().map(each -> each.path().toString()).toList().toString();
    }

    private static InputDomain reading(String source, String behavior) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get(behavior), rules, ReadAs.THE_COMPILATION_DOES);
    }
}
