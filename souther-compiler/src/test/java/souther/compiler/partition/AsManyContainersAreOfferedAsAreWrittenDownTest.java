package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermOrders;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How many containers a total is offered is the figure written down, and what is not offered is said.
 *
 * <p>The budget counts containers. A walk that asked about it while stepping through counts was
 * asking in a place that steps by something else, and a count offering two containers stepped past
 * the figure — so what a caller was handed was the shape of the inner walk rather than what this
 * file says it hands over. Nothing unsound came of it, and that is the point: a limit nobody can
 * read off the constant is a limit that moves when the walk beside it is rearranged.
 *
 * <p>Beside {@link ARowIsComposedForAPointOnATotalTest}, which is about the rows. What a model gets
 * is one row per point, so how many were offered for it to be chosen from is not visible from there
 * at all.
 */
class AsManyContainersAreOfferedAsAreWrittenDownTest {

    private static final Symbols SYMBOLS = Symbols.none(DefaultStdlib.get());

    private static final RuleReadingSource RULES = RuleReadings.ofNoClauseFiled(SYMBOLS);

    private static final ReadingPolicy POLICY = new ReadingPolicy(64, 12,
            souther.compiler.values.AsACompilationAllows.admittedValues(),
            souther.compiler.values.AsACompilationAllows.whatARuleLeaves());

    private static final RuleReadingContext READING =
            RuleReadingContext.unshared(RULES, POLICY);

    /** A list of whole numbers a behavior takes, which nothing bounds and nothing counts. */
    private static final Type OF_WHOLE_NUMBERS = new Type.ListOf(Type.INT);

    /**
     * A total several counts reach, so the walk has more to offer than it will.
     *
     * <p>Six is reached by one element of six, by two of three, by three of two, and so on, and each
     * count offers the two shapes there are. A total no count reaches twice would say nothing about
     * the figure.
     */
    private static final Count SIX = Count.of(6);

    @Test
    void asManyAsAreWrittenDownAndNoMore() {
        TermRealizations.Realization made = offering();

        TermRealizations.Realization.Built built = assertInstanceOf(
                TermRealizations.Realization.Built.class, made,
                "a list of whole numbers coming to six is a container this composes");
        assertEquals(4, built.values().size(),
                () -> "four is what is written down, and the counts each offer two: "
                        + built.values().stream().map(FixtureTemplate::text).toList());
    }

    /**
     * And what the offer leaves out is said, whichever way it came to be left out.
     *
     * <p>Two of them and for two different reasons. A fifth container was in front of the walk and
     * the figure left no room for it; the shapes a count of two is spread in were all offered, and
     * two shapes are not all the ways two elements take a difference. Both are things this offer
     * does not hold, which is what a reader deciding whether every container was refused needs.
     *
     * <p><b>And the count's own figure is not among them.</b> The counts above the one the walk
     * reached were never asked for, because the containers ran out first — so a reader told to raise
     * it would raise it and get the same four.
     */
    @Test
    void whatWasNotOfferedIsSaid() {
        TermRealizations.Realization.Built built =
                assertInstanceOf(TermRealizations.Realization.Built.class, offering());

        assertEquals(Set.of(CompositionBudget.SHAPES_OF_A_TOTAL_OFFERED), built.heldBack(),
                "the walk had a fifth container and no room for it, which is the figure a reader"
                        + " raises — and the counts above the one it reached were never asked for,"
                        + " so no figure over them took anything away");
        assertEquals(Set.of(CompositionRepertoire.WAYS_A_TOTAL_IS_SPREAD), built.notAllOf(),
                "and the ways a count of more than one element is spread are two of the many,"
                        + " which is not a number anybody raises and is said apart from one");
        assertEquals(Generator.UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED,
                Generator.UnresolvedCombination.Reason.wordFor(built.heldBack()),
                "and the word such a walk has always come back with is the one it comes back with");
    }

    /** Distinct containers, so the figure is a figure of what a caller has to try. */
    @Test
    void noneOfThemIsAnother() {
        TermRealizations.Realization.Built built =
                assertInstanceOf(TermRealizations.Realization.Built.class, offering());

        assertEquals(built.values().size(),
                built.values().stream().map(FixtureTemplate::text).distinct().count(),
                "a container offered twice is one candidate the search pays for and none it gains");
    }

    private static TermRealizations.Realization offering() {
        NumericTerm.TakenOf total = NumericTerm.TakenOf.of(
                ValueName.Stdlib.operation("List", "sum"), TermPath.of("ns"), OF_WHOLE_NUMBERS,
                souther.compiler.check.ScopedDeclarations.wrapsOf(SYMBOLS), SYMBOLS);
        assertNotNull(total, "a walk that adds up a list of whole numbers is a number of it");
        TermOrders orders = souther.compiler.inputs.TermOrdersFixtures
                .at(total, OF_WHOLE_NUMBERS, SYMBOLS);
        assertTrue(orders.answered() != null, "and the order it answers on is the elements'");
        return TermRealizations.at(OF_WHOLE_NUMBERS,
                orders, SIX, NothingTheRulesSay.REGION, READING);
    }
}
