package souther.compiler.numeric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a path was told, read back as the rules it holds.
 *
 * <p>The rules are composed as they are said and made distinct where they are read, so what every
 * reader below sees is decided here and nowhere else. Two things follow from that and neither is
 * visible from a domain that was told a handful of rules: that a rule said again is read once and
 * in the place it was first said, which is what makes an answer a function of which rules were said
 * rather than of how often or in what order a caller happened to say them; and that reading them
 * back is not a limit on how many there may be.
 *
 * <p>The second is why the depth and the sharing are both here. A path says one rule at a time, so
 * the composition a long path leaves is as deep as the path is long — read by calling down it, a
 * path stating enough rules would be one the reader could not answer about at all. And nothing is
 * copied when two of them are said together, so what a caller says twice is reached twice and not
 * held twice — read by what it reaches, a caller doubling what it says doubles what reading it
 * costs. Neither failure is one any of these rules is about.
 */
class RulesAreReadOutOnceEachHoweverDeeplyTheyWereSaidTest {

    /** A path saying enough rules for a reader that called down what they compose to not answer. */
    private static final int LONGER_THAN_A_STACK = 100_000;

    /**
     * Doublings enough that a reader paying per reach rather than per part takes far longer than
     * this test is given, and few enough that it still stops and says so.
     */
    private static final int MORE_REACHES_THAN_THERE_ARE_PARTS = 27;

    private static AffineConstraint<String> rule(String atom, long at) {
        return new AffineConstraint.Disequality<>(
                new CanonicalForm<>(Map.of(atom, Rational.of(1))), Rational.of(at));
    }

    private static StatedRules<String> said(List<AffineConstraint<String>> these) {
        StatedRules<String> out = StatedRules.none();
        for (AffineConstraint<String> each : these) {
            out = out.and(StatedRules.of(each));
        }
        return out;
    }

    /** A rule said again is the same rule, and it is read where it was first said. */
    @Test
    void aRuleSaidAgainIsReadOnceAndWhereItWasFirstSaid() {
        AffineConstraint<String> a = rule("x", 1);
        AffineConstraint<String> b = rule("y", 2);
        AffineConstraint<String> c = rule("z", 3);

        assertEquals(List.of(a, b, c), said(List.of(a, b, a, c, b)).distinct());
    }

    /**
     * Two paths' rules said together are read as the first path's and then what the second adds.
     *
     * <p>Which is what a caller holding the readings of two values at once is owed: what the two of
     * them say does not depend on which of them the caller came by first having said something the
     * other says too.
     */
    @Test
    void twoPathsRulesAreReadAsTheFirstsAndThenWhatTheSecondAdds() {
        AffineConstraint<String> a = rule("x", 1);
        AffineConstraint<String> b = rule("y", 2);
        AffineConstraint<String> c = rule("z", 3);

        assertEquals(List.of(a, b, c), said(List.of(a, b)).and(said(List.of(b, c))).distinct());
    }

    /** Nothing said, which is what a path told nothing holds. */
    @Test
    void aPathToldNothingReadsBackNothing() {
        assertEquals(List.of(), StatedRules.<String>none().distinct());
    }

    /**
     * A path longer than a reader could call down is read back whole.
     *
     * <p>The rule said last is reached, and so is the one said first — which is under everything
     * else that was said, and is the one a reader that stopped anywhere would not have.
     */
    @Test
    void aPathLongerThanAStackIsReadBackWhole() {
        AffineConstraint<String> first = rule("x", 1);
        AffineConstraint<String> last = rule("y", 2);

        StatedRules<String> deep = StatedRules.of(first);
        for (int said = 0; said < LONGER_THAN_A_STACK; said++) {
            deep = deep.and(StatedRules.of(first));
        }

        assertEquals(List.of(first, last), deep.and(StatedRules.of(last)).distinct());
    }

    /**
     * What was said once and reached many times is read once.
     *
     * <p>Nothing is copied when two of these are said together, so two paths carrying on from one
     * hold the one they carried on from rather than a copy of it, and saying those two together
     * reaches it twice. Read by what it reaches, a caller doubling what it says would be read a
     * number of times that doubles with it — which is the cost the rest of this is about, arrived
     * at from the other end.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void whatWasSaidOnceIsReadOnceHoweverManyTimesItIsReached() {
        AffineConstraint<String> a = rule("x", 1);

        StatedRules<String> shared = StatedRules.of(a);
        for (int doubled = 0; doubled < MORE_REACHES_THAN_THERE_ARE_PARTS; doubled++) {
            shared = shared.and(shared);
        }

        assertEquals(List.of(a), shared.distinct());
    }
}
