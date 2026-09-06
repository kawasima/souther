package souther.architecture;

import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may say what a rule handle reads as, and which fields of the document one is written into.
 *
 * <p>The document says how a reader is sent to a rule in five fields, and the schema says of each
 * that it carries such a handle. What holds those two lists together is a comparison between the
 * schema and {@code RuleHandleSurface} — and that comparison is worth what the surface is worth: if
 * a handle can reach a document field without going through one, the enum is a list of the places
 * somebody remembered, which is the thing this whole issue is about one layer up.
 *
 * <p>So it is read off the compiled classes rather than trusted. Two questions, and neither is
 * answerable by looking at the enum: who renders a handle at all, and which fields are written
 * through it. A renderer reached by a method reference renders as surely as one called here, which
 * is the other reason to read the classes rather than the source.
 *
 * <p>What the rows leave open is deliberate: a class may put whatever string it likes under whatever
 * key. What it cannot do is get a rule handle's words without appearing here.
 */
class WhoMaySayWhatARuleHandleReadsAsTest {

    private static final String PROSE = "souther/compiler/publish/RuleHandleProse";

    private static final String SURFACE = "souther/compiler/publish/RuleHandleSurface";

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /**
     * Every class that asks what a handle reads as outside a document, and why it may.
     *
     * <p>Two, and both write the report a person reads. {@code AdequacyReport} says a rule in the
     * lines under a behavior; {@code GeneratedRows} says the same rule beside a row it composed, and
     * says it that way so that a reader meeting the finding in both places meets one sentence.
     *
     * <p>A row added here is a place that turns a handle into words with no field of the schema
     * behind it. That is what a document field written past the surface would look like from here,
     * and it is why this list is short on purpose.
     */
    private static final List<String> SAYING_IT_IN_PROSE = List.of(
            "souther/compiler/report/AdequacyReport -> " + PROSE + "#said",
            "souther/compiler/report/GeneratedRows -> " + PROSE + "#said");

    /**
     * And every class that writes one into the document, which is one.
     *
     * <p>The document is written in one place, so a second row here is a second writer — and two
     * writers of one document are two vocabularies for a consumer to learn.
     */
    private static final List<String> WRITING_IT_INTO_THE_DOCUMENT = List.of(
            "souther/compiler/report/AdequacyReport -> " + SURFACE + "#put");

    @Test
    void everyClassThatTurnsARuleHandleIntoWordsIsWrittenDown() {
        assertEquals(SAYING_IT_IN_PROSE, new ArrayList<>(naming(PROSE, "said")),
                "a class here turns a handle into words with nothing in the schema behind it, which"
                        + " is what a document field written past the surface looks like");
    }

    @Test
    void andEveryClassThatWritesOneIntoTheDocumentIsWrittenDown() {
        assertEquals(WRITING_IT_INTO_THE_DOCUMENT, new ArrayList<>(naming(SURFACE, "put")),
                "the document is written in one place, and a handle reaches a consumer through the"
                        + " fields that place names");
    }

    /**
     * And every field the surface declares is one the writer writes.
     *
     * <p>The other direction. A constant nobody names is a field the schema is held to carry a
     * handle in while nothing puts one there — the comparison against the schema would pass, and
     * what it would be comparing is two lists of intentions.
     */
    @Test
    void andEveryFieldTheSurfaceDeclaresIsOneSomethingWrites() {
        assertEquals(surfaceConstants(), used(SURFACE),
                "the fields the surface declares and the fields something writes through");
    }

    /**
     * The walk reads every module's classes.
     *
     * <p>Asked of the modules the repository has and not of what a build happened to leave: a module
     * whose classes are missing is one whose calls this cannot see, and the rows from the rest would
     * match and this would pass while answering about fewer modules than it names.
     */
    @Test
    void andEveryModuleTheRepositoryHoldsWasRead() {
        List<String> unbuilt = new ArrayList<>();
        for (Path module : REPOSITORY.modules()) {
            if (!Files.isDirectory(classesOf(module)) && hasMainSources(module)) {
                unbuilt.add(module.getFileName().toString());
            }
        }

        assertEquals(List.of(), unbuilt,
                "a module whose classes are not built is one this walk passes over, and a walk that"
                        + " passes over a module answers about the rest while saying it answers"
                        + " about all of them");
        assertTrue(modulesRead() > 1,
                "the classes this reads are in more than the one module that declares the sentence");
    }

    /** Every class naming a method called {@code member} on {@code owner}, as the class and what it
     *  named. */
    private static Set<String> naming(String owner, String member) {
        Set<String> found = new TreeSet<>();
        for (Path module : REPOSITORY.modules()) {
            for (Path each : classesUnder(module)) {
                for (PoolEntry entry : constantPoolOf(each)) {
                    if (entry instanceof MemberRefEntry named
                            && owner.equals(named.owner().name().stringValue())
                            && member.equals(named.name().stringValue())) {
                        found.add(internalName(module, each) + " -> " + owner + "#" + member);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Every constant of {@code owner} that some other class reads.
     *
     * <p>The owner itself is passed over. An enum's own class file names every constant it declares,
     * because that is where they are made — counted, this would answer that every field the surface
     * declares is written and go on answering it with nothing writing any of them.
     */
    private static Set<String> used(String owner) {
        Set<String> found = new TreeSet<>();
        for (Path module : REPOSITORY.modules()) {
            for (Path each : classesUnder(module)) {
                if (internalName(module, each).equals(owner)) {
                    continue;
                }
                for (PoolEntry entry : constantPoolOf(each)) {
                    if (entry instanceof FieldRefEntry read
                            && owner.equals(read.owner().name().stringValue())
                            && surfaceConstants().contains(read.name().stringValue())) {
                        found.add(read.name().stringValue());
                    }
                }
            }
        }
        return found;
    }

    /**
     * The fields the surface declares, read off the enum rather than written here.
     *
     * <p>Loaded by name, because this module compiles against the compiler and a list of constants
     * copied into a test is one more list to keep — which is the shape being checked.
     */
    private static Set<String> surfaceConstants() {
        Set<String> out = new TreeSet<>();
        try {
            for (Object each : Class.forName(SURFACE.replace('/', '.')).getEnumConstants()) {
                out.add(((Enum<?>) each).name());
            }
        } catch (ClassNotFoundException e) {
            throw new AssertionError("the surface this is about is on the classpath", e);
        }
        return out;
    }

    private static int modulesRead() {
        int read = 0;
        for (Path module : REPOSITORY.modules()) {
            if (!classesUnder(module).isEmpty()) {
                read++;
            }
        }
        return read;
    }

    private static Iterable<PoolEntry> constantPoolOf(Path compiled) {
        try {
            return ClassFile.of().parse(Files.readAllBytes(compiled)).constantPool();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The class's own binary name, taken against the directory it was found under rather than off
     *  the first {@code classes} in the path, which a checkout under one would be. */
    private static String internalName(Path module, Path compiled) {
        String name = classesOf(module).relativize(compiled).toString().replace('\\', '/');
        return name.substring(0, name.length() - ".class".length());
    }

    private static Path classesOf(Path module) {
        return module.resolve("target").resolve("classes");
    }

    /** Whether the module has main sources to have been built from. A module holding only tests or
     *  only a pom leaves no classes and is not one this walk is missing. */
    private static boolean hasMainSources(Path module) {
        return Files.isDirectory(module.resolve("src").resolve("main").resolve("java"));
    }

    /** The compiled classes of one module that the compiler is made of. Its own tests are not among
     *  them: a test makes one to look at it and ships nothing. */
    private static List<Path> classesUnder(Path module) {
        Path where = classesOf(module);
        if (!Files.isDirectory(where)) {
            return List.of();
        }
        try (Stream<Path> found = Files.walk(where)) {
            return found.filter(p -> p.toString().endsWith(".class")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
