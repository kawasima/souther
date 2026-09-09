package souther.test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassModel;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One compiled output, as the checks of this repository read it.
 *
 * <p>What a rule about compiled code is asked of. The classes under one output root are fixed for
 * the length of a run — they were written by the compile the run is testing — and this is where a
 * check reaches them, so that the reading is done once for the fork rather than once for the
 * question. What each check does with them differs, so what is answered here is the classes and
 * never a verdict about them.
 *
 * <p><b>Four ways of asking, and each pays for what it asks.</b> A rule about a whole module walks
 * {@link #all}; a rule about one class asks {@link #find}, which reaches that file and no other; a
 * rule about a package asks {@link #inPackage}, which settles which files are in it before any of
 * them is parsed; a rule that wants only what there is asks {@link #names}, which reads none of
 * them. A rule that could only have the whole module would make the narrow questions pay for the
 * wide one, and the narrowest of them reads eight classes of four thousand.
 *
 * <p><b>Which output is asked is the caller's to name, and so is what to do with two of them.</b> A
 * rule about what this repository publishes and a rule about what a test compiled beside it are
 * about two populations, and a reader that searched several outputs in some order would be deciding
 * for its callers which population they meant. One of these is one output root.
 *
 * <p>An output root is settled with {@link Path#toRealPath} before it becomes the name of anything,
 * so that a module found through the class path and the same module found by walking the repository
 * are one output and not two. What is found on the class path as a jar is not an output root: there
 * is nothing to walk and nothing to settle, and a check about a module built beside this one asks
 * about the module rather than about the artifact it was packaged into.
 */
public final class CompiledClasses {

    private final Path root;

    private final CompiledClassReadings readings;

    private CompiledClasses(Path root, CompiledClassReadings readings) {
        this.root = root;
        this.readings = readings;
    }

    /** The output at {@code root}, read by the fork this runs in. */
    public static CompiledClasses at(Path root) {
        return at(root, CompiledClassReadings.forThisFork());
    }

    /**
     * The output the module {@code marker} belongs to was compiled into.
     *
     * <p>Found through the class path rather than from the working directory. Where a run was
     * started from is not something a rule about a module is about, and a module reached by the
     * path a build happened to be invoked on answers differently under a build that was invoked
     * elsewhere.
     *
     * @param marker any class of that module
     */
    public static CompiledClasses ofModule(Class<?> marker) {
        String binary = marker.getName();
        String simple = binary.substring(binary.lastIndexOf('.') + 1);
        URL found = marker.getResource(simple + ".class");
        if (found == null || !"file".equals(found.getProtocol())) {
            throw new IllegalArgumentException(binary + " was not loaded from a compiled output on"
                    + " the file system, so the module holding it has no output root to read");
        }
        Path at;
        try {
            at = Path.of(found.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(binary + " is at " + found + ", which names no file",
                    e);
        }
        for (int up = 0; up <= binary.chars().filter(each -> each == '.').count(); up++) {
            at = at.getParent();
        }
        return at(at);
    }

    /** The same of a reading of somebody else's making, which is how what this costs is asked. */
    static CompiledClasses at(Path root, CompiledClassReadings readings) {
        try {
            return new CompiledClasses(root.toRealPath(), readings);
        } catch (IOException e) {
            throw new UncheckedIOException(root.toAbsolutePath() + " is not a compiled output that"
                    + " was built, so a rule read from it would hold nothing", e);
        }
    }

    /** Which output this is, settled. */
    public Path root() {
        return root;
    }

    /**
     * Every class the output holds.
     *
     * <p>A walk finding nothing is a walk of the wrong directory rather than a module that compiled
     * to nothing, so this refuses instead of handing back an empty answer for a rule to pass on.
     */
    public List<ClassModel> all() {
        List<ClassModel> found = read(readings.listing(root));
        if (found.isEmpty()) {
            throw new IllegalStateException(root + " holds no classes, so a rule read from it holds"
                    + " nothing");
        }
        return found;
    }

    /**
     * What the output holds, by binary name, without reading any of it.
     *
     * <p>Which classes there are is a question the listing answers, and a rule that only wants the
     * names — to load them, or to say which packages there are — would otherwise pay for parsing
     * every one of them to be told what the file names already said.
     */
    public List<String> names() {
        List<String> found = new ArrayList<>();
        for (Path each : readings.listing(root)) {
            found.add(root.relativize(each).toString()
                    .replace(File.separatorChar, '.')
                    .replaceAll("\\.class$", ""));
        }
        if (found.isEmpty()) {
            throw new IllegalStateException(root + " holds no classes, so a rule read from it holds"
                    + " nothing");
        }
        return found;
    }

    /**
     * The class {@code binaryName} names, or nothing where the output holds no such class.
     *
     * <p>Reaches that one file. What the rest of the output holds is not part of this question, and
     * a reader that answered it by walking would make a rule about a dozen classes pay for every
     * class the module compiled.
     *
     * @param binaryName as Java names a class, with {@code $} before a nested name
     */
    public Optional<ClassModel> find(String binaryName) {
        return readings.at(root.resolve(binaryName.replace('.', '/') + ".class"));
    }

    /**
     * Every class of {@code packageName} and of the packages under it.
     *
     * <p>Under it as well, because a package's classes are not all written directly in it, and a
     * rule about what a package holds that stopped at its own directory would answer about part of
     * its subject while reading as though it had covered the whole.
     *
     * <p>Which files are in it is settled from the listing, so what is parsed is what the question
     * is about.
     */
    public List<ClassModel> inPackage(String packageName) {
        Path directory = root.resolve(packageName.replace('.', '/'));
        List<Path> under = new ArrayList<>();
        for (Path each : readings.listing(root)) {
            if (each.startsWith(directory)) {
                under.add(each);
            }
        }
        if (under.isEmpty()) {
            throw new IllegalStateException(root + " holds no classes of " + packageName + ", so a"
                    + " rule read from that package holds nothing");
        }
        return read(under);
    }

    private List<ClassModel> read(List<Path> files) {
        List<ClassModel> found = new ArrayList<>();
        for (Path each : files) {
            readings.at(each).ifPresent(found::add);
        }
        return found;
    }
}
