package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What two readings were left holds an answer where holding it twice does.
 *
 * <p>{@link LeftUnbuilt} is put together where readings are, and spent where a verdict is
 * published. Those are two places, and a caller reaching a verdict has met the readings in whatever
 * order the composition happened to take — so what the composition comes to has to hold an answer
 * the way the readings would have held it one at a time, or which answer comes back would follow
 * how the caller bracketed them.
 *
 * <p>Which is the whole of what {@link LeftUnbuilt#met} claims by its name. Written as the word for
 * holding two things together while meaning something else, it would be a walk's own arithmetic
 * that agrees with the word today.
 */
class WhatAReadingLeftUnbuiltHoldsAnAnswerTheSameHoweverItWasComposedTest {

    private static final List<LeftUnbuilt> BOTH =
            List.of(LeftUnbuilt.NOTHING, LeftUnbuilt.A_POSITION);

    private static final List<Emptiness> EVERY_ANSWER =
            List.of(Emptiness.EMPTY, Emptiness.NONEMPTY, Emptiness.UNDECIDED);

    /** Holding an answer under two of these is holding it under what the two come to. */
    @Test
    void holdingTwiceIsHoldingUnderWhatTheTwoCameTo() {
        for (LeftUnbuilt one : BOTH) {
            for (LeftUnbuilt other : BOTH) {
                for (Emptiness answer : EVERY_ANSWER) {
                    assertEquals(one.hold(other.hold(answer)), one.met(other).hold(answer),
                            one + " and " + other + " holding " + answer);
                }
            }
        }
    }

    /** A reading that left nothing unbuilt takes nothing off what it is put beside. */
    @Test
    void aReadingThatLeftNothingIsWhatTheOtherIs() {
        for (LeftUnbuilt each : BOTH) {
            assertSame(each, LeftUnbuilt.NOTHING.met(each));
            assertSame(each, each.met(LeftUnbuilt.NOTHING));
        }
    }

    /** And the same reading met however often, and in whichever order, is the same. */
    @Test
    void neitherTheOrderNorHowOftenReachesTheAnswer() {
        for (LeftUnbuilt one : BOTH) {
            assertSame(one, one.met(one));
            for (LeftUnbuilt other : BOTH) {
                assertSame(one.met(other), other.met(one));
                for (LeftUnbuilt third : BOTH) {
                    assertSame(one.met(other).met(third), one.met(other.met(third)));
                }
            }
        }
    }

    /**
     * And what each of them does to a verdict.
     *
     * <p>The one thing a reader spends this on. A reading short of a position leaves a settled
     * positive answer unsettled and leaves the other two where they were, which is what meeting
     * against the answer nobody has worked out comes to — said here as the six cases, so that the
     * rule is fixed rather than following whatever the embedding is changed to.
     */
    @Test
    void andWhatEachOfThemLeavesAVerdict() {
        for (Emptiness answer : EVERY_ANSWER) {
            assertSame(answer, LeftUnbuilt.NOTHING.hold(answer),
                    "a reading that built everything answers for itself");
        }
        assertSame(Emptiness.EMPTY, LeftUnbuilt.A_POSITION.hold(Emptiness.EMPTY),
                "a narrower reading refuses no less, so settled empty is settled all the same");
        assertSame(Emptiness.UNDECIDED, LeftUnbuilt.A_POSITION.hold(Emptiness.NONEMPTY),
                "and a positive answer was about less than the rules say");
        assertSame(Emptiness.UNDECIDED, LeftUnbuilt.A_POSITION.hold(Emptiness.UNDECIDED));
    }
}
