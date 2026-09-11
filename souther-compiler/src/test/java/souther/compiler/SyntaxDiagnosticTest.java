package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.msg.ParseMessage;
import souther.compiler.diag.CompileException;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Syntax errors: every parser/lexer error carries the SYNTAX ERROR title, reports token kinds by a
 * reader-facing name (`:`, a name — not COLON/IDENT), and localizes both the message and the token
 * category. */
class SyntaxDiagnosticTest {

    private static Diagnostic diagnosticOf(String src) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        return e.diagnostic();
    }

    @Test
    void everyParserErrorCarriesTheSyntaxTitle() {
        // a bespoke error (fn is not a top-level keyword) and a generic expect error both get it
        assertEquals("parse.title", diagnosticOf("module demo\nfn f (x) = x\n").titleKey());
        assertEquals("parse.title", diagnosticOf("module demo\ndata M = { name String }\n").titleKey());
    }

    @Test
    void expectedFoundUsesFriendlyTokenNamesAndLocalizes() {
        Diagnostic d = diagnosticOf("module demo\ndata M = { name String }\n");
        assertInstanceOf(ParseMessage.ADeclarationExpectedSomethingElse.class, d.said());
        SourceContext src = new SourceContext("m.sou", "module demo\ndata M = { name String }\n", SourceLayout.of("module demo\ndata M = { name String }\n"));
        String en = new HumanRenderer(false).render(d, src, Locale.ENGLISH);
        String ja = new HumanRenderer(false).render(d, src, Locale.JAPANESE);
        assertTrue(en.contains("I expected `:` here, but found a name."), en);
        assertTrue(ja.contains("`:`") && ja.contains("名前"), ja);
        assertTrue(en.contains("SYNTAX ERROR") && ja.contains("構文エラー"));
    }

    @Test
    void aBlockOfOnlyStatementsIsASyntaxError() {
        String source = """
                module demo
                let run (i) = {
                    let a = i
                }
                """;
        Diagnostic d = diagnosticOf(source);
        assertEquals("parse.title", d.titleKey());
        assertInstanceOf(ParseMessage.ABlockEndsInOneExpression.class, d.said());
        SourceContext src = new SourceContext("m.sou", source, SourceLayout.of(source));
        String en = new HumanRenderer(false).render(d, src, Locale.ENGLISH);
        String ja = new HumanRenderer(false).render(d, src, Locale.JAPANESE);
        assertTrue(en.contains("A block ends in one expression, which is its value"), en);
        assertTrue(ja.contains("ブロックは最後に値となる式を1つ置きます"), ja);
    }

    /**
     * A result written under a statement that ends in a name is that result. An argument list must
     * begin on the line its callee ends on, and that rule is now the only one: {@code i} and the
     * following line's {@code (a)} were read as one call while an applied name was a shape of its
     * own, which left the block with no result (issue #75) — and made a name applied across a line
     * break mean something a name applied to the result of an expression never did (issue #274).
     */
    @Test
    void aResultUnderAStatementIsNotAppliedToTheNameAboveIt() {
        String source = """
                module demo
                data Out = { n: Int }
                let run (i: Int) = {
                    let a = i
                    (a)
                }
                behavior go : (n: Int) -> Out constructs Out
                let go (n) = Out { n = run(n) }
                """;
        assertDoesNotThrow(() -> Compiler.compile(source));
    }

    @Test
    void lexerErrorIsLocalized() {
        Diagnostic d = diagnosticOf("module demo\ndata M = Int\nlet x = 1.5\n");
        assertEquals("parse.title", d.titleKey());
        assertInstanceOf(ParseMessage.AFractionalLiteralNeedsTheMSuffix.class, d.said());
    }
}
