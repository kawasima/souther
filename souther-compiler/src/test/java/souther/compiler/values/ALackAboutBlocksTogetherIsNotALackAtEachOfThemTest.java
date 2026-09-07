package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
        return new Refusal.OfThemTogether<>(Lacks.of(new RelationalLack.TooFewValuesBetweenThem<>(
                Set.of(one, other), Set.of(Value.text("A")))));
    }

    /** Two lacks at blocks are one lack at the blocks both of them name. */
    @Test
    void twoLacksAtBlocksKeepWhatBothOfThemName() {
        Refusal<String> both = Refusal.shownByBoth(
                new Refusal.AtEachOf<>(Set.of(P, Q)), new Refusal.AtEachOf<>(Set.of(Q, R)));

        assertEquals(Set.of(Q), assertInstanceOf(Refusal.AtEachOf.class, both).blocks());
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

        assertInstanceOf(Refusal.Nowhere.class, both);
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

        assertEquals(Set.of(P, Q), one.blocks(), "the route is what a report may name");
        assertEquals(Set.of(Q, R), other.blocks());

        Refusal<String> both = Refusal.shownByBoth(one, other);

        assertEquals(Set.of(new RelationalLack.NoValueLeftForIt<>(Q)),
                assertInstanceOf(Refusal.OfThemTogether.class, both).lacks().claimed());
        assertEquals(Set.of(P, Q, R), both.blocks(),
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
                assertInstanceOf(Refusal.OfThemTogether.class, both).lacks().claimed());
        assertEquals(Set.of(P, Q, R, s), both.blocks(),
                "and each block is named beside what took its values");
    }

    /** {@code left} holding nothing, reached by {@code by} being left one value in an earlier
     *  round. */
    private static Refusal<String> leaving(Sameness.Block<String> left,
                                           Sameness.Block<String> by) {
        return new Refusal.OfThemTogether<>(
                Lacks.of(new RelationalLack.NoValueLeftForIt<>(left),
                        RelationalEvidence.of(new Provenance<>(Set.of(new Provenance.Removal<>(
                                left, Value.text("A"), 1, Set.of(by)))))));
    }

    /** A lack about blocks together and a lack at blocks are not one another, whatever they name. */
    @Test
    void andALackAtBlocksSaysNothingAboutALackAboutThemTogether() {
        assertInstanceOf(Refusal.Nowhere.class,
                Refusal.shownByBoth(together(P, Q), new Refusal.AtEachOf<>(Set.of(P, Q))));
        assertInstanceOf(Refusal.Nowhere.class,
                Refusal.shownByBoth(new Refusal.AtEachOf<>(Set.of(P, Q)), together(P, Q)));
    }
}
