package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;

import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.diag.msg.ExampleMessage;
import org.junit.jupiter.api.Test;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.Located;
import souther.compiler.diag.Note;
import souther.compiler.observe.WaitShown;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code fake}'s table is built because it is written, not because something reads it.
 *
 * <p>A table is read to stand in for a behavior while a row of something that depends on it runs, and
 * read again against the rows recorded for the behavior itself. A module can write one that neither
 * of those reaches — nothing depends on the faked behavior, and no row records what it owes — and
 * what is wrong with such a table was said nowhere: the reading built it into a list nobody read, and
 * nothing else built it at all.
 */
class CompileFakeTableWhereWrittenTest {

    /** A behavior nothing depends on, with rows of its own: the reading is the only other place its
     * table is built, and it does not report. */
    private static final String UNREAD = """
            module example.written

            data N = Int
            data Found = { n: N }
            data Missing = { why: String }

            behavior find : (n: N) -> Found | Missing
                constructs Found

            let find (n) = Found { n = n }

            example find
                | "one" : (N(1)) -> Found { n = N(1) }
            """;

    /** An injected dependency and a behavior whose rows run against a stand-in for it. */
    private static final String INJECTED = """
            module example.used

            data N = Int
            data Found = { n: N }
            data Missing = { why: String }
            data Ok = { n: N }

            behavior find : (n: N) -> Found | Missing

            behavior use : (n: N) -> Ok | Missing
                depends on find
                constructs Ok, Missing

            let use (n, find) = match find(n) with
                | Found   -> Ok { n = n }
                | Missing -> Missing { why = "none" }
            """;

    @Test
    void aTableNoRowReadsIsStillBuilt() {
        // `N` is a newtype over Int, so `N("x")` is a row that will not build. Nothing depends on
        // `find`, so no row ever stands in with this table; it says what it says all the same.
        String source = UNREAD + """

                fake find
                    | (N("x")) -> Found { n = N(1) }
                """;
        Diagnostic one = only(source);

        assertEquals("E1317", one.code());
        assertEquals(16, WhereItSits.in(source,
                ((Primary.InSource) one.primary()).place().region()).start().line(), "at the row that states no value");
    }

    @Test
    void aTableNothingAtAllReadsIsStillBuilt() {
        // No rows recorded for `find` either, so not even the reading builds this one.
        Diagnostic one = only("""
                module example.alone

                data N = Int
                data Found = { n: N }
                data Missing = { why: String }

                behavior find : (n: N) -> Found | Missing
                    constructs Found

                let find (n) = Found { n = n }

                fake find
                    | (N("x")) -> Found { n = N(1) }
                """);

        assertEquals("E1317", one.code());
    }

    @Test
    void aRowOfTheWrongArityIsSaidWithoutARowReadingTheTable() {
        String source = UNREAD + """

                fake find
                    | (N(1), N(2)) -> Found { n = N(1) }
                """;
        Diagnostic one = only(source);

        assertEquals("E1908", one.code());
        assertInstanceOf(ExampleMessage.TheFakeCouldNotBeBuilt.class, one.said());
        assertEquals(16, WhereItSits.in(source,
                ((Primary.InSource) one.primary()).place().region()).start().line(), "at the row whose input count is wrong");
    }

    /**
     * A second table written for one dependency answers for nothing, but what it writes is still
     * this module's code: every operand a row writes is compiled with the module's definitions, so a
     * row that states no value is refused wherever it is written — reachability decides what a table
     * answers, not what its rows are held to.
     */
    @Test
    void aSecondTablesRowIsStillHeldToItsTypes() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(UNREAD + """

                fake find
                    | (N(1)) -> Found { n = N(1) }

                fake find
                    | (N("x")) -> Found { n = N(1) }
                """));
        assertEquals(List.of("E1317"), codesOf(e), e.getMessage());
    }

    /** And a table that builds is said nowhere: the check is not one every fake fails. */
    @Test
    void aTableThatBuildsIsSaidNowhere() {
        assertDoesNotThrow(() -> Compiler.compile(UNREAD + """

                fake find
                    | (N(1)) -> Found { n = N(1) }
                """));
    }

    /**
     * One table, one report, however many rows read it.
     *
     * <p>A row that applies a fake it could not build does not run, and what is wrong with the table
     * is not the row's to say: each row reaching it would otherwise repeat the one thing wrong with
     * the one table they all read.
     */
    @Test
    void oneTableIsSaidOnceHoweverManyRowsReadIt() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(INJECTED + """

                example use
                    | "one" : (N(1)) -> Ok { n = N(1) }
                    | "two" : (N(2)) -> Ok { n = N(2) }

                fake find
                    | (N("x")) -> Found { n = N(1) }
                """));

        assertEquals(List.of("E1317"), codesOf(e), e.getMessage());
    }

    /** A fake written in an attached file is built for that file, and said in it. */
    @Test
    void aFakeInAnAttachedFileIsSaidInThatFile() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(INJECTED + """

                        example use
                            | "one" : (N(1)) -> Ok { n = N(1) }
                        """, """
                        examples for example.used

                        fake find
                            | (N("x")) -> Found { n = N(1) }
                        """)));

        assertEquals(List.of("E1317"), codesOf(e), e.getMessage());
        assertEquals(new SourceId("1"), e.sourceIdOf(0), "the file that wrote the fake is the file it is said in");
    }

    /**
     * A table whose fixture loops spends the budget the policy allows, and is reported for that.
     *
     * <p>Said as a fact rather than by writing something slow and waiting: the helper the table
     * applies goes round forever, the budget is small, and what comes back is the same on every host.
     * That is what the counting is for, and it is why this test needs no deadline of its own.
     */
    @Test
    void aTableWhoseFixtureLoopsIsReportedForSpendingItsSteps() {
        List<Located> warnings = new ArrayList<>();
        assertDoesNotThrow(() -> Compiler.compiled("""
                module example.slow

                data N = Int
                data Found = { n: N }
                data Missing = { why: String }

                partial let spin (n: Int): Int = spin(n)

                behavior find : (n: N) -> Found | Missing
                    constructs Found

                let find (n) = Found { n = n }

                fake find
                    | (N(spin(1))) -> Found { n = N(1) }
                """, "Main", warnings, souther.compiler.query.Adequacy.Asked.NOTHING,
                EvaluationPolicy.of(10_000L)));

        List<Located> said = only("E1921", warnings);
        assertEquals(1, said.size(), warnings.toString());
        Diagnostic one = said.get(0).diagnostic();
        assertTrue(rendered(one).contains("Building the `fake find` table spent its budget of"
                        + " 10000 steps"),
                rendered(one));
    }

    /**
     * A table that did not finish being built says so, and says nothing about being wrong: waiting
     * tells the two apart in neither direction. Nothing reads this one — no row depends on `find` and
     * none is recorded for it — so the comparison the reading would report on (E1920) never arises,
     * and this is the only thing said about the table.
     */
    @Test
    void aTableThatDidNotFinishBuildingSaysSoAtTheFake() {
        List<Located> warnings = new ArrayList<>();
        String source = """
                module example.slow

                data N = Int
                data Found = { n: N }
                data Missing = { why: String }

                partial let spin (n: Int): Int = spin(n)

                behavior find : (n: N) -> Found | Missing
                    constructs Found

                let find (n) = Found { n = n }

                fake find
                    | (N(spin(1))) -> Found { n = N(1) }
                """;
        assertDoesNotThrow(() -> Compiler.compiled(source, "Main", warnings, souther.compiler.query.Adequacy.Asked.NOTHING,
                DoesNotComeBack.WAIT,
                DoesNotComeBack.overrunningOn(DoesNotComeBack.everyTableOf("find"))));

        List<Located> said = only("E1921", warnings);
        assertEquals(1, said.size(), warnings.toString());
        assertEquals(List.of(), only("E1920", warnings),
                "nothing records what `find` owes, so no comparison was missed");
        Diagnostic one = said.get(0).diagnostic();
        assertEquals(14, WhereItSits.in(source, ((Primary.InSource) one.primary()).place().region()).start().line(), "at the fake");
        assertEquals(6, WhereItSits.in(source, ((Primary.InSource) one.primary()).place().region()).start().column(), "on the behavior it names");
        // What it says, as the values it says it about. Asked of the rendered line it would be
        // asked of the catalog's English as well, and a sentence reworded is not this reading
        // changing.
        assertEquals(new ExampleMessage.TheTableDidNotAnswer("find",
                        WaitShown.of(DoesNotComeBack.WAIT)),
                one.said(), "the fake it is about, and the wait this compile was given");
        assertEquals(List.of(new ExampleMessage.TheTableNotAnsweringIsNotTheTableBeingWrong("find")),
                one.notes().stream().map(Note::said).toList(),
                "and the hint that says whose fault it is not");
    }

    /** The one diagnostic of a compile that has one. */
    private static Diagnostic only(String model) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(model));
        assertEquals(1, e.diagnostics().size(), "one table, one diagnostic: " + e.getMessage());
        return e.diagnostics().get(0);
    }

    private static List<String> codesOf(CompileException e) {
        List<String> codes = new ArrayList<>();
        for (Diagnostic d : e.diagnostics()) {
            codes.add(d.code());
        }
        return codes;
    }

    private static List<Located> only(String code, List<Located> warnings) {
        List<Located> found = new ArrayList<>();
        for (Located w : warnings) {
            if (code.equals(w.diagnostic().code())) {
                found.add(w);
            }
        }
        return found;
    }

    private static String rendered(Diagnostic d) {
        return new HumanRenderer(false).render(d, null, Locale.ENGLISH);
    }
}
