package souther.architecture;

import souther.test.RepositoryLayout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The compiled outputs a name is looked for in, and the order they are looked in.
 *
 * <p>Which outputs those are is what this is: a rule here asks about a class of this repository
 * without knowing which module built it, so the question is put to several outputs in turn and the
 * first that holds the name answers it. What one output holds is read elsewhere; the population and
 * its order are here.
 *
 * <p>Not through {@code java.class.path}. What a module is handed there is the reactor's to decide
 * and it is not the same in every build — a module built beside this one arrives as its
 * {@code target/classes}, and one already packaged arrives as a jar — so a walk over the entries is
 * a walk over something that answers differently depending on which goal was run. What a rule here
 * is about is the compiled surface of a class of this repository, and the repository is where that
 * is.
 *
 * <p>Which output is searched is the caller's to name, because the two questions asked here are
 * about two populations. A rule about what a class of this repository offers its callers is about
 * what the repository publishes, and a class compiled beside a test is not that. A test that
 * declares its own subjects to be read is asking about those, and they are compiled where test
 * output goes. One lookup answering both would be answering each with the other's population.
 *
 * <p>A name this cannot find is not a class that reaches nothing. It is a question this cannot
 * answer, and it says so rather than answering.
 */
final class CompiledOutputs {

    private final RepositoryLayout repository;

    private final List<String> outputs;

    private final Map<String, Optional<ClassModel>> read = new HashMap<>();

    private CompiledOutputs(RepositoryLayout repository, List<String> outputs) {
        this.repository = repository;
        this.outputs = outputs;
    }

    /** What this repository publishes: the compiled surface a caller elsewhere reaches. */
    static CompiledOutputs ofWhatThisRepositoryPublishes() {
        return new CompiledOutputs(RepositoryLayout.ofWorkingDirectory(), List.of("classes"));
    }

    /** That and what was compiled beside it, which is where a test's own subjects are. */
    static CompiledOutputs ofEverythingCompiledHere() {
        return new CompiledOutputs(RepositoryLayout.ofWorkingDirectory(),
                List.of("classes", "test-classes"));
    }

    /** The class {@code internalName} names, or nothing where this repository built no such file. */
    Optional<ClassModel> find(String internalName) {
        return read.computeIfAbsent(internalName, this::parse);
    }

    /** The class {@code internalName} names, where failing to find one fails the reading. */
    ClassModel read(String internalName) {
        return find(internalName).orElseThrow(() -> new AssertionError(
                "the class " + internalName.replace('/', '.') + " was not built here, so what a"
                        + " signature naming it reaches is a question this cannot answer"));
    }

    private Optional<ClassModel> parse(String internalName) {
        // Outputs outermost, because the order they are named in is an order between them: a class
        // this repository publishes answers about that name wherever a module beside it also
        // compiled one.
        for (String output : outputs) {
            for (Path module : repository.modules()) {
                Path compiled = module.resolve("target").resolve(output)
                        .resolve(internalName + ".class");
                if (Files.isRegularFile(compiled)) {
                    try {
                        return Optional.of(ClassFile.of().parse(Files.readAllBytes(compiled)));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }
        return Optional.empty();
    }
}
