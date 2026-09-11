package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.diag.msg.MessageKeys;
import org.junit.jupiter.api.Test;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Db;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a build is told about a name on an import list that nothing below writes.
 *
 * <p>An import list is the module's statement of what vocabulary it speaks, and it is the part of a
 * model a reader believes without checking — finding out otherwise means searching the file for each
 * name on it. So the check is the reverse of the one that reads an import: not "what does this name
 * mean" but "did anyone say it".
 *
 * <p>Said of a spelling and not of a declaration. What another module declares is reachable qualified
 * whether or not it is imported, so a name used only as {@code Stock.Sku} is a use of the declaration
 * and not of the list entry, which can go.
 */
class CompileUnusedImportWarningTest {

    private static final String STOCK = """
            module probe.stock exposing ( StockLine, Sku, Count, priced, unit )

            data Sku = String
                invariant String.length(value) >= 1

            data Count = Int
                invariant value >= 0

            data StockLine = { sku: Sku, quantity: Count }

            behavior priced : (line: StockLine) -> Count

            let priced (line) = line.quantity

            let unit (n: Int) : Int = n
            """;

    /** Every unused-import warning a compile of these sources reports, as `name` at line:column. */
    private static List<String> unused(String... sources) {
        Compilation compilation = Compilation.ofSources(List.of(sources), ModulePath.EMPTY);
        compilation.answerEverything();
        List<String> found = new ArrayList<>();
        for (Db.Found report : compilation.db().allReports()) {
            Diagnostic d = report.report().diagnostic();
            if (!"E1922".equals(d.code())) {
                continue;
            }
            assertFalse(report.report().isError(), "an unused import does not stop a build");
            SourcePos at = ((Primary.InSource) d.primary()).place().region().start();
            found.add(d.values().get("name") + " at " + WhereItSits.in(STOCK, at).line() + ":" + WhereItSits.in(STOCK, at).column());
        }
        return found;
    }

    /**
     * What a compile of these sources said, so a test can name what was reported instead.
     *
     * <p>A diagnostic's code where it has one, and its message key where it does not — several of the
     * import errors are uncoded, and a test that could only match on codes could not tell them from
     * each other or from nothing at all.
     */
    private static List<String> said(String... sources) {
        Compilation compilation = Compilation.ofSources(List.of(sources), ModulePath.EMPTY);
        compilation.answerEverything();
        List<String> found = new ArrayList<>();
        for (Db.Found report : compilation.db().allReports()) {
            Diagnostic d = report.report().diagnostic();
            found.add(d.code() != null ? d.code() : MessageKeys.of(d.said()));
        }
        return found;
    }

    /** A type written bare is what the list entry bought. */
    @Test
    void aTypeWrittenBareIsUsed() {
        assertEquals(List.of(), unused(STOCK, """
                module probe.picking

                import probe.stock ( Sku )

                data Pick = { sku: Sku }
                """));
    }

    /** The repro from the issue: two names imported, one written. */
    @Test
    void aTypeNobodyWritesIsReportedAtItsOwnName() {
        assertEquals(List.of("StockLine at 3:27"), unused(STOCK, """
                module probe.picking

                import probe.stock ( Sku, StockLine )

                data Pick = { sku: Sku }
                """));
    }

    /** A behavior reached by name, and a value expanded at its call, are both uses. */
    @Test
    void aBehaviorAndAValueWrittenBareAreUsed() {
        assertEquals(List.of(), unused(STOCK, """
                module probe.picking

                import probe.stock ( StockLine, Count, priced, unit )

                data Pick = { line: StockLine }

                behavior weigh : (p: Pick) -> Count
                    constructs Count

                let weigh (p) = Count(unit(priced(p.line).value))
                """));
    }

    /**
     * A name only a {@code >->} stage writes is used.
     *
     * <p>Resolution passes a stage through untouched — it is answered where the module is bound — so
     * a check that read only the resolved names would call this import unused and be wrong.
     */
    @Test
    void aBehaviorNamedOnlyByAPipelineStageIsUsed() {
        assertEquals(List.of(), unused(STOCK, """
                module probe.picking

                import probe.stock ( StockLine, Count, priced )

                behavior weigh : (line: StockLine) -> Count
                    constructs Count

                behavior whole = priced
                """));
    }

    /** The same, for a name only a {@code depends on} clause writes. */
    @Test
    void aBehaviorNamedOnlyByDependsOnIsUsed() {
        assertEquals(List.of(), unused(STOCK, """
                module probe.picking

                import probe.stock ( StockLine, Count, priced )

                behavior weigh : (line: StockLine) -> Count
                    constructs Count
                    depends on priced

                let weigh (line) = priced(line)
                """));
    }

    /**
     * A declaration reached through its module is a use of the declaration and not of the entry.
     *
     * <p>Both spellings mean the one type. What differs is what the import list bought: nothing, since
     * taking `Sku` off the list leaves `probe.stock.Sku` saying exactly what it said before.
     */
    @Test
    void aNameOnlyEverWrittenQualifiedIsUnused() {
        assertEquals(List.of("Sku at 3:22"), unused(STOCK, """
                module probe.picking

                import probe.stock ( Sku )

                data Pick = { sku: probe.stock.Sku }
                """));
    }

    /** The same through an alias. */
    @Test
    void aNameOnlyEverWrittenThroughAnAliasIsUnused() {
        assertEquals(List.of("Sku at 3:31"), unused(STOCK, """
                module probe.picking

                import probe.stock as Stock ( Sku )

                data Pick = { sku: Stock.Sku }
                """));
    }

    /** A prelude import is an import list like any other, and its names are checked the same way. */
    @Test
    void anUnusedPreludeNameIsReportedAndAUsedOneIsNot() {
        assertEquals(List.of("all at 3:20", "fold at 3:25"), unused("""
                module probe.tally

                import List ( map, all, fold )

                data Ns = { values: List<Int> }

                behavior doubled : (ns: Ns) -> Ns
                    constructs Ns

                let doubled (ns) = Ns { values = map(x -> x * 2, ns.values) }
                """));
    }

    /** A library function written qualified needs no entry either. */
    @Test
    void aPreludeNameOnlyEverWrittenQualifiedIsUnused() {
        assertEquals(List.of("map at 3:15"), unused("""
                module probe.tally

                import List ( map )

                data Ns = { values: List<Int> }

                behavior doubled : (ns: Ns) -> Ns
                    constructs Ns

                let doubled (ns) = Ns { values = List.map(x -> x * 2, ns.values) }
                """));
    }

    /**
     * A qualified behavior reference makes the compiler synthesize an import of its own. Nobody wrote
     * that one on a list, so there is nothing anybody could delete.
     */
    @Test
    void anImportSynthesizedFromAQualifiedReferenceIsNotReported() {
        assertEquals(List.of(), unused(STOCK, """
                module probe.picking

                import probe.stock ( StockLine, Count )

                behavior weigh : (line: StockLine) -> Count
                    constructs Count

                behavior whole = probe.stock.priced
                """));
    }

    /** A row in an attached `examples for` file writes the module's names, so what it writes is used. */
    @Test
    void aNameWrittenOnlyByAnAttachedExampleFileIsUsed() {
        assertEquals(List.of(), unused(STOCK, """
                module probe.picking exposing ( Pick, Count, weigh )

                import probe.stock ( StockLine, Count, Sku )

                data Pick = { line: StockLine }

                behavior weigh : (p: Pick) -> Count
                    constructs Count

                let weigh (p) = p.line.quantity
                """, """
                examples for probe.picking

                example weigh
                    | (Pick { line = StockLine { sku = Sku("a"), quantity = Count(2) } }) -> Count(2)
                """));
    }

    /**
     * An import that names something the module does not expose is reported as that, and nothing else.
     *
     * <p>The name is on the list and nothing below writes it, which is true and beside the point: what
     * is wrong is the import, and telling the author to delete a line they are part-way through typing
     * is the wrong advice twice over.
     */
    @Test
    void anImportThatIsAlreadyWrongIsNotAlsoCalledUnused() {
        List<String> said = said(STOCK, """
                module probe.picking

                import probe.stock ( NotExposed )

                data Pick = { n: Int }
                """);

        assertFalse(said.contains("E1922"), said.toString());
        assertTrue(said.contains("E1507"),
                "the import itself is still reported: " + said);
    }

    /**
     * The fixture these tests are written against compiles clean.
     *
     * <p>Here because the tests that assert what was said instead of an unused import would pass on a
     * fixture that reported something else entirely — an assertion that a list is not empty is
     * satisfied by any mistake in the model, including one nobody meant to write.
     */
    @Test
    void theFixtureItselfSaysNothing() {
        assertEquals(List.of(), said(STOCK));
    }

    /** A module naming a module this compilation does not have says so, and says nothing else. */
    @Test
    void anImportOfAnUnknownModuleIsNotAlsoCalledUnused() {
        assertEquals(List.of(), unused("""
                module probe.picking

                import probe.nowhere ( Sku )

                data Pick = { n: Int }
                """));
    }

    /**
     * A name in the body that denotes nothing silences this.
     *
     * <p>`Skus` may be what `Sku` was meant to be. "This import is unused" is a false statement about a
     * module whose only mistake is a typo, and the advice it carries — delete the line — would take the
     * import away from the use that was reaching for it.
     */
    @Test
    void aBodyWithANameThatDenotesNothingIsNotToldAboutItsImports() {
        assertEquals(List.of(), unused(STOCK, """
                module probe.picking

                import probe.stock ( Sku, StockLine )

                data Pick = { sku: Skus }
                """));
    }

    /**
     * A name written as a case of a local sum is written, whatever the check makes of it afterwards.
     *
     * <p>Worth its own test because the advice here would not merely be noise. Taking the name off
     * the list does not leave the module saying what it said: an implicit unit data of that spelling
     * is synthesized in its place ({@link souther.compiler.frontend.ImplicitUnits}), so the sum's
     * case stops being the other module's declaration and becomes a new local type — and the compile
     * that reported an error goes quiet while meaning something else.
     */
    @Test
    void aNameWrittenAsACaseOfALocalSumIsUsed() {
        List<String> said = said("""
                module probe.base exposing ( Ok, Failed )
                data Failed = { why: String }
                data Ok = { v: Int }
                """, """
                module probe.p exposing ( Outcome )
                import probe.base ( Ok, Failed )
                data Outcome = Ok | Failed
                """);

        assertFalse(said.contains("E1922"), "the cases are written, so the imports are used: " + said);
        assertTrue(said.contains("E1606"), "what is wrong is the sum, and it is still said: " + said);
    }

    /** A row that disagrees with the model is not a reason to stop saying this: it says nothing about
     * whether anyone wrote the name. */
    @Test
    void aFailingExampleDoesNotSilenceIt() {
        assertEquals(List.of("StockLine at 3:27"), unused(STOCK, """
                module probe.picking exposing ( Pick, Count, weigh )

                import probe.stock ( Sku, StockLine, Count )

                data Pick = { sku: Sku }

                behavior weigh : (p: Pick) -> Count
                    constructs Count

                let weigh (p) = Count(1)

                example weigh
                    | (Pick { sku = Sku("a") }) -> Count(2)
                """));
    }
}
