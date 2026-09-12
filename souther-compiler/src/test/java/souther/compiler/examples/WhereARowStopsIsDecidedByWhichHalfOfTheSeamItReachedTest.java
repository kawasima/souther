package souther.compiler.examples;

import souther.compiler.check.CheckedEnsures;
import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.execute.jvm.JvmDeadlines;
import souther.compiler.observe.Observations;
import souther.compiler.observe.ArmObservation;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.observe.Applied;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.FailurePhase;
import souther.compiler.observe.RowOutcome;
import souther.compiler.observe.Stage;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;
import souther.compiler.query.Shapes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What applies a behavior is asked in two steps, and which of them a row got through is what its
 * outcome says about it.
 *
 * <p>Making a stand-in into something the behavior can be constructed with is building the environment
 * the behavior runs in. A row that could not get one never entered the behavior, and says so: it stops
 * where a row whose dependency has nothing standing in for it stops, and nothing applied it. A row
 * that did get one and then met a failure entered the behavior, and says that instead.
 *
 * <p>Held here because the two steps are what keeps that true. Asked as one call, a stand-in that could
 * not be made would be found after the row had already been recorded as having entered the behavior,
 * and every reader of the outcome would be told a row ran that never did.
 */
class WhereARowStopsIsDecidedByWhichHalfOfTheSeamItReachedTest {

    private static final String MODEL = """
            module example.twohalves

            data 会員ID = String
            data 見つかった = { id: 会員ID }
            data 見つからない = { なぜ: String }

            data 注文 = { by: 会員ID }
            data 受理 = { by: 会員ID }
            data 拒否 = { なぜ: String }

            behavior 会員を探す : (id: 会員ID) -> 見つかった | 見つからない

            behavior 受け付ける : (注文: 注文) -> 受理 | 拒否
                depends on 会員を探す
                constructs 受理, 拒否

            let 受け付ける (注文, 会員を探す) = match 会員を探す(注文.by) with
                | 見つかった  -> 受理 { by = 注文.by }
                | 見つからない -> 拒否 { なぜ = "unknown" }

            fake 会員を探す
              | (会員ID("m-1")) -> 見つかった { id = 会員ID("m-1") }

            example 受け付ける
              | "a member that is known" : (注文 { by = 会員ID("m-1") }) -> 受理 { by = 会員ID("m-1") }
            """;

    /** The row states a stand-in, and it could not be made. Nothing was applied. */
    @Test
    void aStandinThatCouldNotBeMadeStopsTheRowBeforeItEntersTheBehavior() {
        Observations observed = evaluated(REFUSING);

        RowOutcome row = only(observed);
        assertEquals(Stage.FIXTURES_VALIDATED, row.stage(),
                "the fixtures were validated and the behavior was never entered");
        assertEquals(Disposition.FAILED, row.disposition());
        assertEquals(FailurePhase.FAKE_RESOLUTION, row.failurePhase(),
                "the same place a row whose dependency has no fake stops");
        assertInstanceOf(Applied.Nothing.class, row.run().applied(),
                "a row that did not enter the behavior says nothing applied it");
        assertEquals(1, observed.failures().size());
        ExampleMessage.TheFakeCouldNotBeBuilt said = assertInstanceOf(
                ExampleMessage.TheFakeCouldNotBeBuilt.class, observed.failures().get(0).said(),
                "said as a fake that could not be built, where the fake is written");
        assertEquals("会員を探す", said.dependency(), "naming the dependency it stood in for");
        assertTrue(said.why().contains("said by the test"),
                "and carrying the answerer's reason rather than one made up here");
    }

    /** The stand-in was made, and the behavior it was made for failed. The row entered it. */
    @Test
    void aFailureFromTheBehaviorStopsTheRowInsideIt() {
        Observations observed = evaluated(FAILING);

        RowOutcome row = only(observed);
        assertEquals(Stage.INVOKED, row.stage(), "the behavior was entered");
        assertEquals(Disposition.FAILED, row.disposition());
        assertEquals(FailurePhase.INVOCATION, row.failurePhase());
        assertInstanceOf(Applied.GeneratedHere.class, row.run().applied(),
                "and what entered it is on the record");
    }

    /**
     * The implementation could not be reached, so nothing applied the row.
     *
     * <p>The other half of the same invariant. A row is marked as having entered the behavior before
     * the answerer is asked, because that is the one thing a reader of a row that never comes back
     * can be given; an answerer that comes back saying it never got in takes the mark with it.
     */
    @Test
    void anImplementationThatCouldNotBeReachedLeavesTheRowHavingAppliedNothing() {
        Observations observed = evaluated(UNREACHABLE);

        RowOutcome row = only(observed);
        assertEquals(Stage.FIXTURES_VALIDATED, row.stage(), "it never got in");
        assertInstanceOf(Applied.Nothing.class, row.run().applied(),
                "so nothing is recorded as having applied it");
        assertEquals(Disposition.FAILED, row.disposition());
        assertEquals(FailurePhase.INVOCATION, row.failurePhase());
        assertEquals(1, observed.failures().size(),
                "and the row is told, in the words it was told while this and the applied code"
                        + " failing arrived as one throw");
    }

    /**
     * The row could not be put in the form the answerer reads, so it is undecided.
     *
     * <p>What is held is what the row makes of a crossing that failed, not the path that produces
     * one: a row's input is built by this compile's own decoders and is always readable back, so
     * nothing here can make {@link Handed#neutral} fail for real. Nothing about the row was
     * established, so no diagnostic is said about a model that may be right — and it does not take
     * the module's evaluation down with it, which is what it did while nothing caught it.
     */
    @Test
    void aRowThatCouldNotBeHandedOverIsUndecidedRatherThanFailed() {
        Observations observed = evaluated(UNCROSSABLE);

        RowOutcome row = only(observed);
        assertEquals(Stage.FIXTURES_VALIDATED, row.stage(), "it was never handed over");
        assertEquals(Disposition.INCOMPLETE, row.disposition(),
                "so nothing about it was established");
        assertEquals(FailurePhase.VALUE_CROSSING, row.failurePhase());
        assertInstanceOf(Applied.Nothing.class, row.run().applied());
        assertEquals(List.of(), observed.failures(),
                "and nothing is said about the model, which may be right");
        assertEquals(1, observed.incompleteness().size(),
                "the row is reported as one that could not be decided");
    }

    /**
     * Two behaviors of one module, and what applied them recorded one each.
     *
     * <p>What applies a behavior is settled per behavior — a module declares one with a body and one
     * with none, and only the second can have an implementation supplied for it — so an answerer that
     * resolves between them has more than one answer to what applied a row. Asked once for the whole
     * of itself, it could name only one of the two and every row would carry that one.
     */
    @Test
    void whatAppliedARowIsTheBehaviorsAnswerAndNotTheAnswerersOne() {
        Map<String, Applied> perBehavior = new LinkedHashMap<>();
        Answerer resolving = behavior -> {
            Applied its = perBehavior.computeIfAbsent(behavior, _ -> new Applied.GeneratedHere());
            return ofThisCompile(_ -> new Answerer.Applying() {

                @Override
                public Applied applied() {
                    return its;
                }

                @Override
                public Object to(List<Handed> arguments) {
                    throw new InvocationFailure(new IllegalStateException("stopped by the test"));
                }
            });
        };

        List<RowOutcome> rows = evaluated(TWO_BEHAVIORS, resolving).rows();

        assertEquals(2, rows.size());
        assertEquals(2, perBehavior.size(), "the answerer was asked once per behavior");
        for (RowOutcome row : rows) {
            assertSame(perBehavior.get(row.target()), row.run().applied(),
                    "each row records the answer for the behavior it is about");
        }
        assertNotSame(rows.get(0).run().applied(), rows.get(1).run().applied(),
                "so two behaviors of one module do not carry one answer between them");
    }

    private static final String TWO_BEHAVIORS = """
            module example.twobehaviors

            data Amount = Int

            behavior double : (a: Amount) -> Amount
                constructs Amount

            let double (a) = Amount(a.value * 2)

            behavior treble : (a: Amount) -> Amount
                constructs Amount

            let treble (a) = Amount(a.value * 3)

            example double
              | "twice" : (Amount(1)) -> Amount(2)

            example treble
              | "thrice" : (Amount(1)) -> Amount(3)
            """;

    /** A stand-in the answerer will not make. What it could not make is the whole of what it says. */
    private static final Answerer REFUSING = something(_ -> standins -> {
        assertEquals(1, standins.size(), "the row does state a stand-in");
        throw new StandinNotBuilt(standins.get(0).dependency(),
                "its base subclass could not be built: (said by the test)");
    });

    /** A behavior that stops itself. Read the way the applied code stopping itself is always read. */
    private static final Answerer FAILING = something(_ -> _ -> applying(_ -> {
        throw new InvocationFailure(new IllegalStateException("it stopped itself"));
    }));

    /** An answerer whose implementation is not where it looked. */
    private static final Answerer UNREACHABLE = something(behavior -> _ -> applying(_ -> {
        throw new ImplementationNotReached("no class of that name (said by the test)",
                new ClassNotFoundException(behavior));
    }));

    /**
     * An answerer that cannot put a value in the form it reads.
     *
     * <p>Raises what {@link Handed#neutral} raises rather than reading one, because a row's input is
     * built by this compile's own decoders and is always readable back. What is held is what the row
     * makes of a crossing that failed, which is the same whichever of the two raised it.
     */
    private static final Answerer UNCROSSABLE = something(_ -> _ -> applying(_ -> {
        throw new FixtureException("`x` is a Nothing, which is not a type this example can read");
    }));

    /** An answerer that has something for every behavior it is asked about, built from what that
     * something does with the row's stand-ins. What it crosses into is this compile's own
     * declarations: these answerers apply nothing of any other build, and what they are written to
     * hold is where a row stops. */
    private static Answerer something(
            Function<String, Function<List<DependencyStandin>, Answerer.Applying>> per) {
        return behavior -> ofThisCompile(per.apply(behavior));
    }

    /** An answer applying this compile's classes, which does {@code applies} with the stand-ins. */
    private static Answerer.Answer.Something ofThisCompile(
            Function<List<DependencyStandin>, Answerer.Applying> applies) {
        return new Answerer.Answer.Something() {

            @Override
            public Origin origin() {
                return new TheCompilesOwn();
            }

            @Override
            public Answerer.Applying applying(List<DependencyStandin> standins) {
                return applies.apply(standins);
            }
        };
    }

    /** An application that says this compile's classes entered the behavior, and runs {@code body}. */
    private static Answerer.Applying applying(Function<List<Handed>, Object> body) {
        return new Answerer.Applying() {

            @Override
            public Applied applied() {
                return new Applied.GeneratedHere();
            }

            @Override
            public Object to(List<Handed> arguments) {
                return body.apply(arguments);
            }
        };
    }

    private static RowOutcome only(Observations observed) {
        assertEquals(1, observed.rows().size());
        return observed.rows().get(0);
    }

    /** The row, evaluated the way the query asks it, against an answerer the test chooses. */
    private static Observations evaluated(Answerer answerer) {
        return evaluated(MODEL, answerer);
    }

    private static Observations evaluated(String model, Answerer answerer) {
        Compilation c = Compilation.ofSource(model, "Main");
        c.db().ask(new Output.All());
        String name = c.modules().get(0);
        souther.compiler.generated.EvaluationArtifact artifact = c.db()
                .ask(new Output.EvaluationLinked(name, ArmObservation.OMIT)).value();
        return ExampleVerifier.check(
                c.db().ask(new Shapes.Prepared(name)).value().forExamples(),
                Scopes.derived(c.db(), name).value(),
                Shapes.publishedDeclarations(c.db()),
                Shapes.declarationKinds(c.db()),
                souther.compiler.query.ExampleExecutions.of(c.db(), name).fieldTypes(),
                c.db().ask(new Bodies.Reachable(name)).value(),
                artifact,
                // The answerers here apply this compile's own classes, so there is no second set of
                // declarations to hold theirs against and this is never asked for.
                () -> {
                    throw new AssertionError("an answer of this compile's own read declarations");
                },
                c.db().ask(new Bodies.Requirements(name)).value(),
                ExampleVerifier.class.getClassLoader(),
                c.db().ask(new Bodies.ModuleDefinitions(name)).value(),
                JvmDeadlines.of(EvaluationPolicy.DEFAULT.compilerTimeout()),
                EvaluationPolicy.DEFAULT,
                (generated, compiled) -> answerer,
                CheckedEnsures.executableOf(
                        c.db().ask(new Bodies.ReachableContracts(name)).value()));
    }
}
