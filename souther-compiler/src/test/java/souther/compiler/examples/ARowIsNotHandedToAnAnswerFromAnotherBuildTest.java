package souther.compiler.examples;

import souther.compiler.check.CheckedEnsures;
import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.execute.jvm.JvmDeadlines;
import souther.compiler.observe.Observations;
import souther.compiler.observe.ArmObservation;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.generated.EvaluationArtifact;
import souther.compiler.meta.PublishedClasses;
import souther.compiler.observe.Applied;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.FailurePhase;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.RowOutcome;
import souther.compiler.observe.Stage;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Shapes;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run does not hand a row to an answer whose declarations are of another build of the module.
 *
 * <p>What the row's values are is decided by the module being evaluated, and what reads them is
 * decided by whatever the answer was compiled against. Where those are two builds, a row handed over
 * is decided by a disagreement between the builds rather than by the model: an invariant that has
 * been narrowed refuses a value the model admits, a case added to a union arrives at an answer that
 * has no such case. Both land somewhere that reads as a statement about the model.
 *
 * <p>So the row stops before it is handed over, and stops saying what it is: everything it can be
 * held to without being run has been, and what it could not be is run against something nobody could
 * establish. That is an absence of evidence and not a failing row.
 */
class ARowIsNotHandedToAnAnswerFromAnotherBuildTest {

    private static final String MODEL = """
            module example.stale exposing ( Title )
            import String ( length )

            data Title = String
                invariant length(value) > 0

            behavior shout : (t: Title) -> Title
                constructs Title

            let shout (t) = Title(t.value)

            example shout
              | "one"   : (Title("aaaa")) -> Title("aaaa")
              | "again" : (Title("bbbb")) -> Title("bbbb")
            """;

    /** The same model with the invariant narrowed since the answer was built. */
    private static final String NARROWED =
            MODEL.replace("invariant length(value) > 0", "invariant length(value) > 3");

    /**
     * A row of a module that has moved is recorded rather than run, and says which of the two it is.
     *
     * <p>{@code FIXTURES_VALIDATED} because the values are this build's and were built and held to
     * their invariants; {@code INCOMPLETE} because nothing was decided about the model;
     * {@code ANSWERER_ESTABLISHMENT} because what stopped it was the answer, not the row.
     */
    @Test
    void aRowIsRecordedUndecidedWhenTheAnswerIsOfAnotherBuild() {
        Answering older = answeringFrom(NARROWED);

        Observations observed = evaluated(MODEL, older);

        assertEquals(2, observed.rows().size());
        for (RowOutcome row : observed.rows()) {
            assertEquals(Stage.FIXTURES_VALIDATED, row.stage(),
                    "the row's own values were built and held to their invariants");
            assertEquals(Disposition.INCOMPLETE, row.disposition(),
                    "and nothing was decided about the model");
            assertEquals(FailurePhase.ANSWERER_ESTABLISHMENT, row.failurePhase(),
                    "what stopped it is the answer and not the row");
            assertEquals(new Applied.Nothing(), row.run().applied(),
                    "nothing applied the behavior");
        }
    }

    /** The answer is never handed a row: what it would read them by is what was refused. */
    @Test
    void theAnswerIsNeverHandedARow() {
        Refusing older = new Refusing(declarationsOf(NARROWED));

        evaluated(MODEL, older.asAnswering());

        assertFalse(older.handed, "no row reached it");
    }

    /**
     * An answer that says nothing about which build it reads by is refused, not read as this one.
     *
     * <p>What states it is an abstract accessor, so an implementation that does not state it is
     * refused where it is written. That leaves the one Java does not refuse: an implementation that
     * answers with nothing. Both ways of taking it are worse than refusing it — raised, one
     * implementation stops the whole compile; read as this compile's own, an implementation is out
     * of the question by returning null and its rows are run against declarations nobody held it to.
     */
    @Test
    void anAnswerThatSaysNothingAboutItsBuildIsRefusedRatherThanTakenForThisOne() {
        Silent silent = new Silent();

        Observations observed = evaluated(MODEL, silent.asAnswering());

        assertFalse(silent.handed, "no row reached it");
        assertEquals(2, observed.rows().size());
        for (RowOutcome row : observed.rows()) {
            assertEquals(Disposition.INCOMPLETE, row.disposition(),
                    "nothing was decided about the model");
            assertEquals(FailurePhase.ANSWERER_ESTABLISHMENT, row.failurePhase(),
                    "what stopped it is the answer and not the row");
        }
        assertEquals(1, observed.failures().size(),
                "one answer could not be established, whatever the rows were");
    }

    /** An answerer that answers with nothing when asked which build its answers read values by. */
    private static final class Silent implements Answerer {

        private boolean handed;

        private Answering asAnswering() {
            return (generated, compiled) -> this;
        }

        @Override
        public Answer of(String behavior) {
            return new Answer.Something() {

                @Override
                public Origin origin() {
                    return null;
                }

                @Override
                public Applying applying(List<DependencyStandin> standins) {
                    handed = true;
                    throw new ImplementationNotReached("a row reached it (said by the test)",
                            new ClassNotFoundException(behavior));
                }
            };
        }
    }

    /**
     * One diagnostic for the behavior, and not one per row.
     *
     * <p>Two rows did not stop for two reasons. One answer and one module disagree, and that is the
     * one thing there is to say however many rows were waiting on it.
     */
    @Test
    void theDisagreementIsReportedOncePerBehaviorAndNamesWhatMoved() {
        Observations observed = evaluated(MODEL, answeringFrom(NARROWED));

        assertEquals(1, observed.failures().size(),
                "one answer and one module disagree, whatever the rows were");
        String said = observed.failures().get(0).said().toString();
        assertTrue(said.contains("Title"), "it names the declaration that moved: " + said);
    }

    /**
     * Still one diagnostic where the behavior's rows are written in more than one block.
     *
     * <p>A behavior's rows may be written wherever they belong, and what is being reported is not a
     * fact about a block. One answer and one module disagree, and a reader told so twice is being
     * told one thing and counting two.
     */
    @Test
    void theDisagreementIsReportedOncePerBehaviorAcrossBlocks() {
        String twoBlocks = MODEL.replace("""
                example shout
                  | "one"   : (Title("aaaa")) -> Title("aaaa")
                  | "again" : (Title("bbbb")) -> Title("bbbb")
                """, """
                example shout
                  | "one"   : (Title("aaaa")) -> Title("aaaa")

                example shout
                  | "again" : (Title("bbbb")) -> Title("bbbb")
                """);

        Observations observed =
                evaluated(twoBlocks, answeringFrom(NARROWED.replace("""
                        example shout
                          | "one"   : (Title("aaaa")) -> Title("aaaa")
                          | "again" : (Title("bbbb")) -> Title("bbbb")
                        """, """
                        example shout
                          | "one"   : (Title("aaaa")) -> Title("aaaa")

                        example shout
                          | "again" : (Title("bbbb")) -> Title("bbbb")
                        """)));

        assertEquals(2, observed.rows().size(), "both blocks' rows were recorded");
        assertEquals(1, observed.failures().size(),
                "and the answer disagreeing with the module is one thing to say");
    }

    /**
     * What a measure is told: the rows were read and could not be decided.
     *
     * <p>Not that nothing was read. The rows are there and say what they were built from, so a
     * measure over them is over rows it can see; what it cannot answer is anything the running would
     * have decided.
     */
    @Test
    void aMeasureIsToldTheRowsCouldNotBeDecided() {
        Observations observed = evaluated(MODEL, answeringFrom(NARROWED));

        List<Incompleteness.Code> codes =
                observed.incompleteness().stream().map(Incompleteness::code).toList();
        assertEquals(List.of(Incompleteness.Code.ANSWERER_NOT_ESTABLISHED,
                        Incompleteness.Code.ANSWERER_NOT_ESTABLISHED), codes,
                "one per row that could not be decided");
        assertFalse(Incompleteness.Code.ANSWERER_NOT_ESTABLISHED.leftNoRowRead(),
                "the rows were read; what they would have decided is what is missing");
    }

    /**
     * A run whose answers are the compile's own reads nothing.
     *
     * <p>There are not two builds, so there is nothing to compare — and the declarations of the
     * module being evaluated are never asked for. Held by giving it a reader that raises: a run that
     * paid for the question at all would fail here.
     */
    @Test
    void aRunOfTheCompilesOwnAnswersNeverReadsAnyDeclarations() {
        Supplier<PublishedClasses> refuses = () -> {
            throw new AssertionError("a run of this compile's own answers read declarations");
        };

        Observations observed =
                evaluated(MODEL, Answering.generatedHere(), refuses);

        assertEquals(List.of(), observed.failures().stream().map(f -> f.code()).toList());
        for (RowOutcome row : observed.rows()) {
            assertEquals(Disposition.HELD, row.disposition());
        }
    }

    /** An answerer that says its answers read values by {@code theirs}, and is never handed one. */
    private static final class Refusing implements Answerer {

        private final PublishedClasses theirs;
        private boolean handed;

        private Refusing(PublishedClasses theirs) {
            this.theirs = theirs;
        }

        private Answering asAnswering() {
            return (generated, compiled) -> this;
        }

        @Override
        public Answer of(String behavior) {
            return new Answer.Something() {

                @Override
                public Origin origin() {
                    return new Origin.Published(theirs);
                }

                @Override
                public Applying applying(List<DependencyStandin> standins) {
                    handed = true;
                    throw new ImplementationNotReached("a row reached it (said by the test)",
                            new ClassNotFoundException(behavior));
                }
            };
        }
    }

    /** An answerer reading values by the declarations {@code source} was built with. */
    private static Answering answeringFrom(String source) {
        return new Refusing(declarationsOf(source)).asAnswering();
    }

    /** The rows of {@code source}, run against {@code answering}. */
    private static Observations evaluated(String source, Answering answering) {
        return evaluated(source, answering, () -> declarationsOf(source));
    }

    private static Observations evaluated(String source, Answering answering,
                                                          Supplier<PublishedClasses> declared) {
        Compilation c = Compilation.ofSource(source, "Main");
        c.db().ask(new Output.All());
        String name = c.modules().get(0);
        EvaluationArtifact artifact = c.db()
                .ask(new Output.EvaluationLinked(name, ArmObservation.OMIT)).value();
        // Held to compiling: a model that did not is one whose rows were never emitted, and every
        // question below would be answered by that instead of by what is being measured.
        assertEquals(List.of(), c.diagnostics().values().stream().flatMap(List::stream)
                .map(d -> String.valueOf(d.diagnostic().code())).toList(),
                "the model whose rows are run compiles");
        return ExampleVerifier.check(
                c.db().ask(new Shapes.Prepared(name)).value().forExamples(),
                Scopes.derived(c.db(), name).value(),
                Shapes.publishedDeclarations(c.db()),
                Shapes.declarationKinds(c.db()),
                souther.compiler.query.ExampleExecutions.of(c.db(), name).fieldTypes(),
                c.db().ask(new Bodies.Reachable(name)).value(),
                artifact,
                declared,
                c.db().ask(new Bodies.Requirements(name)).value(),
                ExampleVerifier.class.getClassLoader(),
                c.db().ask(new Bodies.ModuleDefinitions(name)).value(),
                JvmDeadlines.of(EvaluationPolicy.DEFAULT.compilerTimeout()),
                EvaluationPolicy.DEFAULT,
                answering,
                CheckedEnsures.executableOf(
                        c.db().ask(new Bodies.ReachableContracts(name)).value()));
    }

    /** The classes one build of {@code source} emits, read for what they were stamped with. */
    private static PublishedClasses declarationsOf(String source) {
        Compilation compiled = Compilation.ofSource(source, "Main");
        Map<String, ClassFileImage> classes = compiled.db().ask(new Output.All()).value();
        return ModulePath.of(classes).declarations();
    }
}
