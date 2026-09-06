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

    /**
     * A choice merged into one product keeps what every alternative states.
     *
     * <p>Merging a union into the smallest product containing it widens what the blocks hold. It
     * does not licence forgetting a rule both branches wrote — and a denial dropped there is one no
     * equality read beside the choice can be refused against, so a declaration nothing satisfies
     * comes back admitted.
     *
     * <p>Asked of the reading whose values are still descriptions as well as of the one whose
     * values are sets. Both merge, both have to carry it, and which of the two a compilation takes
     * is settled by how large the choice is rather than by anything a model says.
     */
    @Test
    void aChoiceMergedIntoOneProductKeepsWhatEveryAlternativeStates() {
        Allowance<String> sets = AsACompilationAllows.forAdmittedValues();

        AdmissibleValues<String> merged = AdmissibleValues.<String>heldApart("p", "r")
                .join(AdmissibleValues.heldApart("p", "r"), sets);
        assertTrue(merged.meet(AdmissibleValues.holdingAsOne("p", "r"), sets).isBottom(),
                "the choice states it, so an equality read beside it refuses");

        PlannedValues<String> planned = PlannedValues.<String>heldApart("p", "r")
                .joinLive(PlannedValues.heldApart("p", "r"));
        assertTrue(planned.meet(PlannedValues.holdingAsOne("p", "r"))
                        .anyAlternativeAdmits((_, _) -> Emptiness.NONEMPTY) == Emptiness.EMPTY,
                "and the same of a reading whose values are still descriptions");
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
     * another order is that same set again. Both are a count taken more often than there are
     * answers, and how much walking a relation is worth is settled by how many blocks it has rather
     * than by how many roads to one set the walk chose to take.
     */
    @Test
    void theSetsCountedAreTheOnesNothingCanBeAddedTo() {
        Apartness<String> triangle = Apartness.of("p", "q")
                .and(Apartness.of("q", "r")).and(Apartness.of("r", "p"));

        assertEquals(List.of(Set.of(P, Q, R)), triangle.everyPairwiseApartSet(),
                "one set, and not every part of it nor every order its blocks come in");

        Apartness<String> chain = Apartness.of("p", "q").and(Apartness.of("q", "r"));
        assertEquals(List.of(Set.of(P, Q), Set.of(Q, R)), chain.everyPairwiseApartSet(),
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

        assertEquals(1, all.everyPairwiseApartSet().size(),
                "one set, and not every part of it nor every order its blocks come in");
        assertInstanceOf(RelationalWitness.TooFewValuesBetweenThem.class,
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
     * <p>Which is the whole reason the shape is asked two questions rather than one. Admitted by
     * how many blocks it has alone, this relation would be the easy one the reading went quiet on.
     */
    @Test
    void andBlocksAllStatedToDifferAreCountedHoweverManyOfThemThereAre() {
        Apartness<String> all = allApart(everyOneOf(34));

        assertTrue(all.extent().isComplete(), "every block is stated to differ from every other");
        assertInstanceOf(RelationalWitness.TooFewValuesBetweenThem.class,
                refusedBy(all.reduce(holding(java.util.Map.of()))));
    }

    /**
     * And a relation neither of those admits is one this says nothing about.
     *
     * <p>All apart but for one pair, which is a shape the count refuses — thirty-three of its
     * blocks are stated to differ from each other and two values are not enough for them. It is not
     * every block against every other, so the shape it is admitted by is how many blocks it has,
     * and it has more than that allows.
     *
     * <p>Written down as the boundary rather than as a gap. The same shape one block smaller is
     * refused, so what is asserted here is where this stops and not that it cannot count: a bound
     * nothing shows the far side of is a bound nobody can tell from an accident.
     */
    @Test
    void andARelationPastBothOfThoseIsOneThisSaysNothingAbout() {
        List<String> named = everyOneOf(31);
        Apartness<String> past = allApartBut(named, named.get(0), named.get(1));
        List<String> fewer = everyOneOf(30);
        Apartness<String> within = allApartBut(fewer, fewer.get(0), fewer.get(1));

        assertFalse(past.extent().isComplete(), "one pair is not stated to differ");
        assertInstanceOf(Apartness.Reduction.NotKnown.class, past.reduce(holding(
                java.util.Map.of())), "past what either question admits, and so unanswered");

        assertFalse(within.extent().isComplete(), "the same shape, one block smaller");
        assertInstanceOf(RelationalWitness.TooFewValuesBetweenThem.class,
                refusedBy(within.reduce(holding(java.util.Map.of()))),
                "and inside the bound the same count refuses it");
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
        return allApartBut(named, null, null);
    }

    /** Every pair of {@code named} stated to differ, less the one pair {@code but} names. */
    private static Apartness<String> allApartBut(List<String> named, String one, String other) {
        Apartness<String> all = Apartness.nothing();
        for (int first = 0; first < named.size(); first++) {
            for (int second = first + 1; second < named.size(); second++) {
                if (named.get(first).equals(one) && named.get(second).equals(other)) {
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

        assertInstanceOf(RelationalWitness.NoAssignmentTellsThemApart.class,
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

        assertEquals(2, made.everyPairwiseApartSet().getFirst().size(),
                "the largest set of blocks all stated to differ is a pair");
        assertInstanceOf(RelationalWitness.NoAssignmentTellsThemApart.class,
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
        assertInstanceOf(RelationalWitness.ABlockApartFromItself.class,
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
        assertInstanceOf(RelationalWitness.NoAssignmentTellsThemApart.class,
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
     * And a relation with more assignments than are looked through is one this says nothing about.
     *
     * <p>A cycle of odd length again, so that what changes between the two halves is how many
     * values its blocks hold and nothing else: the same relation is refused where its assignments
     * can be looked through and unanswered where they cannot.
     *
     * <p>Which is what makes the bound a bound and not an accident. Asserted only past it, the case
     * would pass just as well if the search had stopped working — and asserted only inside it,
     * nothing would say where the reading stops.
     */
    @Test
    void andARelationWithMoreAssignmentsThanAreLookedThroughIsUnanswered() {
        Apartness<String> odd = cycleOf(everyOneOf(19));
        Set<Value> two = Set.of(A, B);
        Set<Value> three = new LinkedHashSet<>(two);
        three.add(Value.text("C"));

        assertInstanceOf(RelationalWitness.NoAssignmentTellsThemApart.class,
                refusedBy(odd.reduce((_, _) -> new Admits.These(two))),
                "two values apiece over these blocks is a search this is allowed to make");
        assertInstanceOf(Apartness.Reduction.NotKnown.class,
                odd.reduce((_, _) -> new Admits.These(three)),
                "and three apiece is more assignments than it looks through");
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
