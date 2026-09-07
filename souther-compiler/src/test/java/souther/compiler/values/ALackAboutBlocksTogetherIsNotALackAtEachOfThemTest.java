package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two readings both left nothing, put together.
 *
 * <p>A lack at each of some blocks says of every one of them that it holds nothing, so two of them
 * agree about the blocks they both name. A lack about several blocks together says that no
 * assignment to all of them stands, while each of them is left values of its own — and two of those
 * over sets that overlap have shown nothing about the overlap. Held as one set of blocks, the
 * second would be intersected like the first and a proof would claim a lack neither reading showed.
 */
class ALackAboutBlocksTogetherIsNotALackAtEachOfThemTest {

    private static final Sameness.Block<String> P = Sameness.Block.of("p");
    private static final Sameness.Block<String> Q = Sameness.Block.of("q");
    private static final Sameness.Block<String> R = Sameness.Block.of("r");

    private static Refusal<String> together(Sameness.Block<String> one,
                                            Sameness.Block<String> other) {
        return Refusal.ofThemTogether(Lacks.of(new RelationalLack.TooFewValuesBetweenThem<>(
                Set.of(one, other), Set.of(Value.text("A")))));
    }

    /** Two lacks at blocks are one lack at the blocks both of them name. */
    @Test
    void twoLacksAtBlocksKeepWhatBothOfThemName() {
        Refusal<String> both = Refusal.shownByBoth(
                Refusal.atEachOf(Set.of(P, Q)), Refusal.atEachOf(Set.of(Q, R)));

        assertEquals(Set.of(Q), both.atEachOf());
    }

    /**
     * And two lacks about blocks together over sets that overlap keep nothing.
     *
     * <p>Neither of them says that {@code q} holds nothing — each says that its own blocks cannot
     * all be told apart, and {@code q} is left values of its own in both. Intersected, the answer
     * would be that the rules leave {@code q} nothing, which is a sentence neither reading showed.
     */
    @Test
    void andTwoLacksAboutBlocksTogetherKeepNothingOfWhatTheyShare() {
        Refusal<String> both = Refusal.shownByBoth(together(P, Q), together(Q, R));

        assertTrue(both.isNowhere());
    }

    /** Two of them that are the same lack are that lack, since both readings showed the one thing. */
    @Test
    void andTwoOfThemThatAreOneLackAreThatLack() {
        assertEquals(together(P, Q), Refusal.shownByBoth(together(P, Q), together(P, Q)));
    }

    /**
     * And two readings that leave one block nothing have shown that, whatever took its values.
     *
     * <p>One reading's rules take {@code q}'s values through {@code p} and the other's through
     * {@code r}, so the two are reached along different routes and neither route is the other's.
     * But each of them leaves {@code q} no value, and a choice between them leaves {@code q} no
     * value — which is what both showed.
     *
     * <p>Asked of the refusals whole, the two would be different wherever their routes were, and
     * the choice would be reported as refused nowhere in particular: a reading holding something
     * where both of its alternatives hold nothing.
     *
     * <p><b>And both routes are kept.</b> An author fixing this has to answer for the rules of
     * either alternative, since the block is left nothing whichever of them is read. Kept as the
     * one route the two agree on, there would be none — and the report would name a block whose
     * own rules leave it everything.
     */
    @Test
    void andTwoReadingsThatLeaveOneBlockNothingHaveShownThatHoweverTheyReachedIt() {
        Refusal<String> one = leaving(Q, P);
        Refusal<String> other = leaving(Q, R);

        assertEquals(Set.of(P, Q), one.together().blocks(),
                "the route is what a report may name");
        assertEquals(Set.of(Q, R), other.together().blocks());

        Refusal<String> both = Refusal.shownByBoth(one, other);

        assertEquals(Set.of(new RelationalLack.NoValueLeftForIt<>(Q)),
                both.together().claimed());
        assertEquals(Set.of(P, Q, R), both.together().blocks(),
                "and what may be named is the block and the rules of either reading");
    }

    /**
     * And a conjunction refused on both sides keeps each side's lack with the rules that reached it.
     *
     * <p>One side leaves {@code p} nothing through {@code q}; the other leaves {@code r} nothing
     * through {@code s}. Both are true of the conjunction, and each is true through its own rules —
     * so what a report may name is the two blocks and the two lots of rules that emptied them.
     *
     * <p>Which is what a refusal carrying one route for all its lacks cannot say. Two routes that
     * are not each other's would have to be dropped, and the report would name two blocks whose own
     * rules leave them everything.
     */
    @Test
    void andAConjunctionRefusedOnBothSidesKeepsEachSidesRoute() {
        Sameness.Block<String> s = Sameness.Block.of("s");

        Refusal<String> both = Refusal.eitherShown(leaving(P, Q), leaving(R, s));

        assertEquals(Set.of(new RelationalLack.NoValueLeftForIt<>(P),
                        new RelationalLack.NoValueLeftForIt<>(R)),
                both.together().claimed());
        assertEquals(Set.of(P, Q, R, s), both.together().blocks(),
                "and each block is named beside what took its values");
    }

    /**
     * How refusals are put together does not turn on how they were bracketed.
     *
     * <p>A conjunction of three sides is refused by what the three showed, and which two of them
     * were put together first is a fact about how somebody wrote it down. The same holds of a
     * choice between three readings.
     *
     * <p>Which is what a refusal holding both of the things it can be is for. Held as one of them,
     * a side refused at a block beside a side refused about blocks together had to give one up —
     * and giving up either leaves an answer that is not the same when the first two are put
     * together first as when the last two are, whichever of the two is given up.
     */
    @Test
    void howTheseArePutTogetherDoesNotTurnOnHowTheyWereBracketed() {
        List<Refusal<String>> these = List.of(Refusal.nowhere(), Refusal.atEachOf(Set.of(P)),
                Refusal.atEachOf(Set.of(Q)), together(P, Q), together(Q, R),
                leaving(Q, P), Refusal.eitherShown(Refusal.atEachOf(Set.of(R)), together(P, Q)));

        for (Refusal<String> one : these) {
            for (Refusal<String> other : these) {
                assertEquals(Refusal.eitherShown(one, other), Refusal.eitherShown(other, one));
                assertEquals(Refusal.shownByBoth(one, other), Refusal.shownByBoth(other, one));
                assertEquals(one, Refusal.eitherShown(one, one));
                assertEquals(one, Refusal.shownByBoth(one, one));
                assertEquals(one, Refusal.eitherShown(one, Refusal.nowhere()));
                for (Refusal<String> third : these) {
                    assertEquals(
                            Refusal.eitherShown(Refusal.eitherShown(one, other), third),
                            Refusal.eitherShown(one, Refusal.eitherShown(other, third)),
                            "what three conjoined sides showed");
                    assertEquals(
                            Refusal.shownByBoth(Refusal.shownByBoth(one, other), third),
                            Refusal.shownByBoth(one, Refusal.shownByBoth(other, third)),
                            "what three readings all showed");
                }
            }
        }
    }

    /** {@code left} holding nothing, reached by {@code by} being left one value in an earlier
     *  round. */
    private static Refusal<String> leaving(Sameness.Block<String> left,
                                           Sameness.Block<String> by) {
        return Refusal.ofThemTogether(
                Lacks.of(new RelationalLack.NoValueLeftForIt<>(left),
                        RelationalEvidence.of(new Provenance<>(Set.of(new Provenance.Removal<>(
                                left, Value.text("A"), 1, Set.of(by)))))));
    }

    /**
     * And two readings of the largest relation a count is taken of are put together by asking one
     * lack apiece.
     *
     * <p>Thirty blocks in groups of three, every pair across the groups stated to differ and none
     * within one — which is the shape that has the most sets of blocks all stated to differ that
     * the count is admitted for, one for each way of taking a block from each group. Two values
     * apiece leaves every one of those sets short, so what the count shows is a lack for each of
     * them.
     *
     * <p>Put together by walking, that is every lack of one reading against every lack of the
     * other, and this relation is inside what a declaration may ask for. So a lack says where it is
     * filed ({@link RelationalLack#scattering}) and two readings are put together by asking: the
     * sets of blocks one relation is short of are drawn from the same few blocks, and a number that
     * is their sum is one nearly all of them share.
     *
     * <p>Held by being here rather than by a figure. A run that asked this by walking takes long
     * enough to be the whole of what the suite costs, which is what a reader of a broken one sees.
     */
    @Test
    void andTwoReadingsOfTheLargestCountedRelationArePutTogetherByAsking() {
        Apartness<String> partite = Apartness.nothing();
        for (int one = 0; one < 30; one++) {
            for (int other = one + 1; other < 30; other++) {
                if (one / 3 != other / 3) {
                    partite = partite.and(Apartness.of("p" + one, "p" + other));
                }
            }
        }
        Apartness.WhatABlockAdmits<String> two =
                (_, _) -> new Admits.These(Set.of(Value.text("A"), Value.text("B")));

        Apartness.Reduction<String> said = partite.reduce(two);
        assertInstanceOf(Apartness.Reduction.Nothing.class, said);
        Lacks<String> lacks = ((Apartness.Reduction.Nothing<String>) said).lacks();

        assertEquals(partite.everySetWorthWalkingFor().orElseThrow().size(), lacks.size(),
                "every set the count was taken of is short, and each of them is its own lack");
        assertEquals(Refusal.ofThemTogether(lacks),
                Refusal.shownByBoth(Refusal.ofThemTogether(lacks),
                        Refusal.ofThemTogether(lacks)),
                "and two readings that show them all show them all");
    }

    /** A lack about blocks together and a lack at blocks are not one another, whatever they name. */
    @Test
    void andALackAtBlocksSaysNothingAboutALackAboutThemTogether() {
        assertTrue(Refusal.shownByBoth(together(P, Q), Refusal.atEachOf(Set.of(P, Q))).isNowhere());
        assertTrue(Refusal.shownByBoth(Refusal.atEachOf(Set.of(P, Q)), together(P, Q)).isNowhere());
    }
}
