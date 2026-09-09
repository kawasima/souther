package souther.compiler.values;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What a walk over many answers is entitled to assume about the operation it walks under.
 *
 * <p>The readings that ask what a whole reading admits fold {@link Emptiness#met} over the blocks of
 * an alternative and {@link Emptiness#joined} over the alternatives. A fold is an answer read off
 * one order of one bracketing, offered as an answer about a set: the alternatives of a reading are
 * a set, and the blocks of one are named by rules whose order is the author's. So what is held here
 * is that the operations cannot tell those apart, and that where a fold stops early it stops
 * where the operation says rather than where one of the answers is.
 *
 * <p><b>Asked of {@link Emptiness#values()}, so that an answer added to the three is asked too.</b>
 * The operations are written as a switch over every pair, so such an answer cannot compile until
 * somebody has said what each pairing comes to; what nothing would otherwise ask is whether what
 * they then say is still something a walk may fold, and whether the two words a walk stops on
 * still name the answers that stop it.
 *
 * <p>Nothing here holds the three answers to a particular table. The laws below settle every
 * pairing of these three between them, so a table written out beside them would be the same claim
 * twice — and the second copy would be the one somebody edited to match a change. What a further
 * answer means is a claim about that answer, and belongs where it is introduced.
 *
 * <p>Distribution is not asked for. A fold reaches one answer over one collection, and no reading
 * here takes an operation through the other.
 */
class TheseAnswersMayBeFoldedInAnyOrderAndSayWhereAFoldStopsTest {

    @Test
    void aFoldOverNothingStandsWhereTheOperationSaysItStarts() {
        for (Emptiness one : Emptiness.values()) {
            assertSame(one, Emptiness.identityForMeet().met(one),
                    "a conjunct met with what a walk starts from is what the conjunct said");
            assertSame(one, one.met(Emptiness.identityForMeet()));
            assertSame(one, Emptiness.identityForJoin().joined(one),
                    "and an alternative joined onto what a walk starts from is that alternative");
            assertSame(one, one.joined(Emptiness.identityForJoin()));
        }
    }

    @Test
    void neitherOperationCanTellTheOrderOfWhatItWasGiven() {
        for (Emptiness one : Emptiness.values()) {
            for (Emptiness other : Emptiness.values()) {
                assertSame(one.met(other), other.met(one),
                        "the blocks of an alternative are named in the order an author wrote the"
                                + " rules, and what they come to is not about that order");
                assertSame(one.joined(other), other.joined(one));
            }
        }
    }

    @Test
    void norTheBracketingOfIt() {
        for (Emptiness one : Emptiness.values()) {
            for (Emptiness other : Emptiness.values()) {
                for (Emptiness third : Emptiness.values()) {
                    assertSame(one.met(other).met(third), one.met(other.met(third)),
                            "a walk takes them in one at a time, and the answer is about the whole");
                    assertSame(one.joined(other).joined(third), one.joined(other.joined(third)));
                }
            }
        }
    }

    @Test
    void andOneAnswerTwiceIsThatAnswer() {
        for (Emptiness one : Emptiness.values()) {
            assertSame(one, one.met(one), "two rules saying the same thing are one rule");
            assertSame(one, one.joined(one));
        }
    }

    @Test
    void whatIsAskedOfAWholeIsNotChangedByAskingItOfAPartFirst() {
        for (Emptiness one : Emptiness.values()) {
            for (Emptiness other : Emptiness.values()) {
                assertSame(one, one.met(one.joined(other)),
                        "an alternative beside this one leaves this one where it was");
                assertSame(one, one.joined(one.met(other)));
            }
        }
    }

    @Test
    void aFoldStopsExactlyWhereTheOperationCanNoLongerBeMoved() {
        for (Emptiness one : Emptiness.values()) {
            boolean meetIsFinished = true;
            boolean joinIsFinished = true;
            for (Emptiness other : Emptiness.values()) {
                meetIsFinished &= one.met(other) == one;
                joinIsFinished &= one.joined(other) == one;
            }
            assertEquals(meetIsFinished, one.endsAMeet(),
                    "a walk that stopped where a further conjunct could still move it would answer"
                            + " about the conjuncts it had reached, and one that went on where"
                            + " nothing could move it would ask after the answer was fixed");
            assertEquals(joinIsFinished, one.endsAJoin());
        }
    }
}
