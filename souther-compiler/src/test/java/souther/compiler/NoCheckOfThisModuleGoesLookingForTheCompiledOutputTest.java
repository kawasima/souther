package souther.compiler;

import org.junit.jupiter.api.Test;

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
 * A check of this module asks {@link WhatWasCompiled} for its classes; it does not go and find them.
 *
 * <p>Where the classes a rule reads are is one question and what they hold is another, and a check
 * that answers the first for itself has taken on a fact about the build. What it takes on is the
 * directory the build happened to be invoked from — so the same check answers differently under a
 * build invoked elsewhere — and, having found the files, it opens them: the same files the check
 * beside it opened, for the fork to read again.
 *
 * <p>What is asked here is which class names an output. Writing down where a build puts what it
 * made is the other way in, and it is refused for every module that reads compiled classes rather
 * than for this one — see {@code NoCheckGoesLookingForACompiledOutputTest} in the architecture
 * tests. What hands the location out is nothing, so those two are the whole of it.
 *
 * <p>What is not refused is reading a class file. This module compiles Souther models and asks what
 * came out of them, and what came out is a value in the test rather than a file anybody went looking
 * for; a rule written over parsing would refuse those as well, and there are many of them.
 */
class NoCheckOfThisModuleGoesLookingForTheCompiledOutputTest {

    /** Naming an output: what a check outside {@code souther.test} can be handed one by. */
    private static final Set<String> NAMES_AN_OUTPUT = Set.of(
            "souther/test/CompiledClasses.ofModule",
            "souther/test/RepositoryLayout.compiledOutputOf");

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
