package souther.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a fork pays to read a compiled output.
 *
 * <p>The classes a check reads were written by the compile the run is testing and do not change
 * while it runs, so what each question costs is decided by how much of the output it needs and not
 * by how many questions there are. That is a fact about the reading rather than about the clock, so
 * it is asked of a reading that says what it touched: a timing would answer about the machine, and
 * would go on passing while every question read the module again.
 *
 * <p>The counted reading stands in for the one a fork shares. That the fork's own reading is made
 * in one place is a different question, and {@link OnePlaceMakesTheReadingAForkSharesTest} asks it.
 */
class WhatIsReadOfACompiledOutputIsReadOnceTest {

    /** A reading that says what it touched, wrapped round the one that goes to the file system. */
    private static final class Counting implements ClassFiles {

        private final ClassFiles real = new ReadClassFiles();

        private final AtomicInteger listings = new AtomicInteger();

        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public List<Path> list(Path root) {
            listings.incrementAndGet();
            return real.list(root);
        }

        @Override
        public Optional<ClassModel> read(Path file) {
            reads.incrementAndGet();
            return real.read(file);
        }
    }

    private final Counting counting = new Counting();

    private final CompiledClassReadings readings = new CompiledClassReadings(counting);

    /** Where this module's main classes are, found the way a check finds them. */
    private static final Path PUBLISHED = CompiledClasses.ofModule(RepositoryLayout.class).root();

    /** Where the classes compiled beside them are, which is a second output and not this one. */
    private static final Path COMPILED_BESIDE =
            CompiledClasses.ofModule(WhatIsReadOfACompiledOutputIsReadOnceTest.class).root();

    private CompiledClasses at(Path root) {
        return CompiledClasses.at(root, readings);
    }

    @Test
    void asks_the_same_question_twice_and_reads_the_output_once() {
        List<ClassModel> first = at(PUBLISHED).all();
        int afterOne = counting.reads.get();
        List<ClassModel> again = at(PUBLISHED).all();

        assertEquals(first.size(), again.size());
        assertEquals(afterOne, counting.reads.get(), "the second walk read a file again");
        assertEquals(1, counting.listings.get(), "the second walk listed the output again");
    }

    @Test
    void asks_two_questions_of_one_output_and_reads_it_once() {
        at(PUBLISHED).all();
        int afterAll = counting.reads.get();

        at(PUBLISHED).inPackage("souther.test");
        at(PUBLISHED).find(RepositoryLayout.class.getName());

        assertEquals(afterAll, counting.reads.get(),
                "a second question opened a file the first had already read");
    }

    @Test
    void is_the_same_output_whichever_class_of_the_module_names_it() {
        Path byOne = CompiledClasses.ofModule(RepositoryLayout.class).root();
        Path byAnother = CompiledClasses.ofModule(GitIndex.class).root();

        assertEquals(byOne, byAnother);

        at(byOne).all();
        at(byAnother).all();

        assertEquals(1, counting.listings.get(),
                "one output was listed twice because two classes of it were named");
    }

    @Test
    void is_a_different_output_when_the_classes_were_compiled_elsewhere() {
        assertNotEquals(PUBLISHED, COMPILED_BESIDE);

        at(PUBLISHED).all();
        at(COMPILED_BESIDE).all();

        assertEquals(2, counting.listings.get(),
                "two outputs were read as one, so a rule about either was answered with both");
    }

    @Test
    void says_what_the_output_holds_without_reading_any_of_it() {
        List<String> named = at(PUBLISHED).names();

        assertTrue(named.contains(RepositoryLayout.class.getName()));
        assertEquals(0, counting.reads.get(),
                "being told which classes there are opened them, so a rule that wants no more than"
                        + " the names pays for parsing every one of them");
        assertEquals(1, counting.listings.get());
    }

    @Test
    void reaches_one_class_without_listing_the_output_it_is_in() {
        Optional<ClassModel> found = at(PUBLISHED).find(RepositoryLayout.class.getName());

        assertTrue(found.isPresent());
        assertEquals(1, counting.reads.get());
        assertEquals(0, counting.listings.get(),
                "asking for one class walked the output, so a check reading a dozen classes pays"
                        + " for every class the module compiled");
    }

    @Test
    void keeps_that_a_class_was_never_built_the_way_it_keeps_one_that_was() {
        String never = RepositoryLayout.class.getName() + "ThatWasNeverWritten";

        assertTrue(at(PUBLISHED).find(never).isEmpty());
        assertEquals(1, counting.reads.get());

        assertTrue(at(PUBLISHED).find(never).isEmpty());
        assertEquals(1, counting.reads.get(), "the same absence was looked for twice");
    }

    /**
     * An output holding one package inside another and a third beside it.
     *
     * <p>Written here because the module this test is in compiles to a single package, and a
     * package that is the whole of an output cannot tell a reading that narrows from one that reads
     * everything and hands back the part that was asked for. What the files hold does not matter to
     * a question about which of them were opened, so they are copies of one that was built.
     */
    private static Path anOutputOfSeveralPackages(Path root) throws IOException {
        Path one = CompiledClasses.ofModule(RepositoryLayout.class).root()
                .resolve("souther/test/RepositoryLayout.class");
        for (String each : List.of("asked/Named.class", "asked/deeper/Nested.class",
                "beside/Other.class")) {
            Path at = root.resolve(each);
            Files.createDirectories(at.getParent());
            Files.copy(one, at);
        }
        return root;
    }

    @Test
    void is_one_output_however_the_path_that_reaches_it_is_spelled(@TempDir Path root)
            throws IOException {
        Path built = anOutputOfSeveralPackages(root.resolve("built"));
        Path reached = Files.createSymbolicLink(root.resolve("reached"), built);

        assertEquals(at(built).root(), at(reached).root());

        at(built).all();
        at(reached).all();

        assertEquals(1, counting.listings.get(),
                "one output was read as two because two paths reach it, so what a fork pays"
                        + " depends on how a caller spelled its way there");
    }

    @Test
    void reads_a_package_without_reading_the_output_round_it(@TempDir Path root) throws IOException {
        List<ClassModel> narrower = at(anOutputOfSeveralPackages(root)).inPackage("asked");

        assertEquals(2, narrower.size());
        assertEquals(2, counting.reads.get(), "reading a package opened files outside it");
    }

    @Test
    void reads_a_package_together_with_the_packages_under_it(@TempDir Path root) throws IOException {
        CompiledClasses output = at(anOutputOfSeveralPackages(root));

        assertEquals(3, output.all().size());
        assertEquals(2, output.inPackage("asked").size(),
                "a package was read without the packages under it, so a rule about what it holds"
                        + " was answered about part of it");
    }
}
