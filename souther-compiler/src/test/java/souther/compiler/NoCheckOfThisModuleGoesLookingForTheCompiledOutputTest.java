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
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A check of this module asks for the compiled output; it does not go and find one.
 *
 * <p>Where the classes a rule reads are is one question and what they hold is another, and a check
 * that answers the first for itself has taken on a fact about the build. What it takes on is the
 * directory the build happened to be invoked from — so the same check answers differently under a
 * build invoked elsewhere — and, having found the files, it opens them: the same files the check
 * beside it opened, for the fork to read again.
 *
 * <p><b>The population is what reaches a class file, and the rule is that none of it says where a
 * build writes.</b> Reaching one is parsing it, or handing the shared reading a path to an output;
 * this module has many tests that do the first, because it compiles Souther models and asks what
 * came out of them, and what came out is a value in the test rather than a file anybody went looking
 * for. Those are what the rule is asked of, and going looking is what it refuses.
 *
 * <p>A check that reaches no class file is not in it. A walk over the repository's own sources says
 * where a build writes in order to leave the build's output out, and answers a question about
 * sources either way; this check says the word in order to look for it. Neither reaches a class
 * file, and a rule about naming alone would be a rule about the word rather than about going to
 * look.
 */
class NoCheckOfThisModuleGoesLookingForTheCompiledOutputTest {

    /** What a build calls the directory it writes to, as a step of a path. */
    private static final String WHERE_A_BUILD_WRITES = "target";

    /** Reaching a class file: parsing one, and asking the shared reading for an output by path. */
    private static final Set<String> READS_A_CLASS_FILE = Set.of(
            "java/lang/classfile/ClassFile.of",
            "souther/test/CompiledClasses.at");

    @Test
    void nothingThatReachesAClassFileSaysWhereABuildWrites() {
        List<ClassModel> reaching = new ArrayList<>();
        for (ClassModel each : CompiledClasses.ofModule(
                NoCheckOfThisModuleGoesLookingForTheCompiledOutputTest.class).all()) {
            if (reachesAClassFile(each)) {
                reaching.add(each);
            }
        }
        assertFalse(reaching.isEmpty(), "no check of this module reaches a class file at all, so"
                + " this rule is asked of nothing and passes by having nobody to ask");

        Set<String> looking = new TreeSet<>();
        for (ClassModel each : reaching) {
            String said = whereABuildWritesAsSaidBy(each);
            if (said != null) {
                looking.add(each.thisClass().asInternalName().replace('/', '.') + " says `" + said
                        + "`");
            }
        }

        assertEquals(Set.of(), looking,
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
