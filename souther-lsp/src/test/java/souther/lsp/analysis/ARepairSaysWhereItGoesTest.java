package souther.lsp.analysis;

import souther.lsp.protocol.CodeAction;
import souther.lsp.protocol.Position;
import souther.lsp.protocol.Range;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a quick fix writes, which is not where the report that produced it was said.
 *
 * <p>The two are the same stretch for a plain misspelled name, which is the only case the offer used
 * to be tried on, and they are not for a name written under a qualifier: the report is about the
 * whole of {@code l.Cst} because which of its two parts is wrong is what it settles, and the edit is
 * about the part after the dot. An offer built from the report's range wrote the type name over
 * both and left the document naming no module at all.
 *
 * <p>Every expectation here is read off the source rather than counted out by hand, so a test says
 * where the edit goes and not what some column happened to be while it was written.
 */
class ARepairSaysWhereItGoesTest {

    private static final String URI = "file:///m.sou";
    private static final String LIB_URI = "file:///lib.sou";

    private static final String LIB = """
            module lib

            data Cost = Int
            """;

    @Test
    void aNameWrittenUnderAQualifierIsRepairedAfterTheDot() {
        String text = """
            module m

            import lib as l ( Cost )

            data Draft = { plannedCost: l.Cst }
            """;
        CodeAction.Edit edit = theOneEdit(text, on(text, "l.Cst"));

        assertEquals("Cost", edit.newText());
        assertEquals(spanOf(text, "Cst"), edit.range(),
                "the module was named right; the part after the dot is what it has no such name for");
    }

    @Test
    void aQualifierNamingNoModuleIsRepairedBeforeTheDot() {
        String text = """
            module m

            import lib as ledger ( Cost )

            data Draft = { plannedCost: ledgr.Cost }
            """;
        CodeAction.Edit edit = theOneEdit(text, on(text, "ledgr.Cost"));

        assertEquals("ledger", edit.newText());
        assertEquals(spanOf(text, "ledgr"), edit.range(),
                "the type name was written right; the qualifier is what names no module");
    }

    /** The offer used to give up on any document with an import, because the compile it recovered
     *  the suggestion from was of that document alone and could not resolve one. */
    @Test
    void aDocumentWithImportsIsOffered() {
        String text = """
            module m

            import lib as l ( Cost )

            data Draft = { plannedCost: Cost }

            behavior price : (draft: Draft) -> Cost
            let price (draft) = drft.plannedCost
            """;
        CodeAction.Edit edit = theOneEdit(text, on(text, "drft"));

        assertEquals("draft", edit.newText());
        assertEquals(spanOf(text, "drft"), edit.range());
    }

    /**
     * A selection is drawn by somebody dragging over the document, so it reaches as many repairs as
     * it covers and the reader picks. One of the two is what the range happens to start on, and an
     * offer answering with that one would be answering a question about a position.
     */
    @Test
    void aSelectionOverTwoMisspellingsOffersBoth() {
        String text = """
            module m

            data Draft = { plannedCost: Int }

            behavior price : (draft: Draft) -> Int
            let price (draft) = drft.plannedCost
            behavior agreed : (agreement: Draft) -> Int
            let agreed (agreement) = agrement.plannedCost
            """;
        List<String> written = new ArrayList<>();
        for (CodeAction action : actions(text, over(text, "drft", "agrement"))) {
            written.add(assertInstanceOf(CodeAction.Applied.class, action).edit().newText());
        }
        assertEquals(List.of("draft", "agreement"), written);
    }

    /** Away from every repair there is nothing to offer, whatever the document is wrong about. */
    @Test
    void aRangeReachingNoRepairIsOfferedNothing() {
        String text = """
            module m

            data Draft = { plannedCost: Int }

            behavior price : (draft: Draft) -> Int
            let price (draft) = drft.plannedCost
            """;
        assertEquals(List.of(), actions(text, on(text, "module m")));
    }

    /**
     * The file an edit lands in is the file it is offered in, and a report published in two of them
     * is one report. The reader of the other file is shown the marker and offered nothing to apply
     * to a line that is not what is wrong.
     */
    @Test
    void aRepairIsOfferedOnlyInTheFileItsEditIsWrittenIn() {
        String text = """
            module m

            import lib as l ( Cost )

            data Draft = { plannedCost: l.Cst }
            """;
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(URI, text);
        sources.put(LIB_URI, LIB);
        ModuleGraph graph = ModuleGraph.of(sources);
        Analyzer analyzer = new Analyzer();

        Range everywhereInLib = new Range(new Position(0, 0),
                new Position((int) LIB.lines().count(), 0));
        assertEquals(List.of(), analyzer.codeActions(LIB_URI, LIB, everywhereInLib, graph),
                "the misspelling is in the other file, whatever this one is told about it");
        assertTrue(!analyzer.codeActions(URI, text, on(text, "l.Cst"), graph).isEmpty(),
                "and the file that holds it is offered the edit");
    }

    /**
     * A module written over two files answers about the file the edit is in, not the file its
     * module is declared in. What a report is filed under falls back to the module's own source
     * where the report claims none, and the rows are in the attached file — so a fix offered by
     * publication would be offered on the model's text and applied to whatever sits at those
     * numbers there.
     */
    @Test
    void anEditInAnAttachedFileIsOfferedOnThatFile() {
        String model = """
            module m

            data D = { v: Int }
            behavior f : (d: D) -> D
            let f (d) = d
            """;
        String attached = """
            examples for m

            let origin = D { v = 1 }

            example f
                | "a" : (orign) -> D { v = 1 }
            """;
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(URI, model);
        sources.put("file:///m.examples.sou", attached);
        ModuleGraph graph = ModuleGraph.of(sources);
        Analyzer analyzer = new Analyzer();

        List<CodeAction> onTheAttached = analyzer.codeActions("file:///m.examples.sou", attached,
                on(attached, "orign"), graph);
        assertEquals(1, onTheAttached.size(), onTheAttached.toString());
        CodeAction.Edit edit =
                assertInstanceOf(CodeAction.Applied.class, onTheAttached.get(0)).edit();
        assertEquals("origin", edit.newText());
        assertEquals(spanOf(attached, "orign"), edit.range());

        Range everywhereInTheModel = new Range(new Position(0, 0),
                new Position((int) model.lines().count(), 0));
        assertEquals(List.of(), analyzer.codeActions(URI, model, everywhereInTheModel, graph),
                "the model's own file holds none of the characters this edit rewrites");
    }

    private static List<CodeAction> actions(String text, Range requested) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(URI, text);
        sources.put(LIB_URI, LIB);
        return new Analyzer().codeActions(URI, text, requested, ModuleGraph.of(sources));
    }

    private static CodeAction.Edit theOneEdit(String text, Range requested) {
        List<CodeAction> offered = actions(text, requested);
        assertEquals(1, offered.size(), offered.toString());
        return assertInstanceOf(CodeAction.Applied.class, offered.get(0)).edit();
    }

    /** The range an editor sends with the caret somewhere in {@code written}. */
    private static Range on(String text, String written) {
        return spanOf(text, written);
    }

    /** The range an editor sends for a selection drawn from the first of these to the last. */
    private static Range over(String text, String from, String to) {
        return new Range(spanOf(text, from).start(), spanOf(text, to).end());
    }

    /** Where {@code written} is in {@code text}, in the editor's line and character numbers. */
    private static Range spanOf(String text, String written) {
        int at = text.indexOf(written);
        assertTrue(at >= 0, () -> "the source does not contain `" + written + "`");
        assertEquals(at, text.lastIndexOf(written),
                () -> "`" + written + "` is written more than once, so it names no one place");
        return new Range(positionOf(text, at), positionOf(text, at + written.length()));
    }

    private static Position positionOf(String text, int offset) {
        String before = text.substring(0, offset);
        int line = (int) before.lines().count() - (before.endsWith("\n") ? 0 : 1);
        return new Position(line, offset - (before.lastIndexOf('\n') + 1));
    }
}
