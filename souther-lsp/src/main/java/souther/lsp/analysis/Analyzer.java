package souther.lsp.analysis;

import souther.compiler.source.SourceId;

import souther.compiler.Compiler;
import souther.compiler.check.BehaviorRequirement;
import souther.compiler.check.Prepared;
import souther.compiler.check.Requirements;
import souther.compiler.check.DeclaredSig;
import souther.compiler.check.Sig;
import souther.compiler.check.Resolve;
import souther.compiler.check.SpecImplementation;
import souther.compiler.examples.ExampleProvisioning;
import souther.compiler.query.Abandonment;
import souther.compiler.query.Adequacy;
import souther.compiler.query.ArmSummary;
import souther.compiler.query.Measurement;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;
import souther.compiler.query.ObligationAssessment;
import souther.compiler.query.Shapes;
import souther.compiler.check.CapabilityResult;
import souther.compiler.check.ClauseDischarge;
import souther.compiler.check.FragmentReason;
import souther.compiler.check.RequiredPart;
import souther.compiler.check.StaticRoute;
import souther.compiler.check.ContractDischarge;
import souther.compiler.check.ContractDischarge.RuleDischarge;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;
import souther.compiler.Reserved;
import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.cst.CstError;
import souther.compiler.cst.CstLexer;
import souther.compiler.cst.CstParser;
import souther.compiler.cst.GreenToken;
import souther.compiler.cst.LineIndex;
import souther.compiler.diag.CompileException;
import souther.compiler.editor.EditorSymbols;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.DiagnosticRenderer;
import souther.compiler.diag.DiagnosticPlace;
import souther.compiler.diag.DiagnosticView;
import souther.compiler.diag.Messages;
import souther.compiler.diag.Located;
import souther.compiler.diag.Spot;
import souther.compiler.diag.Region;
import souther.compiler.diag.Primary;
import souther.compiler.diag.UnnamedRegion;
import souther.compiler.diag.ReportContext;
import souther.compiler.diag.SourceContext;
import souther.compiler.diag.Shown;
import souther.compiler.diag.SourcePos;
import souther.compiler.sites.DeclaredParameter;
import souther.compiler.sites.CalledBehavior;
import souther.compiler.sites.MemberReceiver;
import souther.compiler.sites.Published;
import souther.compiler.sites.SemanticSnapshot;
import souther.compiler.cst.SyntaxElement;
import souther.compiler.cst.SyntaxKind;
import souther.compiler.cst.SyntaxNode;
import souther.compiler.cst.SyntaxToken;
import souther.compiler.cst.TopLevelForm;
import souther.compiler.fmt.Formatter;
import souther.compiler.fmt.Skeleton;
import souther.compiler.frontend.CstFrontend;
import souther.lsp.protocol.CodeAction;
import souther.lsp.protocol.CodeLens;
import souther.lsp.protocol.CompletionItem;
import souther.lsp.protocol.DocumentHighlight;
import souther.lsp.protocol.DocumentSymbol;
import souther.lsp.protocol.Hover;
import souther.lsp.protocol.InlayHint;
import souther.lsp.protocol.Location;
import souther.lsp.protocol.LspDiagnostic;
import souther.lsp.protocol.Position;
import souther.lsp.protocol.Range;
import souther.lsp.protocol.SignatureHelp;
import souther.lsp.protocol.TextEdit;
import souther.lsp.protocol.WorkspaceSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    /**
     * The language an editor's markers are written in.
     *
     * <p>The server's policy, in the server, and English rather than resolved. A language server is
     * told which files the workspace holds and nothing about which language its user reads, so there
     * is no answer here to resolve from; reaching for {@code SOUTHER_LANG} would let whichever shell
     * happened to launch the editor decide what the editor shows. Named once, because a marker's
     * text and a linked location's label are the same answer.
     */
    private static final Locale EDITOR_LANGUAGE = Locale.ENGLISH;

    /**
     * The workspace's compile, kept between edits. An answer is recomputed only when something it
     * read has changed, so a keystroke costs what it reached rather than a whole workspace — and a
     * source that is edited back to what it said costs nothing at all.
     *
     * <p>Null until the first workspace-wide diagnose; a single-document analysis does not build
     * one, because it is a compile of one file with nothing else in sight.
     */
    private Compilation workspaceCompile;
    /** Which module path {@link #workspaceCompile} was built for. A different one is a different
     * set of modules to resolve an import against, so the compile is started again. */
    private souther.compiler.meta.ModulePath compiledAgainst;

    /** What each open document reaches from outside itself, as the last compile that could answer
     * said. Held across edits so completion has something to offer while the document being typed in
     * is held out of the compile for its syntax errors. */
    private final NamesFromElsewhere elsewhere = new NamesFromElsewhere();

    /** What the last compile that could answer said each document owes a declaration for. */
    private final LastAnswered<List<CompletionItem>> behaviorsOwed = new LastAnswered<>();

    /**
     * The compile of the document being typed in, finished off where the author has not finished it.
     *
     * <p>Its own store and not the workspace's, and what it keeps is answers about the source it was
     * last given rather than answers to fall back on. Every question is put to the buffer as it
     * stands; an answer from an earlier revision is never handed over because this one could not be
     * reached, which is what {@link LastAnswered} is for and what a receiver must not be read
     * through — the fields of the type that used to be there wear the shape of a right answer.
     */
    private final SemanticProbe probe = new SemanticProbe();

    /**
     * How much of what the rows cover this editor was told to measure. Off by default.
     *
     * <p>Off because measuring costs what it costs and an editor recompiles on every keystroke:
     * `witness` reads what the compile already ran, and `all` generates a second set of classes and
     * runs every row again on every save. Whichever is asked for, it is decided before anything is
     * asked of a compile — the answers are memoised, so a compile cannot be told halfway through.
     */
    private Adequacy.Asked measure = Adequacy.Asked.NOTHING;

    /**
     * Whether the client will come back for an action's edit.
     *
     * <p>False until the handshake says otherwise, which is the protocol's default. What it decides
     * is when the rows are worked out and never whether they are offered: an offer that is worth
     * making is worth making to every client, and one that a client would never resolve has to
     * arrive with its edit or it does nothing.
     */
    private boolean resolvesActions;

    /**
     * What stops the work this analyzer is doing now, short of the answer.
     *
     * <p>Held rather than passed, because what makes the work stop is a property of the unit of work
     * the caller is carrying out and not of any one question inside it: the same value covers the
     * files this reads, the walk the compile makes, and the markers it lays out afterwards. One
     * caller sets it as it begins a unit of work, and every step of that unit asks the same value.
     */
    private Abandonment abandonment = Abandonment.NEVER;

    /**
     * What makes the work this analyzer does from now on stop short of its answer. Set as a unit of
     * work begins, and read by everything that unit reaches — the compile's walk included.
     *
     * <p>Asked at the turn of every walk over the workspace here, and not left to the store's own
     * asking. A question already answered at this revision comes back without the store getting as
     * far as asking, so a loop over every file that reads one each time would go the whole way
     * without being asked once — and a loop over every file that parses one is the work itself.
     */
    public void abandonWhen(Abandonment abandonment) {
        this.abandonment = abandonment;
    }

    /** Whether anything is being measured, which is what decides if an offer can exist where there
     * is no diagnostic to fix. */
    public boolean measuring() {
        return measure.level().readsRows();
    }

    /** What this editor measures from now on. A change starts the workspace compile again, because
     * what it already answered was answered under the old setting. */
    public void measure(Adequacy.Asked asked) {
        if (!this.measure.equals(asked)) {
            this.measure = asked;
            this.workspaceCompile = null;
        }
    }

    /** Whether this client comes back for an action's edit, which the handshake settles. Nothing is
     * recompiled for it: it changes when the rows are worked out, not what they are. */
    public void resolvesActions(boolean asked) {
        this.resolvesActions = asked;
    }

    /** All diagnostics for a document: every syntax error, or — when there are none — the first
     * semantic error a compile turns up, or the warnings a clean compile found. */
    public List<LspDiagnostic> diagnostics(String text) {
        LineIndex lines = new LineIndex(text);
        List<LspDiagnostic> out = new ArrayList<>();

        try {
            out.addAll(syntaxOf(text, lines));
        } catch (RuntimeException | StackOverflowError e) {
            return List.of(internalError(lines, e));   // the parse itself did not finish
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
            for (Located w : Compiler.compileWithWarnings(text, "Main").locatedWarnings()) {
                out.add(fromDiagnostic(text, lines, w.diagnostic()));
            }
        } catch (CompileException e) {
            out.addAll(fromCompile(text, lines, e));
        } catch (RuntimeException | StackOverflowError e) {
            out.add(internalError(lines, e));
        }
        return out;
    }

    /**
     * What the recovering parser found, as the editor reads it.
     *
     * <p>One reader for both routes. The single-file route and the workspace route asked the parser
     * the same question and built the answer twice, which is how one of them came to hand over the
     * code and the other a null: a reader in an editor met a syntax error with nothing to look up,
     * and only in a workspace. What is tested is one route, and what makes that cover the other is
     * that there is only one of these.
     */
    private List<LspDiagnostic> syntaxOf(String text, LineIndex lines) {
        List<LspDiagnostic> found = new ArrayList<>();
        for (CstError<?> e : CstParser.parse(text).errors()) {
            found.add(new LspDiagnostic(range(lines, e.offset(), e.offset() + e.width()),
                    LspDiagnostic.ERROR, e.code().name(),
                    Messages.render(e.said(), EDITOR_LANGUAGE)));
        }
        return found;
    }

    /**
     * A marker for the compiler answering with something that is not a diagnostic. Analysis must not
     * take the session with it, but staying silent is its own failure: the author is left looking at
     * a file the editor calls clean. {@code StackOverflowError} is included deliberately — it is an
     * {@code Error}, not a {@code RuntimeException}, and a deeply nested expression raises it.
     */
    private LspDiagnostic internalError(LineIndex lines, Throwable t) {
        return new LspDiagnostic(range(lines, 0, 1), LspDiagnostic.ERROR, null,
                "the compiler could not finish reading this file ("
                        + t.getClass().getSimpleName() + ")");
    }

    /**
     * Diagnostics for every file in a workspace, resolved across imports the way the batch compiler
     * links a module set. Each file's syntax errors come from its own recovering parse; a file with a
     * syntax error stays out of the shared compile (it cannot join the graph). The remaining files are
     * compiled together, and each semantic diagnostic is published on the file of the module that owns
     * it — so an error in an imported module lands on that module's document, not on its importer.
     */
    public Map<String, List<LspDiagnostic>> diagnostics(ModuleGraph graph) {
        return diagnostics(graph, souther.compiler.meta.ModulePath.EMPTY);
    }

    /** As {@link #diagnostics(ModuleGraph)}, resolving an import that names no module in the
     * workspace against {@code path} — what the projects beside this one have already built — so
     * an import the build resolves is not reported here as unknown. */
    public Map<String, List<LspDiagnostic>> diagnostics(ModuleGraph graph,
                                                       souther.compiler.meta.ModulePath path) {
        Map<String, List<LspDiagnostic>> out = new LinkedHashMap<>();
        Map<String, String> compileSet = new LinkedHashMap<>();   // uri -> text, syntactically clean only
        Set<String> brokenModules = new HashSet<>();   // names of files held out for their syntax errors

        for (String uri : graph.uris()) {
            abandonment.stopIfAsked();   // between two files, before this one is parsed
            String text = graph.text(uri);
            LineIndex lines = new LineIndex(text);
            List<LspDiagnostic> syntax = new ArrayList<>();
            boolean readable = true;
            try {
                syntax.addAll(syntaxOf(text, lines));
            } catch (RuntimeException | StackOverflowError e) {
                syntax.add(internalError(lines, e));   // the parse itself did not finish
                readable = false;
            }
            out.put(uri, syntax);
            if (readable && syntax.isEmpty()) {
                compileSet.put(uri, text);   // a syntactically broken file cannot join the compile
            } else {
                String name = Compiler.moduleNameFromHeader(text);
                if (name != null) {
                    brokenModules.add(name);   // present but unparseable; importers skip, not cascade
                }
            }
        }

        Map<SourceId, List<Located>> byUri;
        try {
            byUri = compileOf(graph, path, compileSet, brokenModules).diagnostics();
        } catch (RuntimeException | StackOverflowError e) {
            // Which file broke the walk is not known here, so every file that entered the compile is
            // marked. Silence would leave the whole workspace looking clean.
            for (String uri : compileSet.keySet()) {
                out.get(uri).add(internalError(new LineIndex(graph.text(uri)), e));
            }
            return out;
        }
        Map<String, LineIndex> indexes = new LinkedHashMap<>();
        java.util.function.Function<String, LineIndex> linesOf = uri -> {
            if (uri == null) {
                return null;
            }
            return indexes.computeIfAbsent(uri, at -> {
                String text = graph.text(at);
                return text == null ? null : new LineIndex(text);
            });
        };
        for (Map.Entry<SourceId, List<Located>> e : byUri.entrySet()) {
            abandonment.stopIfAsked();   // between two files' markers, before this one is laid out
            List<LspDiagnostic> list = out.get(e.getKey().value());
            if (list == null) {
                continue;
            }
            for (Located loc : e.getValue()) {
                // A workspace names its sources by document URI, so a source id is already the name
                // the editor opens.
                // The document this marker is going in is what this route is reading, and the
                // file the report is listed under is what the compile said. Both, because they
                // are two answers: a problem written in two files is listed under one of them
                // and read from each in turn.
                list.add(project(loc.diagnostic(),
                        ReportContext.of(loc.context().filedUnder().orElse(null),
                                new SourceId(e.getKey().value())),
                        linesOf, uri -> graph.text(uri) == null ? null : uri));
            }
        }
        return out;
    }

    /**
     * The workspace's compile, brought up to date with what the documents now say.
     *
     * <p>Where this analyzer is told which documents the workspace holds, and so where what it
     * remembers about a document it no longer holds is dropped. {@code sources} is not that set —
     * it is the documents that could join the compile — and a document held out for its syntax
     * errors is one this still has. The graph is, which is why it is taken rather than worked out
     * from the two maps.
     *
     * <p>A document is named by its URI and a URI can be used again, so what was remembered has to
     * be dropped while the document is gone rather than when the next one arrives — by then the two
     * are both "the document at this URI" and nothing tells them apart. Every request that arrives
     * with a workspace comes through here, and a file created or deleted on disk reaches it as a
     * diagnose, so the gap is observed wherever there is one.
     */
    private Compilation compileOf(ModuleGraph graph, souther.compiler.meta.ModulePath path,
                                  Map<String, String> sources, Set<String> broken) {
        elsewhere.forgetAllBut(graph.uris());
        behaviorsOwed.forgetAllBut(graph.uris());
        readings.keySet().retainAll(Set.copyOf(graph.uris()));
        if (workspaceCompile == null || !path.equals(compiledAgainst)) {
            workspaceCompile = Compilation.ofDocuments(sources, broken, path);
            workspaceCompile.measure(measure);
            compiledAgainst = path;
        } else {
            workspaceCompile.update(sources, broken);
        }
        // Told here rather than where the compile is made: what stops the walk is whichever unit of
        // work reached it, and a compile outlives every one of them.
        workspaceCompile.abandonWhen(abandonment);
        return workspaceCompile;
    }

    /**
     * The same compile, for a request that arrives with a graph and no path — navigation. A file
     * that will not parse is left out of it, as it is for diagnostics: it cannot join a module set.
     * The path is whichever the last diagnose used, which is current, because a diagnose runs on
     * every change.
     */
    private Compilation compileOf(ModuleGraph graph) {
        Sorted sorted = sorted(graph);
        return compileOf(graph, pathCompiledAgainst(), sorted.joining(), sorted.broken());
    }

    /** The workspace as a compile takes it: what can join one, and the modules of what cannot. */
    private record Sorted(Map<String, String> joining, Set<String> broken) {}

    /**
     * Which documents can join a compile, sorted once.
     *
     * <p>Once because a document is one of the two and not the other, and a second reading of that
     * here would be a second answer free to differ from the one the compile was built from — the
     * probe compiles the same workspace with one document standing in for itself, and the rest of it
     * has to be the rest of it.
     */
    private Sorted sorted(ModuleGraph graph) {
        Map<String, String> joining = new LinkedHashMap<>();
        Set<String> broken = new HashSet<>();
        for (String uri : graph.uris()) {
            abandonment.stopIfAsked();
            String text = graph.text(uri);
            Reading reading = readingOf(uri, text);
            if (reading.parses()) {
                joining.put(uri, text);
            } else if (reading.declares() != null) {
                broken.add(reading.declares());
            }
        }
        return new Sorted(joining, broken);
    }

    private souther.compiler.meta.ModulePath pathCompiledAgainst() {
        return compiledAgainst == null ? souther.compiler.meta.ModulePath.EMPTY : compiledAgainst;
    }

    /**
     * What one document was found to be: whether it can join a compile, and — where it cannot — the
     * module its header names, which is what keeps an importer from being told the module is
     * unknown.
     *
     * <p>Kept with the text it was read from. Every request that arrives with the workspace sorts it
     * into what can be compiled and what cannot, and a request arrives for each keystroke while
     * completion is open; reading every file in the workspace again on each of them is work whose
     * answer cannot have changed, since one text parses one way. On the crm example — seven sources,
     * 3898 lines — it was 7.3 of the 9.3 milliseconds a completion took, and it grows with the
     * workspace rather than with the edit.
     */
    private record Reading(String text, boolean parses, String declares) {}

    /** Documents this analyzer has already read, by URI. Dropped along with everything else it
     * remembers about a document the workspace no longer holds. */
    private final Map<String, Reading> readings = new HashMap<>();

    private Reading readingOf(String uri, String text) {
        Reading had = readings.get(uri);
        if (had != null && had.text().equals(text)) {
            return had;
        }
        boolean parses;
        try {
            parses = CstParser.parse(text).errors().isEmpty();
        } catch (RuntimeException | StackOverflowError e) {
            parses = false;
        }
        Reading now = new Reading(text, parses,
                parses ? null : Compiler.moduleNameFromHeader(text));
        readings.put(uri, now);
        return now;
    }

    /** A source of the compile as the editor names it. A workspace hands its documents over under
     *  their URIs, so the identity a compile files one under is the URI it opens. */
    private static String uriOf(SourceId id) {
        return id == null ? null : id.value();
    }

    /** Where the cursor is, in the terms the compiler answers about: a place in a file, not a line
     * and a column that any file might have. */
    private static SourcePos cursor(String uri, Position pos) {
        return new SourcePos(pos.line() + 1, pos.character() + 1, new SourceId(uri));
    }

    /** What the cursor is on, as the compiler answers it: the type a name at {@code pos} denotes,
     * or the declaration whose own name is there. Null when the compiler cannot say — a file it
     * could not read, or a name in the value namespace. */
    private TypeSymbol typeUnderCursor(Compilation compilation, String uri, Position pos) {
        return compilation.db().ask(new Names.TypeAt(cursor(uri, pos))).value();
    }

    /**
     * The characters one written name occupies, counting only its last segment: renaming a type
     * rewrites the {@code Amount} of {@code up.Amount}, never the {@code up}.
     *
     * <p>Read off the tokens the name is written with, not measured in the name. The two differ by
     * however many combining marks the author typed, and by whatever the grammar lets stand between
     * a qualifier and the name it qualifies — a space, a comment, a line break — so an offset
     * counted in the name lands inside the qualifier at one end and short of the last character at
     * the other.
     */
    private Range writtenRange(WrittenName written) {
        Region segment = written.lastSegment();
        return new Range(editorPosition(segment.start()), editorPosition(segment.end()));
    }

    /** A compiler position as an editor counts, both of its numbers being one less. */
    private static Position editorPosition(SourcePos at) {
        return new Position(at.line() - 1, at.column() - 1);
    }

    /**
     * The document a compiler answer is about: the file its position names, or — for a position
     * that names none — the file of the module it was asked of. Null when neither is a document
     * this editor has open.
     *
     * <p>Every answer that carries a position carries the file it was written in, and what a module
     * is made of is not all written in one file: its own source and every attached {@code examples
     * for} file beside it. Filing an answer under the module's own source is right exactly while a
     * module is one file, and nothing checks that it is.
     */
    private String documentOf(SourcePos written, String moduleUri, ModuleGraph graph) {
        String uri = written == null ? moduleUri : switch (written.quotedFrom()) {
            case souther.compiler.diag.QuotedFrom.ASourceThisCompileHolds(var source) ->
                    source.value();
            // A place with no file of its own is shown against the module's, which is the file this
            // answer was asked of. Right while a module is one file, and nothing checks that it is.
            case souther.compiler.diag.QuotedFrom.TextItCannotShow _,
                 souther.compiler.diag.QuotedFrom.TextItCannotName _ -> moduleUri;
        };
        return uri != null && graph.text(uri) != null ? uri : null;
    }

    /**
     * Where a name the compiler answered about is written, as a place an editor can open.
     *
     * <p>Which file, which characters and how many of them all come from the compiler's answer,
     * which carries the occurrence the name was read from. This used to take a name and a position
     * and look through the syntax tree for a token at or after it spelling that name, because the
     * position it was given was the start of a form rather than of a name. Two things went wrong
     * with that and both were invisible until someone wrote a decomposed name: the token was
     * compared to the name, so a declaration spelled one way was never found from a reference
     * spelled the other; and the range came out as wide as the name rather than as the spelling.
     */
    private Optional<Location> nameAt(WrittenName written, String moduleUri, ModuleGraph graph) {
        if (written == null || !written.authored()) {
            return Optional.empty();
        }
        String uri = documentOf(written.pos(), moduleUri, graph);
        return uri == null ? Optional.empty()
                : Optional.of(new Location(uri, writtenRange(written)));
    }

    /**
     * What each behavior in this document has been pinned down to, as a line above its declaration.
     *
     * <p>Where the author is working is where the numbers are worth reading. The same figures are in
     * {@code souther examples}, and a report in another window is a report nobody opens while writing
     * the behavior it is about.
     *
     * <p>Empty unless there is a workspace compile and this editor was asked to measure. One document
     * is not enough to answer from: a behavior's rows are written across its module's own source and
     * any number of attached files, and reading one file would report what another covers as
     * uncovered — which is worse than saying nothing, being wrong rather than absent.
     */
    public List<CodeLens> codeLenses(String uri, ModuleGraph graph) {
        if (!measure.level().readsRows()) {
            return List.of();
        }
        Compilation compilation = compileOf(graph);
        String module = moduleOf(compilation, graph, uri);
        if (module == null) {
            return List.of();
        }
        souther.compiler.check.Prepared written =
                compilation.db().ask(new Shapes.Prepared(module)).value();
        if (written == null) {
            return List.of();
        }
        Adequacy.Of adequacy = compilation.adequacy(module);
        List<CodeLens> out = new ArrayList<>();
        for (Hir.BehaviorDef behavior : written.behaviors()) {
            // A module's declarations need not all be in this document, and a line number from
            // another file read against this one's index points somewhere arbitrary.
            if (!uri.equals(documentOf(behavior.pos(), null, graph))) {
                continue;
            }
            String title = lensTitle(compilation, module, behavior.name(), adequacy);
            if (title != null) {
                out.add(new CodeLens(lensAnchorRange(behavior.pos()), title));
            }
        }
        return out;
    }

    /**
     * The module this document is part of — the one it declares, or the one an attached
     * {@code examples for} file's rows are for.
     *
     * <p>The compile's answer, with the header read only for a file the compile does not have: one
     * held out for its syntax errors, which is the one case where there is nothing to ask. Reading
     * the header first is what left a cursor in an attached file belonging to no module and being
     * asked nothing.
     */
    private String moduleOf(Compilation compilation, ModuleGraph graph, String uri) {
        String known = compilation.moduleOf(new SourceId(uri));
        if (known != null) {
            return known;
        }
        String text = graph.text(uri);
        return text == null ? null : Compiler.moduleNameFromHeader(text);
    }

    /**
     * One behavior's line, or null where there is nothing to say about it.
     *
     * <p>A measure that could not be made is left out rather than printed as a zero. A behavior with
     * no rows has no numbers at all, and a lens reading {@code out 0/2} over one nobody has exampled
     * yet reports the model as failing something it was never asked.
     */
    private static String lensTitle(Compilation compilation, String module, String behavior,
                                    Adequacy.Of adequacy) {
        // Asked for rather than walked. This gathered the rows out of each source itself and read a
        // source that did not answer as one holding no rows, so a behavior whose file could not be
        // evaluated drew the same lens as one nobody has exampled — and drew none (issue #996).
        Map<String, Adequacy.RowReading> readings =
                compilation.db().ask(new Adequacy.RowReadings(module)).value();
        if (readings == null) {
            // The compile did not get as far as the shapes, so there is nothing to draw a lens
            // from. Not a reading that found no rows, which is what an editor would show as one.
            return null;
        }
        List<souther.compiler.observe.RowOutcome> read =
                Adequacy.RowReadings.readingFor(readings, behavior).measured().made()
                        .map(Adequacy.Observed::rows).orElse(null);
        if (read == null || read.isEmpty()) {
            // No numbers to show: either nobody has written a row here, or nothing read the ones
            // that are. A lens saying `0 rows` would be this deciding which of those it was.
            return null;
        }
        int rows = read.size();
        int pending = (int) read.stream()
                .filter(r -> r.disposition() == souther.compiler.observe.Disposition.PENDING)
                .count();
        List<String> parts = new ArrayList<>();
        parts.add(rows + (rows == 1 ? " row" : " rows"));
        if (pending > 0) {
            parts.add(pending + " pending");
        }
        Adequacy.SignatureEvidence signature =
                adequacy.signatures() == null ? null : adequacy.signatures().get(behavior);
        if (signature != null && !signature.output().declared().isEmpty()) {
            signature.output().cases().made().ifPresent(cases ->
                    parts.add("out " + cases.specified().size() + "/"
                            + signature.output().declared().size()));
        }
        souther.compiler.query.Measure<List<souther.compiler.query.BorderObligationPointAssessment>>
                account = adequacy.accounts() == null ? null : adequacy.accounts().get(behavior);
        if (account != null) {
            // What this behavior is owed a row for, and not every point of every line it met. A
            // line the declarations drew is answered once for the module by a row written for any
            // behavior carrying the type, so a lens over a behavior that counted those would be
            // telling this author about work that is not theirs.
            //
            // Over the points and not over the borders or the readings. A border owes a row at up
            // to four of them, and a line is owed once however many positions read it, so
            // counting either would call a line with one point met as covered as one that owes
            // only that point, or one guard over a sum as many rows as the sum has cases.
            List<ObligationAssessment> settled =
                    account.made().orElseGet(List::of).stream()
                            .map(souther.compiler.query.BorderObligationPointAssessment::item)
                            .filter(Analyzer::settled).toList();
            long met = settled.stream()
                    .filter(ObligationAssessment::hasRowWitness)
                    .count();
            if (!settled.isEmpty()) {
                parts.add("boundary " + met + "/" + settled.size());
            }
        }
        Adequacy.BranchEvidence branch =
                adequacy.branches() == null ? null : adequacy.branches().get(behavior);
        // The measure was made and made in full. A count shown beside a measurement made in part
        // reads as settled, which is the one thing an editor's one line has no room to qualify.
        //
        // This surface's own decision about what to publish, over the same counts the report reads.
        // The prose can print a partial measure's numbers because it can print the word that
        // qualifies them; one line in an editor cannot, so it holds to Complete. What both take from
        // the value is how the numbers are worked out (issue #997).
        if (branch != null
                && branch.measured() instanceof Measurement.Complete<ArmSummary> whole) {
            parts.add("branch " + whole.value().covered() + "/" + whole.value().counted());
        }
        return String.join(" · ", parts);
    }

    /**
     * Whether a line came to an answer against the rows.
     *
     * <p>Hit or missed, and nothing else. A point waiting on the arms has no answer to show beside a
     * declaration, and one whose value could not be read has no answer either — a lens counting it
     * would put a number in front of an author that says a row is missing at a value nothing was able
     * to look at. The report can afford to include such a point because it writes "undecided" beside
     * the count; one number on one line has nowhere to put that word.
     *
     * <p>Nor a point nobody is owed a row at. Nothing was measured there and nothing is missing, and
     * a lens that counted it would put the model's own answer into a ratio of what the rows reach.
     */
    private static boolean settled(ObligationAssessment owed) {
        // What the readings came to together, short of nothing, which is the whole of what this
        // asks. The two states it leaves out are a point left undecided and one nobody read.
        return owed.coverage().settled();
    }

    /**
     * Where a lens is drawn: the declaration's position, as a range of no width.
     *
     * <p>An editor reads the line a lens's range starts on and nothing else of it, so a point says
     * everything a lens needs. It says nothing about what a caret is in, which is a different
     * question and is answered from the declaration a caret is written inside of.
     */
    private static Range lensAnchorRange(SourcePos pos) {
        Position at = new Position(pos.line() - 1, pos.column() - 1);
        return new Range(at, at);
    }

    /**
     * Quick-fix code actions overlapping {@code requested}: currently, replacing a misspelled name
     * with the compiler's did-you-mean suggestion. The suggestion lives on the structured compiler
     * diagnostic — which the published {@link LspDiagnostic} drops — so this recomputes the first
     * semantic error to recover it. The type checker reports only that first error, so at most one
     * fix is offered per compile.
     */
    public List<CodeAction> codeActions(String uri, String text, Range requested) {
        return codeActions(uri, text, requested, null);
    }

    /**
     * The same, with the workspace in reach: what a behavior's rows do not cover can be filled in
     * from here.
     *
     * <p>{@code graph} may be null, and is where the request arrived without one. The generated rows
     * need the whole workspace — the values a row writes are built through the module's derived
     * decoders, and its imports are part of that — so with one document there is nothing to offer.
     */
    public List<CodeAction> codeActions(String uri, String text, Range requested,
                                        ModuleGraph graph) {
        List<CodeAction> out = new ArrayList<>();
        CstParser.Result parsed = CstParser.parse(text);
        if (!parsed.errors().isEmpty()) {
            return out;   // a semantic suggestion needs a clean parse
        }
        if (graph != null) {
            out.addAll(rowsToWrite(uri, text, parsed.root(), requested, graph));
        }
        Diagnostic d = firstSemanticDiagnostic(text);
        if (d == null || d.suggestion() == null) {
            return out;
        }
        // The compile behind this read the document's own text and could not name it, so what it
        // points at is a stretch of the text in front of the author. A report with nothing to point
        // at has no edit to offer: an action needs a range, and a sentence about a module is not
        // one.
        Region diagnosed = switch (d.primary()) {
            case Primary.InSource(DiagnosticPlace.InSource place) -> place.region();
            case Primary.InAnUnnamedText(UnnamedRegion where) -> where.region();
            case Primary.Unavailable _, Primary.Nowhere _ -> null;
        };
        if (diagnosed == null) {
            return out;
        }
        Range diagRange = rangeOfRegion(diagnosed);
        if (overlaps(diagRange, requested)) {
            out.add(new CodeAction.Applied("Replace with '" + d.suggestion() + "'",
                    new CodeAction.Edit(uri, diagRange, d.suggestion())));
        }
        return out;
    }

    /**
     * An offer to write the rows nothing covers, on the behavior the cursor is in.
     *
     * <p>The same block {@code souther examples --generate} prints, put where it goes rather than on
     * a terminal for someone to copy. The rows arrive as rows, each written {@code <?>} where its
     * answer goes: the compiler does not know what the model owes, and a row it filled in would be
     * an assertion nobody made. What that buys here is that the rows are in the document — the
     * checker goes on saying which of them are owed an answer, {@code souther fmt} runs over them,
     * and renaming a field one of them names reaches it. A block written into a file and hidden from
     * everything that would keep it starts going stale where it lands.
     *
     * <p>Inserted at the end of the document. Where rows belong is the author's choice — this
     * module's own source or an attached file — and moving a block is easier than finding out why one
     * landed somewhere surprising.
     */
    private List<CodeAction> rowsToWrite(String uri, String text, SyntaxNode root, Range requested,
                                         ModuleGraph graph) {
        if (!measure.level().readsRows()) {
            return List.of();
        }
        // Which declaration is being asked about, before anything is compiled to answer about it.
        // This reads the document the request arrived with and costs a walk of its top-level nodes;
        // what comes after is a workspace compile and a search over what it holds, and an editor
        // asks this every time the cursor moves.
        LineIndex lines = new LineIndex(text);
        SyntaxNode declaration = behaviorDeclarationAsked(root, lines, requested);
        if (declaration == null) {
            return List.of();
        }
        int writtenFrom = writtenFrom(declaration);
        Compilation compilation = compileOf(graph);
        String module = moduleOf(compilation, graph, uri);
        if (module == null) {
            return List.of();
        }
        souther.compiler.check.Prepared written =
                compilation.db().ask(new Shapes.Prepared(module)).value();
        if (written == null) {
            return List.of();
        }
        for (Hir.BehaviorDef behavior : written.behaviors()) {
            if (!isWrittenIn(behavior, uri, graph)
                    || !startsWithin(lines, behavior.pos(), writtenFrom, declaration.end())) {
                continue;
            }
            // Whether the model owes this behavior anything a row could answer. Asked of the
            // findings, which the report beside this has already worked out, and not of the
            // generator: composing a value costs a decoder run for each point it settles, and an
            // editor asks what is available here every time the cursor moves.
            if (!anythingARowCouldAnswer(compilation, module, behavior.name())) {
                continue;
            }
            CodeAction.Deferred offer = new CodeAction.Deferred(
                    "Write the rows `" + behavior.name() + "` does not cover", uri, module,
                    behavior.name());
            // Where the client will not come back for it, the edit is worked out now. What the
            // handshake settles is when this costs what it costs, and never whether the offer is
            // made: an action a client would never resolve has to arrive with its edit or it does
            // nothing at all.
            if (resolvesActions) {
                return List.of(offer);
            }
            CodeAction.Edit eager = resolve(offer, text, graph);
            return eager == null ? List.of() : List.of(new CodeAction.Applied(offer.title(), eager));
        }
        return List.of();
    }

    /**
     * Whether this behavior is written in this document.
     *
     * <p>The cursor is in one document, so a declaration written in another is not what it is on,
     * however the lines happen to line up. Asked in one place because it is asked twice: an offer
     * names the document it was made about, and taking it later has to find the behavior in that
     * same document — a module can be renamed onto another source between the two, and a behavior
     * of that name found somewhere else is not the one somebody was offered.
     */
    private boolean isWrittenIn(Hir.BehaviorDef behavior, String uri, ModuleGraph graph) {
        return uri.equals(documentOf(behavior.pos(), null, graph));
    }

    /**
     * The behavior declaration the request is in, or null where it is in none.
     *
     * <p>A declaration is what a caret is inside of while reading it, and it runs from its first
     * code token to the end of its last. Not the node's span: a node reaches back over the blank
     * lines and comments in front of it, and a caret on the line above a declaration is where the
     * next one is written rather than inside this one.
     *
     * <p>A caret and a selection are asked differently. A caret is a position, and a position at
     * either end of a declaration is on it. A selection is a stretch, and it meets the declaration
     * where the two share a character — so a selection of the blank line above stops at the first
     * code token rather than reaching past it.
     */
    private SyntaxNode behaviorDeclarationAsked(SyntaxNode root, LineIndex lines, Range requested) {
        int from = lines.offsetOf(requested.start().line(), requested.start().character());
        int to = lines.offsetOf(requested.end().line(), requested.end().character());
        for (SyntaxNode def : root.childNodes()) {
            if (def.kind() != SyntaxKind.BEHAVIOR_DEF) {
                continue;
            }
            int written = writtenFrom(def);
            if (written < 0) {
                continue;
            }
            boolean asked = from == to
                    ? from >= written && from <= def.end()
                    : from < def.end() && written < to;
            if (asked) {
                return def;
            }
        }
        return null;
    }

    /** Whether a compiler position is one this document writes between two of its offsets. */
    private static boolean startsWithin(LineIndex lines, SourcePos pos, int from, int to) {
        int at = lines.offsetOf(pos.line() - 1, pos.column() - 1);
        return at >= from && at <= to;
    }

    /** Whether anything this behavior is short of is a thing writing a row could answer. */
    private static boolean anythingARowCouldAnswer(Compilation compilation, String module,
                                                   String behavior) {
        List<souther.compiler.query.Adequacy.Finding> findings =
                compilation.db().ask(new souther.compiler.query.Adequacy.Findings(module)).value();
        if (findings == null) {
            return false;
        }
        // This behavior's own, and the lines its type declarations are owed that a row written here
        // would settle. A line an `invariant` drew is not this behavior's finding — what `UserId`
        // says is the same wherever the type is carried — and a row written for a behavior carrying
        // the type is what discharges it, so an offer standing beside that behavior is an offer to
        // do that work (issue #1062). Read as the behavior's own alone, the offer went quiet as
        // soon as the only work left was a line a declaration is owed.
        //
        // Asked of the finding and never of what a search has composed. Whether a value has been
        // built turns on how much the build was measuring, and an offer that read it would appear
        // at one level and not at another for work that is there either way.
        for (souther.compiler.query.Adequacy.Finding each : findings) {
            if (souther.compiler.query.Adequacy.whereNoRowCouldAnswer(each.about()) != null) {
                continue;
            }
            // A finding at a point of a line is not by itself work. A line is owed one row however
            // many positions read it, so a coordinate of a line another position answered is a
            // finding standing over nothing to write — and counted here, an offer is made that
            // resolves to no rows. What is owed is the point's own answer and is asked below.
            if (each.about() instanceof souther.compiler.query.About.APointOfABorder
                    || each.about() instanceof souther.compiler.query.About
                            .APointOfADeclaredBorder) {
                continue;
            }
            if (each.subject().isBehavior(behavior)) {
                return true;
            }
        }
        return anyLineIsOwedARow(compilation, module, behavior);
    }

    /**
     * Whether a line this behavior reads is owed a row nothing has written.
     *
     * <p>Asked of what is owed rather than of the findings that stand at it. A report counts a line
     * once per coordinate it was read at and a row is owed once for the line, so the two answer
     * different questions — and the question an offer to write rows is putting is the second one.
     *
     * <p>This behavior's lines and its declarations' alike. A line an {@code invariant} drew is not
     * this behavior's finding — what {@code UserId} says is the same wherever the type is carried —
     * and a row written for a behavior carrying the type is what discharges it, so an offer standing
     * beside that behavior is an offer to do that work.
     *
     * <p>And only the lines this module answers for
     * ({@link souther.compiler.query.BorderObligationPointAssessment#keptBy}). A module that carries
     * an imported type reads its lines and owes rows at none of them: the row belongs where the
     * declaration is. Offered here, the offer is made and the search that follows it leaves the
     * point out, so there is nothing to hand back.
     *
     * <p>What is asked is whether the point is worth looking for a row at, which is the measurement
     * saying no row stands there. Whether one can be composed is a further question and costs a
     * decoder run to answer, so it is left to whoever takes the offer: an offer made here and
     * resolved to nothing is a search that could not compose the row somebody asked for, which is
     * news, and one never made would have been the same search decided in advance.
     */
    private static boolean anyLineIsOwedARow(Compilation compilation, String module,
                                             String behavior) {
        List<souther.compiler.query.BorderObligationPointAssessment> owed = compilation.db()
                .ask(new souther.compiler.query.Adequacy.Obligations(module,
                        new souther.compiler.query.GenerationScope.Behavior(behavior))).value();
        if (owed == null) {
            return false;
        }
        return owed.stream().anyMatch(point -> point.carriedBy(behavior)
                && point.keptBy(module)
                && point.owed().worthSearching());
    }

    /**
     * The rows themselves, for somebody who has taken the offer.
     *
     * <p>Worked out here rather than where the offer was made. This is what costs: the values are
     * put through the module's own decoders, and an author taking the action is asking for that
     * once, rather than on every cursor move.
     *
     * <p>The behavior is looked up in what the workspace holds now. A document is edited between an
     * offer being shown and being taken, and rows built against the older text would be written into
     * source they were not composed for.
     *
     * <p>Null where there is nothing to write. An offer to write rows has to write rows: a block
     * holding only notes is not blank, so a caller reading the text for whether there is work would
     * put a comment into somebody's source. What there is to write is the generator's answer, and it
     * is the count that is asked.
     */
    public CodeAction.Edit resolve(CodeAction.Deferred offer, String text, ModuleGraph graph) {
        if (graph == null || text == null) {
            return null;
        }
        Compilation compilation = compileOf(graph);
        // The whole of what the offer names, and not the part of it a module happens to answer. An
        // offer is about a behavior of a module written in a document, and a document can be given
        // another module's header while a behavior of that name goes on existing somewhere else —
        // so a check that asked only whether the module still has the behavior would compose rows
        // from one source and write them into another.
        if (!offer.module().equals(moduleOf(compilation, graph, offer.uri()))) {
            return null;
        }
        souther.compiler.check.Prepared written =
                compilation.db().ask(new Shapes.Prepared(offer.module())).value();
        if (written == null || written.behaviors().stream()
                .noneMatch(each -> each.name().equals(offer.behavior())
                        && isWrittenIn(each, offer.uri(), graph))) {
            return null;   // what the offer was made about is not there any more
        }
        // An id stands for itself here: a workspace compilation is keyed on the document URIs this
        // server was given, so what identifies a source is already what this server calls it.
        souther.compiler.report.GeneratedRows.Block block =
                souther.compiler.report.GeneratedRows.of(compilation, offer.module(),
                        offer.behavior(), true,
                        souther.compiler.diag.SourceNameResolver.identity());
        if (block.rowCount() == 0) {
            return null;
        }
        // Inserted at the end of the document. Where rows belong is the author's choice — this
        // module's own source or an attached file — and moving a block is easier than finding out
        // why one landed somewhere surprising.
        Position end = new Position((int) text.lines().count(), 0);
        return new CodeAction.Edit(offer.uri(), new Range(end, end),
                System.lineSeparator() + block.text());
    }

    /** The first semantic error a self-contained compile turns up, as the structured compiler
     * {@link Diagnostic} (carrying its suggestion and region), or {@code null} when there is none —
     * mirrors the semantic path of {@link #diagnostics(String)}. */
    private Diagnostic firstSemanticDiagnostic(String text) {
        try {
            Ast.Module module = CstFrontend.parse(text, "Main");
            if (!module.imports().isEmpty() || module.exampleFileTarget() != null) {
                return null;   // a multi-module or examples file cannot be resolved from one file
            }
            Compiler.compile(text, "Main");
            return null;
        } catch (CompileException e) {
            return e.diagnostic();
        } catch (RuntimeException | StackOverflowError _) {
            return null;   // no suggestion to recover; the diagnostics pass reports the failure
        }
    }

    private static boolean overlaps(Range a, Range b) {
        return !before(a.end(), b.start()) && !before(b.end(), a.start());
    }

    private static boolean before(Position p, Position q) {
        return p.line() < q.line() || (p.line() == q.line() && p.character() < q.character());
    }

    /** The document's canonical formatting (see {@link Formatter}), or empty when it has a syntax
     * error — the formatter re-derives layout from a clean parse, so a broken document is left as-is. */
    public Optional<String> format(String text) {
        try {
            CstParser.Result parsed = CstParser.parse(text);
            if (!parsed.errors().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(Formatter.format(parsed.root()));
        } catch (RuntimeException | StackOverflowError _) {
            // The one entry point here that answered a document it could not walk with an error
            // reply rather than by leaving the document alone. The reason it could not is already
            // the author's to see, as a diagnostic; a failed format on top of it says nothing more.
            return Optional.empty();
        }
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
        Compilation compilation = compileOf(graph);
        if (resolves(compilation, uri)) {
            TypeSymbol type = typeUnderCursor(compilation, uri, pos);
            if (type != null) {
                return declarationOf(compilation, type, graph);
            }
            Optional<Location> value = valueDeclarationOf(compilation, uri, pos, graph);
            if (value.isPresent()) {
                return value;
            }
        }
        LineIndex lines = new LineIndex(text);
        SyntaxNode root = CstParser.parse(text).root();
        SyntaxToken ident = identAt(meaningfulTokens(root), lines, lines.offsetOf(pos.line(), pos.character()));
        if (ident == null) {
            return Optional.empty();
        }
        SyntaxNode local = declaringDef(root, nameOf(ident));
        if (local != null) {
            SyntaxToken name = nameToken(local);
            return name == null ? Optional.empty() : Optional.of(new Location(uri, tokenRange(lines, name)));
        }
        String targetModule = importedFrom(text, nameOf(ident));
        if (targetModule == null) {
            return Optional.empty();
        }
        String targetUri = uriOfModule(compilation, graph, targetModule);
        if (targetUri == null) {
            return Optional.empty();
        }
        String targetText = graph.text(targetUri);
        SyntaxNode def = declaringDef(CstParser.parse(targetText).root(), nameOf(ident));
        if (def == null) {
            return Optional.empty();
        }
        SyntaxToken name = nameToken(def);
        return name == null ? Optional.empty()
                : Optional.of(new Location(targetUri, tokenRange(new LineIndex(targetText), name)));
    }

    /**
     * Find-references across the workspace: every use of the name the cursor is on, in the module
     * that declares it and in every module that names it. Namespaces are respected — a type mention
     * and a value of the same spelling are different names — and so is scope: a local is the binding
     * it is, so the uses of one body's {@code x} are not another's. The declaration itself is
     * included only when {@code includeDeclaration} is set, and a behavior has two of them.
     *
     * <p>A local is in reach, which it was not while this matched by spelling: a scan of top-level
     * declarations cannot see a name bound inside one. Where resolution has nothing to say — a
     * pipeline's stages and a behavior's own declaration lines are written outside any body, so it
     * never read them — the spelling is still what answers.
     */
    public List<Location> references(String uri, Position pos, ModuleGraph graph, boolean includeDeclaration) {
        String text = graph.text(uri);
        if (text == null) {
            return List.of();
        }
        Compilation compilation = compileOf(graph);
        if (resolves(compilation, uri)) {
            List<Location> uses = usesOf(compilation, uri, pos, graph, includeDeclaration);
            if (uses != null) {
                return uses;
            }
            List<Location> values = valueUsesOf(compilation, uri, pos, graph, includeDeclaration);
            if (!values.isEmpty()) {
                return values;
            }
            // Nothing found is not nothing here: the resolve pass records the value names written
            // in a body, and a composition's stages and a declaration's own name are not those, so
            // the cursor may be on a name it never saw. Matching the spelling still answers those.
        }
        SyntaxNode root = CstParser.parse(text).root();
        LineIndex lines = new LineIndex(text);
        SyntaxToken ident = identAt(meaningfulTokens(root), lines, lines.offsetOf(pos.line(), pos.character()));
        if (ident == null) {
            return List.of();
        }
        String name = nameOf(ident);
        String definingModule = declaringDef(root, name) != null
                ? moduleOf(compilation, graph, uri) : importedFrom(text, name);
        if (definingModule == null) {
            return List.of();
        }
        String definingUri = uriOfModule(compilation, graph, definingModule);
        if (definingUri == null) {
            return List.of();
        }
        boolean isType = isTypeDef(declaringDef(CstParser.parse(graph.text(definingUri)).root(), name));

        List<Location> out = new ArrayList<>();
        for (String u : graph.uris()) {
            abandonment.stopIfAsked();
            String t = graph.text(u);
            boolean owns = definingModule.equals(moduleOf(compilation, graph, u));
            if (!owns && !definingModule.equals(importedFrom(t, name))) {
                continue;   // this file neither defines nor imports the symbol
            }
            collectReferences(CstParser.parse(t).root(), name, isType, u, new LineIndex(t),
                    includeDeclaration && owns, out);
        }
        return out;
    }

    /**
     * The edits for renaming the name under the cursor to {@code newName}, grouped by document uri:
     * every reference to it plus every line that declares it. Empty when the cursor is not on a
     * name.
     *
     * <p>A local is renameable, from its binding as much as from a use of it — the same reach
     * find-references has, for the same reason.
     *
     * <p>Each edit says what to write as well as where, because those differ. A record pattern's
     * {@code { right }} names the field it reads and binds the value under that same spelling, and
     * once the two are no longer spelled alike the field has to be named: what goes there is
     * {@code right = r}. Answering with places alone left the caller to write one name over all of
     * them, which is right everywhere else and silently produced a module reading a field that does
     * not exist here.
     */
    public Map<String, List<TextEdit>> renameEdits(String uri, Position pos, ModuleGraph graph,
                                                   String newName) {
        Map<String, List<TextEdit>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Range>> e : renameRanges(uri, pos, graph).entrySet()) {
            List<TextEdit> edits = new ArrayList<>();
            SyntaxNode root = CstParser.parse(graph.text(e.getKey())).root();
            LineIndex lines = new LineIndex(graph.text(e.getKey()));
            List<SyntaxToken> tokens = meaningfulTokens(root);
            for (Range range : e.getValue()) {
                String field = fieldTakenAsName(tokens, lines,
                        lines.offsetOf(range.start().line(), range.start().character()));
                edits.add(new TextEdit(range,
                        field == null ? newName : field + " = " + newName));
            }
            out.put(e.getKey(), edits);
        }
        return out;
    }

    /**
     * The field a binding takes its name from, or null where the binding is written as a name of
     * its own.
     *
     * <p>Which characters are there is the syntax tree's question, being what knows about
     * characters, and this is the one place where what a rename writes is not the name it was given.
     */
    private String fieldTakenAsName(List<SyntaxToken> tokens, LineIndex lines, int offset) {
        SyntaxToken token = identAt(tokens, lines, offset);
        SyntaxNode parent = token == null ? null : token.parent();
        if (parent == null || token.start() != offset) {
            return null;
        }
        if (parent.kind() == SyntaxKind.PATTERN_FIELD) {
            // `{ f }` writes one name for both jobs; `{ f = x }` writes the binding's own
            return onlyIdent(parent) ? token.text() : null;
        }
        if (parent.kind() == SyntaxKind.MATCH_CASE) {
            // an arm's fields are the case's own tokens: `Flat { f }` against `Flat { f = x }`
            return standsAlone(parent, token) ? token.text() : null;
        }
        return null;
    }

    /** Whether {@code node} holds one identifier and no other. */
    private boolean onlyIdent(SyntaxNode node) {
        int found = 0;
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                found++;
            }
        }
        return found == 1;
    }

    /** Whether {@code token} is a field written with nothing beside it — the previous and next
     * tokens that are not trivia open or separate the list rather than assign to it. */
    private boolean standsAlone(SyntaxNode node, SyntaxToken token) {
        SyntaxToken before = null;
        boolean past = false;
        for (SyntaxElement e : node.children()) {
            if (!(e instanceof SyntaxToken t) || t.isTrivia()) {
                continue;
            }
            if (past) {
                return (t.kind() == SyntaxKind.COMMA || t.kind() == SyntaxKind.RBRACE)
                        && before != null
                        && (before.kind() == SyntaxKind.LBRACE || before.kind() == SyntaxKind.COMMA);
            }
            if (t == token) {
                past = true;
                continue;
            }
            before = t;
        }
        return false;
    }

    /** Where renaming touches, before what to write there is decided. */
    private Map<String, List<Range>> renameRanges(String uri, Position pos, ModuleGraph graph) {
        Map<String, List<Range>> byUri = new LinkedHashMap<>();
        for (Location loc : references(uri, pos, graph, true)) {
            byUri.computeIfAbsent(loc.uri(), k -> new ArrayList<>()).add(loc.range());
        }
        if (byUri.isEmpty()) {
            return byUri;   // nothing under the cursor to rename
        }
        // references() treats a name in an `exposing`/`import` list as a binding site, not a use, and
        // skips it. Rename must still update those, or the module stops exposing (and importers stop
        // importing) the renamed symbol, breaking the build.
        //
        // Which module those belong to is the same question the references were found by, and it has
        // to be answered the same way: renaming from a qualified use renames the module the
        // reference names, and matching the spelling here would edit this module's own `exposing`
        // instead — leaving both modules uncompilable.
        Compilation compilation = compileOf(graph);
        TypeSymbol type = typeUnderCursor(compilation, uri, pos);
        // What the language gives is exposed by no module and imported by nobody, so there is no
        // `exposing` line and no `import` line naming it to edit.
        if (type instanceof TypeSymbol.AtModule at) {
            addExposingAndImportSites(compilation, at.name(), at.module(), graph, byUri);
            return byUri;
        }
        if (type != null) {
            return byUri;
        }
        ValueName value = valueUnderCursor(compilation, uri, pos);
        if (value != null) {
            String declaring = switch (value) {
                case ValueName.Helper h -> h.module();
                case ValueName.Behavior b -> b.module();
                // a local is named where it is bound and nowhere else; the library and the language
                // are not this workspace's to rename
                case ValueName.Local _, ValueName.Stdlib _, ValueName.OfType _,
                        ValueName.Builtin _ -> null;
            };
            if (declaring != null) {
                addExposingAndImportSites(compilation, value.name(), declaring, graph, byUri);
            }
            return byUri;
        }
        String text = graph.text(uri);
        SyntaxNode root = CstParser.parse(text).root();
        LineIndex lines = new LineIndex(text);
        SyntaxToken ident = identAt(meaningfulTokens(root), lines, lines.offsetOf(pos.line(), pos.character()));
        if (ident != null) {
            String name = nameOf(ident);
            String definingModule = declaringDef(root, name) != null
                    ? moduleOf(compilation, graph, uri) : importedFrom(text, name);
            if (definingModule != null) {
                addExposingAndImportSites(compilation, name, definingModule, graph, byUri);
            }
        }
        return byUri;
    }

    /** What the cursor is on in the value namespace, as the compiler answers it, or null when
     * nothing there is a name in it. */
    private ValueName valueUnderCursor(Compilation compilation, String uri, Position pos) {
        return compilation.db().ask(new Names.ValueAt(cursor(uri, pos))).value();
    }

    /**
     * Where what the cursor names as a value is written, as the compiler answers it: the binding
     * that introduced a local, or the {@code let} or {@code behavior} that declares it.
     *
     * <p>A local was out of reach while this was matched by spelling — one spelling may be bound in
     * several bodies, and nothing said which binding a use belonged to. Resolution says.
     */
    private Optional<Location> valueDeclarationOf(Compilation compilation, String uri, Position pos,
                                                  ModuleGraph graph) {
        ValueName target = valueUnderCursor(compilation, uri, pos);
        if (target == null) {
            return Optional.empty();
        }
        return valueDeclarationOf(compilation, target, uri, graph);
    }

    private Optional<Location> valueDeclarationOf(Compilation compilation, ValueName target,
                                                  String uri, ModuleGraph graph) {
        WrittenName at = compilation.db().ask(new Names.ValueDeclaredAt(target)).value();
        return nameAt(at, declaringFile(compilation, target, uri), graph);
    }

    /**
     * Every place the value's own name is written as a declaration.
     *
     * <p>More than one where a behavior is what it names: the {@code behavior} line and the
     * {@code let} line both write it. Renaming from a use has to reach both, or the module goes on
     * naming something that is no longer there — and matching the spelling, which is what answered
     * this before, saw both because it saw every line the name was on.
     */
    private List<Location> valueDeclarationsOf(Compilation compilation, ValueName target,
                                               String uri, ModuleGraph graph) {
        List<WrittenName> written =
                compilation.db().ask(new Names.ValueDeclarationsOf(target)).value();
        if (written == null) {
            return List.of();
        }
        List<Location> out = new ArrayList<>();
        for (WrittenName at : written) {
            nameAt(at, declaringFile(compilation, target, uri), graph).ifPresent(out::add);
        }
        return out;
    }

    /** The file a value's declaration is in where its position does not name one. */
    private String declaringFile(Compilation compilation, ValueName target, String uri) {
        return switch (target) {
            case ValueName.Local _ -> uri;   // bound in the body the cursor is in
            case ValueName.Helper h -> uriOf(compilation.sourceIdOf(h.module()));
            case ValueName.Behavior b -> uriOf(compilation.sourceIdOf(b.module()));
            case ValueName.Stdlib _, ValueName.OfType _, ValueName.Builtin _ -> null;
        };
    }

    /**
     * The name an identifier token spells.
     *
     * <p>The one door from a token to a name in the paths that read the tree of characters rather
     * than the compiler's answer — an import list entry, a file the compile could not read.
     * Canonically equivalent spellings are one name, and a token is characters, so every one of
     * those paths turns a token into a name exactly here.
     *
     * <p>Canonicalizing at the comparison instead — which is where it was — leaves whichever side
     * came from a cursor raw, so a cursor on a decomposed spelling found no declaration and a
     * cursor on a composed one found it. Which spelling the author's cursor happened to be on is
     * not a thing an editor may answer differently.
     */
    private static String nameOf(SyntaxToken token) {
        return token == null ? null : Reserved.name(token.text());
    }

    /** Whether an identifier token spells {@code name}, which is canonical. */
    private static boolean spells(SyntaxToken token, String name) {
        return token != null && name.equals(nameOf(token));
    }

    /**
     * Whether the compile read this document and answered about the names in it.
     *
     * <p>This is what decides whether an unanswered question is a question with no answer or a
     * question nothing could be asked of. Where the compile read the file, its silence is the
     * answer: nothing is named under the cursor. Where it could not — a file with a syntax error is
     * held out of the module set, and a module whose names would not resolve has no answers to give
     * — matching the spelling is all there is, and it goes on being what an author gets while a file
     * is half-typed.
     */
    private boolean resolves(Compilation compilation, String uri) {
        String module = compilation.moduleOf(new SourceId(uri));
        return module != null && compilation.db().ask(new Names.Facts(module)).present();
    }

    /** Where a type is declared, as the compiler answers it. */
    private Optional<Location> declarationOf(Compilation compilation, TypeSymbol target,
                                             ModuleGraph graph) {
        // Nothing the language gives is written in a source anyone can be sent to: there is no
        // `.sou` that declares `Int`, and the library's own are not this project's.
        if (!(target instanceof TypeSymbol.AtModule declared)) {
            return Optional.empty();
        }
        // Which module, which name and where it was written is the compiler's answer — the part a
        // spelling match gets wrong.
        WrittenName at = compilation.db().ask(new Names.DeclaredAt(declared)).value();
        return nameAt(at, uriOf(compilation.sourceIdOf(declared.module())), graph);
    }

    /**
     * Every place the type under the cursor is named, as the compiler answers it, or null when the
     * cursor is not on a type. A name resolves to one declaration wherever it is written, so a
     * module that declares its own type of the same spelling is not swept up, and a qualified
     * reference to another module's is.
     */
    private List<Location> usesOf(Compilation compilation, String uri, Position pos,
                                  ModuleGraph graph, boolean includeDeclaration) {
        TypeSymbol target = typeUnderCursor(compilation, uri, pos);
        if (target == null) {
            return null;
        }
        List<Location> out = new ArrayList<>();
        if (includeDeclaration) {
            declarationOf(compilation, target, graph).ifPresent(out::add);
        }
        for (String module : compilation.modules()) {
            abandonment.stopIfAsked();
            String moduleUri = uriOf(compilation.sourceIdOf(module));
            for (Resolve.TypeUse use
                    : compilation.db().ask(new Names.UsesOf(module, target)).value()) {
                String at = documentOf(use.pos(), moduleUri, graph);
                if (at != null) {
                    out.add(new Location(at, writtenRange(use.written())));
                }
            }
        }
        return out;
    }

    /**
     * Every place the value under the cursor is named, as the compiler answers it. Empty when the
     * cursor is not on one.
     *
     * <p>{@link #usesOf} for the value namespace, and the reason a local can be asked about at all:
     * a use denotes a binding, so the uses of one body's {@code x} are not another's. Matching the
     * spelling could only ever answer about top-level names, and answered about the wrong one where
     * two modules spelled a name alike.
     */
    private List<Location> valueUsesOf(Compilation compilation, String uri, Position pos,
                                       ModuleGraph graph, boolean includeDeclaration) {
        ValueName target = valueUnderCursor(compilation, uri, pos);
        if (target == null) {
            return List.of();
        }
        List<Location> out = new ArrayList<>();
        if (includeDeclaration) {
            out.addAll(valueDeclarationsOf(compilation, target, uri, graph));
        }
        for (String module : compilation.modules()) {
            abandonment.stopIfAsked();
            String moduleUri = uriOf(compilation.sourceIdOf(module));
            for (Resolve.ValueUse use
                    : compilation.db().ask(new Names.ValueUsesOf(module, target)).value()) {
                String at = documentOf(use.pos(), moduleUri, graph);
                if (at != null) {
                    out.add(new Location(at, writtenRange(use.written())));
                }
            }
        }
        return out;
    }

    /** Adds the {@code exposing ( name )} occurrence in the module that defines {@code name}, and each
     * {@code import <definingModule> ( name )} occurrence in the modules that import it — the binding
     * sites find-references skips but rename must carry along. */
    private void addExposingAndImportSites(Compilation compilation, String name,
                                           String definingModule, ModuleGraph graph,
                                           Map<String, List<Range>> byUri) {
        for (String u : graph.uris()) {
            abandonment.stopIfAsked();
            String t = graph.text(u);
            SyntaxNode root = CstParser.parse(t).root();
            LineIndex lines = new LineIndex(t);
            boolean owns = definingModule.equals(moduleOf(compilation, graph, u));
            for (SyntaxNode top : root.childNodes()) {
                if (top.kind() == SyntaxKind.MODULE_HEADER && owns) {
                    top.child(SyntaxKind.EXPOSING_CLAUSE).ifPresent(clause -> {
                        for (SyntaxNode entry : clause.childNodes()) {
                            if (entry.kind() != SyntaxKind.EXPOSED_ENTRY) {
                                continue;
                            }
                            entry.child(SyntaxKind.QUALIFIED_NAME).ifPresent(qn -> {
                                SyntaxToken tok = nameToken(qn);
                                if (spells(tok, name)) {
                                    byUri.computeIfAbsent(u, k -> new ArrayList<>()).add(tokenRange(lines, tok));
                                }
                            });
                        }
                    });
                } else if (top.kind() == SyntaxKind.IMPORT_DECL) {
                    SyntaxNode module = top.child(SyntaxKind.QUALIFIED_NAME).orElse(null);
                    if (module != null && definingModule.equals(dottedName(module))) {
                        top.child(SyntaxKind.NAME_LIST).ifPresent(list -> {
                            for (SyntaxElement e : list.children()) {
                                if (e instanceof SyntaxToken tok && tok.kind() == SyntaxKind.IDENT
                                        && spells(tok, name)) {
                                    byUri.computeIfAbsent(u, k -> new ArrayList<>()).add(tokenRange(lines, tok));
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    /** The dotted spelling of a {@code QUALIFIED_NAME} node (its identifier tokens joined by {@code .}). */
    private String dottedName(SyntaxNode qualifiedName) {
        StringBuilder sb = new StringBuilder();
        for (SyntaxElement e : qualifiedName.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(nameOf(t));
            }
        }
        return sb.toString();
    }

    /**
     * Completion candidates at the cursor, nearest scope first: the params and {@code let} bindings
     * of the definition the cursor sits in, the top-level names this document declares, the names
     * that reach it from elsewhere, and the language keywords. One item per label, the nearest
     * winning — which is what shadowing means, a binding in force being what its spelling denotes
     * however many imports also spell it.
     *
     * <p>The first two are read off this document's syntax tree every time, so a definition being
     * written now is offered before it compiles. The third is the compiler's answer about the module
     * this document belongs to, kept while the compiler cannot answer (see
     * {@link NamesFromElsewhere}) — a document that does not parse is held out of the compile, and
     * that is the document being typed in.
     *
     * <p>This is name completion. Every reachable name is offered wherever the cursor is, because
     * nothing here reads what the position denotes or what type is expected at it. One item per label
     * follows from that: which namespace a position is in is not being read, so two entities of one
     * spelling cannot be told apart by anything but the label, and the nearest is offered. That is a
     * limit of this list, not a property of the language.
     */
    public List<CompletionItem> completions(String uri, Position pos, ModuleGraph graph) {
        String text = graph.text(uri);
        if (text == null) {
            return List.of();
        }
        List<CompletionItem> members = membersAt(uri, text, pos, graph);
        if (members != null) {
            return members;
        }
        SyntaxNode root = CstParser.parse(text).root();
        Compilation compilation = compileOf(graph);
        String module = moduleOf(compilation, graph, uri);
        List<CompletionItem> declared = declaredIn(root, module);
        // Whether this document is an attached file, read off its own text rather than off the
        // compile: what is offered while a document will not parse is the point of keeping the last
        // answer, and a document held out of the compile still says which of the two it is on its
        // first line.
        boolean writesRows = root.child(SyntaxKind.EXAMPLES_FILE_HEADER).isPresent();
        List<CompletionItem> fromElsewhere =
                elsewhere.of(compilation, uri, module, labelsOf(declared), writesRows);

        LinkedHashMap<String, CompletionItem> byLabel = new LinkedHashMap<>();
        int cursor = new LineIndex(text).offsetOf(pos.line(), pos.character());
        SyntaxNode enclosing = enclosingDef(root, cursor);
        if (enclosing != null) {
            collectLocalBindings(enclosing, cursor, byLabel);
        }
        for (CompletionItem item : declared) {
            byLabel.putIfAbsent(item.label(), item);
        }
        for (CompletionItem item : fromElsewhere) {
            byLabel.putIfAbsent(item.label(), item);
        }
        // Ahead of the keywords, so where a declaration may be written the offer is the declaration
        // rather than the word it starts with. Inside a definition none of these are offered and the
        // word stands, which is what a `let` binding in a block is written with.
        for (CompletionItem item : declarationsToWrite(uri, root, cursor, compilation, module)) {
            byLabel.putIfAbsent(item.label(), item);
        }
        for (String keyword : CstLexer.keywords()) {
            byLabel.putIfAbsent(keyword, new CompletionItem(keyword, CompletionItem.KEYWORD, null));
        }
        return new ArrayList<>(byLabel.values());
    }

    /**
     * What the call the cursor is inside declares it takes, and which of those is being written.
     *
     * <p>Empty where the cursor is in no call, or in one whose callee is not a behavior — a helper
     * and a local holding a function have nothing declared on a {@code behavior} line to show.
     *
     * <p>The two halves come from different places on purpose. What the declaration says is read at
     * the callee's name, which is written before the brackets and so before anything a probe put in.
     * Which argument is being written is counted in the source the author left, so a bracket
     * supplied to make the line parse is not one of the commas.
     *
     * <p>Empty where the author is writing an argument the declaration has no parameter for. There
     * is no way to say "none of them" among parameters that exist: the protocol reads an active
     * parameter outside the list as none given and marks the first, so a fourth argument to a
     * behavior taking three would be answered by pointing at the first — which is not what a reader
     * is writing, and is a worse answer than not writing the signature out.
     *
     * <p>A behavior that takes nothing is not that case. It has nothing to mark and the protocol
     * asks for no mark, so its signature is written out like any other — and seeing that it takes
     * nothing is most of what a reader who has just opened its brackets wants.
     */
    public Optional<SignatureHelp> signatureHelp(String uri, Position pos, ModuleGraph graph) {
        String text = graph.text(uri);
        if (text == null) {
            return Optional.empty();
        }
        int cursor = new LineIndex(text).offsetOf(pos.line(), pos.character());
        Sorted sorted = sorted(graph);
        Map<String, String> rest = new LinkedHashMap<>(sorted.joining());
        rest.remove(uri);
        // The buffer as it stands where it parses, and finished off where it does not: a call is
        // asked about while its closing bracket is not typed, which is most of the time.
        SemanticProbe.Reading reading =
                probe.of(rest, sorted.broken(), pathCompiledAgainst(), uri, text, cursor,
                        abandonment);
        Compilation compilation = reading == null ? compileOf(graph) : reading.compilation();
        String parsed = reading == null ? text : reading.repaired();
        String module = moduleOf(compilation, graph, uri);
        if (module == null) {
            return Optional.empty();
        }
        SyntaxNode call = enclosingCall(CstParser.parse(parsed).root(), cursor);
        SyntaxNode applies = call == null ? null : calleeOf(call);
        SyntaxToken callee = applies == null ? null : firstIdent(applies);
        if (callee == null) {
            return Optional.empty();
        }
        LineIndex lines = new LineIndex(parsed, new SourceId(uri));
        Optional<SemanticSnapshot> snapshot = SemanticSnapshot.of(compilation.db(), module);
        int argument = argumentAt(call, cursor);
        return snapshot.flatMap(reads -> reads.calledAt(lines.posOf(callee.start())))
                .filter(called -> reading == null || reading.mayBeRead(called.writtenAt()))
                .filter(called -> called.takes().isEmpty() || argument < called.takes().size())
                .map(called -> shown(called, snapshot.orElseThrow(), argument));
    }

    /**
     * The declaration written out, as a reader reads a signature.
     *
     * <p>A parameter whose type this module has no name for is shown by its name alone. What is
     * being told is which parameter is being written, and the name says that; a spelling worked out
     * some other way would say something else as well, and be wrong.
     */
    private static SignatureHelp shown(CalledBehavior called, SemanticSnapshot snapshot,
                                       int argument) {
        List<String> parameters = new ArrayList<>();
        for (CalledBehavior.Takes takes : called.takes()) {
            parameters.add(snapshot.spellingOf(takes.type().type())
                    .map(spelling -> takes.name() + ": " + spelling)
                    .orElse(takes.name()));
        }
        return new SignatureHelp(called.name() + "(" + String.join(", ", parameters) + ")",
                parameters,
                parameters.isEmpty() ? java.util.OptionalInt.empty()
                        : java.util.OptionalInt.of(argument));
    }

    /**
     * The innermost application the cursor is inside, or null where it is in none.
     *
     * <p>See {@link #calleeOf} for what a call applies.
     *
     * <p>Inside its brackets and not up to the end of it: a cursor that has just closed a call is
     * past that call and writing what holds it, and showing the one it closed would answer about the
     * argument it has finished rather than the one it is on.
     */
    private static SyntaxNode enclosingCall(SyntaxNode root, int cursor) {
        SyntaxNode innermost = null;
        for (SyntaxNode node : callsIn(root)) {
            if (node.start() <= cursor && cursor < node.end()
                    && (innermost == null || node.start() >= innermost.start())) {
                innermost = node;
            }
        }
        return innermost;
    }

    /**
     * What {@code call} applies: whatever stands in front of the argument list.
     *
     * <p>A place in it is all that is taken from here. How far the name written there runs is the
     * census's to say — a behavior reached through a module is written {@code m.submit} and is one
     * occurrence over the whole run, and a syntax node runs over the trivia in front of it, so
     * neither end of this node is where anything was written.
     */
    private static SyntaxNode calleeOf(SyntaxNode call) {
        for (SyntaxElement each : call.children()) {
            if (each instanceof SyntaxNode node && node.kind() != SyntaxKind.ARG_LIST) {
                return node;
            }
        }
        return null;
    }

    private static List<SyntaxNode> callsIn(SyntaxNode node) {
        List<SyntaxNode> out = new ArrayList<>();
        if (node.kind() == SyntaxKind.APPLY_EXPR) {
            out.add(node);
        }
        for (SyntaxNode child : node.childNodes()) {
            out.addAll(callsIn(child));
        }
        return out;
    }

    /**
     * Which argument of {@code call} the cursor is writing.
     *
     * <p>The commas the argument list itself holds, and no others. The parser writes an argument
     * list as its brackets, its arguments and the commas between them, so a comma inside a nested
     * call, a tuple or a construction is that one's and is not a child here — counted in the
     * characters instead, it would have to be told from those by brackets, and a bracket in a string
     * literal would tell it wrong.
     */
    private static int argumentAt(SyntaxNode call, int cursor) {
        SyntaxNode arguments = call.child(SyntaxKind.ARG_LIST).orElse(null);
        if (arguments == null) {
            return 0;
        }
        int written = 0;
        for (SyntaxElement each : arguments.children()) {
            if (each instanceof SyntaxToken token && token.kind() == SyntaxKind.COMMA
                    && token.start() < cursor) {
                written++;
            }
        }
        return written;
    }

    /**
     * Every declaration in the workspace whose name holds {@code query}, wherever it is written.
     *
     * <p>The projection {@code documentSymbol} answers with, put to every document instead of one.
     * What a file declares is read off its own syntax, so a file that will not compile is searched
     * like any other — and the name an author is looking for is often in the file they are in the
     * middle of.
     *
     * <p>The fields inside a declaration are left out. What this is for is reaching a declaration,
     * and a field is reached by opening the one it belongs to; a workspace of records would answer
     * mostly with {@code name} and {@code id}.
     *
     * <p>An empty query names everything, which is what the protocol says it means: a client sending
     * one is asking for the workspace's declarations to choose from.
     */
    public List<WorkspaceSymbol> workspaceSymbols(String query, ModuleGraph graph) {
        List<WorkspaceSymbol> found = new ArrayList<>();
        for (String uri : graph.uris()) {
            abandonment.stopIfAsked();
            String text = graph.text(uri);
            if (text == null) {
                continue;
            }
            for (DocumentSymbol symbol : documentSymbols(text)) {
                if (holds(symbol.name(), query)) {
                    found.add(new WorkspaceSymbol(symbol.name(), symbol.kind(),
                            new Location(uri, symbol.selectionRange())));
                }
            }
        }
        return List.copyOf(found);
    }

    /** Whether {@code name} is one {@code query} is looking for, ignoring case — an author typing a
     *  name to jump to types it the way it comes to hand. */
    private static boolean holds(String name, String query) {
        return query == null || query.isEmpty()
                || name.toLowerCase(java.util.Locale.ROOT)
                        .contains(query.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Every place in this document that means what the cursor is on.
     *
     * <p>The references answer, kept to one document. It is asked the same way and answers about the
     * same thing — what a name resolves to, and not what spells the same. Two locals of one spelling
     * are two names and are highlighted apart, which is the whole of why this is not a search for
     * the characters.
     *
     * <p>Which of them binds the name is the difference between asking for the declaration and not,
     * so it is that difference: a place in both is where the name is bound, and a place only in the
     * wider answer is where it is read.
     */
    public List<DocumentHighlight> documentHighlights(String uri, Position pos, ModuleGraph graph) {
        List<Range> reads = here(uri, references(uri, pos, graph, false));
        List<DocumentHighlight> out = new ArrayList<>();
        for (Range at : here(uri, references(uri, pos, graph, true))) {
            out.add(new DocumentHighlight(at,
                    reads.contains(at) ? DocumentHighlight.READ : DocumentHighlight.WRITE));
        }
        return List.copyOf(out);
    }

    /** The ranges of {@code found} that are in {@code uri}. A reference in another file is a
     *  reference and is not something this document paints. */
    private static List<Range> here(String uri, List<Location> found) {
        List<Range> out = new ArrayList<>();
        for (Location each : found) {
            if (uri.equals(each.uri())) {
                out.add(each.range());
            }
        }
        return out;
    }

    /**
     * What a widening selection takes in, from what the cursor is on outwards.
     *
     * <p>Syntax and nothing else. What a reader means by widening a selection is the next thing they
     * wrote around it — an arm of a {@code guard}, a {@code { }} block, the declaration around that
     * — and the tree the parser built is exactly that nesting. Nothing here asks what a name means,
     * and a document that does not compile widens the same way, because the question was never about
     * meaning.
     *
     * <p>Innermost first, each containing the one before it, with a range repeated by a node that
     * covers exactly what its child does left out — widening that takes in nothing is a keystroke
     * that did nothing.
     */
    public List<Range> selectionRanges(String uri, Position pos, ModuleGraph graph) {
        String text = graph.text(uri);
        if (text == null) {
            return List.of();
        }
        LineIndex lines = new LineIndex(text);
        int at = lines.offsetOf(pos.line(), pos.character());
        List<Range> widening = new ArrayList<>();
        SyntaxToken on = tokenAt(CstParser.parse(text).root(), at);
        if (on != null) {
            widening.add(tokenRange(lines, on));
        }
        for (SyntaxNode node = on == null ? null : on.parent(); node != null;
                node = node.parent()) {
            Range around = nodeRange(lines, node);
            if (widening.isEmpty() || !around.equals(widening.get(widening.size() - 1))) {
                widening.add(around);
            }
        }
        return List.copyOf(widening);
    }

    /** The token {@code at} falls in, taking the one that starts there over the one that ends there:
     *  a cursor between two tokens is on the one it is about to type into. */
    private SyntaxToken tokenAt(SyntaxNode root, int at) {
        SyntaxToken previous = null;
        for (SyntaxToken token : meaningfulTokens(root)) {
            if (token.start() <= at && at < token.end()) {
                return token;
            }
            if (token.end() == at) {
                previous = token;
            }
        }
        return previous;
    }

    /**
     * The types a {@code let}'s parameters arrive as, to be drawn where the names are written.
     *
     * <p>A signature is written once, on the {@code behavior} line, and the implementation repeats
     * none of it. What is shown here is that declaration carried to where the author is working, and
     * not inference shown to them — which is why it is answered from the signature and stands
     * whether or not the body checks.
     *
     * <p>Only where the type can be written the way this module writes it. A hint naming a type the
     * author has no name for would be showing them something they cannot type.
     *
     * <p>Read from the workspace's compile and not through a probe. A hint is drawn on a document
     * that is being read rather than one mid-keystroke, and what is wanted is what the file says.
     */
    public List<InlayHint> inlayHints(String uri, Range within, ModuleGraph graph) {
        Compilation compilation = compileOf(graph);
        String module = moduleOf(compilation, graph, uri);
        String text = graph.text(uri);
        if (module == null || text == null) {
            return List.of();
        }
        Optional<SemanticSnapshot> snapshot = SemanticSnapshot.of(compilation.db(), module);
        if (snapshot.isEmpty()) {
            return List.of();
        }
        List<InlayHint> hints = new ArrayList<>();
        for (DeclaredParameter parameter : snapshot.get().parametersIn(new SourceId(uri))) {
            Position after = editorPosition(parameter.writtenAt().end());
            if (!within(within, after)) {
                continue;
            }
            // The type in the label, and that it is held to a rule of its own in what a reader gets
            // by asking. A label is what is taken in without looking, and a second thing written
            // into one is read as part of the type's name.
            snapshot.get().spellingOf(parameter.type().type()).ifPresent(spelling ->
                    hints.add(new InlayHint(after, ": " + spelling,
                            parameter.heldToARule() ? spelling + " has an invariant" : null,
                            false)));
        }
        return List.copyOf(hints);
    }

    /** Whether {@code at} is in the range the client asked about, ends included — a hint drawn at
     *  the very end of what is on screen is on screen. */
    private static boolean within(Range range, Position at) {
        return range == null || (!before(at, range.start()) && !before(range.end(), at));
    }

    /**
     * The fields of what the cursor is taking something off, or null where it is taking nothing off
     * anything.
     *
     * <p>Null and not an empty list, because the two mean different things to the caller: nothing may
     * be written after a {@code .} on a value with no fields, and every reachable name may be
     * written where there is no {@code .} at all. A member list replaces the name list rather than
     * joining it — what a field read admits is a field of that value and not whatever else is in
     * scope.
     *
     * <p>Read from the source as it stands, through a probe: the line a field list is wanted on is
     * one the parser cannot finish, so the document is out of the workspace's compile at exactly the
     * moment this is asked. What the probe answers about is the buffer now, and what may be taken
     * from it stops where the probe put anything in — which the receiver clears and the access
     * around it does not.
     */
    private List<CompletionItem> membersAt(String uri, String text, Position pos, ModuleGraph graph) {
        int cursor = new LineIndex(text).offsetOf(pos.line(), pos.character());
        if (!takingSomethingOffSomething(text, cursor)) {
            return null;
        }
        Sorted sorted = sorted(graph);
        Map<String, String> rest = new LinkedHashMap<>(sorted.joining());
        rest.remove(uri);
        SemanticProbe.Reading reading =
                probe.of(rest, sorted.broken(), pathCompiledAgainst(), uri, text, cursor,
                        abandonment);
        if (reading == null) {
            return List.of();
        }
        String module = moduleOf(reading.compilation(), graph, uri);
        if (module == null) {
            return List.of();
        }
        Optional<SemanticSnapshot> snapshot =
                SemanticSnapshot.of(reading.compilation().db(), module);
        if (snapshot.isEmpty()) {
            return List.of();
        }
        SourcePos at = new LineIndex(text, new SourceId(uri)).posOf(cursor);
        Optional<MemberReceiver> receiver = snapshot.get().memberReceiverAround(at);
        if (receiver.isEmpty() || !reading.mayBeRead(receiver.get().writtenAt())) {
            return List.of();
        }
        return switch (receiver.get()) {
            case MemberReceiver.Value held -> fields(snapshot.get(), held);
            case MemberReceiver.Namespace in -> published(snapshot.get(), in);
            // A value the declarations say nothing about is still a receiver: a field read is what
            // was written, and every name in scope is not what may be written after it.
            case MemberReceiver.UntypedValue _ -> List.of();
        };
    }

    /**
     * Whether the cursor is where a member is written: straight after a {@code .}.
     *
     * <p>Read off what is written and not off what could be answered, and that is the point.
     * Whether this reading can say what the members are varies — a source it cannot finish, a module
     * that will not resolve, a receiver no declaration speaks for — and none of that changes what
     * the author is writing. A member position that fell back to the names in scope when the answer
     * was not reached would offer the whole language after a {@code .} exactly when it knows least.
     *
     * <p>Which token, and not which character. Whether a {@code .} is a field's is a question the
     * language answers when it reads the text: {@code .5} is a number, a dot inside a string literal
     * is part of the string, and a rule of this reading's own about digits and quotes would be that
     * reading written a second time and free to differ from the one the parser goes on to use.
     */
    private static boolean takingSomethingOffSomething(String text, int cursor) {
        return SemanticProbe.aDotEndsAt(text, cursor);
    }

    /** What a namespace offers, painted as what the offering module declares it to be. */
    private static List<CompletionItem> published(SemanticSnapshot snapshot,
                                                  MemberReceiver.Namespace in) {
        List<CompletionItem> out = new ArrayList<>();
        for (Published offered : snapshot.namesIn(in)) {
            out.add(new CompletionItem(offered.name(), switch (offered) {
                case Published.AType _ -> CompletionItem.CLASS;
                case Published.ABehavior _ -> CompletionItem.INTERFACE;
                case Published.ADefinition _ -> CompletionItem.FUNCTION;
            }, null));
        }
        return List.copyOf(out);
    }

    /**
     * What a value's fields are, as an editor offers them: the name to write, and what it is.
     *
     * <p>The type only where this module has a name for it. A field may be declared as something the
     * module reading it neither declares nor imports, and an offer carrying a spelling worked out
     * some other way would show the author a name they cannot write. The field is still offered —
     * it is there to be read, and what it is is the part that cannot be said.
     */
    private static List<CompletionItem> fields(SemanticSnapshot snapshot,
                                               MemberReceiver.Value held) {
        List<CompletionItem> out = new ArrayList<>();
        snapshot.fieldsOf(held.type()).forEach((field, is) ->
                out.add(new CompletionItem(field, CompletionItem.FIELD,
                        snapshot.spellingOf(is).orElse(null))));
        return List.copyOf(out);
    }

    /**
     * The declarations that may be written at the cursor.
     *
     * <p>Nothing where the cursor is inside a definition: what may be written there is an
     * expression, and a declaration offered inside one is an offer to write a syntax error. At the
     * file level, the forms whose place in a file the cursor is still in — a header opens a file, an
     * import follows it, and a body item may be written wherever one may.
     *
     * <p>Where the compile can say what this module declares, a behavior with no implementation is
     * offered the {@code let} its signature describes, and every behavior is offered a row. Where it
     * cannot — which is every keystroke that leaves the document unparseable — the forms are still
     * offered, stating what they are and nothing that was not read.
     */
    private List<CompletionItem> declarationsToWrite(String uri, SyntaxNode root, int cursor,
                                                     Compilation compilation, String module) {
        if (enclosingDef(root, cursor) != null) {
            return List.of();
        }
        List<CompletionItem> out = new ArrayList<>();
        Set<TopLevelForm.Region> here = regionsAt(root, cursor);
        for (TopLevelForm form : TopLevelForm.values()) {
            if (here.contains(form.region())) {
                built(form.starter(), CompletionItem.SNIPPET, null,
                        DeclarationSkeletons.fixed(form)).ifPresent(out::add);
            }
        }
        // Through the same gate. A declaration written from a signature stands where a declaration
        // stands, and knowing which one it is does not make it writable anywhere else.
        if (here.contains(TopLevelForm.Region.BODY)) {
            out.addAll(behaviorsToWrite(uri, compilation, module));
        }
        return List.copyOf(out);
    }

    /**
     * What the behaviors of {@code module} are still owed, as declarations to write.
     *
     * <p>Two sets, not one. An implementation is owed by a behavior written as a signature with
     * nothing implementing it, which is {@link Requirements#injectedNames} — the same question the
     * emitter asks about what it has to be given. A row may be written for any behavior at all: a
     * composition has no implementation to offer, since it is its own, and has rows like anything
     * else.
     */
    private List<CompletionItem> behaviorsToWrite(String uri, Compilation compilation,
                                                  String module) {
        List<CompletionItem> answered =
                behaviorsOwed.of(uri, module, () -> askBehaviorsToWrite(compilation, module));
        return answered == null ? List.of() : answered;
    }

    /**
     * The same, asked of the compile — null where it cannot say what this module declares.
     *
     * <p>Null for each of the three questions going unanswered, and not only for the first. A
     * module's requirements not being answered is not that module requiring nothing: a row written
     * from that would leave out the stand-in its target needs, which is E1908 the moment it is
     * completed. Answering nothing is what lets the last answer that was given stand.
     */
    private List<CompletionItem> askBehaviorsToWrite(Compilation compilation, String module) {
        if (module == null) {
            return null;
        }
        Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
        Map<String, List<BehaviorRequirement>> requirements =
                compilation.db().ask(new Bodies.Requirements(module)).value();
        Map<String, Sig> signatures = compilation.db().ask(new Bodies.Signatures(module)).value();
        Map<String, DeclaredSig> declarations =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        if (prepared == null || requirements == null || signatures == null
                || declarations == null) {
            return null;
        }
        List<CompletionItem> out = new ArrayList<>();
        for (Hir.BehaviorDef declared : prepared.behaviors()) {
            implementationToWrite(prepared, declared, module).ifPresent(out::add);
            rowToWrite(prepared, declared, signatures, declarations, requirements, module)
                    .ifPresent(out::add);
        }
        return out;
    }

    /** The {@code let} a behavior is owed, where it is owed one. */
    private static Optional<CompletionItem> implementationToWrite(
            Prepared prepared, Hir.BehaviorDef declared, String module) {
        if (!(declared instanceof Hir.SpecBehavior behavior)
                || prepared.implementationOf(behavior).hasBody()) {
            return Optional.empty();
        }
        List<SpecImplementation.Parameter> parameters = SpecImplementation.parameters(behavior);
        if (parameters.contains(new SpecImplementation.Parameter.Unanswered())) {
            // A dependency naming nothing settles no parameter, and a skeleton stating one it did
            // not read would be this server inventing it.
            return Optional.empty();
        }
        return built(TopLevelForm.FN.starter() + " " + behavior.name(), CompletionItem.SNIPPET,
                module, DeclarationSkeletons.implementing(behavior.name(), parameters));
    }

    /**
     * A row for a behavior: as many arguments as it takes, and what nothing stands in for.
     *
     * <p>How many it takes is the signature's, which is what a row is held to whether the behavior
     * is written as one or composed out of others — a composition takes what its first stage takes,
     * and nothing here works that out a second time. What it depends on is the same:
     * {@link Bodies.Requirements} carries a composition's stages' requirements as its own, so a row
     * for one supplies what the stages want.
     *
     * <p>A behavior that is itself injected requires nothing and is not a key there — the one place
     * a name being missing says something rather than being something missing. Which of the two it
     * is, is asked of the behavior rather than read off the absence: a name that is not there for
     * any other reason is an answer that does not hold together, and a row written as though it
     * required nothing would state no stand-in for what it depends on.
     */
    private static Optional<CompletionItem> rowToWrite(
            Prepared prepared, Hir.BehaviorDef declared, Map<String, Sig> signatures,
            Map<String, DeclaredSig> declarations,
            Map<String, List<BehaviorRequirement>> requirements, String module) {
        Sig sig = signatures.get(declared.name());
        if (sig == null) {
            return Optional.empty();
        }
        List<BehaviorRequirement> required = List.of();
        // A behavior that takes dependencies as arguments is offered a row that supplies them,
        // whether or not its `let` has been written yet. An injection target takes none.
        if (!prepared.implementationOf(declared).isInjectionTarget()) {
            required = requirements.get(declared.name());
            if (required == null) {
                return Optional.empty();
            }
        }
        List<String> unsupplied = ExampleProvisioning.unsupplied(List.of(),
                        Requirements.names(required), prepared.forExamples()).stream()
                .map(dependency -> Requirements.writtenIn(prepared.name(), dependency)).toList();
        DeclaredSig written = declarations.get(declared.name());
        List<String> arguments = written == null
                ? unnamedArguments(sig.ins().size())
                : argumentsOf(written);
        return built(TopLevelForm.EXAMPLE.starter() + " " + declared.name(),
                CompletionItem.SNIPPET, module,
                DeclarationSkeletons.exampleFor(declared.name(), arguments, unsupplied));
    }

    /**
     * What to write in each of a row's argument places, where the behavior named its parameters.
     *
     * <p>A label and nothing more: what stands in an argument place is a value, not the parameter.
     * It reads as the declaration's own word for what goes there, which is what a name is for.
     */
    private static List<String> argumentsOf(DeclaredSig declared) {
        List<String> labels = new ArrayList<>();
        for (DeclaredSig.Input input : declared.inputs()) {
            labels.add(input.name());
        }
        return labels;
    }

    /**
     * The same for a behavior that named none.
     *
     * <p>A composition takes what its first stage takes and calls those nothing of its own, so a row
     * for one says what it takes without saying what that stage happened to call them.
     */
    private static List<String> unnamedArguments(int arity) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < arity; i++) {
            labels.add("arg");
        }
        return labels;
    }

    /**
     * An item writing {@code parts}, or nothing where they do not make a declaration.
     *
     * <p>Fail-open, deliberately. A skeleton is refused where the tokens do not parse or where the
     * formatter did not write back what it was given, and neither is about what an author typed:
     * every name in one comes from a declaration the compiler read, so both mean a defect in the
     * formatter or the grammar. What that costs here is one candidate fewer, which nothing says out
     * loud — this server has no channel to say it on, since its output is the protocol.
     *
     * <p>Taken over the alternative, which is to let it out and lose the whole list: an editor would
     * be left with no completion at all, for every request against that document, over a candidate
     * that was never the one being asked for. What guards against it going unnoticed is that every
     * form is built in a test, and a behavior's is built over each model those tests are written
     * against.
     */
    private static Optional<CompletionItem> built(String label, int kind, String detail,
                                                  List<Skeleton.Part> parts) {
        try {
            return Optional.of(new CompletionItem(label, kind, detail, Skeleton.of(parts)));
        } catch (Skeleton.Mismatch _) {
            return Optional.empty();
        }
    }

    /**
     * The places in a file the cursor is in.
     *
     * <p>A file is read as a header, then its imports, then its body, and a form may be written at
     * the cursor when writing it there leaves the file still in that order. So both sides of the
     * cursor are read: what stands before it says what it is past, and what stands after it says
     * what it may not be written in front of. An import offered above one already written is an
     * offer to write a file whose imports are not together, and a definition offered above one is an
     * offer to write a definition the imports come after.
     *
     * <p>A header is offered only to a file with none. There is one, it opens the file, and a second
     * is not something to write.
     */
    private static Set<TopLevelForm.Region> regionsAt(SyntaxNode root, int cursor) {
        int headerEnds = -1;
        boolean hasHeader = false;
        int lastImportEnds = -1;
        int firstDefinition = Integer.MAX_VALUE;
        boolean anythingBefore = false;
        for (SyntaxNode item : root.childNodes()) {
            // Where the item is written, not where its node begins: a node reaches back over the
            // blank line in front of it, and a cursor on that line is in front of the item.
            int written = writtenFrom(item);
            if (written < 0) {
                continue;
            }
            anythingBefore |= written < cursor;
            switch (item.kind()) {
                case MODULE_HEADER, EXAMPLES_FILE_HEADER -> {
                    hasHeader = true;
                    headerEnds = Math.max(headerEnds, item.end());
                }
                case IMPORT_DECL -> lastImportEnds = Math.max(lastImportEnds, item.end());
                default -> firstDefinition = Math.min(firstDefinition, written);
            }
        }
        Set<TopLevelForm.Region> here = new LinkedHashSet<>();
        if (!hasHeader && !anythingBefore) {
            here.add(TopLevelForm.Region.FILE_HEADER);
        }
        boolean pastTheHeader = cursor >= headerEnds;
        if (pastTheHeader && cursor <= firstDefinition) {
            here.add(TopLevelForm.Region.PRELUDE);
        }
        if (pastTheHeader && cursor >= lastImportEnds) {
            here.add(TopLevelForm.Region.BODY);
        }
        return here;
    }

    /**
     * The top-level names this document declares, read off its tree.
     *
     * <p>Off the tree and not off the compile, because the compile does not have a document that
     * will not parse and this is the one it is being asked about. A {@code data} written as a sum is
     * offered as one, so a declaration reads the same here as it does from another document, where
     * the compiler answers what it is.
     */
    private List<CompletionItem> declaredIn(SyntaxNode root, String module) {
        List<CompletionItem> declared = new ArrayList<>();
        for (SyntaxNode def : root.childNodes()) {
            SyntaxToken name = nameToken(def);
            if (name == null) {
                continue;
            }
            Integer kind = switch (def.kind()) {
                case DATA_DEF -> def.child(SyntaxKind.SUM_BODY).isPresent()
                        ? CompletionItem.ENUM : CompletionItem.CLASS;
                case BEHAVIOR_DEF -> CompletionItem.INTERFACE;
                case FN_DEF -> CompletionItem.FUNCTION;
                default -> null;   // header, imports, error nodes declare no completion name
            };
            if (kind != null) {
                declared.add(new CompletionItem(nameOf(name), kind, module));
            }
        }
        return declared;
    }

    private static Set<String> labelsOf(List<CompletionItem> items) {
        Set<String> labels = new HashSet<>();
        for (CompletionItem item : items) {
            labels.add(item.label());
        }
        return labels;
    }

    /** The top-level definitions, each of which is written as one thing and holds what it binds. */
    private static final Set<SyntaxKind> DEFINITIONS = Set.of(
            SyntaxKind.DATA_DEF, SyntaxKind.BEHAVIOR_DEF, SyntaxKind.FN_DEF,
            SyntaxKind.EXAMPLE_DEF, SyntaxKind.FAKE_DEF);

    /**
     * The top-level definition whose text contains {@code offset}, or {@code null} at the file level.
     *
     * <p>Its text, and not its span. A node reaches back over the blank lines and comments in front
     * of it — the tree covers every character, and they belong to something — so a cursor on the
     * empty line above a definition is inside its span while being nowhere near it. That line is
     * where the next declaration is written, and what is bound inside the definition below is bound
     * nowhere there.
     *
     * <p>The rows of an {@code example} and of a {@code fake} are definitions here as much as a
     * {@code let} is. What is written in one is an expression — a row's inputs, what it expects, what
     * a table answers with — so a declaration offered inside one would be offered inside an
     * expression, and a binding written in a row's block holds there and is in force at a cursor in
     * it.
     */
    private SyntaxNode enclosingDef(SyntaxNode root, int offset) {
        for (SyntaxNode def : root.childNodes()) {
            if (!DEFINITIONS.contains(def.kind())) {
                continue;
            }
            int written = writtenFrom(def);
            if (written >= 0 && offset >= written && offset <= def.end()) {
                return def;
            }
        }
        return null;
    }

    /** Where {@code node}'s own text begins: its first code token, past the trivia in front of it. */
    private static int writtenFrom(SyntaxNode node) {
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxToken token) {
                if (!token.kind().isTrivia()) {
                    return token.start();
                }
            } else if (e instanceof SyntaxNode child) {
                int written = writtenFrom(child);
                if (written >= 0) {
                    return written;
                }
            }
        }
        return -1;
    }

    /**
     * Adds the params and {@code let} names in force at {@code offset} as variable candidates.
     *
     * <p>In force, and not every name bound anywhere in the definition. A binding is what its
     * spelling denotes where it holds, which is what lets a candidate offered from here stand ahead
     * of one an import brought in — so the two have to be the same set. A name bound in an arm of a
     * {@code match} the cursor is not in denotes nothing where the cursor is, and offering it there
     * both says something untrue and takes the place of the name that spelling does denote.
     *
     * <p>A construct that confines what it binds is walked into only when the cursor is inside it,
     * and what it binds is offered only then. Everything else is walked through: a pattern is not a
     * scope of its own, and the names it binds hold over the arm that holds the cursor.
     *
     * <p>A binding written after the cursor is not in force yet either. That is read off where it
     * starts, so the {@code let} on the line below is not offered as though it had already been
     * written.
     */
    private void collectLocalBindings(SyntaxNode node, int offset,
                                      Map<String, CompletionItem> out) {
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxNode child) {
                boolean holds = !BINDING_SCOPES.contains(child.kind())
                        || (offset >= child.start() && offset <= child.end());
                if (!holds) {
                    continue;
                }
                if (VALUE_BINDINGS.contains(child.kind()) && child.start() <= offset) {
                    SyntaxToken bound = firstIdent(child);
                    if (bound != null) {
                        // A binding comes from nowhere else, so it has no origin to show.
                        out.putIfAbsent(nameOf(bound),
                                new CompletionItem(nameOf(bound), CompletionItem.VARIABLE, null));
                    }
                }
                collectLocalBindings(child, offset, out);
            }
        }
    }

    /** Node kinds that confine what they bind: a name bound in one holds inside it and nowhere else.
     * A pattern is not among them — it binds over the arm that holds it, not over itself. */
    private static final java.util.Set<SyntaxKind> BINDING_SCOPES = java.util.Set.of(
            SyntaxKind.BLOCK_EXPR, SyntaxKind.MATCH_CASE, SyntaxKind.LAMBDA_EXPR);

    /** Whether {@code name} is a legal rename target: a single identifier token, not a keyword. The
     * lexer decides, so a non-ASCII name (Souther identifiers may be Japanese) is judged correctly. */
    public boolean isValidName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        CstLexer.Result lexed = CstLexer.lex(name);
        if (!lexed.errors().isEmpty()) {
            return false;
        }
        GreenToken sole = null;
        for (GreenToken t : lexed.tokens()) {
            if (t.kind().isTrivia() || t.kind() == SyntaxKind.EOF) {
                continue;
            }
            if (sole != null) {
                return false;   // more than one token: not a bare identifier
            }
            sole = t;
        }
        return sole != null && sole.kind() == SyntaxKind.IDENT;
    }

    private boolean isTypeDef(SyntaxNode def) {
        return def != null && def.kind() == SyntaxKind.DATA_DEF;
    }

    /** Node kinds where an identifier names a type. */
    private static final java.util.Set<SyntaxKind> TYPE_POSITIONS = java.util.Set.of(
            SyntaxKind.TYPE_REF, SyntaxKind.TYPE_ARGS, SyntaxKind.SUM_BODY, SyntaxKind.NEWTYPE_BODY,
            SyntaxKind.CONSTRUCTS_CLAUSE, SyntaxKind.DEPENDS_CLAUSE, SyntaxKind.NEW_DATA_EXPR,
            SyntaxKind.PATTERN_CTOR);

    /** Node kinds where an identifier binds a value name (a param or a {@code let}). A pattern puts
     * each name it binds in its own node, so a tuple's second name is a binder like its first. */
    private static final java.util.Set<SyntaxKind> VALUE_BINDINGS = java.util.Set.of(
            SyntaxKind.PARAM, SyntaxKind.FN_PARAM, SyntaxKind.LAMBDA_EXPR, SyntaxKind.LET_STMT,
            SyntaxKind.PATTERN_NAME, SyntaxKind.PATTERN_FIELD);

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
                if (t.kind() != SyntaxKind.IDENT || !spells(t, name)) {
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
                    // `{ qty = n }` binds `n`; the first name is the field it reads
                    SyntaxToken bound = child.kind() == SyntaxKind.PATTERN_FIELD
                            ? lastIdent(child)
                            : firstIdent(child);
                    if (spells(bound, name)) {
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

    /** The last identifier directly under {@code node} — the bound name of a {@code f = x} field
     * pattern, where the first is the field it reads. */
    private SyntaxToken lastIdent(SyntaxNode node) {
        SyntaxToken last = null;
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                last = t;
            }
        }
        return last;
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
        } catch (RuntimeException | StackOverflowError _) {
            // a file that does not parse cleanly resolves no imports; go-to-def simply misses
        }
        return null;
    }

    /** The document the module {@code moduleName} is declared in, or {@code null} if none — the
     * compile's answer, with the headers read only for a module whose file the compile could not
     * read. */
    private String uriOfModule(Compilation compilation, ModuleGraph graph, String moduleName) {
        String declared = uriOf(compilation.sourceIdOf(moduleName));
        if (declared != null && graph.text(declared) != null) {
            return declared;
        }
        for (String uri : graph.uris()) {
            abandonment.stopIfAsked();
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
        SyntaxToken ident = identAt(meaningfulTokens(root), lines, lines.offsetOf(pos.line(), pos.character()));
        if (ident == null) {
            return Optional.empty();
        }
        SyntaxNode def = declaringDef(root, nameOf(ident));
        if (def == null) {
            return Optional.empty();
        }
        SyntaxToken name = nameToken(def);
        return name == null ? Optional.empty() : Optional.of(tokenRange(lines, name));
    }

    /**
     * Hover: on a clause of an invariant, how that clause can be discharged at compile time; anywhere
     * else, the signature line of the definition the identifier under the cursor names.
     *
     * <p>The clause answer is the compiler's, because the classification is a fact about the language
     * and not something a reader of the syntax can work out. The rest stays syntactic: a hover that
     * only shows what is written does not need a compile, and this one asks for one only where it has
     * something semantic to say.
     */
    public Optional<Hover> hover(String uri, String text, Position pos, ModuleGraph graph) {
        Optional<Hover> clause = invariantClauseHover(uri, text, pos, graph);
        if (clause.isPresent()) {
            return clause;
        }
        Optional<Hover> rule = ensuresClauseHover(uri, text, pos, graph);
        return rule.isPresent() ? rule
                : hover(text, pos).map(shown -> withWhatIsNotStated(shown, uri, text, pos, graph));
    }

    /**
     * The same hover, and on a behavior that states something, which of its cases nothing is said
     * about.
     *
     * <p>There is no wildcard arm, so a case no rule names is one the behavior promises nothing of.
     * That is the declaration speaking and not a mistake in it, which is why a reader is shown it
     * where they are reading the declaration rather than told about it as a problem.
     */
    private Hover withWhatIsNotStated(Hover shown, String uri, String text, Position pos,
                                      ModuleGraph graph) {
        LineIndex lines = new LineIndex(text);
        SyntaxNode root = CstParser.parse(text).root();
        SyntaxToken ident = identAt(meaningfulTokens(root), lines,
                lines.offsetOf(pos.line(), pos.character()));
        // The cursor on the name a behavior is declared under, and nowhere else. `declaringDef` finds
        // a definition by the characters of a name, so a parameter or a local spelled like a behavior
        // of this module finds that behavior — and what is said here would be said about a value that
        // states nothing. Which token the cursor is on tells the declaration from anything spelled
        // like it.
        SyntaxNode def = ident == null ? null : declaringDef(root, nameOf(ident));
        SyntaxToken declares = def == null ? null : nameToken(def);
        ContractDischarge discharge = def != null && def.kind() == SyntaxKind.BEHAVIOR_DEF
                && declares != null && declares.start() == ident.start()
                ? contractOf(uri, graph, ident) : null;
        if (discharge == null || discharge.casesNothingIsSaidAbout().isEmpty()) {
            return shown;
        }
        List<String> unstated = new ArrayList<>();
        for (TypeSymbol each : discharge.casesNothingIsSaidAbout()) {
            unstated.add("`" + each.name() + "`");
        }
        return new Hover(shown.contents() + "\n\nNothing is stated about "
                + String.join(", ", unstated) + ".", shown.range());
    }

    /**
     * What the check reads of the {@code ensures} clause the cursor is in, rule by rule, or empty
     * when it is not in one.
     *
     * <p>Rule by rule and not clause by clause. One arrow may be written over several cases, and what
     * {@code value} is differs between them, so the same words can be read to different depths — a
     * single answer on the clause would be one of those readings shown as if it were the clause's.
     */
    private Optional<Hover> ensuresClauseHover(String uri, String text, Position pos,
                                               ModuleGraph graph) {
        LineIndex lines = new LineIndex(text);
        SyntaxNode root = CstParser.parse(text).root();
        int offset = lines.offsetOf(pos.line(), pos.character());
        SyntaxNode clause = enclosing(root, offset, SyntaxKind.ENSURES_CLAUSE);
        SyntaxNode behavior = enclosing(root, offset, SyntaxKind.BEHAVIOR_DEF);
        SyntaxToken name = behavior == null ? null : nameToken(behavior);
        if (clause == null || name == null) {
            return Optional.empty();
        }
        ContractDischarge discharge = contractOf(uri, graph, name);
        if (discharge == null) {
            return Optional.empty();
        }
        // The rules of this clause are the ones written inside it. A behavior may carry several
        // clauses, and each of them is classified on its own.
        List<RuleDischarge> here = new ArrayList<>();
        for (RuleDischarge rule : discharge.rules()) {
            SourcePos written = rule.capability().owed().clause();
            int at = lines.offsetOf(written.line() - 1, written.column() - 1);
            if (at >= clause.start() && at < clause.end()) {
                here.add(rule);
            }
        }
        return here.isEmpty() ? Optional.empty()
                : Optional.of(new Hover(ruleContents(here), nodeRange(lines, clause)));
    }

    /**
     * What the compiler says about the behavior the token {@code named} spells, or null where the
     * document belongs to no module this compile has, or the behavior states nothing.
     *
     * <p>A token and not a spelling. The compiler files its answer under the canonical name, and a
     * cursor is characters; handed a {@link String}, a caller has already decided which of the two
     * it is passing, and both call sites here had that decision to make. Canonically equivalent
     * spellings are one name ({@link #nameOf}), and which of them an author's cursor happens to be
     * on is not a thing an editor may answer differently.
     */
    private ContractDischarge contractOf(String uri, ModuleGraph graph, SyntaxToken named) {
        Compilation compilation = compileOf(graph);
        String module = moduleOf(compilation, graph, uri);
        if (module == null || named == null) {
            return null;
        }
        Map<String, ContractDischarge> byName =
                compilation.db().ask(new Bodies.ContractCapabilities(module)).value();
        return byName == null ? null : byName.get(nameOf(named));
    }

    /**
     * What the check reads of each rule, in the terms an author acts on.
     *
     * <p>What the check reads is not what the run-time check holds. Every rule is held when the
     * behavior answers, whatever this says; what this says is how much of the relation is written in
     * a form the check can carry.
     */
    private String ruleContents(List<RuleDischarge> rules) {
        StringBuilder out = new StringBuilder("**What the check reads of this**\n");
        for (RuleDischarge rule : rules) {
            String about = rule.rule().selector() == null ? ""
                    : "`" + rule.rule().selector().name() + "`: ";
            out.append("\n- ").append(about).append(readOfARule(rule.capability())).append(".");
        }
        return out.toString();
    }

    /** What came of reading one rule, said as the readings it got — or as this analysis not having
     *  finished, which says nothing about the rule. */
    private String readOfARule(ClauseDischarge capability) {
        return switch (capability.capability()) {
            case CapabilityResult.AnalysisStopped _ -> "**not determined** — this analysis did not"
                    + " finish on it, so nothing here says what the check can make of it";
            case CapabilityResult.Decided it -> it.holds()
                    ? "**always holds** — it settles on its own, so nothing the behavior answers is"
                            + " asked for it"
                    : "**never holds** — it settles the other way on its own, so nothing the behavior"
                            + " answers satisfies it";
            // Joined by "and", which is what the parts are: every one of them has to be established
            // for the rule to be, and a reader given them as alternatives would take either as
            // enough.
            case CapabilityResult.Analyzed got -> got.parts().stream()
                    .map(this::rulePart).collect(java.util.stream.Collectors.joining("; and "));
        };
    }

    /** One part of a rule, in the terms an author acts on. */
    private String rulePart(RequiredPart part) {
        return switch (part) {
            case RequiredPart.Routed it -> ruleRoute(weakest(it));
            case RequiredPart.OutsideTheFragment it -> "**runtime only** — not read, so the check"
                    + " the behavior runs on its answer is the whole of it; " + saidOf(it.why());
        };
    }

    /** One way a guard could discharge a part. */
    private String ruleRoute(StaticRoute route) {
        return switch (route) {
            case StaticRoute.AsABound _ ->
                    "**derivable** — read as a relation the numeric domain reasons over";
            case StaticRoute.AsATerm _ -> "**exact match** — read as a term the check can name and"
                    + " compare, and nothing weaker states it";
        };
    }

    /** What a finished reading could not read, in the terms an author acts on. The words are this
     *  document's; what the reading records is a value, and the two do not have to be one. */
    private String saidOf(FragmentReason why) {
        return switch (why) {
            case FragmentReason.ItCallsAnOperation it -> "it calls `" + it.operation()
                    + "`, which the check reads as a value and not as a term";
            case FragmentReason.ItsShapeIsNotRead _ ->
                    "it is not one of the shapes the check reads";
        };
    }

    /** The discharge classification of the invariant clause the cursor is in, or empty when it is not
     * in one. */
    private Optional<Hover> invariantClauseHover(String uri, String text, Position pos,
                                                 ModuleGraph graph) {
        LineIndex lines = new LineIndex(text);
        SyntaxNode root = CstParser.parse(text).root();
        int offset = lines.offsetOf(pos.line(), pos.character());
        SyntaxNode clause = enclosing(root, offset, SyntaxKind.INVARIANT_CLAUSE);
        if (clause == null) {
            return Optional.empty();
        }
        SyntaxNode data = enclosing(root, offset, SyntaxKind.DATA_DEF);
        SyntaxToken name = data == null ? null : nameToken(data);
        Compilation compilation = compileOf(graph);
        String module = moduleOf(compilation, graph, uri);
        if (name == null || module == null) {
            return Optional.empty();
        }
        // Asked of the compiler, which is what resolved the declaration this clause is written in.
        // Put together here from the module and the spelling instead, it would be an identity for
        // whatever that address names — including nothing.
        TypeSymbol declared = typeUnderCursor(compilation, uri,
                new Position(lines.lspLine(name.start()), lines.lspColumn(name.start())));
        Map<TypeSymbol, List<ClauseDischarge>> byType =
                compilation.db().ask(new Shapes.InvariantCapabilities(module)).value();
        List<ClauseDischarge> clauses = byType == null || declared == null
                ? null : byType.get(declared);
        if (clauses == null || clauses.isEmpty()) {
            return Optional.empty();
        }
        // The clauses are in the order they are written, so the one the cursor is in is the last that
        // starts at or before it.
        SourcePos found = null;
        for (ClauseDischarge c : clauses) {
            SourcePos at = c.owed().clause();
            if (lines.offsetOf(at.line() - 1, at.column() - 1) <= offset) {
                found = at;
            }
        }
        if (found == null) {
            return Optional.empty();
        }
        // Every reading of that clause, which may be more than one: what is written once can be read
        // as a bound and as a term besides, and showing whichever came last would describe half of it.
        List<String> said = new ArrayList<>();
        for (ClauseDischarge c : clauses) {
            if (c.owed().clause().equals(found)) {
                said.add(dischargeContents(c));
            }
        }
        return Optional.of(new Hover(String.join("\n\n", said), nodeRange(lines, clause)));
    }

    /** What a clause's classification says, in the terms an author acts on. */
    private String dischargeContents(ClauseDischarge clause) {
        String body = switch (clause.capability()) {
            // Nothing about the clause. What is said is that this compiler did not finish, because
            // whether a guard discharges it is exactly what was not established.
            case CapabilityResult.AnalysisStopped _ -> "**Static discharge: not determined**\n\n"
                    + "This analysis did not finish on this clause, so nothing is known here about "
                    + "whether a guard discharges it. The check on construction stands either way.";
            case CapabilityResult.Decided it -> it.holds()
                    ? "**Static discharge: always holds**\n\nThis clause settles to true on its "
                            + "own, so the obligation is met without a guard."
                    : "**Static discharge: never holds**\n\nThis clause settles to false on its "
                            + "own. No guard establishes it, so no construction of this type passes "
                            + "it.";
            case CapabilityResult.Analyzed got -> got.parts().stream().map(this::clausePart)
                    .collect(java.util.stream.Collectors.joining("\n\n"));
        };
        // What the clause is called is what an attempted construction's arm and a boundary issue read,
        // so it belongs beside how the clause discharges.
        return clause.owed().name()
                .map(n -> body + "\n\nDeparted from by name: `| " + n + " -> ...`.")
                .orElse(body);
    }

    /** One part of an invariant clause an author has to establish. Every part of them: a clause is
     *  discharged only where all of them are. */
    private String clausePart(RequiredPart part) {
        return switch (part) {
            case RequiredPart.Routed it -> "**Static discharge: " + routeName(weakest(it))
                    + "**\n\n" + clauseRoute(weakest(it));
            case RequiredPart.OutsideTheFragment it -> "**Static discharge: runtime only**\n\n"
                    + "This part of the clause cannot be represented by the static checker and is "
                    + "enforced only at construction time. No guard discharges it.\n\n"
                    + saidOf(it.why()) + ".";
        };
    }

    /**
     * The least a guard has to do to discharge a part.
     *
     * <p>The routes are alternatives and any of them discharges it, so what an author is owed is the
     * easiest of them rather than a list. A bound takes any guard that implies it and a term takes
     * one stating the same thing, so where both are there the bound is the weaker ask and naming the
     * other beside it offers a harder way to do something already done.
     *
     * <p>A projection, made here. Which routes there are is the check's answer and stays whole in
     * {@link RequiredPart.Routed}; what a document says of them is the document's.
     */
    private StaticRoute weakest(RequiredPart.Routed part) {
        return part.routes().stream().anyMatch(r -> r instanceof StaticRoute.AsABound)
                ? new StaticRoute.AsABound() : new StaticRoute.AsATerm();
    }

    /** What one route is called. */
    private String routeName(StaticRoute route) {
        return switch (route) {
            case StaticRoute.AsABound _ -> "derivable";
            case StaticRoute.AsATerm _ -> "exact match";
        };
    }

    /** What one route takes from a guard. */
    private String clauseRoute(StaticRoute route) {
        return switch (route) {
            case StaticRoute.AsABound _ -> "The checker can prove this from numeric relations when "
                    + "the constructed value is nameable, so any guard that implies it discharges "
                    + "the construction.";
            case StaticRoute.AsATerm _ -> "The checker can discharge this from a guard establishing "
                    + "the same canonical property, and nothing weaker states it.";
        };
    }

    /** The innermost node of {@code kind} whose span contains {@code offset}, or null. */
    private SyntaxNode enclosing(SyntaxNode node, int offset, SyntaxKind kind) {
        SyntaxNode found = node.kind() == kind ? node : null;
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxNode child && offset >= child.start() && offset < child.end()) {
                SyntaxNode inner = enclosing(child, offset, kind);
                if (inner != null) {
                    found = inner;
                }
            }
        }
        return found;
    }

    /** Hover: shows the signature line of the definition the identifier under the cursor names. */
    public Optional<Hover> hover(String text, Position pos) {
        LineIndex lines = new LineIndex(text);
        SyntaxNode root = CstParser.parse(text).root();
        int offset = lines.offsetOf(pos.line(), pos.character());
        SyntaxToken ident = identAt(meaningfulTokens(root), lines, offset);
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
            SyntaxNode def = declaringDef(root, nameOf(ident));
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
                if (spells(n, name)) {
                    return def;
                }
            }
        }
        return null;
    }

    /**
     * The identifier a cursor at {@code offset} is on.
     *
     * <p>Read as the name it spells and asked of that name. The dotted run of identifiers around the
     * cursor is one written name, and {@link WrittenName#partAt} says which of its parts a cursor is
     * on — including where it stops being on one, which is a rule about ends that belongs in one
     * place. This path had a rule of its own twice and it disagreed with the compiler's both times:
     * once by stopping a character early at the end of a name, once by counting the character after
     * a qualifier.
     *
     * <p>What it cannot do is tell {@code up.Amount} from {@code x.field}: one is a name and the
     * other is a field taken off a value, and which it is has to be resolved, which is the thing
     * that is missing whenever this runs. So a dotted run is read as a name, and the boundary inside
     * one is a separator. Said rather than left to be found: this is an approximation the syntax
     * forces, not a second opinion about where a name ends.
     */
    private SyntaxToken identAt(List<SyntaxToken> tokens, LineIndex lines, int offset) {
        DottedRun run = dottedRun(tokens, offset);
        if (run.parts().isEmpty()) {
            return null;
        }
        WrittenName name = spelledBy(run.parts().get(0), lines);
        for (int i = 1; i < run.parts().size(); i++) {
            name = name.then(spelledBy(run.parts().get(i), lines));
        }
        SourcePos at = lines.posOf(offset);
        // A run a dot continues is a name the author is still writing, and the end of what is
        // written of it is not the end of the name — the member is what comes next, and a caret
        // waiting for it means neither the qualifier nor the name.
        int part = run.unfinished() ? name.partWithin(at) : name.partAt(at);
        return part < 0 ? null : run.parts().get(part);
    }

    /** The identifiers of one dotted name, and whether a dot carries it past the last of them —
     *  which is what a name being typed looks like and what a written one never does. */
    private record DottedRun(List<SyntaxToken> parts, boolean unfinished) {

        static DottedRun none() {
            return new DottedRun(List.of(), false);
        }
    }

    /** One token's name, where it is written. */
    private static WrittenName spelledBy(SyntaxToken token, LineIndex lines) {
        return WrittenName.of(token.text(), lines.posOf(token.start()));
    }

    /**
     * The identifiers of the dotted run the cursor is at, in order, or none where it is at no
     * identifier at all.
     *
     * <p>Generous about the ends, because what a cursor at a boundary means is the name's to say
     * and not this walk's. Two identifiers cannot touch — the grammar puts something between them —
     * so at most one answers.
     */
    private DottedRun dottedRun(List<SyntaxToken> tokens, int offset) {
        int at = -1;
        for (int i = 0; i < tokens.size() && at < 0; i++) {
            SyntaxToken t = tokens.get(i);
            if (t.kind() == SyntaxKind.IDENT && offset >= t.start() && offset <= t.end()) {
                at = i;
            }
        }
        if (at < 0) {
            return DottedRun.none();
        }
        int first = at;
        while (first >= 2 && tokens.get(first - 1).kind() == SyntaxKind.DOT
                && tokens.get(first - 2).kind() == SyntaxKind.IDENT) {
            first -= 2;
        }
        int last = at;
        while (last + 2 < tokens.size() && tokens.get(last + 1).kind() == SyntaxKind.DOT
                && tokens.get(last + 2).kind() == SyntaxKind.IDENT) {
            last += 2;
        }
        List<SyntaxToken> parts = new ArrayList<>();
        for (int i = first; i <= last; i += 2) {
            parts.add(tokens.get(i));
        }
        boolean unfinished = last + 1 < tokens.size()
                && tokens.get(last + 1).kind() == SyntaxKind.DOT;
        return new DottedRun(parts, unfinished);
    }

    /** Every token of {@code root} that is not trivia, in the order they are written — read once
     *  per parse and asked many times, since a rename asks about every place it touches. */
    private List<SyntaxToken> meaningfulTokens(SyntaxNode root) {
        List<SyntaxToken> out = new ArrayList<>();
        meaningfulTokens(root, out);
        return out;
    }

    private void meaningfulTokens(SyntaxNode node, List<SyntaxToken> out) {
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxNode child) {
                meaningfulTokens(child, out);
            } else if (e instanceof SyntaxToken t && !t.isTrivia()) {
                out.add(t);
            }
        }
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
        collectTokens(node, lines, out, node.kind(), false);
    }

    /**
     * {@code enclosing} is the nearest ancestor that is not a pattern. A pattern says what a value is
     * made of, not what its names are for: the same {@code (a, b)} binds parameters in a lambda's
     * head and locals in a {@code let}, so classification has to see past it.
     *
     * <p>{@code callee} says this node is what an application applies. It cannot be worked out from
     * the parent alone: a qualified callee is a field read, and only its last name is the function —
     * {@code Map.mapValues(f, m)} names a namespace and then a function in it. So it is carried down, to
     * the field a read takes and not to what the read is taken off.
     */
    private void collectTokens(SyntaxNode node, LineIndex lines, List<int[]> out,
                               SyntaxKind enclosing, boolean callee) {
        SyntaxKind outer = isPatternNode(node.kind()) ? enclosing : node.kind();
        boolean seenIdent = false;
        boolean seenChildNode = false;
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxNode child) {
                boolean applied = node.kind() == SyntaxKind.APPLY_EXPR ? !seenChildNode
                        : callee && node.kind() == SyntaxKind.PAREN_EXPR;
                seenChildNode = true;
                collectTokens(child, lines, out, outer, applied);
            } else {
                SyntaxToken token = (SyntaxToken) e;
                int type = classify(token, node.kind(), outer, seenIdent, callee);
                if (token.kind() == SyntaxKind.IDENT) {
                    seenIdent = true;
                }
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

    private static boolean isPatternNode(SyntaxKind k) {
        return k == SyntaxKind.PATTERN_NAME || k == SyntaxKind.PATTERN_TUPLE
                || k == SyntaxKind.PATTERN_CTOR || k == SyntaxKind.PATTERN_RECORD
                || k == SyntaxKind.PATTERN_FIELD;
    }

    /** The token type index for a leaf, or {@code -1} to emit nothing (punctuation, whitespace). */
    private int classify(SyntaxToken token, SyntaxKind parent, SyntaxKind enclosing,
                         boolean afterFirstIdent, boolean callee) {
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
        if (EditorSymbols.isOperator(k)) {
            return T_OPERATOR;
        }
        if (k == SyntaxKind.IDENT) {
            return classifyIdent(parent, enclosing, afterFirstIdent, callee);
        }
        return -1;   // the punctuation an editor leaves alone, and whitespace
    }

    private int classifyIdent(SyntaxKind parent, SyntaxKind enclosing, boolean afterFirstIdent,
                              boolean callee) {
        // what an application applies: the bare name, or the last name of a qualified one. The
        // qualifier in front of it is not the function and is classified as what it is written as.
        if (callee && (parent == SyntaxKind.VAR_EXPR || parent == SyntaxKind.FIELD_ACCESS)) {
            return T_FUNCTION;
        }
        // a name a pattern binds is a parameter where the pattern is one, and a local otherwise
        if (parent == SyntaxKind.PATTERN_NAME) {
            return enclosing == SyntaxKind.LAMBDA_EXPR || enclosing == SyntaxKind.FN_PARAM
                    || enclosing == SyntaxKind.PARAM ? T_PARAMETER : T_VARIABLE;
        }
        // `{ a }` and `{ a = n }`: the first name is the field read, the second the name it binds
        if (parent == SyntaxKind.PATTERN_FIELD) {
            return afterFirstIdent ? T_VARIABLE : T_PROPERTY;
        }
        // `depends on f`: the `on` lexes as an identifier but is the second word of the keyword
        if (parent == SyntaxKind.DEPENDS_CLAUSE && !afterFirstIdent) {
            return T_KEYWORD;
        }
        return switch (parent) {
            case TYPE_REF, TYPE_ARGS, SUM_BODY, NEWTYPE_BODY, CONSTRUCTS_CLAUSE, DEPENDS_CLAUSE,
                 ENSURES_ARM,
                 DATA_DEF, NEW_DATA_EXPR, PATTERN_CTOR -> T_TYPE;
            case BEHAVIOR_DEF, FN_DEF, STAGE -> T_FUNCTION;
            case PARAM, FN_PARAM, LAMBDA_EXPR -> T_PARAMETER;
            case FIELD, FIELD_INIT, FIELD_ACCESS, FIELD_GETTER -> T_PROPERTY;
            case MODULE_HEADER, QUALIFIED_NAME, IMPORT_DECL -> T_NAMESPACE;
            default -> T_VARIABLE;
        };
    }

    private static boolean isKeyword(SyntaxKind k) {
        return switch (k) {
            case MODULE_KW, IMPORT_KW, EXPOSING_KW, DATA_KW, INVARIANT_KW, ENSURES_KW, AS_KW, LET_KW, GUARD_KW,
                 ELSE_KW, TRUE_KW, FALSE_KW, IF_KW, THEN_KW, BEHAVIOR_KW, DEPENDS_KW, CONSTRUCTS_KW,
                 MATCH_KW, WITH_KW, UNREACHABLE_KW -> true;
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

    /** Every diagnostic the compile error carries, each as its own editor marker — a compile stops at
     * the first error, but a pass that finds several at once (each failing {@code example} row) is
     * squiggled row by row rather than collapsed onto the first. */
    private List<LspDiagnostic> fromCompile(String text, LineIndex lines, CompileException e) {
        if (e.diagnostic() != null) {
            List<LspDiagnostic> out = new ArrayList<>();
            for (Diagnostic d : e.diagnostics()) {
                out.add(fromDiagnostic(text, lines, d));
            }
            return out;
        }
        return List.of(new LspDiagnostic(theHeadOfTheDocument(), LspDiagnostic.ERROR, null,
                cleanMessage(e.getMessage())));
    }

    /**
     * An LSP diagnostic from a structured {@link Diagnostic} for a document read on its own:
     * everything it points at is in that document, so it is its own published source.
     *
     * <p>No linked locations. A link is a URI, and a document compiled from its text alone has none
     * to give — the workspace path is where a marker can point somewhere the editor can open.
     */
    private LspDiagnostic fromDiagnostic(String text, LineIndex lines, Diagnostic d) {
        // The document itself is what this route is reading, and it is the only thing that knows:
        // the compile behind it read this text without a name for it, so a report from that parse
        // has real numbers and no file. Left unsaid, the marker fell to the head of the document.
        return project(d, ReportContext.ofTheTextItself(new SourceContext(null, text)),
                id -> lines, id -> null);
    }

    /**
     * One diagnostic as the file {@code publishedUri} reads it: the range is the place it points at
     * in that file, and every other place it points at becomes a linked location.
     *
     * <p>Which region is the range depends on which file is asking. A problem written in two of them
     * is published in both, and on the second the primary region is the one that is elsewhere — so
     * the two change places rather than the second file getting a marker on a line that has nothing
     * to do with it.
     *
     * @param context what the editor answers for this report: the file it lists it under, and the
     *        document it is reading — which is the one thing that knows which text a report parsed
     *        out of an unsaved buffer is in
     * @param linesOf the line index of a source, for turning its positions into ranges
     * @param uriOf the editor's name for a source, null when it has none to link to
     */
    private LspDiagnostic project(Diagnostic d, ReportContext context,
                                  java.util.function.Function<String, LineIndex> linesOf,
                                  java.util.function.Function<String, String> uriOf) {
        String message = DiagnosticRenderer.body(d, EDITOR_LANGUAGE);
        if (d.diff() != null) {
            message = message + " (expected " + d.diff().expectedType()
                    + ", but was " + d.diff().actualType() + ")";
        }
        int severity = d.severity() == souther.compiler.diag.Severity.WARNING
                ? LspDiagnostic.WARNING : LspDiagnostic.ERROR;
        DiagnosticView view = DiagnosticView.of(d, context);
        List<LspDiagnostic.Related> related = new ArrayList<>();
        // A label with nothing to point at joins the message. An editor's related information is a
        // location, and there is no location — the clause is in a module this workspace has no file
        // for. It used to be given `publishedUri` and the numbers it was read at, which made a link
        // the author could follow into an unrelated line of their own file.
        //
        // What an unlabelled related entry says stays the message as it was. Those sentences are
        // about code somewhere else, and an entry that borrowed them would show them as the note on
        // a location they say nothing about.
        String aboutTheDiagnostic = message;
        for (DiagnosticView.Unquotable said : view.unquotable()) {
            message = message + " " + DiagnosticRenderer.saidAbout(said, EDITOR_LANGUAGE);
        }
        for (Shown other : view.others()) {
            SourceId source = sourceOf(other.spot());
            String uri = source == null ? null : uriOf.apply(source.value());
            LineIndex lines = linesOf.apply(uriOf(source));
            if (uri == null || lines == null) {
                continue;   // nothing the editor could open, so nothing to link to
            }
            related.add(new LspDiagnostic.Related(uri, rangeOfRegion(other.spot().region()),
                    other instanceof Shown.ALabel(Spot _, souther.compiler.diag.msg.Message said)
                            ? DiagnosticRenderer.qualified(
                                    Messages.render(said, EDITOR_LANGUAGE),
                                    other.spot().region().start(), EDITOR_LANGUAGE)
                            : aboutTheDiagnostic));
        }
        Range range = view.anchor()
                .map(shown -> rangeOfRegion(shown.spot().region()))
                .orElseGet(Analyzer::theHeadOfTheDocument);
        return new LspDiagnostic(range, severity, d.code(), message, tagsOf(d), related);
    }

    /** What an editor should do with a diagnostic's range beyond marking it. An unused import names
     * text that is there and does nothing, so the name is faded rather than only listed. */
    private static List<Integer> tagsOf(Diagnostic d) {
        return "E1922".equals(d.code()) ? List.of(LspDiagnostic.UNNECESSARY) : List.of();
    }

    /** The document a spot is in, or none where it is in a text this workspace cannot name. */
    private static SourceId sourceOf(Spot spot) {
        return switch (spot) {
            case Spot.InSource in -> in.place().source();
            case Spot.InTextBeingRead(souther.compiler.diag.TextBeingRead text, UnnamedRegion _) ->
                    text.identity().orElse(null);
        };
    }

    private Range rangeOfRegion(Region r) {
        return new Range(position(r.start()), position(r.end()));
    }

    /** Where an editor puts a marker for something with no place of its own: the head of the
     *  document, which is where a reader looks when nothing points anywhere. */
    private static Range theHeadOfTheDocument() {
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
