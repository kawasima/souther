package souther.compiler.examples;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.Carrier;
import souther.compiler.check.CheckedDeclarations;
import souther.compiler.check.ScopedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.RunSource;
import souther.compiler.inputs.TermOrders;
import souther.compiler.inputs.TermOrdersFixtures;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.LinearForm;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.Limits;
import souther.compiler.observe.ObservedValue;
import souther.compiler.partition.BorderQuantity;
import souther.compiler.partition.Criterion;
import souther.compiler.partition.Level;
import souther.compiler.partition.ObservationAtPoint;
import souther.compiler.partition.ReadingGap;
import souther.compiler.partition.WalkResult;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A limit that fired is not a limit the term read, and the two are joined here.
 *
 * <p>Whether an observation stopped and whether the number a rule is about was one of the things it
 * stopped at are different facts. A count taken at a container's root survives a walk that ran out
 * of nodes inside it — the elements arrive stopped and there are still as many of them — so a walk
 * under a budget and a reading that met the budget do not follow from one another.
 *
 * <p>Which is why the two halves are joined by a value and not by argument. Above this, a producer
 * test holds that each budget stops a walk; below it, a reading test holds that a stopped value
 * never reads as a row that does not stand. Neither says that what the first produces arrives where
 * the second is asked, and a model was met that crossed a budget by two thousand nodes and read
 * back a perfectly good number.
 *
 * <p>Under limits of its own, so the join is a few values wide rather than a compilation deep. What
 * is being held is the path from a walk to a term, and the numbers a shipped observation happens to
 * use are not part of it.
 */
class ALimitThatFiredIsNotALimitTheTermReadTest {

    private static final TermPath UNDER = TermPath.of("lines").element();

    private static final NumericTerm.TakenOver TOTAL = NumericTerm.TakenOver.of(
            ValueName.Stdlib.operation("List", "sum"),
            new RunSource.ProjectedOccurrences(UNDER),
            souther.compiler.types.Type.Prim.INT,
            souther.compiler.check.NewtypeInners.NONE, Symbols.none(DefaultStdlib.get()));

    private static final TermOrders ON_THE_TOTAL =
            TermOrdersFixtures.itself(TOTAL, new Carrier.Whole());

    /** Room for the container and a few of its elements, and no more. */
    private static final Limits FOUR_NODES = new Limits(12, 4, 64, 1024);

    /**
     * A run whose later elements the node budget stopped reads as a reading that was stopped.
     *
     * <p>The whole path in one value: the walk is run under a budget it cannot meet, and what comes
     * out of it is handed to the term the rule is about.
     */
    @Test
    void aRunTheNodeBudgetStoppedInsideReadsAsStopped() {
        ObservedValue observed = observed(FOUR_NODES, longs(6));

        assertEquals(BorderQuantity.Stands.couldNotTell(
                        ReadingGap.of(Incompleteness.Code.VALUE_TRUNCATED)),
                form().standsAt(atTheLevel(15), run(observed)),
                "the elements the walk stopped at are the ones the total is over");
    }

    /**
     * And one the same budget kept whole reads as the number it holds.
     *
     * <p>The control. Without it every reading of this passes for a walk that stops at everything.
     */
    @Test
    void aRunTheSameBudgetKeptReadsAsItsTotal() {
        ObservedValue observed = observed(FOUR_NODES, longs(3));

        assertEquals(BorderQuantity.Stands.YES,
                form().standsAt(atTheLevel(6), run(observed)),
                "three elements fit the budget, and their total is what the run comes to");
    }

    /**
     * A budget that fired somewhere the term does not read leaves the reading whole.
     *
     * <p>The case the two halves apart cannot see. The walk stopped — the elements inside each
     * entry are gone — and the number this rule is about is how many entries there are, which the
     * container still says. A test that watched only for a limit to fire would call this covered.
     */
    @Test
    void aBudgetThatFiredWhereTheTermDoesNotReadLeavesItReadable() {
        ObservedValue observed = observed(FOUR_NODES, nested(6));

        assertInstanceOf(ObservedValue.Sequence.class, observed,
                "the container survives a walk that ran out inside it");
        assertEquals(6, ((ObservedValue.Sequence) observed).elements().size(),
                "and still holds as many elements as were written");
        assertEquals(BorderQuantity.Stands.YES, howMany().standsAt(atTheLevel(6), at(observed)),
                "so a count of them is a number, though a limit fired inside every one");
    }

    private static Criterion atTheLevel(long at) {
        return new Criterion.AtTheLevel(new Level.ACount(Count.of(at)));
    }

    private static BorderQuantity.OverAForm form() {
        return new BorderQuantity.OverAForm("decide",
                LinearForm.atom((NumericTerm) TOTAL), Map.of(TOTAL, ON_THE_TOTAL));
    }

    /**
     * The same container, counted rather than added up.
     *
     * <p>A number read at the container's own root, which is what makes it the other half of the
     * pair: the walk stops in the same places and this term never looks at any of them.
     */
    private static BorderQuantity.OverAForm howMany() {
        NumericTerm.TakenOf counted = NumericTerm.TakenOf.of(
                ValueName.Stdlib.operation("List", "length"),
                TermPath.of("lines"),
                new souther.compiler.types.Type.ListOf(souther.compiler.types.Type.Prim.INT),
                souther.compiler.check.NewtypeInners.NONE, Symbols.none(DefaultStdlib.get()));
        return new BorderQuantity.OverAForm("decide", LinearForm.atom((NumericTerm) counted),
                Map.of(counted, TermOrdersFixtures.itself(counted, new Carrier.Whole())));
    }

    /** A row whose only readable place is the container itself. */
    private static BorderQuantity.Observation at(ObservedValue observed) {
        return new BorderQuantity.Observation() {

            @Override
            public WalkResult<ObservationAtPoint> at(TermPath path) {
                assertEquals(TermPath.of("lines"), path, "read at the container the count is of");
                return WalkResult.reached(new ObservationAtPoint.Value(observed));
            }

            @Override
            public WalkResult<List<ObservedValue>> everyValueAt(TermPath path) {
                throw new AssertionError("a count of a container is not read over a run");
            }
        };
    }

    /** A row whose only readable place is the run, holding what {@code observed} came to. */
    private static BorderQuantity.Observation run(ObservedValue observed) {
        return new BorderQuantity.Observation() {

            @Override
            public WalkResult<ObservationAtPoint> at(TermPath path) {
                throw new AssertionError("a number over a run is not read from one value");
            }

            @Override
            public WalkResult<List<ObservedValue>> everyValueAt(TermPath path) {
                return WalkResult.reached(observed instanceof ObservedValue.Sequence held
                        ? held.elements() : List.of(observed));
            }
        };
    }

    private static ObservedValue observed(Limits limits, Object live) {
        Symbols symbols = Symbols.none(DefaultStdlib.get());
        // No module is being read, so nothing here declares a data whose fields could be asked for.
        return ObservedValues.of(live, symbols,
                new NeutralForm(symbols, ScopedDeclarations.of(symbols),
                        ScopedDeclarations.kindsOf(symbols),
                        FieldTypes.over(new CheckedDeclarations(_ -> null, _ -> null))), limits);
    }

    private static List<Object> longs(int count) {
        List<Object> out = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            out.add(i);
        }
        return out;
    }

    /** A list of {@code count} lists, so a budget runs out inside the elements and not at them. */
    private static List<Object> nested(int count) {
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(longs(3));
        }
        return out;
    }
}
