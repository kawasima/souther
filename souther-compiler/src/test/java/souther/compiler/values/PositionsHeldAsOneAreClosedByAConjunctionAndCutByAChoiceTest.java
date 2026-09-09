package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which positions a reading holds as one value, and what the connectives do to it.
 *
 * <p>The coordinate every answer of a reading is in, so the two connectives move it in opposite
 * directions and neither may be read as the other. Rules stated together hold as one whatever
 * either of them holds as one, and the closure of that; a choice holds as one only what both of
 * its branches do.
 *
 * <p>What is asserted here is the relation on its own. That the sets, the promises and the proofs
 * follow it is asserted where each of them is.
 */
class PositionsHeldAsOneAreClosedByAConjunctionAndCutByAChoiceTest {

    /** A position nothing was said about is its own, and asking is not a way to hear otherwise. */
    @Test
    void aPositionNothingHoldsWithAnythingIsItsOwn() {
        Sameness<String> nothing = Sameness.discrete();

        assertTrue(nothing.isDiscrete());
        assertEquals(Sameness.Block.of("p"), nothing.blockOf("p"));
        assertTrue(nothing.blockOf("p").isOne());
        assertEquals(List.of(), List.copyOf(nothing.joined()));
    }

    /** An equality says the two are one, which is one block holding both. */
    @Test
    void anEqualityMakesOneBlockOfTwoPositions() {
        Sameness<String> both = Sameness.of("p", "r");

        assertFalse(both.isDiscrete());
        assertEquals(both.blockOf("p"), both.blockOf("r"));
        assertEquals(Set.of("p", "r"), both.blockOf("p").members());
        assertFalse(both.blockOf("p").isOne());
        assertTrue(both.blockOf("p").holds("r"));
    }

    /**
     * Two equalities sharing a position hold all three as one.
     *
     * <p>The closure and not the pair of pairs. A reader handed the two pairs would find no rule
     * saying that {@code p} and {@code r} are one value, and every answer about the three would be
     * two answers.
     */
    @Test
    void twoEqualitiesSharingAPositionHoldAllThreeAsOne() {
        Sameness<String> chain = Sameness.of("p", "q").meet(Sameness.of("q", "r"));

        assertEquals(Set.of("p", "q", "r"), chain.blockOf("p").members());
        assertEquals(chain.blockOf("p"), chain.blockOf("r"));
    }

    /** And the closure does not depend on which of them was stated first. */
    @Test
    void theClosureIsTheSameWhicheverEqualityWasStatedFirst() {
        assertEquals(Sameness.of("p", "q").meet(Sameness.of("q", "r")),
                Sameness.of("q", "r").meet(Sameness.of("p", "q")));
    }

    /** Rules that hold different pairs as one hold both pairs, and nothing across them. */
    @Test
    void twoEqualitiesOverDifferentPositionsAreTwoBlocks() {
        Sameness<String> two = Sameness.of("p", "q").meet(Sameness.of("r", "s"));

        assertEquals(Set.of("p", "q"), two.blockOf("p").members());
        assertEquals(Set.of("r", "s"), two.blockOf("r").members());
        assertEquals(2, two.joined().size());
    }

    /**
     * A choice holds as one what both branches hold as one, and a block is cut down rather than
     * kept or dropped whole.
     */
    @Test
    void aChoiceKeepsWhatBothBranchesHoldAsOne() {
        Sameness<String> three = Sameness.of("p", "q").meet(Sameness.of("q", "r"));
        Sameness<String> two = Sameness.of("p", "q");

        Sameness<String> common = three.common(two);

        assertEquals(Set.of("p", "q"), common.blockOf("p").members());
        assertTrue(common.blockOf("r").isOne(), "nothing in both branches holds r with anything");
    }

    /** A branch may not lend an equality to the branch beside it. */
    @Test
    void aChoiceHoldsNothingAsOneWhereOnlyOneBranchDoes() {
        assertTrue(Sameness.of("p", "r").common(Sameness.discrete()).isDiscrete());
        assertTrue(Sameness.of("p", "r").common(Sameness.of("p", "s")).isDiscrete());
    }

    /** And what a choice leaves does not depend on which branch was written first. */
    @Test
    void whatAChoiceLeavesIsTheSameWhicheverBranchIsRead() {
        Sameness<String> three = Sameness.of("p", "q").meet(Sameness.of("q", "r"));
        Sameness<String> two = Sameness.of("q", "r");

        assertEquals(three.common(two), two.common(three));
    }

    /**
     * A block is its positions and nothing else, so the same positions written two ways are one
     * coordinate — which is what lets one purse pay for one machine.
     */
    @Test
    void aBlockIsEqualByItsPositionsHoweverItWasMade() {
        Sameness<String> here = Sameness.of("p", "q").meet(Sameness.of("q", "r"));
        Sameness<String> there = Sameness.of("r", "q").meet(Sameness.of("q", "p"));

        assertEquals(here.blockOf("p"), there.blockOf("p"));
        assertEquals(here.blockOf("p").hashCode(), there.blockOf("p").hashCode());
        assertEquals(here, there);
    }

    /** And it reads the same however the equalities reached its positions, because what is put in
     *  an order to write it out is the positions' renderings and not the positions. */
    @Test
    void aBlockReadsTheSameHoweverItsPositionsWereReached() {
        Sameness<String> here = Sameness.of("c", "a").meet(Sameness.of("a", "b"));
        Sameness<String> there = Sameness.of("b", "a").meet(Sameness.of("a", "c"));

        assertEquals(String.valueOf(here.blockOf("a")), String.valueOf(there.blockOf("a")));
        assertEquals(String.valueOf(here), String.valueOf(there));
    }

    /** A renaming names two positions two positions, so a block has as many members after it. */
    @Test
    void aRenamingMovesABlockAndDoesNotFoldIt() {
        Sameness<String> both = Sameness.of("p", "r");

        Sameness<String> renamed = both.renamed(each -> each + "!");

        assertEquals(Set.of("p!", "r!"), renamed.blockOf("p!").members());
        assertTrue(renamed.blockOf("p").isOne(), "the old names are not in it");
    }

    /** An answer is about at least one position, so there is no block of none. */
    @Test
    void thereIsNoBlockOfNoPositions() {
        assertThrows(IllegalArgumentException.class, () -> Sameness.Block.of(Set.<String>of()));
    }

    /**
     * Blocks that share a position are the classes of no relation, and reading them as one is
     * refused.
     *
     * <p>Read as a relation, {@code p} would be held with {@code q} while the two of them answered
     * to different classes — which is not a partition and is not something anything below can be
     * asked about. Refused where the relation is built, so that a caller with blocks in hand
     * cannot reach a state a product would have refused.
     */
    @Test
    void blocksThatShareAPositionAreTheClassesOfNoRelation() {
        Sameness.Block<String> pq = Sameness.of("p", "q").blockOf("p");
        Sameness.Block<String> qr = Sameness.of("q", "r").blockOf("q");

        assertThrows(IllegalArgumentException.class, () -> Sameness.of(List.of(pq, qr)));
    }

    /** And blocks that share none are read as the relation they are the classes of. */
    @Test
    void blocksThatShareNoPositionAreReadAsTheRelationTheyAreTheClassesOf() {
        Sameness.Block<String> pq = Sameness.of("p", "q").blockOf("p");
        Sameness.Block<String> rs = Sameness.of("r", "s").blockOf("r");

        Sameness<String> both = Sameness.of(List.of(pq, rs));

        assertEquals(pq, both.blockOf("p"));
        assertEquals(rs, both.blockOf("s"));
    }
}
