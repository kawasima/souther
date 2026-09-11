package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermOrders;
import souther.compiler.inputs.TermOrdersFixtures;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A total the rules leave no container for comes back as one nothing here writes a container for.
 *
 * <p>Every count the rules admit is tried and every shape of each of them, and the figures are
 * nowhere near: the rules hold the container to fewer elements than one is worth carrying, and the
 * shapes a decomposition takes are all offered at each count. So what is left when nothing is
 * composed is the model, and a walk that came back naming a figure would be sending a reader to
 * raise a number that took nothing away from them.
 *
 * <p><b>Held to a container the counts run out inside.</b> A list nothing bounds has counts above
 * the figure that were never tried, and a figure reached there is a figure that really did decline
 * to go on — which is a different answer and not this one. What this pins is the case where the
 * counts are exhausted before any figure is met.
 */
class ATotalNothingReachesIsNotOneThisCompilerStoppedAtTest {

    private static final Symbols SYMBOLS = Symbols.none(DefaultStdlib.get());

    private static final RuleReadingSource RULES = RuleReadings.ofNoClauseFiled(SYMBOLS);

    private static final ReadingPolicy POLICY = new ReadingPolicy(64, 12,
            souther.compiler.values.AsACompilationAllows.admittedValues(),
            souther.compiler.values.AsACompilationAllows.whatARuleLeaves());

    private static final RuleReadingContext READING =
            RuleReadingContext.unshared(RULES, POLICY);

    /** A list of whole numbers, which is what the rules below hold to three values either way. */
    private static final Type OF_WHOLE_NUMBERS = new Type.ListOf(Type.INT);

    /**
     * Three values every position of the region runs over, which is nought to two.
     *
     * <p>Both the count and the elements, since the region answers alike for every term. So the
     * container holds at most two and each of them is at most two, and every count the rules admit
     * is inside the figure.
     */
    private static final int A_RUN_OF_THREE = 3;

    /** More than the largest container the rules leave: two elements of two come to four. */
    private static final Count FIVE = Count.of(5);

    /**
     * Nothing composes it, and no figure of this compiler's was reached working that out.
     *
     * <p>The word and the evidence both. A reader told the walk stopped would look for a number to
     * raise; what happened is that the rules leave no container adding up to this, which is an
     * answer about the model.
     */
    @Test
    void aTotalTheRulesLeaveNoContainerForIsComposedByNothing() {
        TermRealizations.Realization made = totalOf(FIVE);

        TermRealizations.Realization.None none = assertInstanceOf(
                TermRealizations.Realization.None.class, made,
                () -> "no container of at most two whole numbers, each at most two, comes to five,"
                        + " and no figure declined to look for one: " + made);
        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE, none.why(),
                "which is the word for a point nothing here writes a value at");
    }

    /** And a total the same rules do leave a container for is composed, so the run is walked. */
    @Test
    void aTotalTheyDoLeaveOneForIsComposed() {
        TermRealizations.Realization made = totalOf(Count.of(4));

        assertInstanceOf(TermRealizations.Realization.Built.class, made,
                () -> "two elements of two come to four, which the same rules admit: " + made);
    }

    /** Containers of whole numbers adding up to {@code total}, under a region that bounds both how
     *  many the container holds and what each of them is. */
    private static TermRealizations.Realization totalOf(Count total) {
        NumericTerm.TakenOf sum = NumericTerm.TakenOf.of(
                ValueName.Stdlib.operation("List", "sum"), TermPath.of("ns"), OF_WHOLE_NUMBERS,
                SYMBOLS);
        assertNotNull(sum, "a walk that adds up a list of whole numbers is a number of it");
        TermOrders orders = TermOrdersFixtures.at(sum, OF_WHOLE_NUMBERS, SYMBOLS);
        return TermRealizations.at(OF_WHOLE_NUMBERS, orders, total,
                new ARunOfThisMany(A_RUN_OF_THREE), READING);
    }
}
