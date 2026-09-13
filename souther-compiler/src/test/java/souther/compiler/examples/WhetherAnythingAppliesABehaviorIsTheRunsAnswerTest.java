package souther.compiler.examples;

import souther.compiler.check.CheckedEnsures;
import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.execute.jvm.JvmDeadlines;
import souther.compiler.observe.Observations;
import souther.compiler.observe.ArmObservation;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.generated.EvaluationArtifact;
import souther.compiler.generated.GeneratedImplementations;
import souther.compiler.generated.MemoryClassLoader;
import souther.compiler.observe.Applied;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.FailurePhase;
import souther.compiler.observe.RowOutcome;
import souther.compiler.observe.Stage;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;
import souther.compiler.query.Shapes;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether anything applies a behavior for a row is what the run's answerer says, and not how the
 * behavior is written.
 *
 * <p>The two say the same thing while a compile's own classes are the only thing that applies
 * anything, so either could be read for the other without being wrong. They are different questions
 * all the same, and each test here is one of the ways they come apart:
 *
 * <ul>
 *   <li>a behavior written with no {@code let} that something applies — the row runs;</li>
 *   <li>a behavior written with a body that nothing applies — the row is recorded;</li>
 *   <li>a behavior something has an implementation for that cannot be reached — a failure, and not
 *       a row nothing applies.</li>
 * </ul>
 *
 * <p>The third is the one that says why this is answered rather than derived from what a loader
 * holds. An implementation that is not where it was looked for is this compiler failing to reach
 * what it undertook to apply, and answering that with "nothing applies this behavior" would record a
 * broken compile as a model waiting for a {@code let}.
 */
class WhetherAnythingAppliesABehaviorIsTheRunsAnswerTest {

    /** Two behaviors that mean the same thing, one written with a body and one written without. */
    private static final String MODEL = """
            module example.applying

            data Amount = Int

            behavior double : (a: Amount) -> Amount
                constructs Amount

            let double (a) = Amount(a.value * 2)

            behavior doubleFromOutside : (a: Amount) -> Amount
                constructs Amount

            example double
              | "twice" : (Amount(1)) -> Amount(2)

            example doubleFromOutside
              | "twice, from outside" : (Amount(1)) -> Amount(2)
            """;

    /**
     * A behavior the module writes no {@code let} for, and a run that has something applying it.
     *
     * <p>The row runs, its answer is compared and it holds. Nothing about how the behavior is written
     * changed — a reader going to the declaration would still find no body — so a row deciding from
     * there would record this one as pending and the answer it agrees with would never be compared.
     */
    @Test
    void aBehaviorWithNoBodyIsRunWhenTheRunHasSomethingToApplyIt() {
        Observations observed = evaluated(applyingTheOneWithNoBody());

        RowOutcome row = rowFor(observed, "doubleFromOutside");
        assertEquals(Stage.COMPARED, row.stage(), "the row was run and its answer compared");
        assertEquals(Disposition.HELD, row.disposition(), "and it held: " + observed.failures());
        assertEquals(FailurePhase.NONE, row.failurePhase());
        assertInstanceOf(Applied.GeneratedHere.class, row.run().applied(),
                "what applied it is what the answerer said applied it");
        assertEquals(List.of(), observed.failures());
    }

    /**
     * The control, on the same run: a behavior with a body that this run has nothing for.
     *
     * <p>Recorded rather than run, and recorded as what a row nothing applies has always been. A
     * reader deciding from the declaration would have run it against classes this run never offered.
     */
    @Test
    void aBehaviorWithABodyIsRecordedWhenNothingInTheRunAppliesIt() {
        Observations observed = evaluated(applyingTheOneWithNoBody());

        RowOutcome row = rowFor(observed, "double");
        assertEquals(Stage.FIXTURES_VALIDATED, row.stage(),
                "everything it can be held to without being run, it was held to");
        assertEquals(Disposition.PENDING, row.disposition());
        assertEquals(FailurePhase.NONE, row.failurePhase(), "which is not a failure");
        assertInstanceOf(Applied.Nothing.class, row.run().applied());
        assertEquals(List.of(), observed.failures(),
                "and nothing is said about a model that may be right");
    }

    /**
     * An answerer that says it has an implementation for every behavior and reaches none of them.
     *
     * <p>Each row fails and says the behavior was never entered. Not {@code PENDING}: having nothing
     * to apply and having something that could not be reached are different states, and only the
     * second is a fault. A reader that decided applicability by looking the class up in the loader
     * would turn every one of these into the first, and a compile that emitted nothing would report a
     * corpus of models waiting for a {@code let}.
     */
    @Test
    void anImplementationThatCannotBeReachedIsAFailureAndNotARowNothingApplies() {
        Observations observed = evaluated(claimingEverythingAndReachingNothing());

        assertEquals(2, observed.rows().size());
        for (RowOutcome row : observed.rows()) {
            assertNotEquals(Disposition.PENDING, row.disposition(),
                    "a row whose implementation could not be reached is not one nothing applies");
            assertEquals(Disposition.FAILED, row.disposition());
            assertEquals(FailurePhase.INVOCATION, row.failurePhase());
            assertEquals(Stage.FIXTURES_VALIDATED, row.stage(), "it never got in");
            assertInstanceOf(Applied.Nothing.class, row.run().applied(),
                    "nothing applied it, which is not nothing being able to");
        }
        assertEquals(2, observed.failures().size(), "and both rows are told");
    }

    /**
     * What this compile itself answers with, held to what it emitted.
     *
     * <p>The manifest is the emitter's own record: the behavior with a body is in it and the one
     * without is not. Those are the two answers the rows above were given, from the one place that
     * decided them.
     */
    @Test
    void whatACompileAppliesIsWhatItEmittedAnImplementationFor() {
        GeneratedImplementations generated = artifactOf(compiled(), "example.applying").implementations();

        assertEquals("example.applying", generated.module());
        assertTrue(generated.has("double"),
                "it emitted an implementation for the behavior with a body");
        assertEquals(Set.of("double"), generated.behaviors(),
                "and for nothing else: a behavior with no `let` is not implemented here");
    }

    /**
     * The compile's own answerer, asked about a behavior it emitted and whose class is not there.
     *
     * <p>It says {@link Answerer.Answer.Something} — the manifest is what it reads, and the manifest
     * says this compile implemented the behavior. What the class not being there is comes out where
     * the behavior is applied, as {@link ImplementationNotReached}.
     *
     * <p>The one that holds the production answerer to it. Reading the loader here instead would be
     * the cheaper way to write {@link GeneratedImplementation#of} and would answer this case with
     * "nothing applies it", turning every implementation this compiler failed to reach into a row
     * waiting for a {@code let}.
     */
    @Test
    void theCompilesOwnAnswererSaysSomethingForWhatItEmittedEvenWithNoClassToLoad() {
        GeneratedImplementations manifest =
                new GeneratedImplementations("example.applying", Set.of("double"));
        MemoryClassLoader empty =
                new MemoryClassLoader(Map.of(), ExampleVerifier.class.getClassLoader());

        Answerer answerer = Answering.generatedHere().over(manifest, empty);

        Answerer.Answer answer = answerer.of("double");
        Answerer.Answer.Something something = assertInstanceOf(Answerer.Answer.Something.class,
                answer, "the manifest says this compile implemented it, so something applies it");
        ImplementationNotReached notReached = assertThrows(ImplementationNotReached.class,
                () -> something.applying(List.of()).to(List.of()),
                "and the class not being there is said where the behavior is applied");
        assertTrue(notReached.getMessage().contains("Double"),
                "naming what could not be reached: " + notReached.getMessage());
        assertInstanceOf(Answerer.Answer.Nothing.class, answerer.of("doubleFromOutside"),
                "and what it did not emit is what it has nothing for");
    }

    /**
     * A run over one module's rows, handed another module's artifact.
     *
     * <p>Refused where both are in hand. The manifest is what says which module's implementations an
     * answerer applies, so past this point the module the rows belong to is gone: a behavior of this
     * module would be looked up in that one, and a name it has one of would be applied — a row
     * answered by an implementation of something else.
     */
    @Test
    void anArtifactOfAnotherModuleIsRefusedWhereTheRunIsMade() {
        Compilation mine = compiled();
        Compilation other = Compilation.ofSource("""
                module example.elsewhere

                data Amount = Int

                behavior double : (a: Amount) -> Amount
                    constructs Amount

                let double (a) = Amount(a.value * 99)
                """, "Main");
        other.db().ask(new Output.All());
        String name = mine.modules().get(0);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> ExampleVerifier.check(
                        mine.db().ask(new Shapes.Prepared(name)).value().forExamples(),
                        Scopes.derived(mine.db(), name).value(),
                        Shapes.publishedDeclarations(mine.db()),
                        Shapes.declarationKinds(mine.db()),
                        souther.compiler.query.ExampleExecutions.of(mine.db(), name).fieldTypes(),
                        mine.db().ask(new Bodies.Reachable(name)).value(),
                        artifactOf(other, "example.elsewhere"),
                        () -> {
                            throw new AssertionError("the run was made before it was refused");
                        },
                        mine.db().ask(new Bodies.Requirements(name)).value(),
                        ExampleVerifier.class.getClassLoader(),
                        mine.db().ask(new Bodies.ModuleDefinitions(name)).value(),
                        JvmDeadlines.of(EvaluationPolicy.DEFAULT.compilerTimeout()),
                        EvaluationPolicy.DEFAULT,
                        Answering.generatedHere(),
                        CheckedEnsures.executableOf(
                                mine.db().ask(new Bodies.ReachableContracts(name)).value())));

        assertTrue(refused.getMessage().contains("example.applying")
                        && refused.getMessage().contains("example.elsewhere"),
                "the refusal names both: " + refused.getMessage());
    }

    /**
     * An answerer with something for the behavior that has no body, and nothing for the one that has.
     *
     * <p>What it applies is this compile's own implementation of {@code double}, which is what makes
     * the row that runs have an answer to compare: the two behaviors mean the same thing, so a row
     * written for either asserts the same value.
     */
    private static Answering applyingTheOneWithNoBody() {
        return (generated, compiled) -> {
            // The compile's own answerer, told that what it applies is `double` — which is what it
            // emitted, so this is its manifest and not a claim about anything else.
            Answerer own = Answering.generatedHere().over(
                    new GeneratedImplementations(generated.module(), Set.of("double")), compiled);
            return behavior -> {
                if (!behavior.equals("doubleFromOutside")) {
                    return new Answerer.Answer.Nothing();
                }
                return new Answerer.Answer.Something() {

                    @Override
                    public Origin origin() {
                        return new TheCompilesOwn();
                    }

                    @Override
                    public Answerer.Applying applying(List<DependencyStandin> standins) {
                        return ((Answerer.Answer.Something) own.of("double")).applying(standins);
                    }
                };
            };
        };
    }

    /** An answerer claiming an implementation for every behavior and reaching none of them. */
    private static Answering claimingEverythingAndReachingNothing() {
        return (generated, compiled) -> behavior -> new Answerer.Answer.Something() {

            @Override
            public Origin origin() {
                return new TheCompilesOwn();
            }

            @Override
            public Answerer.Applying applying(List<DependencyStandin> standins) {
                return new Answerer.Applying() {

                    @Override
                    public Applied applied() {
                        return new Applied.GeneratedHere();
                    }

                    @Override
                    public Object to(List<Handed> arguments) {
                        throw new ImplementationNotReached("no class of that name (said by the test)",
                                new ClassNotFoundException(behavior));
                    }
                };
            }
        };
    }

    private static RowOutcome rowFor(Observations observed, String target) {
        List<RowOutcome> rows =
                observed.rows().stream().filter(r -> r.target().equals(target)).toList();
        assertEquals(1, rows.size(), "one row is written for `" + target + "`");
        return rows.get(0);
    }

    private static Observations evaluated(Answering answering) {
        Compilation c = compiled();
        String name = c.modules().get(0);
        return ExampleVerifier.check(
                c.db().ask(new Shapes.Prepared(name)).value().forExamples(),
                Scopes.derived(c.db(), name).value(),
                Shapes.publishedDeclarations(c.db()),
                Shapes.declarationKinds(c.db()),
                souther.compiler.query.ExampleExecutions.of(c.db(), name).fieldTypes(),
                c.db().ask(new Bodies.Reachable(name)).value(),
                artifactOf(c, name),
                // Every answer here applies this compile's own classes, so nothing is held against
                // this module's declarations and they are never read.
                () -> {
                    throw new AssertionError("an answer of this compile's own read declarations");
                },
                c.db().ask(new Bodies.Requirements(name)).value(),
                ExampleVerifier.class.getClassLoader(),
                c.db().ask(new Bodies.ModuleDefinitions(name)).value(),
                JvmDeadlines.of(EvaluationPolicy.DEFAULT.compilerTimeout()),
                EvaluationPolicy.DEFAULT,
                answering,
                CheckedEnsures.executableOf(
                        c.db().ask(new Bodies.ReachableContracts(name)).value()));
    }

    private static Compilation compiled() {
        Compilation c = Compilation.ofSource(MODEL, "Main");
        c.db().ask(new Output.All());
        return c;
    }

    private static EvaluationArtifact artifactOf(Compilation c, String name) {
        return c.db().ask(new Output.EvaluationLinked(name, ArmObservation.OMIT)).value();
    }
}
