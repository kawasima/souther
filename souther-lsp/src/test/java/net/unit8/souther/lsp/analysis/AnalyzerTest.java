package net.unit8.souther.lsp.analysis;

import net.unit8.souther.lsp.protocol.DocumentSymbol;
import net.unit8.souther.lsp.protocol.Hover;
import net.unit8.souther.lsp.protocol.Location;
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
    void diagnosticsAcrossModulesLandOnTheOwningFile() {
        String a = "module a exposing ( N )\ndata N = { v: Int }\n";
        String b = "module b\nimport a ( N )\ndata M = { n: Int }\n"
                + "behavior f : (x: M) -> M\nlet f (x) = x\n"
                + "example f\n  | (M { n = 1 }) -> M { n = 2 }\n";   // identity f, so the example fails
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a, "file:///b.sou", b));

        java.util.Map<String, List<LspDiagnostic>> diags = analyzer.diagnostics(graph);

        assertEquals(List.of(), diags.get("file:///a.sou"), "the clean imported module has no diagnostics");
        assertEquals(1, diags.get("file:///b.sou").size(), diags.get("file:///b.sou").toString());
        assertEquals("E1905", diags.get("file:///b.sou").get(0).code());
    }

    @Test
    void diagnosticsDoNotBailWhenAModuleImportsAnother() {
        // the single-file path returns nothing on an import; the workspace path resolves it and finds
        // the importing module clean
        String a = "module a exposing ( N )\ndata N = { v: Int }\n";
        String b = "module b\nimport a ( N )\ndata M = { n: Int }\n"
                + "behavior f : (x: M) -> M\nlet f (x) = x\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a, "file:///b.sou", b));

        java.util.Map<String, List<LspDiagnostic>> diags = analyzer.diagnostics(graph);

        assertEquals(List.of(), diags.get("file:///b.sou"), "a valid importing module is clean");
    }

    @Test
    void workspaceDiagnosticsReportEverySemanticErrorInAFile() {
        // two behaviors, each with an unbound name — the type checker recovers past the first
        String m = "module m\ndata N = { v: Int }\n"
                + "behavior f : (n: N) -> N\nlet f (n) = bogusOne\n"
                + "behavior g : (n: N) -> N\nlet g (n) = bogusTwo\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///m.sou", m));

        List<LspDiagnostic> diags = analyzer.diagnostics(graph).get("file:///m.sou");

        assertEquals(2, diags.size(), diags.toString());
    }

    @Test
    void aFailingExampleMessageIncludesExpectedAndActual() {
        String a = "module a\ndata M = { n: Int }\nbehavior f : (x: M) -> M\nlet f (x) = x\n"
                + "example f\n  | (M { n = 1 }) -> M { n = 2 }\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a));

        List<LspDiagnostic> diags = analyzer.diagnostics(graph).get("file:///a.sou");

        assertEquals(1, diags.size(), diags.toString());
        assertEquals("E1905", diags.get(0).code());
        String msg = diags.get(0).message();
        assertTrue(msg.contains("expected") && msg.contains("was"),
                "the example message keeps the expected/actual detail: " + msg);
    }

    @Test
    void aSyntaxErrorInAnImportedFileDoesNotCascadeToItsImporter() {
        String a = "module a exposing ( N )\ndata N = { name String }\n";   // missing `:` — a syntax error
        String b = "module b\nimport a ( N )\nbehavior f : (n: N) -> N\nlet f (n) = n\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a, "file:///b.sou", b));

        java.util.Map<String, List<LspDiagnostic>> diags = analyzer.diagnostics(graph);

        assertTrue(!diags.get("file:///a.sou").isEmpty(), "the broken file shows its own syntax error");
        assertEquals(List.of(), diags.get("file:///b.sou"),
                "the importer is not told the (broken but present) module is unknown");
    }

    @Test
    void examplesForFileFailuresLandOnThatFileNotTheModule() {
        String a = "module a exposing ( M, f )\ndata M = { n: Int }\n"
                + "behavior f : (x: M) -> M\nlet f (x) = x\n";
        String aExamples = "examples for a\nexample f\n  | (M { n = 1 }) -> M { n = 2 }\n";   // fails
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of(
                "file:///a.sou", a, "file:///a.examples.sou", aExamples));

        java.util.Map<String, List<LspDiagnostic>> diags = analyzer.diagnostics(graph);

        assertEquals(List.of(), diags.get("file:///a.sou"), "the module file itself is clean");
        assertEquals(1, diags.get("file:///a.examples.sou").size(),
                diags.get("file:///a.examples.sou").toString());
        assertEquals("E1905", diags.get("file:///a.examples.sou").get(0).code());
    }

    @Test
    void definitionResolvesAcrossAnImport() {
        String a = "module a exposing ( N )\ndata N = { v: Int }\n";
        String b = "module b\nimport a ( N )\nbehavior f : (n: N) -> N\nlet f (n) = n\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a, "file:///b.sou", b));

        // the `N` param-type reference in b's behavior signature is at line 2, character 17
        Optional<Location> target = analyzer.definition("file:///b.sou", new Position(2, 17), graph);

        assertTrue(target.isPresent(), "expected a cross-module definition");
        assertEquals("file:///a.sou", target.get().uri(), "N is defined in module a");
        assertEquals(1, target.get().range().start().line(), "the `data N` declaration is on line 1");
        assertEquals(5, target.get().range().start().character(), "at the name `N`");
    }

    @Test
    void definitionStillResolvesWithinTheSameFile() {
        String a = "module a exposing ( N )\ndata N = { v: Int }\n";
        String b = "module b\nimport a ( N )\ndata Thing = { id: String }\n"
                + "behavior use : (t: Thing) -> Thing\nlet use (t) = t\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a, "file:///b.sou", b));

        // the `Thing` reference in b's own signature (line 3, char 19) resolves within b
        Optional<Location> target = analyzer.definition("file:///b.sou", new Position(3, 19), graph);

        assertTrue(target.isPresent());
        assertEquals("file:///b.sou", target.get().uri());
        assertEquals(2, target.get().range().start().line(), "the `data Thing` declaration is on line 2");
    }

    @Test
    void referencesFindTypeUsagesAcrossModules() {
        String a = "module a exposing ( N )\ndata N = { v: Int }\n";
        String b = "module b\nimport a ( N )\nbehavior f : (n: N) -> N\nlet f (n) = n\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a, "file:///b.sou", b));

        // cursor on the `data N` declaration in a (line 1, char 5)
        List<Location> refs = analyzer.references("file:///a.sou", new Position(1, 5), graph, true);

        assertEquals(java.util.Set.of("file:///a.sou:1:5", "file:///b.sou:2:17", "file:///b.sou:2:23"),
                keys(refs), refs.toString());
    }

    @Test
    void referencesFindValueUsagesAcrossModules() {
        String a = "module a exposing ( N, g )\n"
                + "data N = { v: Int }\n"
                + "behavior g : (n: N) -> N\n"
                + "let g (n) = n\n";
        String b = "module b\nimport a ( N, g )\nbehavior h = g >-> g\n";   // two uses of a.g
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a, "file:///b.sou", b));

        // cursor on the first `g` use in b's composition (line 2, char 13); declaration excluded
        List<Location> refs = analyzer.references("file:///b.sou", new Position(2, 13), graph, false);

        assertEquals(java.util.Set.of("file:///b.sou:2:13", "file:///b.sou:2:19"), keys(refs), refs.toString());
    }

    @Test
    void referencesToAValueExcludeAShadowingParam() {
        String a = "module a exposing ( N, g )\n"
                + "data N = { v: Int }\n"
                + "behavior g : (n: N) -> N\n"
                + "let g (n) = n\n";
        // c imports g but also binds a param named g; the body `g` is the param, not a.g
        String c = "module c\nimport a ( N, g )\nbehavior k : (g: N) -> N\nlet k (g) = g\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a, "file:///c.sou", c));

        // cursor on a's `behavior g` declaration (line 2, char 9)
        List<Location> refs = analyzer.references("file:///a.sou", new Position(2, 9), graph, true);

        // only a's own two declarations (behavior g, let g); c contributes nothing — its `g` is shadowed
        assertEquals(java.util.Set.of("file:///a.sou:2:9", "file:///a.sou:3:4"), keys(refs), refs.toString());
    }

    private static java.util.Set<String> keys(List<Location> refs) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (Location l : refs) {
            out.add(l.uri() + ":" + l.range().start().line() + ":" + l.range().start().character());
        }
        return out;
    }

    @Test
    void analysisNeverThrows() {
        // a broken buffer must yield diagnostics, not an exception
        assertTrue(!analyzer.diagnostics("module\ndata = = {{{").isEmpty());
    }

    @Test
    void aFailingInlineExampleIsReportedOnSave() {
        // a self-contained module is compiled on save, so a failing `example` surfaces as E1905
        String src = "module demo\n"
                + "data M = { n: Int }\n"
                + "behavior f : (x: M) -> M\n"
                + "let f (x) = x\n"
                + "example f\n"
                + "  | (M { n = 1 }) -> M { n = 2 }\n";
        List<LspDiagnostic> diags = analyzer.diagnostics(src);
        assertEquals(1, diags.size(), diags.toString());
        assertEquals("E1905", diags.get(0).code());
    }
}
