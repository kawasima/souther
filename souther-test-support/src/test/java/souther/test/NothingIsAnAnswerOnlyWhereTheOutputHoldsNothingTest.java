package souther.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a reading may answer with nothing, and when it has to say it cannot answer.
 *
 * <p>That a module built no such class is a fact about the compile the run is testing, and a check
 * following a name from one output to the next is entitled to it. That this process could not read
 * a file, that the directory asked about holds no classes at all, that a name reaches no output on
 * the file system — none of those are that fact, and answering them with the same nothing hands a
 * check the fact it asked for whichever it met. A rule would then report that nothing declares
 * something, having been unable to look.
 *
 * <p>So nothing is an answer where the output was read and holds none, and everywhere else this
 * refuses.
 */
class NothingIsAnAnswerOnlyWhereTheOutputHoldsNothingTest {

    private static CompiledClasses reading(Path root) {
        return CompiledClasses.at(root, new CompiledClassReadings(new ReadClassFiles()));
    }

    private static Path anOutputHolding(Path root, String named) throws IOException {
        Path built = root.resolve(named);
        Files.createDirectories(built.getParent());
        Files.copy(CompiledClasses.ofModule(RepositoryLayout.class).root()
                .resolve("souther/test/RepositoryLayout.class"), built);
        return root;
    }

    @Test
    void answers_with_nothing_where_no_such_class_was_built(@TempDir Path root) throws IOException {
        assertTrue(reading(anOutputHolding(root, "asked/Named.class")).find("asked.NeverWritten")
                .isEmpty());
    }

    @Test
    void refuses_where_the_read_stopped_for_any_other_reason(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("asked/Named.class"));

        assertThrows(UncheckedIOException.class, () -> reading(root).find("asked.Named"),
                "a read this could not make was answered with the fact that a module builds no such"
                        + " class, so a check that could not look reports what it did not see");
    }

    @Test
    void refuses_an_output_that_holds_no_classes(@TempDir Path root) {
        assertThrows(IllegalStateException.class, () -> reading(root).all(),
                "an output with nothing in it was handed over as a population, so a rule about"
                        + " every class of a module passes by having none to read");
    }

    @Test
    void refuses_a_package_that_holds_no_classes(@TempDir Path root) throws IOException {
        CompiledClasses output = reading(anOutputHolding(root, "asked/Named.class"));

        assertThrows(IllegalStateException.class, () -> output.inPackage("elsewhere"),
                "a package with nothing in it was handed over as a population, so a rule about what"
                        + " a package holds passes by looking at the wrong one");
    }

    @Test
    void refuses_a_class_that_reaches_no_output_on_the_file_system() {
        assertThrows(IllegalArgumentException.class, () -> CompiledClasses.ofModule(Test.class),
                "a class that arrived in a jar was taken to name an output root, which has nothing"
                        + " to walk and nothing to settle");
    }
}
