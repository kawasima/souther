package souther.compiler.partition;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.check.Carrier;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.RunSource;
import souther.compiler.inputs.TermOrders;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.observe.ObservedValue;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A total of a container is added up over its elements, and the elements are read the same way
 * wherever the total is.
 *
 * <p>The values a total is taken of are places of the order the answer is measured on: a walk
 * carries what it has so far in the type it answers, so an element is a place of the same carrier
 * the sum is. The other end is the order the value at the term's own path is written on, and a
 * container is written on none — so a reader taking that end of the pair has nothing to add its
 * elements up with, and the total it answers is no number. Every class of the axis measuring that
 * total then says it does not hold the value, which is the partition's own contract broken: a class
 * holds every value of the position it is a class of.
 *
 * <p>Which is why a row is written here. Measuring the reading alone leaves this unexercised — an
 * axis is made and a border is drawn without any row falling into a class.
 */
class ATotalReadsItsElementsOnOneOrderWhereverItIsReadTest {

    private static final String MODEL = """
            module example.totals

            data Yes
            data No

            behavior overAListAtAPosition : (planned: List<Int>) -> Yes | No
            let overAListAtAPosition (planned) =
                if List.sum(planned) >= 3 then Yes else No

            example overAListAtAPosition
                | "under" : ([ 1 ]) -> No
                | "over"  : ([ 1, 5 ]) -> Yes
            """;

    /** A row written against a total of a list at a position is classified, like any other. */
    @Test
    void aRowAgainstATotalOfAListAtAPositionFallsInAClass() {
        String report = report();
        assertTrue(report.contains("equivalence partitions 2/2"),
                () -> "both rows fell in a class of the total: " + report);
    }

    /**
     * The two readers of one account add the same values up to the same number.
     *
     * <p>The orders they are given are the ones a container stands on: none of its own, and the
     * total measured by whole numbers. A reader taking the first end has nothing to read the
     * elements on, and the number it answers of a list of ones and fives is no number at all.
     */
    @Test
    void aTotalOfAContainerAndATotalOverARunReadOneOrder() {
        List<ObservedValue> elements = List.of(new ObservedValue.Integer(1),
                new ObservedValue.Integer(5));
        assertEquals(Count.of(6),
                numberOf(AT_A_POSITIONS_ORDERS.read(new ObservedValue.Sequence(elements))),
                "the total of what the place holds");
        assertEquals(Count.of(6), numberOf(OVER_A_RUNS_ORDERS.readOver(elements)),
                "and the total over the values of a run, which are the same values");
    }

    /** {@code List.sum} of what a place holds. */
    private static final NumericTerm.FromOnePosition AT_A_POSITION = NumericTerm.TakenOf.of(
            ValueName.Stdlib.operation("List", "sum"), TermPath.of("ns"),
            new Type.ListOf(Type.INT), Symbols.none(souther.compiler.DefaultStdlib.get()));

    /** And of the values a walk answered, which is the same operation over a run. */
    private static final NumericTerm.TakenOver OVER_A_RUN = NumericTerm.TakenOver.of(
            ValueName.Stdlib.operation("List", "sum"),
            RunSource.overTheOccurrencesAt(TermPath.of("ns").element()), Type.INT,
            Symbols.none(souther.compiler.DefaultStdlib.get()));

    /** A container is on no order of its own, and the total it answers is counted by one. */
    private static final TermOrders AT_A_POSITIONS_ORDERS =
            souther.compiler.inputs.TermOrdersFixtures.orders(AT_A_POSITION, null, Carrier.WHOLE);

    /** The same two orders, of the number over the run — which is the point: one operation, one
     *  account of what its elements are places of, whichever of the two names it. */
    private static final TermOrders OVER_A_RUNS_ORDERS =
            souther.compiler.inputs.TermOrdersFixtures.orders(OVER_A_RUN, null, Carrier.WHOLE);

    private static Count numberOf(NumericTerm.Reading read) {
        return (Count) ((NumericTerm.Reading.Number) read).value();
    }

    private static String report() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
