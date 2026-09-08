package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A block is asked what it is called far more often than one is made, and what it answers says
 * which positions are in it rather than only which positions there are.
 *
 * <p>A relation's answers are filed under blocks, so a round of a narrowing reads a map of them for
 * every block it walks and a reading asks which block a position is on for every position it reads.
 * Worked out from the members on each of those, the answer is a walk of the set for a value that
 * cannot change — which is what the first of these is about.
 *
 * <p>The rest are about what that answer is. A set's hash is the sum of its members', so a block
 * that answered with that sum and nothing else would be one whose name adds up the same however its
 * positions are grouped: a set of blocks would come to the sum over every position in all of them,
 * and the grouping a block exists to state would be the one thing its name leaves out. These do not
 * ask that two blocks never share a name — nothing can promise that of an {@code int} — but that
 * regrouping the same positions is not a thing the name is arranged to lose.
 */
class WhatABlockIsCalledIsWorkedOutOnceAndKeepsItsGroupingTest {

    private static final Counted P = new Counted("p", 1);
    private static final Counted Q = new Counted("q", 2);
    private static final Counted R = new Counted("r", 3);

    /** Asked again and again, a block reads nothing of its members. */
    @Test
    void aBlockIsWorkedOutWhereItIsMadeAndNotWhereItIsAsked() {
        Sameness.Block<Counted> block = Sameness.Block.of(members(P, Q));
        long made = Counted.asked;

        int name = block.hashCode();
        for (int again = 0; again < 100; again++) {
            assertEquals(name, block.hashCode(), "a block gave two answers about one set");
        }

        assertEquals(made, Counted.asked, "a block asked its name read its members again");
    }

    /** The same positions found in either order are one block, called one thing. */
    @Test
    void andTheOrderItsPositionsArrivedInIsNotPartOfWhatItIsCalled() {
        Sameness.Block<Counted> one = Sameness.Block.of(members(P, Q));
        Sameness.Block<Counted> back = Sameness.Block.of(members(Q, P));

        assertEquals(one, back);
        assertEquals(one.hashCode(), back.hashCode());
    }

    /** A block of one position, however it was asked for. */
    @Test
    void andAPositionOnItsOwnIsOneBlockWhicheverWayItWasAskedFor() {
        assertEquals(Sameness.Block.of(P), Sameness.Block.of(members(P)));
        assertEquals(Sameness.Block.of(P).hashCode(), Sameness.Block.of(members(P)).hashCode());
    }

    /**
     * The same three positions, grouped two ways.
     *
     * <p>Summed and left, these two are one number whatever the positions are: both come to what
     * the three of them come to. So this is the shape a name that says nothing about grouping
     * loses, and it is here to keep the name from being arranged that way again.
     */
    @Test
    void andTwoSetsOfBlocksOverThePositionsAreNotOneNumberByArithmetic() {
        Set<Sameness.Block<Counted>> apart =
                Set.of(Sameness.Block.of(P), Sameness.Block.of(members(Q, R)));
        Set<Sameness.Block<Counted>> together =
                Set.of(Sameness.Block.of(members(P, Q)), Sameness.Block.of(R));

        assertNotEquals(apart.hashCode(), together.hashCode(),
                "the same positions grouped two ways came to one number");
    }

    private static Set<Counted> members(Counted... these) {
        Set<Counted> out = new LinkedHashSet<>();
        for (Counted each : these) {
            out.add(each);
        }
        return out;
    }

    /** A position that says how often it was asked what it is called. */
    private static final class Counted {

        private static long asked;

        private final String name;
        private final int hash;

        private Counted(String name, int hash) {
            this.name = name;
            this.hash = hash;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Counted it && name.equals(it.name) && hash == it.hash;
        }

        @Override
        public int hashCode() {
            asked++;
            return hash;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
