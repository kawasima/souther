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

    /**
     * Away from every repair there is nothing to offer, whatever the document is wrong about.
     *
     * <p>Asked beside the range that does offer one, in the same document and the same run. An
     * offer that goes missing for any reason at all leaves an empty list too, and that is what an
     * emptiness on its own cannot be told apart from.
     */
    @Test
    void aRangeReachingNoRepairIsOfferedNothing() {
        String text = """
            module m

            data Draft = { plannedCost: Int }

            behavior price : (draft: Draft) -> Int
            let price (draft) = drft.plannedCost
            """;
        assertEquals(1, actions(text, on(text, "drft")).size(),
                "there is an offer in this document to go missing");
        assertEquals(List.of(), actions(text, on(text, "module m")));
    }

    /**
     * An offer stands where the problem is marked, so a file the problem is not marked in is
     * offered nothing however much it is asked. The imported module is fine; what is wrong is the
     * name written in the file that imports it.
     */
    @Test
    void aFileTheProblemIsNotMarkedInIsOfferedNothing() {
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
                "nothing is marked here, whatever this file is asked");
        assertTrue(!analyzer.codeActions(URI, text, on(text, "l.Cst"), graph).isEmpty(),
                "and the file the problem is marked in is offered the edit");
    }

    /**
     * A module written over two files marks the problem in the file it is written in, and that is
     * the file the offer stands in. Not the file the module is declared in: a reader looking at the
     * model is looking at text this problem says nothing about.
     */
    @Test
    void aProblemInAnAttachedFileIsOfferedOnThatFile() {
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
                "the model's own file marks nothing about the rows in the other one");
    }

    /**
     * The offer stands wherever the problem is marked, and the marked stretch is not the stretch
     * the edit rewrites. A caret on the qualifier of {@code l.Cst} is on the squiggle the author is
     * looking at; the characters to change are after the dot, and an offer that compared the caret
     * against those is an offer that is not there where the author asks for it.
     */
    @Test
    void theOfferStandsAtEveryPositionTheProblemIsMarkedAt() {
        String text = """
            module m

            import lib as l ( Cost )

            data Draft = { plannedCost: l.Cst }
            """;
        Range marked = spanOf(text, "l.Cst");
        for (int at = 0; at < "l.Cst".length(); at++) {
            Range caret = caretAt(text, text.indexOf("l.Cst") + at);
            List<CodeAction> offered = actions(text, caret);
            assertEquals(1, offered.size(),
                    () -> "the caret is inside " + marked + " at " + caret + ": " + offered);
            assertEquals("Cost",
                    assertInstanceOf(CodeAction.Applied.class, offered.getFirst()).edit().newText());
        }
    }

    /** And the same where the part to rewrite is the qualifier: a caret on the type name is on the
     *  same marker, and the fix it is offered writes before the dot. */
    @Test
    void theOfferStandsOnTheWholeMarkerWhenTheEditIsTheQualifier() {
        String text = """
            module m

            import lib as ledger ( Cost )

            data Draft = { plannedCost: ledgr.Cost }
            """;
        for (int at = 0; at < "ledgr.Cost".length(); at++) {
            Range caret = caretAt(text, text.indexOf("ledgr.Cost") + at);
            List<CodeAction> offered = actions(text, caret);
            assertEquals(1, offered.size(), () -> "at " + caret + ": " + offered);
            CodeAction.Edit edit =
                    assertInstanceOf(CodeAction.Applied.class, offered.getFirst()).edit();
            assertEquals("ledger", edit.newText());
            assertEquals(spanOf(text, "ledgr"), edit.range());
        }
    }

    /**
     * Two files, each with a misspelling of its own, and each keeps its own fix.
     *
     * <p>What a file reads a report as is asked of the files that report is said in, and asking it
     * of one it says nothing about is refused rather than answered emptily. A walk over every
     * report in the workspace meets both of these on the way to either, so a walk that asked
     * without looking lost the fix that was there — not the other file's, its own, and every other
     * offer in the request with it.
     *
     * <p>Said as what is offered rather than as what is not. An offer that goes missing looks the
     * same from outside as one there was never anything to make, and the second is what a test
     * asserting emptiness cannot tell it from.
     */
    @Test
    void eachOfTwoFilesKeepsItsOwnFix() {
        String first = """
            module first

            behavior price : (draft: Int) -> Int
            let price (draft) = drft
            """;
        String second = """
            module second

            behavior agreed : (agreement: Int) -> Int
            let agreed (agreement) = agrement
            """;
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("file:///first.sou", first);
        sources.put("file:///second.sou", second);
        ModuleGraph graph = ModuleGraph.of(sources);
        Analyzer analyzer = new Analyzer();

        List<CodeAction> onFirst = analyzer.codeActions("file:///first.sou", first,
                caretAt(first, first.indexOf("drft")), graph);
        assertEquals(1, onFirst.size(), onFirst::toString);
        assertEquals("draft",
                assertInstanceOf(CodeAction.Applied.class, onFirst.getFirst()).edit().newText());

        List<CodeAction> onSecond = analyzer.codeActions("file:///second.sou", second,
                caretAt(second, second.indexOf("agrement")), graph);
        assertEquals(1, onSecond.size(), onSecond::toString);
        assertEquals("agreement",
                assertInstanceOf(CodeAction.Applied.class, onSecond.getFirst()).edit().newText());
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

    /** The range an editor sends for a selection drawn over {@code written}. */
    private static Range on(String text, String written) {
        return spanOf(text, written);
    }

    /** The range an editor sends with the caret at {@code offset} and nothing selected. */
    private static Range caretAt(String text, int offset) {
        Position at = positionOf(text, offset);
        return new Range(at, at);
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
