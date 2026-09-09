package souther.test;

import java.lang.classfile.ClassModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * What has been read of the compiled classes, kept for as long as the fork that read it.
 *
 * <p>What a check of compiled code is about was written before any test started: the compile the
 * run is testing produced it, and nothing that happens while the tests run changes it. So a reading
 * of it is worth keeping, and keeping it is not a cache over something that moves — there is
 * nothing here that could go out of date, and nothing here asks whether it has.
 *
 * <p><b>What is kept is the reading and not an answer.</b> One check counts invocations of a
 * method, another asks which methods take a value of some type, another reads field declarations.
 * Kept per question, the same files would be parsed again for each of them; kept as answers, every
 * check would have to be written against a vocabulary decided here. The parsed class is what they
 * have in common.
 *
 * <p>A file is kept under the path it was read from, and a listing under the root it was taken of.
 * A root is settled by its caller before it arrives, so two callers that found one module by
 * different routes ask about one key rather than two, and the files beneath a settled root are
 * settled by being built under it.
 *
 * <p>Absence is kept beside presence and in the same key space. Whether a module built a class is
 * as fixed as what the class holds, and a reader that walks a name from one output to the next asks
 * the same question of the same path however many times its walk arrives there.
 */
final class CompiledClassReadings {

    /**
     * The readings of the fork this runs in.
     *
     * <p>The lifetime is the fork rather than the run: forks are capped rather than taken as a
     * share of the machine, so what a suite pays is one reading per fork, and a check gets the
     * reading of whichever fork it landed in.
     */
    private static final CompiledClassReadings FOR_THIS_FORK =
            new CompiledClassReadings(new ReadClassFiles());

    private final ClassFiles files;

    private final ConcurrentMap<Path, List<Path>> listings = new ConcurrentHashMap<>();

    private final ConcurrentMap<Path, Optional<ClassModel>> parsed = new ConcurrentHashMap<>();

    CompiledClassReadings(ClassFiles files) {
        this.files = files;
    }

    /** The readings every check of this fork shares. */
    static CompiledClassReadings forThisFork() {
        return FOR_THIS_FORK;
    }

    /** Every class file under {@code root}, taken once however many checks ask. */
    List<Path> listing(Path root) {
        return listings.computeIfAbsent(root, files::list);
    }

    /** {@code file} parsed, or nothing where no such file was built, read once however many checks
     *  ask and whether they arrive by a listing or by a name. */
    Optional<ClassModel> at(Path file) {
        return parsed.computeIfAbsent(file, files::read);
    }
}
