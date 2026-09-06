package souther.compiler.partition;

import souther.compiler.coverage.ComparisonEmissionSite;
import souther.compiler.types.WrittenOwner;
import souther.compiler.coverage.Numberings;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Carrier;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.Citation;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Towards;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An account counts by a name it derives, so it says first that the name tells its input apart.
 *
 * <p>What a conservation account is for is the piece that never arrives. Two pieces sharing a name
 * are one entry in what it is owed, and then the piece that went missing is the one the other
 * answered for — the account closes over evidence it never held, and reports the measure complete.
 * Which is the thing it exists to refuse, happening to the account itself.
 *
 * <p>So the check is where the input is taken. A check that fires when two pieces meet assumes what
 * it is meant to establish: the case it has to catch is the one where the second never comes.
 */
class AnAccountEstablishesItsDenominatorBeforeItCountsTest {

    private static final NumericTerm.FromOnePosition AT =
            new NumericTerm.ValueOf(TermPath.of("r").then("cost"));

    /**
     * Two pieces of evidence called one name are refused where they are handed over.
     *
     * <p>One rule parting the position's values in two places is not something the reader above
     * writes today. What this fixes is not that: it is that the account below would not have noticed
     * if it were, and would have said so by reporting a complete reading.
     */
    @Test
    void twoPiecesOfEvidenceUnderOneNameAreRefusedWhereTheyAreHandedOver() {
        RuleEvidence one = dividing("10");
        RuleEvidence other = dividing("20");
        assertEquals(one.id(), other.id(), "the same rule and the same number");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> new EvidenceAccount(List.of(one, other), List.of()));

        assertTrue(refused.getMessage().startsWith("two pieces of evidence are called"),
                refused.getMessage());
    }

    /**
     * And the same piece handed over twice is one piece.
     *
     * <p>Nothing here counts arrivals. What the account holds the stage to is that everything it was
     * given has an answer, so being given one thing twice is being given one thing.
     */
    @Test
    void onePieceHandedOverTwiceIsOnePiece() {
        RuleEvidence one = dividing("10");

        EvidenceAccount account = new EvidenceAccount(List.of(one, one), List.of());
        account.measured(one, new AxisId("f", AT.toString()));

        account.everyPieceWasDisposedOf(List.of(new Axis(new AxisId("f", AT.toString()), AT,
                List.of(),
                List.of(Cut.at(new Carrier.Whole(),
                        new Count(new java.math.BigDecimal("10")), origin())))));
    }

    /** A piece disposed of under a name belonging to another is refused. */
    @Test
    void aPieceDisposedOfUnderAnothersNameIsRefused() {
        EvidenceAccount account = new EvidenceAccount(List.of(dividing("10")), List.of());

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> account.measured(dividing("20"), new AxisId("f", AT.toString())));

        assertTrue(refused.getMessage().contains("under the name of"), refused.getMessage());
    }

    private static RuleEvidence dividing(String at) {
        return new RuleEvidence.Divides(new Threshold(AT,
                Seam.of(LevelSpace.onACarrier(new Carrier.Whole()),
                        new Level.OnACarrier(new Carrier.Whole(),
                                new Count(new java.math.BigDecimal(at))),
                        Towards.BELOW),
                Towards.BELOW, origin()));
    }

    /** The numbering this fixture's places are of. One of them, so that two origins built here
     *  address one place rather than the same number of two numberings. */
    private static final ComparisonEmissionSite WHERE = Numberings.comparison(1, 0);

    /** One rule, written in one place. Two lines of it are told apart by where they part the
     *  values, which is what the account may not be asked to do by name alone. */
    private static LineOrigin origin() {
        return new LineOrigin.ComparisonOrigin(
                new RuleRef.Comparison("f", new souther.compiler.types.SourceConstructOrigin(
                        new WrittenOwner.Body("example.one", "f"), 2, 0,
                        souther.compiler.types.SourceConstruct.BINARY)),
                new LineOrigin.ComparisonOrigin.Read(
                        new souther.compiler.coverage.ComparisonOccurrence("example.one", "f", 0),
                        new RuleCitation.WrittenAt(Citation.of(
                                new souther.compiler.diag.SourcePos(1, 1))),
                        WHERE),
                new LineFacts(new souther.compiler.check.ComparisonClaim.Cut(Towards.BELOW, true)));
    }
}
