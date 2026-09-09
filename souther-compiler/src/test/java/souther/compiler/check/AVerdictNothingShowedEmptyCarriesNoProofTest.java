package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.values.Refusal;
import souther.compiler.values.Sameness;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The three words beside a verdict describe a lack, so a verdict short of one carries none of them.
 *
 * <p>What emptied the pair, where the lack is, and whether the readings showed it between them are
 * a proof. Beside a verdict nothing showed empty they describe a refusal no walk reached, and a
 * reader that took one onwards would answer for it — {@link Confinement.Admission#left} refuses the
 * other direction, and this closes the value itself so that the canonical constructor cannot be
 * used to write what the maker refuses.
 *
 * <p><b>Both unsettled verdicts and each word on its own.</b> The rule is about a verdict that is
 * not the settled answer that nothing is admitted, which is two of the three answers; written as a
 * comparison against one of them it would hold of that one and leave the other free, and the answer
 * added to the three would arrive free as well. So each word is refused under each of the two.
 */
class AVerdictNothingShowedEmptyCarriesNoProofTest {

    /** A place, which is what a verdict nothing showed empty may not name. */
    private static final Refusal<String> SOMEWHERE =
            Refusal.atEachOf(Set.of(Sameness.Block.of("here")));

    private static final Refusal<String> NOWHERE = Refusal.nowhere();

    /** The two answers this rule is about: neither of them is a lack anything showed. */
    private static final Set<souther.compiler.values.Emptiness> NOTHING_SHOWED_THESE_EMPTY =
            Set.of(souther.compiler.values.Emptiness.NONEMPTY,
                    souther.compiler.values.Emptiness.UNDECIDED);

    @Test
    void neitherOfThemMayNameWhatEmptiedThePair() {
        NOTHING_SHOWED_THESE_EMPTY.forEach(verdict -> assertThrows(
                IllegalArgumentException.class,
                () -> new Confinement.Admission<>(verdict, Confinement.EmptyBy.ORDER, NOWHERE,
                        Confinement.Shown.BY_THE_READINGS),
                verdict + " was not emptied by the orders, or by anything else"));
    }

    @Test
    void neitherOfThemMayNameWhereTheLackIs() {
        NOTHING_SHOWED_THESE_EMPTY.forEach(verdict -> assertThrows(
                IllegalArgumentException.class,
                () -> new Confinement.Admission<>(verdict, Confinement.EmptyBy.NOTHING_SHOWN,
                        SOMEWHERE, Confinement.Shown.BY_THE_READINGS),
                verdict + " leaves no position refused, so there is nowhere for it to be"));
    }

    @Test
    void andNeitherMaySayWhatShowedIt() {
        NOTHING_SHOWED_THESE_EMPTY.forEach(verdict -> assertThrows(
                IllegalArgumentException.class,
                () -> new Confinement.Admission<>(verdict, Confinement.EmptyBy.NOTHING_SHOWN,
                        NOWHERE, Confinement.Shown.ONCE_THE_POSITIONS_ARE_PLACED),
                verdict + " is not something the placed positions showed"));
    }

    /**
     * And the settled answer that nothing is admitted may still name no place.
     *
     * <p>The direction this does not close, kept as a value that has to go on being writable: a
     * walk shown a lack about no block in particular has nothing to name, and a rule reading the
     * implication backwards would refuse the answer such a walk reaches.
     */
    @Test
    void whileALackShownAtNoPlaceIsStillALack() {
        assertDoesNotThrow(() -> new Confinement.Admission<>(
                souther.compiler.values.Emptiness.EMPTY, Confinement.EmptyBy.NOTHING_SHOWN,
                NOWHERE, Confinement.Shown.BY_THE_READINGS));
    }
}
