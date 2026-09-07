package souther.compiler.report;

import souther.compiler.conformance.RepositoryModels;
import souther.compiler.observe.RunSensitivity;
import souther.compiler.query.Compilation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything holding a verdict open leaves a reader somewhere, and somewhere it can act.
 *
 * <p>The question a reader of {@code undetermined} has is what to do next, and until now the report
 * answered it only where the answer was a row. What is held here is the other half: every entry
 * reaches exactly one {@link ReaderDisposition}, and which one follows from what the compiler
 * established rather than from a formatter's reading of the words.
 *
 * <p><b>Over the models this repository carries and not over values a test built.</b> A
 * disposition worked out from a hand-made opening says the switch is total, which javac already
 * says; what it does not say is that the openings a real model produces reach the arms anybody
 * expected. Those are two claims and only the second can go wrong quietly.
 */
class EveryThingThatHoldsAVerdictOpenLeavesTheReaderSomewhereTest {

    /** Every opening every model here produces, which is where a claim about them has to be held. */
    private static List<AdequacyOpening> everyOpening() {
        List<AdequacyOpening> out = new ArrayList<>();
        for (Compilation compilation : RepositoryModels.all()) {
            out.addAll(AdequacyReport.of(compilation).whatKeepsTheVerdictOpen());
        }
        return out;
    }

    /**
     * One apiece, and the population is not empty.
     *
     * <p>The second half is what keeps this from passing over a corpus that holds nothing open. A
     * law about openings answered over none of them is a law nothing has been held to.
     */
    @Test
    void everyOpeningReachesADisposition() {
        List<AdequacyOpening> openings = everyOpening();

        assertFalse(openings.isEmpty(), "the models here hold verdicts open, which is what this is"
                + " a law about");
        for (AdequacyOpening each : openings) {
            assertTrue(ReaderDisposition.of(each) != null,
                    () -> each + " holds a verdict open and leaves a reader nowhere");
        }
    }

    /**
     * And a wider run is offered exactly where a wider run would answer it.
     *
     * <p>The one arm a reader acts on without looking at anything, so it is the one that must not be
     * offered where it is wrong: told to measure again over a rule this compiler has no reading for,
     * an author runs the build and meets the same sentence.
     */
    @Test
    void aWiderRunIsOfferedExactlyWhereItWouldAnswer() {
        for (AdequacyOpening each : everyOpening()) {
            boolean offered = ReaderDisposition.of(each) instanceof ReaderDisposition.WidenTheRun;

            assertEquals(each.runSensitivity() == RunSensitivity.MAY_CHANGE, offered,
                    () -> "a wider run is offered for " + each + " and it says "
                            + each.runSensitivity());
        }
    }

    /**
     * What the corpus actually reaches, written out.
     *
     * <p>So that a change which quietly stops producing an opening, or starts answering one with a
     * different arm, is read here rather than found by someone running the command. The set and not
     * the counts: how many of a kind a model holds open moves with the model, and which kinds these
     * models reach at all is what this is about.
     */
    @Test
    void theModelsHereReachTheseDispositions() {
        Set<String> reached = new LinkedHashSet<>();
        for (AdequacyOpening each : everyOpening()) {
            reached.add(ReaderDisposition.of(each).getClass().getSimpleName());
        }

        // Four of the eight. A fork nothing tells apart holds no verdict open in any model here,
        // and this compiler's own proof has never been contradicted by one — so those two arms are
        // held where a fact can be built rather than found, and the two that ask a person to weigh
        // something are not reached by an opening at all.
        assertEquals(Set.of("LookAtTheRule", "LookAtWhyNothingWasMeasured",
                        "LookAtWhatShowedNoRow", "LookAtWhatTheMeasureWentWithout"), reached,
                () -> "the arms these models reach: " + reached);
    }
}
