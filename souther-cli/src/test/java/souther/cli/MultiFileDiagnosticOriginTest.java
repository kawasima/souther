package souther.cli;

import souther.compiler.cst.SourceLayout;
import souther.compiler.source.SourceId;

import souther.compiler.Compiler;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;
import souther.compiler.diag.SourcePos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An error in a multi-file compile says which file it came from. A position is a line and a column,
 * so without the module it belongs to there is nothing to quote — the linker tags the error with the
 * source it was compiling, and the caller turns that into the file it read.
 */
class MultiFileDiagnosticOriginTest {

    private static final String A = """
            module a exposing ( 従業員ID )
            data 従業員ID = String
            """;

    private static final String B = """
            module b
            import a ( 従業員ID )
            data Out = { v: Int }
            behavior f : (who: 従業員ID) -> Out
                constructs Out
            let f (who) = Out { v = who }
            """;

    @Test
    void theErrorIsTaggedWithTheSourceItCameFrom() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(A, B)));

        assertEquals(new SourceId("1"), e.sourceId(), "the error is in the second source");
    }

    @Test
    void anErrorInTheFirstSourceIsTaggedToo() {
        String broken = """
                module a exposing ( 従業員ID )
                data 従業員ID = String
                data Bad = { v: Nonexistent }
                """;

        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(broken, B)));

        assertEquals(new SourceId("0"), e.sourceId());
    }

    /** A compile of one source tags its error with that source, as a compile of several does. It
     *  used to tag nothing, which left the primary naming no file while the secondaries named the
     *  one there was — and a renderer comparing them printed a file name over a note in the file it
     *  was already quoting. */
    @Test
    void aSingleModuleCompileCarriesTheOneSourceItWasGiven() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module solo
                data Out = { v: Int }
                behavior f : (s: String) -> Out
                    constructs Out
                let f (s) = Out { v = s }
                """));

        assertEquals(new SourceId("0"), e.sourceId(),
                "the one source is a source, and an error in it says so");
    }

    @Test
    void aModuleWithAnAttachedExampleFileStillNamesItsOwnBodyError() {
        String target = """
                module t
                data Out = { v: Int }
                behavior f : (s: String) -> Out
                    constructs Out
                let f (s) = Out { v = s }
                """;
        String examples = """
                examples for t
                example f
                    | "one" : ("a") -> Out { v = 1 }
                """;

        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(target, examples)));

        assertEquals(new SourceId("0"), e.sourceId(), "the type error is in the module's own body");
    }

    @Test
    void aConstantInvariantViolationNamesTheModuleEvenWithAnAttachedExampleFile() {
        // a regex invariant is not discharged by the checker, so the constant is rejected by the
        // compile-time evaluation that runs alongside the examples — the pass this test pins down
        String target = """
                module t
                import String ( matches )
                data メール = String
                    invariant matches("[^@]+@[^@]+", value)
                data Out = { v: メール }
                behavior f : (n: Int) -> Out
                    constructs Out, メール
                let f (n) = Out { v = メール("not-an-email") }
                """;
        String examples = """
                examples for t
                example f
                    | "one" : (1) -> Out { v = メール("a@b") }
                """;

        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(target, examples)));

        assertEquals(new SourceId("0"), e.sourceId(),
                "the rejected construction is written in the module, not in the example file");
    }

    @Test
    void aFailingExampleNamesTheFileItWasWrittenIn() {
        String target = """
                module t
                data Out = { v: Int }
                behavior f : (n: Int) -> Out
                    constructs Out
                let f (n) = Out { v = n }
                """;
        String examples = """
                examples for t
                example f
                    | "wrong" : (1) -> Out { v = 2 }
                """;

        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(target, examples)));

        assertEquals(new SourceId("1"), e.sourceId(),
                "the row is written in the `examples for` file, so that is the file quoted");
    }

    @Test
    void theCliPicksTheFileTheErrorCameFrom(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("a.sou");
        Path b = dir.resolve("b.sou");
        Files.writeString(a, A);
        Files.writeString(b, B);

        CompileException e = assertThrows(CompileException.class,
                () -> Main.compileToDir(List.of(a, b), dir.resolve("out")));

        assertEquals(b, Main.sourceOf(List.of(a, b), e));
    }

    @Test
    void anUntaggedMultiFileErrorStillRendersWithoutASnippet(@TempDir Path dir) {
        Path a = dir.resolve("a.sou");
        Path b = dir.resolve("b.sou");
        CompileException untagged = new CompileException(
                Diagnostic.literal(new SourcePos(1, 1), "boom"), "boom");

        assertNull(Main.sourceOf(List.of(a, b), untagged));
    }

    @Test
    void theRenderedErrorQuotesTheRightFile(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("a.sou");
        Path b = dir.resolve("b.sou");
        Files.writeString(a, A);
        Files.writeString(b, B);

        CompileException e = assertThrows(CompileException.class,
                () -> Main.compileToDir(List.of(a, b), dir.resolve("out")));
        Path source = Main.sourceOf(List.of(a, b), e);
        String rendered = new HumanRenderer(false).render(e.diagnostic(),
                new SourceContext(source.getFileName().toString(), Files.readString(source), SourceLayout.of(Files.readString(source))),
                Locale.ENGLISH);

        assertTrue(rendered.contains("b.sou:6:"), rendered);
        assertTrue(rendered.contains("let f (who) = Out { v = who }"), rendered);
    }
}
