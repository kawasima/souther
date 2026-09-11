package souther.compiler.query;

import souther.compiler.WhereItSits;
import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;

import souther.compiler.Compiler;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.DiagnosticRenderer;
import souther.compiler.diag.Located;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.msg.InvariantMessage;
import souther.compiler.meta.ModulePath;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A compilation files every error it finds, and the exception it fails with carries all of them.
 *
 * <p>Handing back one of several sends the author to fix that one and compile again to be told the
 * next, which the compiler had already worked out. What a caller reads off the exception —
 * {@link CompileException#code()}, its message — answers from the first of them, and which one that
 * is comes from where the errors are rather than from the order the questions were answered in.
 */
class ACompileAnswersWithEveryErrorItFoundTest {

    /** Three behaviors, each short of exactly one `constructs` entry — the case its line holds.
     *  One name each, so what is counted here is the behaviors and not the names within one:
     *  a clause short of several is its own question (E1002 reports each of them). */
    private static final String THREE_UNDER_DECLARED = """
            module m.a exposing ( Ticket, CookLine, ItemCookState, Pending, InProgress, Done, start, finish, reopen )

            data Pending = { at: Int }
            data InProgress = { since: Int }
            data Done = { at: Int }
            data ItemCookState = Pending | InProgress | Done
            data CookLine = { state: ItemCookState }
            data Ticket = { lines: List<CookLine> }

            behavior start : (t: Ticket) -> Ticket
                constructs Ticket, CookLine
            let start (t) = Ticket { lines = [ CookLine { state = InProgress { since = 0 } } ] }

            behavior finish : (t: Ticket) -> Ticket
                constructs Ticket, CookLine
            let finish (t) = Ticket { lines = [ CookLine { state = Done { at = 0 } } ] }

            behavior reopen : (t: Ticket) -> Ticket
                constructs Ticket, CookLine
            let reopen (t) = Ticket { lines = [ CookLine { state = Pending { at = 0 } } ] }
            """;

    private static CompileException failure(String source) {
        return assertThrows(CompileException.class, () -> Compiler.compile(source));
    }

    /** Read off {@link CompileException#locatedDiagnostics()}, which is what a renderer walks. */
    private static List<String> bodiesOf(CompileException e) {
        List<String> said = new ArrayList<>();
        for (Located located : e.locatedDiagnostics()) {
            said.add(DiagnosticRenderer.legacyBody(located.diagnostic()));
        }
        return said;
    }

    @Test
    void everyBehaviorsErrorIsCarried() {
        CompileException e = failure(THREE_UNDER_DECLARED);

        List<String> said = bodiesOf(e);
        assertEquals(3, said.size(), "one per behavior that under-declares: " + said);
        assertTrue(said.get(0).contains("`start`"), said.toString());
        assertTrue(said.get(1).contains("`finish`"), said.toString());
        assertTrue(said.get(2).contains("`reopen`"), said.toString());
    }

    @Test
    void theFirstErrorLeads() {
        CompileException e = failure(THREE_UNDER_DECLARED);

        assertEquals("E1002", e.code(), "the code is the first diagnostic's");
        assertTrue(e.getMessage().contains("`start`"),
                "the message is the first diagnostic's: " + e.getMessage());
        assertEquals(e.diagnostic(), e.diagnostics().get(0),
                "and the first diagnostic is the one the list leads with");
    }

    @Test
    void theyComeInTheOrderTheyAreWritten() {
        CompileException e = failure(THREE_UNDER_DECLARED);

        List<Integer> lines = new ArrayList<>();
        for (Diagnostic d : e.diagnostics()) {
            lines.add(WhereItSits.in(THREE_UNDER_DECLARED,
                    ((Primary.InSource) d.primary()).place().region()).start().line());
        }
        List<Integer> ascending = new ArrayList<>(lines);
        ascending.sort(Integer::compareTo);
        assertEquals(ascending, lines, "a reader takes them down the file: " + lines);
    }

    @Test
    void everyErrorIsTaggedWithTheSourceItIsIn() {
        CompileException e = failure(THREE_UNDER_DECLARED);

        for (int i = 0; i < e.diagnostics().size(); i++) {
            assertEquals(e.sourceIdOf(0), e.sourceIdOf(i),
                    "one source here, so every diagnostic names it");
        }
    }

    /** A source that compiles, so the compilation the ordering is asked of has one to name. */
    private static final String ONE_SOURCE = """
            module m.c exposing ( A, f )

            data A = { n: Int }

            behavior f : (a: A) -> Int
            let f (a) = a.n
            """;

    private static Db.Found errorAt(int line, int column, String says) {
        return errorIn(new SourceId("a.sou"), line, column, says);
    }

    private static Db.Found errorIn(SourceId sourceId, int line, int column, String says) {
        return new Db.Found("m.c", sourceId,
                Report.of(Diagnostic.literal(new SourcePos(line, column, sourceId), says)));
    }

    /** A warning, which is not what a compile fails with however many of them there are. */
    private static Db.Found warningAt(int line, String says) {
        return new Db.Found("m.c", new SourceId("a.sou"), Report.of(Diagnostic
                .say(new InvariantMessage.NothingKnownHereEstablishesTheInvariant(says))
                .at(new SourcePos(line, 1, new SourceId("a.sou"))).build()));
    }

    private static Compilation ofOneSource() {
        return Compilation.ofDocuments(java.util.Map.of("a.sou", ONE_SOURCE), java.util.Set.of(),
                ModulePath.EMPTY);
    }

    /** Two sources handed over as a list, so each has the index its order in that list gives it. */
    private static Compilation ofTwoSources() {
        return Compilation.ofSources(List.of(ONE_SOURCE, ONE_SOURCE), ModulePath.EMPTY);
    }

    @Test
    void theyAreOrderedByWhereTheyAreNotByWhenTheyWereFound() {
        CompileException e = ofOneSource().failure(List.of(
                errorAt(9, 1, "third"),
                errorAt(3, 1, "first"),
                errorAt(5, 7, "second")));

        assertEquals(List.of("first", "second", "third"), bodiesOf(e),
                "which question was answered first is not where the author looks");
    }

    @Test
    void aColumnBreaksATieOnALine() {
        CompileException e = ofOneSource().failure(List.of(
                errorAt(4, 20, "later"),
                errorAt(4, 3, "earlier")));

        assertEquals(List.of("earlier", "later"), bodiesOf(e));
    }

    @Test
    void twoAtOnePositionKeepTheOrderTheyWereFoundIn() {
        CompileException e = ofOneSource().failure(List.of(
                errorAt(6, 1, "said first"),
                errorAt(6, 1, "said second"),
                errorAt(6, 1, "said third")));

        assertEquals(List.of("said first", "said second", "said third"), bodiesOf(e),
                "a check reporting each of its own violations puts them all at one declaration");
    }

    @Test
    void aSourceGivenFirstLeadsHoweverFarDownItsErrorIs() {
        CompileException e = ofTwoSources().failure(List.of(
                errorIn(Compilation.idOfSourceIndex(1), 1, 1, "at the top of the second"),
                errorIn(Compilation.idOfSourceIndex(0), 20, 1, "far down the first")));

        assertEquals(List.of("far down the first", "at the top of the second"), bodiesOf(e),
                "a file is read through before the next one is opened");
    }

    @Test
    void nothingAmongThemIsNotAnError() {
        assertEquals(null, ofOneSource().failure(List.of()));
        assertEquals(null, ofOneSource().failure(List.of(warningAt(3, "A"), warningAt(5, "B"))),
                "a warning is not a reason to fail");
    }

    @Test
    void aCleanCompileStillCompiles() {
        Compiler.compile("""
                module m.b exposing ( Ticket, CookLine, ItemCookState, Pending, InProgress, Done, start )

                data ItemCookState = Pending | InProgress | Done
                data CookLine = { state: ItemCookState }
                data Ticket = { lines: List<CookLine> }

                behavior start : (t: Ticket) -> Ticket
                    constructs Ticket, CookLine
                let start (t) = Ticket { lines = [ CookLine { state = InProgress } ] }
                """);
    }
}
