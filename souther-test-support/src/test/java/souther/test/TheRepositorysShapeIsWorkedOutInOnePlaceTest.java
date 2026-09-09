package souther.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That nobody works out the repository's shape a second time.
 *
 * <p>Before {@link RepositoryLayout} there were eleven answers to where the repository root is and
 * eight to which modules it has. None of them was wrong when it was written. What they cost was
 * that each was one more place to be one module behind, and a check whose scan is one module behind
 * reports a pass about the modules it read and says nothing about the rest — which is the shape
 * those checks exist to refuse.
 *
 * <p>Moving them here fixes the eleven that existed. This is what stops the twelfth: a tripwire on
 * the ways the answer has actually been worked out before, and not an analysis of every way it
 * could be. It names what it caught, so the reply to it is to ask {@link RepositoryLayout} rather
 * than to spell the same derivation differently.
 *
 * <p>A path that steps out of a check's own directory to name another module is not covered here
 * and is not allowed either. It is refused where it can be seen as one thing rather than as a
 * spelling — over what a compiled method says, by
 * {@code NoCheckReachesAModuleByWalkingOutOfWhereItStandsTest}.
 */
class TheRepositorysShapeIsWorkedOutInOnePlaceTest {

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /** A way the shape has been worked out before, and what to ask instead. */
    private record Rederivation(String written, String instead) {}

    /** The two files that write these words because of what they are for. */
    private static final List<String> QUOTE_THEM =
            List.of("RepositoryLayout.java", "TheRepositorysShapeIsWorkedOutInOnePlaceTest.java");

    private static final List<Rederivation> REDERIVATIONS = List.of(
            new Rederivation("Path.of(\"\").toAbsolutePath()",
                    "RepositoryLayout.ofWorkingDirectory().root()"),
            new Rederivation("Files.walk(Path.of(\"..\"))",
                    "RepositoryLayout.southerSources(), or the source trees it names"),
            new Rederivation("Files.list(Path.of(\"..\"))",
                    "RepositoryLayout.modules()"),
            new Rederivation("\"<module>",
                    "RepositoryLayout.modules()"));

    @Test
    void nobodyWorksOutWhereTheRepositoryIsForThemselves() {
        List<String> found = new ArrayList<>();
        for (Path source : sourcesThatCouldRederive()) {
            String text = read(source);
            for (Rederivation rederivation : REDERIVATIONS) {
                if (text.contains(rederivation.written())) {
                    found.add(REPOSITORY.root().relativize(source) + " writes "
                            + rederivation.written() + " — ask " + rederivation.instead());
                }
            }
        }
        assertEquals(List.of(), found,
                "the repository's shape is RepositoryLayout's answer, and a second answer is a"
                        + " second place to be one module behind");
    }

    /**
     * And the scan reaches the files that would say so.
     *
     * <p>An empty scan satisfies the check above, and the way it would come to be empty is a source
     * tree this stopped reaching — the very thing the check is about.
     */
    @Test
    void andTheScanReachesTheSourcesToSayItOf() {
        List<Path> scanned = sourcesThatCouldRederive();
        assertTrue(scanned.size() > 500, "only " + scanned.size() + " sources scanned");
        assertTrue(scanned.stream().anyMatch(each -> each.toString().contains("souther-bench")),
                "including the module whose checks are about the repository");
    }

    /**
     * Every Java source but the two that have to write these words.
     *
     * <p>{@link RepositoryLayout} works the shape out because that is what it is for, and this
     * class quotes the derivations in order to refuse them. Named one file at a time rather than by
     * leaving out the module they are in: the rest of this module is held to the rule like
     * everything else.
     */
    private static List<Path> sourcesThatCouldRederive() {
        List<Path> out = new ArrayList<>();
        for (Path tree : REPOSITORY.sourceTrees()) {
            try (Stream<Path> walk = Files.walk(tree)) {
                walk.filter(Files::isRegularFile)
                        .filter(each -> each.getFileName().toString().endsWith(".java"))
                        .filter(each -> !QUOTE_THEM.contains(each.getFileName().toString()))
                        .forEach(out::add);
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
        out.sort(Path::compareTo);
        return out;
    }

    private static String read(Path source) {
        try {
            return Files.readString(source);
        } catch (MalformedInputException notText) {
            return "";
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
