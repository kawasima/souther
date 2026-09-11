package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;

import souther.compiler.diag.msg.InvariantMessage;
import souther.compiler.diag.Located;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Region;
import souther.compiler.diag.Diagnostic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An invariant may not construct a data (spec §invariant-expressions), and it is told so however the
 * construction is spelled and however far from the clause it is written.
 *
 * <p>A newtype is constructed two ways — {@code Yen(0)} and {@code Yen { value = 0 }} — and one of
 * them is rewritten into the other before anything reads it. An invariant used to be the one place
 * that rewrite did not reach, so the call spelling survived into the check as a call to a name that
 * is not a behavior, and the author was told to implement `Yen` from Java. Both spellings are one
 * construction here.
 *
 * <p>The clause is what carries the rule, so the clause is what the diagnostic names: the
 * construction is where the error is pointed, and the clause that reaches it is labelled beside it.
 */
class CompileConstructionInInvariantTest {

    private static CompileException err(String src) {
        return assertThrows(CompileException.class, () -> Compiler.compile(src));
    }

    // --- one construction, four spellings and placements -------------------------------------

    @Test
    void theCallSpellingWrittenInTheClause() {
        CompileException e = err("""
                module m
                data Yen = Int invariant value >= 0
                data Table = List<Int>
                    invariant ok = List.all(x -> Yen(0).value <= x, value)
                """);
        assertInstanceOf(InvariantMessage.TheNamedClauseConstructsAData.class, e.diagnostic().said());
        assertEquals(4, ((Primary.InSource) e.diagnostic().primary()).place().region().start().line());
    }

    @Test
    void theRecordSpellingWrittenInTheClause() {
        CompileException e = err("""
                module m
                data Yen = Int invariant value >= 0
                data Table = List<Int>
                    invariant ok = List.all(x -> Yen { value = 0 }.value <= x, value)
                """);
        assertInstanceOf(InvariantMessage.TheNamedClauseConstructsAData.class, e.diagnostic().said());
        assertEquals(4, ((Primary.InSource) e.diagnostic().primary()).place().region().start().line());
    }

    @Test
    void theCallSpellingWrittenInAHelperTheClauseNames() {
        CompileException e = err("""
                module m
                data Yen = Int invariant value >= 0
                let atLeastZero (x: Int): Bool = Yen(0).value <= x
                data Table = Int
                    invariant ok = atLeastZero(value)
                """);
        assertInstanceOf(InvariantMessage.TheNamedClauseConstructsAData.class, e.diagnostic().said());
        assertEquals(3, ((Primary.InSource) e.diagnostic().primary()).place().region().start().line(),
                "the error is at the construction, which is in the helper");
    }

    @Test
    void theRecordSpellingWrittenInAHelperTheClauseNames() {
        CompileException e = err("""
                module m
                data Yen = Int invariant value >= 0
                let atLeastZero (x: Int): Bool = Yen { value = 0 }.value <= x
                data Table = Int
                    invariant ok = atLeastZero(value)
                """);
        assertInstanceOf(InvariantMessage.TheNamedClauseConstructsAData.class, e.diagnostic().said());
        assertEquals(3, ((Primary.InSource) e.diagnostic().primary()).place().region().start().line());
    }

    /** However many helpers away: the clause arrives at the check with all of them expanded into it,
     *  so the error is at the construction and not at the first helper the clause names. */
    @Test
    void theConstructionIsFoundThroughAChainOfHelpers() {
        CompileException e = err("""
                module m
                data Yen = Int invariant value >= 0
                let inner (x: Int): Bool = Yen(0).value <= x
                let outer (x: Int): Bool = inner(x)
                data Table = Int
                    invariant ok = outer(value)
                """);
        assertInstanceOf(InvariantMessage.TheNamedClauseConstructsAData.class, e.diagnostic().said());
        assertEquals(3, ((Primary.InSource) e.diagnostic().primary()).place().region().start().line(),
                "`inner` holds the construction; `outer` only passes through");
    }

    /** A helper another module published is substituted into the clause the same way, and arrives
     *  already rewritten because that module lowered its own bodies. */
    @Test
    void theConstructionIsFoundInAnImportedHelper() {
        Map<String, String> sources = Map.of(
                "up", """
                        module up exposing ( Yen, atLeastZero )
                        data Yen = Int invariant value >= 0
                        let atLeastZero (x: Int): Bool = Yen(0).value <= x
                        """,
                "down", """
                        module down
                        import up ( atLeastZero )
                        data Table = List<Int>
                            invariant ok = List.all(x -> atLeastZero(x), value)
                        """);
        List<Diagnostic> down = Located.diagnosticsOf(Compiler.diagnoseModules(sources)).get(new SourceId("down"));
        assertEquals(1, down.size(), () -> "expected one diagnostic, got " + down);
        assertInstanceOf(InvariantMessage.TheNamedClauseConstructsAData.class, down.get(0).said());
    }

    // --- what the diagnostic says ------------------------------------------------------------

    @Test
    void aNamedClauseIsNamed() {
        CompileException e = err("""
                module m
                data Yen = Int invariant value >= 0
                let atLeastZero (x: Int): Bool = Yen(0).value <= x
                data Table = List<Int>
                    invariant ok = List.all(x -> atLeastZero(x), value)
                """);
        assertEquals(List.of("Table", "Yen", "ok"), List.copyOf(e.diagnostic().values().values()));
    }

    @Test
    void anUnnamedClauseNamesOnlyTheDeclaration() {
        CompileException e = err("""
                module m
                data Yen = Int invariant value >= 0
                let atLeastZero (x: Int): Bool = Yen(0).value <= x
                data Table = List<Int>
                    invariant List.all(x -> atLeastZero(x), value)
                """);
        assertInstanceOf(InvariantMessage.TheInvariantConstructsAData.class, e.diagnostic().said());
    }

    /**
     * The construction and the clause that reaches it are two places, so the diagnostic carries
     * both: the error where the data is built, the clause labelled where the rule is written.
     *
     * <p>The label covers the clause, which the clause has held since it was parsed. A marker at the
     * one point the clause is anchored at leaves a reader looking for what about that line the
     * report means, on a line the report is about the whole of.
     */
    @Test
    void theClauseIsLabelledWhereItIsWritten() {
        String source = """
                module m
                data Yen = Int invariant value >= 0
                let atLeastZero (x: Int): Bool = Yen(0).value <= x
                data Table = Int
                    invariant ok = atLeastZero(value)
                """;
        CompileException e = err(source);
        assertEquals(1, e.diagnostic().secondary().size());
        assertInstanceOf(InvariantMessage.TheClauseReachesThatConstruction.class,
                e.diagnostic().secondary().get(0).said());

        Region marked = ((souther.compiler.diag.DiagnosticPlace.InSource) e.diagnostic().secondary().get(0).place()).region();
        assertEquals(5, marked.start().line());
        assertEquals("invariant ok = atLeastZero(value)",
                source.split("\n", -1)[marked.start().line() - 1]
                        .substring(marked.start().column() - 1, marked.end().column() - 1),
                "the clause as it was written, and not the point it is anchored at");
    }

    /**
     * A combinator between the clause and the construction changes nothing about where the error is
     * put. Expanding a prelude helper stamps the call site on the prelude''s own body, so a diagnostic
     * from there points at the user''s call rather than at the shipped source of {@code souther.*};
     * what the caller handed it keeps the positions the author wrote it at.
     */
    @Test
    void aConstructionReachedThroughACombinatorIsPointedAtTheConstruction() {
        CompileException e = err("""
                module m
                data Yen = Int invariant value >= 0
                let atLeastZero (x: Int): Bool = Yen(0).value <= x
                data Table = List<Int>
                    invariant ok = List.all(x -> atLeastZero(x), value)
                """);
        assertInstanceOf(InvariantMessage.TheNamedClauseConstructsAData.class, e.diagnostic().said());
        assertEquals(3, ((Primary.InSource) e.diagnostic().primary()).place().region().start().line());
        assertEquals(34, ((Primary.InSource) e.diagnostic().primary()).place().region().start().column(),
                "the construction in the helper, not the combinator the clause calls");
    }

    /** With no helper anywhere, the lambda handed to the combinator is caller code all the way down.
     *  It is written on a line of its own, so a call site stamped over it shows up in the line and
     *  not only in the column. */
    @Test
    void aConstructionWrittenInTheLambdaGivenToACombinatorKeepsItsPlace() {
        CompileException e = err("""
                module m
                data Yen = Int invariant value >= 0
                data Table = List<Int>
                    invariant ok = List.all(
                        x -> Yen(0).value <= x,
                        value)
                """);
        assertInstanceOf(InvariantMessage.TheNamedClauseConstructsAData.class, e.diagnostic().said());
        assertEquals(5, ((Primary.InSource) e.diagnostic().primary()).place().region().start().line());
        assertEquals(14, ((Primary.InSource) e.diagnostic().primary()).place().region().start().column());
    }

    /**
     * And written in a clause that runs over several lines, where the two are as much one place as
     * they are when the whole clause is written on one.
     *
     * <p>What decides this is the stretch the clause covers. A clause the author broke over three
     * lines holds everything written inside it, and a reader shown the construction and then sent to
     * the clause around it is being sent where they already are.
     */
    @Test
    void aConstructionWrittenInAClauseThatRunsOverSeveralLinesIsLabelledOnce() {
        CompileException e = err("""
                module m
                data Yen = Int invariant value >= 0
                data Table = List<Int>
                    invariant ok = List.all(
                        x -> Yen(0).value <= x,
                        value)
                """);
        assertTrue(e.diagnostic().secondary().isEmpty());
    }

    /** Written in the clause itself there is one place, and labelling it twice says nothing. */
    @Test
    void aConstructionWrittenInTheClauseIsLabelledOnce() {
        CompileException e = err("""
                module m
                data Yen = Int invariant value >= 0
                data Table = List<Int>
                    invariant ok = List.all(x -> Yen(0).value <= x, value)
                """);
        assertTrue(e.diagnostic().secondary().isEmpty());
    }

    /** A clause is a unit of its own — it is what an attempt answers by name and what discharge
     *  answers about — so a declaration with two wrong clauses says so about both. */
    @Test
    void everyWrongClauseIsReported() {
        Map<SourceId, List<Diagnostic>> found = Located.diagnosticsOf(Compiler.diagnoseModules(Map.of("m", """
                module m
                data Yen = Int invariant value >= 0
                let atLeastZero (x: Int): Bool = Yen(0).value <= x
                data Table = List<Int>
                    invariant low = List.all(x -> atLeastZero(x), value)
                    invariant high = List.all(x -> atLeastZero(x), value)
                """)));
        List<String> clauses = found.get(new SourceId("m")).stream()
                .filter(d -> d.said() instanceof InvariantMessage.TheNamedClauseConstructsAData)
                .map(d -> String.valueOf(d.values().get("clause")))
                .toList();
        assertEquals(List.of("low", "high"), clauses);
    }

    /** Within one clause the first construction is the answer: naming every one of them tells the
     *  author nothing the first does not. */
    @Test
    void oneConstructionIsReportedPerClause() {
        List<Diagnostic> found = Located.diagnosticsOf(Compiler.diagnoseModules(Map.of("m", """
                module m
                data Yen = Int invariant value >= 0
                data Table = List<Int>
                    invariant ok = List.all(x -> Yen(0).value <= x, value) && Yen(1).value >= 0
                """))).get(new SourceId("m"));
        assertEquals(1, found.stream()
                .filter(d -> "E1105".equals(d.code())).count());
    }

    // --- the rule is about constructing, not about the newtype --------------------------------

    @Test
    void anInvariantThatOnlyReadsAConstructedValueIsFine() {
        assertFalse(Compiler.compile("""
                module m
                data Yen = Int invariant value >= 0
                data Row = { lo: Yen }
                let ascending (r: Row): Bool = r.lo.value >= 0
                data Table = List<Row>
                    invariant ok = List.all(r -> ascending(r), value)
                """).isEmpty());
    }
}
