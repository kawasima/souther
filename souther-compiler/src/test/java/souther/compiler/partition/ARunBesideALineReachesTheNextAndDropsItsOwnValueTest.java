package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Carrier;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Towards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run beside a line reaches the next line and holds the value there; what it drops is the value of
 * the line it is named for.
 *
 * <p>Which is what {@link Criterion.Within} is: a band, less the one level this point is against
 * ({@code band.region().without(except)}). So of two lines at two and at five, the point away from
 * the first is `2 < n <= 5` and five is in it, and the point away from the second is `2 <= n < 5`
 * and two is in it. There is no rule taking the far value out, and nothing that searches for a row
 * may act as though there were — {@link Criterion#region} is the whole of what an item means and
 * {@code anchor} says where to start looking and never what satisfies the item.
 *
 * <p><b>One row answering two items is not two readings of one item.</b> A row at five stands at the
 * point on the line at five and inside the run above two, and both are owed: what tells two items
 * apart is what each demands, which is why a level and a run one value wide are separate demands that
 * the same rows happen to hold ({@link Criterion#sameAs}). Read the other way — the far value taken
 * out so that each row answers one item — a band would mean one thing to the arrangement that builds
 * it and another to the search that fills it, which is the pair of geometries this type exists to
 * keep as one (issue #880).
 *
 * <p>Written against the criteria rather than against a report, because this is the geometry. What a
 * search manages to compose in one of these runs moves as the search learns to look further, and this
 * says what the runs are whichever way that goes.
 */
class ARunBesideALineReachesTheNextAndDropsItsOwnValueTest {

    private static final Carrier WHOLE = new Carrier.Whole();

    private static Level at(long number) {
        return new Level.OnACarrier(WHOLE, Count.of(number));
    }

    /**
     * The run one of the lines leaves, in the shape the report arrives with.
     *
     * <p>The band is what the rules leave the position — both ends the declaration's own, held — and
     * the point away from a line is that band less the value the line stands at. Nothing parts the
     * band here, which is what {@code invariant n == 2 || n == 5} comes to: the two named values are
     * the ends of what the position holds, and each is a line a row is owed at.
     */
    private static Criterion.Within besideTheLineAt(long named, Towards away) {
        Band band = new Band(Band.endAt(null, Bound.at(at(2), true), Towards.ABOVE),
                Band.endAt(null, Bound.at(at(5), true), Towards.BELOW));
        return new Criterion.Within(band, at(named), away);
    }

    @Test
    void theRunAboveALineHoldsTheValueAtTheNextLine() {
        Criterion aboveTwo = besideTheLineAt(2, Towards.ABOVE);

        assertTrue(aboveTwo.holds(at(5)),
                "five is where this run stops and it stops there holding it: the rules part the"
                        + " values at five and nothing takes five out of the run below it");
        assertFalse(aboveTwo.holds(at(2)),
                "and two is the value this point is named against, which is the one it drops");
    }

    /** And the run below the other line, which is the same sentence the other way round. */
    @Test
    void theRunBelowALineHoldsTheValueAtTheLineBeforeIt() {
        Criterion belowFive = besideTheLineAt(5, Towards.BELOW);

        assertTrue(belowFive.holds(at(2)), "two is where this run starts and it starts there"
                + " holding it");
        assertFalse(belowFive.holds(at(5)), "and five is the value it drops");
    }

    /**
     * What each run is called, which is the same reading the report writes.
     *
     * <p>Held beside the membership above so that the label and the set cannot come apart: a run
     * spelled as reaching the next line while holding nothing there would tell an author to write a
     * row at a value the item refuses.
     */
    @Test
    void theLabelSaysWhichEndEachRunKeeps() {
        assertTrue(besideTheLineAt(2, Towards.ABOVE).region()
                        .contains(at(5)),
                "the run named for two keeps five");
        assertTrue(besideTheLineAt(5, Towards.BELOW).region()
                        .contains(at(2)),
                "and the run named for five keeps two");
    }
}
