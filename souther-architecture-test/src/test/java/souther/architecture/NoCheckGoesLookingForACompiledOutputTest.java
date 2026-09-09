package souther.architecture;

import org.junit.jupiter.api.Test;
import souther.test.RepositoryLayout;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A check asks for the compiled output it is about; it does not go and find one.
 *
 * <p>Where the classes a rule reads are is one question and what they hold is another, and a check
 * that answers the first for itself has taken on a fact about the build. What it takes on is the
 * directory the build happened to be invoked from — so the same check answers differently under a
 * build invoked elsewhere — and, having found the files, it opens them: the same files the check
 * beside it opened, for the fork to read again.
 *
 * <p>What is asked here is the word a build's output goes by, which nothing that reads compiled
 * classes has any business writing down. A rule that looked for the two halves of going to look —
 * the word and the reading — in one class would be a rule about where somebody wrote them: pulling
 * the path into a class of its own leaves each half innocent, and the walk is back with every check
 * still passing. What hands the location out is nothing, so writing it down is the whole of what is
 * left: a reading answers what an output holds and never where it is, and the repository answers
 * whether something is under a build's output rather than what a build calls its directory.
 *
 * <p><b>Asked of the modules whose checks read compiled classes, which is a population and not a
 * list.</b> A module whose checks run the shipped binary names what a build wrote and reads no
 * class file — the jar, the launcher — and a rule refusing that would be about the word rather than
 * about going to look. Which modules those are is worked out from what their checks call.
 *
 * <p>What this does not say is that each module asks in one place. A check reading its own module's
 * fixtures asks for its own output, and telling that from naming somebody else's is a distinction
 * nothing here models; where a module has one place, that module's own checks say so.
 */
class NoCheckGoesLookingForACompiledOutputTest {

    /** Naming an output: what a check outside {@code souther.test} can be handed one by. */
    private static final Set<String> NAMES_AN_OUTPUT = Set.of(
            "souther/test/CompiledClasses.ofModule",
            "souther/test/RepositoryLayout.compiledOutputOf");

    private static final CompiledOutputs EVERYTHING = CompiledOutputs.ofEverythingCompiledHere();

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();


    @Test
    void nothingWritesDownWhereABuildPutsWhatItMade() {
        Map<String, List<String>> writing = new LinkedHashMap<>();
        List<ClassModel> checks = new ArrayList<>();
        for (Path module : REPOSITORY.modules()) {
            List<ClassModel> of = EVERYTHING.testClassesOf(module);
            if (of.stream().anyMatch(NoCheckGoesLookingForACompiledOutputTest::namesAnOutput)) {
                checks.addAll(of);
            }
        }
        assertFalse(checks.isEmpty(), "no check of this repository was read at all, so this rule is"
                + " asked of nothing and passes by having nobody to ask");

        for (ClassModel each : checks) {
            for (String constant : constantsOf(each)) {
                if (RepositoryLayout.namesBuildOutput(constant)) {
                    writing.computeIfAbsent(named(each), _ -> new ArrayList<>()).add(constant);
                }
            }
        }

        assertEquals(Map.of(), writing,
                "a check writes down where a build puts what it made, which is the half of finding"
                        + " the files for itself that nothing else needs: what is under a build's"
                        + " output is the repository's to say");
    }

    private static boolean namesAnOutput(ClassModel model) {
        for (MethodModel method : model.methods()) {
            CodeModel code = method.code().orElse(null);
            if (code == null) {
                continue;
            }
            for (var element : code) {
                if (element instanceof InvokeInstruction call && NAMES_AN_OUTPUT.contains(
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

    private static String named(ClassModel of) {
        return of.thisClass().asInternalName().replace('/', '.');
    }
}
