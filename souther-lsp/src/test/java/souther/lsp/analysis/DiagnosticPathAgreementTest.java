package souther.lsp.analysis;

import souther.compiler.diag.PhysicalPos;
import souther.compiler.cst.SourceLayout;
import souther.compiler.Compiler;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.lsp.protocol.LspDiagnostic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The same error, reached two ways, says the same thing. A compile reports through
 * {@link CompileException}, which the CLI and the annotation processor render; an editor reaches the
 * same source through {@link Analyzer}, which builds {@code LspDiagnostic}s. The two are separate
 * implementations over one compile, and every diagnostic defect this project has filed —
 * #93 (the processor printed a raw line), #94 (a multi-file compile lost the file), #98 (an
 * aggregate lost the reason) — was one path behaving differently from another.
 *
 * <p>Nothing compared them. Each path had its own tests, over its own inputs, so a change to one
 * could not fail the other. This runs one source through both and asserts they agree on what is
 * wrong and where.
 */
class DiagnosticPathAgreementTest {

    private final Analyzer analyzer = new Analyzer();

    /** Sources whose first error both paths must describe the same way. Each names a different check,
     *  so the agreement is not a property of one diagnostic family. */
    static Stream<org.junit.jupiter.params.provider.Arguments> brokenSources() {
        return Stream.of(
                arguments("a field type that does not exist", """
                        module demo
                        data Out = { v: Nonexistent }
                        """),
                arguments("a body whose type does not match the signature", """
                        module demo
                        data Out = { v: Int }
                        behavior f : (s: String) -> Out
                            constructs Out
                        let f (s) = Out { v = s }
                        """),
                arguments("a construction the behavior may not make", """
                        module demo
                        data Out = { v: Int }
                        data Other = { v: Int }
                        behavior f : (n: Int) -> Out
                            constructs Out
                        let f (n) = Other { v = n }
                        """),
                arguments("a failing example", """
                        module demo
                        data In = { n: Int }
                        data Out = { n: Int }
                        behavior f : (i: In) -> Out constructs Out
                        let f (i) = Out { n = i.n }
                        example f
                          | "wrong" : (In { n = 1 }) -> Out { n = 2 }
                        """),
                arguments("a Map key that cannot cross the boundary", """
                        module demo
                        data EmployeeNo = Int
                        data Roster = { byNo: Map<EmployeeNo, String> }
                        """),
                arguments("an unknown name in a body", """
                        module demo
                        data Out = { v: Int }
                        behavior f : (n: Int) -> Out
                            constructs Out
                        let f (n) = Out { v = bogus }
                        """),
                arguments("an optional as a newtype base", """
                        module demo
                        data Note = String?
                        """),
                arguments("a binding that opens what is not a newtype", """
                        module demo
                        data Line = { sku: String, qty: Int }
                        data Out = { v: String }
                        behavior read : (l: Line) -> Out constructs Out
                        let read (l) = {
                            let Line(x) = l
                            Out { v = x }
                        }
                        """),
                arguments("a binding that opens a newtype the value is not", """
                        module demo
                        data Tags = List<String>
                        data Labels = List<String>
                        data Out = { v: Int }
                        behavior read : (t: Tags) -> Out constructs Out
                        let read (t) = {
                            let Labels(xs) = t
                            Out { v = 1 }
                        }
                        """),
                // Syntax errors reach the editor through the recovering parser rather than through a
                // compile, so they are the one family whose agreement is not implied by the others.
                arguments("a behavior signature that does not read", """
                        module demo
                        behavior submit : (id Int) -> Int
                        """),
                arguments("a lambda missing its arrow", """
                        module demo
                        let f (xs: List<Int>) : List<Int> = List.map((x) x, xs)
                        """),
                arguments("a case pattern that does not read", """
                        module demo
                        data Rank = Manager | Staff
                        data Manager
                        data Staff
                        let name (r: Rank) : Int =
                            match r with
                                | Manager as -> 1
                                | Staff -> 2
                        """),
                arguments("an example row that does not read", """
                        module demo
                        data Amount = Int
                        behavior double : (a: Amount) -> Amount
                            constructs Amount
                        let double (a) = Amount(a.value * 2)
                        example double
                            | (Amount(1)) Amount(2)
                        """),
                arguments("a fractional literal without its suffix", """
                        module demo
                        let rate () : Int = 1.5
                        """),
                arguments("a character that begins nothing", """
                        module demo
                        let f () : Int = @
                        """));
    }

    private static org.junit.jupiter.params.provider.Arguments arguments(String label, String source) {
        return org.junit.jupiter.params.provider.Arguments.of(label, source);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenSources")
    void theCompileAndTheEditorAgreeOnTheCode(String label, String source) {
        Diagnostic compiled = firstFromCompile(source);
        LspDiagnostic seen = firstFromAnalyzer(source);

        assertEquals(compiled.code(), seen.code(),
                "the stable identity is what a tool keys on, so both paths must give the same one");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenSources")
    void theCompileAndTheEditorAgreeOnWhereItIs(String label, String source) {
        Diagnostic compiled = firstFromCompile(source);
        LspDiagnostic seen = firstFromAnalyzer(source);

        // LSP positions are 0-based, the compiler's are 1-based; that offset is the only difference
        // allowed between them.
        // The compile reads a document of its own, so what it reports points into a source of it.
        souther.compiler.diag.SourcePos at = assertInstanceOf(
                souther.compiler.diag.Primary.InSource.class, compiled.primary(),
                "a report from a compile of a source points into that source")
                .place().region().start();
        PhysicalPos sits = SourceLayout.of(source).resolve(at);
        assertEquals(sits.line() - 1, seen.range().start().line(), "line");
        assertEquals(sits.column() - 1, seen.range().start().character(), "column");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenSources")
    void neitherPathReportsAnEmptyReason(String label, String source) {
        LspDiagnostic seen = firstFromAnalyzer(source);

        assertFalse(seen.message() == null || seen.message().isBlank(),
                "an editor marker with no message is what #98 shipped");
        assertFalse(seen.message().contains("{0}"),
                "an unsubstituted placeholder means the catalog pattern is broken");
    }

    @Test
    void aCleanSourceIsCleanOnBothPaths() {
        String source = """
                module demo
                data In = { n: Int }
                data Out = { n: Int }
                behavior f : (i: In) -> Out constructs Out
                let f (i) = Out { n = i.n * 2 }
                example f
                  | "doubles" : (In { n = 3 }) -> Out { n = 6 }
                """;

        Compiler.compile(source);
        assertEquals(List.of(), analyzer.diagnostics(source));
    }

    private static Diagnostic firstFromCompile(String source) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(source),
                "the source is supposed to be broken");
        return e.diagnostic();
    }

    private LspDiagnostic firstFromAnalyzer(String source) {
        List<LspDiagnostic> seen = analyzer.diagnostics(source);
        assertFalse(seen.isEmpty(), "the editor path found nothing in a source the compile rejects");
        return seen.get(0);
    }
}
