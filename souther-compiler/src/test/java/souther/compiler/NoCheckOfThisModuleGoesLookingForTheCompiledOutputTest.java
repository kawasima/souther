package souther.compiler;

import org.junit.jupiter.api.Test;
import souther.test.RepositoryLayout;

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
 * A check of this module asks {@link WhatWasCompiled} for its classes; it does not go and find them.
 *
 * <p>Where the classes a rule reads are is one question and what they hold is another, and a check
 * that answers the first for itself has taken on a fact about the build. What it takes on is the
 * directory the build happened to be invoked from — so the same check answers differently under a
 * build invoked elsewhere — and, having found the files, it opens them: the same files the check
 * beside it opened, for the fork to read again.
 *
 * <p><b>Two ways in, and both are closed here rather than one being guessed at.</b> A check can
 * reach an output through the shared reading by naming one of its own, or it can find the files
 * itself and parse them. A rule that looked for the two halves of the second in one class would be
 * a rule about where somebody wrote them: pulling the path into a class of its own leaves each half
 * innocent, and the walk is back with every check still passing. So neither is asked as a
 * co-occurrence. Naming an output is asked of the call that names one, and finding the files is
 * asked of the word a build's output goes by, which nothing here has any business writing down.
 *
 * <p>What is not refused is reading a class file. This module compiles Souther models and asks what
 * came out of them, and what came out is a value in the test rather than a file anybody went looking
 * for; a rule written over parsing would refuse those as well, and there are many of them.
 */
class NoCheckOfThisModuleGoesLookingForTheCompiledOutputTest {

    /** Naming an output: the two ways the shared reading is handed one. */
    private static final Set<String> NAMES_AN_OUTPUT = Set.of(
            "souther/test/CompiledClasses.ofModule",
            "souther/test/CompiledClasses.at");

    /** The one place this module's outputs are named, which is what the rule is that there is one. */
    private static final String THE_ONE_PLACE = WhatWasCompiled.class.getName();

    @Test
    void oneCheckNamesThisModulesOutputsAndTheRestAskIt() {
        Set<String> naming = new TreeSet<>();
        for (ClassModel each : checks()) {
            for (MethodModel method : each.methods()) {
                CodeModel code = method.code().orElse(null);
                if (code == null) {
                    continue;
                }
                for (var element : code) {
                    if (element instanceof InvokeInstruction call && NAMES_AN_OUTPUT.contains(
                            call.owner().asInternalName() + "." + call.name().stringValue())) {
                        naming.add(named(each));
                    }
                }
            }
        }

        assertEquals(Set.of(THE_ONE_PLACE), naming,
                "a check of this module works out which output to read instead of asking, so where"
                        + " this module's classes are is said in more than one place and a module"
                        + " that moves is as many edits as there are checks");
    }

    @Test
    void andNothingWritesDownWhereABuildPutsWhatItMade() {
        String said = RepositoryLayout.whereABuildWrites();
        Set<String> writing = new TreeSet<>();
        List<ClassModel> checks = checks();
        assertFalse(checks.isEmpty(), "no check of this module was read at all, so this rule is"
                + " asked of nothing and passes by having nobody to ask");

        for (ClassModel each : checks) {
            for (String constant : constantsOf(each)) {
                for (String step : constant.split("[/\\\\]")) {
                    if (step.equals(said)) {
                        writing.add(named(each) + " says `" + constant + "`");
                    }
                }
            }
        }

        assertEquals(Set.of(), writing,
                "a check of this module writes down where a build puts what it made, which is the"
                        + " half of finding the files for itself that nothing else needs: what is"
                        + " under a build's output is the repository's to say");
    }

    private static List<ClassModel> checks() {
        return WhatWasCompiled.checksCompiledBesideIt().all();
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
