package souther.compiler.report;

import souther.compiler.conformance.RepositoryModels;
import souther.compiler.observe.RunSensitivity;
import souther.compiler.query.Compilation;

import org.junit.jupiter.api.Tag;
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
@Tag("population")
class EveryThingThatHoldsAVerdictOpenLeavesTheReaderSomewhereTest {

    /**
     * Every opening every model here produces, which is where a claim about them has to be held.
     *
     * <p>Built once for the class. Nothing else here asks a bench corpus how adequate its rows
     * are, so the measuring of one of them is paid by this and by nothing else — measured, it is
     * that one model rather than the population, which is built for whoever asks first.
     *
     * <p>It is not narrowed to make it cheaper. Narrowed, the law is held over fewer of the arms it
     * is about, which is a smaller claim rather than a faster one. What it is instead is where such
     * a claim belongs: its subject is the models this repository carries, so it runs where they are
     * the subject.
     *
     * <p>Which arms they reach is theirs to change. A model reaches an arm about something a measure
     * went without only while it is short of that thing, so a model made more adequate takes such a
     * witness away — these are evidence about the language, not fixtures held to reach an arm.
     */
    private static final List<AdequacyOpening> OPENINGS = everyOpening();

    private static List<AdequacyOpening> everyOpening() {
        List<AdequacyOpening> out = new ArrayList<>();
        for (Compilation compilation : RepositoryModels.all()) {
            out.addAll(AdequacyReport.of(compilation).whatKeepsTheVerdictOpen());
        }
        return List.copyOf(out);
    }

    /**
     * One apiece, and the population is not empty.
     *
     * <p>The second half is what keeps this from passing over a corpus that holds nothing open. A
     * law about openings answered over none of them is a law nothing has been held to.
     */
    @Test
    void everyOpeningReachesADisposition() {
        List<AdequacyOpening> openings = OPENINGS;

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
        for (AdequacyOpening each : OPENINGS) {
            boolean offered = ReaderDisposition.of(each) instanceof ReaderDisposition.WidenTheRun;

            assertEquals(each.runSensitivity() == RunSensitivity.MAY_CHANGE, offered,
                    () -> "a wider run is offered for " + each + " and it says "
                            + each.runSensitivity());
        }
    }

    /**
     * The dispositions a model this repository carries reaches.
     *
     * <p>Written out so that a change which quietly stops producing an opening, or starts answering
     * one with a different arm, is read here rather than found by someone running the command. The
     * set and not the counts: how many of a kind a model holds open moves with the model.
     */
    private static final Set<String> WITNESSED = Set.of(
            "LookAtTheRule", "LookAtWhyNothingWasMeasured", "LookAtWhatShowedNoRow");

    /**
     * The dispositions nothing here witnesses, which is a thing owed rather than a thing settled.
     *
     * <p>An arm a model reaches only while the model is short of something is witnessed by accident:
     * the models are evidence about the language and not fixtures for these arms, and one of them
     * becoming more adequate takes its witness away. That is what happened to the one named here,
     * and pinning the corpus to keep it would be holding a model back from what it is for.
     *
     * <p>What such an arm needs is a witness of its own — a model written to reach it, through the
     * analysis rather than by building the value — and until there is one this says so. It is not a
     * note: nothing here may reach one of these, so the day a model does, this fails and the entry
     * comes out.
     */
    private static final Set<String> WITNESS_OWED = Set.of("LookAtWhatTheMeasureWentWithout");

    /**
     * Each disposition this holds is either reached by these models or recorded as owing a witness.
     *
     * <p>Not historical reachability. A disposition these models reach can go away when one of them
     * becomes more adequate, so what was reached before is no contract; what is kept stable is that
     * every arm named here is on one of the two lists and on the right one.
     */
    @Test
    void theModelsHereReachTheseDispositions() {
        Set<String> reached = new LinkedHashSet<>();
        for (AdequacyOpening each : OPENINGS) {
            reached.add(ReaderDisposition.of(each).getClass().getSimpleName());
        }

        Set<String> unwitnessed = new LinkedHashSet<>(WITNESSED);
        unwitnessed.removeAll(reached);
        assertEquals(Set.of(), unwitnessed,
                () -> "a disposition held to be witnessed here is reached by none of these models."
                        + " Reached: " + reached);

        Set<String> owedButReached = new LinkedHashSet<>(WITNESS_OWED);
        owedButReached.retainAll(reached);
        assertEquals(Set.of(), owedButReached,
                "a disposition recorded as owing a witness is reached after all: move it to the"
                        + " witnessed ones, and take out whatever was written to owe it");

        Set<String> both = new LinkedHashSet<>(WITNESSED);
        both.retainAll(WITNESS_OWED);
        assertEquals(Set.of(), both, "a disposition is witnessed or owed, and not both");
    }
}
