package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.AReadingOfAPosition;
import souther.compiler.check.Carrier;
import souther.compiler.check.Clause;
import souther.compiler.check.DeclaredLine;
import souther.compiler.check.InvariantStatementId;
import souther.compiler.check.PartId;
import souther.compiler.check.ClauseName;
import souther.compiler.check.MatchedEndAttribution;
import souther.compiler.check.NarrowedBounds;
import souther.compiler.check.RuleRef;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermOrdersFixtures;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.EndSide;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A declaration is named as taking a run's end in only where that is the end it was read at.
 *
 * <p>Two readings reach a border and they are readings of different rules. Which declarations hold
 * an end comes from the value the position sits in, and is worked out against the number that
 * reading arrived at; where the run actually stops is what every rule reaching the behavior leaves
 * the term, which that reading knew nothing of. So the two can leave the position in different
 * places, and a name carried across without asking is a name written against a number no clause of
 * that declaration moved — an author sent to a line that admits every value the run stops short of.
 *
 * <p>Assembled here rather than read from a model. What decides this is which end the reading was
 * asked about, and a model reaching the case where the two ends differ is a separate question about
 * what the declarations can be made to say — one this does not answer and does not need to.
 */
class ANarrowingIsToldOnlyAboutTheEndItWasReadAtTest {

    private static final Carrier WHOLE = new Carrier.Whole();

    /** Where the rules leave the term: a minimum at a hundred, running to a thousand. */
    private static final NumericDomain.Bounds RUNS_TO_A_THOUSAND = new NumericDomain.Bounds(
            Endpoint.inclusive(Count.of(100)), Endpoint.inclusive(Count.of(1000)));

    /** The reading that answers about the end the run stops at is the one that is heard. */
    @Test
    void aReadingAboutTheEndTheRunStopsAtIsNamedThere() {
        assertEquals(List.of(HELD),
                narrowersInsideTheRun(AReadingOfAPosition.withAnUpperEndAt(
                        Endpoint.inclusive(Count.of(1000)), HELD), RUNS_TO_A_THOUSAND),
                "the reading stops the position where the run stops, so what holds that end holds"
                        + " this one");
    }

    /**
     * And a reading about another end is not.
     *
     * <p>The declaration did move an end; it moved one this run never reaches. Named here, a report
     * would send its author to a clause that leaves every value between the two ends in.
     */
    @Test
    void aReadingAboutAnotherEndIsNotNamedThere() {
        assertEquals(List.of(),
                narrowersInsideTheRun(AReadingOfAPosition.withAnUpperEndAt(
                        Endpoint.inclusive(Count.of(999)), HELD), RUNS_TO_A_THOUSAND),
                "999 is not where the run stops, so what holds it holds nothing here");
    }

    /**
     * Nor is a reading of the end at the other side of the run.
     *
     * <p>The low end of what the rules leave is where this run starts and not where it stops, so a
     * reading answering about it is answering about another end. Here the two are at two numbers and
     * that is all this shows — whether the side travels beside the number is a question the numbers
     * cannot be made to ask at a border, because a quantity whose two ends are one number holds one
     * value and has no point inside a run to be owed anything.
     * {@code ARangeStoppingAtOneValueStillHasTwoEndsTest} asks it where it can be asked.
     */
    @Test
    void aReadingOfTheEndTheRunStartsAtIsNotNamedWhereItStops() {
        assertEquals(List.of(),
                narrowersInsideTheRun(AReadingOfAPosition.withALowerEndAt(
                        Endpoint.inclusive(Count.of(100)), HELD), RUNS_TO_A_THOUSAND),
                "a hundred is where the run starts, and this end is not the one it stops at");
    }

    /**
     * A cut refuses a narrowing worked out at the other side, at the same number.
     *
     * <p>The other place a name crosses to an end, and the one where the two ends can be one number
     * without the quantity holding one value: a minimum and a maximum of one position both stand at
     * a number some clause wrote. So the origin is held to the end the bound placed, and asks rather
     * than taking the caller's word — the caller having asked the reading correctly is what nothing
     * downstream can see.
     */
    @Test
    void aBoundIsNotTakenInByWhatHoldsTheOtherEnd() {
        Endpoint at = Endpoint.inclusive(Count.of(100));
        MatchedEndAttribution holdsTheHighEnd = AReadingOfAPosition.withAnUpperEndAt(at, HELD)
                .matching(EndSide.UPPER, at).orElseThrow();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> LineOrigin.NarrowedOrigin.of(aMinimum(), at, holdsTheHighEnd));
        assertTrue(refused.getMessage().startsWith("a bound placing the LOWER end"),
                refused.getMessage());
    }

    /** And one worked out at another number on its own side. */
    @Test
    void aBoundIsNotTakenInByWhatHoldsAnotherPlace() {
        Endpoint elsewhere = Endpoint.inclusive(Count.of(99));
        MatchedEndAttribution holdsAnotherEnd = AReadingOfAPosition
                .withALowerEndAt(elsewhere, HELD)
                .matching(EndSide.LOWER, elsewhere).orElseThrow();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> LineOrigin.NarrowedOrigin.of(aMinimum(),
                        Endpoint.inclusive(Count.of(100)), holdsAnotherEnd));
        assertTrue(refused.getMessage().startsWith("a cut at "), refused.getMessage());
    }

    /**
     * What a row inside the run is told the run's far end is owed to.
     *
     * <p>The point inside the partition a minimum bounds: its line is the minimum's own, and the far
     * side of the run beside it is where the rules leave the term.
     */
    private static List<TypeSymbol.AtModule> narrowersInsideTheRun(NarrowedBounds narrowed,
                                                                   NumericDomain.Bounds runs) {
        PointAnswer answer = Border.at(aLineAt(100), aMinimum(), runs, List.of(), narrowed)
                .answer(PointRole.IN);
        return ((PointAnswer.InRegion) answer).claims().stream()
                .flatMap(claim -> claim.contributions().narrowers().stream())
                .toList();
    }

    /** A declaration that could have moved where the position stops. */
    private static final TypeSymbol.AtModule HELD =
            TypeSymbols.declared(new TypeKey("example.weigh", "Held"));

    /** The clause the bound is written in, which is only an identity here. */
    private static LineOrigin.InvariantOrigin aMinimum() {
        return new LineOrigin.InvariantOrigin(
                new DeclaredLine.OfAStatement(new InvariantStatementId(
                        new PartId<>(new RuleRef.Invariant(new Clause.Ref(
                                new Clause.Id(TypeSymbols.declared(
                                        new TypeKey("example.weigh", "Amount")), 0),
                                Optional.of(new ClauseName("floor")))), 0),
                        0)),
                EndSide.LOWER, true);
    }

    private static BoundaryTarget aLineAt(int value) {
        AxisId axis = new AxisId("weigh", "w.a");
        NumericTerm.ValueOf term = new NumericTerm.ValueOf(TermPath.of(axis.term()));
        return BoundaryTarget.at(
                new BorderQuantity.OfACoordinate(axis.behavior(), term,
                        TermOrdersFixtures.itself(term, WHOLE)),
                new Level.OnACarrier(WHOLE, Count.of(value)));
    }
}
