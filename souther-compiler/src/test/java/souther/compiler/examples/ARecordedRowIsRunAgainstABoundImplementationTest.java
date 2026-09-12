package souther.compiler.examples;

import souther.compiler.check.CheckedEnsures;
import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.execute.jvm.JvmDeadlines;
import souther.compiler.observe.Observations;
import souther.compiler.observe.ArmObservation;
import org.junit.jupiter.api.Test;

import souther.compiler.diag.Diagnostic;
import souther.compiler.generated.EvaluationArtifact;
import souther.compiler.meta.PublishedClasses;
import souther.compiler.observe.Applied;
import souther.compiler.coverage.RunRecord;
import souther.compiler.observe.Counting;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.FailurePhase;
import souther.compiler.observe.RowOutcome;
import souther.compiler.observe.Stage;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;
import souther.compiler.query.Scopes;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Shapes;

import javax.tools.ToolProvider;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row recorded against an injected behavior, run against a Java implementation the evaluation was
 * given.
 *
 * <p>ADR-0088 made such a row a record of what the behavior owes: its arity is checked, its inputs
 * are built through their derived decoders and held to their invariants, its expectation is held to
 * the output's cases, and there it stops as {@code PENDING}. What is held here is the other half —
 * the row deciding whether the implementation answers what the model says it owes.
 *
 * <p>The implementation is compiled by {@code javac} against the module's classes written to a
 * directory, and loaded from that directory. That is the position a real one is in and not a
 * simulation of it: it extends a base of a build that is not this run's, its classes are its own
 * loader's, and nothing of this compile's may be handed to it. An implementation given this run's
 * own values fails on the first cast inside itself.
 */
class ARecordedRowIsRunAgainstABoundImplementationTest {

    private static final String MODEL = """
            module example.todo
            import String ( length )

            data TodoId = Int
            data Title = String
                invariant length(value) > 0

            data Todo = { id: TodoId, title: Title, done: Bool }
            data NotFound = { id: TodoId }

            behavior findTodo : (id: TodoId) -> Todo | NotFound

            example findTodo
                | "a todo that is stored" : (TodoId(1))
                    -> Todo { id = TodoId(1), title = Title("write the SQL"), done = false }
                | "an id nothing is stored under" : (TodoId(99))
                    -> NotFound { id = TodoId(99) }
            """;

    /** Answers both rows the way the model says it owes. */
    private static final String ANSWERS = """
            package example.todo;
            public final class FindTodoImpl extends FindTodo {
                public FindTodoResult apply(TodoId id) {
                    return id.equals(new TodoId(1L))
                            ? new Todo(new TodoId(1L), new Title("write the SQL"), false)
                            : new NotFound(id);
                }
            }
            """;

    /** Answers the stored id with the wrong title, which is what a query mapping two columns the
     *  wrong way round does. */
    private static final String MISMAPS = """
            package example.todo;
            public final class FindTodoImpl extends FindTodo {
                public FindTodoResult apply(TodoId id) {
                    return id.equals(new TodoId(1L))
                            ? new Todo(new TodoId(1L), new Title("read the SQL"), false)
                            : new NotFound(id);
                }
            }
            """;

    /**
     * The same model, with the behavior stating a relation between what it is given and what it
     * answers.
     *
     * <p>Nothing in the generated surface carries it: an injected behavior's base class is the same
     * class whether or not a clause is written, so the agent writing the SQL cannot see the contract
     * in what it extends.
     */
    private static final String DECLARING = MODEL.replace(
            "behavior findTodo : (id: TodoId) -> Todo | NotFound\n",
            """
            behavior findTodo : (id: TodoId) -> Todo | NotFound
                ensures Todo -> value.id.value == id.value
            """);

    /** Answers the stored row with a todo carrying an id nobody asked for. */
    private static final String ANSWERS_ABOUT_ANOTHER = """
            package example.todo;
            public final class FindTodoImpl extends FindTodo {
                public FindTodoResult apply(TodoId id) {
                    return id.equals(new TodoId(1L))
                            ? new Todo(new TodoId(7L), new Title("write the SQL"), false)
                            : new NotFound(id);
                }
            }
            """;

    /**
     * What the implementation answered, held to what the behavior declares.
     *
     * <p>This is the only place such an answer is ever checked when the application's own Java is
     * what calls the behavior: there is no body to check in, and a behavior nothing in the module
     * calls has no crossing into generated code either.
     *
     * <p>The row is stopped at {@code ENSURES} and before its own comparison — what the answer
     * disagrees with is the model, and a row told only that it expected one value and saw another
     * would send its author to look at the row.
     */
    @Test
    void whatAnInjectedImplementationAnsweredIsHeldToWhatTheBehaviorDeclares() throws Exception {
        Observations observed = evaluated(DECLARING, ANSWERS_ABOUT_ANOTHER);

        RowOutcome stored = named(observed, "a todo that is stored");
        assertEquals(Disposition.FAILED, stored.disposition());
        assertEquals(FailurePhase.ENSURES, stored.failurePhase(),
                "and not COMPARISON, which is the row disagreeing rather than the model");
        assertTrue(reasons(observed).contains("E1930"), reasons(observed).toString());
    }

    /** The control: the same declaration, kept. */
    @Test
    void anImplementationThatKeepsTheDeclarationIsNotStoppedByIt() throws Exception {
        Observations observed = evaluated(DECLARING, ANSWERS);

        assertEquals(List.of(), reasons(observed));
        for (RowOutcome row : observed.rows()) {
            assertEquals(Disposition.HELD, row.disposition(), row.identity().shown());
        }
    }

    /** The row it owes, held against what the implementation answered. */
    @Test
    void aPendingRowIsDecidedByTheImplementationTheEvaluationWasGiven() throws Exception {
        Observations observed = evaluated(ANSWERS);

        assertEquals(List.of(), reasons(observed), "the implementation answers what it owes");
        assertEquals(2, observed.rows().size());
        for (RowOutcome row : observed.rows()) {
            assertEquals(Disposition.HELD, row.disposition(), row.identity().shown());
            assertEquals(Stage.COMPARED, row.stage());
            assertEquals(FailurePhase.NONE, row.failurePhase());
        }
    }

    /** What applied it is the third arm, and a reader telling the arms apart can. */
    @Test
    void theRowSaysABindingAppliedIt() throws Exception {
        for (RowOutcome row : evaluated(ANSWERS).rows()) {
            assertInstanceOf(Applied.Bound.class, row.run().applied());
        }
    }

    /**
     * A bound row's counting is read, and what it read is the fixtures'.
     *
     * <p>{@link Counting.Unread} is an evaluation given up on; answering it for a row nothing this
     * compile instrumented ran through would put back together the two axes #717 separated. The
     * steps are not zero and that is the same fact from the other side: {@code Title}'s invariant is
     * this compile's code and the row's fixture goes through it, so what is counted covers the
     * fixtures and stops at the behavior — which is injected, has no body, and has nothing to count.
     *
     * <p>And nothing recorded where the row went, which is the other half and is not an empty
     * account of one. This compile was asked to leave the recording calls out, so there is no
     * probed body for the row to be written down by — a measure reading an empty account instead
     * would see a row shown to have reached no arm at all.
     */
    @Test
    void aBoundRowsCountingIsReadAndCoversItsFixturesOnly() throws Exception {
        for (RowOutcome row : evaluated(ANSWERS).rows()) {
            Counting.Read read = assertInstanceOf(Counting.Read.class, row.run().counting(),
                    "the counting was read");
            assertInstanceOf(RunRecord.NoAccount.class, read.recorded(),
                    "and nothing recorded where the row went: the implementation is bound from"
                            + " outside this compile, so there is no probed body to write anything"
                            + " down — which is not the same as a run that lit no branch");
        }
    }

    /** The row that would catch the defect catches it, and the one that would not stays held. */
    @Test
    void anImplementationThatAnswersOtherwiseFailsTheRowThatSaysSo() throws Exception {
        Observations observed = evaluated(MISMAPS);

        RowOutcome stored = named(observed, "a todo that is stored");
        RowOutcome missing = named(observed, "an id nothing is stored under");
        assertEquals(Disposition.FAILED, stored.disposition());
        assertEquals(FailurePhase.COMPARISON, stored.failurePhase());
        assertEquals(Disposition.HELD, missing.disposition(),
                "the row the defect does not reach is not evidence against it");
        assertTrue(reasons(observed).contains("E1905"),
                "and the row is told the way a row that disagrees is told: " + reasons(observed));
    }


    /**
     * A row that failed says which value differed and where, not only that it failed.
     *
     * <p>Both rows here expect a `Todo` and both answer with one, so the outcome carries `FAILED`,
     * `COMPARISON` and `Todo` on either side — which is the same outcome a right implementation
     * produces for a right row. What tells the two apart is the diagnostic, and answering with the
     * outcome alone would have a consumer report that a row failed while the compiler had the
     * sentence in hand and dropped it.
     */
    @Test
    void aRowThatFailedCarriesWhatWasSaidAboutIt() throws Exception {
        BoundExamples examples = SoutherExamples.ofSource(MODEL)
                .bind(builtElsewhere(compiled(MODEL), MISMAPS));

        RowEvaluation failed = examples.evaluate(examples.rows().get(0));
        assertFalse(failed.held());
        assertEquals(FailurePhase.COMPARISON, failed.outcome().failurePhase());
        assertEquals(List.of("E1905"),
                failed.diagnostics().stream().map(Diagnostic::code).toList());

        String shown = failed.shown(java.util.Locale.ENGLISH);
        assertTrue(shown.contains("read the SQL") && shown.contains("write the SQL"),
                "both values are in what a consumer would print: " + shown);

        RowEvaluation held = examples.evaluate(examples.rows().get(1));
        assertTrue(held.held());
        assertEquals(List.of(), held.diagnostics(), "a row that held has nothing said about it");
    }

    /**
     * A row not handed over says why, and not only where it stopped.
     *
     * <p>A bulk run says this once for the behavior and every row of it is in one report; a row
     * handed over on its own is the only place its reader looks.
     */
    @Test
    void aRowKeptFromAnImplementationOfAnotherBuildSaysWhy() throws Exception {
        // A build the rows still compile under, and whose `Title` admits fewer values than the one
        // the implementation was compiled against.
        String narrowed = MODEL.replace("    invariant length(value) > 0\n",
                "    invariant length(value) > 3\n");
        BoundExamples examples = SoutherExamples.ofSource(narrowed)
                .bind(builtElsewhere(compiled(MODEL), ANSWERS));

        RowEvaluation kept = examples.evaluate(examples.rows().get(0));
        assertEquals(FailurePhase.ANSWERER_ESTABLISHMENT, kept.outcome().failurePhase());
        assertEquals(List.of("E1927"), kept.diagnostics().stream().map(Diagnostic::code).toList(),
                "which build it is of, said where the row is");
    }


    /**
     * A behavior answering a scalar is held to its clause too.
     *
     * <p>Read off the case the answer turned out to be, this was skipped: a `Long` is no declared
     * type, so there was no case to read it at and the check answered "kept" for every
     * implementation. Every behavior answering `Int`, `String`, `Bool` or a bare collection was
     * unchecked, and the row said `COMPARISON` where the model is what the answer disagrees with.
     */
    @Test
    void ascalarAnswerIsHeldToWhatTheBehaviorDeclares() throws Exception {
        String model = """
                module example.count

                data TodoId = Int

                behavior countFor : (id: TodoId) -> Int
                    ensures value >= id.value

                example countFor
                    | "a todo with no children" : (TodoId(0)) -> 0
                """;
        String breaks = """
                package example.count;
                public final class CountForImpl extends CountFor {
                    public Long apply(TodoId id) { return -1L; }
                }
                """;

        BoundExamples examples = SoutherExamples.ofSource(model)
                .bind(builtElsewhere(compiled(model), breaks, "example/count/CountForImpl.java",
                        "example.count.CountForImpl"));

        RowEvaluation ran = examples.evaluate(examples.rows().get(0));
        assertEquals(FailurePhase.ENSURES, ran.outcome().failurePhase(),
                "and not COMPARISON, which would send its author to look at the row");
        assertEquals(List.of("E1930"), ran.diagnostics().stream().map(Diagnostic::code).toList());
    }

    /** An implementation that stops with a throw rather than answering. */
    private static final String THROWS = """
            package example.todo;
            public final class FindTodoImpl extends FindTodo {
                public FindTodoResult apply(TodoId id) {
                    throw new IllegalStateException("the query would not run");
                }
            }
            """;

    /**
     * What the applied code ended with is read the same whether or not the application crossed back
     * to the thread that asked for the row.
     *
     * <p>A binding drives its rows over the crossing and a run given a deadline of its own applies
     * where it stands. Which of the two a row went through decides where the code ran and nothing
     * else: the failure is the implementation's either way, and a reader deciding whose failure a
     * row met would otherwise be told two different things about one throw.
     */
    @Test
    void anImplementationThatThrowsFailsTheSameWayOnEitherSideOfTheCrossing() throws Exception {
        BoundExamples over = SoutherExamples.ofSource(MODEL)
                .bind(builtElsewhere(compiled(MODEL), THROWS));
        RowOutcome crossed = over.evaluate(over.rows().get(0)).outcome();

        RowOutcome stood = named(evaluated(MODEL, THROWS), crossed.identity().shown());

        assertEquals(stood.disposition(), crossed.disposition());
        assertEquals(stood.stage(), crossed.stage());
        assertEquals(stood.failurePhase(), crossed.failurePhase());

        // And what the two agree on is what a throw from the applied code means, rather than
        // whatever the two happen to arrive at together.
        assertEquals(Disposition.FAILED, crossed.disposition());
        assertEquals(Stage.INVOKED, crossed.stage());
    }

    private static Map<String, ClassFileImage> compiled(String model) {
        Compilation c = Compilation.ofSource(model, "Main");
        return c.db().ask(new Output.All()).value();
    }

    private static RowOutcome named(Observations observed, String name) {
        return observed.rows().stream()
                .filter(r -> r.identity().shown().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("no row named `" + name + "`"));
    }

    private static List<String> reasons(Observations observed) {
        return observed.failures().stream().map(f -> String.valueOf(f.code())).toList();
    }

    /** The module's rows, run against {@code implementation} as a build that is not this one's. */
    private static Observations evaluated(String implementation) throws Exception {
        return evaluated(MODEL, implementation);
    }

    private static Observations evaluated(String model, String implementation)
            throws Exception {
        Compilation c = Compilation.ofSource(model, "Main");
        c.db().ask(new Output.All());
        assertEquals(List.of(), c.diagnostics().values().stream().flatMap(List::stream)
                        .map(d -> String.valueOf(d.diagnostic().code())).toList(),
                "the model whose rows are run compiles");
        String name = c.modules().get(0);
        EvaluationArtifact artifact = c.db()
                .ask(new Output.EvaluationLinked(name, ArmObservation.OMIT)).value();
        assertEquals(List.of(), c.diagnostics().values().stream().flatMap(List::stream)
                        .map(d -> String.valueOf(d.diagnostic().code())).toList(),
                "the model whose rows are run compiles");

        Object bound = builtElsewhere(c.db().ask(new Output.All()).value(), implementation);
        ClassLoader parent = ExampleVerifier.class.getClassLoader();
        return ExampleVerifier.check(
                c.db().ask(new Shapes.Prepared(name)).value().forExamples(),
                Scopes.derived(c.db(), name).value(),
                Shapes.publishedDeclarations(c.db()),
                Shapes.declarationKinds(c.db()),
                souther.compiler.query.ExampleExecutions.of(c.db(), name).fieldTypes(),
                c.db().ask(new Bodies.Reachable(name)).value(),
                artifact,
                declarationsOf(c),
                c.db().ask(new Bodies.Requirements(name)).value(),
                parent,
                c.db().ask(new Bodies.ModuleDefinitions(name)).value(),
                JvmDeadlines.of(EvaluationPolicy.DEFAULT.compilerTimeout()),
                EvaluationPolicy.DEFAULT,
                // What this instance is supplied for, said by the caller. Whether a behavior may be
                // supplied for at all is `SoutherExamples.bind`'s rule; this is the seam below it.
                Answering.bound(bound, java.util.Set.of("findTodo"),
                        c.db().ask(new Bodies.Signatures(name)).value(),
                        // Applied where the row stands. What a binding arranges is that the
                        // implementation answers on the thread that asked for the row, and nothing
                        // here is that binding: this drives the seam under it, so it says outright
                        // that the application does not cross anywhere.
                        CallerApplication.Application::call),
                CheckedEnsures.executableOf(
                        c.db().ask(new Bodies.ReachableContracts(name)).value()));
    }

    /** What the module's rows are written for, as this compile emitted it. */
    private static java.util.function.Supplier<PublishedClasses> declarationsOf(Compilation c) {
        Map<String, ClassFileImage> classes = c.db().ask(new Output.All()).value();
        return () -> ModulePath.of(classes).declarations();
    }

    /**
     * {@code source} compiled against the module's classes and instantiated from a loader of its own.
     *
     * <p>The classes go to a directory rather than a map because that is what makes the loader answer
     * for its own resources: what an implementation's declarations are read from is the class files
     * its loader has, and a loader that serves classes and not resources would leave the reading with
     * nothing to read.
     */
    private static Object builtElsewhere(Map<String, ClassFileImage> generated, String source)
            throws Exception {
        return builtElsewhere(generated, source, "example/todo/FindTodoImpl.java",
                "example.todo.FindTodoImpl");
    }

    private static Object builtElsewhere(Map<String, ClassFileImage> generated, String source,
                                         String written, String named) throws Exception {
        Path classes = Files.createTempDirectory("souther-bound");
        for (Map.Entry<String, ClassFileImage> e : generated.entrySet()) {
            Path at = classes.resolve(e.getKey().replace('.', '/') + ".class");
            Files.createDirectories(at.getParent());
            Files.write(at, e.getValue().bytes());
        }
        Path java = classes.resolve(written);
        Files.createDirectories(java.getParent());
        Files.writeString(java, source);
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8",
                "-classpath", classes + File.pathSeparator + System.getProperty("java.class.path"),
                "-d", classes.toString(), java.toString());
        assertEquals(0, rc, "the implementation compiles against the module's classes");

        // Its own loader, and the module's classes in it: the base it extends is this loader's and
        // not the run's, which is the whole of what a supplied implementation is.
        URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()},
                ExampleVerifier.class.getClassLoader());
        return loader.loadClass(named).getConstructor().newInstance();
    }
}
