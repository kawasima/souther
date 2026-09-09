package souther.architecture;

import org.junit.jupiter.api.Test;
import souther.test.RepositoryLayout;

import java.lang.classfile.ClassModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * <p>What is asked is whether a check works out where the compiled classes are. Saying so takes two
 * words — the directory a build writes to and the one it compiles into — and they are asked of
 * everything one place says rather than of one text, because a path is as often built a step at a
 * time as written whole. {@link WhatACheckSays} is what a place is and what one says. What hands the location out is nothing, so saying it is the whole of what
 * is left: a reading answers what an output holds and never where it is, and the repository answers
 * whether something is under a build's output rather than what a build calls its directories.
 *
 * <p><b>Asked of every check this repository has, and not of the ones that already ask properly.</b>
 * A population taken from what the checks call would be taken from the property being checked: a
 * module that reads no compiled output today, and tomorrow reads one by walking to it, would never
 * have called the thing that put it in the population, and the walk it added would be the one case
 * nothing looked at. So the population is everything, and the rule is the narrow one.
 *
 * <p>Narrow because a build writes more than classes. A check that runs what was shipped names the
 * jar and the launcher under the same directory and goes looking for nobody's classes, and a rule
 * refusing that would be about a word rather than about the thing.
 *
 * <p>What this does not say is that each module asks in one place. A check reading its own module's
 * fixtures asks for its own output, and telling that from naming somebody else's is a distinction
 * nothing here models; where a module has one place, that module's own checks say so.
 */
class NoCheckGoesLookingForACompiledOutputTest {

    private static final CompiledOutputs EVERYTHING = CompiledOutputs.ofEverythingCompiledHere();

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();


    @Test
    void nothingWorksOutWhereTheCompiledClassesAre() {
        List<ClassModel> checks = new ArrayList<>();
        for (Path module : REPOSITORY.modules()) {
            checks.addAll(EVERYTHING.testClassesOf(module));
        }
        assertFalse(checks.isEmpty(), "no check of this repository was read at all, so this rule is"
                + " asked of nothing and passes by having nobody to ask");

        Map<String, List<String>> working = new LinkedHashMap<>();
        for (ClassModel each : checks) {
            WhatACheckSays.of(each).forEach((where, said) -> {
                if (RepositoryLayout.namesCompiledOutput(said)) {
                    working.put(where, said);
                }
            });
        }

        assertEquals(Map.of(), working,
                "a check works out where the compiled classes are instead of asking for them, so it"
                        + " answers about wherever the build was invoked from and reads files a"
                        + " check beside it has already read");
    }


}
