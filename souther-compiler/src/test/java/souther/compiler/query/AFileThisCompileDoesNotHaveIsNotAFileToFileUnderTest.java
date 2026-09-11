package souther.compiler.query;


import souther.compiler.diag.Placement;
import souther.compiler.source.SourceId;

import souther.compiler.diag.msg.NameMessage;


import souther.compiler.diag.Diagnostic;
import souther.compiler.meta.ModulePath;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A position says which source it was read from, and that source is not always one this compile has.
 * The prelude and a module read back off the module path are both read somewhere else, and a caller
 * can hand over any set of files it likes.
 *
 * <p>What is guarded here is availability as much as attribution. A report filed under a name
 * {@link Compilation#diagnostics()} has no entry for is dropped on the floor, and a problem the
 * author never sees is worse than one shown against the wrong file. So a claim this compile cannot
 * honour falls back to the module's own source rather than being taken at its word.
 */
class AFileThisCompileDoesNotHaveIsNotAFileToFileUnderTest {

    private static final String M = """
            module m
            data N = { v: Int }
            """;

    /** A report about module {@code m} whose primary region was read from {@code positionsFile}. */
    private static Db.Found about(String positionsFile) {
        Diagnostic d = Diagnostic.say(new NameMessage.NoValueOfThatNameInScope("x"))
                .at(Placement.aFileOfThisCompile(new SourceId(positionsFile)).at(2, 1), 4).build();
        return new Db.Found("m", null, Report.of(d));
    }

    private static Compilation ofDocuments(Map<String, String> byId) {
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
    }

    @Test
    void aKnownPositionalSourceIsUsed() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("m.sou", M);
        Compilation c = ofDocuments(byId);

        assertEquals(new SourceId("m.sou"), c.filedUnderOf(about("m.sou")));
    }

    @Test
    void anUnknownPositionalSourceFallsBackToTheModulesSource() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("m.sou", M);
        Compilation c = ofDocuments(byId);

        assertEquals(new SourceId("m.sou"), c.filedUnderOf(about("somewhere-else.sou")),
                "the position names a file this compile was never handed");
    }

    /** The failure this guards against is silent: {@code diagnostics()} keys its buckets by the
     *  sources it was given, so a report filed under anything else is simply not there. */
    @Test
    void anUnknownPositionalSourceDoesNotMakeTheReportDisappear() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("m.sou", M);
        Compilation c = ofDocuments(byId);

        List<SourceId> saidAt = c.publishSourceIdsOf(about("somewhere-else.sou"));

        assertEquals(1, saidAt.size(), saidAt.toString());
        assertTrue(c.sourceIds().contains(saidAt.get(0)),
                "it is said at a source this compile has, or it is said nowhere: " + saidAt);
    }

    /** A workspace names its sources by document URI, and never fills the index a compile of a
     *  plain list keeps — so reading the guard off that index would leave the editor unfixed. */
    @Test
    void aKnownSourceIsRecognisedThroughOfDocuments() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("file:///w/m.sou", M);
        Compilation c = ofDocuments(byId);

        assertEquals(List.of(new SourceId("file:///w/m.sou")), c.publishSourceIdsOf(about("file:///w/m.sou")));
    }

    /** A compile of one source tells its caller nothing about which file, since the caller knows —
     *  but it still has one to quote from, and files its problems under it. */
    @Test
    void aKnownSourceIsRecognisedThroughOfSource() {
        Compilation c = Compilation.ofSource(M, "Main");

        assertEquals(List.of(new SourceId("0")), c.publishSourceIdsOf(about("0")));
    }
}
