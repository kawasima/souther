package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Carrier;
import souther.compiler.check.DeclaredLine;
import souther.compiler.check.InvariantStatementId;
import souther.compiler.check.PartId;
import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.RuleRef;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.InputReading;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermOrdersFixtures;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.EndSide;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.ReadAs;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A line a measurement reads is a line that measurement drew.
 *
 * <p>Which lines there are was settled when the input was measured. So what makes a border one of
 * this measurement's is not that its behavior, its numbers, the position it is on and the orders it
 * is measured on each agree with this one — those are the attributes of a value that already has an
 * identity, and comparing them one at a time is a derivation that is never finished. It is that
 * this measurement drew it.
 *
 * <p>And what comes back is the line the measurement holds rather than the one handed in. A reader
 * of a line asks it what it demands of a row and where the run below it stops, and answers off the
 * value it was given — so a caller's copy would be read in place of the reading's own, whatever it
 * carried where the caller got it.
 */
class ALineIsOfAPositionTheMeasurementMeasuresTest {

    private static final RuleReadingSource SYMBOLS =
            RuleReadings.ofNoClauseFiled(Symbols.none(DefaultStdlib.get()));

    private static final Carrier WHOLE = new Carrier.Whole();

    /** The reading of an input of one parameter apiece, all of them plain numbers. */
    private static InputReading readingOf(String... parameters) {
        List<InputDomain.Parameter> declared = new ArrayList<>();
        for (String each : parameters) {
            declared.add(new InputDomain.Parameter(each, null, Type.INT));
        }
        return InputDomain.of(declared, SYMBOLS, ReadAs.THE_COMPILATION_DOES).reading(SYMBOLS);
    }

    private static NumericTerm.ValueOf number(String parameter) {
        return new NumericTerm.ValueOf(TermPath.of(parameter));
    }

    /** A measure that bounds the number at that parameter and divides it into nothing, which is
     *  what a rule that only caps a value leaves. */
    private static Axis bounding(String behavior, String parameter) {
        NumericTerm.ValueOf number = number(parameter);
        return new Axis(AxisId.of(behavior, number), number, List.of(),
                List.of(new Cut(WHOLE, Count.of(100), List.of(aBound()))));
    }

    /** A line at a hundred on the number that parameter holds. */
    private static Border lineOn(String behavior, String parameter) {
        NumericTerm.ValueOf number = number(parameter);
        return Border.at(BoundaryTarget.at(
                        new BorderQuantity.OfACoordinate(behavior, number,
                                TermOrdersFixtures.itself(number, WHOLE)),
                        new Level.OnACarrier(WHOLE, Count.of(100))),
                aBound(),
                new NumericDomain.Bounds(Endpoint.inclusive(Count.of(100)),
                        Endpoint.inclusive(Count.of(1000))));
    }

    private static LineOrigin aBound() {
        return new LineOrigin.InvariantOrigin(
                new DeclaredLine.OfAStatement(new InvariantStatementId(
                        new PartId<>(new RuleRef.Invariant(new Clause.Ref(
                                new Clause.Id(TypeSymbols.declared(
                                        new TypeKey("example.fee", "Amount")), 0),
                                Optional.of(new ClauseName("cap")))), 0),
                        0)),
                EndSide.LOWER, true);
    }

    /** A measurement that bounds {@code days} and read the line at that bound. */
    private static MeasuredInput measuring(String... parameters) {
        Axis bounded = bounding("fee", "days");
        return MeasuredInput.of("fee", readingOf(parameters),
                AxesATestWrote.asAMeasurement("fee", List.of(bounded),
                        bounded.id(), List.of(lineOn("fee", "days"))));
    }

    /** The line the measurement drew is read. */
    @Test
    void aLineTheMeasurementDrewIsRead() {
        MeasuredInput subject = measuring("days");

        assertNotNull(subject.at(lineOn("fee", "days")),
                "the measurement read a line where this one is");
    }

    /**
     * And a line on a position the reading knows and the measurement never drew at is not.
     *
     * <p>The number is one this input has and one this reading answers for, so nothing about the
     * term says no. What the measurement has no line at is the place, and reading a row there would
     * be reading it at a line this measurement never drew.
     */
    @Test
    void aLineTheMeasurementNeverDrewIsRefused() {
        MeasuredInput subject = measuring("days", "cap");

        assertNotNull(subject.quantities().ordersOf(number("cap")),
                "the reading answers for the number the refused line is on");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> subject.at(lineOn("fee", "cap")));
        assertTrue(refused.getMessage().startsWith(
                        "the measurement of fee read no line where this one is:"),
                refused.getMessage());
    }

    /**
     * What comes back is the measurement's own line and not the one asked with.
     *
     * <p>Asked with a border carrying what a caller had beside it — here the answers a later
     * reading of the same line filled in — the reading gives back the value it holds. Anything
     * else and a row would be read at the caller's copy: what a line demands and where the run
     * below it stops are read off whatever value the reader was handed.
     */
    @Test
    void theMeasurementsOwnLineComesBack() {
        MeasuredInput subject = measuring("days");
        Border mine = subject.partitioning().along(bounding("fee", "days")).get(0);

        Border asked = new Border(mine.cut(), mine.origin(), mine.answers());
        assertNotSame(mine, asked, "a border put together again, equal to the one it stands for");

        assertSame(mine, subject.at(asked).border(),
                "and what comes back is the one the measurement drew");
    }
}
