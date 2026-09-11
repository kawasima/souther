package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Shape;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermOrders;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A composer that stops at a figure of this compiler's says which figure, at the figure and not one
 * short of it.
 *
 * <p>Put to the producer directly and one either side of the line. What a search comes back with is
 * the same word whether it walked everything it had or stopped at a number — so a test that only
 * asked what it came back with would pass while the number and the name had come apart, which is
 * the whole of what a budget travelling as itself is for.
 *
 * <p>The figures are read from {@link CompositionBudget} rather than written here. A budget raised
 * moves this test with it, and a test holding its own copy of the number would go on asking about a
 * line the compiler no longer has.
 *
 * <p><b>Only the figures a producer can be put either side of on its own.</b> How many elements a
 * total is spread over is reached by what the elements may hold rather than by the total, so one
 * side of it takes a model whose elements are bounded — that one is held against a model, and what
 * is here is the half that a call can ask for.
 */
class ABudgetIsNamedByThePlaceItStopsTest {

    private static final Symbols SYMBOLS = Symbols.none(DefaultStdlib.get());

    private static final RuleReadingSource RULES = RuleReadings.ofNoClauseFiled(SYMBOLS);
    private static final ReadingPolicy POLICY = new ReadingPolicy(64, 12,
            souther.compiler.values.AsACompilationAllows.admittedValues(),
            souther.compiler.values.AsACompilationAllows.whatARuleLeaves());
    private static final RuleReadingContext READING =
            RuleReadingContext.unshared(RULES, POLICY);

    /** What a position of this type is, which is what the builder is asked about. */
    private static Shape shape(Type type) {
        return TypeView.of(type, SYMBOLS).shape();
    }

    /**
     * A collection at the figure is built, and one past it is a composing this stopped.
     *
     * <p>Both halves. That the far side names the budget says the name travels; that the near side
     * names none says the name is the figure being reached and not the shape of the ask.
     */
    @Test
    void aProposalHoldsAsManyElementsAsTheFigureAndNoMore() {
        int most = CompositionBudget.ELEMENTS_A_PROPOSAL_HOLDS.maximum();

        assertEquals(Set.of(),
                Witnesses.heldBackFor(shape(Type.list(Type.INT)), most, READING),
                "a collection of exactly as many as a row carries is one this builds");
        assertEquals(Set.of(CompositionBudget.ELEMENTS_A_PROPOSAL_HOLDS),
                Witnesses.heldBackFor(shape(Type.list(Type.INT)), most + 1, READING),
                "and one past it is this compiler declining, said as the figure it declined at");
    }

    /** The same of a string, which has a figure of its own and says that one. */
    @Test
    void aProposalHoldsAsManyCharactersAsItsOwnFigure() {
        int most = CompositionBudget.CHARACTERS_A_PROPOSAL_HOLDS.maximum();

        assertEquals(Set.of(), Witnesses.heldBackFor(shape(Type.STRING), most, READING),
                "a string of exactly as many characters as one is worth building");
        assertEquals(Set.of(CompositionBudget.CHARACTERS_A_PROPOSAL_HOLDS),
                Witnesses.heldBackFor(shape(Type.STRING), most + 1, READING),
                "and one past it names the string's figure and not the collection's");
    }

    /**
     * A total the figure does reach is composed, and what it did not offer is held back rather than
     * reported as a composing that was stopped.
     *
     * <p>The distinction the account turns on. A budget that cut an offering short after a value
     * was built took nothing away from the point, so it may not arrive as a point nothing was
     * composed for.
     */
    @Test
    void aTotalTheFigureReachesIsBuiltAndSaysWhatItHeldBack() {
        TermRealizations.Realization made = totalOf(4);

        TermRealizations.Realization.Built built = assertInstanceOf(
                TermRealizations.Realization.Built.class, made,
                () -> "a container of ones reaches a small total: " + made);
        assertFalse(built.values().isEmpty(), "and a row can be written from it");
        assertFalse(built.heldBack().isEmpty(),
                "the walk offered some of the decompositions and says which figures stopped the"
                        + " rest, which is not the same news as nothing having been composed");
    }

    /** Containers of whole numbers adding up to {@code total}. */
    private static TermRealizations.Realization totalOf(int total) {
        Type ofWholeNumbers = new Type.ListOf(Type.INT);
        NumericTerm.TakenOf sum = NumericTerm.TakenOf.of(
                ValueName.Stdlib.operation("List", "sum"), TermPath.of("ns"), ofWholeNumbers,
                SYMBOLS);
        assertNotNull(sum, "a walk that adds up a list of whole numbers is a number of it");
        TermOrders orders = souther.compiler.inputs.TermOrdersFixtures
                .at(sum, ofWholeNumbers, SYMBOLS);
        return TermRealizations.at(ofWholeNumbers, orders, Count.of(total),
                NothingTheRulesSay.REGION, READING);
    }

    /** Every budget the enum names has a figure, and a figure nobody could reach is not one. */
    @Test
    void everyBudgetNamesAFigureAWalkCouldReach() {
        List<String> wrong = new ArrayList<>();
        for (CompositionBudget each : CompositionBudget.values()) {
            if (each.maximum() <= 0) {
                wrong.add(each + " stops at " + each.maximum());
            }
        }

        assertEquals(List.of(), wrong, "a budget is how much of a search this compiler will do");
    }
}
