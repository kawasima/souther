package souther.compiler.execute;

import souther.compiler.observe.ArmObservation;
import souther.compiler.observe.RowRun;
import souther.compiler.observe.StatementReading;
import souther.compiler.observe.TableBuild;
import souther.compiler.source.SourceId;

/**
 * The questions the language can only answer by running the program.
 *
 * <p>Two of the things deciding whether Souther accepts a program need the program to run: a
 * constant construction has to satisfy the invariant of what it builds, and an {@code example} row
 * has to hold — in Souther a row that disagrees is a compile error and not a test failure. ADR-0032
 * settles how they are run: by the same program that will ship, with no second evaluator that could
 * disagree. That is not what this is about. This is about which way the dependency runs.
 *
 * <p>Acceptance asked those questions of the JVM. It reflected over a generated class name, built
 * class loaders, and handed an artifact of emitted classes to the thing that runs rows, so a caller
 * with nothing to do with the JVM went through it anyway and a program the JVM could not emit was
 * refused whether or not the language had anything against it. Asked here instead, the questions
 * are the language's and the JVM is one implementation of the answering.
 *
 * <p>What crosses, in both directions, is what the language asked and what happened. No
 * {@code Db}, no {@code Compilation}, no artifact of emitted classes, no class loader, no generated
 * class, and no bare {@code Object} value — in what this is asked as well as in what it answers. A
 * capability asked in the machine's words is the same dependency with an interface in front of it;
 * that is what the walk over this boundary refuses, and refusing it in the answers alone would
 * leave half of it standing.
 *
 * <p>Two shapes of answer, and which one a question takes is not a matter of taste. A question
 * about what happened answers with what happened, and says in its own type that it was not done
 * here — {@code Holds}, {@code NotRunHere}, {@code NotBuiltHere} — because "nothing was wrong" and
 * "nothing was tried" are the two a caller must never read as one. A question asking for a way to
 * go on asking answers with that way, or with nothing: there is no outcome to report yet, and the
 * absence is that there is no operation to ask. {@link #values} and {@link #trials} are the second
 * kind and answer null; the other four are the first.
 *
 * <p>It does not say which implementation runs. ADR-0032 does, and today the answer is the
 * generated JVM program. If that is ever re-opened, what it takes is a second implementation of
 * this rather than taking the example subsystem apart; and if it never is, the arrangement still
 * reads correctly — the policy is then a statement about which implementation is used, rather than
 * a shape the whole subsystem is built into.
 */
public interface ProgramExecution {

    /**
     * Whether {@code written} satisfies the invariant of the type it builds.
     *
     * <p>Answered by running the check the compile has for that type, which is the check a
     * construction at run time would run. Where it cannot be run here the answer says so and the
     * run-time check stands.
     */
    ConstantOutcome check(ConstantConstruction written);

    /**
     * What running the rows written in {@code source} came to.
     *
     * <p>The rows of one file, and the module they are rows of, are both in {@code asked}: they came
     * from one preparation, so which module's program a row is run against is not something a caller
     * can state wrongly here.
     *
     * <p>{@code arms} is what the run is to record beside the counting, which is the one thing that
     * varies between running a module's rows to compile it and running them to measure it. It is
     * said in what the run is to observe rather than in what it is to be built from, because which
     * classes those are is not a fact about the program.
     */
    RowRun run(ExampleExecution asked, SourceId source, ArmObservation arms);

    /**
     * What building the tables the {@code fake} rows of {@code source} state says about them.
     *
     * <p>No behavior is applied. A fake states what stands in for one while some other behavior's
     * row runs, and building the value it names runs the decoders the module's types derive and the
     * definitions its rows reach — which are classes a compile produced, which is why this is asked
     * here at all. It is not a run of the model.
     *
     * <p>Answers that nothing was built only where there was something to build. Every block here
     * whose target reached a behavior is built, whether or not it is the one standing in for that
     * behavior, and a source that writes none of them built all none of them.
     */
    TableBuild fakeTables(ExampleExecution asked, SourceId source);

    /**
     * What the module's written statements about one behavior say about each other.
     *
     * <p>No behavior is applied here either, and asked of the module rather than of a source: a
     * module's fakes are what its attached files' rows are recorded against and the other way round,
     * so the two sides of one disagreement need not be written in one file.
     */
    StatementReading statements(ExampleExecution asked);

    /**
     * A way to build the values a row would carry at this module's boundary, or none where there is
     * nothing to build them against.
     *
     * <p>Not part of deciding whether the language accepts the program. What asks this is the search
     * that offers a row for a gap nothing covers: which values a type admits together is the derived
     * decoder's answer, so a candidate has to go through the decoder a written row's fixture goes
     * through or it is a guess.
     */
    BoundaryValues values(ExampleExecution asked);

    /**
     * A way to run rows nobody wrote and see where they went, or none where nothing records it.
     *
     * <p>{@code arms} is what the run is to record, as it is for {@link #run}. A trial exists to
     * find out which arms a candidate reaches, so a run recording nothing answers a search with
     * every candidate having reached nothing — which reads as a row that missed rather than as a
     * measurement that was not made.
     */
    RowTrials trials(ExampleExecution asked, ArmObservation arms);
}
