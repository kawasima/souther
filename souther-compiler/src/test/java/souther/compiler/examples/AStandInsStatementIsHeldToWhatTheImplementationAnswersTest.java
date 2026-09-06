package souther.compiler.examples;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.observe.ObservedValue;

import javax.tools.ToolProvider;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code fake}'s entries, run against the implementation the behavior was bound to.
 *
 * <p>ADR-0093 compares a stand-in with the behavior's recorded rows and gives neither precedence.
 * What it cannot reach is a faked behavior with no rows of its own — and every faked behavior in the
 * examples repository is injected. Once an implementation is bound, each explicit entry is an input
 * and an answer in the same form a recorded row is, and running them says whether the table a
 * composite's rows dispatched through describes something the implementation produces.
 *
 * <p>What comes back is an observation and not a verdict. A disagreement on its own does not say
 * which side is wrong: a fake may be deliberately written to answer what the real dependency cannot,
 * to reach a composite's error path. So there is no severity here, and a consumer that wants a table
 * held strictly writes a filter.
 */
class AStandInsStatementIsHeldToWhatTheImplementationAnswersTest {

    private static final String MODEL = """
            module example.faked

            data TodoId = Int
            data Title = String
            data Todo = { id: TodoId, title: Title, done: Bool }
            data NotFound = { id: TodoId }

            behavior findTodo : (id: TodoId) -> Todo | NotFound

            fake findTodo
                | (TodoId(1)) -> Todo { id = TodoId(1), title = Title("write the SQL"), done = false }
                | (TodoId(2)) -> Todo { id = TodoId(2), title = Title("harvested long ago"), done = true }
                | _           -> NotFound { id = TodoId(0) }

            example findTodo
                | "a todo that is stored" : (TodoId(1))
                    -> Todo { id = TodoId(1), title = Title("write the SQL"), done = false }
            """;

    /** Stores exactly the one todo the model's own row is written about. */
    private static final String IMPL = """
            package example.faked;
            public final class FindTodoImpl extends FindTodo {
                public FindTodoResult apply(TodoId id) {
                    return id.equals(new TodoId(1L))
                            ? new Todo(new TodoId(1L), new Title("write the SQL"), false)
                            : new NotFound(id);
                }
            }
            """;

    /**
     * The explicit entries and no others.
     *
     * <p>The {@code _} row states no input and is the table's fallback, so it is not one of them.
     */
    @Test
    void theEntriesAreTheOnesDispatchCanAnswerWith() throws Exception {
        List<StandinEntry> entries = bound().standinEntries();

        assertEquals(2, entries.size(), "the `_` row states no input and is not an entry");
        assertEquals(List.of("findTodo", "findTodo"),
                entries.stream().map(StandinEntry::behavior).toList());
        assertEquals(List.of(1, 1), entries.stream().map(e -> e.inputs().size()).toList());
        assertTrue(entries.get(0).shownStated().contains("write the SQL"),
                entries.get(0).shownStated());
    }

    /**
     * What a machine reads and what a person reads are not one String.
     *
     * <p>A caller arranging the world for an entry reads the values; switching on presentation text
     * is the dependence {@link RowKey} exists to remove, and it must not be rebuilt one field over.
     */
    @Test
    void anEntryCarriesItsValuesBesideItsText() throws Exception {
        StandinEntry entry = bound().standinEntries().get(0);

        ObservedValue input = assertInstanceOf(ObservedValue.Constructed.class, entry.inputs().get(0),
                "the input the entry states, as a value");
        assertInstanceOf(ObservedValue.Constructed.class, entry.stated());
        assertEquals(1, entry.shownInputs().size());
        assertTrue(entry.shownInputs().get(0).contains("1"), entry.shownInputs().get(0));
        assertTrue(input.toString().contains("TodoId"), input.toString());
    }

    /**
     * Which recorded rows state an entry's input, which is the static half of an apportionment.
     *
     * <p>The dynamic half — that the row and the entry saw one world — is the caller's, because the
     * caller is the only thing that holds world identity.
     */
    @Test
    void anEntrySaysWhichRecordedRowsStateItsInput() throws Exception {
        List<StandinEntry> entries = bound().standinEntries();

        assertEquals(List.of("a todo that is stored"),
                entries.get(0).alsoBy().stream().map(RecordedRow::shown).toList(),
                "a row of the behavior states this entry's input");
        assertEquals(List.of(), entries.get(1).alsoBy(),
                "and the table states an input no row of the behavior mentions");
    }

    /**
     * The entry the implementation agrees with, and the one it does not.
     *
     * <p>The second is the whole point: nothing was ever written about {@code TodoId(2)} except the
     * table a composite's rows dispatched through, and it says the implementation answers something
     * it does not.
     */
    @Test
    void anEntryIsObservedAsStatedOrOtherwise() throws Exception {
        BoundExamples examples = bound();
        List<StandinEntry> entries = examples.standinEntries();

        assertInstanceOf(StandinObservation.AsStated.class, examples.observe(entries.get(0)),
                "the implementation answers what this entry states");

        StandinObservation.OtherThanStated differs = assertInstanceOf(
                StandinObservation.OtherThanStated.class, examples.observe(entries.get(1)),
                "and not what the other one does");
        assertEquals(entries.get(1).stated(), differs.stated());
        assertInstanceOf(ObservedValue.Constructed.class, differs.answered());
    }

    /** Observing changes nothing, so the same entry gives the same observation. */
    @Test
    void observingAnEntryRetainsNothing() throws Exception {
        BoundExamples examples = bound();
        StandinEntry entry = examples.standinEntries().get(0);

        assertInstanceOf(StandinObservation.AsStated.class, examples.observe(entry));
        assertInstanceOf(StandinObservation.AsStated.class, examples.observe(entry));
    }

    /** An entry belongs to the enumeration that made it. */
    @Test
    void anEntryOfOneBindingIsRefusedByAnother() throws Exception {
        StandinEntry itsOwn = bound().standinEntries().get(0);
        BoundExamples other = bound();

        assertThrows(IllegalArgumentException.class, () -> other.observe(itsOwn));
    }

    private static final String TWICE = """
            module example.twice

            data TodoId = Int
            data Title = String
            data Todo = { id: TodoId, title: Title, done: Bool }
            data NotFound = { id: TodoId }

            behavior findTodo : (id: TodoId) -> Todo | NotFound

            fake findTodo
                | (TodoId(1)) -> Todo { id = TodoId(1), title = Title("the table that answers"), done = false }
                | _           -> NotFound { id = TodoId(0) }

            fake findTodo
                | (TodoId(2)) -> Todo { id = TodoId(2), title = Title("the table that never does"), done = false }
                | _           -> NotFound { id = TodoId(0) }
            """;

    /**
     * A behavior two blocks name has no entries here, because it has no stand-in.
     *
     * <p>The model is refused where the blocks are written (E1933), neither of them standing in for
     * the behavior. Enumerating either would hand a caller entries of a table nothing dispatches
     * through, and running them would report disagreements about values no fake would ever answer
     * with — the mistake ADR-0093 was written to avoid, one level up from the row it was written
     * about.
     */
    @Test
    void aBehaviorNamedByTwoBlocksHasNoEntries() {
        CompileException refused = assertThrows(CompileException.class,
                () -> SoutherExamples.ofSource(TWICE));

        List<String> codes = new ArrayList<>();
        for (Diagnostic said : refused.diagnostics()) {
            codes.add(said.code());
        }
        assertEquals(List.of("E1933", "E1933"), codes, refused.getMessage());
    }

    private static BoundExamples bound() throws Exception {
        return SoutherExamples.ofSource(MODEL)
                .bind(builtElsewhere(MODEL, "example.faked", IMPL));
    }

    /**
     * The implementations built here, one per package, kept across the checks that ask for them.
     *
     * <p>Building one runs the system Java compiler, and seven calls here wanted the same class out
     * of it. An instance each is what the checks need rather than a class each: two of them take
     * two bindings to show that what one does is not something the other sees.
     */
    private static final java.util.Map<String, Class<?>> BUILT = new java.util.HashMap<>();

    private static Object builtElsewhere(String model, String pkg, String impl) throws Exception {
        Class<?> already = BUILT.get(pkg);
        if (already == null) {
            already = compileIt(model, pkg, impl);
            BUILT.put(pkg, already);
        }
        return already.getConstructor().newInstance();
    }

    private static Class<?> compileIt(String model, String pkg, String impl) throws Exception {
        Path classes = Files.createTempDirectory("souther-faked");
        for (var e : souther.compiler.Compiler.compileModules(List.of(model)).entrySet()) {
            Path at = classes.resolve(e.getKey().replace('.', '/') + ".class");
            Files.createDirectories(at.getParent());
            Files.write(at, e.getValue().bytes());
        }
        Path java = classes.resolve(pkg.replace('.', '/') + "/FindTodoImpl.java");
        Files.writeString(java, impl);
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8",
                "-classpath", classes + File.pathSeparator + System.getProperty("java.class.path"),
                "-d", classes.toString(), java.toString());
        assertEquals(0, rc, "the implementation compiles against the module's classes");
        URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()},
                SoutherExamples.class.getClassLoader());
        return loader.loadClass(pkg + ".FindTodoImpl");
    }
}
