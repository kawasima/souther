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
        return new Refusal.OfThemTogether<>(new RelationalWitness.TooFewValuesBetweenThem<>(
                Set.of(one, other), Set.of(Value.text("A"))));
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

    /** A lack about blocks together and a lack at blocks are not one another, whatever they name. */
    @Test
    void andALackAtBlocksSaysNothingAboutALackAboutThemTogether() {
        assertInstanceOf(Refusal.Nowhere.class,
                Refusal.shownByBoth(together(P, Q), new Refusal.AtEachOf<>(Set.of(P, Q))));
        assertInstanceOf(Refusal.Nowhere.class,
                Refusal.shownByBoth(new Refusal.AtEachOf<>(Set.of(P, Q)), together(P, Q)));
    }
}
