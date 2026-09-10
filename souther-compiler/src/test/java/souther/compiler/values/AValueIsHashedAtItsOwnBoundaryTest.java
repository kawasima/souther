package souther.compiler.values;

import org.junit.jupiter.api.Test;
import souther.compiler.hash.ValueHash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * What a value's parts come to is settled by the value that owns them, and what is handed up is not
 * a function anything above can take apart again.
 *
 * <p>Whatever hashes a value is nearly always something that adds hashes: a set sums what it holds,
 * a map sums its entries, a record carries its last component into its own number unchanged. So a
 * number gathered from parts and handed up as gathered is one whose parts are still separable
 * there, and the sum above cancels whatever the value was arranged to say about which part went
 * with which. These are about that cancelling, said of the algebra rather than of any one value
 * that uses it.
 *
 * <p>None of them asks that two different values never come to one number, which nothing can
 * promise of an {@code int}. What each asks is that a way of losing the value's structure is not
 * arithmetic — that two of these added are not, as an identity over every input, the same as two
 * others added.
 */
class AValueIsHashedAtItsOwnBoundaryTest {

    private static final Class<?> A_KIND = Apartness.Edge.class;

    private static final Class<?> ANOTHER_KIND = Shown.class;

    /**
     * Four things paired two ways, each pair hashed and the two pairs added.
     *
     * <p>Gathered and handed up, these two are one number: both pairings come to the same sum of
     * sums and the same sum of exclusive-ors, so neither of the two numbers a pair is gathered
     * from tells them apart once the sum above has been taken. What tells them apart is the
     * finishing, and it has to be at each pair's own boundary — which is what this holds, and it
     * holds it for the gathering as well, since four things that separated under the
     * exclusive-ors alone would let the finishing go and stay green.
     */
    @Test
    void twoUnorderedPairsAddedDoNotForgetWhichEndWentWithWhich() {
        int a = 0;
        int b = 1;
        int c = 2;
        int d = 4;

        int paired = ValueHash.ofAnUnorderedPair(A_KIND, a, b)
                + ValueHash.ofAnUnorderedPair(A_KIND, c, d);
        int otherwise = ValueHash.ofAnUnorderedPair(A_KIND, a, c)
                + ValueHash.ofAnUnorderedPair(A_KIND, b, d);

        assertNotEquals(paired, otherwise,
                "the same four ends paired two ways came to one number");
    }

    /** And the same of two things held in places, which a record's own number would lose in the
     *  same way: the second part joins it unchanged, so exchanging the seconds is arithmetic. */
    @Test
    void andTwoValuesOfTwoPartsAddedDoNotForgetWhichPartWentWithWhich() {
        int first = 11;
        int second = 22;
        int another = 33;
        int itsSecond = 44;

        int held = ValueHash.ofItsParts(ANOTHER_KIND, first, second)
                + ValueHash.ofItsParts(ANOTHER_KIND, another, itsSecond);
        int exchanged = ValueHash.ofItsParts(ANOTHER_KIND, first, itsSecond)
                + ValueHash.ofItsParts(ANOTHER_KIND, another, second);

        assertNotEquals(held, exchanged,
                "the same parts shared out two ways came to one number");
    }

    /** A pair with no order between its ends is one pair written either way round. */
    @Test
    void andAnUnorderedPairIsOneNumberWhicheverEndWasWrittenFirst() {
        assertEquals(ValueHash.ofAnUnorderedPair(A_KIND, 11, 22),
                ValueHash.ofAnUnorderedPair(A_KIND, 22, 11));
    }

    /**
     * Two pairs whose ends add alike.
     *
     * <p>Which is a different question from the one above, and it is why an unordered pair is
     * gathered from two numbers rather than from the sum alone. The sum is what the ends have that
     * does not depend on their order; it is not all they have. Pairs adding alike is what a
     * relation drawn from few blocks has, and telling those apart is what the second number is for
     * — dropping it leaves the cancelling above still closed and these two still one number.
     */
    @Test
    void andTwoPairsWhoseEndsAddAlikeAreNotOnePair() {
        assertNotEquals(ValueHash.ofAnUnorderedPair(A_KIND, 0, 4),
                ValueHash.ofAnUnorderedPair(A_KIND, 1, 3),
                "two pairs were told apart by nothing but what their ends add to");
    }

    /** Two things in places are in their places: exchanging them is another value. */
    @Test
    void andPartsHeldInPlacesAreNotAPair() {
        assertNotEquals(ValueHash.ofItsParts(ANOTHER_KIND, 11, 22),
                ValueHash.ofItsParts(ANOTHER_KIND, 22, 11));
    }

    /** Two kinds holding alike are two values, which is what a sum type's cases need of this. */
    @Test
    void andTwoKindsHoldingAlikeAreTwoValues() {
        assertNotEquals(ValueHash.ofOnePart(A_KIND, 11), ValueHash.ofOnePart(ANOTHER_KIND, 11));
    }

    /** And what a collection holds is how much of it there is as well as what it sums to. */
    @Test
    void andWhatACollectionHoldsIsHowManyOfThemThereAreAsWell() {
        assertNotEquals(ValueHash.ofWhatItHolds(A_KIND, 100, 2),
                ValueHash.ofWhatItHolds(A_KIND, 100, 3));
    }
}
