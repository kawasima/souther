package net.unit8.souther.lsp.analysis;

import net.unit8.souther.compiler.Compiler;
import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.cst.CstError;
import net.unit8.souther.compiler.cst.CstParser;
import net.unit8.souther.compiler.cst.LineIndex;
import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.diag.Diagnostic;
import net.unit8.souther.compiler.diag.DiagnosticRenderer;
import net.unit8.souther.compiler.diag.Region;
import net.unit8.souther.compiler.diag.SourcePos;
import net.unit8.souther.compiler.cst.SyntaxElement;
import net.unit8.souther.compiler.cst.SyntaxKind;
import net.unit8.souther.compiler.cst.SyntaxNode;
import net.unit8.souther.compiler.cst.SyntaxToken;
import net.unit8.souther.compiler.frontend.CstFrontend;
import net.unit8.souther.lsp.protocol.DocumentSymbol;
import net.unit8.souther.lsp.protocol.Hover;
import net.unit8.souther.lsp.protocol.Location;
import net.unit8.souther.lsp.protocol.LspDiagnostic;
import net.unit8.souther.lsp.protocol.Position;
import net.unit8.souther.lsp.protocol.Range;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The language-analysis core, independent of the LSP transport: pure functions from source text to
 * the data an editor asks for. Diagnostics come from the recovering CST parser (all syntax errors)
 * and, when the syntax is clean, a best-effort compile that surfaces the first semantic error
 * (the type checker does not recover yet, ADR-deferred).
 */
public final class Analyzer {

    /** All diagnostics for a document: every syntax error, or — when there are none — the first
     * semantic error a compile turns up. */
    public List<LspDiagnostic> diagnostics(String text) {
        LineIndex lines = new LineIndex(text);
        List<LspDiagnostic> out = new ArrayList<>();

        CstParser.Result parsed = CstParser.parse(text);
        for (CstError e : parsed.errors()) {
            out.add(new LspDiagnostic(range(lines, e.offset(), e.offset() + e.width()),
                    LspDiagnostic.ERROR, null, e.legacyMessage()));
        }
        if (!out.isEmpty()) {
            return out;   // don't chase semantics through a broken parse
        }

        try {
            Ast.Module module = CstFrontend.parse(text, "Main");
            if (!module.imports().isEmpty()) {
                return out;   // a multi-module program can't be resolved from a single file yet
            }
            if (module.exampleFileTarget() != null) {
                return out;   // an `examples for` file needs its target module, absent from one file
            }
            // A self-contained module compiles fully here, so its inline `example`s are evaluated
            // on save and a failing one (E1805) surfaces as an editor diagnostic.
            Compiler.compile(text, "Main");
        } catch (CompileException e) {
            out.add(fromCompile(lines, e));
        } catch (RuntimeException e) {
            // analysis must never crash the server; a non-diagnostic failure yields no marker
        }
        return out;
    }

    /**
     * Diagnostics for every file in a workspace, resolved across imports the way the batch compiler
     * links a module set. Each file's syntax errors come from its own recovering parse; a file with a
     * syntax error stays out of the shared compile (it cannot join the graph). The remaining files are
     * compiled together, and each semantic diagnostic is published on the file of the module that owns
     * it — so an error in an imported module lands on that module's document, not on its importer.
     */
    public Map<String, List<LspDiagnostic>> diagnostics(ModuleGraph graph) {
        Map<String, List<LspDiagnostic>> out = new LinkedHashMap<>();
        Map<String, String> compileSet = new LinkedHashMap<>();   // uri -> text, syntactically clean only
        Set<String> brokenModules = new HashSet<>();   // names of files held out for their syntax errors

        for (String uri : graph.uris()) {
            String text = graph.text(uri);
            LineIndex lines = new LineIndex(text);
            List<LspDiagnostic> syntax = new ArrayList<>();
            for (CstError e : CstParser.parse(text).errors()) {
                syntax.add(new LspDiagnostic(range(lines, e.offset(), e.offset() + e.width()),
                        LspDiagnostic.ERROR, null, e.legacyMessage()));
            }
            out.put(uri, syntax);
            if (syntax.isEmpty()) {
                compileSet.put(uri, text);   // a syntactically broken file cannot join the compile
            } else {
                String name = Compiler.moduleNameFromHeader(text);
                if (name != null) {
                    brokenModules.add(name);   // present but unparseable; importers skip, not cascade
                }
            }
        }

        Map<String, List<Diagnostic>> byUri;
        try {
            byUri = Compiler.diagnoseModules(compileSet, brokenModules);
        } catch (RuntimeException e) {
            return out;   // analysis must never crash the server
        }
        for (Map.Entry<String, List<Diagnostic>> e : byUri.entrySet()) {
            List<LspDiagnostic> list = out.get(e.getKey());
            if (list == null) {
                continue;
            }
            LineIndex lines = new LineIndex(graph.text(e.getKey()));
            for (Diagnostic d : e.getValue()) {
                list.add(fromDiagnostic(lines, d));
            }
        }
        return out;
    }

    /**
     * Go-to-definition across the workspace: resolves the identifier under the cursor to the document
     * and name range of the top-level definition it names. A name defined in the current file resolves
     * locally; otherwise it is resolved through the file's imports to the module that exposes it, and
     * the target's own document supplies the range.
     */
    public Optional<Location> definition(String uri, Position pos, ModuleGraph graph) {
        String text = graph.text(uri);
        if (text == null) {
            return Optional.empty();
        }
        LineIndex lines = new LineIndex(text);
        SyntaxNode root = CstParser.parse(text).root();
        SyntaxToken ident = identAt(root, lines.offsetOf(pos.line(), pos.character()));
        if (ident == null) {
            return Optional.empty();
        }
        SyntaxNode local = declaringDef(root, ident.text());
        if (local != null) {
            SyntaxToken name = nameToken(local);
            return name == null ? Optional.empty() : Optional.of(new Location(uri, tokenRange(lines, name)));
        }
        String targetModule = importedFrom(text, ident.text());
        if (targetModule == null) {
            return Optional.empty();
        }
        String targetUri = uriOfModule(graph, targetModule);
        if (targetUri == null) {
            return Optional.empty();
        }
        String targetText = graph.text(targetUri);
        SyntaxNode def = declaringDef(CstParser.parse(targetText).root(), ident.text());
        if (def == null) {
            return Optional.empty();
        }
        SyntaxToken name = nameToken(def);
        return name == null ? Optional.empty()
                : Optional.of(new Location(targetUri, tokenRange(new LineIndex(targetText), name)));
    }

    /**
     * Find-references across the workspace: every use of the top-level symbol the cursor names, in the
     * defining module and in every module that imports it. Resolution respects namespaces — a type
     * mention and a value of the same spelling are different symbols — and scope: a use inside a
     * definition that binds the name locally (a param or a {@code let}) is that local, not the symbol,
     * and is excluded. The declaration itself is included only when {@code includeDeclaration} is set.
     */
    public List<Location> references(String uri, Position pos, ModuleGraph graph, boolean includeDeclaration) {
        String text = graph.text(uri);
        if (text == null) {
            return List.of();
        }
        SyntaxNode root = CstParser.parse(text).root();
        SyntaxToken ident = identAt(root, new LineIndex(text).offsetOf(pos.line(), pos.character()));
        if (ident == null) {
            return List.of();
        }
        String name = ident.text();
        String definingModule = declaringDef(root, name) != null
                ? Compiler.moduleNameFromHeader(text) : importedFrom(text, name);
        if (definingModule == null) {
            return List.of();
        }
        String definingUri = uriOfModule(graph, definingModule);
        if (definingUri == null) {
            return List.of();
        }
        boolean isType = isTypeDef(declaringDef(CstParser.parse(graph.text(definingUri)).root(), name));

        List<Location> out = new ArrayList<>();
        for (String u : graph.uris()) {
            String t = graph.text(u);
            boolean owns = definingModule.equals(Compiler.moduleNameFromHeader(t));
            if (!owns && !definingModule.equals(importedFrom(t, name))) {
                continue;   // this file neither defines nor imports the symbol
            }
            collectReferences(CstParser.parse(t).root(), name, isType, u, new LineIndex(t),
                    includeDeclaration && owns, out);
        }
        return out;
    }

    private boolean isTypeDef(SyntaxNode def) {
        return def != null && def.kind() == SyntaxKind.DATA_DEF;
    }

    /** Node kinds where an identifier names a type. */
    private static final java.util.Set<SyntaxKind> TYPE_POSITIONS = java.util.Set.of(
            SyntaxKind.TYPE_REF, SyntaxKind.TYPE_ARGS, SyntaxKind.SUM_BODY, SyntaxKind.NEWTYPE_BODY,
            SyntaxKind.CONSTRUCTS_CLAUSE, SyntaxKind.REQUIRES_CLAUSE, SyntaxKind.NEW_DATA_EXPR);

    /** Node kinds where an identifier binds a value name (a param or a {@code let}). */
    private static final java.util.Set<SyntaxKind> VALUE_BINDINGS = java.util.Set.of(
            SyntaxKind.PARAM, SyntaxKind.FN_PARAM, SyntaxKind.LAMBDA_EXPR, SyntaxKind.LET_STMT);

    /** Appends every occurrence of {@code name} in {@code root} that refers to the target symbol. A
     * value use inside a top-level definition that binds {@code name} locally is shadowed and skipped. */
    private void collectReferences(SyntaxNode root, String name, boolean isType, String uri,
                                   LineIndex lines, boolean includeDeclaration, List<Location> out) {
        for (SyntaxNode def : root.childNodes()) {
            if (def.kind() == SyntaxKind.MODULE_HEADER || def.kind() == SyntaxKind.IMPORT_DECL) {
                continue;   // names in the header's exposing list or an import list are not uses
            }
            boolean shadows = !isType && defBindsName(def, name);
            collectInNode(def, name, isType, uri, lines, includeDeclaration, shadows, out);
        }
    }

    private void collectInNode(SyntaxNode node, String name, boolean isType, String uri, LineIndex lines,
                               boolean includeDeclaration, boolean shadowed, List<Location> out) {
        SyntaxKind parent = node.kind();
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxNode child) {
                collectInNode(child, name, isType, uri, lines, includeDeclaration, shadowed, out);
            } else {
                SyntaxToken t = (SyntaxToken) e;
                if (t.kind() != SyntaxKind.IDENT || !t.text().equals(name)) {
                    continue;
                }
                if (isDeclarationName(node, t)) {
                    if (includeDeclaration) {
                        out.add(new Location(uri, tokenRange(lines, t)));
                    }
                } else if (isType) {
                    if (TYPE_POSITIONS.contains(parent)) {
                        out.add(new Location(uri, tokenRange(lines, t)));
                    }
                } else if (!VALUE_BINDINGS.contains(parent) && !TYPE_POSITIONS.contains(parent) && !shadowed) {
                    out.add(new Location(uri, tokenRange(lines, t)));
                }
            }
        }
    }

    /** Whether {@code t} is the name token of the top-level definition {@code node}. */
    private boolean isDeclarationName(SyntaxNode node, SyntaxToken t) {
        return (node.kind() == SyntaxKind.DATA_DEF || node.kind() == SyntaxKind.BEHAVIOR_DEF
                || node.kind() == SyntaxKind.FN_DEF) && t == nameToken(node);
    }

    /** Whether a top-level definition binds {@code name} as a param or a {@code let} anywhere inside it. */
    private boolean defBindsName(SyntaxNode def, String name) {
        for (SyntaxElement e : def.children()) {
            if (e instanceof SyntaxNode child) {
                if (VALUE_BINDINGS.contains(child.kind())) {
                    SyntaxToken bound = firstIdent(child);
                    if (bound != null && bound.text().equals(name)) {
                        return true;
                    }
                }
                if (defBindsName(child, name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private SyntaxToken firstIdent(SyntaxNode node) {
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                return t;
            }
            if (e instanceof SyntaxNode child) {
                SyntaxToken inner = firstIdent(child);
                if (inner != null) {
                    return inner;
                }
            }
        }
        return null;
    }

    /** The module a name is imported from in {@code text}, or {@code null} if no import exposes it. */
    private String importedFrom(String text, String name) {
        try {
            for (Ast.Import imp : CstFrontend.parse(text, "Main").imports()) {
                if (imp.names().contains(name)) {
                    return imp.module();
                }
            }
        } catch (RuntimeException e) {
            // a file that does not parse cleanly resolves no imports; go-to-def simply misses
        }
        return null;
    }

    /** The document URI of the module declared with {@code moduleName}, or {@code null} if none. */
    private String uriOfModule(ModuleGraph graph, String moduleName) {
        for (String uri : graph.uris()) {
            if (moduleName.equals(Compiler.moduleNameFromHeader(graph.text(uri)))) {
                return uri;
            }
        }
        return null;
    }

    /** Go-to-definition within one document: resolves the identifier under the cursor to the name
     * range of the top-level definition it names, if any. The workspace-aware
     * {@link #definition(String, Position, ModuleGraph)} resolves across imports as well. */
    public Optional<Range> definition(String text, Position pos) {
        LineIndex lines = new LineIndex(text);
        SyntaxNode root = CstParser.parse(text).root();
        SyntaxToken ident = identAt(root, lines.offsetOf(pos.line(), pos.character()));
        if (ident == null) {
            return Optional.empty();
        }
        SyntaxNode def = declaringDef(root, ident.text());
        if (def == null) {
            return Optional.empty();
        }
        SyntaxToken name = nameToken(def);
        return name == null ? Optional.empty() : Optional.of(tokenRange(lines, name));
    }

    /** Hover: shows the signature line of the definition the identifier under the cursor names. */
    public Optional<Hover> hover(String text, Position pos) {
        LineIndex lines = new LineIndex(text);
        SyntaxNode root = CstParser.parse(text).root();
        int offset = lines.offsetOf(pos.line(), pos.character());
        SyntaxToken ident = identAt(root, offset);
        if (ident == null) {
            return Optional.empty();
        }
        String signature;
        SyntaxNode parent = ident.parent();
        if (parent != null && (parent.kind() == SyntaxKind.FIELD
                || parent.kind() == SyntaxKind.PARAM || parent.kind() == SyntaxKind.FN_PARAM)) {
            // a declaration site: show the binding's own `name: Type`, read straight from the tree
            signature = signatureLine(text, parent);
        } else {
            SyntaxNode def = declaringDef(root, ident.text());
            signature = def != null ? signatureLine(text, def) : ident.text();
        }
        String contents = "```souther\n" + signature + "\n```";
        return Optional.of(new Hover(contents, tokenRange(lines, ident)));
    }

    /** The definition's first source line, from its first real token (leading comments dropped). */
    private String signatureLine(String text, SyntaxNode def) {
        SyntaxToken first = firstMeaningfulToken(def);
        if (first == null) {
            return def.text().strip();
        }
        int start = first.start();
        int newline = text.indexOf('\n', start);
        int end = newline < 0 ? def.end() : Math.min(newline, def.end());
        return text.substring(start, end).strip();
    }

    private SyntaxNode declaringDef(SyntaxNode file, String name) {
        for (SyntaxNode def : file.childNodes()) {
            if (def.kind() == SyntaxKind.DATA_DEF || def.kind() == SyntaxKind.BEHAVIOR_DEF
                    || def.kind() == SyntaxKind.FN_DEF) {
                SyntaxToken n = nameToken(def);
                if (n != null && n.text().equals(name)) {
                    return def;
                }
            }
        }
        return null;
    }

    /** The identifier token covering {@code offset}, descending only into the node that contains it. */
    private SyntaxToken identAt(SyntaxNode node, int offset) {
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxNode child) {
                if (offset >= child.start() && offset < child.end()) {
                    SyntaxToken found = identAt(child, offset);
                    if (found != null) {
                        return found;
                    }
                }
            } else {
                SyntaxToken t = (SyntaxToken) e;
                if (t.kind() == SyntaxKind.IDENT && offset >= t.start() && offset < t.end()) {
                    return t;
                }
            }
        }
        return null;
    }

    private SyntaxToken firstMeaningfulToken(SyntaxNode node) {
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia()) {
                return t;
            }
            if (e instanceof SyntaxNode child) {
                SyntaxToken inner = firstMeaningfulToken(child);
                if (inner != null) {
                    return inner;
                }
            }
        }
        return null;
    }

    /** The semantic-token legend, in index order. Declared to the client in the server capabilities
     * and referenced by the numbers in {@link #semanticTokens}. */
    public static final List<String> TOKEN_TYPES = List.of(
            "namespace", "type", "typeParameter", "parameter", "variable", "property",
            "function", "keyword", "string", "number", "comment", "operator");

    private static final int T_NAMESPACE = 0;
    private static final int T_TYPE = 1;
    private static final int T_TYPEPARAM = 2;
    private static final int T_PARAMETER = 3;
    private static final int T_VARIABLE = 4;
    private static final int T_PROPERTY = 5;
    private static final int T_FUNCTION = 6;
    private static final int T_KEYWORD = 7;
    private static final int T_STRING = 8;
    private static final int T_NUMBER = 9;
    private static final int T_COMMENT = 10;
    private static final int T_OPERATOR = 11;

    /**
     * The document's semantic tokens as the LSP delta-encoded {@code data} array (five integers per
     * token: {@code deltaLine, deltaStartChar, length, tokenType, tokenModifiers}). Classification
     * reads the CST — an identifier's role (a type, a parameter, a call target) comes from its parent
     * node — which a regex grammar cannot see, so this is where highlighting gains precision over the
     * TextMate grammar. Multi-line tokens (a string literal spanning lines) are dropped, since a
     * semantic token may not cross a line.
     */
    public int[] semanticTokens(String text) {
        LineIndex lines = new LineIndex(text);
        SyntaxNode root = CstParser.parse(text).root();
        List<int[]> tokens = new ArrayList<>();
        collectTokens(root, lines, tokens);

        int[] data = new int[tokens.size() * 5];
        int prevLine = 0;
        int prevChar = 0;
        int i = 0;
        for (int[] t : tokens) {
            int deltaLine = t[0] - prevLine;
            int deltaChar = deltaLine == 0 ? t[1] - prevChar : t[1];
            data[i++] = deltaLine;
            data[i++] = deltaChar;
            data[i++] = t[2];
            data[i++] = t[3];
            data[i++] = 0;   // no modifiers
            prevLine = t[0];
            prevChar = t[1];
        }
        return data;
    }

    /** Pre-order walk, appending {@code {line, startChar, length, tokenType}} for each classifiable
     * token in source order. */
    private void collectTokens(SyntaxNode node, LineIndex lines, List<int[]> out) {
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxNode child) {
                collectTokens(child, lines, out);
            } else {
                SyntaxToken token = (SyntaxToken) e;
                int type = classify(token, node.kind());
                if (type < 0) {
                    continue;
                }
                int line = lines.lspLine(token.start());
                if (line != lines.lspLine(token.end())) {
                    continue;   // a semantic token may not span lines
                }
                out.add(new int[]{line, lines.lspColumn(token.start()),
                        token.end() - token.start(), type});
            }
        }
    }

    /** The token type index for a leaf, or {@code -1} to emit nothing (punctuation, whitespace). */
    private int classify(SyntaxToken token, SyntaxKind parent) {
        SyntaxKind k = token.kind();
        if (k == SyntaxKind.LINE_COMMENT) {
            return T_COMMENT;
        }
        if (k == SyntaxKind.STRING_LIT) {
            return T_STRING;
        }
        if (k == SyntaxKind.INT_LIT || k == SyntaxKind.DECIMAL_LIT) {
            return T_NUMBER;
        }
        if (k == SyntaxKind.TYPEVAR) {
            return T_TYPEPARAM;
        }
        if (isKeyword(k)) {
            return T_KEYWORD;
        }
        if (isOperator(k)) {
            return T_OPERATOR;
        }
        if (k == SyntaxKind.IDENT) {
            return classifyIdent(parent);
        }
        return -1;   // braces, parens, commas, colons, dots
    }

    private int classifyIdent(SyntaxKind parent) {
        return switch (parent) {
            case TYPE_REF, TYPE_ARGS, SUM_BODY, NEWTYPE_BODY, CONSTRUCTS_CLAUSE, REQUIRES_CLAUSE,
                 DATA_DEF, NEW_DATA_EXPR -> T_TYPE;
            case BEHAVIOR_DEF, FN_DEF, STAGE, CALL_EXPR -> T_FUNCTION;
            case PARAM, FN_PARAM, LAMBDA_EXPR -> T_PARAMETER;
            case FIELD, FIELD_INIT, FIELD_ACCESS -> T_PROPERTY;
            case MODULE_HEADER, QUALIFIED_NAME, IMPORT_DECL -> T_NAMESPACE;
            default -> T_VARIABLE;
        };
    }

    private static boolean isKeyword(SyntaxKind k) {
        return switch (k) {
            case MODULE_KW, IMPORT_KW, EXPOSING_KW, DATA_KW, INVARIANT_KW, AS_KW, LET_KW, REQUIRE_KW,
                 ELSE_KW, TRUE_KW, FALSE_KW, IF_KW, THEN_KW, BEHAVIOR_KW, REQUIRES_KW, CONSTRUCTS_KW,
                 MATCH_KW, WITH_KW -> true;
            default -> false;
        };
    }

    private static boolean isOperator(SyntaxKind k) {
        return switch (k) {
            case EQ, NE, LT, LE, GT, GE, AND, OR, PLUS, MINUS, STAR, SLASH, PLUSPLUS, ARROW, LARROW,
                 PIPEFWD, VPIPE, PIPE -> true;
            default -> false;
        };
    }

    /** The document outline: one symbol per top-level definition, a data type's fields as children. */
    public List<DocumentSymbol> documentSymbols(String text) {
        LineIndex lines = new LineIndex(text);
        SyntaxNode file = CstParser.parse(text).root();
        List<DocumentSymbol> out = new ArrayList<>();
        for (SyntaxNode def : file.childNodes()) {
            switch (def.kind()) {
                case DATA_DEF -> out.add(dataSymbol(lines, def));
                case BEHAVIOR_DEF -> symbol(lines, def, DocumentSymbol.INTERFACE).ifPresent(out::add);
                case FN_DEF -> symbol(lines, def, DocumentSymbol.FUNCTION).ifPresent(out::add);
                default -> { /* module header, imports, error nodes */ }
            }
        }
        return out;
    }

    private DocumentSymbol dataSymbol(LineIndex lines, SyntaxNode def) {
        SyntaxToken name = nameToken(def);
        List<DocumentSymbol> fields = new ArrayList<>();
        def.child(SyntaxKind.PRODUCT_BODY).ifPresent(body -> {
            for (SyntaxNode member : body.childNodes()) {
                if (member.kind() == SyntaxKind.FIELD) {
                    SyntaxToken fieldName = nameToken(member);
                    if (fieldName != null) {
                        fields.add(new DocumentSymbol(fieldName.text(), DocumentSymbol.FIELD,
                                nodeRange(lines, member), tokenRange(lines, fieldName), List.of()));
                    }
                }
            }
        });
        String label = name != null ? name.text() : "?";
        Range selection = name != null ? tokenRange(lines, name) : nodeRange(lines, def);
        return new DocumentSymbol(label, DocumentSymbol.CLASS, nodeRange(lines, def), selection, fields);
    }

    private Optional<DocumentSymbol> symbol(LineIndex lines, SyntaxNode def, int kind) {
        SyntaxToken name = nameToken(def);
        if (name == null) {
            return Optional.empty();
        }
        return Optional.of(new DocumentSymbol(name.text(), kind,
                nodeRange(lines, def), tokenRange(lines, name), List.of()));
    }

    private SyntaxToken nameToken(SyntaxNode node) {
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                return t;
            }
        }
        return null;
    }

    private Range nodeRange(LineIndex lines, SyntaxNode node) {
        return range(lines, node.start(), node.end());
    }

    private Range tokenRange(LineIndex lines, SyntaxToken token) {
        return range(lines, token.start(), token.end());
    }

    private LspDiagnostic fromCompile(LineIndex lines, CompileException e) {
        Diagnostic d = e.diagnostic();
        if (d != null) {
            return fromDiagnostic(lines, d);
        }
        return new LspDiagnostic(rangeOf(lines, null), LspDiagnostic.ERROR, null,
                cleanMessage(e.getMessage()));
    }

    /** An LSP diagnostic from a structured {@link Diagnostic}: its range from the region, its code
     * from the stable identity, its message rendered from the catalog (or the compatibility literal).
     * A found-vs-expected diff (a type mismatch, a failing example) is appended, since the catalog body
     * alone omits the two values — the detail the editor needs to see what went wrong. */
    private LspDiagnostic fromDiagnostic(LineIndex lines, Diagnostic d) {
        String message = DiagnosticRenderer.body(d, Locale.ENGLISH);
        if (d.diff() != null) {
            message = message + " (expected " + d.diff().expectedType()
                    + ", but was " + d.diff().actualType() + ")";
        }
        return new LspDiagnostic(rangeOf(lines, d), LspDiagnostic.ERROR, d.code(), message);
    }

    private Range rangeOf(LineIndex lines, Diagnostic d) {
        if (d != null && d.region() != null) {
            Region r = d.region();
            return new Range(position(r.start()), position(r.end()));
        }
        if (d != null && d.pos() != null) {
            Position p = position(d.pos());
            return new Range(p, p);
        }
        Position origin = new Position(0, 0);
        return new Range(origin, origin);
    }

    /** A 1-based compiler {@link SourcePos} as a 0-based LSP position. */
    private Position position(SourcePos p) {
        return new Position(Math.max(0, p.line() - 1), Math.max(0, p.column() - 1));
    }

    private Range range(LineIndex lines, int startOffset, int endOffset) {
        return new Range(
                new Position(lines.lspLine(startOffset), lines.lspColumn(startOffset)),
                new Position(lines.lspLine(endOffset), lines.lspColumn(endOffset)));
    }

    /** Strips the {@code line:col} and {@code Ennnn:} prefixes the compiler's message carries, since
     * the LSP conveys the position through the range and the code through its own field. */
    private static String cleanMessage(String message) {
        String m = message.replaceFirst("^\\d+:\\d+ ", "");
        m = m.replaceFirst("^[A-Z]\\d+: ", "");
        return m;
    }
}
