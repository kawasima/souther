package souther.architecture;

import souther.compiler.partition.FixtureReferences;
import souther.compiler.types.FixtureReferenceOrigin;
import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
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
 * Who may compose a reference for a row the generator offers, and who may number one.
 *
 * <p>A row naming a value the module states holds a name reaching a declaration, and such a name is
 * some reference of it. No source wrote that one, so what says which it is, is the run that composed
 * it — counted within that run and meaning nothing outside it
 * ({@link FixtureReferenceOrigin}).
 *
 * <p>Which is a rule about how many minters there are. One per run and handed to whatever composes:
 * a minter per composer starts each of them at nought and gives one number to several references,
 * and a static counter carries one run's numbering into the next. Both make two occurrences one, and
 * neither shows up as a failure — the rows still come out, naming values that are no longer told
 * apart.
 *
 * <p>Held here because the types cannot hold it. A record's constructor is public, as a record's is,
 * and a class with a counter has to be built by somebody — so what stops a second minter is not the
 * compiler but this walk, the way the makers of a construct's origin are held
 * ({@code WhoMaySettleASourceConstructOriginTest}). Written down as who names the maker, so that a
 * second one is a row here before it is a numbering nobody notices.
 */
class WhoMayComposeAFixtureReferenceTest {

    private static final String MINTER = internalNameOf(FixtureReferences.class);

    private static final String ORIGIN = internalNameOf(FixtureReferenceOrigin.class);

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /**
     * Every class that makes a minter or numbers a reference, with what it names.
     *
     * <p>The generator makes the one minter its run hands round, and the minter is the only thing
     * that numbers a reference. A row naming the origin's constructor from anywhere else is a
     * second numbering, and one naming the minter's is a second run inside a run.
     */
    private static final List<String> NAMING_A_MAKER = List.of(
            "souther/compiler/partition/FixtureReferences -> " + ORIGIN + "#<init>(I)V",
            "souther/compiler/partition/Generator -> " + MINTER + "#<init>()V");

    @Test
    void everyClassThatComposesOrNumbersOneIsWrittenDown() {
        assertEquals(NAMING_A_MAKER, new ArrayList<>(namingAMaker()),
                "a reference a row names is numbered within one run of the generator: a row here is"
                        + " a second minter or a second numbering, and either tells two references"
                        + " as one");
    }

    /**
     * The walk reads every module's classes.
     *
     * <p>Asked of the modules the repository has and not of what a build happened to leave: a module
     * whose classes are missing is one whose calls this cannot see, and the rows from the rest would
     * match while answering about fewer modules than it names.
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
                "the classes this reads are in more than the one module that declares a minter");
    }

    /** Every class naming one of the two makers, as the class and the maker it names. */
    private static Set<String> namingAMaker() {
        Set<String> makers = Set.of(ORIGIN + "#<init>(I)V", MINTER + "#<init>()V");
        Set<String> found = new TreeSet<>();
        for (Path module : REPOSITORY.modules()) {
            for (Path each : classesUnder(module)) {
                for (PoolEntry entry : constantPoolOf(each)) {
                    if (entry instanceof MemberRefEntry member) {
                        String named = member.owner().name().stringValue() + "#"
                                + member.name().stringValue() + member.type().stringValue();
                        if (makers.contains(named)) {
                            found.add(internalName(module, each) + " -> " + named);
                        }
                    }
                }
            }
        }
        return found;
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

    private static String internalName(Path module, Path compiled) {
        String name = classesOf(module).relativize(compiled).toString().replace('\\', '/');
        return name.substring(0, name.length() - ".class".length());
    }

    private static Path classesOf(Path module) {
        return module.resolve("target").resolve("classes");
    }

    private static boolean hasMainSources(Path module) {
        return Files.isDirectory(module.resolve("src").resolve("main").resolve("java"));
    }

    /** The compiled classes of one module the compiler is made of. Its own tests are not among them:
     *  a test composing a reference to look at it ships nothing, and a list that moved whenever one
     *  was written is a list nobody keeps up. */
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

    private static String internalNameOf(Class<?> type) {
        return type.getName().replace('.', '/');
    }
}
