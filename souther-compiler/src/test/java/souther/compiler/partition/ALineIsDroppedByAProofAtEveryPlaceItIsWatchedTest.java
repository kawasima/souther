package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Carrier;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.inputs.TermOrdersFixtures;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Towards;
import souther.compiler.reach.ComparisonArrival;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A line goes only where every place it is watched at proved nothing reaches it.
 *
 * <p>One rule may be written into the tree that runs more than once — a library operation
 * evaluating a closure it was handed twice writes the comparison twice — so what arrives at the
 * line is answered once per place. The rule is one: a run through any of the copies is a run
 * through it, so the line is what the model states about all of them.
 *
 * <p><b>Dropped by a proof and never by the absence of one.</b> A walk that could not project what
 * arrives says so ({@link ComparisonArrival.NoProjection}) and restricts nothing; read as a place
 * where nothing arrives, it would take a line away because this compiler did not look.
 *
 * <p><b>Which is why the quantifier is the whole of this.</b> Every model written today watches
 * each rule in one place, and there the two quantifiers answer alike — so the rule is held here,
 * over the arrivals themselves, rather than waiting for a corpus that happens to write one twice.
 */
class ALineIsDroppedByAProofAtEveryPlaceItIsWatchedTest {

    private static final Carrier WHOLE = new Carrier.Whole();

    /** {@code n > 10}, read as a line on the values at {@code n}. */
    private static ComparisonAssessment.AtAPosition line() {
        NumericTerm.FromOnePosition n = new NumericTerm.ValueOf(TermPath.of("n"));
        Cutting cutting = new Cutting(
                new BorderQuantity.OfACoordinate("f", n,
                        TermOrdersFixtures.itself(n, WHOLE)),
                new Level.OnACarrier(WHOLE, new Count(BigDecimal.TEN)),
                new ComparisonClaim.Cut(Towards.BELOW, false), null);
        return new ComparisonAssessment.AtAPosition(cutting, n, null,
                ComparisonAssessment.Places.AT_NO_VALUE);
    }

    /** Values that stop short of the line, which is a proof that nothing reaches it. */
    private static ComparisonArrival misses() {
        return new ComparisonArrival.Values(TermPath.of("n"),
                new NumericDomain.Bounds(null,
                        new Endpoint(new Count(BigDecimal.ONE), true)));
    }

    @Test
    void oneProofAmongSeveralPlacesTakesNoLineAway() {
        ComparisonAssessment read = line();

        assertEquals(read, ComparisonAssessment.narrowedByWhatArrives(read,
                        List.of(new ComparisonArrival.NothingArrives(), new ComparisonArrival.NoProjection()),
                        false),
                "one place nobody could look at is not a place nothing arrives at");
    }

    @Test
    void everyPlaceProvingItTakesTheLineAway() {
        assertInstanceOf(ComparisonAssessment.NothingArrivesAtItsLine.class,
                ComparisonAssessment.narrowedByWhatArrives(line(),
                        List.of(new ComparisonArrival.NothingArrives(), misses()), false),
                "no run answers through the rule at any place it is watched");
    }

    @Test
    void onePlaceReachingItKeepsTheLine() {
        ComparisonAssessment read = line();

        assertEquals(read, ComparisonAssessment.narrowedByWhatArrives(read,
                        List.of(new ComparisonArrival.NothingArrives(),
                                new ComparisonArrival.Values(TermPath.of("n"),
                                        NumericDomain.Bounds.OPEN)),
                        false),
                "a run reaches the line at one of the places the rule is watched at");
    }
}
