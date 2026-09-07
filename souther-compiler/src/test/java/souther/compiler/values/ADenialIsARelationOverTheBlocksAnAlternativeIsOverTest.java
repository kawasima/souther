package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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

    /** The first block of a run or a ring these tests build, for giving one of them a value the
     *  rest do not have. */
    private static final Sameness.Block<String> P0 = Sameness.Block.of("p0");

    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");
    private static final Value C = Value.text("C");

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
        assertInstanceOf(RelationalLack.ABlockApartFromItself.class,
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

    /**
     * A choice merged into one product keeps what every alternative states.
     *
     * <p>Merging a union into the smallest product containing it widens what the blocks hold. It
     * does not licence forgetting a rule both branches wrote — and a denial dropped there is one no
     * equality read beside the choice can be refused against, so a declaration nothing satisfies
     * comes back admitted.
     *
     * <p>Merging is one of the two ways a choice is held, and which of them a compilation takes is
     * settled by how large the choice is rather than by anything a model says. Held apart the
     * alternatives keep their own denials and there is nothing to carry, so this is asked of the
     * merged one.
     */
    @Test
    void aChoiceMergedIntoOneProductKeepsWhatEveryAlternativeStates() {
        Allowance<String> sets = AsACompilationAllows.forAdmittedValues();

        PlannedValues<String> planned = PlannedValues.<String>heldApart("p", "r")
                .joinLive(PlannedValues.heldApart("p", "r"));
        assertTrue(planned.meet(PlannedValues.holdingAsOne("p", "r"))
                        .anyAlternativeAdmits((_, _) -> Emptiness.NONEMPTY) == Emptiness.EMPTY,
                "the choice states it, so an equality read beside it refuses");
        assertTrue(planned.resolve(sets).values()
                        .meet(AdmissibleValues.holdingAsOne("p", "r"), sets).isBottom(),
                "and it is still stated once the values are worked out");
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

        RelationalLack<String> why = refusedBy(triangle.reduce(holding(java.util.Map.of())));
        if (!(why instanceof RelationalLack.TooFewValuesBetweenThem<String> few)) {
            throw new AssertionError("refused by counting, and said so: " + why);
        }
        assertEquals(Set.of(P, Q, R), few.blocks());
        assertEquals(Set.of(A, B), few.available());

        // And the chain is not refused, and is said to stand: two values are enough for it, and
        // what shows that is an assignment found rather than a count that came out even.
        assertInstanceOf(Apartness.Reduction.Standing.class,
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
     * A block both of whose neighbours leave it no room holds nothing, and the lack is at that
     * block.
     *
     * <p>{@code p} holds {@code A} and leaves {@code q} no room for it; {@code r} holds {@code B}
     * and leaves it no room for that. So {@code q} is what the rules leave nothing, and it is left
     * nothing by two blocks neither of which took a value the other's removal made available.
     *
     * <p><b>Which block that is, is not a question about the order the denials were written.</b>
     * Taking the blocks one after another and writing each removal down as it is made, {@code p}
     * leaves {@code q} holding {@code B} and {@code q} then leaves {@code r} nothing — the same
     * relation refused at a different block, and which of the two a reader is handed would be
     * settled by which pair was stated first.
     */
    @Test
    void aBlockItsNeighboursLeaveNoRoomForHoldsNothing() {
        Apartness<String> chain = Apartness.of("p", "q").and(Apartness.of("q", "r"));

        Apartness.Reduction<String> said = chain.reduce(holding(java.util.Map.of(
                P, Set.of(A), Q, Set.of(A, B), R, Set.of(B))));

        assertEquals(Set.of(new RelationalLack.NoValueLeftForIt<>(Q)), everyLackOf(said).claimed(),
                "and what is claimed is that this block is left nothing, and no more than that");
        // And the blocks a report may name are those and the ones the values went to, which is
        // what the route says and the claim does not.
        assertEquals(Set.of(P, Q, R), refusalOf(said).blocks());

        // And the relation written the other way round is the same relation and is answered the
        // same way, route and all.
        assertEquals(refusalOf(said), refusalOf(
                Apartness.of("q", "r").and(Apartness.of("p", "q"))
                        .reduce(holding(java.util.Map.of(
                                P, Set.of(A), Q, Set.of(A, B), R, Set.of(B))))));
    }

    /**
     * And a lack at another block is another lack.
     *
     * <p>Two relations of the same shape over different blocks: one leaves {@code q} nothing and
     * the other leaves {@code s} nothing, and each block is left values of its own in the relation
     * that does not name it. Read as one lack, a choice between readings holding them would say
     * that both blocks are left nothing, which neither of them showed.
     */
    @Test
    void andALackAtAnotherBlockIsAnotherLack() {
        RelationalLack<String> one = refusedBy(
                Apartness.of("p", "q").and(Apartness.of("q", "r")).reduce(holding(java.util.Map.of(
                        P, Set.of(A), Q, Set.of(A, B), R, Set.of(B)))));
        RelationalLack<String> other = refusedBy(
                Apartness.of("p", "s").and(Apartness.of("s", "r")).reduce(holding(java.util.Map.of(
                        P, Set.of(A), Sameness.Block.of("s"), Set.of(A, B), R, Set.of(B)))));

        assertNotEquals(one, other, "two lacks at different blocks are two lacks");
        assertInstanceOf(Refusal.Nowhere.class,
                Refusal.shownByBoth(new Refusal.OfThemTogether<>(Lacks.of(one)),
                        new Refusal.OfThemTogether<>(Lacks.of(other))),
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
        assertInstanceOf(RelationalLack.ABlockApartFromItself.class,
                together.lacks().only().lack());
        assertEquals(Set.of(Sameness.of("p", "r").blockOf("p")), both.refusedBy().blocks());
    }

    /** What a reduction that refused was refused by, where what it shows is one lack. */
    private static RelationalLack<String> refusedBy(Apartness.Reduction<String> said) {
        Lacks<String> lacks = everyLackOf(said);
        assertEquals(1, lacks.size(), "one lack, and this shows several: " + lacks);
        return lacks.only().lack();
    }

    /** Every lack a reduction that refused shows. */
    private static Lacks<String> everyLackOf(Apartness.Reduction<String> said) {
        assertInstanceOf(Apartness.Reduction.Nothing.class, said);
        return ((Apartness.Reduction.Nothing<String>) said).lacks();
    }

    /** What a reduction that refused leaves a reading holding it. */
    private static Refusal<String> refusalOf(Apartness.Reduction<String> said) {
        return new Refusal.OfThemTogether<>(everyLackOf(said));
    }

    /**
     * The sets a count is taken of are the ones nothing can be added to, each of them once.
     *
     * <p>A set inside one of these is refused only where the whole is, so emitting the parts as
     * well is the same question asked once per subset — and a set reached by taking its blocks in
     * another order is that same set again. Both are a count taken more often than there are
     * answers, and how much walking a relation is worth is settled by how many blocks it has rather
     * than by how many roads to one set the walk chose to take.
     */
    @Test
    void theSetsCountedAreTheOnesNothingCanBeAddedTo() {
        Apartness<String> triangle = Apartness.of("p", "q")
                .and(Apartness.of("q", "r")).and(Apartness.of("r", "p"));

        assertEquals(List.of(Set.of(P, Q, R)), triangle.everySetWorthWalkingFor().orElseThrow(),
                "one set, and not every part of it nor every order its blocks come in");

        Apartness<String> chain = Apartness.of("p", "q").and(Apartness.of("q", "r"));
        assertEquals(Set.of(Set.of(P, Q), Set.of(Q, R)),
                new LinkedHashSet<>(chain.everySetWorthWalkingFor().orElseThrow()),
                "and a chain is two of them, neither of which the other holds");
    }

    /**
     * And seven blocks all stated to differ over two values are refused, like three of them.
     *
     * <p>Beside the cycle below, and the two together are what part the incompleteness this reading
     * means from one it would have by accident. Seven blocks all apart is the same argument three
     * of them are refused by and nothing harder, and it is the shape the walk is cheapest on: the
     * pivot leaves one way in at every level, so the whole relation is one path however many blocks
     * it has.
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

        assertEquals(1, all.everySetWorthWalkingFor().orElseThrow().size(),
                "one set, and not every part of it nor every order its blocks come in");
        assertInstanceOf(RelationalLack.TooFewValuesBetweenThem.class,
                refusedBy(all.reduce(holding(java.util.Map.of()))));
    }

    /**
     * And blocks all stated to differ are counted however many of them there are.
     *
     * <p>Past the bound a general relation is admitted by, and refused all the same. What that
     * bound is about is a walk that may reach one set by many roads; here the pivot leaves one way
     * in at every level, so the shape says the walk is a single path and the relation is admitted
     * on that.
     *
     * <p><b>Leaving one pair out as well as leaving none.</b> A relation all of whose pairs are
     * stated is one point of what makes the walk cheap, and an admission written on that point
     * would be about an example rather than about the property — a relation one pair short of it is
     * as cheap and would go unanswered. So both are asserted, and the second is what a rule about
     * complete relations would miss.
     */
    @Test
    void andBlocksNearlyAllStatedToDifferAreCountedHoweverManyOfThemThereAre() {
        List<String> named = everyOneOf(34);
        Apartness<String> all = allApart(named);
        Apartness<String> butOne = allApartBut(named, List.of(named.get(0), named.get(1)));

        assertEquals(0, all.extent().pairsLeftOut(), "every block against every other");
        assertInstanceOf(RelationalLack.TooFewValuesBetweenThem.class,
                refusedBy(all.reduce(holding(java.util.Map.of()))));

        assertEquals(1, butOne.extent().pairsLeftOut(), "and this one leaves a pair out");
        // Two sets nothing can be added to, since the pair left out is in neither, and a shortage
        // shown of each. Both are said: each of them is short of its own blocks, which is nearer
        // than anything true of the two together.
        Lacks<String> shown = everyLackOf(butOne.reduce(holding(java.util.Map.of())));
        assertEquals(butOne.everySetWorthWalkingFor().orElseThrow().size(), shown.size(),
                "which the walk is as cheap on, so every set of it is counted too");
        assertTrue(shown.all(lack -> lack instanceof RelationalLack.TooFewValuesBetweenThem));
    }

    /**
     * A relation whose blocks each hold more values than they have neighbours stands, however large
     * it is.
     *
     * <p>Read off the pairs and answered before anything walks. Take the blocks in any order and
     * give each one a value: what it is stated to differ from has taken at most one value apiece
     * and there are fewer of them than it holds, so one is always free.
     *
     * <p>Which is why the relation asserted here is one nothing else reaches. A ring of forty
     * blocks is past what a set of blocks all stated to differ is looked for over, and three values
     * apiece is more assignments than are looked through — so both of the arguments that decide by
     * walking are out, and what answers is the shape.
     *
     * <p><b>And the same ring over two values is not.</b> Two values is what each block has
     * neighbours, so the argument says nothing of it — and a ring of even length over two values
     * stands all the same, which is what nothing here can show. Asserted so that what is claimed is
     * where this stops rather than that it decides rings.
     */
    @Test
    void andBlocksHoldingMoreValuesThanTheyHaveNeighboursStandHoweverLargeTheRelationIs() {
        Apartness<String> ring = cycleOf(everyOneOf(40));
        Set<Value> three = new LinkedHashSet<>(Set.of(A, B));
        three.add(C);

        assertEquals(40, ring.extent().blocks(), "past what the sets are walked for");
        assertInstanceOf(Apartness.Reduction.Standing.class,
                ring.reduce((_, _) -> new Admits.These(three)),
                "and past what the assignments are looked through, and answered all the same");

        assertInstanceOf(Apartness.Reduction.NotKnown.class,
                ring.reduce(holding(java.util.Map.of())),
                "where two values apiece is what its blocks have neighbours, and nothing answers");
    }

    /**
     * And a relation neither of those admits is one this says nothing about.
     *
     * <p>Enough blocks to be past what a general relation is admitted by, and enough pairs left out
     * to be past what a nearly-complete one is. Each pair left out can double how many sets of
     * blocks all stated to differ there are to find, so a relation leaving many of them is what
     * both questions are asked to keep out.
     *
     * <p>Written down as the boundary rather than as a gap. The same relation leaving fewer pairs
     * out is counted, so what is asserted is where this stops and not that it cannot count: a bound
     * nothing shows the far side of is a bound nobody can tell from an accident.
     */
    @Test
    void andARelationPastBothOfThoseIsOneThisSaysNothingAbout() {
        List<String> named = everyOneOf(34);
        Apartness<String> past = leavingOut(named, 13);
        Apartness<String> within = leavingOut(named, 12);

        assertInstanceOf(Apartness.Reduction.NotKnown.class,
                past.reduce(holding(java.util.Map.of())),
                "past what either question admits, and so unanswered");
        // And counted, which is what the two are asserted for. Every set it is taken of is short,
        // and each of those is its own lack — as many of them as the walk found, which is what the
        // bound this relation is admitted by is derived from.
        Lacks<String> shown = everyLackOf(within.reduce(holding(java.util.Map.of())));
        assertEquals(within.everySetWorthWalkingFor().orElseThrow().size(), shown.size(),
                "and one pair fewer left out is inside it, and counted");
        assertTrue(shown.all(lack -> lack instanceof RelationalLack.TooFewValuesBetweenThem));
    }

    /**
     * Every pair of {@code named} stated to differ but for {@code many} of them, no two of which
     * share a block, which is the most sets that many left-out pairs can make.
     *
     * <p>How many were left out is asserted rather than assumed. Asked for more pairs than the
     * blocks can supply without sharing an end, this leaves out as many as it can and says nothing
     * — and a boundary asserted through it would then be about a shape it never built, passing
     * whatever the figure it is meant to hold was moved to.
     */
    private static Apartness<String> leavingOut(List<String> named, int many) {
        Apartness<String> all = Apartness.nothing();
        for (int first = 0; first < named.size(); first++) {
            for (int second = first + 1; second < named.size(); second++) {
                if (second == first + 1 && first % 2 == 0 && first / 2 < many) {
                    continue;
                }
                all = all.and(Apartness.of(named.get(first), named.get(second)));
            }
        }
        assertEquals(many, all.extent().pairsLeftOut(),
                "the fixture leaves out what it was asked to and not as many as it could");
        return all;
    }

    private static List<String> everyOneOf(int many) {
        List<String> named = new ArrayList<>();
        for (int each = 0; each < many; each++) {
            named.add("p" + each);
        }
        return named;
    }

    /**
     * A relation over eleven blocks whose largest set of blocks all stated to differ is a pair, and
     * which three values do not satisfy.
     *
     * <p>Built beside a ring of five: a shadow of each of its blocks, stated to differ from that
     * block's neighbours rather than from the block itself, and one block above stated to differ
     * from every shadow. A shadow and its own block are not stated to differ, so no set of three
     * grows, and the value the block above takes is one no shadow may hold — which leaves the ring
     * two values, and a ring of five needs three.
     */
    private static Apartness<String> noThreeApartAndNotThreeColourable() {
        List<String> ring = everyOneOf(5);
        Apartness<String> all = cycleOf(ring);
        for (int each = 0; each < ring.size(); each++) {
            String shadow = "s" + ring.get(each);
            all = all.and(Apartness.of(shadow, ring.get((each + 1) % ring.size())))
                    .and(Apartness.of(shadow, ring.get((each + ring.size() - 1) % ring.size())))
                    .and(Apartness.of("above", shadow));
        }
        return all;
    }

    private static Set<Value> five() {
        Set<Value> these = four();
        these.add(Value.text("E"));
        return these;
    }

    private static Set<Value> four() {
        Set<Value> these = new LinkedHashSet<>(Set.of(A, B));
        these.add(C);
        these.add(Value.text("D"));
        return these;
    }

    /** Each of {@code named} stated to differ from the next, and the last from the first. */
    private static Apartness<String> cycleOf(List<String> named) {
        Apartness<String> all = Apartness.nothing();
        for (int each = 0; each < named.size(); each++) {
            all = all.and(Apartness.of(named.get(each), named.get((each + 1) % named.size())));
        }
        return all;
    }

    private static Apartness<String> allApart(List<String> named) {
        return allApartBut(named, List.of());
    }

    /** Every pair of {@code named} stated to differ, less {@code spared}, which is a pair or is
     *  none. */
    private static Apartness<String> allApartBut(List<String> named, List<String> spared) {
        Apartness<String> all = Apartness.nothing();
        for (int first = 0; first < named.size(); first++) {
            for (int second = first + 1; second < named.size(); second++) {
                if (spared.equals(List.of(named.get(first), named.get(second)))) {
                    continue;
                }
                all = all.and(Apartness.of(named.get(first), named.get(second)));
            }
        }
        return all;
    }

    /**
     * A cycle of odd length over two values is refused, and one of even length stands.
     *
     * <p>Neither is refused by a pair or by a set of blocks all stated to differ: no block is left
     * one value, so nothing is taken away, and every such set here is a pair, which two values are
     * enough for. What parts them is that a cycle of odd length needs three values and one of even
     * length does not, and the only thing that reads that off the relation is looking for an
     * assignment.
     *
     * <p>Which is why the two are asserted together. Refused by counting how many blocks a cycle
     * has, both would come out the same way — and the reading would be answering a shape rather
     * than a relation.
     */
    @Test
    void aCycleOfOddLengthOverTwoValuesIsRefusedAndOneOfEvenLengthStands() {
        Apartness<String> odd = cycleOf(everyOneOf(5));
        Apartness<String> even = cycleOf(everyOneOf(6));

        assertInstanceOf(RelationalLack.NoAssignmentTellsThemApart.class,
                refusedBy(odd.reduce(holding(java.util.Map.of()))),
                "nothing satisfies it, and what shows that is running out of assignments");
        assertInstanceOf(Apartness.Reduction.Standing.class, even.reduce(holding(
                java.util.Map.of())), "and two values are enough for this one, and it is said to");
    }

    /**
     * And a relation no two of whose blocks make a set of three is still not satisfied by three
     * values.
     *
     * <p>A ring of five is refused over two values, and a reading could reach that by counting how
     * many blocks a ring has rather than by looking for an assignment. This one cannot be reached
     * that way. No three of its blocks are all stated to differ, so every set the counting argument
     * is asked of is a pair and three values are more than enough for a pair; and three values are
     * still not enough for the whole of it.
     *
     * <p>Which is what parts the argument that was added from a rule about rings. Written as one,
     * this relation would be admitted and no value of it exists.
     *
     * <p>Four values do satisfy it, and this does not say so: eleven blocks over four values is
     * more assignments than are looked through. The smallest relation of this shape is this one, so
     * that is not a size the bound could be raised past — it is where a reading that decides by
     * looking stops.
     */
    @Test
    void andBlocksNoThreeOfWhichAreAllApartAreStillNotSatisfiedByThreeValues() {
        Apartness<String> made = noThreeApartAndNotThreeColourable();
        Set<Value> three = new LinkedHashSet<>(Set.of(A, B));
        three.add(C);

        assertEquals(2, made.everySetWorthWalkingFor().orElseThrow().stream()
                        .mapToInt(Set::size).max().orElseThrow(),
                "the largest set of blocks all stated to differ is a pair");
        assertInstanceOf(RelationalLack.NoAssignmentTellsThemApart.class,
                refusedBy(made.reduce((_, _) -> new Admits.These(three))),
                "and three values are not enough for it");
        assertInstanceOf(Apartness.Reduction.NotKnown.class,
                made.reduce((_, _) -> new Admits.These(four())),
                "and what four values leave is past what is looked through");
    }

    /**
     * And a block stated to differ from itself is refused whatever it holds.
     *
     * <p>Beside the search rather than inside it. A block holding more values than the relation has
     * blocks is left out of the search, because it can be given a value after every other block has
     * — and that argument holds for a block whose neighbours are other blocks and for no block that
     * is its own. Left out on the count of its values alone, this relation would be a search over
     * nothing, and a search over nothing finds an assignment.
     */
    @Test
    void andABlockStatedToDifferFromItselfIsRefusedHoweverManyValuesItHolds() {
        assertInstanceOf(RelationalLack.ABlockApartFromItself.class,
                refusedBy(Apartness.of("p", "p").reduce((_, _) -> new Admits.MoreThanCounted())));
    }

    /**
     * A block this cannot say the values of is one the relation says nothing about.
     *
     * <p>Both ways round, which is what parts leaving such a block out from leaving out one that
     * holds more values than were counted. An assignment found over the rest is not an assignment
     * over this block, since it may hold no value at all; nothing satisfying the rest is nothing
     * satisfying the whole, since an assignment to every block is an assignment to some of them.
     */
    @Test
    void aBlockWhoseValuesAreNotKnownIsOneTheRelationSaysNothingAbout() {
        assertInstanceOf(Apartness.Reduction.NotKnown.class,
                Apartness.of("p", "r").reduce((_, _) -> new Admits.NotKnown()));

        // A relation the rest of which is satisfiable, so that what is asserted is the block left
        // out and not a refusal reached some other way.
        Apartness<String> chain = Apartness.of("p", "q").and(Apartness.of("q", "r"));
        assertInstanceOf(Apartness.Reduction.NotKnown.class,
                chain.reduce((block, _) -> block.equals(R) ? new Admits.NotKnown()
                        : new Admits.These(Set.of(A, B))),
                "the rest of it stands, and the block nothing wrote down may hold nothing");

        // And nothing satisfying the part that was searched is nothing satisfying the whole.
        Apartness<String> odd = cycleOf(everyOneOf(5)).and(Apartness.of("p0", "z"));
        assertInstanceOf(RelationalLack.NoAssignmentTellsThemApart.class,
                refusedBy(odd.reduce((block, _) -> block.equals(Sameness.Block.of("z"))
                        ? new Admits.NotKnown() : new Admits.These(Set.of(A, B)))),
                "and a cycle beside it is refused whatever the block left out holds");
    }

    /** And a block holding more values than the relation has blocks never runs out. */
    @Test
    void andABlockHoldingMoreValuesThanThereAreBlocksNeverRunsOut() {
        assertInstanceOf(Apartness.Reduction.Standing.class,
                Apartness.of("p", "r").reduce((_, _) -> new Admits.MoreThanCounted()));
    }

    /**
     * And what may still be asked is read off what the rules leave, not off what they started from.
     *
     * <p>One block pinned to a value and ten stated to differ from it, each holding five. As
     * written that is more assignments than are looked through; once the ten have lost the value
     * the pinned one holds, it is not, and the relation is answered.
     *
     * <p>So taking values away is not only what says a lack more nearly than a search can. It makes
     * the search smaller, and a reading that read the shape before it ran would go quiet on a
     * relation it can answer.
     */
    @Test
    void andWhatMayStillBeAskedIsReadOffWhatTheRulesLeave() {
        List<String> around = everyOneOf(10);
        Apartness<String> all = Apartness.nothing();
        for (String each : around) {
            all = all.and(Apartness.of("pinned", each));
        }
        Set<Value> five = five();

        assertInstanceOf(Apartness.Reduction.Standing.class,
                all.reduce((block, _) -> block.equals(Sameness.Block.of("pinned"))
                        ? new Admits.These(Set.of(A)) : new Admits.These(five)),
                "the ten are left four values apiece, which is a search this makes");

        // The same relation with nothing pinned, so that what changed is the narrowing and not the
        // blocks or the pairs.
        assertInstanceOf(Apartness.Reduction.NotKnown.class,
                all.reduce((_, _) -> new Admits.These(five)),
                "and five apiece is more assignments than are looked through");
    }

    /**
     * And a relation of one assignment over many blocks is one this says nothing about.
     *
     * <p>A run of blocks each stated to differ from the next and each left one value, alternating,
     * so that taking values away finds nothing to take. Its blocks' values come to a single
     * assignment between them however long the run is, so how many assignments there are says
     * nothing about what looking through them costs — the looking is a step per block and the
     * making of the question is the square of them.
     *
     * <p>Which is why the search is bounded by two figures. Bounded by the assignments alone, this
     * is a relation of one assignment and of as many blocks as anybody writes.
     */
    @Test
    void andARelationOfOneAssignmentOverManyBlocksIsUnanswered() {
        // One block between the two, and their assignments are the same number: each block holds
        // one value, so what the answer turns on is how many blocks there are and nothing else.
        assertInstanceOf(Apartness.Reduction.NotKnown.class, runOf(65).reduce(this::alternating),
                "one assignment, and one block more than are stepped through");
        assertInstanceOf(Apartness.Reduction.Standing.class, runOf(64).reduce(this::alternating),
                "and one block fewer is answered");
    }

    /** A run of {@code many} blocks, each stated to differ from the next. */
    private static Apartness<String> runOf(int many) {
        List<String> named = everyOneOf(many);
        Apartness<String> all = Apartness.nothing();
        for (int each = 0; each + 1 < many; each++) {
            all = all.and(Apartness.of(named.get(each), named.get(each + 1)));
        }
        return all;
    }

    /** One value apiece, alternating along the run, which is what nothing can take a value from. */
    private Admits alternating(Sameness.Block<String> block, int atMost) {
        String named = block.members().iterator().next();
        return new Admits.These(Set.of(
                Integer.parseInt(named.substring(1)) % 2 == 0 ? A : B));
    }

    /**
     * And how large the relation is reaches the search as well as the count.
     *
     * <p>A search over two blocks is a small question however large the relation holding them, and
     * making it reads every pair the relation has. So the figure bounding what may be read at all
     * is asked of both readers — asked of the count alone, a relation of any size could be read
     * through by leaving all but two of its blocks holding more values than were counted.
     *
     * <p>Asserted of the shape rather than of a relation that size. Building one costs the square
     * of its pairs, which is minutes at the figure this is about, and what the assertion is of is
     * the question the shape is asked.
     */
    @Test
    void andHowLargeTheRelationIsReachesTheSearchAsWellAsTheCount() {
        // One pair between the two, and nothing else between them: the same blocks, so what the
        // answer turns on is how many pairs there are and not how many blocks. Compared against a
        // shape of another block count, this would pass just as well against a bound on the blocks,
        // which is a different rule that happens to answer these two the same way.
        Apartness.Extent within = new Apartness.Extent(101, 5000);
        Apartness.Extent past = new Apartness.Extent(101, 5001);

        assertTrue(within.isSmallEnoughToRead(), "as many pairs as may be read");
        assertFalse(past.isSmallEnoughToRead(), "and one more than that");

        // And the search is not made of the second, asked at the seam the relation's size arrives
        // through. Two blocks holding two values apiece is a question inside every figure of its
        // own, so what is left to refuse it is how large the relation those blocks are part of is.
        java.util.Map<Sameness.Block<String>, Set<Value>> two =
                java.util.Map.of(P, Set.of(A, B), Q, Set.of(A, B));
        assertTrue(TellingApart.lookingThrough(within, two, _ -> Set.of()).isPresent(),
                "a search over two blocks is a small question and there is one to ask");
        assertTrue(TellingApart.lookingThrough(past, two, _ -> Set.of()).isEmpty(),
                "and one pair more in the relation holding them leaves none, because making the"
                        + " question reads every pair however small the question is");
    }

    /**
     * And what a relation all of whose pairs are stated may be, which is where that figure bites.
     *
     * <p>Not one pair apart, because there is no such pair to compare against: at this many blocks
     * every relation leaving few enough pairs out to be counted for that reason has more pairs than
     * may be read, and every relation with few enough pairs to be read leaves too many out. What
     * the two figures come to together is a size of relation all of whose pairs are stated, and
     * that is what is asserted rather than an isolation there is none of.
     */
    @Test
    void andTheLargestRelationAllOfWhosePairsAreStatedThatIsCounted() {
        assertTrue(new Apartness.Extent(100, 100 * 99 / 2).admitsCounting(),
                "every block against every other, and few enough pairs to read");
        assertFalse(new Apartness.Extent(101, 101 * 100 / 2).admitsCounting(),
                "and one block more is more pairs than may be read");
    }

    /**
     * And a relation with more assignments than are looked through is one this says nothing about.
     *
     * <p>One relation and one value between the two halves. Every block holds two values, which is
     * as many assignments as are looked through; then one block holds a third, which is more.
     * Nothing else moves — the same blocks, the same pairs — so what the answer turns on is the
     * assignments and not how many blocks there are or how large the relation is.
     *
     * <p>And they are one value apart, which is what holds the figure to what it is. A third value
     * given to every block would be past the bound too, and so would a relation of twice the
     * blocks; asserted that way, the case would pass for any figure between the two, and what it
     * says is that there is one somewhere.
     */
    @Test
    void andARelationWithMoreAssignmentsThanAreLookedThroughIsUnanswered() {
        Apartness<String> even = cycleOf(everyOneOf(20));
        Set<Value> two = Set.of(A, B);
        Set<Value> three = new LinkedHashSet<>(two);
        three.add(C);

        assertInstanceOf(Apartness.Reduction.Standing.class,
                even.reduce((_, _) -> new Admits.These(two)),
                "two values apiece over these blocks is as many assignments as are looked through");
        assertInstanceOf(Apartness.Reduction.NotKnown.class,
                even.reduce((block, _) -> new Admits.These(block.equals(P0) ? three : two)),
                "and one value more at one block is one assignment too many");
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
