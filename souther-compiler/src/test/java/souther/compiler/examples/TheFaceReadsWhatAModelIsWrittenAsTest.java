package souther.compiler.examples;

import souther.compiler.WhereItSits;
import org.junit.jupiter.api.Test;

import souther.compiler.diag.CompileException;
import souther.compiler.meta.ModulePath;
import souther.compiler.observe.RowIdentity;

import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The face reads a model as a model is written, not as one file that imports nothing.
 *
 * <p>A module may write its rows beside itself and in an {@code examples for} file, and may import
 * another user module. A face taking one source and no dependency path would be narrower than the
 * language it stands in front of: a project using either would find its rows unreachable rather than
 * failing, which is worse than not offering the entrance at all.
 */
class TheFaceReadsWhatAModelIsWrittenAsTest {

    private static final String SHARED = """
            module example.ids exposing ( TodoId )

            data TodoId = Int
            """;

    private static final String MODEL = """
            module example.app

            import example.ids ( TodoId )

            data Title = String
            data Todo = { id: TodoId, title: Title, done: Bool }
            data NotFound = { id: TodoId }

            behavior findTodo : (id: TodoId) -> Todo | NotFound
            """;

    /** The rows, written where the language says they may be written and not beside the module. */
    private static final String COMPANION = """
            examples for example.app

            example findTodo
                | "a todo that is stored" : (TodoId(1))
                    -> Todo { id = TodoId(1), title = Title("write the SQL"), done = false }
                | (TodoId(99)) -> NotFound { id = TodoId(99) }
            """;

    private static final String IMPL = """
            package example.app;
            import example.ids.TodoId;
            public final class FindTodoImpl extends FindTodo {
                public FindTodoResult apply(TodoId id) {
                    // Another module's newtype arrives and is passed on: its constructor is not
                    // this package's to call, which is the position a real implementation is in.
                    return id.value() == 1L
                            ? new Todo(id, new Title("write the SQL"), false)
                            : new NotFound(id);
                }
            }
            """;

    /**
     * A module importing another user module, with its rows in an {@code examples for} file.
     *
     * <p>Both at once, because both were unreachable for one reason: the face read one source and
     * gave the compile an empty path.
     */
    @Test
    void aModuleThatImportsAnotherAndKeepsItsRowsBesideItRuns() throws Exception {
        BoundExamples examples = bound();

        List<RecordedRow> rows = examples.rows();
        assertEquals(2, rows.size(), "the companion file's rows are the module's rows");
        for (RecordedRow row : rows) {
            assertTrue(examples.evaluate(row).held(), row.shown());
        }
    }

    /** Which module the rows are of is the binding's answer, not the order the files came in. */
    @Test
    void whichModuleIsBoundComesFromTheImplementation() throws Exception {
        SoutherExamples model = SoutherExamples.ofSources(List.of(SHARED, MODEL, COMPANION),
                ModulePath.EMPTY);

        assertTrue(model.modules().containsAll(List.of("example.ids", "example.app")),
                model.modules().toString());
        assertEquals(List.of("findTodo"), model.bind(implementation()).boundBehaviors());
    }

    /** A source that does not compile is refused with what the compiler says about it. */
    @Test
    void aModelThatDoesNotCompileIsRefusedWithItsDiagnostics() {
        String source = """
                module example.broken

                data Todo = { id: NoSuchType }
                """;
        CompileException refused = assertThrows(CompileException.class,
                () -> SoutherExamples.ofSource(source));

        assertFalse(refused.diagnostics().isEmpty(), "the diagnostics are kept, not their codes");
        assertEquals(refused.diagnostics().size(), refused.locatedDiagnostics().size(),
                "and each is still located in the source it is about");
        assertTrue(WhereItSits.in(source, refused.pos()).line() > 0, "with the position the compiler found it at");
    }

    /**
     * A key is made where it is resolved, and nowhere else.
     *
     * <p>The type closes it: there is no public constructor, so a name nothing answers to cannot be
     * written past the resolution and left to match no row. That is the setup that silently never
     * runs, which is what this type exists to prevent.
     */
    @Test
    void aKeyCannotBeWrittenForARowNothingAnswersTo() throws Exception {
        BoundExamples examples = bound();

        assertThrows(IllegalArgumentException.class,
                () -> examples.rowKey("findTodo", "a todo that was never written"));
        assertEquals(0, RowKey.class.getConstructors().length,
                "nothing outside the package makes one");

        RowKey stored = examples.rowKey("findTodo", "a todo that is stored");
        assertTrue(stored.is(examples.rows().get(0)));
        assertFalse(stored.is(examples.rows().get(1)));
    }

    /** A key of one enumeration is refused by another rather than quietly matching nothing. */
    @Test
    void aKeyOfOneBindingIsRefusedByAnothersRows() throws Exception {
        RowKey stored = bound().rowKey("findTodo", "a todo that is stored");
        RecordedRow elsewhere = bound().rows().get(0);

        assertThrows(IllegalArgumentException.class, () -> stored.is(elsewhere));
    }

    /** An unnamed row is enumerated and has no address, which is the pair `RowKey` was split for. */
    @Test
    void anUnnamedRowIsRunnableAndUnaddressable() throws Exception {
        BoundExamples examples = bound();

        RecordedRow unnamed = examples.rows().get(1);
        assertEquals(new RowIdentity.Unnamed(2), unnamed.identity());
        assertTrue(examples.evaluate(unnamed).held());
    }


    /**
     * A lone file may leave its `module` header off, as it may when the compiler is handed it.
     *
     * <p>Reading one file the way several are read refuses it: linking several sources needs each to
     * say which module it is, and a face that took the many-source route for a single file would be
     * narrower than the language it stands in front of. That narrowing was introduced by the fix for
     * the source-set finding and is what this holds.
     */
    @Test
    void aLoneFileMayLeaveItsModuleHeaderOff(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("todo.sou");
        Files.writeString(source, """
                data TodoId = Int
                data NotFound = { id: TodoId }

                behavior findTodo : (id: TodoId) -> NotFound
                """);

        assertEquals(List.of("todo"), SoutherExamples.of(source).modules(),
                "and is called what the file is");
        assertEquals(List.of("Main"), SoutherExamples.ofSource(Files.readString(source)).modules(),
                "or `Main`, where the caller held the text rather than a file");
    }

    /**
     * What a dependency published is read from the path, which is how every other reader of a
     * published module reads one.
     *
     * <p>The other half of the source-set finding, and the half a source set cannot stand in for: a
     * project depends on modules it does not compile.
     */
    @Test
    void anImportedModuleIsReadFromTheDependencyPath(@TempDir Path dir) throws Exception {
        Path published = Files.createDirectory(dir.resolve("dependency"));
        for (var e : souther.compiler.Compiler.compileModules(List.of(SHARED)).entrySet()) {
            Path at = published.resolve(e.getKey().replace('.', '/') + ".class");
            Files.createDirectories(at.getParent());
            Files.write(at, e.getValue().bytes());
        }
        Path model = dir.resolve("app.sou");
        Files.writeString(model, MODEL);
        Path companion = dir.resolve("app.examples.sou");
        Files.writeString(companion, COMPANION);

        SoutherExamples read = SoutherExamples.of(List.of(model, companion),
                ModulePath.ofClassPath(List.of(published)));

        assertEquals(List.of("example.app"), read.modules(),
                "`example.ids` is a dependency and not one of these sources");
        BoundExamples examples = read.bind(implementation());
        assertEquals("example.app", examples.moduleName());
        for (RecordedRow row : examples.rows()) {
            assertTrue(examples.evaluate(row).held(), row.shown());
        }
    }

    /** A refusal names the file the reader is looking at. */
    @Test
    void aRefusalNamesTheFileItIsAbout(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("broken.sou");
        Files.writeString(source, "data Todo = { id: NoSuchType }\n");

        CompileException refused = assertThrows(CompileException.class,
                () -> SoutherExamples.of(source));

        assertEquals(source.toString(), refused.sourceId().value(),
                "and not the number this compile held it under");
    }


    /**
     * A behavior that implements itself is not one a binding makes runnable.
     *
     * <p>Its rows were runnable before anything was bound and are run by its own body where a
     * compile runs them. Answering one from a supplied instance would not be running a recorded row
     * against an implementation — it would be replacing the model's own with another and reporting
     * the difference as the model's. Whether that is a thing to offer is its own question.
     *
     * <p>Refused where the binding is made, so a caller who bound the wrong instance is told what is
     * wrong with the instance rather than that some row did not hold.
     */
    @Test
    void aBehaviorThatImplementsItselfIsNotBound() throws Exception {
        String withABody = """
                module example.own

                data TodoId = Int
                data NotFound = { id: TodoId }

                behavior findTodo : (id: TodoId) -> NotFound
                    constructs NotFound

                let findTodo (id) = NotFound { id = id }

                example findTodo
                    | "anything at all" : (TodoId(1)) -> NotFound { id = TodoId(1) }
                """;
        String replacing = """
                package example.own;
                public final class FindTodoImpl implements FindTodo {
                    public NotFound apply(TodoId id) { return null; }
                }
                """;

        SoutherExamples model = SoutherExamples.ofSource(withABody);
        Object instance = builtElsewhere(withABody, "example.own", replacing, "FindTodoImpl");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> model.bind(instance));
        assertTrue(refused.getMessage().contains("has an implementation of its own"),
                refused.getMessage());
    }

    private static BoundExamples bound() throws Exception {
        return SoutherExamples.ofSources(List.of(SHARED, MODEL, COMPANION), ModulePath.EMPTY)
                .bind(implementation());
    }

    /**
     * The one implementation the checks below bind to, built once.
     *
     * <p>Building it runs the system Java compiler over a source written into a temporary
     * directory, and eight of the calls here wanted the same class out of it. The class is what is
     * kept; each caller still gets an instance of its own, so nothing one check does to it is
     * something another can see.
     */
    private static Class<?> built;

    private static Object implementation() throws Exception {
        if (built == null) {
            built = builtElsewhere(null, "example.app", IMPL, "FindTodoImpl").getClass();
        }
        return built.getConstructor().newInstance();
    }

    private static Object builtElsewhere(String only, String pkg, String impl, String named)
            throws Exception {
        Path classes = Files.createTempDirectory("souther-sources");
        List<String> models = only == null ? List.of(SHARED, MODEL) : List.of(only);
        for (var e : souther.compiler.Compiler.compileModules(models).entrySet()) {
            Path at = classes.resolve(e.getKey().replace('.', '/') + ".class");
            Files.createDirectories(at.getParent());
            Files.write(at, e.getValue().bytes());
        }
        Path java = classes.resolve(pkg.replace('.', '/') + "/" + named + ".java");
        Files.createDirectories(java.getParent());
        Files.writeString(java, impl);
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, err,
                "-encoding", "UTF-8",
                "-classpath", classes + File.pathSeparator + System.getProperty("java.class.path"),
                "-d", classes.toString(), java.toString());
        assertEquals(0, rc, "the implementation compiles: " + err);
        URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()},
                SoutherExamples.class.getClassLoader());
        return loader.loadClass(pkg + "." + named).getConstructor().newInstance();
    }
}
