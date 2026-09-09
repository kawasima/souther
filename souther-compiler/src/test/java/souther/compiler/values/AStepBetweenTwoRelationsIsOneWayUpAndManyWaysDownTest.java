package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Carrying a block from one relation to another, which is two operations and not one.
 *
 * <p>A block of the finer relation is inside one block of the coarser, so what was said about it is
 * said about that one — an answer any of its positions gives. A block of the coarser relation is
 * made of several of the finer, so what the finer says about it is what it says about all of them —
 * an answer no one position gives. So the two are asked for by name, and the one that has no block
 * to give back cannot be spelled as though it had.
 *
 * <p>The warrant is asked for where the step is made and not where it is taken, so a relation that
 * is not a coarsening is refused once rather than answered wrongly at every block. What each end
 * may be asked about is its own relation's blocks, which is asked for at the ask.
 */
class AStepBetweenTwoRelationsIsOneWayUpAndManyWaysDownTest {

    /** {@code p == q} met with {@code q == r}, which is the conjunction's relation. */
    private static final Sameness<String> COARSER =
            Sameness.of("p", "q").meet(Sameness.of("q", "r"));

    /** One side of it, which states the first equality and not the second. */
    private static final Sameness<String> FINER = Sameness.of("p", "q");

    /** Every block of the finer relation lands on one block, which is what making this proves. */
    @Test
    void aBlockOfTheFinerRelationIsInsideOneBlockOfTheCoarser() {
        Refinement<String> up = Refinement.of(FINER, COARSER);

        assertEquals(COARSER.blockOf("p"), up.coarseBlockOf(FINER.blockOf("p")));
        assertEquals(Set.of("p", "q", "r"), up.coarseBlockOf(FINER.blockOf("p")).members());
    }

    /** And a position the finer relation holds with nothing is inside one block just the same. */
    @Test
    void andSoIsAPositionHeldWithNothing() {
        Refinement<String> up = Refinement.of(FINER, COARSER);

        assertEquals(COARSER.blockOf("r"), up.coarseBlockOf(Sameness.Block.of("r")));
    }

    /**
     * A block of the coarser relation is several of the finer, and all of them are the answer.
     *
     * <p>Which is the case the pick was wrong about: {@code p} and {@code q} are one block of the
     * finer relation and {@code r} is another, so a reader handed one position would be told about
     * whichever of the two that position is in.
     */
    @Test
    void aBlockOfTheCoarserRelationIsSeveralOfTheFiner() {
        Refinement<String> up = Refinement.of(FINER, COARSER);

        assertEquals(Set.of(FINER.blockOf("p"), Sameness.Block.of("r")),
                up.fineBlocksWithin(COARSER.blockOf("p")));
    }

    /**
     * A relation that holds a block's positions apart is refused, and refused where it is read
     * rather than at the first ask.
     *
     * <p>Which is the other way round: the conjunction's relation read against one side's. What is
     * wanted there is every block of the side the coarse block covers, and that is
     * {@link Refinement#fineBlocksWithin} of a step made the way round it exists.
     */
    @Test
    void aRelationThatIsNotACoarseningIsRefusedWhereItIsRead() {
        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> Refinement.of(COARSER, FINER));

        assertEquals("positions held as one at [p, q, r] are held apart by the relation they are"
                + " read against, which holds [p=[p, q], q=[p, q], r=r]", refused.getMessage());
    }

    /**
     * A set of positions the finer relation does not hold together has no block up there.
     *
     * <p>Which is the ask that could still be answered from one position. A block is a set and
     * anybody may build one, so the step being total over the blocks that were walked is not the
     * step being total — and the positions of a block nothing made are in whatever blocks they are
     * in.
     */
    @Test
    void aSetOfPositionsTheFinerRelationDoesNotHoldTogetherIsNotItsToCarry() {
        Refinement<String> up = Refinement.of(FINER, COARSER);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> up.coarseBlockOf(Sameness.Block.of(Set.of("p", "s"))));

        assertEquals("the relation these positions are read from does not hold them as one value:"
                + " [p, s], which it holds as [[p, q], s]", refused.getMessage());
    }

    /** And the other end asks the same of the relation it reads against. */
    @Test
    void norIsOneTheCoarserRelationDoesNotHoldTogether() {
        Refinement<String> up = Refinement.of(FINER, COARSER);

        assertThrows(IllegalArgumentException.class,
                () -> up.fineBlocksWithin(Sameness.Block.of(Set.of("p", "s"))));
    }

    /** And a discrete relation is finer than everything, which is where a denial stated of single
     *  positions is carried from. */
    @Test
    void aDiscreteRelationIsFinerThanEveryOther() {
        Refinement<String> up = Refinement.of(Sameness.discrete(), COARSER);

        assertEquals(COARSER.blockOf("q"), up.coarseBlockOf(Sameness.Block.of("q")));
    }
}
