package souther.bench;

import org.junit.jupiter.api.Test;

import souther.compiler.check.ChoicesRead;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every point the choice measurement times reaches the reading it says it is about.
 *
 * <p>A measurement of a path nothing arrives at reports a number and answers nothing, and the two
 * are indistinguishable in a report: a compile that never settled a choice is quick, and a change
 * that made settling one slower comes back as no change. So each point states which reading its
 * figure would be about, and this runs the points and holds them to it.
 *
 * <p><b>The points, and not shapes written to stand for them.</b> {@link Choices#points()} is what
 * the measurement times and what this walks — one list, one builder, one set of sizes. A test that
 * built its own copy of a shape would be checking that copy: the sizes could part, or the source a
 * series is generated from could change under the series alone, and the reading a figure is about
 * would go unwatched while a reading of something adjacent stayed green. That is the shape of the
 * defect this whole measurement exists because of, one level up.
 *
 * <p>Held against what the compiler says it did ({@link ChoicesRead}) and never against the source
 * text. A count of {@code ||} in a generated string is a reading of the generator, and the generator
 * is the thing that would be wrong: a shape written to state sixty-four alternatives whose reading
 * merges them is exactly the accident, and the text says nothing about it.
 *
 * <p>Nothing here is timed. What the measurements come to on this machine is not a fact about the
 * compiler, and what they reach is. And nothing here is a model this repository carries, so it is
 * not among the population the nightly runs: what it holds is what a change to the reading of a
 * choice would break, which is a thing to find out on the change and not the night after it.
 */
class EveryChoiceMeasurementReachesTheReadingItIsAboutTest {

    /** What one compile of {@code source} did with the choices in it. */
    private static ChoicesRead.Snapshot readingOf(String source) {
        ChoicesRead.Snapshot before = ChoicesRead.snapshot();
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.answerEverything();
        compilation.classes();
        return ChoicesRead.snapshot().since(before);
    }

    /** Every point, and every one of them arriving where its line says the figure came from. */
    @Test
    void everyPointReachesWhatItsFigureIsAbout() {
        List<String> unmet = new ArrayList<>();
        for (Choices.Point point : Choices.points()) {
            String why = point.claim().unmetBy(readingOf(point.source()));
            if (why != null) {
                unmet.add(point.series() + " " + point.label() + " " + why);
            }
        }
        assertEquals(List.of(), unmet,
                "a point this measurement times does not reach the reading its figure is about, so"
                        + " the line says nothing about what it names");
    }

    /**
     * And the points are not all one claim.
     *
     * <p>Every claim above is met by a reading that arrived, so a list of points that had all lost
     * their shapes to one another would pass: sixty-four alternatives merged into two would fail,
     * and sixty-four shapes each stating two would not. What separates the series is that some hold
     * their alternatives apart and some merge them, and some distribute and some do not, so that is
     * asked of the list rather than of any point in it.
     */
    @Test
    void thePointsBetweenThemReachEachWayAChoiceIsRead() {
        ChoicesRead.Snapshot everything = ChoicesRead.snapshot();
        for (Choices.Point point : Choices.points()) {
            readingOf(point.source());
        }
        ChoicesRead.Snapshot read = ChoicesRead.snapshot().since(everything);

        assertTrue(read.merged() > 0, "no point of this measurement is past the limit");
        assertTrue(read.carriedToSettlement() > 0, "no point of it reaches the settlement");
        assertTrue(read.settledOffDescriptions() > 0,
                "no point of it is settled off the descriptions alone");
        assertTrue(read.placesMet() > read.carriedToSettlement(),
                "no point of it puts a branch anywhere but where it was written");
        assertTrue(read.everyAlternativeStood() > 0 && read.oneAlternativeStood() > 0
                        && read.noAlternativeStood() > 0,
                "the points do not between them reach every fate a choice comes to");
    }
}
