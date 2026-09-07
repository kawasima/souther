package souther.lsp.analysis;

import org.junit.jupiter.api.Test;
import souther.compiler.query.Abandoned;
import souther.compiler.query.Abandonment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reading a workspace stops where the reading is, not where what it was looking for turns out to be.
 *
 * <p>What a walk of a root costs is the paths it goes through, and how many of them are Souther
 * sources says nothing about that. A workspace is mostly not Souther sources: it is a repository,
 * with a build directory and a history and whatever else is checked into it. Asked only at the paths
 * that turn out to be sources, a walk of a hundred thousand files reads all of them after being told
 * to stop — and a root holding none reads to the end without being asked once.
 *
 * <p>So the roots here hold nothing the walk is looking for. That is the case where a stop placed at
 * what was found never comes, and it is not a corner: a workspace that has not been built has no
 * class output anywhere in it, which is the second of these every time.
 */
class AWalkOfTheWorkspaceIsStoppedWhereItIsReadingTest {

    @Test
    void aRootWithNoSourceInItIsStillARootBeingRead() throws IOException {
        Workspace workspace = readingOnly(aRootOfOtherPeoplesFiles());

        assertThrows(Abandoned.class, () -> workspace.snapshot(Map.of()),
                "a root with nothing to find in it is a root this walked to the end");
    }

    @Test
    void aRootWithNothingBuiltInItIsStillARootBeingRead() throws IOException {
        Workspace workspace = readingOnly(aRootOfOtherPeoplesFiles());

        assertThrows(Abandoned.class, workspace::modulePath,
                "and looking for what a build left is the same walk over the same paths");
    }

    /** A workspace over {@code root} that has already been told to stop. */
    private static Workspace readingOnly(Path root) {
        Workspace workspace = new Workspace();
        workspace.setRoots(List.of(root.toUri().toString()));
        workspace.abandonWhen(new Abandonment(() -> true));
        return workspace;
    }

    /** A root with files in it, none of which is a Souther source or anything a build left. */
    private static Path aRootOfOtherPeoplesFiles() throws IOException {
        Path root = Files.createTempDirectory("workspace-walk");
        Files.writeString(root.resolve("README.md"), "not a source\n");
        Files.createDirectories(root.resolve("docs").resolve("pictures"));
        Files.writeString(root.resolve("docs").resolve("why.md"), "nor is this\n");
        return root;
    }
}
