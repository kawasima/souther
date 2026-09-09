package souther.bench;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.check.ChoicesRead;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That each of the choice measurements reaches the reading it says it is about.
 *
 * <p>A measurement of a path nothing arrives at reports a number and answers nothing, and the two
 * are indistinguishable in a report: a compile that never settled a choice is quick, and a change
 * that made settling one slower comes back as no change. So what each shape here was written to
 * exercise is stated as a claim about the reading and held to it.
 *
 * <p>Held against what the compiler says it did ({@link ChoicesRead}) and never against the source
 * text. A count of {@code ||} in a generated string is a reading of the generator, and the generator
 * is the thing that would be wrong: a shape written to state sixty-four alternatives whose reading
 * merges them is exactly the accident this exists to catch, and the text says nothing about it.
 *
 * <p>Nothing here is timed. What the measurements come to on this machine is not a fact about the
 * compiler, and what they reach is.
 *
 * <p><b>And nothing here is a count.</b> A declaration is read as many times as the questions put to
 * it need — its own reading, and one more for each counterfactual somebody asks — so a figure below
 * is that many times what one reading of the shape did, and how many that is belongs to the callers
 * and not to this. What is asserted is what holds whatever the number is: how the figures stand to
 * each other. A shape stating one choice per reading and a shape stating sixty-three are told apart
 * by what divides the total, which is a fact about the shape either way.
 */
@Tag("population")
class EveryChoiceMeasurementReachesTheReadingItIsAboutTest {

    /** What one compile of {@code source} did with the choices in it. */
    private static ChoicesRead.Snapshot readingOf(String source) {
        ChoicesRead.Snapshot before = ChoicesRead.snapshot();
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.answerEverything();
        compilation.classes();
        return ChoicesRead.snapshot().since(before);
    }

    /**
     * The wide shape states the alternatives it was asked for, holds them apart, and puts each
     * branch in one place.
     *
     * <p>The number is the experiment, so it is checked rather than assumed: a balanced tree of n
     * alternatives is n − 1 written choices per reading, and a reading that came to fewer answered a
     * shape this series is not about. What divides the total says so however many readings were
     * made.
     */
    @Test
    void theWideShapeHoldsEveryAlternativeItStates() {
        for (int alternatives : new int[] {2, 8, 64}) {
            ChoicesRead.Snapshot read = readingOf(Choices.wide(alternatives));
            assertTrue(read.stated() > 0 && read.stated() % (alternatives - 1) == 0,
                    () -> "a wide shape of " + alternatives + " alternatives states "
                            + (alternatives - 1) + " choices in each reading of it, and this stated "
                            + read.stated());
            assertEquals(0, read.merged(),
                    () -> "a wide shape of " + alternatives + " alternatives was merged, so the"
                            + " reading held none of them apart");
            assertEquals(read.stated(), read.placesMet(),
                    () -> "a wide shape of " + alternatives + " put a branch in more than the one"
                            + " place it was written, so it is not the shape the deep series is"
                            + " read against");
            assertEquals(read.stated(), read.everyAlternativeStood(),
                    () -> "an alternative of a wide shape of " + alternatives + " was dropped");
        }
    }

    /**
     * And the deep shape puts each of its branches in every place distribution reaches.
     *
     * <p>This is what the series is for. Its alternatives are the wide shape's, and what it pays for
     * them is not, so a run in which the branches stood in one place each would be measuring the
     * wide shape twice.
     */
    @Test
    void theDeepShapePutsEachBranchInEveryPlaceItIsMetWith() {
        for (int choices : new int[] {1, 3, 6}) {
            ChoicesRead.Snapshot read = readingOf(Choices.deep(choices));
            assertTrue(read.stated() > 0 && read.stated() % choices == 0,
                    () -> "a deep shape of " + choices + " choices states that many in each reading"
                            + " of it, and this stated " + read.stated());
            // Every choice met with every other, so the tree the settlement walks is a full binary
            // one of this many choices and its nodes are the places a branch stands. Written as the
            // ratio the two figures stand in, which is the same whatever the reading was asked for.
            assertEquals(read.stated() * ((1L << choices) - 1), read.placesMet() * choices,
                    () -> "a deep shape of " + choices + " choices did not distribute into every"
                            + " place its neighbours put a branch: " + read.placesMet()
                            + " places for " + read.stated() + " choices");
            assertTrue(read.placesMet() > read.stated() || choices == 1,
                    () -> "a deep shape of " + choices + " choices left every branch where it was"
                            + " written, which is the wide shape measured again");
        }
    }

    /** A shape stating no choice reaches none of this, which is what the floor of a series is. */
    @Test
    void theFloorOfEachSeriesStatesNoChoice() {
        assertEquals(0, readingOf(Choices.wide(1)).stated(), "the wide floor states a choice");
        assertEquals(0, readingOf(Choices.deep(0)).stated(), "the deep floor states a choice");
    }

    /**
     * The boundary series is one alternative either side of where the reading stops holding them
     * apart.
     *
     * <p>Both halves, because either on its own is satisfied by a policy that does the same thing
     * everywhere: a run in which nothing merged and a run in which everything did would each pass
     * half of this, and neither is a boundary.
     */
    @Test
    void theBoundarySeriesFallsOnBothSidesOfTheLimit() {
        assertEquals(0, readingOf(Choices.wide(64)).merged(),
                "the reading merged the alternatives at the limit, so the pair straddles nothing");
        assertTrue(readingOf(Choices.wide(65)).merged() > 0,
                "the reading held the alternatives apart past the limit, so the pair straddles"
                        + " nothing");
    }

    /**
     * The clauses met with a choice do not multiply the places its branches stand in.
     *
     * <p>Which is what makes this series the other half of the expansion one. A clause that stated a
     * choice of its own would put the branch in more places, and the line would be measuring what
     * the deep series measures.
     */
    @Test
    void theClausesMetWithAChoiceMultiplyNothing() {
        for (int conjuncts : new int[] {0, 1, 8}) {
            ChoicesRead.Snapshot read = readingOf(Choices.conjuncts(conjuncts));
            assertTrue(read.stated() > 0,
                    () -> "the shape met with " + conjuncts + " clauses states no choice");
            assertEquals(read.stated(), read.placesMet(),
                    () -> "the shape met with " + conjuncts + " clauses put a branch in more than"
                            + " the one place it was written, so what it varies is not what it says"
                            + " it varies");
        }
    }

    /** And each fate shape comes to the fate it was written for, and to no other. */
    @Test
    void eachFateShapeComesToTheFateItWasWrittenFor() {
        ChoicesRead.Snapshot both = readingOf(Choices.Fate.BOTH_STAND.source(1));
        assertTrue(both.stated() > 0, "the shape written to keep both alternatives states no"
                + " choice");
        assertEquals(both.stated(), both.everyAlternativeStood(),
                "an alternative of the shape written to keep both was dropped");

        ChoicesRead.Snapshot one = readingOf(Choices.Fate.ONE_STANDS.source(1));
        assertTrue(one.stated() > 0, "the shape written to lose one alternative states no choice");
        assertEquals(one.stated(), one.oneAlternativeStood(),
                "the shape written to lose one alternative lost none or both");

        ChoicesRead.Snapshot none = readingOf(Choices.Fate.NONE_STANDS.source(1));
        assertTrue(none.stated() > 0, "the shape written to keep no alternative states no choice");
        assertEquals(none.stated(), none.noAlternativeStood(),
                "an alternative of the shape written to keep none of them stood");
    }
}
