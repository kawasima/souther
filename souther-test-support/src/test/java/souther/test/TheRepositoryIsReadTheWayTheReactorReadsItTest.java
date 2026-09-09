package souther.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That what {@link RepositoryLayout} answers is this repository and not something shaped like it.
 *
 * <p>Everything else built on it inherits whatever this gets wrong, and the way it would go wrong
 * is quietly: a layout that found one module fewer would let every sweep over it report a pass
 * about the modules it did read.
 */
class TheRepositoryIsReadTheWayTheReactorReadsItTest {

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    @Test
    void theRootIsTheOneHoldingTheAggregator() {
        assertTrue(Files.isRegularFile(REPOSITORY.root().resolve("pom.xml")));
        assertTrue(Files.isDirectory(REPOSITORY.root().resolve(".github")),
                "the repository root, and not a module that happens to have a pom");
    }

    /**
     * And it is the same root from wherever a test is started.
     *
     * <p>Maven runs a test in its module's directory, an editor may run it from the repository
     * root, and both have to reach the same answer or a check means something different depending
     * on who ran it.
     */
    @Test
    void andItIsFoundFromAnywhereBelowIt() {
        Path root = REPOSITORY.root();
        for (Path start : List.of(root, root.resolve("souther-compiler"),
                root.resolve("souther-compiler").resolve("src").resolve("main").resolve("java"))) {
            assertEquals(root, RepositoryLayout.of(start).root(), "started from " + start);
        }
    }

    /**
     * Including from a path that means nothing on its own.
     *
     * <p>A relative path is a working directory away from being somewhere, and this class exists so
     * that nothing else has to know what the working directory is. Left as written, {@code "."} has
     * no parent to search upward through and {@code "souther-compiler"} has none either, so the
     * search would end at the first step — for a path naming a real directory. The tests here run
     * in a module, so both of these reach the same root as everything else.
     */
    @Test
    void includingFromOneThatMeansNothingWithoutTheWorkingDirectory() {
        Path root = REPOSITORY.root();
        assertEquals(root, RepositoryLayout.of(Path.of(".")).root());
        assertEquals(root, RepositoryLayout.of(Path.of("")).root());
        assertEquals(root, RepositoryLayout.of(Path.of("src")).root(), "a directory of this module");
        assertEquals(root, RepositoryLayout.of(Path.of("..")).root(), "and the root itself");
    }

    @Test
    void theModulesAreTheOnesTheRootPomNames() {
        List<String> named = REPOSITORY.modules().stream()
                .map(module -> module.getFileName().toString()).toList();
        assertEquals(List.of("souther-test-support", "souther-runtime", "souther-syntax",
                        "souther-compiler", "souther-build-driver", "souther-fmt", "souther-lsp",
                        "souther-cli", "souther-bench", "souther-program-api-test",
                        "souther-architecture-test"), named,
                "the reactor's modules, in the order the root pom names them");
    }

    /**
     * A module with no sources of its own is not a hole.
     *
     * <p>{@code souther-program-api-test} exists to compile against what another module publishes
     * and stand where that artifact's consumer stands, so it has tests and no main sources. That is
     * an answer and not a gap in one, which is why only a missing module refuses.
     */
    @Test
    void aModuleWithoutMainSourcesIsStillAModule() {
        List<String> withMainJava = REPOSITORY.mainJavaTrees().stream()
                .map(tree -> tree.getParent().getParent().getParent().getFileName().toString())
                .toList();
        assertTrue(REPOSITORY.modules().stream()
                        .anyMatch(module -> module.getFileName().toString()
                                .equals("souther-program-api-test")),
                "the module is there");
        assertFalse(withMainJava.contains("souther-program-api-test"),
                "and it contributes no main sources: " + withMainJava);
    }

    /**
     * A module is reached by the name the root pom gives it, and by no other.
     *
     * <p>What a check reaching another module's files says. A name the reactor does not have is
     * refused rather than resolved, so a module renamed out from under a check stops it instead of
     * handing it a directory that is not there.
     */
    @Test
    void aModuleIsFoundByTheNameTheRootPomGivesIt() {
        for (Path module : REPOSITORY.modules()) {
            assertEquals(module, REPOSITORY.moduleNamed(module.getFileName().toString()));
        }
        assertThrows(IllegalArgumentException.class,
                () -> REPOSITORY.moduleNamed("souther-there-is-no-such-module"));
    }

    /**
     * The default library is a population, and it is narrower than every source here.
     *
     * <p>Narrower is the whole of what makes it an answer: the models written to ask one question
     * are Souther sources too, and a check about the library that swept those would be holding the
     * library's properties over somebody's example.
     */
    @Test
    void theDefaultLibraryIsWhatTheCompilerShips() {
        List<Path> prelude = REPOSITORY.preludeSources();
        assertFalse(prelude.isEmpty(), "this repository ships a default library");
        assertEquals(prelude.stream().sorted().toList(), prelude, "sorted, so a sweep is ordered");
        Path resources = REPOSITORY.moduleNamed("souther-compiler")
                .resolve("src").resolve("main").resolve("resources");
        for (Path source : prelude) {
            assertTrue(source.isAbsolute(), source + " is where it is, not where somebody stands");
            assertTrue(source.startsWith(resources), source + " is a resource the compiler ships");
            assertTrue(source.getFileName().toString().endsWith(".sou"), source.toString());
        }
        List<Path> everySource = REPOSITORY.southerSources();
        assertTrue(everySource.containsAll(prelude), "the library is among the sources held here");
        assertTrue(everySource.size() > prelude.size(),
                "and the sources written to ask one question are not the library");
    }

    /**
     * And one of them is asked for by the name the library gives it.
     *
     * <p>Taken from the population rather than built from the same steps a second time, so what
     * comes back is one of the sources swept above and not a path that would be one if it were
     * there.
     */
    @Test
    void andOneOfThemIsAskedForByName() {
        for (Path source : REPOSITORY.preludeSources()) {
            String name = source.getFileName().toString();
            assertEquals(source, REPOSITORY.preludeSourceOf(name.substring(0, name.length() - 4)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> REPOSITORY.preludeSourceOf("there-is-no-such-module"));
    }

    /**
     * The search space is the source trees, and nothing a build writes is in it.
     *
     * <p>This is the property the sweeps depend on rather than a restatement of the filter: both
     * {@code target/} and surefire's {@code .surefire-*} record sit beside {@code src} rather than
     * under it, so a walk given these roots cannot reach either — which is also why it cannot race
     * with a build writing them.
     */
    @Test
    void nothingABuildWritesIsUnderASourceTree() {
        for (Path tree : REPOSITORY.sourceTrees()) {
            assertEquals("src", tree.getFileName().toString());
            assertTrue(REPOSITORY.modules().contains(tree.getParent()),
                    tree + " is the src of a module the root pom names");
        }
        List<Path> sources = REPOSITORY.southerSources();
        assertFalse(sources.isEmpty(), "this repository has Souther sources");
        for (Path source : sources) {
            assertFalse(RepositoryLayout.namesBuildOutput(source.toString()), source.toString());
            assertFalse(source.getFileName().toString().startsWith(".surefire-"), source.toString());
        }
        assertEquals(sources.stream().sorted().toList(), sources, "sorted, so a sweep is ordered");
    }
}
