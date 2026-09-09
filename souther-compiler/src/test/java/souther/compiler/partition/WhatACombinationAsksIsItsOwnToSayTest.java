package souther.compiler.partition;

import souther.compiler.coverage.Numberings;

import org.junit.jupiter.api.Test;

import souther.compiler.coverage.ControlClaim;
import souther.compiler.coverage.ControlPointId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How many things a combination can be asking, and what each of them says.
 *
 * <p>A combination is a class apiece at the positions it is about. Where it leaves one of them more
 * than one class it is asking more than one thing, and a search bounded at three tries should get
 * three of those.
 *
 * <p>What it used to get was three assignments. An assignment fixes every position — the ones the
 * combination is about and the ones it says nothing about alike — and the ones it says nothing about
 * are the search's own choice. Counted off together, the first three assignments of a combination
 * over one free position of four classes were one thing asked four ways, and the second thing the
 * combination could have been asking went untried however much of the bound was left.
 *
 * <p>Built here rather than read off a model. A combination that leaves two positions open beside a
 * free one is a shape a model in the corpus need not have, and a mechanism is untested for shapes
 * its data never takes.
 */
class WhatACombinationAsksIsItsOwnToSayTest {

    /** Four classes at each position, with the ones listed left open. */
    private static boolean[] leaving(int... classes) {
        boolean[] allowed = new boolean[4];
        for (int each : classes) {
            allowed[each] = true;
        }
        return allowed;
    }

    private static CellSelection over(boolean[]... positions) {
        return new CellSelection(new InteractionCells.Cell(positions),
                List.of(ControlClaim.of(new ControlPointId.ArmPoint(Numberings.armOfForkAt(1),
                                Optional.of(Numberings.arm(2, 1)), null))
                        .orElseThrow(() -> new AssertionError("an arm with a probe can be claimed"))));
    }

    /**
     * A walk that stops at the first reading is stopped, and one that is let run to the end is
     * exhausted.
     *
     * <p>Which is the whole of what a caller reads to say whether it looked everywhere. The
     * combination says how many readings there are by running out of them, and the caller says how
     * many it will look at by refusing one — so the answer is about a reading that exists and was
     * not looked at, rather than about a number a counter reached.
     */
    @Test
    void aWalkSaysWhetherAnyReadingWasLeft() {
        CellSelection selection = over(leaving(0, 1, 2, 3), leaving(0, 2), leaving(1));

        assertEquals(Traversal.EXHAUSTED,
                selection.interpretations(_ -> Taking.Taken.AND_MORE));
        assertEquals(Traversal.STOPPED,
                selection.interpretations(_ -> Taking.Taken.NOT_TAKEN));
        assertEquals(Traversal.SATISFIED,
                selection.interpretations(_ -> Taking.Taken.AND_DONE),
                "a consumer that has what it came for left nothing undone");
    }

    /** Every reading the combination has, for a test asking what they are rather than how many of
     *  them something looked at. */
    private static List<Interpretation> everythingAsked(CellSelection selection) {
        List<Interpretation> out = new java.util.ArrayList<>();
        assertEquals(Traversal.EXHAUSTED, selection.interpretations(reading -> {
                    out.add(reading);
                    return Taking.Taken.AND_MORE;
                }),
                "nothing here refuses a reading, so the walk runs out");
        return List.copyOf(out);
    }

    /**
     * A reading says where the positions the combination is about stand, and says nothing anywhere
     * else.
     */
    @Test
    void aReadingIsAboutThePositionsTheCombinationNarrows() {
        CellSelection selection =
                over(leaving(0, 1, 2, 3), leaving(0, 2), leaving(1));

        assertEquals(List.of(Map.of(1, 0, 2, 1), Map.of(1, 2, 2, 1)),
                everythingAsked(selection).stream().map(Interpretation::pins).toList(),
                "the free position is no part of what the combination asks");
    }

    /**
     * A position the combination says nothing about does not multiply what it is asking.
     *
     * <p>Which is the whole of what the bound is for. The two things this combination can be asking
     * are both within three tries; counted over assignments, the first three were one of them at
     * three of the free position's four classes.
     */
    @Test
    void aFreePositionDoesNotMultiplyTheReadings() {
        CellSelection selection =
                over(leaving(0, 1, 2, 3), leaving(0, 2), leaving(1));

        assertEquals(2, everythingAsked(selection).size(),
                "two things asked, whatever the free position may hold");
        assertTrue(everythingAsked(selection).stream()
                        .allMatch(reading -> reading.at().equals(java.util.Set.of(1, 2))),
                "and both of them are about the same two positions");
    }

    /**
     * A combination of more positions than a number can count is still walked one reading at a time.
     *
     * <p>Sixty-three positions of two classes apiece is more readings than fit in a machine word.
     * Nobody needs that number: a search takes three readings and stops. Asked for it all the same,
     * the count came back negative — so the walk handed nothing over and said it had run out, over a
     * combination with more readings than anything will ever look at.
     */
    @Test
    void aCombinationIsWalkedWithoutCountingHowManyReadingsItHas() {
        boolean[][] wide = new boolean[63][];
        for (int i = 0; i < wide.length; i++) {
            wide[i] = leaving(0, 1);   // narrowed, and two of the four left open
        }
        int[] handed = {0};

        Traversal walked = over(wide).interpretations(_ -> {
            handed[0]++;
            return Taking.Taken.NOT_TAKEN;
        });

        assertEquals(1, handed[0], "the first reading was handed over");
        assertEquals(Traversal.STOPPED, walked, "and the rest of them are work nobody did");
    }

    /**
     * A position with nothing left at it is no combination, and is not a reading that failed.
     *
     * <p>Told apart because they are different news. A combination the model does not have is not
     * one a search came back empty from, and the second sends a person looking for values to write.
     */
    @Test
    void aPositionWithNothingLeftAtItIsNoCombination() {
        assertEquals(List.of(), everythingAsked(over(leaving(0, 1, 2, 3), leaving())),
                "nothing stands at the second position, so there is nothing to look for");
    }
}
