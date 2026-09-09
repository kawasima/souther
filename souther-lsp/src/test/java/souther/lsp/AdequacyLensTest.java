package souther.lsp;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.lsp.analysis.Analyzer;
import souther.lsp.analysis.ModuleGraph;
import souther.lsp.protocol.CodeAction;
import souther.lsp.protocol.CodeLens;
import souther.lsp.protocol.Position;
import souther.lsp.protocol.Range;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an editor is told about the rows, where the author is working.
 *
 * <p>Off unless asked for, and answered from the workspace or not at all. One document cannot say
 * what a behavior's rows cover: they are written across its module's own source and any number of
 * attached files, so reading one file would report what another covers as uncovered — which is worse
 * than saying nothing, being wrong rather than absent.
 */
class AdequacyLensTest {

    private static final String MODULE = "file:///trip.sou";
    private static final String ATTACHED = "file:///trip.examples.sou";

    private static final String TRIP = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Draft = { cost: Amount }
            data Submitted = { cost: Amount }
            data Waiting = { cost: Amount }

            behavior submit : (request: Draft) -> Submitted | Waiting
                constructs Submitted, Waiting

            let submit (request) = {
                guard request.cost.value <= 100 else Waiting { cost = request.cost }
                Submitted { cost = request.cost }
            }

            example submit
                | (Draft { cost = Amount(50) }) -> Submitted { cost = Amount(50) }
            """;

    private static final String EDGES = "file:///edges.sou";

    /** A behavior whose only gaps are the two lines its invariant draws: the output is one data, the
     *  body forks nowhere, and the one row covers the one class the position divides into. So the
     *  block a person is offered holds boundary rows or nothing. */
    private static final String ONLY_EDGES = """
            module edges

            data Amount = Int
                invariant value >= 0 && value <= 10

            data Ok = { n: Amount }

            behavior keep : (a: Amount) -> Ok
                constructs Ok

            let keep (a) = Ok { n = a }

            example keep
                | "mid" : (Amount(5)) -> Ok { n = Amount(5) }
            """;

    private static ModuleGraph graphOf(Map<String, String> documents) {
        return ModuleGraph.of(new LinkedHashMap<>(documents));
    }

    private static Analyzer measuring(Adequacy.Level level) {
        return measuring(level, true);
    }

    /** @param resolves what the client's handshake said about coming back for an action's edit */
    private static Analyzer measuring(Adequacy.Level level, boolean resolves) {
        Analyzer analyzer = new Analyzer();
        analyzer.measure(Adequacy.Asked.reportOnly(level));
        analyzer.resolvesActions(resolves);
        return analyzer;
    }

    /** The default. Nothing is measured, so nothing is drawn. */
    @Test
    void anEditorThatDidNotAskIsShownNothing() {
        Analyzer analyzer = new Analyzer();

        assertEquals(List.of(),
                analyzer.codeLenses(MODULE, graphOf(Map.of(MODULE, TRIP))));
        assertFalse(analyzer.measuring());
    }

    /**
     * Asked for, the numbers appear on the behavior's own line.
     *
     * <p>The boundary ratio is over what this behavior is owed a row for. The line
     * {@code Amount}'s invariant draws is owed to that declaration and answered by a row written
     * for any behavior carrying the type, so the point against it is not this author's work and is
     * not in the ratio drawn over their behavior.
     */
    @Test
    void theNumbersAreDrawnOnTheDeclarationTheyAreAbout() {
        List<CodeLens> lenses = measuring(Adequacy.Level.ALL)
                .codeLenses(MODULE, graphOf(Map.of(MODULE, TRIP)));

        assertEquals(1, lenses.size());
        assertEquals(9, lenses.get(0).range().start().line(), "the `behavior` line, zero-based");
        assertEquals("1 row · out 1/2 · boundary 2/5 · branch 1/2", lenses.get(0).title());
    }

    /**
     * A row in an attached file counts.
     *
     * <p>This is the reason the question is asked of a workspace. The second row is written in
     * another document entirely, and it is what makes `Waiting` a case something claims.
     */
    @Test
    void aRowInAnAttachedFileIsCounted() {
        Map<String, String> workspace = new LinkedHashMap<>();
        workspace.put(MODULE, TRIP);
        workspace.put(ATTACHED, """
                examples for example.trip

                example submit
                    | (Draft { cost = Amount(500) }) -> Waiting { cost = Amount(500) }
                """);

        List<CodeLens> lenses = measuring(Adequacy.Level.ALL).codeLenses(MODULE, graphOf(workspace));

        assertEquals(1, lenses.size());
        assertEquals("2 rows · out 2/2 · boundary 3/5 · branch 2/2", lenses.get(0).title());
    }

    /** Nothing has been claimed about a behavior no row names, so there is nothing to draw over it. */
    @Test
    void aBehaviorNoRowNamesGetsNoLine() {
        String unexampled = TRIP.substring(0, TRIP.indexOf("example submit"));

        assertEquals(List.of(), measuring(Adequacy.Level.ALL)
                .codeLenses(MODULE, graphOf(Map.of(MODULE, unexampled))));
    }

    /** Below `all` the arms are not run, so the branch figure is left off rather than printed as
     * nothing reached. */
    @Test
    void whatWasNotMeasuredIsLeftOffRatherThanShownAsZero() {
        String title = measuring(Adequacy.Level.WITNESS)
                .codeLenses(MODULE, graphOf(Map.of(MODULE, TRIP))).get(0).title();

        assertTrue(title.startsWith("1 row · out 1/2"), title);
        assertFalse(title.contains("branch"), title);
    }

    // --- writing the rows in ----------------------------------------------------------------------

    private static Range on(int line) {
        return new Range(new Position(line, 0), new Position(line, 0));
    }

    /**
     * A guard on a name every case of a sum spreads, with the rows written under one of them.
     *
     * <p>The line is read once per case and owes one row, so rows under {@code P} settle all four of
     * its points — and {@code T}'s coordinates go on being counted, because a report counts a line
     * at each position it was read at. Nothing is left to write and there are findings all the same.
     */
    private static final String SPREAD_URI = "file:///spread.sou";

    private static final String SETTLED_UNDER_ONE_CASE = """
            module spread

            data Base = { deadline: Int }
            data P = { ...Base, x: Int }
            data T = { ...Base, y: Int }
            data Req = P | T

            data Ok
            data No

            behavior check : (r: Req) -> Ok | No

            let check (r) = {
                guard r.deadline > 10 else No
                Ok
            }

            example check
                | "on"   : (P { deadline = 11, x = 0 }) -> Ok
                | "off"  : (P { deadline = 10, x = 0 }) -> No
                | "in"   : (P { deadline = 12, x = 0 }) -> Ok
                | "out"  : (P { deadline = 9, x = 0 }) -> No
                | "tOn"  : (T { deadline = 11, y = 0 }) -> Ok
                | "tOff" : (T { deadline = 10, y = 0 }) -> No
            """;

    private static final String PRODUCER_URI = "file:///producer.sou";

    private static final String CARRIER_URI = "file:///carrier.sou";

    /** The module that writes the rule, and so owns the line its {@code invariant} draws. */
    private static final String PRODUCER = """
            module example.producer exposing ( Cap )

            data Cap = Int
                invariant floor = value >= 0
            """;

    /** A module that only carries the imported type: it reads the line at its own position and owes
     *  a row at none of its points, because the declaration that drew it is somewhere else. The ON
     *  point of that line is one this module reads, has no row at, and does not answer for. */
    private static final String CARRIER = """
            module example.carrier

            import example.producer (Cap)

            data Holds = { c: Cap }

            behavior take : (h: Holds) -> Int
            let take (h) = 1

            example take
                | "x" : (Holds { c = Cap(1) }) -> 1
            """;

    /**
     * Neither client is offered rows for a line another module answers for.
     *
     * <p>A module carrying an imported type reads that type's lines wherever it takes the position
     * in, and the rows for them are written where the declaration is. So a coordinate of one is
     * something this module is short of and nothing it can be asked to write.
     *
     * <p>The same two clients and a second way to tell them apart. Read as work, the offer is made
     * and the search behind it leaves the point out, so a client that comes back for the edit is
     * handed nothing — and one that wanted it at once was never shown the offer.
     */
    @Test
    void neitherClientIsOfferedRowsForALineAnotherModuleAnswersFor() {
        Map<String, String> documents = Map.of(PRODUCER_URI, PRODUCER, CARRIER_URI, CARRIER);

        assertEquals(List.of(), measuring(Adequacy.Level.ALL, true)
                        .codeActions(CARRIER_URI, CARRIER, on(6), graphOf(documents)),
                "a client that comes back for the edit is offered nothing");
        assertEquals(List.of(), measuring(Adequacy.Level.ALL, false)
                        .codeActions(CARRIER_URI, CARRIER, on(6), graphOf(documents)),
                "and neither is one that wants it now");
    }

    /**
     * Neither client is offered rows where there are none to write.
     *
     * <p>What the handshake settles is when the rows are worked out and never whether the offer is
     * made, so the two clients answer alike or the contract is only kept by the one that pays for it
     * up front.
     *
     * <p>The state that tells them apart is a finding standing at a coordinate of a line another
     * position already answered. Read off the findings, the deferred client is offered rows and gets
     * none when it comes back; the eager client composes first and offers nothing. What is owed is
     * the question, and a line owed no row is no work whichever coordinate a report counted it at.
     */
    @Test
    void neitherClientIsOfferedRowsWhereTheLinesAreAnsweredAlready() {
        Map<String, String> documents = Map.of(SPREAD_URI, SETTLED_UNDER_ONE_CASE);

        assertEquals(List.of(), measuring(Adequacy.Level.ALL, true)
                        .codeActions(SPREAD_URI, SETTLED_UNDER_ONE_CASE, on(10),
                                graphOf(documents)),
                "a client that comes back for the edit is offered nothing");
        assertEquals(List.of(), measuring(Adequacy.Level.ALL, false)
                        .codeActions(SPREAD_URI, SETTLED_UNDER_ONE_CASE, on(10),
                                graphOf(documents)),
                "and neither is one that wants it now");
    }

    /**
     * The offer on a behavior's declaration writes the block `--generate` prints.
     *
     * <p>Rows, with every answer left owed, for the same reason the command's output is: the
     * compiler does not know what the model owes, and a row it filled in would be an assertion
     * nobody made. What is written into somebody's file is source — which is what keeps the
     * checker, the formatter and a rename reaching it after it lands — and the prose beside the
     * rows is what arrives commented.
     */
    @Test
    void theRowsABehaviorDoesNotCoverCanBeWrittenIn() {
        Analyzer analyzer = measuring(Adequacy.Level.ALL);
        ModuleGraph graph = graphOf(Map.of(MODULE, TRIP));
        List<CodeAction> actions = analyzer.codeActions(MODULE, TRIP, on(9), graph);

        assertEquals(1, actions.size(), actions.toString());
        assertEquals("Write the rows `submit` does not cover", actions.get(0).title());
        // Offered without the rows, which is the point of offering it: what they cost is paid by
        // somebody taking it.
        CodeAction.Deferred offered =
                assertInstanceOf(CodeAction.Deferred.class, actions.get(0));

        CodeAction.Edit taken = analyzer.resolve(offered, TRIP, graph);
        assertNotNull(taken, "and taking it writes rows");
        assertTrue(taken.newText().contains("-> <?>"), taken.newText());
        assertTrue(taken.newText().lines().anyMatch(line -> line.startsWith("example ")),
                "the rows are written as rows: " + taken.newText());
        // And what is written into the file compiles with the document it lands in, which is what
        // keeps everything that reads source reading these. Asserted on the document rather than on
        // the block, because that is what an author is left with.
        assertEquals(List.of(),
                errorsIn(TRIP.stripTrailing() + "\n" + taken.newText()),
                "and the document goes on compiling with them in it");
    }

    /** What a compile of {@code source} refuses it for, which is nothing where it compiles. */
    private static List<String> errorsIn(String source) {
        List<String> said = new ArrayList<>();
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        for (souther.compiler.query.Db.Found each : compilation.db().allReports()) {
            if (each.report().isError()) {
                said.add(each.report().diagnostic().code());
            }
        }
        return said;
    }

    /**
     * A client that will not come back for the edit is handed it.
     *
     * <p>What the handshake settles is when the rows are worked out, and never whether the offer is
     * made. Deferring to a client that does not resolve would show an offer that does nothing when
     * it is taken, which is the same source-with-no-rows the eager path was written against.
     */
    @Test
    void aClientThatDoesNotResolveIsHandedTheEditWithTheOffer() {
        ModuleGraph graph = graphOf(Map.of(MODULE, TRIP));
        List<CodeAction> actions = measuring(Adequacy.Level.ALL, false)
                .codeActions(MODULE, TRIP, on(9), graph);

        assertEquals(1, actions.size(), actions.toString());
        CodeAction.Applied eager = assertInstanceOf(CodeAction.Applied.class, actions.get(0));

        Analyzer resolving = measuring(Adequacy.Level.ALL, true);
        CodeAction.Deferred offered = assertInstanceOf(CodeAction.Deferred.class,
                resolving.codeActions(MODULE, TRIP, on(9), graph).get(0));
        assertEquals(eager.edit().newText(), resolving.resolve(offered, TRIP, graph).newText(),
                "and the rows are the same rows either way");
    }

    /**
     * An offer names a behavior of a module written in a document, and taking it checks all three.
     *
     * <p>A document can be given another module's header while a behavior of that name goes on
     * existing somewhere else. Checked only against the module, the rows would be composed from the
     * source that still has the behavior and written into the one that no longer does.
     */
    @Test
    void anOfferIsNotTakenWhereItsBehaviorHasMovedToAnotherDocument() {
        String other = "file:///other.sou";
        Analyzer analyzer = measuring(Adequacy.Level.ALL);
        ModuleGraph before = graphOf(Map.of(MODULE, TRIP));
        CodeAction.Deferred offered = assertInstanceOf(CodeAction.Deferred.class,
                analyzer.codeActions(MODULE, TRIP, on(9), before).get(0));

        // The document the offer was made about now declares something else, and what it was made
        // about is written in the other one.
        ModuleGraph after = graphOf(Map.of(
                MODULE, TRIP.replaceFirst("module \\S+", "module example.moved"),
                other, TRIP));
        assertNull(analyzer.resolve(offered, TRIP, after),
                "the behavior the offer names is not written in the document it names");
    }

    /**
     * The rows at an edge are offered whatever the build was measuring.
     *
     * <p>What an author asked for by taking "write the rows this does not cover" is those rows.
     * Composing a value costs a decoder run for each point it settles, which is not what a build at
     * {@code witness} promises — so the measurement composes none, and a build is not slowed by
     * values nobody asked to see. But taking the action is asking, and it is asked once rather than
     * on every keystroke, so what it costs is paid where somebody wanted it.
     *
     * <p>The level does not decide it, because it is not about how much to measure. Read off the
     * level, the editor offered to write rows at {@code witness} and put a comment in somebody's
     * source, since the block held the reason nothing was composed and no rows.
     *
     * <p>An offer to write rows still has to write rows. What there is to write is the generator's
     * answer either way, and the block is asked how many rows it holds rather than whether its text
     * is blank.
     */
    @Test
    void theRowsAtAnEdgeAreOfferedWhateverTheBuildMeasured() {
        assertEquals(1, measuring(Adequacy.Level.ALL)
                        .codeActions(EDGES, ONLY_EDGES, on(7),
                                graphOf(Map.of(EDGES, ONLY_EDGES))).size(),
                "at `all` the two lines are rows to write");

        assertEquals(1, measuring(Adequacy.Level.WITNESS)
                        .codeActions(EDGES, ONLY_EDGES, on(7),
                                graphOf(Map.of(EDGES, ONLY_EDGES))).size(),
                "and at `witness` they are the same rows: taking the action is what asks for them");
    }

    /**
     * The rows the action writes are not printed under a sentence saying nothing offers them.
     *
     * <p>Both are answers about the same request, and they were read from different places. A line
     * an {@code invariant} drew is owed to the declaration, and what a generation can do about it is
     * what this request's search composed — read from the measurement instead, a build at
     * {@code witness} composes nothing while it measures, so the block printed the rows the action
     * had just composed and then said no row was on offer (issue #1062).
     *
     * <p>At {@code witness} because that is where the two come apart. At {@code all} the measurement
     * composes values of its own and the two answers agree by accident.
     */
    @Test
    void theRowsTheActionWritesAreNotPrintedUnderASentenceSayingNothingOffersThem() {
        Analyzer analyzer = measuring(Adequacy.Level.WITNESS);
        ModuleGraph graph = graphOf(Map.of(EDGES, ONLY_EDGES));
        CodeAction.Deferred offered = assertInstanceOf(CodeAction.Deferred.class,
                analyzer.codeActions(EDGES, ONLY_EDGES, on(7), graph).get(0),
                "there is work to offer");

        CodeAction.Edit taken = analyzer.resolve(offered, ONLY_EDGES, graph);
        assertNotNull(taken, "and taking it writes rows");
        assertFalse(taken.newText().contains("nothing offers a row"),
                () -> "the rows are right there: " + taken.newText());
    }

    /** With one document there is nothing to offer: the values a row writes are built through the
     * module's derived decoders, and its imports are part of that. */
    @Test
    void withNoWorkspaceThereIsNothingToOffer() {
        assertEquals(List.of(),
                measuring(Adequacy.Level.ALL).codeActions(MODULE, TRIP, on(9)));
    }

    /**
     * The offer stands at every position the declaration is written over, and at no other.
     *
     * <p>Asked from every position in the document rather than from the one column a helper sends.
     * A lens is drawn at a point, and the point a declaration begins at was what the offer compared
     * a caret against — so the offer was made at the first column of the {@code behavior} line and
     * nowhere else on it, which no test said either way.
     *
     * <p>What decides it here is read off the source: from the first character of {@code behavior}
     * to the last of the declaration's final line. The offer works it out from the syntax tree, so
     * the two would have to be wrong in the same way to agree.
     */
    @Test
    void theOfferStandsExactlyWhereTheDeclarationIsWritten() {
        Analyzer analyzer = measuring(Adequacy.Level.ALL);
        ModuleGraph graph = graphOf(Map.of(MODULE, TRIP));
        String lastLine = "    constructs Submitted, Waiting";
        int from = TRIP.indexOf("behavior submit");
        int to = TRIP.indexOf(lastLine) + lastLine.length();

        List<String> offeredOutsideIt = new ArrayList<>();
        List<String> withheldInsideIt = new ArrayList<>();
        String[] lines = TRIP.split("\n", -1);
        int startOfLine = 0;
        for (int line = 0; line < lines.length; line++) {
            for (int column = 0; column <= lines[line].length(); column++) {
                boolean offered = offersRows(analyzer, MODULE, TRIP, caret(line, column), graph);
                int at = startOfLine + column;
                if (offered != (at >= from && at <= to)) {
                    (offered ? offeredOutsideIt : withheldInsideIt).add(line + ":" + column);
                }
            }
            startOfLine += lines[line].length() + 1;
        }

        assertEquals(List.of(), withheldInsideIt,
                "the offer stands wherever the caret is in the declaration");
        assertEquals(List.of(), offeredOutsideIt, "and nowhere it is not");
    }

    /**
     * A comment above a declaration is not inside it.
     *
     * <p>The syntax tree covers every character, so the blank lines and the comment in front of a
     * declaration are part of its node. They are not part of what it writes: a caret there is where
     * the comment is being written, and the declaration below it is the next thing on the page
     * rather than the thing the caret is in.
     */
    @Test
    void aCommentAboveADeclarationIsNotInsideIt() {
        String noted = ONLY_EDGES.replace("behavior keep",
                "// what this keeps\nbehavior keep");
        Analyzer analyzer = measuring(Adequacy.Level.ALL);
        ModuleGraph graph = graphOf(Map.of(EDGES, noted));

        assertFalse(offersRows(analyzer, EDGES, noted, caret(7, 3), graph),
                "the caret is in the comment");
        assertTrue(offersRows(analyzer, EDGES, noted, caret(8, 3), graph),
                "and on the line under it, in the declaration");
    }

    /**
     * A selection meets the declaration where the two share a character.
     *
     * <p>A caret is a position and is inside the declaration at either end of it. A selection is a
     * stretch, and read the same way a selection of the blank line above would reach the
     * declaration below by touching its first character — which is the boundary the point range
     * got wrong at the other end.
     */
    @Test
    void aSelectionMeetsTheDeclarationWhereTheyShareACharacter() {
        Analyzer analyzer = measuring(Adequacy.Level.ALL);
        ModuleGraph graph = graphOf(Map.of(MODULE, TRIP));
        int endOfTheDeclaration = "    constructs Submitted, Waiting".length();

        assertFalse(offersRows(analyzer, MODULE, TRIP, over(8, 0, 9, 0), graph),
                "the blank line above, up to where the declaration starts");
        assertTrue(offersRows(analyzer, MODULE, TRIP, over(8, 0, 9, 1), graph),
                "and one character further, into it");
        assertFalse(offersRows(analyzer, MODULE, TRIP, over(10, endOfTheDeclaration, 12, 0), graph),
                "from where the declaration ends, down");
        assertTrue(offersRows(analyzer, MODULE, TRIP, over(10, endOfTheDeclaration - 1, 12, 0), graph),
                "and one character back, from inside it");
    }

    /** Whether the rows this behavior does not cover are on offer for {@code asked}. */
    private static boolean offersRows(Analyzer analyzer, String uri, String text, Range asked,
                                      ModuleGraph graph) {
        return analyzer.codeActions(uri, text, asked, graph).stream()
                .anyMatch(action -> action.title().startsWith("Write the rows"));
    }

    private static Range caret(int line, int column) {
        Position at = new Position(line, column);
        return new Range(at, at);
    }

    private static Range over(int line, int column, int toLine, int toColumn) {
        return new Range(new Position(line, column), new Position(toLine, toColumn));
    }

    /** And nothing is offered where nothing was asked to be measured. */
    @Test
    void anEditorThatDidNotAskIsOfferedNothing() {
        assertEquals(List.of(), new Analyzer()
                .codeActions(MODULE, TRIP, on(9), graphOf(Map.of(MODULE, TRIP))));
    }

    /**
     * A line whose value could not be read is left out of the ratio.
     *
     * <p>A lens is one number on one line and has nowhere to put a word beside it. The report writes
     * "undecided" next to the count and can afford to include such a line; drawn over a declaration
     * the same line reads as a row the author has not written, at a value nothing was able to look
     * at. So the ratio counts the lines that came to an answer, and the rest are absent rather than
     * counted as missing.
     *
     * <p>The row below hands the behavior more nodes than an observation keeps, so the value at
     * {@code cost} is truncated and the boundaries there are undecided.
     */
    @Test
    void aLineWhoseValueCouldNotBeReadIsNotInTheRatio() {
        // Computed rather than spelled: a row's operand is compiled as a method of the module, and
        // a literal this size is past what a JVM method holds — which would leave the module with
        // no classes at all, where what this measures is the observation's limit.
        String groups = "someGroups(64)";
        String unread = """
                module example.wide

                data Amount = Int
                    invariant value >= 0 && value <= 1000

                data Item = { a: String, b: String, c: String }
                data Group = { items: List<Item> }
                data Draft = { groups: List<Group>, cost: Amount }
                data Ok = { n: Int }

                behavior take : (request: Draft) -> Ok
                    constructs Ok

                let take (request) = Ok { n = request.cost.value }

                let someItems (n: Int): List<Item> =
                    List.map({ (i) -> Item { a = "x", b = "x", c = "x" } }, List.rangeInclusive(1, n))

                let someGroups (n: Int): List<Group> =
                    List.map({ (i) -> Group { items = someItems(64) } }, List.rangeInclusive(1, n))

                example take
                    | (Draft { groups = %s, cost = Amount(0) }) -> Ok { n = 0 }
                """.formatted(groups);

        List<CodeLens> lenses = measuring(Adequacy.Level.ALL)
                .codeLenses(MODULE, graphOf(Map.of(MODULE, unread)));

        assertEquals(1, lenses.size());
        assertFalse(lenses.get(0).title().contains("boundary"),
                "the invariant draws two lines and neither was decided: " + lenses.get(0).title());
    }
}
