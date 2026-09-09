package souther.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
