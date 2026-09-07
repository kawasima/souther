package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Carrier;
import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.RuleRef;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading that lost a line is not a reading that ran out.
 *
 * <p>{@code Closed} is what takes a behavior out of the verdict, and it used to follow from three
 * absences: nothing was set aside, nothing went unanswered, no position went unreached. A line
 * dropped before it could be set aside leaves all three of those exactly as they were, so a measure
 * built from them reported a model read in full on the strength of a reading that had lost part of
 * it — a {@code Decimal} bounded by two clauses came back as a behavior whose rules draw no line
 * anywhere, and the module was called adequate (issue #1079).
 *
 * <p>So what the reading produced is held against what it found, by identity. Counted instead, a
 * reading that lost one line and made another twice comes back whole; asked of the lines themselves,
 * neither is possible.
 */
class AClosedReadingIsOneThatDrewEveryLineItFoundTest {

    private static final AxisId AT = new AxisId("take", "h.a");

    /** The number that measure is of, which its orders are the orders of. */
    private static final NumericTerm.ValueOf AT_A =
            new NumericTerm.ValueOf(TermPath.of("h").then("a"));

    /** A reading of one line that drew it, which is what a closed one is. */
    @Test
    void aReadingThatDrewItsLineMayBeClosed() {
        LinesRead read = new LinesRead();
        read.found(aLine(), bound("cap"));
        read.returning(List.of(read.drew(aBorder("cap"))));

        MeasureClosure.Both closed =
                MeasureClosure.of(List.of(aPosition()), List.of(), read);

        assertInstanceOf(MeasureClosure.OfTheBorder.Closed.class, closed.border(),
                "every question this measure answers was answered");
        assertInstanceOf(MeasureClosure.OfThePartition.Closed.class, closed.partition());
    }

    /**
     * One found and not drawn is refused rather than reported as a gap.
     *
     * <p>What was lost is not known: a gap says which rule or which position a measure is short of,
     * and a reading that dropped one has nothing to name. This is this compiler having lost
     * something, which is the one thing a document may not be written from.
     */
    @Test
    void aLineFoundAndNotDrawnIsRefused() {
        LinesRead read = new LinesRead();
        read.found(aLine(), bound("cap"));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> MeasureClosure.of(List.of(aPosition()), List.of(), read));

        assertTrue(refused.getMessage().startsWith("a line this reading found and did not draw"),
                refused.getMessage());
        assertTrue(refused.getMessage().contains("invariant Amount (cap)"), refused.getMessage());
    }

    /**
     * And one drawn twice, which a count cannot tell from one drawn once beside one lost.
     *
     * <p>Two borders at one place drawn by one rule ask for the same row under two names. Held as a
     * number, a reading that dropped a line and made another twice adds up to what a whole one does
     * — which is why the account is of the lines and not of how many there were.
     */
    @Test
    void aLineDrawnTwiceIsRefused() {
        LinesRead read = new LinesRead();
        read.found(aLine(), bound("cap"));
        read.drew(aBorder("cap"));
        read.drew(aBorder("cap"));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> MeasureClosure.of(List.of(aPosition()), List.of(), read));

        assertTrue(refused.getMessage().startsWith("a line drawn more than once"),
                refused.getMessage());
    }

    /** And a border off a line this reading never met, which is the other way the two come apart. */
    @Test
    void aBorderTheReadingNeverFoundIsRefused() {
        LinesRead read = new LinesRead();
        read.found(aLine(), bound("cap"));
        read.drew(aBorder("cap"));
        read.drew(aBorder("floor"));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> MeasureClosure.of(List.of(aPosition()), List.of(), read));

        assertTrue(refused.getMessage().startsWith("a border this reading cannot account for"),
                refused.getMessage());
    }

    /**
     * And a border the reading hands back that it never wrote down.
     *
     * <p>The other end of the account, and the one that does not depend on a producer having
     * remembered. Every producer there is writes into this today; a third one added later would be
     * off the books from the day it is written, and what it made would still reach a caller — so
     * what the reading returns is held against what it says it made.
     */
    @Test
    void aBorderReturnedThatWasNeverWrittenDownIsRefused() {
        LinesRead read = new LinesRead();
        read.found(aLine(), bound("cap"));
        read.drew(aBorder("cap"));
        read.returning(List.of(aBorder("cap"), aBorder("floor")));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> MeasureClosure.of(List.of(aPosition()), List.of(), read));

        assertTrue(refused.getMessage().startsWith(
                        "a border returned by a reading that did not write it down"),
                refused.getMessage());
    }

    /**
     * And one it wrote down and did not hand back, which is a loss between the two.
     */
    @Test
    void aBorderDrawnAndNotReturnedIsRefused() {
        LinesRead read = new LinesRead();
        read.found(aLine(), bound("cap"));
        read.drew(aBorder("cap"));
        read.returning(List.of());

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> MeasureClosure.of(List.of(aPosition()), List.of(), read));

        assertTrue(refused.getMessage().startsWith(
                        "a border this reading drew and did not return"),
                refused.getMessage());
    }

    /** The position the lines here are of. This measure's closure is about what the reading of a
     *  position came to, and the axis on it is not what that is asked of. */
    private static PositionAccount aPosition() {
        return PositionAccount.at("f", TermPath.of("h").then("a"), Type.INT);
    }

    private static BoundaryTarget aLine() {
        return BoundaryTarget.at(
                new BorderQuantity.OfACoordinate(AT.behavior(), AT_A,
                        souther.compiler.inputs.TermOrdersFixtures.itself(AT_A, Carrier.WHOLE)),
                new Level.OnACarrier(Carrier.WHOLE, Count.of(5)));
    }

    private static Border aBorder(String clause) {
        return Border.at(aLine(), bound(clause),
                new souther.compiler.numeric.NumericDomain.Bounds(
                        souther.compiler.numeric.Endpoint.inclusive(Count.of(5)), null));
    }

    private static LineOrigin bound(String clause) {
        return new LineOrigin.InvariantOrigin(
                new souther.compiler.check.PartId<>(new RuleRef.Invariant(new Clause.Ref(
                        new Clause.Id(
                                TypeSymbols.declared(new TypeKey("example.rate", "Amount")), 0),
                        java.util.Optional.of(new ClauseName(clause)))), 0),
                souther.compiler.numeric.EndSide.LOWER, true);
    }
}
