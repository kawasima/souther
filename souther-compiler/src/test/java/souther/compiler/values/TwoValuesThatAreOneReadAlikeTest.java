package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * What a value about a relation holds no order of is not an order it shows.
 *
 * <p>These values keep what they hold in the order it arrived, so that what a compilation writes
 * out comes out the same on two runs of it. That order is a fact about how one was built, and two
 * of them that are equal were built two ways — so a value that showed it would be showing what its
 * own equality says it is not, and a reader holding two equal values would see two different
 * things.
 *
 * <p>Asked of each of them built two ways round, because the boundary is one every value of this
 * kind stands on. A rule kept in one head is one the next value added beside these is written
 * without.
 */
class TwoValuesThatAreOneReadAlikeTest {

    private static final Sameness.Block<String> P = Sameness.Block.of("p");
    private static final Sameness.Block<String> Q = Sameness.Block.of("q");
    private static final Sameness.Block<String> R = Sameness.Block.of("r");

    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    /** Two blocks stated to differ, and the same pair stated the other way round. */
    @Test
    void aRelationReadsTheSameWhicheverWayItsPairsWereStated() {
        assertSame(Apartness.of("p", "q").and(Apartness.of("q", "r")),
                Apartness.of("q", "r").and(Apartness.of("p", "q")));
    }

    /** What the blocks are left, handed over in either order. */
    @Test
    void andAReadingReadsTheSameWhicheverOrderItsBlocksArrivedIn() {
        Map<Sameness.Block<String>, Admits> one = new LinkedHashMap<>();
        one.put(P, new Admits.These(Set.of(A)));
        one.put(Q, new Admits.These(Set.of(B)));
        Map<Sameness.Block<String>, Admits> back = new LinkedHashMap<>();
        back.put(Q, new Admits.These(Set.of(B)));
        back.put(P, new Admits.These(Set.of(A)));

        assertSame(new Domains<>(one), new Domains<>(back));
    }

    /** A lack about blocks, named in either order. */
    @Test
    void andALackReadsTheSameWhicheverOrderItsBlocksWereNamedIn() {
        assertSame(new RelationalLack.TooFewValuesBetweenThem<>(ordered(P, Q, R), ordered(A, B)),
                new RelationalLack.TooFewValuesBetweenThem<>(ordered(R, Q, P), ordered(B, A)));
        assertSame(new RelationalLack.NoAssignmentTellsThemApart<>(ordered(P, Q)),
                new RelationalLack.NoAssignmentTellsThemApart<>(ordered(Q, P)));
    }

    /** The removals of a narrowing, and what blocked one of them. */
    @Test
    void andARouteReadsTheSameWhicheverOrderItsRemovalsWereMadeIn() {
        Provenance.Removal<String> one = new Provenance.Removal<>(P, A, 1, ordered(Q, R));
        Provenance.Removal<String> back = new Provenance.Removal<>(P, A, 1, ordered(R, Q));
        assertSame(one, back);

        Provenance.Removal<String> other = new Provenance.Removal<>(Q, B, 2, ordered(P));
        assertSame(new Provenance<>(ordered(one, other)), new Provenance<>(ordered(other, back)));
    }

    /** What an argument showed, and how each of them was reached. */
    @Test
    void andWhatAnArgumentShowedReadsTheSameWhicheverOrderItReachedThemIn() {
        Shown<String> one = Shown.of(new RelationalLack.NoValueLeftForIt<>(P));
        Shown<String> other = Shown.of(new RelationalLack.NoValueLeftForIt<>(Q));

        assertSame(Lacks.of(List.of(one, other)), Lacks.of(List.of(other, one)));

        RelationalEvidence<String> both = RelationalEvidence
                .of(new Provenance<>(ordered(new Provenance.Removal<>(P, A, 1, ordered(Q)))))
                .and(RelationalEvidence.of(new Provenance<>(
                        ordered(new Provenance.Removal<>(P, B, 1, ordered(R))))));
        RelationalEvidence<String> back = RelationalEvidence
                .of(new Provenance<>(ordered(new Provenance.Removal<>(P, B, 1, ordered(R)))))
                .and(RelationalEvidence.of(new Provenance<>(
                        ordered(new Provenance.Removal<>(P, A, 1, ordered(Q))))));
        assertSame(both, back);
    }

    /** And where a narrowing left blocks nothing. */
    @Test
    void andWhereANarrowingLeftBlocksNothingReadsTheSameWhicheverOrderItEmptiedThem() {
        assertSame(new Closure.Contradicted<>(ordered(P, Q), Provenance.nothing()),
                new Closure.Contradicted<>(ordered(Q, P), Provenance.nothing()));
        assertSame(new Refusal.AtEachOf<>(ordered(P, Q)), new Refusal.AtEachOf<>(ordered(Q, P)));
    }

    /** And two that are not one value read differently, which is what says the reading is of the
     *  value and not of one word for every one of them. */
    @Test
    void andTwoThatAreNotOneValueReadDifferently() {
        RelationalLack<String> one = new RelationalLack.NoAssignmentTellsThemApart<>(ordered(P, Q));
        RelationalLack<String> other = new RelationalLack.NoAssignmentTellsThemApart<>(ordered(P, R));

        assertNotEquals(one, other);
        assertNotEquals(String.valueOf(one), String.valueOf(other));
    }

    /** That two values which are one are written the same way. */
    private static void assertSame(Object one, Object other) {
        assertEquals(one, other, "two of these built two ways are one value");
        assertEquals(String.valueOf(one), String.valueOf(other),
                "and one value is written one way");
    }

    /** These, in the order they are given, which is what a caller who built them another way round
     *  would not have. */
    @SafeVarargs
    private static <T> Set<T> ordered(T... these) {
        Set<T> out = new LinkedHashSet<>();
        for (T each : these) {
            out.add(each);
        }
        return out;
    }
}
