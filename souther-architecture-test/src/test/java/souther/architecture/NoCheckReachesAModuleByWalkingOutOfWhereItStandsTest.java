package souther.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import souther.test.RepositoryLayout;

import java.lang.classfile.ClassModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A check names the module whose files it wants; it does not work out which way that module is.
 *
 * <p>Which module a check reads is the check's own subject — this module's fixtures, that module's
 * corpus — and it stays written where the check is. Where that module is, is not a subject: a check
 * stepping out of its own directory to land in another has answered it from wherever the build was
 * invoked, so the same check reads different files under a build started elsewhere. Started
 * somewhere the step does not land, the three checks that spelled the default library this way
 * answered three different ways, and one of them swept no sources and reported a pass over all of
 * them.
 *
 * <p>What is asked is whether a check works out which way another module is. Saying so takes two
 * words — a step out of where it stands and the name of the module it means to reach — and they are
 * asked of everything one place says rather than of one text, because a path is as often built a
 * step at a time as written whole. A parameterized case says where its subjects are in an
 * annotation, so that is one of the places: {@link WhatACheckSays} is what they are.
 *
 * <p>The module's name is not what this refuses, and a rule that refused it would be a rule against
 * naming a subject. What it refuses is the {@code ..} beside the name:
 * {@link RepositoryLayout#moduleNamed} answers where a module is, and a check that asks it says
 * which module and nothing about where anything is.
 *
 * <p><b>Asked of every check this repository has, and not of the ones that reach another module
 * today.</b> A population taken from the checks that read across modules would be taken from the
 * property being checked: a check reading only its own module now, and reaching for another
 * tomorrow by stepping out of its directory, would be the one case nothing looked at.
 */
class NoCheckReachesAModuleByWalkingOutOfWhereItStandsTest {

    private static final CompiledOutputs EVERYTHING = CompiledOutputs.ofEverythingCompiledHere();

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    @Test
    void nothingWorksOutWhichWayAnotherModuleIs() {
        List<ClassModel> checks = new ArrayList<>();
        for (Path module : REPOSITORY.modules()) {
            checks.addAll(EVERYTHING.testClassesOf(module));
        }
        assertFalse(checks.isEmpty(), "no check of this repository was read at all, so this rule is"
                + " asked of nothing and passes by having nobody to ask");

        Map<String, List<String>> walking = new LinkedHashMap<>();
        for (ClassModel each : checks) {
            WhatACheckSays.of(each).forEach((where, said) -> {
                if (REPOSITORY.reachesAModuleThroughAParent(said)) {
                    walking.put(where, said);
                }
            });
        }

        assertEquals(Map.of(), walking,
                "a check works out which way another module is instead of naming it, so what it"
                        + " reads is decided by the directory the build was invoked from, and a step"
                        + " that lands nowhere leaves it sweeping whatever it found");
    }

    /**
     * And the words the rule looks for are words it would recognise.
     *
     * <p>A rule reading a repository that no longer writes what it refuses passes because there is
     * nothing to find, and so does one whose reading has stopped answering. The module's name is
     * taken from the repository rather than written here, so that this says nothing the rule above
     * would have to leave itself out of.
     */
    @Test
    void andTheWordsItLooksForAreWordsItWouldRecognise() {
        String module = REPOSITORY.modules().getFirst().getFileName().toString();
        String out = "..";
        assertTrue(REPOSITORY.reachesAModuleThroughAParent(List.of(out, module, "src", "main")),
                "a path built a step at a time");
        assertTrue(REPOSITORY.reachesAModuleThroughAParent(
                        List.of(out + "/" + module + "/src/main/resources")),
                "and one written whole");
        assertFalse(REPOSITORY.reachesAModuleThroughAParent(List.of(out)),
                "a step out that lands nowhere in particular is the repository's own business");
        assertFalse(REPOSITORY.reachesAModuleThroughAParent(List.of(module + "/src/main")),
                "and naming a module is naming a subject");
    }

    /**
     * And what a check writes in an annotation is something it said.
     *
     * <p>The half the rule above cannot show. A parameterized case takes its subjects from an
     * annotation, so a corpus named there is named as plainly as one named in the body — and it is
     * an element value, which never reaches the code the class holds. Read through the class file,
     * the way the rule reads it, because a reading that answered nothing would leave the rule
     * passing over every check in the repository with nothing to say why.
     */
    @Test
    void andWhatACheckWritesInAnAnnotationIsSomethingItSaid() {
        Map<String, List<String>> said = WhatACheckSays.of(EVERYTHING.read(
                "souther/architecture/NoCheckReachesAModuleByWalkingOutOfWhereItStandsTest"
                        + "$SaidOnlyInAnAnnotation"));
        assertEquals(List.of(SENTINEL, SENTINEL + "/and-another"),
                said.get("souther.architecture.NoCheckReachesAModuleByWalkingOutOfWhereItStands"
                        + "Test$SaidOnlyInAnAnnotation#takesItsSubjectsFromAnAnnotation"),
                "what the annotation says, in the order it says it: " + said);
    }

    /** A word nothing else writes, so that finding it is finding this one. */
    private static final String SENTINEL = "a-corpus-named-where-only-an-annotation-can-see-it";

    /**
     * A check whose subjects are written in an annotation and nowhere else.
     *
     * <p>Here to be read as a class file rather than to be run, so it carries the source of the
     * subjects without the annotation that would ask for them. What it names is a sentinel rather
     * than a real corpus: a class here naming one would be a check reaching another module, which is
     * what the rule above refuses, and an exemption for this one would be the hole it is for.
     */
    static final class SaidOnlyInAnAnnotation {
        @ValueSource(strings = {SENTINEL, SENTINEL + "/and-another"})
        void takesItsSubjectsFromAnAnnotation(String corpus) {
        }
    }
}
