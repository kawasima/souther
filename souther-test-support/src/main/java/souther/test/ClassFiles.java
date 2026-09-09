package souther.test;

import java.lang.classfile.ClassModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Where a compiled class file is touched, and the only place it is.
 *
 * <p>Both ways of touching one are here, because a reading that asked this for the bytes and looked
 * for the file itself would leave half of what it does outside the boundary — and a rule saying
 * where compiled output is reached could then be met while the file system was still being asked
 * somewhere else.
 *
 * <p>An interface because what a reading costs is a question about it: a caller counting what was
 * touched hands one that counts and asks the reading, rather than the reading keeping a tally of
 * its own. A tally kept inside would be a number nothing spends, and it would be there in every run
 * to serve a question only a check asks.
 */
interface ClassFiles {

    /** Every class file under {@code root}, in a settled order, so that two readings of one root
     *  answer in the same order. */
    List<Path> list(Path root);

    /** {@code file} parsed, or nothing where no such file was built. Absence is an answer this
     *  gives rather than a question the caller asks the file system first. */
    Optional<ClassModel> read(Path file);
}
