package souther.bench;

import souther.test.CompiledClasses;
import souther.test.RepositoryLayout;

import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the reactor builds, asked once.
 *
 * <p>The checks in this module are about the repository rather than about one build, so each of
 * them needs the same two answers: which modules there are, and where their classes ended up. Each
 * had worked them out for itself, and four copies of "which modules there are" is four places for
 * one of them to stop covering the module added next — which is the shape those checks exist to
 * refuse.
 *
 * <p>The modules come from the root pom, which is what the reactor reads. Named in a list here
 * instead, this would be a copy of the reactor rather than a reading of it.
 *
 * <p>A module with nothing built is a hole and not a pass. A check that walks fewer modules than it
 * claims answers about the ones it read and says nothing about the rest, so this refuses rather
 * than skipping — which is also what makes naming every module a dependency of this one the thing
 * that keeps such a check honest under {@code -am}.
 */
final class Reactor {

    private Reactor() {}

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /** The modules the root pom names, as directories. */
    static List<Path> modules() {
        return REPOSITORY.modules();
    }

    /** What to call one of them in a message. */
    static String name(Path module) {
        return module.getFileName().toString();
    }

    /**
     * Whether {@code module} has main sources at all.
     *
     * <p>Told apart from a module that has them and was not built, which is the hole below. A
     * module holding only tests — one that exists to compile against what another artifact
     * publishes, and stand where that artifact's consumer stands — has nothing here to walk, and
     * finding nothing is the whole answer rather than a gap in one.
     */
    static boolean hasMainSources(Path module) {
        return Files.isDirectory(module.resolve("src/main/java"));
    }

    /** Every {@code .java} of every one of them. */
    static List<Path> mainJavaSources() {
        return REPOSITORY.mainJavaSources();
    }

    /** Every compiled class of every one of them, read once for the fork rather than per check. */
    static List<ClassModel> classes() {
        List<ClassModel> found = new ArrayList<>();
        for (Path module : modules()) {
            if (!hasMainSources(module)) {
                continue;
            }
            found.addAll(mainOutputOf(module).all());
        }
        return found;
    }

    /**
     * What {@code module} compiled its main sources to.
     *
     * <p>A module that has sources and no output is a hole and not a pass, so this refuses rather
     * than passing over it — which is what a check covering every module needs, and is also what
     * makes naming every module a dependency of this one the thing that keeps such a check honest
     * under {@code -am}.
     */
    static CompiledClasses mainOutputOf(Path module) {
        return REPOSITORY.compiledOutputOf(module, "main").orElseGet(() -> {
            throw new AssertionError(name(module) + " has no built classes: this check covers what"
                    + " has been built, so a module that has not been is a hole rather than a pass");
        });
    }

    /** The same of what its tests compiled to, or nothing where it has none. */
    static Optional<CompiledClasses> testOutputOf(Path module) {
        return REPOSITORY.compiledOutputOf(module, "test");
    }

    /** The repository, from the module this runs in. */
    static Path root() {
        return REPOSITORY.root();
    }
}
