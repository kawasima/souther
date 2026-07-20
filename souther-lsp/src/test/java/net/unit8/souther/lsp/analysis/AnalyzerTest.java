package net.unit8.souther.lsp.analysis;

import net.unit8.souther.lsp.protocol.DocumentSymbol;
import net.unit8.souther.lsp.protocol.Hover;
import net.unit8.souther.lsp.protocol.LspDiagnostic;
import net.unit8.souther.lsp.protocol.Position;
import net.unit8.souther.lsp.protocol.Range;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyzerTest {

    private final Analyzer analyzer = new Analyzer();

    @Test
    void aValidModuleHasNoDiagnostics() {
        List<LspDiagnostic> diags = analyzer.diagnostics("module demo\ndata X = { a: Int }\n");
        assertEquals(List.of(), diags);
    }

    @Test
    void aSyntaxErrorIsReportedWithARange() {
        // `name String` is missing the `:` between the field name and its type
        List<LspDiagnostic> diags = analyzer.diagnostics("module demo\ndata M = { name String }\n");
        assertTrue(!diags.isEmpty(), "expected a syntax diagnostic");
        LspDiagnostic d = diags.get(0);
        assertEquals(LspDiagnostic.ERROR, d.severity());
        assertEquals(1, d.range().start().line(), "the error is on the second line (0-based line 1)");
    }

    @Test
    void aSemanticErrorIsReportedWhenSyntaxIsClean() {
        // a type variable in a user module is a semantic error the compiler raises
        String src = "module demo\ndata X = { v: Int }\nlet f (x: 'a) = x\n";
        List<LspDiagnostic> diags = analyzer.diagnostics(src);
        assertTrue(!diags.isEmpty(), "expected a semantic diagnostic");
        assertTrue(diags.get(0).message().contains("type variable"), diags.get(0).message());
    }

    @Test
    void documentSymbolsListEveryTopLevelDefinition() {
        String src = "module demo\n"
                + "data 会員 =\n    { id: String\n    , 名前: String\n    }\n"
                + "behavior greet : (m: 会員) -> String\n"
                + "let greet (m) = m.名前\n";
        List<DocumentSymbol> symbols = analyzer.documentSymbols(src);
        assertEquals(3, symbols.size());
        assertEquals("会員", symbols.get(0).name());
        assertEquals(DocumentSymbol.CLASS, symbols.get(0).kind());
        assertEquals(List.of("id", "名前"),
                symbols.get(0).children().stream().map(DocumentSymbol::name).toList());
        assertEquals("greet", symbols.get(1).name());
        assertEquals(DocumentSymbol.INTERFACE, symbols.get(1).kind());
        assertEquals(DocumentSymbol.FUNCTION, symbols.get(2).kind());
    }

    @Test
    void semanticTokensDeltaEncodeAndClassifyFromTheTree() {
        int[] data = analyzer.semanticTokens("module demo\ndata X = { a: Int }\n");
        assertEquals(0, data.length % 5, "five integers per token");
        List<int[]> tokens = decodeSemanticTokens(data);

        // `module` keyword at line 0, char 0; `demo` namespace at line 0, char 7
        assertEquals(7, typeAt(tokens, 0, 0), "`module` is a keyword (index 7)");
        assertEquals(0, typeAt(tokens, 0, 7), "`demo` is a namespace (index 0)");
        // `X` is a type (index 1) at char 5, `a` a property (5) at char 11, `Int` a type at char 14
        assertEquals(1, typeAt(tokens, 1, 5), "`X` is a type");
        assertEquals(5, typeAt(tokens, 1, 11), "`a` is a property");
        assertEquals(1, typeAt(tokens, 1, 14), "`Int` is a type");
    }

    /** Reverses the LSP delta encoding into absolute {@code {line, char, length, type}} tokens. */
    private static List<int[]> decodeSemanticTokens(int[] data) {
        List<int[]> out = new java.util.ArrayList<>();
        int line = 0;
        int ch = 0;
        for (int i = 0; i < data.length; i += 5) {
            int deltaLine = data[i];
            int deltaChar = data[i + 1];
            if (deltaLine == 0) {
                ch += deltaChar;
            } else {
                line += deltaLine;
                ch = deltaChar;
            }
            out.add(new int[]{line, ch, data[i + 2], data[i + 3]});
        }
        return out;
    }

    private static int typeAt(List<int[]> tokens, int line, int ch) {
        for (int[] t : tokens) {
            if (t[0] == line && t[1] == ch) {
                return t[3];
            }
        }
        throw new AssertionError("no semantic token at " + line + ":" + ch);
    }

    private static final String RESOLVE_SRC =
            "module demo\n"
            + "data Thing = { id: String }\n"
            + "behavior use : (t: Thing) -> String\n"
            + "let use (t) = t.id\n";

    @Test
    void definitionResolvesATypeReferenceToItsDeclaration() {
        // the `Thing` reference in the behavior signature is at line 2, character 19
        Optional<Range> target = analyzer.definition(RESOLVE_SRC, new Position(2, 19));
        assertTrue(target.isPresent(), "expected a definition");
        assertEquals(1, target.get().start().line(), "the `data Thing` declaration is on line 1");
        assertEquals(5, target.get().start().character(), "at the name `Thing`");
    }

    @Test
    void hoverShowsTheSignatureLine() {
        Optional<Hover> hover = analyzer.hover(RESOLVE_SRC, new Position(1, 5));   // over `Thing`
        assertTrue(hover.isPresent());
        assertTrue(hover.get().contents().contains("data Thing"), hover.get().contents());
    }

    @Test
    void hoverOnAFieldShowsItsDeclaredType() {
        // the field name `id` in `data Thing = { id: String }` is at line 1, character 15
        Optional<Hover> hover = analyzer.hover(RESOLVE_SRC, new Position(1, 15));
        assertTrue(hover.isPresent());
        assertTrue(hover.get().contents().contains("id: String"), hover.get().contents());
    }

    @Test
    void analysisNeverThrows() {
        // a broken buffer must yield diagnostics, not an exception
        assertTrue(!analyzer.diagnostics("module\ndata = = {{{").isEmpty());
    }
}
