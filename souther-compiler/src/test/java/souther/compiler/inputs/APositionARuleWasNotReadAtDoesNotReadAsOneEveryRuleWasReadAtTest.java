package souther.compiler.inputs;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.conformance.RepositoryModels;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A position a rule was not read at does not come back as one every rule was read at.
 *
 * <p>What a rule this compiler did not get through costs the reading is everything that rule would
 * have said, so the values gathered at the position are an upper bound and not what the rules leave.
 * A reading that called itself complete there hands that bound on as the answer, and every measure
 * taken of it counts values the model may well refuse.
 *
 * <p><b>Two things say a reading stopped and neither is the whole of it.</b> A rule of a declaration
 * whose line nothing could fold leaves a finding at the position and its question is raised by the
 * accounting; a rule nothing classified at all leaves a question and no finding. Asked of one list,
 * this holds for every position carrying the other kind and says nothing about the rest — which is
 * why the population here is every position of every model this repository carries, and the reason
 * is read off the finding rather than looked for in one place.
 */
@Tag("population")
class APositionARuleWasNotReadAtDoesNotReadAsOneEveryRuleWasReadAtTest {

    /**
     * Over every model this repository carries, and it holds at every position of each.
     *
     * <p>The positions this is about are named, so a change that stops producing them is a failure
     * here as much as one that lets a reading call such a position complete: a test whose subjects
     * all went away holds for a reason other than the one it states.
     */
    @Test
    void aReadingThatStoppedIsCarriedByThePositionItStoppedAt() throws Exception {
        TreeSet<String> met = new TreeSet<>();
        for (InputDomain read : everyReading()) {
            for (Position each : read.positions()) {
                if (each.rulesWithoutALine().stream()
                        .noneMatch(one -> one.why() instanceof BlockReason.RuleReadingStopped)) {
                    continue;
                }
                met.add(each.path().toString());
                assertFalse(each.reading() instanceof ReadingResult.Complete
                                || each.reading() instanceof ReadingResult.NotSeparated,
                        () -> "the reading of a rule at `" + each.path() + "` stopped, and the "
                                + "position hands its values on as what the rules leave: "
                                + each.reading());
            }
        }
        assertFalse(met.isEmpty(), "no model here has a position a rule was not read at");
    }

    /** And where a question stands instead of a finding, which is the other of the two. */
    @Test
    void andSoIsOneWhoseRuleNothingClassified() throws Exception {
        TreeSet<String> met = new TreeSet<>();
        for (InputDomain read : everyReading()) {
            for (Position each : read.positions()) {
                if (each.unansweredQuestions().stream()
                        .noneMatch(StandingQuestion.Unclassified.class::isInstance)) {
                    continue;
                }
                met.add(each.path().toString());
                assertFalse(each.reading() instanceof ReadingResult.Complete
                                || each.reading() instanceof ReadingResult.NotSeparated,
                        () -> "nothing worked out what a rule at `" + each.path() + "` raises, and "
                                + "the position hands its values on as what the rules leave: "
                                + each.reading());
            }
        }
        assertFalse(met.isEmpty(), "no model here has a position a rule went unclassified at");
    }

    /**
     * And a position nothing was short of reads as one every rule was read at.
     *
     * <p>Here so that what the two above assert is answerable rather than true of every position
     * this compiler reads. Without it, a reading that called itself partial wherever it went would
     * pass them both and say nothing about the rules it did or did not get through.
     */
    @Test
    void andAPositionNothingWasShortOfReadsAsOne() throws Exception {
        Compilation compilation = Compilation.ofSources(List.of("""
                module probe.plain

                data Red
                data Green
                data Colour = Red | Green

                data Ok

                behavior f : (c: Colour) -> Ok
                """), ModulePath.EMPTY);
        compilation.answerEverything();
        List<InputDomain> read = new ArrayList<>();
        readings(compilation, read);
        Position at = read.get(0).positions().stream()
                .filter(each -> each.path().toString().equals("c")).findFirst().orElseThrow();

        assertEquals(List.of(), at.rulesWithoutALine(), "no rule of the model came to no line here");
        assertEquals(ReadingResult.Complete.class, at.reading().getClass(),
                () -> "and nothing is short of the position's rules: " + at.reading());
    }

    /** The readings, once they have been made. */
    private static List<InputDomain> everyReading;

    /**
     * The reading of every behavior of every model this repository carries.
     *
     * <p>Read once for the class, as the models are compiled once for the JVM: two questions here
     * walk the same readings and neither changes one, so reading them per question is the same
     * work over — and reading the population is most of what this class costs.
     *
     * <p>Read when one of them asks, and not while the class is initialised. What this population
     * failing to be read looks like is the regression this class is about, and a class that could
     * not initialise reports it as an initialiser that threw on every method — including the one
     * below that reads none of this and is what says the other two are answerable.
     */
    private static synchronized List<InputDomain> everyReading() {
        if (everyReading != null) {
            return everyReading;
        }
        List<InputDomain> out = new ArrayList<>();
        for (Compilation compilation : RepositoryModels.all()) {
            readings(compilation, out);
        }
        everyReading = List.copyOf(out);
        return everyReading;
    }

    /**
     * And given back when the class is done with them.
     *
     * <p>A fork keeps its JVM, so what is held statically is held for every class after this one.
     * Less than what a corpus's compiles come to — the compilations these are read from are
     * {@link RepositoryModels}' and stay whatever this does — but the readings themselves are this
     * class's, and it is the class that wanted them.
     */
    @AfterAll
    static void released() {
        everyReading = null;
    }

    private static void readings(Compilation compilation, List<InputDomain> out) {
        for (String module : compilation.modules()) {
            Map<String, DeclaredSig> sigs =
                    compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
            RuleReadingSource rules = RuleReadings.of(compilation, module);
            for (DeclaredSig declared : sigs.values()) {
                out.add(InputDomain.of(declared, rules, ReadAs.THE_COMPILATION_DOES));
            }
        }
    }
}
