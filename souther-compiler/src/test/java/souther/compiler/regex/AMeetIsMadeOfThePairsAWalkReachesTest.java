package souther.compiler.regex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A meet holds the pairs of states a walk gets to, and is charged for those.
 *
 * <p>Two things are being said and they fail apart. The pairs the two sizes multiply to are what a
 * meet could hold, and a pair nothing leads to is a state made, stepped out of and paid for that no
 * string is ever in — so what is built is the walk's own. And the walk is over both kinds of step:
 * a pair one side leaves for nothing is reached by no symbol at all, and a growth that followed
 * only the labelled steps would come to a smaller machine accepting less.
 */
class AMeetIsMadeOfThePairsAWalkReachesTest {

    private static Automaton of(String regex, Meter meter) {
        PatternSyntax syntax =
                assertInstanceOf(PatternRead.Read.class, PatternParser.read(regex), regex).syntax();
        Automaton made = Automaton.of(syntax, meter);
        assertNotNull(made, regex);
        return made;
    }

    private static Meter roomy() {
        return new Meter(100_000, 10_000_000);
    }

    /**
     * Two machines whose labels never agree.
     *
     * <p>Every pair past the one a walk begins at is reached by a symbol both sides step over, and
     * there is none — so the meet is the beginning and nothing else, where the pairs of four states
     * against four are sixteen.
     */
    @Test
    void aPairNothingLeadsToIsNotMade() {
        Meter meter = roomy();
        Automaton left = of("[ab]{2}", meter);
        Automaton right = of("[cd]{2}", meter);
        assertEquals(16, left.size() * right.size(), "the pairs there are");

        Automaton met = left.and(right, meter);

        assertNotNull(met);
        assertEquals(1, met.size(), "and the pairs a walk gets to is the one it begins at");
        assertFalse(met.accepts("ab"), "nothing is accepted, which is what the meet holds");
    }

    /**
     * And a meet whose pairs would not have fit is built where the ones it reaches do.
     *
     * <p>The observable half of the same rule. What the two sizes multiply to is over what a
     * machine may hold and the walk reaches a handful, so a caller told this was not built would be
     * told it about an answer this compiler can afford — and the caller is a model somebody wrote.
     */
    @Test
    void aMeetIsRefusedOnWhatItReachesAndNotOnWhatItCouldReach() {
        // Room for either side and for what the walk reaches, and not for the pairs there are.
        Meter meter = new Meter(600, 2000);
        Automaton left = of("[ab]{30}", meter);
        Automaton right = of("[bc]{30}", meter);
        assertTrue(left.size() * right.size() > 600, "the pairs there are do not fit in a machine");

        Automaton met = left.and(right, meter);

        assertNotNull(met, "and the meet is built, being the pairs a walk reaches");
        assertTrue(met.accepts("b".repeat(30)), "and it holds what both sides hold");
    }

    /** And what a meet spends is the pairs it made, not the pairs it could have made. */
    @Test
    void aMeetIsChargedThePairsItReached() {
        Meter meter = roomy();
        Automaton left = of("[ab]{2}", meter);
        Automaton right = of("[cd]{2}", meter);
        int before = meter.left();

        assertNotNull(left.and(right, meter));

        assertEquals(1, before - meter.left(), "one pair reached is one state charged");
    }

    /**
     * A meet whose sides do agree, where the pairs reached are still fewer than the pairs there
     * are: the two walk in step, so all but the ones around the end are the same state on both
     * sides.
     */
    @Test
    void thePairsReachedAreTheWalksOwn() {
        Meter meter = roomy();
        Automaton left = of("[ab]{2}", meter);
        Automaton right = of("[bc]{2}", meter);

        Automaton met = left.and(right, meter);

        assertNotNull(met);
        assertEquals(6, met.size(), "six of the sixteen pairs are reached");
        assertTrue(met.accepts("bb"), "the one string both sides accept");
        assertFalse(met.accepts("ab"), "which the right side does not");
        assertFalse(met.accepts("bc"), "nor the left");
    }

    /**
     * The walk follows the steps that cost nothing as well.
     *
     * <p>Where the sides repeat different numbers of times, a pair is left by one side moving for
     * nothing while the other stays — so the pairs are not the ones both sides reach at the same
     * point in a string. A growth over the labelled steps alone stops early and the meet accepts
     * less than it holds.
     */
    @Test
    void aPairReachedByASideMovingForNothingIsMadeToo() {
        Meter meter = roomy();
        Automaton left = of("(aa)*", meter);
        Automaton right = of("(aaa)*", meter);

        Automaton met = left.and(right, meter);

        assertNotNull(met);
        assertTrue(met.accepts(""), "no letters at all is a run of both");
        assertTrue(met.accepts("aaaaaa"), "six is two threes and three twos");
        assertFalse(met.accepts("aa"), "which the right side does not accept");
        assertFalse(met.accepts("aaa"), "nor the left");
        assertFalse(met.accepts("aaaa"), "and four is neither");
    }
}
