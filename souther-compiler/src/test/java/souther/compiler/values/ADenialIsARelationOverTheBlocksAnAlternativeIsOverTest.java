package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a relation between blocks holds, and how it moves when the blocks do.
 *
 * <p>The blocks an alternative is a product over are settled by what it holds, and a conjunction
 * leaves coarser ones while a choice leaves finer ones. So a relation stated of the blocks either
 * side was over has to arrive at the blocks the operation leaves, and in the direction that
 * operation goes: pushed forward under a conjunction, pulled back under a choice.
 *
 * <p>Asked here rather than through a model because what a model can show is which declarations are
 * refused, and these are rules about where an answer is filed. Two of them are only visible as a
 * refusal that fails to happen.
 */
class ADenialIsARelationOverTheBlocksAnAlternativeIsOverTest {

    private static final Sameness.Block<String> P = Sameness.Block.of("p");
    private static final Sameness.Block<String> Q = Sameness.Block.of("q");
    private static final Sameness.Block<String> R = Sameness.Block.of("r");

    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    /** What a block admits, for a reduction that has to be asked. */
    private static Apartness.WhatABlockAdmits<String> holding(
            java.util.Map<Sameness.Block<String>, Set<Value>> these) {
        return (block, _) -> new Admits.These(these.getOrDefault(block, Set.of(A, B)));
    }

    /** A pair is unordered, so a rule written either way round is one rule. */
    @Test
    void aPairIsUnorderedAndIsOneRuleWrittenEitherWayRound() {
        assertEquals(Apartness.of("p", "r"), Apartness.of("r", "p"));
        assertEquals(1, Apartness.of("p", "r").and(Apartness.of("r", "p")).edges().size(),
                "one rule stated twice is stated once");
    }

    /**
     * A conjunction that holds two blocks as one carries a denial onto the block it leaves.
     *
     * <p>{@code q /= r} stated of {@code q} on its own is a denial between {@code r} and whatever
     * {@code q} is part of once {@code p == q} is read. Left where it was stated, it would name a
     * block the conjunction does not answer in.
     */
    @Test
    void aConjunctionCarriesADenialOntoTheBlockItLeaves() {
        Sameness<String> heldAsOne = Sameness.of("p", "q");
        Sameness.Block<String> both = heldAsOne.blockOf("p");

        Apartness<String> filed = Apartness.of("q", "r").filedIn(heldAsOne);

        assertEquals(Set.of(both, R), filed.blocks());
        assertEquals(Set.of(both), filed.apartFrom(R));
    }

    /** And where both ends land on one block, the rules state that a value differs from itself. */
    @Test
    void andWhereBothEndsLandOnOneBlockAValueIsStatedToDifferFromItself() {
        Apartness<String> filed = Apartness.of("p", "q").filedIn(Sameness.of("p", "q"));

        assertTrue(filed.holdsABlockApartFromItself());
        assertInstanceOf(RelationalWitness.ABlockApartFromItself.class,
                refusedBy(filed.reduce(holding(java.util.Map.of()))));
    }

    /**
     * A choice keeps what both alternatives state, read at the finer blocks it answers in.
     *
     * <p>One alternative holds {@code p} and {@code q} as one value and states that block apart
     * from {@code r}; the other states {@code p /= r} and {@code q /= r} of the two positions
     * separately. The choice holds neither {@code p} nor {@code q} as one with anything, and what
     * both alternatives state of them is that each differs from {@code r}.
     *
     * <p>Compared where they were stated, the coarser pair would match neither of the finer ones
     * and a denial both alternatives state would be lost.
     */
    @Test
    void aChoiceKeepsWhatBothAlternativesStateAtTheFinerBlocks() {
        Sameness<String> coarser = Sameness.of("p", "q");
        Apartness<String> one = Apartness.of("p", "r").filedIn(coarser);
        Apartness<String> other = Apartness.of("p", "r").and(Apartness.of("q", "r"));

        Apartness<String> both = one.commonWith(other, Sameness.discrete());

        assertEquals(Set.of(Sameness.Block.of("p"), Sameness.Block.of("q"), R), both.blocks());
        assertEquals(Set.of(P, Q), both.apartFrom(R));
    }

    /** And a denial only one alternative states is not the choice's. */
    @Test
    void andADenialOnlyOneAlternativeStatesIsNotTheChoices() {
        assertTrue(Apartness.of("p", "r")
                .commonWith(Apartness.of("q", "r"), Sameness.discrete()).isEmpty());
    }

    /**
     * Blocks all stated to differ, counted; blocks merely related, not.
     *
     * <p>{@code p /= q && q /= r && r /= p} needs a value each over two values and nothing
     * satisfies it. {@code p /= q && q /= r} states nothing of {@code p} and {@code r}, so two
     * values are enough — and a reduction counting what a relation reaches rather than what it
     * states to differ would refuse a model no rule refuses.
     */
    @Test
    void blocksAllStatedToDifferAreCountedAndBlocksMerelyRelatedAreNot() {
        Apartness<String> triangle = Apartness.of("p", "q")
                .and(Apartness.of("q", "r")).and(Apartness.of("r", "p"));
        Apartness<String> chain = Apartness.of("p", "q").and(Apartness.of("q", "r"));

        RelationalWitness<String> why = refusedBy(triangle.reduce(holding(java.util.Map.of())));
        if (!(why instanceof RelationalWitness.TooFewValuesBetweenThem<String> few)) {
            throw new AssertionError("refused by counting, and said so: " + why);
        }
        assertEquals(Set.of(P, Q, R), few.blocks());
        assertEquals(Set.of(A, B), few.available());

        assertInstanceOf(Apartness.Reduction.Standing.class,
                chain.reduce(holding(java.util.Map.of())));
    }

    /**
     * A block whose neighbours hold one value each loses those values, along the whole chain.
     *
     * <p>{@code p} at {@code A} takes {@code A} from {@code q}, which leaves {@code q} at {@code B}
     * — and that takes {@code B} from {@code r}, which is left nothing. Two steps and not one, so a
     * reading that took only what the blocks held when it started would admit this.
     */
    @Test
    void takingWhatOneValuedBlocksHoldFollowsTheWholeChain() {
        Apartness<String> chain = Apartness.of("p", "q").and(Apartness.of("q", "r"));

        RelationalWitness<String> why = refusedBy(chain.reduce(holding(java.util.Map.of(
                P, Set.of(A), Q, Set.of(A, B), R, Set.of(B)))));

        if (!(why instanceof RelationalWitness.NoValueLeftBetweenThem<String> left)) {
            throw new AssertionError("refused by taking values away, and said so: " + why);
        }
        assertEquals(R, left.block());
        assertEquals(Set.of(Q), left.by(), "the neighbour whose one value took the last of them");
    }

    /** What a reduction that refused was refused by. */
    private static RelationalWitness<String> refusedBy(Apartness.Reduction<String> said) {
        assertInstanceOf(Apartness.Reduction.Nothing.class, said);
        return ((Apartness.Reduction.Nothing<String>) said).why();
    }

    /**
     * A cycle of five over two values is refused by no pair and by no set of blocks all stated to
     * differ, and this says nothing about it.
     *
     * <p>Nothing satisfies it — a cycle of odd length needs three values — and no argument this
     * reduction has reaches it: no block is left one value, so nothing is taken away; and every set
     * of blocks all stated to differ here is a pair, which two values are enough for. Deciding it
     * is colouring a graph, which is a different question from the one this answers.
     *
     * <p>Written down as the boundary and not as a gap to be closed here. What this holds is the
     * whole relation, so a reduction that can colour is one added beside these rather than a
     * rewrite of what they leave — and a reading that answered {@link Apartness.Reduction.Standing}
     * would be claiming an assignment it has not got.
     */
    @Test
    void aCycleOfFiveOverTwoValuesIsPastWhatThisReductionShows() {
        Apartness<String> cycle = Apartness.of("a", "b")
                .and(Apartness.of("b", "c")).and(Apartness.of("c", "d"))
                .and(Apartness.of("d", "e")).and(Apartness.of("e", "a"));

        assertInstanceOf(Apartness.Reduction.NotKnown.class, cycle.reduce(holding(
                java.util.Map.of())), "nothing satisfies it, and no argument here reaches it");
    }

    /** A block this cannot say the values of is one the relation says nothing about. */
    @Test
    void aBlockWhoseValuesAreNotKnownIsOneTheRelationSaysNothingAbout() {
        assertInstanceOf(Apartness.Reduction.NotKnown.class,
                Apartness.of("p", "r").reduce((_, _) -> new Admits.NotKnown()));
    }

    /** And a block holding more values than the relation has blocks never runs out. */
    @Test
    void andABlockHoldingMoreValuesThanThereAreBlocksNeverRunsOut() {
        assertInstanceOf(Apartness.Reduction.Standing.class,
                Apartness.of("p", "r").reduce((_, _) -> new Admits.MoreThanCounted()));
    }

    /** A relation nothing stated is one nothing refuses. */
    @Test
    void aRelationNothingStatedRefusesNothing() {
        assertTrue(Apartness.<String>nothing().isEmpty());
        assertFalse(Apartness.<String>nothing().holdsABlockApartFromItself());
        assertInstanceOf(Apartness.Reduction.Standing.class,
                Apartness.<String>nothing().reduce((_, _) -> new Admits.NotKnown()));
    }
}
