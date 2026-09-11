package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;

import souther.compiler.diag.msg.MessageKeys;
import souther.compiler.diag.Located;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name that arrives twice says so. Two bounded contexts each exposing {@code Amount} are ordinary,
 * and the module integrating both used to be told the import "conflicts with a local definition" —
 * one it had never written (issue #101). The two cases are told apart, and the import collision names
 * both modules it came from.
 */
class CompileImportCollisionTest {

    private static final String A = """
            module probe.a
            exposing ( Amount )
            data Amount = Int
            """;

    private static final String B = """
            module probe.b
            exposing ( Amount )
            data Amount = Decimal
            """;

    @Test
    void twoImportsOfTheSameNameReportBothModules() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(A, B, """
                        module probe.c
                        import probe.a ( Amount )
                        import probe.b ( Amount )
                        data Line = { a: Amount }
                        """)));

        assertTrue(e.getMessage().contains("probe.a"), e.getMessage());
        assertTrue(e.getMessage().contains("probe.b"), e.getMessage());
        assertFalse(e.getMessage().contains("local definition"),
                "there is no local definition to conflict with: " + e.getMessage());
    }

    @Test
    void theCollisionPointsAtBothImports() {
        String source = """
                module probe.c
                import probe.a ( Amount )
                import probe.b ( Amount )
                data Line = { a: Amount }
                """;
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(A, B, source)));

        assertEquals(3, WhereItSits.in(source,
                ((Primary.InSource) e.diagnostic().primary()).place().region()).start().line(), "the caret is on the second import");
        assertEquals(1, e.diagnostic().secondary().size(), "the first import is labelled too");
        assertEquals(2, WhereItSits.in(source,
                ((souther.compiler.diag.DiagnosticPlace.InSource)
                        e.diagnostic().secondary().get(0).place()).region()).start().line());
    }

    @Test
    void aCollisionWithARealLocalDefinitionStillSaysSo() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(A, """
                        module probe.c
                        import probe.a ( Amount )
                        data Amount = String
                        data Line = { a: Amount }
                        """)));

        assertTrue(e.getMessage().contains("local"), e.getMessage());
        assertTrue(e.getMessage().contains("Amount"), e.getMessage());
    }

    /**
     * Two lines bringing in one declaration have written one meaning twice, and there is nothing to
     * settle between them.
     *
     * <p>What a spelling means is counted in the things brought and not in the lines that brought
     * them. Counted in lines, an author who wrote the same import twice was told their name was
     * ambiguous and shown one module twice as the two things it stood for. The library path has
     * always answered it this way — two {@code import List ( map )} lines are one table entry — and
     * a name is not more ambiguous for having arrived through a user module.
     *
     * <p>The redundant line itself goes unsaid. Whether a line that adds nothing is worth a warning
     * is a question about import lines and not about what a name means, and nothing here answers
     * it.
     */
    @Test
    void theSameNameTwiceFromOneModuleIsOneMeaningWrittenTwice() {
        assertDoesNotThrow(() -> Compiler.compileModules(List.of(A, """
                module probe.c
                import probe.a ( Amount )
                import probe.a ( Amount )
                data Line = { a: Amount }
                """)));
    }

    @Test
    void distinctImportedNamesAreFine() {
        Compiler.compileModules(List.of(A, B, """
                module probe.c
                import probe.a ( Amount )
                data Line = { a: Amount }
                """));
    }

    /**
     * Which kind of declaration the import lands beside does not enter into it. A data, a
     * {@code let} and a behavior all reach the value namespace under the name they are written
     * with, so any of them beside an import of that name is one spelling with two answers. The
     * check was written for a data and stopped there: a behavior of the same spelling compiled and
     * emitted a call to the imported one, which is a class that has no such method (issue #287).
     */
    @Test
    void anImportBesideADeclarationOfAnyKindIsRefused() {
        String up = """
                module up exposing ( twice, Thing )
                data Thing = { a: Int }
                behavior twice : (n: Int) -> Int
                let twice (n) = n * 2
                """;
        String upValue = """
                module up exposing ( twice )
                let twice (n: Int) = n * 2
                """;

        assertEquals("import.imported-name-collides-with-a-declaration", refused(List.of(up, """
                module probe.c
                import up ( twice )
                behavior twice : (a: Int, b: Int) -> Int
                let twice (a, b) = a * b
                data Line = { a: Int }
                """)));
        assertEquals("import.imported-name-collides-with-a-declaration", refused(List.of(upValue, """
                module probe.c
                import up ( twice )
                let twice (a: Int, b: Int) = a * b
                data Line = { a: Int }
                """)));
        assertEquals("import.imported-name-collides-with-a-declaration", refused(List.of(up, """
                module probe.c
                import up ( Thing )
                let Thing (n: Int) = n
                data Line = { a: Int }
                """)));
    }

    /** The standard library is imported by the same rule. A module's own declaration used to shadow
     * the import instead, so one spelling meant the library's function on one line and the module's
     * own definition on the next. */
    @Test
    void aLibraryImportBesideADeclarationIsRefused() {
        assertEquals("import.imported-name-collides-with-a-declaration", refused(List.of("""
                module probe.c
                import List ( map )
                let map (n: Int) = n + 1
                data Line = { a: Int }
                """)));
    }

    /**
     * A value an attached file declares is one of the module's values, so an import of that
     * spelling collides with it as one written in the model file does. The library imports are read
     * where the table they fill is built, and the attached files used to join after that — so the
     * one kind of declaration that could still shadow a library import was the one written in a
     * file the check never saw.
     */
    @Test
    void aValueAnAttachedFileDeclaresCollidesWithALibraryImport() {
        assertEquals("import.imported-name-collides-with-a-declaration", refused(List.of("""
                module f25
                import List ( map )
                data In  = { n: Int }
                data Out = { m: Int }
                behavior run : (i: In) -> Out
                    constructs Out
                let run (i) = Out { m = i.n }
                """, """
                examples for f25

                let map = In { n = 1 }

                example run
                    | "a fixture named like the import" : (map) -> Out { m = 1 }
                """)));
    }

    /** A binding is not a declaration. It is written inside a body, and an import says what a name
     * means where no binding answers it. */
    @Test
    void aBindingSpelledLikeAnImportIsStillTheBinding() {
        Compiler.compileModules(List.of("""
                module probe.c
                import List ( map )
                data In = { xs: List<Int> }
                data Out = { n: Int }
                behavior go : (i: In) -> Out constructs Out
                let go (i) = {
                    let map = 7
                    Out { n = map }
                }
                """));
    }

    /**
     * A name the line asks for and the source module does not expose is nothing the import brought
     * in. Saying it collides as well would tell the author to rename a local definition that is not
     * what is wrong; the line has one thing wrong with it and gets one report.
     */
    @Test
    void aNameTheSourceDoesNotExposeIsNotAlsoACollision() {
        Map<SourceId, List<Diagnostic>> diagnostics = Located.diagnosticsOf(Compiler.diagnoseModules(Map.of(
                "up.sou", """
                        module up exposing ( other )
                        data Thing = { a: Int }
                        let other (n: Int) = n
                        """,
                "c.sou", """
                        module probe.c
                        import up ( Thing )
                        let Thing (n: Int) = n
                        data Line = { a: Int }
                        """)));

        assertEquals(List.of("module.the-module-does-not-expose-it"),
                diagnostics.get(new SourceId("c.sou")).stream().map(d -> MessageKeys.of(d.said())).toList());
    }

    /**
     * The collision is reported, not raised. Raised, it escaped the question that read the module
     * and took every other file's diagnostics with it — an editor showed one "the compiler could
     * not finish reading this file" for the whole workspace while the author was part-way through
     * writing the `let` that collided.
     */
    @Test
    void theCollisionIsReportedWithoutStoppingTheOtherFiles() {
        Map<SourceId, List<Diagnostic>> diagnostics = Located.diagnosticsOf(Compiler.diagnoseModules(Map.of(
                "c.sou", """
                        module probe.c
                        import List ( map )
                        let map (n: Int) = n + 1
                        """,
                "d.sou", """
                        module probe.d
                        data Broken = { a: NoSuchType }
                        """)));

        assertEquals(List.of("import.imported-name-collides-with-a-declaration"),
                diagnostics.get(new SourceId("c.sou")).stream().map(d -> MessageKeys.of(d.said())).toList());
        assertEquals(List.of("name.no-type-of-that-name"),
                diagnostics.get(new SourceId("d.sou")).stream().map(d -> MessageKeys.of(d.said())).toList());
    }

    private static String refused(List<String> modules) {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(modules));
        return MessageKeys.of(e.diagnostic().said());
    }
}
