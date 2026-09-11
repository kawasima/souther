package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Located;
import souther.compiler.meta.ModulePath;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.RowOutcome;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code fake}'s table dispatches: the first explicit row stating the arguments it is asked, and
 * otherwise the {@code _} row. A row the dispatch can never return is a statement its author is
 * making and nothing reads, and the table said nothing about it.
 *
 * <p>Two shapes of it, pointing opposite ways. Of two explicit rows stating one input the first
 * answers, so the second is dead; of two {@code _} rows the last is what a table falls through to,
 * so the earlier ones are. Which of a pair is dead is the dispatch's to say, which is why the report
 * quotes the row that answers rather than leaving it to be read off the order — and why what decides
 * it here is the dispatch itself rather than a second reading of "these arguments are those".
 *
 * <p>An explicit row and a {@code _} row do not shadow each other in either order, which is the
 * measurement that tells a check that asks the dispatch from one that asks whether anything answered
 * before: a {@code _} written above an explicit row answers where that row is absent, not where it
 * is.
 */
class ARowAFakeCannotAnswerWithIsRefusedTest {

    private static final String BASE = """
            module example.members

            data MemberId = String
            data Found = { id: MemberId }
            data Missing = { why: String }

            data Order = { by: MemberId }
            data Placed = { by: MemberId }
            data Refused = { why: String }

            behavior findMember : (id: MemberId) -> Found | Missing

            behavior place : (order: Order) -> Placed | Refused
                depends on findMember
                constructs Placed, Refused

            let place (order, findMember) = match findMember(order.by) with
                | Found    -> Placed { by = order.by }
                | Missing as m -> Refused { why = m.why }
            """;

    /** The same, with an invariant on the id, so that a row's answer and a row's arguments fail to
     * build for reasons a report tells apart. */
    private static final String BASE_WITH_AN_INVARIANT = BASE
            .replace("module example.members", "module example.members\n\nimport String ( length )")
            .replace("data MemberId = String",
                    "data MemberId = String\n    invariant length(value) > 0");

    /** A model asking the fake for {@code m-1}, with {@code table} standing in and {@code expected}
     * what the row says the behavior answers. */
    private static String model(String table, String expected) {
        return BASE + "\n" + table + """

                example place
                    | "what the table answers" : (Order { by = MemberId("m-1") })
                        ->""" + expected + "\n";
    }

    private static List<Diagnostic> diagnosticsOf(String source) {
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        List<Diagnostic> out = new ArrayList<>();
        compilation.diagnostics().forEach((id, found) -> out.addAll(Located.diagnosticsOf(found)));
        return out;
    }

    /** The ones saying a row of a table can never answer. */
    private static List<Diagnostic> unanswerable(String source) {
        return diagnosticsOf(source).stream().filter(d -> "E1926".equals(d.code())).toList();
    }

    /** The rows of a model that compiles as far as running them. */
    private static List<RowOutcome> rowsOf(String source) {
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        List<RowOutcome> rows = new ArrayList<>();
        for (String module : compilation.modules()) {
            for (SourceId id : compilation.exampleSourcesOf(module)) {
                Output.Examples.Of ran = compilation.db()
                        .ask(Output.Examples.asked(compilation.db(), module, id)).value();
                if (ran != null) {
                    rows.addAll(ran.rows());
                }
            }
        }
        return rows;
    }

    /** The 1-based line {@code needle} is written on. */
    private static int lineOf(String source, String needle) {
        List<String> lines = source.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i + 1;
            }
        }
        throw new AssertionError("not written in this source: " + needle);
    }

    // --- what is refused -----------------------------------------------------------------------

    @Test
    void theSecondOfTwoRowsStatingOneInputIsReportedAtItself() {
        String source = model("""
                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                    | (MemberId("m-1")) -> Missing { why = "second" }
                """, "Placed { by = MemberId(\"m-1\") }");

        List<Diagnostic> said = unanswerable(source);

        assertEquals(1, said.size(), "one row of the two answers nothing");
        assertEquals(lineOf(source, "\"second\""), WhereItSits.in(source, ((Primary.InSource) said.get(0).primary()).place().region()).start().line(),
                "and it is the later one, since the first match is what answers");
        assertEquals(lineOf(source, "Found { id = MemberId(\"m-1\") }"),
                WhereItSits.in(source, ((souther.compiler.diag.DiagnosticPlace.InSource) said.get(0).secondary().get(0).place()).region()).start().line(),
                "the row that answers instead is quoted");
    }

    @Test
    void theEarlierOfTwoDefaultRowsIsReportedAtItself() {
        String source = model("""
                fake findMember
                    | _ -> Missing { why = "first" }
                    | _ -> Missing { why = "second" }
                """, "Refused { why = \"second\" }");

        List<Diagnostic> said = unanswerable(source);

        assertEquals(1, said.size(), "one of the two `_` rows answers nothing");
        assertEquals(lineOf(source, "\"first\""), WhereItSits.in(source, ((Primary.InSource) said.get(0).primary()).place().region()).start().line(),
                "and it is the earlier one, since a table falls through to the last `_`");
        assertEquals(lineOf(source, "\"second\""),
                WhereItSits.in(source, ((souther.compiler.diag.DiagnosticPlace.InSource) said.get(0).secondary().get(0).place()).region()).start().line(),
                "the `_` that answers is quoted");
    }

    @Test
    void everyDefaultRowButTheLastIsReported() {
        String source = model("""
                fake findMember
                    | _ -> Missing { why = "first" }
                    | _ -> Missing { why = "second" }
                    | _ -> Missing { why = "third" }
                """, "Refused { why = \"third\" }");

        List<Diagnostic> said = unanswerable(source);

        assertEquals(List.of(lineOf(source, "\"first\""), lineOf(source, "\"second\"")),
                said.stream().map(d -> WhereItSits.in(source, ((Primary.InSource) d.primary()).place().region()).start().line()).sorted().toList(),
                "two rows answer nothing, and each is said at itself");
        assertTrue(said.stream().allMatch(d -> WhereItSits.in(source, ((souther.compiler.diag.DiagnosticPlace.InSource) d.secondary().get(0).place()).region()).start().line()
                        == lineOf(source, "\"third\"")),
                "and both name the one that answers, rather than the row written after them");
    }

    @Test
    void aRowThatCanNeverAnswerRefusesTheCompile() {
        String source = model("""
                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                    | (MemberId("m-1")) -> Missing { why = "second" }
                """, "Placed { by = MemberId(\"m-1\") }");

        CompileException raised = assertThrows(CompileException.class, () -> Compiler.compile(source));

        assertEquals("E1926", raised.diagnostic().code(),
                "which row answers is decided by the table's rule, so this is refused rather than measured");
    }

    /**
     * Nothing of an unreachable row is built, so nothing about what it answers can be reported in
     * place of the row being unreachable. A dead row whose output breaks the invariant it is built
     * against would otherwise be the table refused for a row that is not part of it (E1908), and a
     * dead row whose output is slow would be a warning that the table could not be read (E1921) —
     * a rule that decides what compiles losing to one that decides what a build is told.
     */
    @Test
    void whatAnUnreachableRowAnswersIsNotBuilt() {
        // The output that fails is one that fails when it runs — an invariant its construction
        // breaks — because a spelling the language refuses statically is refused wherever it is
        // written, reachable or not, and would answer this question with the wrong rule.
        String source = BASE_WITH_AN_INVARIANT + """

                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                    | (MemberId("m-1")) -> Found { id = MemberId("") }

                example place
                    | "what the table answers" : (Order { by = MemberId("m-1") })
                        -> Placed { by = MemberId("m-1") }
                """;

        List<Diagnostic> said = diagnosticsOf(source);

        assertEquals(List.of("E1926"), said.stream().map(Diagnostic::code).toList(),
                "the row is unreachable, and what it would have answered was never asked for");

        String reachable = BASE_WITH_AN_INVARIANT + """

                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("") }

                example place
                    | "what the table answers" : (Order { by = MemberId("m-1") })
                        -> Refused { why = "" }
                """;

        assertTrue(diagnosticsOf(reachable).stream().anyMatch(d -> "E1908".equals(d.code())),
                "and the same output in a row the table can reach is the error it is: "
                        + diagnosticsOf(reachable));
    }

    /**
     * A row is read as far as it is reached, in the order the rows are written. Reading every row's
     * arguments first and every answer after would not build what an unreachable row answers either,
     * and would move the fault rather than remove it: a row whose answer is wrong would be reported
     * or not depending on what a row below it does with its arguments.
     */
    @Test
    void aRowIsReadWhereItIsWritten() {
        String source = BASE_WITH_AN_INVARIANT + """

                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("") }
                    | (MemberId(String.slice(0, 99, "in"))) -> Missing { why = "b" }

                example place
                    | "one" : (Order { by = MemberId("m-1") }) -> Refused { why = "" }
                """;

        List<Diagnostic> said = diagnosticsOf(source);

        assertEquals(1, said.size(), "the first row that cannot be read is what the table is refused for");
        assertTrue(String.valueOf(said.get(0).values().get("why"))
                        .contains("invariant violated on example.members.MemberId"),
                "which is the answer of the row written first — the invariant its construction"
                        + " breaks — not the arguments of the one below it: " + said);
    }

    // --- what is not ---------------------------------------------------------------------------

    @Test
    void aDefaultRowAndAnExplicitRowDoNotShadowEachOtherInEitherOrder() {
        String defaultFirst = model("""
                fake findMember
                    | _ -> Missing { why = "fallback" }
                    | (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                """, "Placed { by = MemberId(\"m-1\") }");
        String defaultLast = model("""
                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                    | _ -> Missing { why = "fallback" }
                """, "Placed { by = MemberId(\"m-1\") }");

        assertEquals(List.of(), unanswerable(defaultFirst),
                "the `_` answers where the explicit row is not asked for, so neither is dead");
        assertEquals(List.of(), unanswerable(defaultLast), "and the order does not decide it");
        assertEquals(Disposition.HELD, onlyRow(defaultFirst).disposition(),
                "the explicit row is what answers, `_` above it or not");
    }

    @Test
    void rowsStatingDifferentInputsAreNotReported() {
        String source = model("""
                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                    | (MemberId("m-9")) -> Missing { why = "nobody" }
                    | _ -> Missing { why = "fallback" }
                """, "Placed { by = MemberId(\"m-1\") }");

        assertEquals(List.of(), unanswerable(source));
        assertEquals(Disposition.HELD, onlyRow(source).disposition());
    }

    /**
     * What the table answers is unchanged. The rows it cannot dispatch to are held apart from the
     * ones it can, and holding them apart is not a change to which of them answers.
     */
    @Test
    void theRowThatAnsweredBeforeStillAnswers() {
        String shadowing = model("""
                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                    | (MemberId("m-1")) -> Missing { why = "second" }
                """, "Placed { by = MemberId(\"m-1\") }");
        String defaults = model("""
                fake findMember
                    | _ -> Missing { why = "first" }
                    | _ -> Missing { why = "second" }
                """, "Refused { why = \"second\" }");

        assertEquals(Disposition.HELD, onlyRow(shadowing).disposition(),
                "the first of two explicit rows is still what the fake answers with");
        assertEquals(Disposition.HELD, onlyRow(defaults).disposition(),
                "and the last of two `_` rows is still what it falls through to");
    }

    private static RowOutcome onlyRow(String source) {
        List<RowOutcome> rows = rowsOf(source);
        assertEquals(1, rows.size(), "the model writes one row");
        return assertInstanceOf(RowOutcome.class, rows.get(0));
    }
}
