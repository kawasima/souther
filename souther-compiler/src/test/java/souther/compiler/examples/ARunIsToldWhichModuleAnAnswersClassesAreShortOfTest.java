package souther.compiler.examples;

import souther.compiler.check.CheckedEnsures;
import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.execute.jvm.JvmDeadlines;
import souther.compiler.observe.Observations;
import souther.compiler.observe.ArmObservation;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Note;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.diag.msg.Message;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.generated.EvaluationArtifact;
import souther.compiler.meta.ModulePath;
import souther.compiler.meta.PublishedClasses;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.query.Shapes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run whose answer was built without one of the modules it reads is told which module that is.
 *
 * <p>The plainest way an answer's classes come up short, and the one nothing said anything about.
 * They carry the module being evaluated and not the module its import line names, so the module
 * cannot be read here — and every way a reading stops came out as one sentence, that what was
 * published cannot be read by this compiler. There was a module to put where those classes are read
 * from, and nothing named it.
 *
 * <p>What is held is the whole of what a reader is told, and not only that the reason survived as a
 * value. Three things reach the row: whose declarations could not be read, why they could not, and
 * what there is to do about it — and the last is the one that turns on this. Classes short of a
 * module are classes short of something, and the artifact naming it is not at fault; classes
 * carrying something wrong are, and telling somebody to rebuild the first is sending them to fix
 * what was never broken.
 */
class ARunIsToldWhichModuleAnAnswersClassesAreShortOfTest {

    /** What the model's type is declared by. */
    private static final String SHARED = """
            module example.shared exposing ( Title )
            import String ( length )

            data Title = String
                invariant length(value) > 0
            """;

    /** The module the rows are written for, which reads that type through an import line. */
    private static final String ROOT = """
            module example.root
            import example.shared ( Title )

            behavior shout : (t: Title) -> Title
                constructs Title

            let shout (t) = Title(t.value)

            example shout
              | "one" : (Title("aaaa")) -> Title("aaaa")
            """;

    /**
     * The answer's classes carry the module and not what its import line names.
     *
     * <p>Its reason reaches the row — which module the line names — and so does what there is to do
     * about it, which for classes that are short of a module is to build the answer against a path
     * that has it rather than to rebuild an artifact nothing is wrong with.
     */
    @Test
    void aModuleTheAnswersClassesLeaveOutIsNamedToTheRowAndSoIsWhatToDo() {
        Answering short0 = answeringFrom(without("example.shared"));

        List<Diagnostic> failures = evaluated(short0).failures();

        assertEquals(1, failures.size(), "one row, and one thing it could not be held to");
        List<Message> told = noted(failures.get(0));
        assertTrue(told.contains(
                        new ExampleMessage.WhatItPublishedCannotBeReadHere("example.root")),
                "whose declarations could not be read: " + told);
        assertTrue(told.contains(
                        new ModuleMessage.AnImportLineOfItsCannotBeReadHere("example.shared")),
                "why they could not, naming the module that is short: " + told);
        assertTrue(told.contains(
                        new ExampleMessage.BuildWhatAnswersItAgainstAPathThatCarries(
                                "example.shared")),
                "and what there is to do about it, which is to supply that module: " + told);
    }

    /**
     * A line refused for anything else is told to build the artifact again.
     *
     * <p>The control that holds the line where it is drawn. This stops the same reading at the same
     * place as the one above — an import line of {@code example.root} that could not do its job,
     * naming the same module — and the classes are not short of anything: the module is there and
     * does not expose what the line asks for, which is the artifact being wrong about it. So the
     * reason is the same sentence and what there is to do is not, and a run that answered the two
     * alike would pass the test above by supplying a module for every refused line.
     */
    @Test
    void aLineRefusedForAnythingElseIsToldToBuildTheArtifactAgain() {
        Answering refused = answeringFrom(withSharedKeepingItsTypeToItself());

        List<Message> told = noted(evaluated(refused).failures().get(0));

        assertTrue(told.contains(
                        new ModuleMessage.AnImportLineOfItsCannotBeReadHere("example.shared")),
                "the same reason, naming the same module: " + told);
        assertTrue(told.contains(
                        new ExampleMessage.BuildWhatAnswersItAgainstThisRevision("example.root")),
                "and a different thing to do, because there is nothing to supply: " + told);
        assertTrue(told.stream().noneMatch(
                        said -> said instanceof ExampleMessage.BuildWhatAnswersItAgainstAPathThatCarries),
                "so nobody is sent to add a module to a path: " + told);
    }

    /**
     * An answer whose classes carry something wrong is told to build it again.
     *
     * <p>The same control one level out, over the failure rather than over the line: this one never
     * reaches an import line at all.
     */
    @Test
    void anArtifactThatIsWrongRatherThanShortIsToldToBeBuiltAgain() {
        Answering unreadable = answeringFrom(carryingNoDeclarationsOf("example.root"));

        List<Message> told = noted(evaluated(unreadable).failures().get(0));

        assertTrue(told.contains(
                        new ExampleMessage.BuildWhatAnswersItAgainstThisRevision("example.root")),
                "there is nothing to supply, so the artifact is built again: " + told);
        assertTrue(told.stream().noneMatch(
                        said -> said instanceof ExampleMessage.BuildWhatAnswersItAgainstAPathThatCarries),
                "and nobody is sent to add a module to a path: " + told);
    }

    /** A build of the shared module that declares the type and does not expose it. */
    private static final String KEEPING_IT = """
            module example.shared exposing ( Note )
            import String ( length )

            data Title = String
                invariant length(value) > 0

            data Note = String
            """;

    /** The good build, with the shared module's classes taken from one that keeps its type to
     *  itself — two builds, which is what an answer brought from elsewhere is. */
    private static PublishedClasses withSharedKeepingItsTypeToItself() {
        PublishedClasses both = declarationsOf(List.of(SHARED, ROOT));
        PublishedClasses keeping = declarationsOf(List.of(KEEPING_IT));
        return binaryName -> binaryName.equals("example.shared")
                || binaryName.startsWith("example.shared.")
                ? keeping.of(binaryName) : both.of(binaryName);
    }

    /** What the report says under what it says, which is what the reader is told. */
    private static List<Message> noted(Diagnostic said) {
        return said.notes().stream().map(Note::said).toList();
    }

    /** The two modules built together, with everything of {@code module} taken off. */
    private static PublishedClasses without(String module) {
        PublishedClasses both = declarationsOf(List.of(SHARED, ROOT));
        return binaryName -> binaryName.equals(module) || binaryName.startsWith(module + ".")
                ? new PublishedClasses.Carried.NoSuchClass() : both.of(binaryName);
    }

    /** The same, with {@code module}'s own declarations class carrying nothing this compiler
     *  reads — an artifact that is wrong rather than one that is short. */
    private static PublishedClasses carryingNoDeclarationsOf(String module) {
        PublishedClasses both = declarationsOf(List.of(SHARED, ROOT));
        return binaryName -> binaryName.startsWith(module + ".")
                ? new PublishedClasses.Carried.UnreadableMetadata() : both.of(binaryName);
    }

    /** An answerer saying its answers read values by {@code theirs}, and never handed a row. */
    private static Answering answeringFrom(PublishedClasses theirs) {
        Answerer answerer = behavior -> new Answerer.Answer.Something() {

            @Override
            public Origin origin() {
                return new Origin.Published(theirs);
            }

            @Override
            public Answerer.Applying applying(List<DependencyStandin> standins) {
                throw new AssertionError("a row was handed to an answer nothing could establish");
            }
        };
        return (generated, compiled) -> answerer;
    }

    /** The rows of {@code ROOT}, run against {@code answering}. */
    private static Observations evaluated(Answering answering) {
        Compilation c = Compilation.ofSources(List.of(SHARED, ROOT), ModulePath.EMPTY);
        c.db().ask(new Output.All());
        String name = "example.root";
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
                () -> declarationsOf(List.of(SHARED, ROOT)),
                c.db().ask(new Bodies.Requirements(name)).value(),
                ExampleVerifier.class.getClassLoader(),
                c.db().ask(new Bodies.ModuleDefinitions(name)).value(),
                JvmDeadlines.of(EvaluationPolicy.DEFAULT.compilerTimeout()),
                EvaluationPolicy.DEFAULT,
                answering,
                CheckedEnsures.executableOf(
                        c.db().ask(new Bodies.ReachableContracts(name)).value()));
    }

    /** The classes one build of these sources emits, read for what they were stamped with. */
    private static PublishedClasses declarationsOf(List<String> sources) {
        Compilation compiled = Compilation.ofSources(sources, ModulePath.EMPTY);
        Map<String, ClassFileImage> classes = compiled.db().ask(new Output.All()).value();
        return ModulePath.of(classes).declarations();
    }
}
