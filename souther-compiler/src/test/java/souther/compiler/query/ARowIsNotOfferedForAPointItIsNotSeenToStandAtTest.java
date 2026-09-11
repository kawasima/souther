package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Count;
import souther.compiler.partition.Generator;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.partition.PointRole;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A gap in what the walk can say about the way costs rows, and never costs the truth about them.
 *
 * <p>The two halves of this are asymmetric on purpose, and the asymmetry is the design. What a
 * search composes a row against is however much of the way to the border this reading could state;
 * what says a row is a row for the point is the walk that reads any row at a point. So a condition
 * on the way that nothing could state makes rows harder to compose — and cannot make a row that
 * does not stand at a point be offered for it.
 *
 * <p>Held against a condition this reading declines rather than one it takes in, because that is the
 * case the second half exists for. A model whose way is understood composes a row that reaches and
 * the walk agrees with it; the run worth writing down is the one where the composer was told
 * nothing and guessed.
 *
 * <p>This is the test that goes on holding when a fork nothing here has learned to read arrives in
 * the language. What such a fork takes away is rows. What it may not take away is that an offered
 * row is one nothing saw standing somewhere else.
 */
class ARowIsNotOfferedForAPointItIsNotSeenToStandAtTest {

    /**
     * A comparison reached past a disjunction, which is the shape nothing can state.
     *
     * <p>{@code A || B} coming out true says one of them held and names neither, so there is no cut
     * to narrow a search by. The values the composer then writes at {@code x} and {@code y} are
     * whatever their own rules leave, which is the bottom of the run and takes the other branch —
     * so a row carrying the value the inner line is drawn at turns back above it.
     */
    private static final String MODEL = """
            module example.unspoken

            data N = Int
                invariant value >= 0 && value <= 100

            data Yes
            data No

            behavior f : (x: N, y: N, n: N) -> Yes | No

            let f (x, y, n) =
                if x > 0 || y > 0
                then (if n > 5 then Yes else No)
                else No
            """;

    /** Where the line behind the disjunction is drawn, which is the one this is about. */
    private static final Count BEHIND_THE_DISJUNCTION = Count.of(5);

    /**
     * Every point of that line is owed a row and offered none, and the reason names the walk.
     *
     * <p>The point stays owed — it is one of the things this run was asked for a row at — and what
     * is missing is a row to offer, which is the honest answer where nothing composed one that
     * reaches. Offered anyway, the row would be a piece of work handed to somebody that does not do
     * what it says.
     */
    @Test
    void noRowIsOfferedWhereTheOneComposedWasNotSeenStandingThere() {
        List<ItemAssessment> owed = pointsOfTheInnerLine();
        assertFalse(owed.isEmpty(), "the line behind the disjunction is owed rows");
        for (ItemAssessment item : owed) {
            ItemAssessment.Attempt.Unresolved no = assertInstanceOf(
                    ItemAssessment.Attempt.Unresolved.class,
                    ((ItemAssessment.Owed) item).searches().only(),
                    "nothing composed a row that reaches this point, so none is offered");
            assertEquals(Generator.UnresolvedCombination.Reason.NO_CERTIFIED_WITNESS,
                    no.why().reason(),
                    "and the reason is what the walk that reads a row said, not what the search"
                            + " managed to compose");
        }
    }

    /** And the way to it is on the account, saying nothing — which is what made the composer guess. */
    @Test
    void theWayToItIsDeclinedRatherThanLeftOff() {
        for (ItemAssessment item : pointsOfTheInnerLine()) {
            ItemAssessment.Attempt.Unresolved no = assertInstanceOf(
                    ItemAssessment.Attempt.Unresolved.class,
                    ((ItemAssessment.Owed) item).searches().only());
            assertFalse(no.way().declined().isEmpty(),
                    "a disjunction states one of two things and this reading says so: "
                            + no.way().onTheWay());
            assertTrue(no.way().takenIn().isEmpty(),
                    "and it narrowed nothing: " + no.way().onTheWay());
        }
    }

    /** And every row that is offered settles what it was composed for, which is what the first half
     *  costing rows buys. */
    @Test
    void everyRowThatIsOfferedSettlesWhatItWasComposedFor() {
        Settlements table = settlements();
        assertFalse(table.composedFor().isEmpty(), "rows are composed for this model");
        table.composedFor().forEach((item, row) -> {
            Map<ObligationIdentity, Settlement> here = table.byRow().get(row);
            assertNotNull(here, "the row composed for " + item + " is one this offers: " + row);
            assertFalse(here.get(item) instanceof Settlement.DoesNotSettle,
                    "a row composed for " + item + " is not read as standing elsewhere: " + row);
        });
    }

    /** What was searched for at each point of the line behind the disjunction. */
    private static List<ItemAssessment> pointsOfTheInnerLine() {
        Compilation compilation = analysed();
        List<BorderAssessment> edges = compilation.db()
                .ask(new Adequacy.BoundarySearch("example.unspoken", "f")).value();
        assertNotNull(edges, "the lines of this behavior were searched");
        List<ItemAssessment> out = new java.util.ArrayList<>();
        for (BorderAssessment each : edges) {
            if (each.border().origin().comparisonAt().isEmpty()
                    || !isAt(each.border().obligation().at(), BEHIND_THE_DISJUNCTION)) {
                continue;
            }
            for (PointRole role : PointRole.values()) {
                if (each.at(role) instanceof ItemAssessment.Owed) {
                    out.add(each.at(role));
                }
            }
        }
        return out;
    }

    /** Whether a line was drawn at {@code number} of whatever it is a line of. */
    private static boolean isAt(souther.compiler.partition.Level level, Count number) {
        return level instanceof souther.compiler.partition.Level.OnACarrier(var ignored, var at)
                && at instanceof Count count && count.compareTo(number) == 0;
    }

    private static Settlements settlements() {
        Compilation compilation = analysed();
        Map<String, Adequacy.Filling> generated =
                Adequacy.generatedOf(compilation.db(), "example.unspoken");
        assertNotNull(generated, "the model under test compiles");
        return Settlements.of(compilation.db(), Composition.composed(
                OfferingRequest.overTheModule("example.unspoken", true), generated,
                Adequacy.accountFor(compilation.db(), "example.unspoken",
                        new GenerationScope.Module()),
                Composition.WhatStandsIn.REQUIRING_NOTHING));
    }

    private static Compilation analysed() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }
}
