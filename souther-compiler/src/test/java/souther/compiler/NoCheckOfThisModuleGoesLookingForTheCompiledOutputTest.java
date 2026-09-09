package souther.compiler;

import org.junit.jupiter.api.Test;
import souther.test.CompiledClasses;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A check of this module asks for the compiled output; it does not go and find one.
 *
 * <p>Where the classes a rule reads are is one question and what they hold is another, and a check
 * that answers the first for itself has taken on a fact about the build. What it takes on is the
 * directory the build happened to be invoked from — so the same check answers differently under a
 * build invoked elsewhere — and, having found the files, it opens them: the same files the check
 * beside it opened, for the fork to read again.
 *
 * <p><b>What is refused is going to look, and not reading a class file.</b> A test handed bytes to
 * read is doing something else entirely: this module compiles Souther models and asks what came out
 * of them, and what came out is a value in the test rather than a file anybody went looking for. A
 * rule written over parsing would refuse those as well, and there are many of them.
 *
 * <p>So what is refused is the pair: a check that says where a build writes <em>and</em> reaches a
 * class file. Either alone is somebody else's business — a walk over the repository's own sources
 * says where a build writes in order to leave it out, and a test handed bytes reads a class file
 * without going anywhere — and it is holding both that makes a second way into the compiled output.
 */
class NoCheckOfThisModuleGoesLookingForTheCompiledOutputTest {

    /** What a build calls the directory it writes to, as a step of a path. */
    private static final String WHERE_A_BUILD_WRITES = "target";

    /** Reaching a class file: parsing one, and asking the shared reading for an output by path. */
    private static final Set<String> READS_A_CLASS_FILE = Set.of(
            "java/lang/classfile/ClassFile.of",
            "souther/test/CompiledClasses.at");

    @Test
    void nothingSaysWhereABuildWritesAndReachesAClassFileAsWell() {
        Set<String> both = new TreeSet<>();
        List<ClassModel> checks = CompiledClasses.ofModule(
                NoCheckOfThisModuleGoesLookingForTheCompiledOutputTest.class).all();
        for (ClassModel each : checks) {
            String said = whereABuildWritesAsSaidBy(each);
            if (said != null && reachesAClassFile(each)) {
                both.add(each.thisClass().asInternalName().replace('/', '.') + " says `" + said
                        + "`");
            }
        }

        assertEquals(Set.of(), both,
                "a check works out where this module's compiled output is instead of asking for it,"
                        + " so it answers about wherever the build was invoked from and reads files"
                        + " a check beside it has already read");
    }

    /** What the class says that names the directory a build writes to, where it says one. */
    private static String whereABuildWritesAsSaidBy(ClassModel model) {
        for (String said : constantsOf(model)) {
            for (String step : said.split("[/\\\\]")) {
                if (step.equals(WHERE_A_BUILD_WRITES)) {
                    return said;
                }
            }
        }
        return null;
    }

    private static boolean reachesAClassFile(ClassModel model) {
        for (MethodModel method : model.methods()) {
            CodeModel code = method.code().orElse(null);
            if (code == null) {
                continue;
            }
            for (var element : code) {
                if (element instanceof InvokeInstruction call && READS_A_CLASS_FILE.contains(
                        call.owner().asInternalName() + "." + call.name().stringValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> constantsOf(ClassModel model) {
        List<String> said = new ArrayList<>();
        for (MethodModel method : model.methods()) {
            CodeModel code = method.code().orElse(null);
            if (code == null) {
                continue;
            }
            for (var element : code) {
                if (element instanceof ConstantInstruction loaded
                        && loaded.constantValue() instanceof String text) {
                    said.add(text);
                }
            }
        }
        return said;
    }
}
