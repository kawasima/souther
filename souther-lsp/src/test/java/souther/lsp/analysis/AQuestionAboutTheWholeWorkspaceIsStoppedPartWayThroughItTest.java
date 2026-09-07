package souther.lsp.analysis;

import org.junit.jupiter.api.Test;
import souther.compiler.query.Abandoned;
import souther.compiler.query.Abandonment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A question about every file in the workspace stops part way through the files.
 *
 * <p>Some of what an editor asks is a walk of the workspace that this layer does itself, rather than
 * a question put to the store: what a name is used in, what declarations match what the author is
 * typing, which file declares a module. Each turn of those reads a file and parses it, and none of
 * it reaches the store — so a stop that only the store asked about would never come, however long
 * the walk.
 *
 * <p>The compile is warmed first, and that is what makes this say anything. Everything the store
 * could be asked here is already answered at this revision, so an answer comes back without the
 * store getting as far as asking whether the work is still wanted. What is left to stop the walk is
 * the walk asking on its own account, which is the thing under test.
 */
class AQuestionAboutTheWholeWorkspaceIsStoppedPartWayThroughItTest {

    @Test
    void lookingForDeclarationsAcrossTheWorkspaceIsStopped() {
        Analyzer analyzer = new Analyzer();
        ModuleGraph graph = aWorkspace();
        assertEquals(24, analyzer.workspaceSymbols("Amount", graph).size(),
                "warm: every module declares one type the query names");

        analyzer.abandonWhen(new Abandonment(() -> true));

        assertThrows(Abandoned.class, () -> analyzer.workspaceSymbols("Amount", graph),
                "a walk of every file in the workspace is a walk that can be given up on");
    }

    @Test
    void whatTheStoreAlreadyAnsweredDoesNotHoldTheWalkOpen() {
        Analyzer analyzer = new Analyzer();
        ModuleGraph graph = aWorkspace();
        analyzer.diagnostics(graph);   // the compile, so nothing below has to be worked out again

        analyzer.abandonWhen(new Abandonment(() -> true));

        assertThrows(Abandoned.class, () -> analyzer.diagnostics(graph),
                "a diagnose over answers it already has is still a walk of the workspace");
    }

    private static ModuleGraph aWorkspace() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (int i = 0; i < 24; i++) {
            sources.put("file:///m" + i + ".sou", """
                    module m%d
                    data Amount%d = { of: Int }
                    """.formatted(i, i));
        }
        return ModuleGraph.of(sources);
    }
}
