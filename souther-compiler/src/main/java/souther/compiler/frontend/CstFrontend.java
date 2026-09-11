package souther.compiler.frontend;

import souther.compiler.source.SourceId;

import souther.compiler.ast.Ast;
import souther.compiler.cst.CstError;
import souther.compiler.cst.CstParser;
import souther.compiler.cst.SourceLayout;
import souther.compiler.cst.SyntaxNode;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.SourceProvenance;
import souther.compiler.diag.Placement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The front-end facade: source → lossless CST → the compiler's {@link Ast}. This is the single
 * entry the compiler pipeline calls in place of the older {@code syntax.Parser}. Syntax errors are
 * accumulated by the CST parser rather than thrown; at this batch boundary the first one is raised
 * as a {@link CompileException}, so callers keep their fail-fast behaviour while the same CST feeds
 * the formatter and the LSP without a throw.
 */
public final class CstFrontend {

    private CstFrontend() {
    }

    /**
     * Parses one compilation unit of a text nobody has named, naming a header-less source
     * {@code defaultModuleName} (a {@code null} default makes the {@code module} header required).
     *
     * <p>The positions it makes name no source and carry the code written at them, which is what a
     * text somebody wrote and nobody filed is. A caller that has a name for the text — a compile
     * reading one of its own sources — parses through
     * {@link #parseWithSlices(String, String, SourceId)}; a caller reading a text put back together
     * out of what a module published parses through {@link #parseWhatAModulePublished}, because
     * those positions are in a text this compile holds no file for.
     */
    public static Ast.Module parse(String source, String defaultModuleName) {
        return parseModule(source, defaultModuleName, Placement.aTextWithNoIdentity());
    }

    /** As {@link #parse(String, String)} with the default module name {@code Main}. */
    public static Ast.Module parse(String source) {
        return parse(source, "Main");
    }

    /**
     * Parses the text a module published, put back together by whoever is reading it back.
     *
     * <p>Every position it makes says it is in {@code provenance}'s text, from the moment it is
     * made. The text is real and its line 4 is a real line of it, carrying the code that module's
     * author wrote there; what no reader has is a file for it, so nothing downstream may read the
     * position as a place. This is where that is settled, and it is settled once — the pass that
     * splices such a body into a caller reads the answer rather than working it out from the source
     * being absent.
     *
     * <p>Which is not the same as saying the code was copied here. Nothing moved these positions:
     * they are where that module's code is written, and being unable to show a text is the other
     * question ({@link souther.compiler.diag.Placement}).
     *
     * <p>The header is required: a published text carries one, and a default module name here would
     * be a name this compile made up for a module that has one.
     */
    public static Ast.Module parseWhatAModulePublished(String source, SourceProvenance provenance) {
        return readBackWhatAModulePublished(source, provenance).module();
    }

    /**
     * The same, and how the text it read is laid out.
     *
     * <p>The two together, because the places in that module travel in it. What a place says is
     * which of the things written in a text it is, and the text here is one this compile put back
     * together and nobody holds a file for — so a reader that kept the module and let the text go
     * would be holding places nothing left could read. Whoever remembers the module remembers this
     * beside it.
     */
    public static ReadBack readBackWhatAModulePublished(String source, SourceProvenance provenance) {
        return parse(source, null, Placement.whatAModulePublished(provenance));
    }

    /** A module read back out of what it published, and the layout of the text it was read from. */
    public record ReadBack(Ast.Module module, SourceLayout laidOut) {}

    private static Ast.Module parseModule(String source, String defaultModuleName, Placement read) {
        return parse(source, defaultModuleName, read).module();
    }

    private static ReadBack parse(String source, String defaultModuleName, Placement read) {
        CstParser.Result result = CstParser.parse(source);
        if (!result.errors().isEmpty()) {
            throw firstError(result.root(), source, read, result.errors().get(0));
        }
        return new ReadBack(
                ImplicitUnits.expand(AstBuilder.build(result.root(), source, defaultModuleName, read)),
                SourceLayout.of(result.root(), source, read));
    }

    /**
     * The module, and the source each of its top-level declarations was written as, filed under the
     * name that declaration declares. The CST keeps every character, so a declaration comes back
     * character for character, with the whitespace around it dropped. A comment goes with the
     * declaration it precedes: one written after a declaration on its own line, or on the same line
     * as its last token, belongs to whatever is declared next, since that is where the CST puts it.
     *
     * <p>A module publishes what it declares by carrying these into its jar; the importing project
     * parses them back, rather than reading a second description of the same syntax. Each name comes
     * from the node the same way the builder takes it, so a slice cannot end up filed under its
     * neighbour's name.
     */
    public static Parsed parseWithSlices(String source, String defaultModuleName) {
        return parseWithSlices(source, defaultModuleName, null);
    }

    /**
     * As {@link #parseWithSlices(String, String)}, with every position it makes naming
     * {@code sourceId}.
     *
     * <p>A compile that holds several sources has a name for each of them, and this is where that
     * name reaches the positions. It has to be here: a module's writings do not all stay in the file
     * they were written in — an attached {@code examples for} file's rows, tables and values join the
     * module they are for — and after that a line and a column no longer say which file they were
     * read from. Read at the one place a position is made from a text, the answer never has to be
     * worked out again.
     */
    public static Parsed parseWithSlices(String source, String defaultModuleName, SourceId sourceId) {
        Placement read = sourceId == null ? Placement.aTextWithNoIdentity()
                : Placement.aFileOfThisCompile(sourceId);
        CstParser.Result result = CstParser.parse(source);
        if (!result.errors().isEmpty()) {
            throw firstError(result.root(), source, read, result.errors().get(0));
        }
        Ast.Module module = ImplicitUnits.expand(
                AstBuilder.build(result.root(), source, defaultModuleName, read));
        String header = "module " + module.name();   // a source with no header is named for its file
        List<String> imports = new ArrayList<>();
        Map<String, String> defs = new LinkedHashMap<>();
        Map<String, String> behaviors = new LinkedHashMap<>();
        Map<String, String> fns = new LinkedHashMap<>();
        for (SyntaxNode n : result.root().childNodes()) {
            switch (n.kind()) {
                case MODULE_HEADER -> header = n.text().strip();
                case IMPORT_DECL -> imports.add(n.text().strip());
                case DATA_DEF -> defs.put(AstBuilder.firstIdentText(n), n.text().strip());
                case BEHAVIOR_DEF -> behaviors.put(AstBuilder.firstIdentText(n), n.text().strip());
                case FN_DEF -> fns.put(AstBuilder.firstIdentText(n), n.text().strip());
                default -> { /* examples and fakes are not declarations to publish */ }
            }
        }
        // a unit only named (spec §unit-data) has no slice of its own; what it declares is its name, so
        // that is what an importing project reads back
        for (Ast.Def def : module.defs()) {
            defs.computeIfAbsent(def.name(), name -> "data " + name);
        }
        return new Parsed(module, new Slices(header, imports, defs, behaviors, fns));
    }

    /** A parsed module together with the source of each declaration in it. */
    public record Parsed(Ast.Module module, Slices slices) {}

    /**
     * The source a module's declarations were written as, by the name each declares. {@code header}
     * is the {@code module … exposing ( … )} line as written, which is more than the exposed names:
     * a composition's declared output is written there too, and reassembling the clause from the
     * names alone would lose it.
     */
    public record Slices(String header, List<String> imports, Map<String, String> defs,
                         Map<String, String> behaviors, Map<String, String> fns) {}

    /**
     * The parser's first error, placed in the file it was read from. The layout is built here rather
     * than taken off the builder — the build never ran — so this is the one place of a source that
     * would otherwise not say which file it is in, and a syntax error would be the single kind of
     * mistake still reported against whatever file the reader guessed at.
     *
     * <p>Placed from an offset and not from a token, because what the parser has to say is where it
     * stopped, and where it stopped may be no token at all.
     */
    private static CompileException firstError(SyntaxNode root, String source, Placement read,
                                               CstError<?> e) {
        SourceLayout layout = SourceLayout.of(root, source, read);
        Diagnostic diag = Diagnostic.say(e.said())
                .at(layout.placeAt(e.offset()), e.width()).build();
        return CompileException.of(diag);
    }
}
