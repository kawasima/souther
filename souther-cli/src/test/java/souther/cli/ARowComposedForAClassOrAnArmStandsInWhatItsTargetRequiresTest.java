package souther.cli;

import org.junit.jupiter.api.Test;
import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.GeneratedRows;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A candidate composed for a class of a position or for an arm of the body stands its target's
 * dependencies in, so it is a candidate something can run.
 *
 * <p>Which is what settles what it fills. What a class or an arm asks about is the positions, and
 * nothing on that route asks a dependency for one answer over another — but the behavior cannot be
 * applied without one, so a candidate composed without them is one nothing runs, and what it
 * reaches is then read off the body instead of watched. The arm it was steered to stays unanswered
 * and the row goes out saying less than it did.
 *
 * <p>Both halves are asserted because each fails on its own: that the candidate runs, and that the
 * row it becomes says what it stood the dependency in with. A row certified by a run in one
 * environment and offered in another is the two coming apart.
 */
class ARowComposedForAClassOrAnArmStandsInWhatItsTargetRequiresTest {

    /**
     * A body with a class of its position and an arm nothing written reaches, of a behavior
     * requiring one dependency the decision reads.
     *
     * <p>Over a union and not over a number, so that what composes the rows is the search over the
     * classes and the arms. A position with a border has rows composed at the points of that
     * border too, which is a second search with a route of its own — and a claim about one
     * answered by the other is a claim nothing here established.
     */
    private static final String UNREACHED = """
            module example.stood

            data Yes
            data No
            data Answer = Yes | No
            data Found
            data Missing
            data Sighting = Found | Missing

            behavior lookup : (s: Sighting) -> Answer

            behavior decides : (s: Sighting) -> Answer
                depends on lookup
            let decides (s, lookup) = match s with
                | Found -> lookup(s)
                | Missing -> No

            example decides
                | "missing" : (Missing) with lookup = Yes -> No
            """;

    /** The same, beside a second dependency the decision reads nothing of. */
    private static final String UNREAD = """
            module example.stood

            data Yes
            data No
            data Answer = Yes | No
            data Found
            data Missing
            data Sighting = Found | Missing

            behavior lookup : (s: Sighting) -> Answer
            behavior marking : (s: Sighting) -> Answer

            behavior decides : (s: Sighting) -> Answer
                depends on lookup, marking
            let decides (s, lookup, marking) = match s with
                | Found -> lookup(s)
                | Missing -> marking(s)

            example decides
                | "missing" : (Missing) with lookup = Yes, marking = No -> No
            """;

    @Test
    void whatACandidateFillsIsSettledByRunningItAndTheRowSaysWhatItStoodIn() {
        String block = generated(UNREACHED);

        assertTrue(block.contains("// fills s=Found"),
                () -> "the class of the position the candidate lands in is filled by it: " + block);
        assertTrue(block.contains("// fills case Found"),
                () -> "and so is the arm the run went through: " + block);
        assertFalse(block.contains("were not run"),
                () -> "nothing here is left unrun for want of a stand-in: " + block);
        assertTrue(everyRowStandsIn(block, "lookup"),
                () -> "and the row goes out saying what it answered with: " + block);
    }

    /**
     * And of a dependency the decision never reads.
     *
     * <p>The one the body turns on is the one a search has a reason to think about. A dependency it
     * only calls is answered because the behavior cannot be applied without it, which is a fact
     * about running the row rather than about what the row is for.
     */
    @Test
    void aDependencyTheDecisionNeverReadsIsStoodInOnSuchACandidateToo() {
        String block = generated(UNREAD);

        assertTrue(block.contains("// fills case Found"),
                () -> "the candidate ran, which takes standing both dependencies in: " + block);
        assertTrue(everyRowStandsIn(block, "lookup"),
                () -> "the dependency the arm turns on is answered: " + block);
        assertTrue(everyRowStandsIn(block, "marking"),
                () -> "and so is the one the decision reads nothing of: " + block);
    }

    /** Whether every row of the block says what {@code dependency} answers. Counted over the rows
     *  rather than asked of the block, so that a block with no rows at all does not pass by saying
     *  nothing. */
    private static boolean everyRowStandsIn(String block, String dependency) {
        long rows = block.lines().filter(each -> each.trim().startsWith("| ")).count();
        return rows > 0 && rows == block.lines()
                .filter(each -> each.trim().startsWith("| "))
                .filter(each -> each.contains(dependency + " = "))
                .count();
    }

    private static String generated(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return GeneratedRows.of(compilation, compilation.modules().get(0), "decides", true,
                SourceRendering.namedByIdentity(SourceLayouts.NONE)).text();
    }
}
