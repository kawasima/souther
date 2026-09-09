package souther.lsp.analysis;

import souther.lsp.protocol.CodeAction;
import souther.lsp.protocol.CompletionItem;
import souther.lsp.protocol.DocumentSymbol;
import souther.lsp.protocol.Hover;
import souther.lsp.protocol.Location;
import souther.lsp.protocol.LspDiagnostic;
import souther.lsp.protocol.Position;
import souther.lsp.protocol.Range;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    /** A warning is all the invariant checker has to say about a construction it cannot prove, so an
     *  editor that shows none leaves the author with nothing to read. */
    @Test
    void anUnprovenConstructionIsReportedAsAWarningWhereItIsWritten() {
        String src = """
                module demo

                data Eaches = Int
                    invariant value >= 0

                behavior wrap : (n: Int) -> Eaches
                    constructs Eaches
                let wrap (n) = {
                    let m = n
                    Eaches(m)
                }
                """;
        List<LspDiagnostic> diags = analyzer.diagnostics(src);

        assertEquals(1, diags.size(), diags.toString());
        LspDiagnostic d = diags.get(0);
        assertEquals(LspDiagnostic.WARNING, d.severity());
        assertEquals("E2011", d.code());
        assertEquals(9, d.range().start().line(), "the construction is on the tenth line");
        assertEquals(4, d.range().start().character());
    }

    @Test
    void aSemanticErrorIsReportedWhenSyntaxIsClean() {
        // a type variable in a user module is a semantic error the compiler raises
        String src = "module demo\ndata X = { v: Int }\nlet f (x: 'a) = x\n";
        List<LspDiagnostic> diags = analyzer.diagnostics(src);
        assertTrue(!diags.isEmpty(), "expected a semantic diagnostic");
        assertTrue(diags.get(0).message().contains("type variable"), diags.get(0).message());
    }

    /**
     * And it is marked where it is written, not at the head of the document.
     *
     * <p>The compile behind this route reads the document without a name for it, so what it reports
     * points into a text this compilation cannot name — real numbers, no file. Which document that
     * is, is what this route knows and nothing else does; left unsaid, the marker has nowhere to go
     * and falls to the first character.
     */
    @Test
    void aSemanticErrorIsMarkedWhereItIsWritten() {
        String src = "module demo\ndata X = { v: Int }\nlet f (x: 'a) = x\n";

        List<LspDiagnostic> diags = analyzer.diagnostics(src);

        assertEquals(2, diags.get(0).range().start().line(),
                "the third line, which is where the type variable is written: " + diags.get(0));
    }

    @Test
    void formatReturnsCanonicalTextForACleanDocument() {
        // a one-line product def is reflowed into the multi-line leading-comma block
        Optional<String> formatted = analyzer.format("module demo\ndata X = { a: Int }\n");
        assertTrue(formatted.isPresent(), "a clean document formats");
        assertTrue(formatted.get().contains("{ a: Int"), formatted.get());
        assertEquals(formatted.get(), analyzer.format(formatted.get()).orElseThrow(),
                "formatting is idempotent");
    }

    @Test
    void formatIsEmptyForADocumentWithASyntaxError() {
        // `name String` is missing the `:`; the formatter needs a clean parse, so no formatting
        assertTrue(analyzer.format("module demo\ndata M = { name String }\n").isEmpty());
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

    @Test
    void aLambdaParameterIsStillAParameterNowThatItIsAPattern() {
        // the parameter of `x -> e` moved under a pattern node; classification reads the node an
        // identifier sits in, so without looking past the pattern every lambda parameter in every
        // file would highlight as an ordinary local
        int[] data = analyzer.semanticTokens(
                "module demo\ndata X = { a: Int }\nlet f (xs: List<Int>) = List.map(n -> n, xs)\n");
        List<int[]> tokens = decodeSemanticTokens(data);

        assertEquals(3, typeAt(tokens, 2, 33), "the lambda's `n` is a parameter (index 3)");
    }

    @Test
    void aNameATuplePatternBindsIsALocal() {
        // the same pattern node in a `let` binds locals, not parameters
        int[] data = analyzer.semanticTokens(
                "module demo\ndata X = { a: Int }\nlet f (p: (Int, Int)) = {\nlet (q, r) = p\nq\n}\n");
        List<int[]> tokens = decodeSemanticTokens(data);

        assertEquals(4, typeAt(tokens, 3, 5), "`q` is a variable (index 4)");
        assertEquals(4, typeAt(tokens, 3, 8), "`r` is a variable (index 4)");
    }

    @Test
    void aRecordPatternsFieldNameIsAProperty() {
        int[] data = analyzer.semanticTokens(
                "module demo\ndata X = { a: Int }\nlet f (x: X) = {\nlet { a = n } = x\nn\n}\n");
        List<int[]> tokens = decodeSemanticTokens(data);

        assertEquals(5, typeAt(tokens, 3, 6), "`a` names the field, so it is a property (index 5)");
        assertEquals(4, typeAt(tokens, 3, 10), "`n` is the name it binds, a variable (index 4)");
    }

    /**
     * What an application applies is the function, and a qualified callee is a read whose last name
     * is the one. Every application is now an argument list written after an expression (issue
     * #274), so a call is no longer a node of its own to classify by — the callee position is
     * carried down instead, to the field a read takes and not to what it is taken off.
     */
    @Test
    void whatAnApplicationAppliesIsTheFunctionAndTheQualifierInFrontOfItIsNot() {
        //                     0         1         2         3         4
        //                     0123456789012345678901234567890123456789012345
        String body = "let g (d: D, xs: List<Int>) = List.map(f, xs)\n";
        int[] data = analyzer.semanticTokens(
                "module demo\ndata D = { n: Int }\nlet f (x: Int) = x\n" + body);
        List<int[]> tokens = decodeSemanticTokens(data);

        assertEquals(6, typeAt(tokens, 3, 35), "`map` is what is applied, so it is a function (index 6)");
        assertEquals(4, typeAt(tokens, 3, 30), "`List` is the qualifier, not the function");
        assertEquals(4, typeAt(tokens, 3, 39), "`f` is an argument, not what is applied");
    }

    @Test
    void aBareCalleeIsAFunctionAndAFieldReadThatIsNotAppliedIsAProperty() {
        //                     0         1         2         3
        //                     012345678901234567890123456789012345
        String body = "let g (d: D) = f(d.n)\n";
        int[] data = analyzer.semanticTokens(
                "module demo\ndata D = { n: Int }\nlet f (x: Int) = x\n" + body);
        List<int[]> tokens = decodeSemanticTokens(data);

        assertEquals(6, typeAt(tokens, 3, 15), "`f` is what is applied (index 6)");
        assertEquals(4, typeAt(tokens, 3, 17), "`d` is what the read is taken off, a variable here");
        assertEquals(5, typeAt(tokens, 3, 19), "`n` is a field read, not a function (index 5)");
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

    /** `data`, `invariant` on the next line, one clause per line so a position picks one out. */
    private static final String CLAUSES_SRC = """
            module demo
            data Money = Int
                invariant value >= 0
            data Code = String
                invariant String.matches("[A-Z]{2}", value)
            data Sum = Int
                invariant Int.sum([value]) >= 0
            """;

    private Optional<Hover> clauseHover(int line, int character) {
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///demo.sou", CLAUSES_SRC));
        return analyzer.hover("file:///demo.sou", CLAUSES_SRC, new Position(line, character), graph);
    }

    @Test
    void hoverOnANumericClauseSaysItIsDerivable() {
        // `value >= 0` — a relation the domain reasons over, so any guard implying it discharges
        Optional<Hover> hover = clauseHover(2, 18);
        assertTrue(hover.isPresent());
        assertTrue(hover.get().contents().contains("derivable"), hover.get().contents());
    }

    @Test
    void hoverOnAPatternClauseSaysItTakesAnExactMatch() {
        // `String.matches(...)` — nameable but not a relation, so only the same guard discharges it
        Optional<Hover> hover = clauseHover(4, 20);
        assertTrue(hover.isPresent());
        assertTrue(hover.get().contents().contains("exact match"), hover.get().contents());
    }

    /**
     * A clause hover asks the compiler what it can discharge, keyed by the type the clause is on —
     * so the name the syntax reads has to be the name the compiler filed it under.
     *
     * <p>A decomposed declaration is filed under its composed name, and reading the token's
     * characters straight into the key missed it. What an author saw was a hover that showed the
     * signature instead of the discharge, which reads as "this clause has nothing to say".
     */
    @Test
    void hoverOnAClauseOfADecomposedlyNamedDataStillSaysHowItDischarges() {
        String kana = new String(new int[] {0x304b, 0x3099}, 0, 2);
        String source = "module demo exposing ( " + kana + " )\n\ndata " + kana + " = Int\n"
                + "    invariant value >= 0\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///demo.sou", source));

        Optional<Hover> hover = analyzer.hover("file:///demo.sou", source,
                new Position(3, "    invariant ".length()), graph);

        assertTrue(hover.isPresent());
        assertTrue(hover.get().contents().contains("derivable"), hover.get().contents());
    }

    @Test
    void hoverOutsideAnInvariantStillShowsTheSignature() {
        Optional<Hover> hover = clauseHover(1, 5);   // over `Money`
        assertTrue(hover.isPresent());
        assertTrue(hover.get().contents().contains("data Money"), hover.get().contents());
    }

    @Test
    void diagnosticsAcrossModulesLandOnTheOwningFile() {
        String a = "module a exposing ( N )\ndata N = { v: Int }\n";
        // `Held` writes the imported name, which is what makes b depend on a; an import nothing
        // writes would be reported as unused, and that is not what this test is about.
        String b = "module b\nimport a ( N )\ndata Held = { it: N }\ndata M = { n: Int }\n"
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
        String b = "module b\nimport a ( N )\ndata Held = { it: N }\ndata M = { n: Int }\n"
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

    @Test
    void renameEditsCoverEveryUseOfTheSymbolAcrossModules() {
        String a = "module a exposing ( N )\ndata N = { v: Int }\n";
        String b = "module b\nimport a ( N )\nbehavior f : (n: N) -> N\nlet f (n) = n\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a, "file:///b.sou", b));

        // cursor on the `data N` declaration in a (line 1, char 5)
        java.util.Map<String, List<souther.lsp.protocol.TextEdit>> edits =
                analyzer.renameEdits("file:///a.sou", new Position(1, 5), graph, "Renamed");

        assertEquals(java.util.Set.of("file:///a.sou", "file:///b.sou"), edits.keySet(), edits.toString());
        // a: the `exposing ( N )` entry (line 0) and the `data N` declaration (line 1)
        assertEquals(2, edits.get("file:///a.sou").size(), "exposing entry + declaration in a: " + edits);
        // b: the `import a ( N )` entry (line 1) and both type uses (line 2)
        assertEquals(3, edits.get("file:///b.sou").size(), "import entry + both uses in b: " + edits);
    }

    @Test
    void renameEditsIncludeATypeWrittenOnALocalBinding() {
        // a local binding may carry a type annotation (issue #71); renaming the type must reach it,
        // or the rename leaves a dangling name in the body.
        String a = "module a\n"
                + "data N = { v: Int }\n"
                + "behavior f : (n: N) -> N\n"
                + "let f (n) = {\n"
                + "let kept: N = n\n"
                + "kept\n"
                + "}\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a));

        // cursor on the `data N` declaration (line 1, char 5)
        java.util.Map<String, List<souther.lsp.protocol.TextEdit>> edits =
                analyzer.renameEdits("file:///a.sou", new Position(1, 5), graph, "Renamed");

        java.util.Set<Integer> lines = new java.util.HashSet<>();
        for (souther.lsp.protocol.TextEdit e : edits.get("file:///a.sou")) {
            lines.add(e.range().start().line());
        }
        assertTrue(lines.contains(4), "the annotation on line 4 is renamed: " + lines);
    }

    @Test
    void renameEditsReachInsideACollectionNewtypeBase() {
        // a newtype's base is a written type, so it may be a collection whose element names a data;
        // renaming that data must reach the element inside `List<...>`
        String a = "module a\n"
                + "data Tag = { v: Int }\n"
                + "data Tags = List<Tag>\n"
                + "behavior f : (t: Tags) -> Tags\n"
                + "let f (t) = t\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a));

        // cursor on the `data Tag` declaration (line 1, char 5)
        java.util.Map<String, List<souther.lsp.protocol.TextEdit>> edits =
                analyzer.renameEdits("file:///a.sou", new Position(1, 5), graph, "Renamed");

        java.util.Set<Integer> lines = new java.util.HashSet<>();
        for (souther.lsp.protocol.TextEdit e : edits.get("file:///a.sou")) {
            lines.add(e.range().start().line());
        }
        assertTrue(lines.contains(2), "the element inside `List<Tag>` on line 2 is renamed: " + lines);
    }

    @Test
    void renameEditsReachTheTypeNamedByABindingPattern() {
        // `let Tags(xs) = t` names a type; renaming the data must reach it, or the rename leaves a
        // pattern that opens a name no longer declared
        String a = "module a\n"
                + "data Tags = List<String>\n"
                + "behavior f : (t: Tags) -> Tags\n"
                + "let f (t) = {\n"
                + "let Tags(xs) = t\n"
                + "t\n"
                + "}\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a));

        java.util.Map<String, List<souther.lsp.protocol.TextEdit>> edits =
                analyzer.renameEdits("file:///a.sou", new Position(1, 5), graph, "Renamed");

        java.util.Set<Integer> lines = new java.util.HashSet<>();
        for (souther.lsp.protocol.TextEdit e : edits.get("file:///a.sou")) {
            lines.add(e.range().start().line());
        }
        assertTrue(lines.contains(4), "the pattern on line 4 is renamed: " + lines);
    }

    @Test
    void everyNameATuplePatternBindsShadowsAnOuterOne() {
        // `let (a, b) = p` binds both names; only the first used to be seen as a binder, so a use of
        // the second was reported as a reference to the outer symbol it shadows
        String a = "module a\n"
                + "let b (n: Int) = n\n"
                + "behavior f : (i: Int) -> Int\n"
                + "let f (i) = {\n"
                + "let (a, b) = (1, 2)\n"
                + "b\n"
                + "}\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a));

        // cursor on the top-level `let b` declaration (line 1, char 4)
        List<Location> refs = analyzer.references("file:///a.sou", new Position(1, 4), graph, false);

        for (Location l : refs) {
            assertTrue(l.range().start().line() != 5,
                    "line 5 uses the `b` the tuple pattern bound, not the helper: " + refs);
        }
    }

    @Test
    void renameEditsIncludeExposingAndImportBindingSites() {
        String a = "module a exposing ( N )\ndata N = { v: Int }\n";
        String b = "module b\nimport a ( N )\nbehavior f : (n: N) -> N\nlet f (n) = n\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///a.sou", a, "file:///b.sou", b));

        java.util.Map<String, List<souther.lsp.protocol.TextEdit>> edits =
                analyzer.renameEdits("file:///a.sou", new Position(1, 5), graph, "Renamed");

        java.util.Set<Integer> aLines = new java.util.HashSet<>();
        for (souther.lsp.protocol.TextEdit e : edits.get("file:///a.sou")) {
            aLines.add(e.range().start().line());
        }
        assertTrue(aLines.contains(0), "the exposing clause on line 0 is renamed: " + aLines);

        java.util.Set<Integer> bLines = new java.util.HashSet<>();
        for (souther.lsp.protocol.TextEdit e : edits.get("file:///b.sou")) {
            bLines.add(e.range().start().line());
        }
        assertTrue(bLines.contains(1), "the import list on line 1 is renamed: " + bLines);
    }

    @Test
    void isValidNameAcceptsIdentifiersAndRejectsGarbage() {
        assertTrue(analyzer.isValidName("従業員ID"), "a non-ASCII identifier is valid");
        assertTrue(analyzer.isValidName("foo"));
        assertTrue(!analyzer.isValidName(""), "empty is rejected");
        assertTrue(!analyzer.isValidName("a b"), "two tokens are rejected");
        assertTrue(!analyzer.isValidName("data"), "a keyword is not an identifier");
    }

    @Test
    void completionsOfferKeywordsTopLevelNamesImportsAndLocals() {
        String text = "module demo\n"
                + "import String ( length )\n"
                + "data Thing = { v: Int }\n"
                + "behavior f : (x: Thing) -> Thing\n"
                + "let f (x) = x\n";
        // cursor in the body of `let f (x) = x` (line 4, on the trailing `x`)
        List<CompletionItem> items = analyzer.completions("file:///demo.sou", new Position(4, 12),
                ModuleGraph.of(java.util.Map.of("file:///demo.sou", text)));

        java.util.Set<String> labels = new java.util.HashSet<>();
        for (CompletionItem i : items) {
            labels.add(i.label());
        }
        assertTrue(labels.contains("behavior"), "a language keyword: " + labels);
        assertTrue(labels.contains("Thing"), "the data type defined in the file: " + labels);
        assertTrue(labels.contains("f"), "the function name: " + labels);
        assertTrue(labels.contains("length"), "an imported name: " + labels);
        assertTrue(labels.contains("x"), "the param in scope: " + labels);
    }

    @Test
    void codeActionSuggestsReplacingAMisspelledName() {
        // `valuee` in the body is a typo of the param `value`; the compiler carries a did-you-mean
        String text = "module demo\nbehavior f : (value: Int) -> Int\nlet f (value) = valuee\n";
        Range onTheTypo = new Range(new Position(2, 16), new Position(2, 22));
        List<CodeAction> actions = analyzer.codeActions("file:///m.sou", text, onTheTypo,
                ModuleGraph.of(java.util.Map.of("file:///m.sou", text)));

        assertEquals(1, actions.size(), actions.toString());
        assertEquals("Replace with 'value'", actions.get(0).title());
        assertEquals("value",
                assertInstanceOf(CodeAction.Applied.class, actions.get(0)).edit().newText());
    }

    @Test
    void codeActionIsEmptyWhenTheRangeIsAwayFromTheError() {
        String text = "module demo\nbehavior f : (value: Int) -> Int\nlet f (value) = valuee\n";
        ModuleGraph graph = ModuleGraph.of(java.util.Map.of("file:///m.sou", text));
        // Beside the range that does offer one: an emptiness on its own is the same answer a
        // request that failed altogether gives.
        Range onTheTypo = new Range(new Position(2, 16), new Position(2, 22));
        assertEquals(1, analyzer.codeActions("file:///m.sou", text, onTheTypo, graph).size(),
                "there is an offer in this document to go missing");
        Range onTheHeader = new Range(new Position(0, 0), new Position(0, 5));
        assertEquals(List.of(), analyzer.codeActions("file:///m.sou", text, onTheHeader, graph));
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

    @Test
    void everyFailingInlineExampleRowIsSquiggled() {
        // the single-file path used to keep only the first failure the compile threw, so a second
        // failing row was left unmarked in the editor (issue #98)
        String src = "module demo\n"
                + "data M = { n: Int }\n"
                + "behavior f : (x: M) -> M\n"
                + "let f (x) = x\n"
                + "example f\n"
                + "  | (M { n = 1 }) -> M { n = 2 }\n"
                + "  | (M { n = 3 }) -> M { n = 4 }\n";
        List<LspDiagnostic> diags = analyzer.diagnostics(src);
        assertEquals(2, diags.size(), diags.toString());
        assertNotEquals(diags.get(0).range().start().line(), diags.get(1).range().start().line(),
                "each marks its own row: " + diags);
    }
}
