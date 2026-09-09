package souther.architecture;

import souther.test.CompiledClasses;
import souther.test.RepositoryLayout;

import java.lang.classfile.ClassModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The compiled outputs a rule here is about, and the order a name is looked for in them.
 *
 * <p>Which outputs those are is what this is: a rule here asks about a class of this repository
 * without knowing which module built it, so the question is put to several outputs in turn and the
 * first that holds the name answers it. What one output holds is read elsewhere; the population and
 * its order are here.
 *
 * <p>Not through {@code java.class.path}. What a module is handed there is the reactor's to decide
 * and it is not the same in every build — a module built beside this one arrives as its compiled
 * classes, and one already packaged arrives as a jar — so a walk over the entries is a walk over
 * something that answers differently depending on which goal was run. What a rule here is about is
 * the compiled surface of a class of this repository, and the repository is where that is.
 *
 * <p>Which output is searched is the caller's to name, because the two questions asked here are
 * about two populations. A rule about what a class of this repository offers its callers is about
 * what the repository publishes, and a class compiled beside a test is not that. A test that
 * declares its own subjects to be read is asking about those, and they are compiled where test
 * output goes. One lookup answering both would be answering each with the other's population.
 *
 * <p>A name this cannot find is not a class that reaches nothing. It is a question this cannot
 * answer, and it says so rather than answering.
 *
 * <p><b>Held for the run, because the repository does not move while it happens.</b> Where a
 * module's output is is worked out by asking the file system what a path really is, and a rule
 * following a name from one output to the next asks for the outputs once per name it follows.
 */
final class CompiledOutputs {

    /** What this repository publishes: the compiled surface a caller elsewhere reaches. */
    private static final CompiledOutputs PUBLISHED = new CompiledOutputs(List.of("main"));

    /** That and what was compiled beside it, which is where a test's own subjects are. */
    private static final CompiledOutputs EVERYTHING = new CompiledOutputs(List.of("main", "test"));

    private final RepositoryLayout repository = RepositoryLayout.ofWorkingDirectory();

    private final List<String> phases;

    private List<CompiledClasses> outputs;

    private CompiledOutputs(List<String> phases) {
        this.phases = phases;
    }

    static CompiledOutputs ofWhatThisRepositoryPublishes() {
        return PUBLISHED;
    }

    static CompiledOutputs ofEverythingCompiledHere() {
        return EVERYTHING;
    }

    /** The class {@code internalName} names, or nothing where this repository built no such file. */
    Optional<ClassModel> find(String internalName) {
        // Outputs in the order they were named, because that order is a rule between them: a class
        // this repository publishes answers about that name wherever a module beside it also
        // compiled one.
        for (CompiledClasses output : outputs()) {
            Optional<ClassModel> found = output.find(internalName.replace('/', '.'));
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** The class {@code internalName} names, where failing to find one fails the reading. */
    ClassModel read(String internalName) {
        return find(internalName).orElseThrow(() -> new AssertionError(
                "the class " + internalName.replace('/', '.') + " was not built here, so what a"
                        + " signature naming it reaches is a question this cannot answer"));
    }

    /**
     * Every class of every output in the population.
     *
     * <p>A module that has sources of a phase and compiled none of them is a hole rather than a
     * pass: a rule about every class of this repository that passed over a module would answer
     * about the rest while reading as though it had covered all of them. A module that has no
     * sources of that phase compiled nothing, and finding nothing there is the whole answer.
     */
    List<ClassModel> all() {
        List<ClassModel> found = new ArrayList<>();
        for (CompiledClasses output : outputs()) {
            found.addAll(output.all());
        }
        return found;
    }

    /** The modules of this repository, for a rule that is about one of them at a time. */
    List<Path> modules() {
        return repository.modules();
    }

    /** What {@code module} compiled its main sources to, where it has any to compile. */
    Optional<CompiledClasses> mainOutputOf(Path module) {
        return outputOf(module, "main");
    }

    /**
     * Every class {@code module} compiled from its main sources, and none where it has none.
     *
     * <p>For a rule that reads the repository a module at a time. A module holding only tests or
     * only a pom compiled nothing of its own, and nothing is the whole answer; a module with
     * sources and nothing built is refused, because a rule that read fewer modules than it names
     * answers about the rest.
     */
    List<ClassModel> classesOf(Path module) {
        return mainOutputOf(module).map(CompiledClasses::all).orElse(List.of());
    }

    /**
     * Every class {@code module} compiled from its tests, and none where it has none.
     *
     * <p>A module with no tests of its own compiled none, and that is an answer rather than a hole:
     * a module exists to publish something and having nothing of its own to check is a thing a
     * module may be.
     */
    List<ClassModel> testClassesOf(Path module) {
        return outputOf(module, "test").map(CompiledClasses::all).orElse(List.of());
    }

    /**
     * The classes written directly in {@code packageName}, and none of the packages under it.
     *
     * <p>Narrower than asking a reading for a package, which answers with the packages under it as
     * well, and narrower on purpose: a rule here is about what one package holds, and a package
     * written under it later is a package of its own that nobody has said anything about. Which of
     * the two a rule wants is the rule's to say, so both are here rather than one standing in for
     * the other.
     *
     * @param packageName as a class file spells it, with {@code /} between the steps
     */
    List<ClassModel> inTheClassesOf(String packageName) {
        String under = packageName.endsWith("/") ? packageName : packageName + "/";
        List<ClassModel> found = new ArrayList<>();
        for (ClassModel each : all()) {
            String named = each.thisClass().asInternalName();
            if (named.startsWith(under) && !named.substring(under.length()).contains("/")) {
                found.add(each);
            }
        }
        return found;
    }

    private List<CompiledClasses> outputs() {
        if (outputs == null) {
            List<CompiledClasses> found = new ArrayList<>();
            for (String phase : phases) {
                for (Path module : repository.modules()) {
                    outputOf(module, phase).ifPresent(found::add);
                }
            }
            outputs = List.copyOf(found);
        }
        return outputs;
    }

    /**
     * What {@code module} compiled its {@code phase} sources to, where it has any of that phase.
     *
     * <p>Having sources and no output is the hole: it is refused here rather than passed over, so a
     * rule reading every class reads every class there is or says which module it could not.
     */
    private Optional<CompiledClasses> outputOf(Path module, String phase) {
        if (repository.javaTreeOf(module, phase) == null) {
            return Optional.empty();
        }
        Optional<CompiledClasses> built = repository.compiledOutputOf(module, phase);
        if (built.isEmpty()) {
            throw new AssertionError(module.getFileName() + " has " + phase + " sources and no"
                    + " classes built from them: a rule that passed over it would answer about the"
                    + " rest of the repository while reading as though it had covered this too");
        }
        return built;
    }
}
