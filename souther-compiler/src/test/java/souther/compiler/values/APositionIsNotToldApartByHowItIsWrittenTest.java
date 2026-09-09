package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a position is called is the caller's, and how it is written says nothing about which one it
 * is.
 *
 * <p>A rendering is not an order over positions and not an identity for them. The types this is
 * instantiated at say so themselves — {@code FactSubject#rendered} is "for a reader, not for
 * equality", and an evaluation carries a position that "takes no part in telling two apart" — so a
 * relation reading the spelling would be reading something its own positions have told it not to.
 *
 * <p>Asked with two positions written the same way, because that is the case a spelling cannot
 * answer and the one every rule below has to hold for anyway. A test whose positions all render
 * differently is one an ordering by rendering passes.
 */
class APositionIsNotToldApartByHowItIsWrittenTest {

    /**
     * A position written the same way as another and equal to none of them.
     *
     * <p>Equality is identity, which is what a value nothing may share is. The rendering is fixed
     * so that no two of these can be told apart by it, which is the whole of what this is for.
     */
    private static final class Alike {

        private final String written;

        private Alike(String written) {
            this.written = written;
        }

        @Override
        public String toString() {
            return written;
        }
    }

    private static final Alike ONE = new Alike("x");
    private static final Alike OTHER = new Alike("x");
    private static final Alike THIRD = new Alike("x");

    /** And one written differently, so that a block holding it with the others has something an
     *  order over renderings can move. Without it every arrangement writes the same string
     *  whether or not anything was put in an order. */
    private static final Alike APART = new Alike("a");

    /** Two of them are two positions, however one reads. */
    @Test
    void twoPositionsWrittenAlikeAreTwoPositions() {
        assertNotEquals(ONE, OTHER);
        assertEquals(String.valueOf(ONE), String.valueOf(OTHER));
        assertEquals(2, Sameness.of(ONE, OTHER).blockOf(ONE).members().size());
    }

    /**
     * A block is its positions, so the same ones gathered in any order are one block.
     *
     * <p>Over every order they can be gathered in and not over two of them. What this is about is
     * that no order decides anything, and a property over permutations is the only way to say it —
     * a pair of cases is a pair of cases, and the ordering this replaced passed one of them.
     */
    @Test
    void aBlockIsOneBlockHoweverItsPositionsWereGathered() {
        Sameness.Block<Alike> first = null;
        for (List<Alike> order : permutations(List.of(ONE, OTHER, APART))) {
            Sameness.Block<Alike> made = Sameness.Block.of(new LinkedHashSet<>(order));
            if (first == null) {
                first = made;
            }
            assertEquals(first, made, "a block is equal by its positions");
            assertEquals(first.hashCode(), made.hashCode(), "and hashes by them");
            assertEquals(String.valueOf(first), String.valueOf(made), "and reads by them");
            assertTrue(made.holds(APART), "and holds every one of them");
        }
        assertEquals("[a, x, x]", String.valueOf(first),
                "written in the order the renderings fall in, both of the alike ones kept");
    }

    /**
     * And a relation is one relation however its equalities were reached.
     *
     * <p>Which is the promise the ordering was written for. It is kept by putting the renderings in
     * an order where the relation is written out, and there two that are alike are two entries of
     * one string rather than two positions in an order that decides nothing.
     */
    @Test
    void aRelationReadsTheSameHoweverItsEqualitiesWereReached() {
        Sameness<Alike> here = Sameness.of(ONE, OTHER).joining(OTHER, APART);
        Sameness<Alike> there = Sameness.of(APART, OTHER).joining(OTHER, ONE);

        assertEquals(here, there);
        assertEquals(String.valueOf(here), String.valueOf(there));
        assertEquals("[[a, x, x]]", String.valueOf(here),
                "and the two written alike are written twice and not folded to one");
    }

    /** And two blocks of one relation that read alike are two blocks. */
    @Test
    void twoBlocksThatReadAlikeAreTwoBlocks() {
        Sameness<Alike> two = Sameness.of(ONE, OTHER).joining(THIRD, new Alike("x"));

        assertEquals(2, two.joined().size());
        assertEquals("[[x, x], [x, x]]", String.valueOf(two),
                "and both of them are written, since what is put in an order is what is written");
    }

    /** A block of one is written as the position itself, which two of them being alike does not
     *  make one. */
    @Test
    void aBlockOfOnePositionIsWrittenAsThatPosition() {
        assertEquals("x", String.valueOf(Sameness.Block.of(ONE)));
        assertNotEquals(Sameness.Block.of(ONE), Sameness.Block.of(OTHER));
        assertFalse(Sameness.Block.of(ONE).holds(OTHER));
    }

    /** Every order these can be gathered in. */
    private static <T> List<List<T>> permutations(List<T> these) {
        if (these.size() <= 1) {
            return List.of(these);
        }
        List<List<T>> out = new ArrayList<>();
        for (int at = 0; at < these.size(); at++) {
            List<T> rest = new ArrayList<>(these);
            T taken = rest.remove(at);
            for (List<T> tail : permutations(rest)) {
                List<T> made = new ArrayList<>();
                made.add(taken);
                made.addAll(tail);
                out.add(made);
            }
        }
        return out;
    }

    /** That the fake above is the case this is about: a set of them keeps all three. */
    @Test
    void theseArePositionsASpellingCannotTellApart() {
        Set<Alike> all = new LinkedHashSet<>(List.of(ONE, OTHER, THIRD));

        assertEquals(3, all.size());
        assertEquals(1, all.stream().map(String::valueOf).distinct().count());
    }
}
