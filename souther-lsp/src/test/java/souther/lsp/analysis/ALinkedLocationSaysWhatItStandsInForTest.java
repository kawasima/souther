package souther.lsp.analysis;

import org.junit.jupiter.api.Test;

import souther.compiler.Compiler;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Adequacy;
import souther.lsp.protocol.LspDiagnostic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An editor's linked location is a sentence about a place, and says what the place stands in for.
 *
 * <p>A warning about an edge no row is at points at the guard that drew it. Where the guard is one
 * of a body spliced in from a module this compile has no source for, the location it can be given
 * is the call in the reader's own file — so a link saying "the guard that draws that line" against
 * it says the guard is there, and it is not.
 *
 * <p>Here rather than only in the compiler's own fixture because this is a third renderer. The
 * terminal and the JSON a build reads both come from {@code souther-syntax}; an editor's related
 * information is assembled here, and it was the one surface that kept rendering a label and nothing
 * else after the other two had stopped. Nothing in the editor's own tests reads a link's message.
 */
class ALinkedLocationSaysWhatItStandsInForTest {

    private static final String URI = "file:///abs.sou";

    /**
     * A module put on the path, which this compile holds no source for.
     *
     * <p>Not the standard library, though that is out of sight too. What the library writes in this
     * language stays standing where rules are read, so a comparison inside one of its operations is
     * that operation's implementation and no caller's rule. A helper of any other published module
     * is spliced into whoever calls it, and the comparison it writes is the caller's model — read
     * where the caller wrote the call, and pointing at a guard the reader has no file for.
     */
    private static final String PUBLISHED = """
            module lib exposing ( big )

            let big (n: Int): Bool = n > 10
            """;

    /** Its fork draws lines on {@code n} that the one row is not at, and the warning about them
     *  points at the guard — which here is a copy, stamped with the call on line 9. */
    private static final String MODEL = """
            module demo

            import lib ( big )

            data Size = Int

            behavior sized : (n: Int) -> Size
                constructs Size
            let sized (n) = if big(n) then Size(1) else Size(0)

            example sized
                | "a big one" : (50) -> Size(1)
            """;

    private static List<LspDiagnostic.Related> linksOfTheEdgeWarning() {
        Analyzer analyzer = new Analyzer();
        analyzer.measure(Adequacy.Asked.warningsAt(Adequacy.Level.ALL));
        Map<String, List<LspDiagnostic>> byUri = analyzer.diagnostics(
                ModuleGraph.of(Map.of(URI, MODEL)),
                ModulePath.of(Compiler.compile(PUBLISHED)));

        List<LspDiagnostic> edges = byUri.getOrDefault(URI, List.of()).stream()
                .filter(d -> "E1916".equals(d.code()))
                .filter(d -> !d.related().isEmpty())
                .toList();
        assertFalse(edges.isEmpty(), () -> "an edge no row is at links to its guard: " + byUri);
        return edges.get(0).related();
    }

    @Test
    void aLinkIntoACopiedBodySaysWhereTheCodeIs() {
        for (LspDiagnostic.Related link : linksOfTheEdgeWarning()) {
            assertTrue(link.message().contains("`lib.big`"),
                    () -> "the link says what the place it points at stands in for: "
                            + link.message());
        }
    }

    /** And it is still a link the editor can open: where the code is written is not where a reader
     *  can be sent, and the range is the call this compile has a file for. */
    @Test
    void theLinkStillPointsSomewhereTheEditorCanOpen() {
        for (LspDiagnostic.Related link : linksOfTheEdgeWarning()) {
            assertEquals(URI, link.uri());
            assertEquals(8, link.range().start().line(), "the call, as the editor counts lines");
        }
    }
}
