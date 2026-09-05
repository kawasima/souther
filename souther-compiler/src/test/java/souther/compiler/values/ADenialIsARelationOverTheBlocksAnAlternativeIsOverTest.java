package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    /**
     * A pair is unordered, so a rule written either way round is one rule.
     *
     * <p>By being a pair and not by putting its ends in an order. An order over the blocks would
     * have to come from how they are spelled, and then two blocks that render alike would be one
     * end — so what is asserted is the equality itself, which is what the reading below it is
     * filed and deduplicated by.
     */
    @Test
    void aPairIsUnorderedAndIsOneRuleWrittenEitherWayRound() {
        Apartness.Edge<String> one = new Apartness.Edge<>(P, R);
        Apartness.Edge<String> back = new Apartness.Edge<>(R, P);

        assertEquals(one, back);
        assertEquals(one.hashCode(), back.hashCode(), "and equal pairs hash alike");
        assertEquals(String.valueOf(one), String.valueOf(back),
                "and are written out the same way, whichever end was stated first");

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

        // And the chain is not refused. Whether it is said to stand is a different question: a
        // value apiece exists for it and no argument here shows one, which is what
        // {@link Apartness.Reduction.NotKnown} says.
        assertInstanceOf(Apartness.Reduction.NotKnown.class,
                chain.reduce(holding(java.util.Map.of())));
    }

    /**
     * An assignment is claimed only where every order of the blocks shows one.
     *
     * <p>The same relation over the same values, stated two ways round. Both leave every block more
     * values than the relation has blocks, so each of them can be given one no other took whatever
     * order they are taken in — and a reading that took the blocks in the order the denials were
     * stated in would answer one of these and not the other, which is a fact about the writing.
     */
    @Test
    void whetherAnAssignmentIsClaimedDoesNotTurnOnHowTheDenialsWereStated() {
        Apartness<String> one = Apartness.of("p", "q")
                .and(Apartness.of("p", "r")).and(Apartness.of("q", "r"));
        Apartness<String> back = Apartness.of("q", "r")
                .and(Apartness.of("p", "r")).and(Apartness.of("p", "q"));

        assertEquals(one, back, "one relation, written two ways");
        assertInstanceOf(Apartness.Reduction.Standing.class,
                one.reduce((_, _) -> new Admits.MoreThanCounted()));
        assertInstanceOf(Apartness.Reduction.Standing.class,
                back.reduce((_, _) -> new Admits.MoreThanCounted()));
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
        // And the argument is every block it rests on. {@code q} took the last value from {@code r}
        // and holds one value only because {@code p} does — so {@code q} with {@code r} alone is
        // satisfiable, and a witness naming those two would say a lack is at a pair whose own rules
        // are fine with what they leave it.
        assertEquals(Set.of(P, Q), left.by(),
                "every block the argument rests on, and not the one that took the last value");
        assertEquals(Set.of(P, Q, R), left.blocks());
    }

    /**
     * And a lack reached through other blocks is another lack, whatever it ends at.
     *
     * <p>Two chains ending at the same block, taking the same values away, resting on different
     * rules: one runs through {@code q} and the other through {@code s}. Named by where they end,
     * the two would be one lack — and a choice between readings holding them would keep it and say
     * that those blocks together admit nothing, which neither of them showed of them.
     */
    @Test
    void andALackReachedThroughOtherBlocksIsAnotherLack() {
        RelationalWitness<String> one = refusedBy(
                Apartness.of("p", "q").and(Apartness.of("q", "r")).reduce(holding(java.util.Map.of(
                        P, Set.of(A), Q, Set.of(A, B), R, Set.of(B)))));
        RelationalWitness<String> other = refusedBy(
                Apartness.of("p", "s").and(Apartness.of("s", "r")).reduce(holding(java.util.Map.of(
                        P, Set.of(A), Sameness.Block.of("s"), Set.of(A, B), R, Set.of(B)))));

        assertNotEquals(one, other, "two arguments over different blocks are two arguments");
        assertInstanceOf(Refusal.Nowhere.class,
                Refusal.shownByBoth(new Refusal.OfThemTogether<>(one),
                        new Refusal.OfThemTogether<>(other)),
                "so a choice between readings holding them keeps neither");
    }

    /**
     * A conjunction that makes a denial into a value stated to differ from itself holds nothing,
     * and says what by.
     *
     * <p>Nothing stands in such an alternative, so it is no member of a union — a choice between it
     * and something else is that something else. Kept as one so that its argument could be read
     * later, the union would hold what its alternatives do not and a reading of it would say the
     * declaration admits what none of them does.
     *
     * <p>So the argument leaves with it. What refused an alternative is knowable only while it is
     * being refused, and it is carried out rather than worked out again from what is left.
     */
    @Test
    void aConjunctionEmptiedByItsRelationHoldsNothingAndSaysWhat() {
        Allowance<String> sets = AsACompilationAllows.forAdmittedValues();
        AdmissibleValues<String> both = AdmissibleValues.<String>holdingAsOne("p", "r")
                .meet(AdmissibleValues.heldApart("p", "r"), sets);

        assertTrue(both.isBottom(), "no value of these rules can be written");

        if (!(both.refusedBy() instanceof Refusal.OfThemTogether<String> together)) {
            throw new AssertionError("refused by what its blocks are held as: " + both.refusedBy());
        }
        assertInstanceOf(RelationalWitness.ABlockApartFromItself.class, together.why());
        assertEquals(Set.of(Sameness.of("p", "r").blockOf("p")), together.blocks());
    }

    /**
     * And a choice between it and an alternative somebody can take is that alternative.
     *
     * <p>A union with an empty member is the union of the rest, so what the choice leaves at
     * {@code p} is what the branch anybody can take leaves it. Kept as a member, the dead branch
     * says nothing about {@code p} — nothing narrowed it there — and the choice would come back
     * admitting every value.
     */
    @Test
    void andAChoiceBetweenItAndSomethingStandingIsThatSomething() {
        Allowance<String> sets = AsACompilationAllows.forAdmittedValues();
        AdmissibleValues<String> dead = AdmissibleValues.<String>holdingAsOne("p", "r")
                .meet(AdmissibleValues.heldApart("p", "r"), sets);

        assertEquals(ValueSet.just(A),
                dead.joinApart(AdmissibleValues.at("p", ValueSet.just(A)), sets).at("p"));
        assertEquals(ValueSet.just(A),
                AdmissibleValues.at("p", ValueSet.just(A)).joinApart(dead, sets).at("p"));
    }

    /** What a reduction that refused was refused by. */
    private static RelationalWitness<String> refusedBy(Apartness.Reduction<String> said) {
        assertInstanceOf(Apartness.Reduction.Nothing.class, said);
        return ((Apartness.Reduction.Nothing<String>) said).why();
    }

    /**
     * The sets a count is taken of are the ones nothing can be added to, each of them once.
     *
     * <p>A set inside one of these is refused only where the whole is, so emitting the parts as
     * well is the same question asked once per subset — and a set reached by taking its blocks in
     * another order is that same set again. Both show up as a count taken more often than there are
     * answers, which is what the bound on how many sets this will look at then runs into.
     */
    @Test
    void theSetsCountedAreTheOnesNothingCanBeAddedTo() {
        Apartness<String> triangle = Apartness.of("p", "q")
                .and(Apartness.of("q", "r")).and(Apartness.of("r", "p"));

        assertEquals(List.of(Set.of(P, Q, R)), triangle.everyPairwiseApartSet(64),
                "one set, and not every part of it nor every order its blocks come in");

        Apartness<String> chain = Apartness.of("p", "q").and(Apartness.of("q", "r"));
        assertEquals(List.of(Set.of(P, Q), Set.of(Q, R)), chain.everyPairwiseApartSet(64),
                "and a chain is two of them, neither of which the other holds");
    }

    /**
     * And seven blocks all stated to differ over two values are refused, like three of them.
     *
     * <p>Beside the cycle below, and the two together are what part the incompleteness this reading
     * means from one it would have by accident. Seven blocks all apart is the same argument three
     * of them are refused by and nothing harder; a reading that reached it by walking every part of
     * the set, or the set once per order its blocks come in, would run out of what it allows itself
     * to look at and say nothing — and the shape it went quiet on would be the easy one.
     */
    @Test
    void andSevenBlocksAllStatedToDifferOverTwoValuesAreRefusedLikeThree() {
        Apartness<String> all = Apartness.nothing();
        List<String> named = List.of("a", "b", "c", "d", "e", "f", "g");
        for (int one = 0; one < named.size(); one++) {
            for (int other = one + 1; other < named.size(); other++) {
                all = all.and(Apartness.of(named.get(one), named.get(other)));
            }
        }

        assertEquals(1, all.everyPairwiseApartSet(64).size(),
                "one set, and not every part of it nor every order its blocks come in");
        assertInstanceOf(RelationalWitness.TooFewValuesBetweenThem.class,
                refusedBy(all.reduce(holding(java.util.Map.of()))));
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
